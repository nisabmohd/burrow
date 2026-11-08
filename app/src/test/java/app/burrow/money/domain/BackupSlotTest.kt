package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BackupSlotTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    @Test fun dailyBeforeOneAmIsTonight() = assertEquals(at(2026, 11, 8, 1), nextDriveSlot(at(2026, 11, 8, 0, 30), "DAILY"))

    @Test fun dailyAfterOneAmIsTomorrow() = assertEquals(at(2026, 11, 9, 1), nextDriveSlot(at(2026, 11, 8, 9), "DAILY"))

    // 8 Nov 2026 is a Sunday: "Sunday night" is Monday 1 AM.
    @Test fun weeklyIsMondayOneAm() = assertEquals(at(2026, 11, 9, 1), nextDriveSlot(at(2026, 11, 8, 18), "WEEKLY"))

    @Test fun weeklyOnMondayAfterOneAmIsNextWeek() = assertEquals(at(2026, 11, 16, 1), nextDriveSlot(at(2026, 11, 9, 2), "WEEKLY"))
}
