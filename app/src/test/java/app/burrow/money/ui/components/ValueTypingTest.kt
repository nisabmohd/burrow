package app.burrow.money.ui.components

import app.burrow.money.domain.AmountInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What each key does in the value sheet (136–142): amounts, days, rates. Times are the wheel (TimeWheelTest). */
class ValueTypingTest {
    private fun d(c: Char) = Key.Digit(c)

    // --- amount ---------------------------------------------------------------------------------
    private fun amount(start: Long, vararg keys: Key, digits: Int = 2): Long =
        keys.fold(AmountTyped(AmountInput.of(start, digits), fresh = true)) { t, k -> t.press(k, digits) }.input.minor()

    @Test fun firstKeyReplacesTheShownAmount() {
        assertEquals("₹9,000 shown, typing 2 5 gives 25", 2500L, amount(900000, d('2'), d('5')))
    }

    @Test fun backspaceFirstClearsInsteadOfEditingTheOldValue() {
        assertEquals(0L, amount(900000, Key.Backspace))
    }

    @Test fun decimalsAndNoDecimals() {
        assertEquals(1250L, amount(0, d('1'), d('2'), Key.Dot, d('5')))
        assertEquals("yen: no minor digits", 125L, amount(0, d('1'), d('2'), d('5'), digits = 0))
    }

    @Test fun holdBackspaceClears() {
        assertEquals(0L, amount(0, d('4'), d('2'), Key.Clear))
    }

    // --- day of month ---------------------------------------------------------------------------
    private fun day(typed: String, vararg keys: Key, max: Int = 28) = keys.fold(typed) { t, k -> typeDay(t, k, max) }

    @Test fun dayTyping() {
        assertEquals("5", day("", d('5')))
        assertEquals("25", day("", d('2'), d('5')))
        assertEquals("a second digit past the max starts a new day", "9", day("", d('3'), d('9')))
        assertEquals("31 allowed when the max is 31", "31", day("", d('3'), d('1'), max = 31))
        assertEquals("a leading 0 does nothing", "", day("", d('0')))
        assertEquals("1", day("12", Key.Backspace))
        assertEquals("", day("12", Key.Clear))
        assertEquals("no dot in days", "12", day("12", Key.Dot))
    }

    // --- rate -----------------------------------------------------------------------------------
    private fun rate(vararg keys: Key) = keys.fold("") { t, k -> typeRate(t, k) }

    @Test fun rateTyping() {
        assertEquals("84.10", rate(d('8'), d('4'), Key.Dot, d('1'), d('0')))
        assertEquals("a dot first reads 0.", "0.5", rate(Key.Dot, d('5')))
        assertEquals("one dot only", "1.2", rate(d('1'), Key.Dot, Key.Dot, d('2')))
        assertEquals("four decimals at most", "1.2345", rate(d('1'), Key.Dot, d('2'), d('3'), d('4'), d('5'), d('6')))
        assertEquals("a leading 0 is replaced", "7", rate(d('0'), d('7')))
    }
}
