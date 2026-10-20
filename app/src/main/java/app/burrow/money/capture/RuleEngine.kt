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

    /** Compiled patterns, so a message doesn't recompile every rule. */
    private val cache = object : LinkedHashMap<String, Regex?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex?>?) = size > 256
    }

    @Synchronized
    private fun regex(pattern: String): Regex? = cache.getOrPut(pattern) { runCatching { Regex(pattern) }.getOrNull() }

    /** True for OTPs, offers and failed payments: never stored, never parsed. */
    fun isNoise(body: String): Boolean = skip.any { it.containsMatchIn(body) }

    fun parse(sender: String, body: String, rules: List<Rule>, fractionDigits: (String?) -> Int = { 2 }): Parsed? {
        if (isNoise(body)) return null
        for (rule in rules) {
            if (!rule.enabled) continue
            val p = parseWith(rule, sender, body, fractionDigits) ?: continue
            return p
        }
        return null
    }

    /** One rule on one message, ignoring the noise filter (Rule detail's "Test it", Teach). */
    fun parseWith(rule: Rule, sender: String?, body: String, fractionDigits: (String?) -> Int = { 2 }): Parsed? {
        if (sender != null && regex(rule.sender)?.containsMatchIn(sender) != true) return null
        val m = regex(rule.pattern)?.find(body) ?: return null
        val amountGroup = m.groupOrNull("amount") ?: return null
        val currency = m.groupOrNull("currency")?.value?.let { normaliseCurrency(it) } ?: rule.currency
        val amount = parseAmount(amountGroup.value, rule.numberStyle) ?: return null
        val digits = fractionDigits(currency)
        val minor = runCatching { amount.setScale(digits, RoundingMode.HALF_EVEN).movePointRight(digits).longValueExact() }.getOrNull() ?: return null
        if (minor <= 0) return null
        val dir = rule.direction ?: when {
            rule.inWords.any { body.contains(it, ignoreCase = true) } -> Direction.IN
            else -> Direction.OUT
        }
        val payee = m.groupOrNull("payee")
        val account = m.groupOrNull("account")
        val spans = buildMap {
            put(Part.AMOUNT, (m.groupOrNull("currency")?.range?.first ?: amountGroup.range.first).coerceAtMost(amountGroup.range.first)..amountGroup.range.last)
            payee?.let { put(Part.PAYEE, it.range) }
            account?.let { put(Part.ACCOUNT, it.range) }
        }
        return Parsed(minor, currency, dir, payee?.value?.trim()?.trimEnd('.', ',')?.ifBlank { null }, account?.value, rule.id, spans)
    }

    private fun MatchResult.groupOrNull(name: String): MatchGroup? = runCatching { groups[name] }.getOrNull()

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
            var s = raw.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' || it == ' ' }.trim()
            style.group?.let { g -> s = s.replace(g.toString(), "").replace(" ", "") }
            if (style.decimal == ',') s = s.replace(',', '.')
            s = s.replace(" ", "")
            return s.toBigDecimalOrNull()
        }
    }
}
