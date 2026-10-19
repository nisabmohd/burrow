package app.burrow.money.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class RuleEngineTest {
    private val engine = RuleEngine()

    private val hdfc = Rule(
        id = "in.hdfc.upi",
        name = "HDFC Bank UPI",
        sender = "(?i)HDFCBK",
        pattern = "(?i)(?<currency>Rs\\.?|INR)\\s?(?<amount>[\\d,]+(\\.\\d{1,2})?) debited from A/c (?<account>XX\\d{4}).*?to VPA (?<payee>[\\w.@-]+)",
        direction = null,
    )
    private val sparkasse = Rule(
        id = "de.sparkasse.card",
        name = "Sparkasse",
        sender = "(?i)sparkasse",
        pattern = "Kartenzahlung (?<amount>[\\d.]+,\\d{2}) (?<currency>EUR) bei (?<payee>[^,]+), Konto \\.\\.\\.(?<account>\\d{4})",
        language = "de",
        numberStyle = NumberStyle.DOT_COMMA,
    )

    @Test fun readsIndianUpiSms() {
        val p = engine.parse("AX-HDFCBK", "Rs.1,240.00 debited from A/c XX1234 on 19-10-26 to VPA thirdwave@okicici. UPI Ref 4821.", listOf(hdfc))!!
        assertEquals(124000L, p.amountMinor)
        assertEquals("INR", p.currency)
        assertEquals("XX1234", p.account)
        assertEquals(Direction.OUT, p.direction)
    }

    @Test fun readsGermanNumberStyle() {
        val p = engine.parse("Sparkasse", "Kartenzahlung 1.023,40 EUR bei REWE Markt, Konto ...7781.", listOf(sparkasse))!!
        assertEquals(102340L, p.amountMinor)
        assertEquals("REWE Markt", p.payee)
    }

    @Test fun skipsOtpAndFailed() {
        assertNull(engine.parse("AX-HDFCBK", "Your OTP is 123456 for Rs.1,240.00 debited from A/c XX1234 to VPA a@b", listOf(hdfc)))
        assertNull(engine.parse("AX-HDFCBK", "Rs.1,240.00 debited from A/c XX1234 to VPA a@b failed", listOf(hdfc)))
    }

    @Test fun wrongSenderIsIgnored() {
        assertNull(engine.parse("VM-ICICIB", "Rs.10 debited from A/c XX1234 to VPA a@b", listOf(hdfc)))
    }

    @Test fun amountStyles() {
        assertEquals(BigDecimal("123456.78"), RuleEngine.parseAmount("1,23,456.78", NumberStyle.COMMA_DOT))
        assertEquals(BigDecimal("1234.56"), RuleEngine.parseAmount("1.234,56", NumberStyle.DOT_COMMA))
        assertEquals(BigDecimal("1234.56"), RuleEngine.parseAmount("1 234,56", NumberStyle.SPACE_COMMA))
    }
}
