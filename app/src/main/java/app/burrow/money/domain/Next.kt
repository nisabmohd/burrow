package app.burrow.money.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/*
 * The next round (burrow-next/NEXT.md): the daily number, splits, refunds, trust in capture and the
 * Sunday review. Pure and unit-tested; money is Long minor units.
 */

/**
 * Safe to spend today = (left this month − [comingUpMinor] + spent today) ÷ days left including today
 * − spent today, never below zero (docs/DESIGN.md § Upcoming). [comingUpMinor] is what is still due
 * before the month ends (unpaid bills and upcoming expenses, not transfers), when that option is on.
 * Today's hero, the home-screen widget and the Sunday review all show this number. Rounded down to
 * whole units ([digits] minor digits) so it never promises more than is there.
 */
fun safeToSpendToday(leftMinor: Long, spentTodayMinor: Long, daysLeft: Int, digits: Int = 2, comingUpMinor: Long = 0): Long {
    val pool = leftMinor - comingUpMinor.coerceAtLeast(0) + spentTodayMinor.coerceAtLeast(0)
    if (pool <= 0) return 0
    val days = daysLeft.coerceAtLeast(1)
    val safe = pool / days - spentTodayMinor.coerceAtLeast(0)
    if (safe <= 0) return 0
    var unit = 1L
    repeat(digits) { unit *= 10 }
    return safe / unit * unit
}

/** What is still to put in a category when splitting [total] into [parts] (negative = too much). */
fun splitRemaining(total: Long, parts: List<Long>): Long = total - parts.sum()

/** A split can be saved when the parts add up exactly and at least two of them hold money (08). */
fun canSaveSplit(total: Long, parts: List<Long>): Boolean =
    total > 0 && parts.all { it >= 0 } && parts.sum() == total && parts.count { it > 0 } >= 2

/** The most a new refund can be: what was paid less what came back already. */
fun refundable(paid: Long, refundedSoFar: Long): Long = (paid - refundedSoFar).coerceAtLeast(0)

/** "Half" on the refund sheet: half, rounded down to whole units. */
fun halfOf(amount: Long, digits: Int = 2): Long {
    var unit = 1L
    repeat(digits) { unit *= 10 }
    val half = amount / 2
    return (half / unit * unit).takeIf { it > 0 } ?: half
}

/** "92% recorded by itself": payments found from messages out of all payments, or null when none. */
fun recordedByItself(found: Int, total: Int): Int? =
    if (total <= 0) null else Math.round(found.coerceIn(0, total) * 100f / total)

/** The week a Sunday review looks back on: the week holding [today], starting on [weekStart]. */
fun reviewWeek(today: LocalDate, weekStart: DayOfWeek): DayRange {
    val start = today.with(TemporalAdjusters.previousOrSame(weekStart))
    return DayRange(start, start.plusDays(6))
}

/** The next Sunday-review moment after [now]: Sunday at [hour]:00 (today, if it's Sunday and earlier). */
fun nextReviewAt(now: java.time.LocalDateTime, hour: Int = 18, minute: Int = 0): java.time.LocalDateTime {
    val todayAt = now.toLocalDate().atTime(hour, minute)
    if (now.dayOfWeek == DayOfWeek.SUNDAY && now.isBefore(todayAt)) return todayAt
    return now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).atTime(hour, minute)
}

/** The Welcome budget step's quick picks, in whole units of the main currency (20k, 30k, 50k, 75k for INR). */
fun budgetChips(currency: String): List<Long> = when (currency) {
    "INR", "PKR", "LKR", "NPR", "BDT" -> listOf(20_000, 30_000, 50_000, 75_000)
    "JPY" -> listOf(100_000, 150_000, 200_000, 300_000)
    "KRW" -> listOf(1_000_000, 1_500_000, 2_000_000, 3_000_000)
    "IDR", "VND" -> listOf(5_000_000, 10_000_000, 15_000_000, 20_000_000)
    else -> listOf(1_000, 1_500, 2_000, 3_000)
}

/** The step of the Welcome budget stepper: a twentieth of the second chip (₹1,500 for INR). */
fun budgetStep(currency: String): Long = (budgetChips(currency)[1] / 20).coerceAtLeast(1)
