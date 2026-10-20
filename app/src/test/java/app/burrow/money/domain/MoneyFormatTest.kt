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

    @Test fun wholeUnits() {
        assertEquals(876_00L, inr.whole(876_33))
        assertEquals(877_00L, inr.whole(876_50))
        assertEquals(1500L, MoneyFormat.of("JPY", Locale.JAPAN).whole(1500))
    }

    @Test fun hiddenAmounts() {
        val h = inr.withHidden(true)
        assertEquals("₹ ••••", h.plain(240_00))
        assertEquals("₹ ••••", h.signed(-240_00))
        assertEquals("₹ ••••", h.short(6_84_250_00))
        assertEquals("₹240", h.withHidden(false).plain(240_00))
    }

    @Test fun customiseChoices() {
        val en = Locale("en", "IN")
        assertEquals("INR 1,240", MoneyFormat.of("INR", en, null, "CODE", "AUTO").plain(1_240_00))
        assertEquals("₹1,240.00", MoneyFormat.of("INR", en, null, "SYMBOL", "ALWAYS").plain(1_240_00))
        assertEquals("₹1,240", MoneyFormat.of("INR", en, null, "SYMBOL", "NONE").plain(1_240_49))
        assertEquals("₹1,240.50", MoneyFormat.of("INR", en, null, "SYMBOL", "AUTO").plain(1_240_50))
        assertEquals("€100.000,00", MoneyFormat.of("EUR", Locale.US, "DOT", "SYMBOL", "ALWAYS").plain(100_000_00))
        assertEquals("$100,000", MoneyFormat.of("USD", Locale.US, "WESTERN", "SYMBOL", "AUTO").plain(100_000_00))
        assertEquals("₹1,00,000", MoneyFormat.of("INR", Locale.US, "INDIAN", "SYMBOL", "AUTO").plain(1_00_000_00))
        assertEquals("¥1,500", MoneyFormat.of("JPY", Locale.JAPAN, null, "SYMBOL", "ALWAYS").plain(1500))
        assertEquals("INR ••••", MoneyFormat.of("INR", en, null, "CODE", "AUTO").withHidden(true).plain(1))
    }
}
