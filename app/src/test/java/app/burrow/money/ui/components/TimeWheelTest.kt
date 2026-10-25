package app.burrow.money.ui.components

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The time wheel's columns ↔ the time (135, 160). */
class TimeWheelTest {
    @Test fun twelveHourColumns() {
        assertEquals(Triple(8, 6, false), wheelStart(LocalTime.of(9, 30), h24 = false)) // "9" is index 8 of 1–12
        assertEquals(Triple(11, 0, false), wheelStart(LocalTime.of(0, 0), h24 = false)) // midnight = 12 AM
        assertEquals(Triple(11, 0, true), wheelStart(LocalTime.of(12, 0), h24 = false)) // noon = 12 PM
        assertEquals(Triple(5, 0, true), wheelStart(LocalTime.of(18, 0), h24 = false))
    }

    @Test fun minutesRoundToTheNearestFive() {
        assertEquals(6, wheelStart(LocalTime.of(9, 31), false).second)
        assertEquals(7, wheelStart(LocalTime.of(9, 33), false).second)
        assertEquals("9:58 rounds up to 10:00", Triple(9, 0, false), wheelStart(LocalTime.of(9, 58), false))
        assertEquals("11:58 PM rounds to midnight", Triple(0, 0, false), wheelStart(LocalTime.of(23, 58), true).let { Triple(it.first, it.second, false) })
    }

    @Test fun backToATime() {
        assertEquals(LocalTime.of(9, 30), wheelTime(8, 6, pm = false, h24 = false))
        assertEquals(LocalTime.of(0, 0), wheelTime(11, 0, pm = false, h24 = false))
        assertEquals(LocalTime.of(12, 0), wheelTime(11, 0, pm = true, h24 = false))
        assertEquals(LocalTime.of(18, 0), wheelTime(5, 0, pm = true, h24 = false))
        assertEquals(LocalTime.of(22, 15), wheelTime(22, 3, pm = false, h24 = true))
    }

    @Test fun everyFiveMinuteTimeSurvivesTheRoundTrip() {
        for (h24 in listOf(false, true)) for (h in 0..23) for (m in 0 until 60 step 5) {
            val t = LocalTime.of(h, m)
            val (hi, mi, pm) = wheelStart(t, h24)
            assertEquals("$t h24=$h24", t, wheelTime(hi, mi, pm, h24))
        }
    }
}
