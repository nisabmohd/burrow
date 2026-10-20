package app.burrow.money.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Insights and the Activity chart (docs/DESIGN.md § Insights), pure and unit-tested.
 * Spending means expenses only: transfers and balance fixes never count (CLAUDE.md § Behaviour).
 */

/** A range of days, both ends included. */
data class DayRange(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
    operator fun contains(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)

    /** The same number of days just before this range ("vs previous period"). */
    fun previous(): DayRange = DayRange(start.minusDays(days.toLong()), start.minusDays(1))

    fun dates(): List<LocalDate> = (0 until days).map { start.plusDays(it.toLong()) }

    companion object {
        fun lastDays(n: Int, today: LocalDate) = DayRange(today.minusDays((n - 1).toLong()), today)
        fun thisMonth(today: LocalDate) = DayRange(today.withDayOfMonth(1), today)
        fun lastMonth(today: LocalDate) = today.withDayOfMonth(1).minusMonths(1).let { DayRange(it, it.plusMonths(1).minusDays(1)) }
        fun thisYear(today: LocalDate) = DayRange(today.withDayOfYear(1), today)
    }
}

/** How a chart groups days: one bar a day up to a month, a week up to 14 weeks, else a month. */
enum class Bucket { DAY, WEEK, MONTH }

fun bucketFor(range: DayRange): Bucket = when {
    range.days <= 31 -> Bucket.DAY
    range.days <= 98 -> Bucket.WEEK
    else -> Bucket.MONTH
}

/** Bars for [range]: each bar's first day and its total, from per-day totals. */
fun bars(range: DayRange, perDay: Map<LocalDate, Long>): List<Pair<LocalDate, Long>> = when (bucketFor(range)) {
    Bucket.DAY -> range.dates().map { it to (perDay[it] ?: 0) }
    Bucket.WEEK -> range.dates().chunked(7).map { week -> week.first() to week.sumOf { perDay[it] ?: 0 } }
    Bucket.MONTH -> range.dates().groupBy { it.withDayOfMonth(1) }.map { (m, days) -> days.first().coerceAtLeast(m) to days.sumOf { perDay[it] ?: 0 } }
}

private fun LocalDate.coerceAtLeast(other: LocalDate) = if (isBefore(other)) other else this

/** Running total day by day (the Insights line). */
fun cumulative(range: DayRange, perDay: Map<LocalDate, Long>): List<Long> {
    var sum = 0L
    return range.dates().map { d -> sum += perDay[d] ?: 0; sum }
}

/** Spending by weekday, Monday first. */
fun byWeekday(perDay: Map<LocalDate, Long>, range: DayRange): Map<DayOfWeek, Long> =
    DayOfWeek.entries.associateWith { w -> range.dates().filter { it.dayOfWeek == w }.sumOf { perDay[it] ?: 0 } }

/** Days in [range] (up to [today]) with no spending. */
fun noSpendDays(perDay: Map<LocalDate, Long>, range: DayRange, today: LocalDate): Pair<Int, Int> {
    val days = range.dates().filter { !it.isAfter(today) }
    return days.count { (perDay[it] ?: 0) == 0L } to days.size
}

/** Share of income not spent, 0–100; null without income. */
fun keptPercent(income: Long, spent: Long): Int? = if (income <= 0) null else (((income - spent).coerceAtLeast(0)) * 100 / income).toInt()
