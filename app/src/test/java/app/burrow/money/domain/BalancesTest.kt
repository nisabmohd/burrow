package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BalancesTest {
    @Test fun openingPlusTransactions() {
        val b = balances(
            opening = mapOf(1L to 10_000L, 2L to 0L),
            outSums = listOf(TypeSum(1, "EXPENSE", 2_500), TypeSum(1, "INCOME", 50_000), TypeSum(1, "TRANSFER", 8_000)),
            transfersIn = mapOf(2L to 8_000L),
        )
        assertEquals(49_500L, b[1L])
        assertEquals(8_000L, b[2L])
    }

    @Test fun transferMovesMoneyWithoutChangingTotal() {
        val b = balances(mapOf(1L to 100L, 2L to 100L), listOf(TypeSum(1, "TRANSFER", 40)), mapOf(2L to 40L))
        assertEquals(200L, b.values.sum())
    }

    @Test fun cardGoesNegative() {
        val b = balances(mapOf(3L to 0L), listOf(TypeSum(3, "EXPENSE", 8_120)), emptyMap())
        assertEquals(-8_120L, b[3L])
    }

    @Test fun fixBalanceMovesItBothWays() {
        val b = balances(mapOf(1L to 1_000L), listOf(TypeSum(1, "ADJUST_UP", 250), TypeSum(1, "ADJUST_DOWN", 100)), emptyMap())
        assertEquals(1_150L, b[1L])
    }
}

class RefundBalanceTest {
    @org.junit.Test fun refundRaisesItsAccount() {
        val b = balances(mapOf(1L to 1_000L), listOf(TypeSum(1, "EXPENSE", 300), TypeSum(1, "REFUND", 100)), emptyMap())
        org.junit.Assert.assertEquals(800L, b[1])
    }
}
