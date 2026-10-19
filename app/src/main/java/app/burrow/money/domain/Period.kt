package app.burrow.money.domain

import java.time.LocalDate
import java.time.YearMonth

/*
 * The budget month (docs/DATA.md, CLAUDE.md § Behaviour): it starts on the user's month-start day
 * (1–28) and runs to the day before it next month. Pure, unit-tested.
 */
data class BudgetPeriod(val start: LocalDate, val endExclusive: LocalDate) {
    /** Days left, today included. */
    fun daysLeft(today: LocalDate): Int = (endExclusive.toEpochDay() - today.toEpochDay()).toInt().coerceAtLeast(1)

    /** The month the period is named after and stored under (yyyy-MM of its start). */
    val month: YearMonth get() = YearMonth.from(start)

    companion object {
        fun containing(today: LocalDate, startDay: Int): BudgetPeriod {
            val day = startDay.coerceIn(1, 28)
            val thisMonth = today.withDayOfMonth(day)
            val start = if (today.dayOfMonth >= day) thisMonth else thisMonth.minusMonths(1)
            return BudgetPeriod(start, start.plusMonths(1))
        }
    }
}

/** "₹X a day" = left ÷ days left including today, never negative (CLAUDE.md § Behaviour). */
fun perDay(leftMinor: Long, daysLeft: Int): Long = if (leftMinor <= 0) 0 else leftMinor / daysLeft.coerceAtLeast(1)
