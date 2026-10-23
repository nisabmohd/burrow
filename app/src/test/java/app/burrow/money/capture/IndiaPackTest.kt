package app.burrow.money.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every rule in assets/rules/in.json, one real-shaped (anonymised) message per sender. */
class IndiaPackTest {
    private val pack = RuleFiles.decode(File("src/main/assets/rules/in.json").readText())
    private val engine = RuleEngine()

    private data class Case(val ruleId: String, val sender: String, val amount: Long, val payee: String?, val account: String?, val dir: Direction = Direction.OUT)

    private val cases = listOf(
        Case("in.hdfc.upi", "AX-HDFCBK", 240_00, "thirdwave@okicici", "1234"),
        Case("in.hdfc.sent", "VM-HDFCBK", 180_00, "CHAAYOS", "1234"),
        Case("in.hdfc.card", "JD-HDFCBK", 1_499_00, "MYNTRA", "5678"),
        Case("in.hdfc.credit", "AD-HDFCBK", 1_20_000_00, "ACME PVT LTD", "1234", Direction.IN),
        Case("in.icici.card", "VK-ICICIB", 649_00, "NETFLIX", "8890"),
        Case("in.icici.upi", "AX-ICICIT", 500_00, "zomato", "123"),
        Case("in.sbi.upi", "JM-SBIUPI", 240_00, "THIRD WAVE COFFE", "1234"),
        Case("in.axis.upi", "AX-AXISBK", 1_200_00, "SWIGGY", "1234"),
        Case("in.kotak.sent", "AX-KOTAKB", 2_350_00, "zomato@hdfcbank", "4471"),
        Case("in.idfc.upi", "BZ-IDFCFB", 450_00, "RAPIDO", "1234"),
        Case("in.yes.upi", "VM-YESBNK", 300_00, "BLINKIT", "1234"),
        Case("in.bob.upi", "BP-BOBTXN", 500_00, "swiggy@icici", "1234"),
        Case("in.pnb.debit", "AD-PNBSMS", 1_100_00, null, "1234"),
        Case("in.canara.debit", "VM-CANBNK", 299_00, "AMAZON", "1234"),
        Case("in.gpay.paid", "com.google.android.apps.nbu.paisa.user", 240_00, "Third Wave Coffee", null),
        Case("in.gpay.received", "com.google.android.apps.nbu.paisa.user", 500_00, "Rahul Sharma", null, Direction.IN),
        Case("in.phonepe.paid", "com.phonepe.app", 96_00, "Rapido Bike", null),
        Case("in.phonepe.received", "com.phonepe.app", 500_00, "Rahul Sharma", null, Direction.IN),
        Case("in.paytm.paid", "net.one97.paytm", 96_00, "Rapido Bike", "Paytm Wallet"),
        // Money in: every bank's credit SMS, so income doesn't go missing (2026-10-23).
        Case("in.hdfc.received", "AD-HDFCBK", 500_00, "rahul@okaxis", "1234", Direction.IN),
        Case("in.icici.credit", "VK-ICICIB", 1_500_00, "ACME PVT LTD", "123", Direction.IN),
        Case("in.sbi.credit", "JM-SBIINB", 500_00, "RAHUL SHARMA", "1234", Direction.IN),
        Case("in.axis.credit", "AX-AXISBK", 500_00, "RAHUL SHARMA", "1234", Direction.IN),
        Case("in.kotak.received", "AX-KOTAKB", 500_00, "rahul@okaxis", "4471", Direction.IN),
        Case("in.idfc.credit", "BZ-IDFCFB", 500_00, "RAHUL SHARMA", "1234", Direction.IN),
        Case("in.yes.credit", "VM-YESBNK", 500_00, "RAHUL SHARMA", "1234", Direction.IN),
        Case("in.bob.credit", "BP-BOBTXN", 500_00, "rahul@okaxis", "1234", Direction.IN),
        Case("in.pnb.credit", "AD-PNBSMS", 500_00, null, "1234", Direction.IN),
        Case("in.canara.credit", "VM-CANBNK", 500_00, "RAHUL SHARMA", "1234", Direction.IN),
        Case("in.any.credit", "AD-FEDBNK", 2_000_00, "ACME PVT LTD", "9012", Direction.IN),
    )

    @Test fun everyRuleHasACase() = assertEquals(pack.rules.map { it.id }.toSet(), cases.map { it.ruleId }.toSet())

    @Test fun eachExampleReadsRight() {
        for (c in cases) {
            val rule = pack.rules.first { it.id == c.ruleId }
            val p = engine.parse(c.sender, rule.example!!, pack.rules)
            assertNotNull("${c.ruleId} did not read its example", p)
            p!!
            assertEquals(c.ruleId, c.ruleId, p.ruleId)
            assertEquals(c.ruleId, c.amount, p.amountMinor)
            assertEquals(c.ruleId, "INR", p.currency)
            assertEquals(c.ruleId, c.payee, p.payee)
            assertEquals(c.ruleId, c.account, p.account)
            assertEquals(c.ruleId, c.dir, p.direction)
            assertTrue("${c.ruleId} has no amount highlight", Part.AMOUNT in p.spans)
        }
    }

