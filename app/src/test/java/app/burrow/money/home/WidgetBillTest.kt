package app.burrow.money.home

import app.burrow.money.data.BillOccurrenceEntity
import app.burrow.money.data.OccurrenceView
import app.burrow.money.ui.plan.comingUp
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Next bill widget (89): the soonest unpaid bill due this week, and how many more are due. */
class WidgetBillTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 15)

    private fun occ(id: Long, name: String, day: LocalDate, categoryKind: String = "EXPENSE") = OccurrenceView(
        BillOccurrenceEntity(id = id, billId = id, dueDate = day.atStartOfDay(zone).toInstant().toEpochMilli()),
        name = name, kind = "BILL", billAmount = 649_00, accountId = 1, accountName = "HDFC Savings",
        categoryId = id, categoryName = name, categoryKind = categoryKind, icon = "movie", hue = 280f,
        repeatRule = "MONTHLY:1", onDue = "ASK", remindersOn = true, remindDaysBefore = 1, remindAtMinutes = 540,
    )

    private fun pick(vararg o: OccurrenceView) = widgetBill(comingUp(o.toList(), emptyList(), today, zone))

    @Test fun soonestShowsAndTheRestOfTheWeekIsCounted() {
        val (bill, more) = pick(occ(1, "Rent", today.plusDays(5)), occ(2, "Netflix", today), occ(3, "Gym", today.plusDays(2)))
        assertEquals("Netflix", bill?.name)
        assertEquals(2, more)
    }

    @Test fun laterThanThisWeekIsNothingDue() {
        val (bill, more) = pick(occ(1, "Rent", today.plusDays(7)), occ(2, "Insurance", today.plusDays(20)))
        assertNull(bill)
        assertEquals(0, more)
    }

    @Test fun overdueComesFirstAndIncomeNeverShows() {
        val (bill, more) = pick(occ(1, "Salary", today, "INCOME"), occ(2, "Power", today.minusDays(3)), occ(3, "Netflix", today.plusDays(1)))
        assertEquals("Power", bill?.name)
        assertEquals(1, more)
    }
}

