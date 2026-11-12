package app.burrow.money.ui.plan

import app.burrow.money.data.BillOccurrenceEntity
import app.burrow.money.data.OccurrenceView
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A repeating salary is money coming in: "+", Mark received, never lowers safe to spend. */
class ComingUpTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 12)

    private fun occ(id: Long, name: String, categoryKind: String?, day: LocalDate, kind: String = "BILL") = OccurrenceView(
        BillOccurrenceEntity(id = id, billId = id, dueDate = day.atStartOfDay(zone).toInstant().toEpochMilli()),
        name = name, kind = kind, billAmount = 50_000_00, accountId = 1, accountName = "HDFC Savings",
        categoryId = id, categoryName = name, categoryKind = categoryKind, icon = null, hue = null,
        repeatRule = "MONTHLY:1", onDue = "ASK", remindersOn = true, remindDaysBefore = 1, remindAtMinutes = 540,
    )

    @Test fun salaryIsIncomeAndRentIsNot() {
        val items = comingUp(
            listOf(occ(1, "Salary", "INCOME", today.plusDays(3)), occ(2, "Rent", "EXPENSE", today.plusDays(4))),
            emptyList(), today, zone,
        )
        val salary = items.first { it.name == "Salary" }
        val rent = items.first { it.name == "Rent" }
        assertTrue(salary.income)
        assertFalse(salary.countsAsSpending)
        assertFalse(rent.income)
        assertTrue(rent.countsAsSpending)
        // Only the rent lowers safe to spend.
        assertEquals(50_000_00L, dueThisMonth(items, today.withDayOfMonth(30)))
    }

    @Test fun aWeeklyBillCountsEveryDueDateThisMonth() {
        val days = listOf(3L, 10L, 17L)
        val occ = days.mapIndexed { i, d -> occ(10L + i, "Cleaner", "EXPENSE", today.plusDays(d)).let { o -> o.copy(occurrence = o.occurrence.copy(billId = 10)) } }
        val items = comingUp(occ, emptyList(), today, zone)
        // One row in Coming up, three due dates in safe to spend (Nov 15, 22, 29).
        assertEquals(1, items.size)
        assertEquals(3 * 50_000_00L, dueThisMonth(items, today.withDayOfMonth(30)))
    }
}
