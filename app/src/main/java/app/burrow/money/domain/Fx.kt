package app.burrow.money.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Typed-in exchange rates (Customise › Currency, 83–84). Burrow never fetches rates: the user types
 * 1 USD = ₹84.10 and each transaction keeps the rate it used, so history never changes.
 */
object Fx {
    /**
     * [minor] of [currency] in the main currency, with 1 [currency] = [perPrimary] main units.
     * Rounded once, HALF_EVEN. Null when there is no rate for a foreign currency.
     */
    fun toMain(minor: Long, currency: String, main: String, perPrimary: String?): Long? {
        if (currency == main) return minor
        val rate = perPrimary?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        val major = BigDecimal.valueOf(minor, digits(currency))
        return major.multiply(rate).setScale(digits(main), RoundingMode.HALF_EVEN).unscaledValue().toLong()
    }

    /** "84.10" from what was typed ("84.1", "84,10"); null when it isn't a positive number. */
    fun parseRate(text: String): String? =
        text.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.setScale(4, RoundingMode.HALF_EVEN)?.stripTrailingZeros()?.toPlainString()

    /** The rate stepped by ±0.10 on the rate sheet (84). Never below 0.01. */
    fun step(rate: String, up: Boolean): String {
        val r = rate.toBigDecimalOrNull() ?: BigDecimal.ONE
        val next = if (up) r.add(BigDecimal("0.10")) else r.subtract(BigDecimal("0.10"))
        return next.max(BigDecimal("0.01")).setScale(2, RoundingMode.HALF_EVEN).toPlainString()
    }

    private fun digits(currency: String) =
        runCatching { java.util.Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
}

/**
 * What one person owes you (positive) or you owe them (negative), from their transactions:
 * lent +, borrowed −, a settlement they paid back (money in) −, one you paid back (money out) +.
 */
fun personBalance(rows: List<Triple<String?, String, Long>>): Long = rows.sumOf { (lend, type, total) ->
    when (lend) {
        "LENT" -> total
        "BORROWED" -> -total
        "SETTLE" -> if (type == "EXPENSE") total else -total
        else -> 0L
    }
}