    /** Banks show 2 to 6 last digits, masked with X, *, • or dots; every rule reads them all. */
    @Test fun accountDigitsAreNotFixedAtFour() {
        val variants = listOf("XX12" to "12", "**123" to "123", "XXXX5678" to "5678", "••123456" to "123456", "..34" to "34", "x7788" to "7788")
        for (c in cases.filter { it.account?.all(Char::isDigit) == true }) {
            val rule = pack.rules.first { it.id == c.ruleId }
            val token = Regex("[Xx*•.]*${c.account}\\b").find(rule.example!!)!!.value
            for ((written, digits) in variants) {
                val body = rule.example!!.replaceFirst(token, written)
                val p = engine.parse(c.sender, body, pack.rules)
                assertNotNull("${c.ruleId} did not read \"$written\"", p)
                assertEquals("${c.ruleId} with \"$written\"", digits, p!!.account)
                assertEquals("${c.ruleId} with \"$written\"", c.amount, p.amountMinor)
            }
        }
    }

    @Test fun hdfcWithoutTheDate() {
        val p = engine.parse("AX-HDFCBK", "Rs.240.00 debited from A/c XX1234 to VPA thirdwave@okicici. UPI Ref 4821.", pack.rules)!!
        assertEquals(240_00L, p.amountMinor)
        assertEquals("thirdwave@okicici", p.payee)
    }

    @Test fun otpsOffersAndFailuresAreDropped() {
        assertNull(engine.parse("AX-HDFCBK", "OTP 482913 for Rs.240.00 debited from A/c XX1234 on 19-10-26 to VPA a@b. Do not share.", pack.rules))
        assertNull(engine.parse("AX-HDFCBK", "Rs.240.00 debited from A/c XX1234 on 19-10-26 to VPA a@b failed.", pack.rules))
        assertNull(engine.parse("VK-ICICIB", "Congratulations! You are pre-approved for a loan of Rs 5,00,000.", pack.rules))
    }

    /** SBI also sends credits without a name: "credited by Rs500 on 22Oct26 by (Ref no …)". */
    @Test fun sbiCreditWithoutAName() {
        val p = engine.parse("JM-SBIUPI", "Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 22Oct26 by (Ref no 629184791)", pack.rules)!!
        assertEquals(500_00L, p.amountMinor)
        assertEquals(Direction.IN, p.direction)
        assertEquals("1234", p.account)
    }

    /** A credit is never read as a debit, and the catch-all credit rule never reads a debit. */
    @Test fun creditsAndDebitsStayApart() {
        assertEquals(Direction.OUT, engine.parse("AX-HDFCBK", pack.rules.first { it.id == "in.hdfc.upi" }.example!!, pack.rules)!!.direction)
        assertNull(engine.parse("AD-FEDBNK", "Rs.2,000.00 debited from your A/c XX9012 on 21-10-26 to ACME.", pack.rules))
        assertNull(engine.parse("AD-FEDBNK", "OTP 4821 to receive Rs.2,000.00 credited to your A/c XX9012. Do not share.", pack.rules))
    }

    @Test fun otherSendersAreIgnored() {
        val hdfc = pack.rules.first { it.id == "in.hdfc.upi" }.example!!
        assertNull(engine.parse("AX-KOTAKB", hdfc, pack.rules))
        assertNull(engine.parse("com.whatsapp", "You paid ₹240 to Third Wave Coffee", pack.rules))
    }

    /** design/screens/52: this Kotak format is what a user teaches; the pack must not read it. */
    @Test fun teachExampleIsNotUnderstood() =
        assertNull(engine.parse("AX-KOTAKB", "Rs.2,350.00 debited from A/c XX4471 on 18-10-26 to VPA zomato@hdfcbank Ref 629184. Not you? Call 1860-266-2666", pack.rules))

    @Test fun highlightsCoverTheParts() {
        val body = pack.rules.first { it.id == "in.hdfc.upi" }.example!!
        val p = engine.parse("AX-HDFCBK", body, pack.rules)!!
        assertEquals("Rs.240.00", body.substring(p.spans.getValue(Part.AMOUNT)))
        assertEquals("1234", body.substring(p.spans.getValue(Part.ACCOUNT)))
        assertEquals("thirdwave@okicici", body.substring(p.spans.getValue(Part.PAYEE)))
    }

    @Test fun exportLeavesOutMessageText() {
        val text = RuleFiles.export("Mine", listOf(pack.rules.first().copy(userMade = true)))
        assertTrue(!text.contains("thirdwave"))
        assertEquals(1, RuleFiles.decode(text).rules.size)
    }
}
