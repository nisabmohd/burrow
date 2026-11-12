package app.burrow.money.domain

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Scheduled Drive backups start after this time of night (while charging, on the right network). */
private val NIGHT: LocalTime = LocalTime.of(1, 0)

/**
 * When the next scheduled Drive backup may run (docs/DATA.md § Drive backup): DAILY = the next 1 AM,
 * WEEKLY = the next Monday 1 AM ("Sunday night"). Pure: unit-tested.
 */
fun nextDriveSlot(now: ZonedDateTime, schedule: String): ZonedDateTime {
    val today = now.toLocalDate()
    fun at(day: java.time.LocalDate): ZonedDateTime = day.atTime(NIGHT).atZone(now.zone)
    return if (schedule == "WEEKLY") {
        at(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))).takeIf { it.isAfter(now) }
            ?: at(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)))
    } else {
        at(today).takeIf { it.isAfter(now) } ?: at(today.plusDays(1))
    }
}
