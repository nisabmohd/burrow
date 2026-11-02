package app.burrow.money.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** The UAE and UK packs: every rule reads its example, in the pack's currency. */
class WorldPacksTest {
    private val engine = RuleEngine()

    private data class Case(val ruleId: String, val sender: String, val amount: Long, val payee: String?, val account: String?, val dir: Direction = Direction.OUT)

    private fun check(file: String, currency: String, cases: List<Case>) {
        val pack = RuleFiles.decode(File("src/main/assets/rules/$file.json").readText())
        assertEquals(file, pack.rules.map { it.id }.toSet(), cases.map { it.ruleId }.toSet())
        for (c in cases) {
            val rule = pack.rules.first { it.id == c.ruleId }
            val p = engine.parse(c.sender, rule.example!!, pack.rules)
            assertNotNull("${c.ruleId} did not read its example", p)
            p!!
            assertEquals(c.ruleId, c.ruleId, p.ruleId)
            assertEquals(c.ruleId, c.amount, p.amountMinor)
            assertEquals(c.ruleId, currency, p.currency)
            assertEquals(c.ruleId, c.payee, p.payee)
            assertEquals(c.ruleId, c.account, p.account)
            assertEquals(c.ruleId, c.dir, p.direction)
        }
    }

    @Test fun uae() = check(
        "ae", "AED",
        listOf(
            Case("ae.enbd.card", "EmiratesNBD", 45_50, "CARREFOUR MOE", "1234"),
            Case("ae.enbd.debit", "EmiratesNBD", 1_200_00, "DEWA BILL PAYMENT", "1234"),
            Case("ae.adcb.card", "ADCBAlert", 45_00, "TALABAT", "1234"),
            Case("ae.fab.card", "FAB", 89_00, "LULU HYPERMARKET", "1234"),
            Case("ae.mashreq.card", "Mashreq", 230_00, "AMAZON.AE", "1234"),
            Case("ae.any.credit", "RAKBANK", 15_000_00, null, "1234", Direction.IN),
        ),
    )

    @Test fun uk() = check(
        "gb", "GBP",
        listOf(
            Case("gb.monzo.spent", "co.uk.getmondo", 4_50, "Pret A Manger", null),
            Case("gb.starling.spent", "com.starlingbank.android", 8_20, "Tesco", null),
            Case("gb.revolut.paid", "com.revolut.revolut", 12_40, "Sainsbury's", null),
            Case("gb.any.card", "com.barclays.android.barclaysmobilebanking", 23_99, "AMAZON UK", "1234"),
            Case("gb.any.received", "co.uk.getmondo", 50_00, "Sam Taylor", null, Direction.IN),
        ),
    )

    @Test fun otherSendersAreNotRead() {
        val ae = RuleFiles.decode(File("src/main/assets/rules/ae.json").readText())
        assertNull(engine.parse("AX-HDFCBK", "Purchase of AED 45.50 with Credit Card ending 1234 at CARREFOUR, DUBAI.", ae.rules))
    }

    @Test fun otpsAreStillDropped() {
        val ae = RuleFiles.decode(File("src/main/assets/rules/ae.json").readText())
        assertNull(engine.parse("EmiratesNBD", "Your OTP for purchase of AED 45.50 with Credit Card ending 1234 at CARREFOUR, DUBAI is 123456.", ae.rules))
    }
}
