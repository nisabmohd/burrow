package app.burrow.money.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpandForStatsTest {
    private fun view(id: Long, type: String, amount: Long, category: Long?) = TxnView(
        TxnEntity(id = id, type = type, amount = amount, currency = "INR", baseAmount = amount, accountId = 1, categoryId = category, at = 0, createdAt = 0, updatedAt = 0, refundOf = if (type == TxnType.REFUND) 1 else null),
        categoryName = "Food", categoryIcon = "restaurant", categoryHue = 28f,
        accountName = "HDFC", accountIcon = "account_balance", accountHue = 215f,
        toAccountName = null, toAccountIcon = null, toAccountHue = null,
    )

    private fun part(txn: Long, category: Long, amount: Long, name: String) =
        SplitView(SplitEntity(txnId = txn, categoryId = category, amount = amount), name, "x", 0f)

    @Test fun splitBecomesOneRowPerPart() {
        val out = expandForStats(listOf(view(1, TxnType.EXPENSE, 1_400_00, 10)), listOf(part(1, 10, 840_00, "Groceries"), part(1, 20, 560_00, "Rent")))
        assertEquals(listOf(840_00L, 560_00L), out.map { it.txn.amount })
        assertEquals(listOf(10L, 20L), out.map { it.txn.categoryId })
        assertEquals(listOf("Groceries", "Rent"), out.map { it.categoryName })
        assertEquals(1_400_00L, out.sumOf { it.txn.baseAmount })
    }

    @Test fun refundIsNegativeSpendingInItsCategory() {
        val out = expandForStats(listOf(view(1, TxnType.EXPENSE, 1_930_00, 10), view(2, TxnType.REFUND, 1_930_00, 10)), emptyList())
        assertEquals(0L, out.filter { it.txn.type == TxnType.EXPENSE }.sumOf { it.txn.amount })
        assertEquals(TxnType.EXPENSE, out[1].txn.type)
    }

    @Test fun otherRowsPassThrough() {
        val v = listOf(view(1, TxnType.INCOME, 100, 5), view(2, TxnType.TRANSFER, 50, null))
        assertEquals(v, expandForStats(v, listOf(part(9, 1, 1, "orphan"))))
    }
}
