package app.burrow.money.capture

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Applies rules to a message. Order: user rules first (newest first), then built-in packs.
 * Drops anything that looks like an OTP, an offer or a failed payment before trying rules.
 * Pure function of (sender, body, rules): no clock, no Android, easy to test.
 */
class RuleEngine {

    private val skip = listOf(
        Regex("(?i)\\b(otp|one[- ]time password|verification code)\\b"),
        Regex("(?i)\\b(offer|cashback up to|win|congratulations|pre-approved)\\b"),
        Regex("(?i)\\b(failed|declined|reversed|could not be processed)\\b"),
    )

    fun parse(sender: String, body: String, rules: List<Rule>, fractionDigits: (String?) -> Int = { 2 }): Parsed? {
        if (skip.any { it.containsMatchIn(body) }) return null
        for (rule in rules) {
            if (!rule.enabled) continue
            if (!Regex(rule.sender).containsMatchIn(sender)) continue
            val m = Regex(rule.pattern).find(body) ?: continue
            val rawAmount = m.groups["amount"]?.value ?: continue
            val currency = m.groupOrNull("currency")?.let { normaliseCurrency(it) } ?: rule.currency
            val amount = parseAmount(rawAmount, rule.numberStyle) ?: continue
            val minor = amount.setScale(fractionDigits(currency), RoundingMode.HALF_EVEN).movePointRight(fractionDigits(currency)).longValueExact()
            val dir = rule.direction ?: when {
                rule.inWords.any { body.contains(it, ignoreCase = true) } -> Direction.IN
                else -> Direction.OUT
            }
            return Parsed(minor, currency, dir, m.groupOrNull("payee")?.trim()?.trimEnd('.', ','), m.groupOrNull("account"), rule.id)
        }
        return null
    }

    private fun MatchResult.groupOrNull(name: String): String? = runCatching { groups[name]?.value }.getOrNull()

    private fun normaliseCurrency(s: String): String? = when (s.trim().uppercase().trimEnd('.')) {
        "RS", "INR", "₹" -> "INR"
        "AED" -> "AED"
        "$", "USD" -> "USD"
        "€", "EUR" -> "EUR"
        "£", "GBP" -> "GBP"
        else -> null
    }

    companion object {
        /** "1,23,456.78" / "1.234,56" / "1 234,56" / "1234" → BigDecimal, by the rule's number style. */
        fun parseAmount(raw: String, style: NumberStyle): BigDecimal? {
            var s = raw.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' || it == ' ' }.trim()
            style.group?.let { g -> s = s.replace(g.toString(), "").replace(" ", "") }
            if (style.decimal == ',') s = s.replace(',', '.')
            s = s.replace(" ", "")
            return s.toBigDecimalOrNull()
        }
    }
}
