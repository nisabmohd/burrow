package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class NextTest {
    @Test fun safeToSpendMatchesTheDesign() {
        // ₹13,644 left with 13 days to go and ₹4,060 already spent today → (13,644 + 4,060) / 13 − 4,060 < 0 → 0
        assertEquals(0L, safeToSpendToday(13_644_00, 4_060_00, 13))
        // Nothing spent today: left / days, whole rupees, rounded down.
        assertEquals(1_049_00L, safeToSpendToday(13_644_00, 0, 13))
        // ₹400 spent today out of a ₹1,050 day: ₹649 left today.
        assertEquals(649_00L, safeToSpendToday(13_244_00, 400_00, 13))
    }

    @Test fun safeToSpendIsNeverNegative() {
        assertEquals(0L, safeToSpendToday(-5_000_00, 100_00, 10))
        assertEquals(0L, safeToSpendToday(0, 0, 10))
        // Last day, ₹5,000 spent today and ₹100 still left: ₹100 is still safe today.
        assertEquals(100_00L, safeToSpendToday(100_00, 5_000_00, 1))
    }

    @Test fun lastDayGetsEverythingLeft() {
        assertEquals(500_00L, safeToSpendToday(500_00, 0, 1))
        assertEquals(500_00L, safeToSpendToday(500_00, 0, 0)) // 0 days is treated as today
    }

    @Test fun safeToSpendWithoutMinorUnits() {
        assertEquals(333L, safeToSpendToday(1_000, 0, 3, digits = 0))
    }

    @Test fun splitNeedsTwoPartsThatAddUp() {
        assertEquals(560_00L, splitRemaining(1_400_00, listOf(840_00, 0)))
        assertFalse(canSaveSplit(1_400_00, listOf(840_00, 0)))
        assertFalse(canSaveSplit(1_400_00, listOf(1_400_00, 0)))
        assertTrue(canSaveSplit(1_400_00, listOf(840_00, 560_00)))
        assertFalse(canSaveSplit(1_400_00, listOf(900_00, 560_00)))
        assertEquals(-60_00L, splitRemaining(1_400_00, listOf(900_00, 560_00)))
        assertFalse(canSaveSplit(0, listOf(0, 0)))
        assertFalse(canSaveSplit(100, listOf(150, -50)))
    }

    @Test fun refundsCannotExceedWhatWasPaid() {
        assertEquals(1_930_00L, refundable(1_930_00, 0))
        assertEquals(930_00L, refundable(1_930_00, 1_000_00))
        assertEquals(0L, refundable(1_930_00, 2_000_00))
        assertEquals(965_00L, halfOf(1_930_00))
        assertEquals(1L, halfOf(3, digits = 2)) // under one unit: plain half
    }

    @Test fun recordedByItselfPercent() {
        assertEquals(92, recordedByItself(131, 142))
        assertEquals(100, recordedByItself(5, 5))
        assertEquals(0, recordedByItself(0, 7))
        assertNull(recordedByItself(0, 0))
        assertEquals(100, recordedByItself(9, 5)) // never over 100
    }

    @Test fun reviewWeekFollowsWeekStart() {
        val sunday = LocalDate.of(2026, 10, 18)
        assertEquals(DayRange(LocalDate.of(2026, 10, 12), sunday), reviewWeek(sunday, DayOfWeek.MONDAY))
        assertEquals(DayRange(sunday, LocalDate.of(2026, 10, 24)), reviewWeek(sunday, DayOfWeek.SUNDAY))
    }

    @Test fun nextReviewIsSundayEvening() {
        val thu = LocalDateTime.of(2026, 10, 22, 9, 0)
        assertEquals(LocalDateTime.of(2026, 10, 25, 18, 0), nextReviewAt(thu))
        val sunMorning = LocalDateTime.of(2026, 10, 25, 9, 0)
        assertEquals(LocalDateTime.of(2026, 10, 25, 18, 0), nextReviewAt(sunMorning))
        val sunNight = LocalDateTime.of(2026, 10, 25, 18, 0)
        assertEquals(LocalDateTime.of(2026, 11, 1, 18, 0), nextReviewAt(sunNight))
    }

    @Test fun budgetChipsPerCurrency() {
        assertEquals(listOf(20_000L, 30_000L, 50_000L, 75_000L), budgetChips("INR"))
        assertEquals(1_500L, budgetStep("INR"))
        assertEquals(75L, budgetStep("USD"))
    }
}
