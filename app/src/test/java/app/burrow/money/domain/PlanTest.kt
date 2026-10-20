package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class PlanTest {
    private val d = LocalDate.of(2026, 10, 19)

    @Test fun monthlyRepeat() {
        val r = Repeat.Monthly(17)
        assertEquals(LocalDate.of(2026, 11, 17), r.nextOnOrAfter(d))
        assertEquals(LocalDate.of(2026, 10, 17), r.nextOnOrAfter(LocalDate.of(2026, 10, 17)))
        assertEquals(LocalDate.of(2026, 2, 28), Repeat.Monthly(31).nextOnOrAfter(LocalDate.of(2026, 2, 1)))
        assertEquals(3, r.between(LocalDate.of(2026, 8, 1), d).size)
    }

    @Test fun yearlyAndWeekly() {
        assertEquals(LocalDate.of(2027, 1, 9), Repeat.Yearly(1, 9).nextOnOrAfter(d))
        assertEquals(LocalDate.of(2026, 10, 19), Repeat.Weekly(DayOfWeek.MONDAY).nextOnOrAfter(d))
        assertEquals(LocalDate.of(2026, 10, 26), Repeat.Weekly(DayOfWeek.MONDAY).nextOnOrAfter(d.plusDays(1)))
    }

    @Test fun repeatRoundTrip() {
        for (r in listOf(Repeat.Monthly(17), Repeat.Yearly(1, 9), Repeat.Weekly(DayOfWeek.MONDAY))) assertEquals(r, Repeat.parse(r.encode()))
        assertNull(Repeat.parse("MONTHLY:40"))
        assertNull(Repeat.parse("nonsense"))
    }

    @Test fun billMatch() {
        val due = listOf(DueBill(1, "Netflix", 649_00, LocalDate.of(2026, 10, 19)), DueBill(2, "Electricity", 1_800_00, LocalDate.of(2026, 10, 17)))
        assertEquals(1L, matchBill("NETFLIX", 649_00, d, due)?.occurrenceId)
        assertEquals(2L, matchBill("BESCOM Electricity", 1_900_00, d, due)?.occurrenceId) // within 10%, 2 days late
        assertNull(matchBill("NETFLIX", 800_00, d, due)) // amount off by more than 10%
        assertNull(matchBill("NETFLIX", 649_00, d.plusDays(5), due)) // too far from the due date
        assertNull(matchBill(null, 649_00, d, due))
    }

    @Test fun loanSchedule() {
        val s = loanSchedule(21_40_560_00, BigDecimal("8.5"), 32_400_00, LocalDate.of(2026, 10, 25), 42)
        val first = s.first()
        assertEquals(42, first.number)
        assertEquals(15_162_30L, first.interest) // 21,40,560 × 8.5% / 12 = 15,162.30
        assertEquals(32_400_00L - 15_162_30L, first.principal)
        assertEquals(21_40_560_00L, s.sumOf { it.principal }) // pays off exactly
        assertEquals(90, s.size)
    }

    @Test fun loanInWholeRupees() {
        val first = loanSchedule(21_40_560_00, BigDecimal("8.5"), 32_400_00, LocalDate.of(2026, 10, 25), 42, unit = 100).first()
        assertEquals(15_162_00L, first.interest)
        assertEquals(17_238_00L, first.principal)
    }

    @Test fun loanThatNeverEnds() = assertEquals(0, loanSchedule(1_00_000_00, BigDecimal("12"), 500_00, d, 1).size)

    @Test fun depositGrows() {
        val start = LocalDate.of(2025, 3, 12)
        val maturity = LocalDate.of(2027, 3, 12)
        val atEnd = depositValue(2_00_000_00, BigDecimal("7.1"), start, maturity, maturity)
        assertTrue("was $atEnd", atEnd in 2_30_000_00L..2_30_500_00L) // ₹2L × (1 + 7.1%/4)^8 ≈ ₹2,30,226
        assertEquals(atEnd, depositValue(2_00_000_00, BigDecimal("7.1"), start, maturity, maturity.plusYears(1)))
    }

    @Test fun budgetAlerts() {
        assertEquals(80, budgetAlert(7_000, 8_200, 10_000))
        assertEquals(100, budgetAlert(9_000, 10_100, 10_000))
        assertEquals(100, budgetAlert(5_000, 12_000, 10_000)) // jumping past both: the bigger one
        assertNull(budgetAlert(8_100, 8_500, 10_000)) // already past 80
        assertNull(budgetAlert(0, 500, 0))
        assertEquals(110, BudgetStatus(7_710, 7_000).percent)
    }

    @Test fun goalPace() {
        assertEquals(23_333_34L, goalPerMonth(2_50_000_00, 1_10_000_00, d, LocalDate.of(2027, 4, 1)))
        assertNull(goalPerMonth(50_000_00, 50_000_00, d, LocalDate.of(2027, 4, 1)))
        assertNull(goalPerMonth(50_000_00, 0, d, null))
    }

    @Test fun shortAmounts() {
        val inr = MoneyFormat.of("INR", Locale("en", "IN"))
        assertEquals("₹6.84L", inr.short(6_84_250_00))
        assertEquals("₹1.1L", inr.short(1_10_000_00))
        assertEquals("₹21.41L", inr.short(21_40_560_00))
        assertEquals("₹16.4k", inr.short(16_356_00))
        assertEquals("₹1.2Cr", inr.short(1_20_00_000_00))
        assertEquals("₹8,120", inr.short(8_120_00))
        assertEquals("$1.2M", MoneyFormat.of("USD", Locale.US).short(1_200_000_00))
    }
}
