package app.burrow.money.domain

import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

/*
 * Money as people read it (docs/COPY.md § Numbers). Pure Kotlin so it is unit-tested on the JVM.
 * Whole amounts drop the decimals ("₹240"); others keep them ("₹240.50"). The minus sign is "−".
 * Indian grouping (1,00,000) for India and INR, Western (100,000) elsewhere; changeable in More later.
 */

enum class Grouping { INDIAN, WESTERN }

data class NumberStyle(val grouping: Grouping, val groupSeparator: Char, val decimalSeparator: Char) {
    companion object {
        fun forLocale(locale: Locale, currency: String): NumberStyle {
            val symbols = DecimalFormatSymbols.getInstance(locale)
            val indian = locale.country == "IN" || currency == "INR"
            return NumberStyle(if (indian) Grouping.INDIAN else Grouping.WESTERN, symbols.groupingSeparator, symbols.decimalSeparator)
        }
    }
}

class MoneyFormat(val currency: String, private val symbol: String, private val style: NumberStyle) {
    /** Minor-unit digits for this currency (JPY 0, INR 2, KWD 3). */
    val digits: Int = runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    /** [minor] rounded to whole units (₹876.33 → ₹876), for per-month figures from yearly ones. */
    fun whole(minor: Long): Long {
        var unit = 1L
        repeat(digits) { unit *= 10 }
        return Math.floorDiv(minor + unit / 2, unit) * unit
    }

    /** "₹13,644", never signed (budgets, balances in headers, transfers). */
    fun plain(minor: Long): String = symbol + number(minor)

    /** "−₹240" for money out, "+₹1,20,000" for money in, "₹0" for nothing. */
    fun signed(minor: Long): String = when {
        minor < 0 -> MINUS + plain(minor)
        minor > 0 -> "+" + plain(minor)
        else -> plain(0)
    }

    /** "−₹240" when negative, "₹240" otherwise (balances that can go below zero). */
    fun balance(minor: Long): String = if (minor < 0) MINUS + plain(minor) else plain(minor)

    /**
     * Short amounts for widgets and summaries: "₹6.84L", "₹1.2Cr" in Indian style, "$16.4k",
     * "$1.2M" otherwise; below 10,000 the plain amount. Never signed.
     */
    fun short(minor: Long): String {
        var unit = 1L
        repeat(digits) { unit *= 10 }
        val whole = kotlin.math.abs(minor) / unit
        val indian = style.grouping == Grouping.INDIAN
        return when {
            whole < 10_000 -> plain(kotlin.math.abs(minor) / unit * unit)
            indian && whole >= 1_00_00_000 -> scaled(whole, 10_000_000, "Cr", 2)
            indian && whole >= 1_00_000 -> scaled(whole, 100_000, "L", 2)
            whole >= 1_000_000 -> scaled(whole, 1_000_000, "M", 2)
            else -> scaled(whole, 1_000, "k", 1)
        }
    }

    private fun scaled(value: Long, by: Long, suffix: String, decimals: Int): String =
        symbol + java.math.BigDecimal(value).divide(java.math.BigDecimal(by), decimals, java.math.RoundingMode.HALF_EVEN)
            .stripTrailingZeros().toPlainString().replace('.', style.decimalSeparator) + suffix

    /** The number without the symbol: "1,23,456.78", "1,234". */
    fun number(minor: Long): String {
        val abs = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        var unit = 1L
        repeat(digits) { unit *= 10 }
        val whole = abs / unit
        val fraction = abs % unit
        val grouped = group(whole.toString())
        return if (fraction == 0L) grouped else grouped + style.decimalSeparator + fraction.toString().padStart(digits, '0')
    }

    private fun group(s: String): String {
        if (s.length <= 3) return s
        val sep = style.groupSeparator
        val last3 = s.takeLast(3)
        val head = s.dropLast(3)
        val size = if (style.grouping == Grouping.INDIAN) 2 else 3
        val parts = head.reversed().chunked(size).map { it.reversed() }.reversed()
        return parts.joinToString(sep.toString()) + sep + last3
    }

    companion object {
        const val MINUS = "−"

        /** The short symbol people expect, whatever the phone's language ("₹", not "INR" or "Rs"). */
        fun symbolFor(currency: String, locale: Locale): String = when (currency) {
            "INR" -> "₹"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            else -> runCatching { Currency.getInstance(currency).getSymbol(locale) }.getOrDefault(currency)
        }

        fun of(currency: String, locale: Locale = Locale.getDefault()) =
            MoneyFormat(currency, symbolFor(currency, locale), NumberStyle.forLocale(locale, currency))
    }
}
