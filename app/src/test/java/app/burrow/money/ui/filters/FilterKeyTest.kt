package app.burrow.money.ui.filters

import app.burrow.money.data.FilterKey
import app.burrow.money.data.TxnEntity
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.domain.DayRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Activity's Filter sheet counts over [FilterKey]s (five columns per row) instead of every full row.
 * The count must be exactly what the list then shows: the key test and the row test agree.
 */
class FilterKeyTest {
    private val zone = ZoneOffset.UTC
    private val day0 = LocalDate.of(2026, 1, 1)
    private val types = listOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.TRANSFER, TxnType.REFUND)

    private fun row(r: Random, id: Long): TxnView {
        val type = types[r.nextInt(types.size)]
        val at = day0.plusDays(r.nextLong(320)).atStartOfDay(zone).toInstant().toEpochMilli() + r.nextLong(86_400_000)
        val txn = TxnEntity(
            id = id, type = type, amount = 100, currency = "INR", baseAmount = 100, accountId = 1L + r.nextInt(6),
            toAccountId = if (type == TxnType.TRANSFER) 1L + r.nextInt(6) else null,
            categoryId = if (type == TxnType.TRANSFER || r.nextInt(10) == 0) null else 10L + r.nextInt(8),
            at = at, createdAt = at, updatedAt = at,
        )
        return TxnView(txn, null, null, null, "A", "x", 0f, null, null, null)
    }

    private fun key(v: TxnView) = v.txn.let { FilterKey(it.at, it.type, it.accountId, it.toAccountId, it.categoryId) }

    @Test fun keyAndRowAgreeForEveryFilter() {
        val r = Random(7)
        val rows = (1L..3_000L).map { row(r, it) }
        val filters = buildList {
            add(Filters())
            TypeFilter.entries.forEach { add(Filters(type = it)) }
            add(Filters(accounts = setOf(3)))
            add(Filters(accounts = setOf(2, 5), type = TypeFilter.TRANSFERS))
            add(Filters(categories = setOf(11, 14)))
            add(Filters(range = DayRange(day0.plusDays(40), day0.plusDays(70))))
            add(Filters(TypeFilter.SPENT, setOf(1, 4), setOf(10, 12, 17), DayRange(day0.plusDays(100), day0.plusDays(220))))
        }
        filters.forEach { f ->
            assertEquals("$f", rows.count { f.matches(it, zone) }, rows.count { f.matches(key(it), zone) })
        }
    }

    @Test fun transferInCountsForTheAccountItGoesTo() {
        val v = TxnView(
            TxnEntity(type = TxnType.TRANSFER, amount = 5_000_00, currency = "INR", baseAmount = 5_000_00, accountId = 1, toAccountId = 9, at = 0, createdAt = 0, updatedAt = 0),
            null, null, null, "HDFC", "x", 0f, "Card", "x", 0f,
        )
        val f = Filters(accounts = setOf(9))
        assertEquals(true, f.matches(v, zone))
        assertEquals(true, f.matches(key(v), zone))
    }
}
