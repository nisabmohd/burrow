package app.burrow.money.domain

import java.math.BigDecimal
import java.math.RoundingMode

/*
 * The keypad's sum ("120+45−10"), as an immutable value. Pure Kotlin, unit-tested.
 * Operators are "+" and "−" only; the total never goes below zero. At most [digits] decimals per
 * number and 9 whole digits, so an amount always fits a Long in minor units.
 */
data class AmountInput(val text: String = "", val digits: Int = 2) {

    val hasOperator: Boolean get() = text.any { it == PLUS || it == MINUS }

    fun press(key: Char): AmountInput {
        val current = lastNumber()
        return when (key) {
            PLUS, MINUS -> when {
                text.isEmpty() -> this
                text.last() == PLUS || text.last() == MINUS -> copy(text = text.dropLast(1) + key)
                else -> copy(text = text + key)
            }
            DOT -> when {
                digits == 0 || current.contains(DOT) -> this
                current.isEmpty() -> copy(text = text + "0.")
                else -> copy(text = text + DOT)
            }
            in '0'..'9' -> when {
                current.contains(DOT) && current.substringAfter(DOT).length >= digits -> this
                !current.contains(DOT) && current.length >= MAX_WHOLE -> this
                current == "0" -> copy(text = text.dropLast(1) + key) // no "05"
                else -> copy(text = text + key)
            }
            else -> this
        }
    }

    /** "00" is two zeros, but never as the start of a number. */
    fun pressDoubleZero(): AmountInput = if (lastNumber().isEmpty() || lastNumber() == "0") this else press('0').press('0')

    fun backspace(): AmountInput = copy(text = text.dropLast(1))

    /** The sum in minor units, never negative. */
    fun minor(): Long {
        var total = BigDecimal.ZERO
        var sign = 1
        var number = StringBuilder()
        fun flush() {
            val n = number.toString().trimEnd(DOT).toBigDecimalOrNull() ?: BigDecimal.ZERO
            total = if (sign > 0) total + n else total - n
            number = StringBuilder()
        }
        for (ch in text) {
            when (ch) {
                PLUS -> { flush(); sign = 1 }
                MINUS -> { flush(); sign = -1 }
                else -> number.append(ch)
            }
        }
        flush()
        if (total.signum() <= 0) return 0
        return total.setScale(digits, RoundingMode.HALF_EVEN).movePointRight(digits).longValueExact()
    }

    /** The sum written back as the only number, after "=" (the ✓ key with operators). */
    fun evaluated(): AmountInput {
        val m = minor()
        if (m == 0L) return copy(text = "")
        val value = BigDecimal.valueOf(m, digits).stripTrailingZeros().toPlainString()
        return copy(text = value)
    }

    private fun lastNumber(): String = text.takeLastWhile { it != PLUS && it != MINUS }

    companion object {
        const val PLUS = '+'
        const val MINUS = '−'
        const val DOT = '.'
        private const val MAX_WHOLE = 9

        /** Prefills the keypad when editing: 24050 minor → "240.5". */
        fun of(minor: Long, digits: Int): AmountInput =
            if (minor <= 0) AmountInput(digits = digits)
            else AmountInput(BigDecimal.valueOf(minor, digits).stripTrailingZeros().toPlainString(), digits)
    }
}
