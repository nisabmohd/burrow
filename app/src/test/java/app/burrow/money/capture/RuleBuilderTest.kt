package app.burrow.money.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBuilderTest {
    private val engine = RuleEngine()
    private val kotak = "Rs.2,350.00 debited from A/c XX4471 on 18-10-26 to VPA zomato@hdfcbank Ref 629184. Not you? Call 1860-266-2666"

    private fun teach(text: String, sender: String?, amountAt: String, payeeAt: String?, accountAt: String?): Rule {
        fun tok(word: String?) = word?.let { RuleBuilder.tokenAt(text, text.indexOf(it)) }
        return RuleBuilder.build("user.1", "Kotak", sender, text, tok(amountAt)!!, tok(payeeAt), tok(accountAt))
    }

    @Test fun tokensDropTrailingPunctuation() {
        assertEquals("zomato@hdfcbank", kotak.substring(RuleBuilder.tokenAt(kotak, kotak.indexOf("zomato"))!!))
        assertEquals("629184", kotak.substring(RuleBuilder.tokenAt(kotak, kotak.indexOf("629184") + 2)!!))
        assertNull(RuleBuilder.tokenAt(kotak, kotak.indexOf(' ')))
        assertTrue(RuleBuilder.isAmount("Rs.2,350.00"))
        assertFalse(RuleBuilder.isAmount("zomato@hdfcbank"))
    }

    @Test fun sameWordForTwoPartsDoesNotCrash() {
        // Tapping the account word again for Paid to used to cut the text between them backwards.
        val rule = teach(kotak, "AX-KOTAKB", "Rs.2,350.00", "XX4471", "XX4471")
        assertNotNull(engine.parseWith(rule, "AX-KOTAKB", kotak))
    }

    @Test fun readsTheMessageItWasTaughtOn() {
        val rule = teach(kotak, "AX-KOTAKB", "Rs.2,350.00", "zomato@hdfcbank", "XX4471")
        val p = engine.parseWith(rule, "AX-KOTAKB", kotak)
        assertNotNull(rule.pattern, p)
        assertEquals(2_350_00L, p!!.amountMinor)
        assertEquals("INR", p.currency)
        assertEquals("zomato@hdfcbank", p.payee)
        assertEquals("XX4471", p.account)
        assertEquals(Direction.OUT, p.direction)
    }

    @Test fun readsTheNextMessageOfTheSameKind() {
        val rule = teach(kotak, "AX-KOTAKB", "Rs.2,350.00", "zomato@hdfcbank", "XX4471")
        val next = "Rs.96.00 debited from A/c XX4471 on 20-10-26 to VPA rapido.bike@axl Ref 731122. Not you? Call 1860-266-2666"
        val p = engine.parseWith(rule, "VM-KOTAKB", next)!!
        assertEquals(96_00L, p.amountMinor)
        assertEquals("rapido.bike@axl", p.payee)
        assertNull(engine.parseWith(rule, "AX-HDFCBK", next)) // other senders don't use it
    }

    @Test fun amountOnly() {
        val rule = teach(kotak, "AX-KOTAKB", "Rs.2,350.00", null, null)
        assertEquals(2_350_00L, engine.parseWith(rule, "AX-KOTAKB", kotak)!!.amountMinor)
    }

    @Test fun incomeIsSeen() {
        val text = "Your A/c 4471 is credited with INR 5,000.00 by RAHUL on 18-10-26"
        val rule = teach(text, "AX-KOTAKB", "5,000.00", "RAHUL", "4471")
        val p = engine.parseWith(rule, "AX-KOTAKB", text)!!
        assertEquals(5_000_00L, p.amountMinor)
        assertEquals(Direction.IN, p.direction)
    }

    @Test fun builtRulesAreSafe() =
        assertEquals(RuleSafety.Check.Ok, RuleSafety.check(teach(kotak, "AX-KOTAKB", "Rs.2,350.00", "zomato@hdfcbank", "XX4471")))

    @Test fun catastrophicPatternsAreRejected() {
        val bad = Rule(id = "x", name = "x", sender = ".*", pattern = "(?<amount>(a+)+)!?\\d")
        assertTrue(RuleSafety.check(bad) is RuleSafety.Check.Bad)
        assertTrue(RuleSafety.check(bad.copy(pattern = "([unclosed")) is RuleSafety.Check.Bad)
        assertTrue(RuleSafety.check(bad.copy(pattern = "debited \\d+")) is RuleSafety.Check.Bad) // no amount group
    }

    @Test fun packRulesAreSafe() {
        val pack = RuleFiles.decode(java.io.File("src/main/assets/rules/in.json").readText())
        assertEquals(RuleSafety.Check.Ok, RuleSafety.checkAll(pack.rules))
    }
}
