package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StatsTest {
    private val today = LocalDate.of(2026, 10, 19) // a Monday

    @Test fun ranges() {
        val r = DayRange.lastDays(14, today)
        assertEquals(LocalDate.of(2026, 10, 6), r.start)
        assertEquals(14, r.days)
        assertEquals(DayRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 5)), r.previous())
        assertEquals(DayRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), DayRange.lastMonth(today))
        assertEquals(19, DayRange.thisMonth(today).days)
    }

    @Test fun buckets() {
        assertEquals(Bucket.DAY, bucketFor(DayRange.lastDays(31, today)))
        assertEquals(Bucket.WEEK, bucketFor(DayRange.lastDays(60, today)))
        assertEquals(Bucket.MONTH, bucketFor(DayRange.thisYear(today)))
    }

    @Test fun barsAddUp() {
        val per = mapOf(today to 500L, today.minusDays(3) to 200L, today.minusDays(40) to 100L)
        assertEquals(14, bars(DayRange.lastDays(14, today), per).size)
        assertEquals(700L, bars(DayRange.lastDays(14, today), per).sumOf { it.second })
        val year = bars(DayRange.thisYear(today), per)
        assertEquals(10, year.size)
        assertEquals(800L, year.sumOf { it.second })
    }

    @Test fun runningTotal() = assertEquals(listOf(0L, 200L, 200L, 700L), cumulative(DayRange(today.minusDays(3), today), mapOf(today.minusDays(2) to 200L, today to 500L)))

    @Test fun patterns() {
        val per = mapOf(today to 500L, today.minusDays(1) to 300L, today.minusDays(8) to 100L)
        val w = byWeekday(per, DayRange.lastDays(14, today))
        assertEquals(500L, w[DayOfWeek.MONDAY])
        assertEquals(400L, w[DayOfWeek.SUNDAY])
        assertEquals(11 to 14, noSpendDays(per, DayRange.lastDays(14, today), today))
        assertEquals(86, keptPercent(1_20_000, 16_356))
        assertNull(keptPercent(0, 100))
    }
}
