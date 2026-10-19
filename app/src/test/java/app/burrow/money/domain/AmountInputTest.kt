package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInputTest {
    private fun type(keys: String, digits: Int = 2) = keys.fold(AmountInput(digits = digits)) { a, k -> a.press(k) }

    @Test fun plainNumber() = assertEquals(24000L, type("240").minor())

    @Test fun decimalsAreLimited() {
        assertEquals("1.25", type("1.257").text)
        assertEquals(125L, type("1.25").minor())
        assertEquals("0.5", type(".5").text)
        assertEquals("15", type("1.5", digits = 0).text)
    }

    @Test fun sums() {
        val a = type("120+45−10")
        assertTrue(a.hasOperator)
        assertEquals(15500L, a.minor())
        assertEquals("155", a.evaluated().text)
        assertFalse(a.evaluated().hasOperator)
    }

    @Test fun neverNegative() = assertEquals(0L, type("10−50").minor())

    @Test fun operatorRules() {
        assertEquals("", type("+").text)
        assertEquals("12−", type("12+−").text)
        assertEquals(1200L, type("12+").minor())
    }

    @Test fun noLeadingZeros() {
        assertEquals("5", type("05").text)
        assertEquals("", AmountInput().pressDoubleZero().text)
        assertEquals("100", type("1").pressDoubleZero().text)
    }

    @Test fun prefill() {
        assertEquals("240.5", AmountInput.of(24050, 2).text)
        assertEquals("", AmountInput.of(0, 2).text)
    }

    @Test fun wholeDigitsAreCapped() = assertEquals("123456789", type("1234567890").text)
}
