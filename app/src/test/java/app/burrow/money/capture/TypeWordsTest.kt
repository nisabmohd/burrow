package app.burrow.money.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Teach's Type (235, 236): the words in a message say what it is. */
class TypeWordsTest {
    private val debit = "Rs.2,350.00 debited from A/c XX4471 on 18-10-26 to VPA zomato@hdfcbank Ref 629184."

    @Test fun wordsGiveTheType() {
        assertEquals(MoneyType.EXPENSE, TypeWords.detect(debit)?.type)
        assertEquals("debited", TypeWords.detect(debit)?.word)
        assertEquals(MoneyType.INCOME, TypeWords.detect("Rs 500 credited to A/c XX1")?.type)
        // More specific first: a refund that is credited is a refund, never income.
        assertEquals(MoneyType.REFUND, TypeWords.detect("Refund of Rs 499 credited to A/c XX1")?.type)
        assertEquals(MoneyType.NOT_PAYMENT, TypeWords.detect("Rs 300 requested by ravi@okaxis")?.type)
        assertNull(TypeWords.detect("Rs 300 moved from A/c XX1"))
    }

    @Test fun wholeWordsOnly() {
        // "sent" inside "present" is not a word of its own.
        assertNull(TypeWords.detect("Rs 10 present in A/c"))
    }

    @Test fun aRuleAddsItsOwnWords() {
        val own = mapOf(MoneyType.EXPENSE to listOf("moved"))
        assertEquals(MoneyType.EXPENSE, TypeWords.detect("Rs 300 moved from A/c XX1", own)?.type)
    }

    @Test fun storedTypeRoundTrips() {
        MoneyType.entries.forEach { assertEquals(it, MoneyType.ofStored(it.stored)) }
        // Captures saved before types keep reading as before.
        assertEquals(MoneyType.EXPENSE, MoneyType.ofStored("OUT"))
        assertEquals(MoneyType.INCOME, MoneyType.ofStored("IN"))
        assertEquals(MoneyType.EXPENSE, MoneyType.ofStored(null))
    }

    @Test fun engineUsesThePickedTypeThenTheWords() {
        val rule = RuleBuilder.build("u", "Kotak", "AX-KOTAKB", debit, 0..10, null, null)
        val engine = RuleEngine()
        val byWords = rule.copy(direction = null, type = null)
        assertEquals(MoneyType.EXPENSE, engine.parseWith(byWords, "AX-KOTAKB", debit)?.type)
        val picked = rule.copy(direction = null, type = MoneyType.TRANSFER)
        assertEquals(MoneyType.TRANSFER, engine.parseWith(picked, "AX-KOTAKB", debit)?.type)
        assertEquals(Direction.OUT, engine.parseWith(picked, "AX-KOTAKB", debit)?.direction)
    }

    @Test fun guessesTheAmountWithItsCurrency() {
        assertEquals("Rs.2,350.00", RuleBuilder.firstAmount(debit)?.let { debit.substring(it) })
        // An account number is never the guess; the number after "INR " is.
        val t = "A/c XX4471 debited INR 99.00"
        assertEquals("99.00", RuleBuilder.firstAmount(t)?.let { t.substring(it) })
    }

    @Test fun aRuleCanTakeABuiltInWordAway() {
        // For this bank "paid" isn't an expense word: with it taken away, nothing else matches.
        val t = "Rs 300 paid into A/c XX1"
        assertEquals(MoneyType.EXPENSE, TypeWords.detect(t)?.type)
        assertNull(TypeWords.detect(t, removed = mapOf(MoneyType.EXPENSE to listOf("paid"))))
    }

    @Test fun otherLanguagesHaveTheirWordsAndEnglish() {
        assertEquals(MoneyType.EXPENSE, TypeWords.detect("Ihr Konto wurde mit 25,00 EUR belastet", language = "de")?.type)
        assertEquals(MoneyType.REFUND, TypeWords.detect("Remboursement de 12,00 EUR crédité", language = "fr")?.type)
        assertEquals(MoneyType.INCOME, TypeWords.detect("Rs 500 credited to A/c XX1", language = "hi")?.type)
        assertEquals(MoneyType.INCOME, TypeWords.detect("आपके खाते में Rs 500 जमा किए गए", language = "hi")?.type)
        // Japanese has no spaces; "予定" (scheduled) is not a payment yet.
        assertEquals(MoneyType.EXPENSE, TypeWords.detect("3,000円のご利用がありました", language = "ja")?.type)
        assertEquals(MoneyType.NOT_PAYMENT, TypeWords.detect("3,000円の引き落とし予定です", language = "ja")?.type)
        assertEquals(MoneyType.EXPENSE, TypeWords.detect("Compra de R$ 50,00 aprovada", language = "pt-BR")?.type)
    }
}
