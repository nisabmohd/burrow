package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PeriodTest {
    @Test fun calendarMonth() {
        val p = BudgetPeriod.containing(LocalDate.of(2026, 10, 19), 1)
        assertEquals(LocalDate.of(2026, 10, 1), p.start)
        assertEquals(LocalDate.of(2026, 11, 1), p.endExclusive)
        assertEquals(13, p.daysLeft(LocalDate.of(2026, 10, 19))) // 19..31, today included
    }

    @Test fun startDayLaterInMonth() {
        val before = BudgetPeriod.containing(LocalDate.of(2026, 10, 10), 25)
        assertEquals(LocalDate.of(2026, 9, 25), before.start)
        val on = BudgetPeriod.containing(LocalDate.of(2026, 10, 25), 25)
        assertEquals(LocalDate.of(2026, 10, 25), on.start)
        assertEquals(1, on.daysLeft(LocalDate.of(2026, 11, 24)))
    }

    @Test fun perDayRule() {
        assertEquals(1_049_53L, perDay(13_644_00, 13))
        assertEquals(0L, perDay(-500, 5))
    }
}
