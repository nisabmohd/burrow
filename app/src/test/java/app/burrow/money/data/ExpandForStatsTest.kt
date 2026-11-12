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

    /** A payment in another currency counts at its main-currency value (QA 12 Nov: $50 counted as ₹50). */
    @Test fun otherCurrencyCountsInTheMainOne() {
        val usd = view(9, TxnType.EXPENSE, 50_00, 1).let { it.copy(txn = it.txn.copy(currency = "USD", baseAmount = 4_200_00)) }
        val out = expandForStats(listOf(usd), emptyList()).single()
        org.junit.Assert.assertEquals(4_200_00L, out.txn.amount)
        // A split in dollars: each part at its share of the converted amount.
        val parts = listOf(part(9, 1, 30_00, "Food"), part(9, 2, 20_00, "Fun"))
        org.junit.Assert.assertEquals(listOf(2_520_00L, 1_680_00L), expandForStats(listOf(usd), parts).map { it.txn.amount })
    }
}
