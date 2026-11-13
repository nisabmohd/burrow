package app.burrow.money.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Check a message (52, final 12 Nov): Burrow reads a message no rule could, before the user taps anything.
 * Its guesses must be right on the shapes banks send, and Fix it rings the words a part could be (239).
 */
class GuessTest {
    private val kotak = "Rs.2,350.00 debited from A/c XX4471 on 18-10-26 to VPA zomato@hdfcbank Ref 629184. Not you? Call 1860-266-2666"
    private val sbiCard = "Txn of Rs 899.00 on SBI Card ending 3321 at DECATHLON on 17/10/26. Avl lmt: Rs 62,101"

    private fun word(text: String, r: IntRange?) = r?.let { text.substring(it) }

    @Test fun readsTheDesignsKotakMessage() {
        assertEquals("Rs.2,350.00", word(kotak, RuleBuilder.firstAmount(kotak)))
        assertEquals("zomato@hdfcbank", word(kotak, RuleBuilder.guessPayee(kotak)))
        assertEquals("XX4471", word(kotak, RuleBuilder.guessAccount(kotak)))
    }

    @Test fun readsTheDesignsCardMessage() {
        assertEquals("DECATHLON", word(sbiCard, RuleBuilder.guessPayee(sbiCard)))
        assertEquals("3321", word(sbiCard, RuleBuilder.guessAccount(sbiCard)))
        // "Txn of" has no type word: the type stays unknown and the screen asks (240).
        assertNull(TypeWords.detect(sbiCard, emptyMap(), "en", emptyMap()))
    }

    @Test fun commonShapes() {
        val hdfc = "Spent Rs.311.00 On HDFC Bank Card 7711 At CROMA On 2026-11-12:10:11:03.Not You? Call 18002586161"
        assertEquals("CROMA", word(hdfc, RuleBuilder.guessPayee(hdfc)))
        assertEquals("7711", word(hdfc, RuleBuilder.guessAccount(hdfc)))
        val masked = "INR 2,350.00 debited towards ZOMATO from a/c **4471 on 18-Oct. Avl bal INR 12,004."
        assertEquals("ZOMATO", word(masked, RuleBuilder.guessPayee(masked)))
        assertEquals("**4471", word(masked, RuleBuilder.guessAccount(masked)))
        val credit = "Your A/c XX1234 is credited with Rs.5,000.00 from RAHUL K on 12-11-26."
        assertEquals("XX1234", word(credit, RuleBuilder.guessAccount(credit)))
        assertEquals("RAHUL", word(credit, RuleBuilder.guessPayee(credit)))
        // The payment rail is never the name: "by NEFT from ACME PVT LTD" is ACME.
        val salary = "Rs.45000.00 credited to A/c XX1234 on 12-11-26 by NEFT from ACME PVT LTD. Ref 1005."
        assertEquals("ACME", word(salary, RuleBuilder.guessPayee(salary)))
    }

    @Test fun noGuessRatherThanAWrongOne() {
        val plain = "Thank you for banking with us. Have a nice day."
        assertNull(RuleBuilder.guessPayee(plain))
        assertNull(RuleBuilder.guessAccount(plain))
        // "to your" / "at the" are not names.
        assertNull(RuleBuilder.guessPayee("Rs.50 sent to your wallet at the counter"))
    }

    @Test fun fixItRingsWhatAPartCouldBe() {
        // 239: for the amount, every number in the message except the one already read, never the account.
        val amount = RuleBuilder.firstAmount(kotak)!!
        val account = RuleBuilder.guessAccount(kotak)!!
        val ringed = RuleBuilder.candidates(kotak, Part.AMOUNT, listOf(amount, account)).map { kotak.substring(it) }
        assertEquals(listOf("18-10-26", "629184", "1860-266-2666"), ringed)
        // For Paid to: names, never numbers.
        val payees = RuleBuilder.candidates(kotak, Part.PAYEE, listOf(amount, account)).map { kotak.substring(it) }
        assertTrue("zomato@hdfcbank" in payees)
        assertFalse(payees.any { s -> s.any { it.isDigit() } && '@' !in s })
        // For the account: masked digits and 3–6 digit numbers.
        val accounts = RuleBuilder.candidates(kotak, Part.ACCOUNT, listOf(amount)).map { kotak.substring(it) }
        assertTrue("XX4471" in accounts && "629184" in accounts)
        assertFalse("Rs.2,350.00" in accounts)
    }
}
