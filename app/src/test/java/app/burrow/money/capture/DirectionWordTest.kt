package app.burrow.money.capture

import org.junit.Assert.assertEquals
import org.junit.Test

/** Teach's fourth part: the word the user taps decides money in or out. */
class DirectionWordTest {
    private val engine = RuleEngine()
    private fun tok(text: String, word: String) = RuleBuilder.tokenAt(text, text.indexOf(word))!!

    @Test fun wordsMeanWhatTheySay() {
        for (w in listOf("credited", "Credited.", "CREDIT", "received", "deposited", "refunded", "added", "cashback", "reversal")) assertEquals(w, Direction.IN, RuleBuilder.directionOf(w))
        for (w in listOf("debited", "spent", "paid", "sent", "withdrawn", "charged", "abgebucht")) assertEquals(w, Direction.OUT, RuleBuilder.directionOf(w))
    }

    @Test fun theTappedWordWinsOverTheGuessWhenBothWordsAreThere() {
        // "debited … credited to VPA": guessing from the whole message called this money in.
        val text = "Rs.500.00 debited from A/c XX1234 and credited to VPA chai@okaxis. UPI Ref 1234."
        val tapped = RuleBuilder.build("user.1", "T", "HDFCBK", text, tok(text, "Rs.500.00"), tok(text, "chai@okaxis"), tok(text, "XX1234"), tok(text, "debited"))
        assertEquals(Direction.OUT, tapped.direction)
        assertEquals(Direction.OUT, engine.parseWith(tapped, "HDFCBK", text)!!.direction)
    }

    @Test fun tappingCreditedMakesItIncome() {
        val text = "INR 2,000.00 credited to A/c XX9921 by NEFT from ACME CORP. Avl bal 10,000.00"
        val r = RuleBuilder.build("user.2", "T", "ICICIB", text, tok(text, "2,000.00"), tok(text, "ACME"), tok(text, "XX9921"), tok(text, "credited"))
        assertEquals(Direction.IN, r.direction)
    }

    @Test fun skippingTheWordKeepsTheOldGuess() {
        val text = "INR 2,000.00 credited to A/c XX9921 by NEFT"
        val r = RuleBuilder.build("user.3", "T", "ICICIB", text, tok(text, "2,000.00"), null, tok(text, "XX9921"), null)
        assertEquals(Direction.IN, r.direction)
    }

    @Test fun theDirectionWordIsNeverACapturedGroup() {
        val text = "Rs.500.00 debited from A/c XX1234 to VPA chai@okaxis."
        val r = RuleBuilder.build("user.4", "T", null, text, tok(text, "Rs.500.00"), null, null, tok(text, "debited"))
        val p = engine.parseWith(r, null, text)!!
        assertEquals(50000L, p.amountMinor)
        assertEquals(null, p.spans[Part.DIRECTION])
    }
}
