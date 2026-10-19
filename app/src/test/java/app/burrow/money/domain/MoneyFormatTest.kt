package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MoneyFormatTest {
    private val inr = MoneyFormat.of("INR", Locale("en", "IN"))
    private val usd = MoneyFormat.of("USD", Locale.US)
    private val eurDe = MoneyFormat.of("EUR", Locale.GERMANY)

    @Test fun indianGrouping() {
        assertEquals("₹1,20,000", inr.plain(1_20_000_00))
        assertEquals("₹13,644", inr.plain(13_644_00))
        assertEquals("₹1,23,45,678", inr.plain(1_23_45_678_00))
        assertEquals("₹999", inr.plain(999_00))
    }

    @Test fun westernGrouping() {
        assertEquals("$120,000", usd.plain(120_000_00))
        assertEquals("$1,234,567.50", usd.plain(1_234_567_50))
        assertEquals("€1.234,56", eurDe.plain(1_234_56))
    }

    @Test fun wholeAmountsDropDecimals() {
        assertEquals("₹240", inr.plain(240_00))
        assertEquals("₹240.05", inr.plain(240_05))
    }

    @Test fun signs() {
        assertEquals("−₹240", inr.signed(-240_00))
        assertEquals("+₹1,20,000", inr.signed(1_20_000_00))
        assertEquals("₹0", inr.signed(0))
        assertEquals("−₹8,120", inr.balance(-8_120_00))
        assertEquals("₹8,120", inr.balance(8_120_00))
    }

    @Test fun zeroDigitCurrency() {
        assertEquals("¥1,500", MoneyFormat.of("JPY", Locale.JAPAN).plain(1500))
    }
}
