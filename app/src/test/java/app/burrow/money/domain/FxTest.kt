package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FxTest {
    @Test fun sameCurrencyIsUnchanged() = assertEquals(12_345L, Fx.toMain(12_345, "INR", "INR", null))

    @Test fun convertsWithTheTypedRate() {
        // $12.50 at ₹84.10 = ₹1,051.25
        assertEquals(1_051_25L, Fx.toMain(12_50, "USD", "INR", "84.10"))
    }

    @Test fun zeroDigitCurrencies() {
        // ¥1,000 at ₹0.55 = ₹550.00; ₹100 at ¥1.81 = ¥181
        assertEquals(550_00L, Fx.toMain(1_000, "JPY", "INR", "0.55"))
        assertEquals(181L, Fx.toMain(100_00, "INR", "JPY", "1.81"))
    }

    @Test fun roundsHalfEvenOnce() {
        // 0.05 × 0.5 = 0.025 → 0.02 (half even)
        assertEquals(2L, Fx.toMain(5, "USD", "INR", "0.5"))
    }

    @Test fun noRateMeansNoAmount() {
        assertNull(Fx.toMain(100, "USD", "INR", null))
        assertNull(Fx.toMain(100, "USD", "INR", "0"))
    }

    @Test fun parsesAndStepsRates() {
        assertEquals("84.1", Fx.parseRate("84,10"))
        assertNull(Fx.parseRate("-2"))
        assertEquals("84.20", Fx.step("84.10", up = true))
        assertEquals("0.01", Fx.step("0.05", up = false))
    }

    @Test fun personBalances() {
        val rows = listOf(
            Triple("LENT", "EXPENSE", 2_000_00L),     // lent ₹2,000
            Triple("SETTLE", "INCOME", 500_00L),      // they paid ₹500 back
            Triple("BORROWED", "INCOME", 300_00L),    // borrowed ₹300
        )
        assertEquals(1_200_00L, personBalance(rows))
        assertEquals(-300_00L, personBalance(listOf(Triple("BORROWED", "INCOME", 300_00L))))
        assertEquals(0L, personBalance(listOf(Triple("BORROWED", "INCOME", 300_00L), Triple("SETTLE", "EXPENSE", 300_00L))))
    }
}
