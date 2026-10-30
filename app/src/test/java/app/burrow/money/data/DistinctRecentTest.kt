package app.burrow.money.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DistinctRecentTest {
    private fun txn(id: Long, payee: String?, amount: Long, category: Long = 10) =
        TxnEntity(id = id, type = TxnType.EXPENSE, amount = amount, currency = "INR", baseAmount = amount, accountId = 1, categoryId = category, payee = payee, at = 0, createdAt = 0, updatedAt = 0)

    @Test fun onePerPayeeNewestKept() {
        val out = distinctRecent(listOf(txn(3, "Swiggy", 250_00), txn(2, "swiggy ", 180_00), txn(1, "Uber", 90_00)), 4)
        assertEquals(listOf(3L, 1L), out.map { it.id })
    }

    @Test fun noPayeeGoesByCategory() {
        val out = distinctRecent(listOf(txn(3, null, 20_00, 10), txn(2, "", 30_00, 10), txn(1, null, 40_00, 20)), 4)
        assertEquals(listOf(3L, 1L), out.map { it.id })
    }

    @Test fun keepsToLimit() {
        val out = distinctRecent((1L..9L).map { txn(it, "P$it", it) }, 4)
        assertEquals(4, out.size)
    }
}
