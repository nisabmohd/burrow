package app.burrow.money.ui.components

import app.burrow.money.domain.AmountInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What each key does in the value sheet (135–142): amounts, days, times, rates. */
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

    // --- time ------------------------------------------------------------------------------------
    private fun time(vararg keys: Key, h24: Boolean = false) = keys.fold("") { t, k -> typeTime(t, k, h24) }

    @Test fun twelveHourTyping() {
        assertEquals("1030", time(d('1'), d('0'), d('3'), d('0')))
        assertEquals(10 to 30, typedTime("1030"))
        assertEquals(9 to 30, typedTime("930"))
        assertEquals("no 0 to start", "", time(d('0')))
        assertEquals("a minute never starts with 6–9", "9", time(d('9'), d('7')))
        assertEquals("never 13 o'clock on a 12-hour clock: 1:3·", "13", time(d('1'), d('3')))
        assertNull("not whole yet", typedTime("10"))
        assertNull(typedTime("1360"))
    }

    @Test fun twentyFourHourTyping() {
        assertEquals("2215", time(d('2'), d('2'), d('1'), d('5'), h24 = true))
        assertEquals(22 to 15, typedTime("2215", h24 = true))
        assertEquals(0 to 5, typedTime("0005", h24 = true))
        assertNull(typedTime("2460", h24 = true))
    }

    @Test fun timeReadsWhileTyping() {
        assertEquals("··:··", typedTimeText(""))
        assertEquals("1:··", typedTimeText("1"))
        assertEquals("10:··", typedTimeText("10"))
        assertEquals("9:3·", typedTimeText("93"))
        assertEquals("9:30", typedTimeText("930"))
        assertEquals("10:30", typedTimeText("1030"))
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
