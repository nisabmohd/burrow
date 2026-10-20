package app.burrow.money.data

import app.burrow.money.domain.TypeSum
import app.burrow.money.domain.balances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SampleDataTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val now = today.atTime(9, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val ids = (1L..16L).toList()

    private fun build(currency: String = "INR") =
        SampleData.build(today, now, currency, digits = 2, monthStartDay = 1, categoryIds = ids, zone = ZoneOffset.UTC)

    private fun balancesOf(s: SampleData): List<Long> {
        val all = s.txns + s.goals.flatMap { it.added }
        val out = all.groupBy { it.accountId to it.type }.map { (k, v) -> TypeSum(k.first, k.second, v.sumOf { it.amount }) }
        val into = all.filter { it.toAccountId != null }.groupBy { it.toAccountId!! }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val b = balances(s.accounts.withIndex().associate { (i, a) -> i.toLong() to a.openingBalance }, out, into)
        return s.accounts.indices.map { b.getValue(it.toLong()) }
    }

    @Test fun accountsShowTheDesignBalances() {
        assertEquals(listOf(142_300_00L, -8_120_00L, 1_500_00L, 850_00L, 16_600_00L, -21_40_560_00L, 2_00_000_00L), balancesOf(build()))
    }

    /** Design 55: net worth ₹3,61,250 excludes the card and the home loan (Count in net worth off). */
    @Test fun netWorthLeavesOutCardAndLoan() {
        val s = build()
        val counted = s.accounts.withIndex().filter { it.value.includeInNetWorth }.sumOf { balancesOf(s)[it.index] }
        assertEquals(3_61_250_00L, counted)
        assertEquals(listOf("CARD", "LOAN"), s.accounts.filter { !it.includeInNetWorth }.map { it.type })
    }

    @Test fun sameDataEveryTime() = assertEquals(build().txns, build().txns)

    @Test fun nothingInTheFuture() = assertTrue(build().txns.all { it.at <= now })

    @Test fun todayHasTheTwoDesignPayments() {
        val start = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(listOf("Uber", "Third Wave Coffee"), build().txns.filter { it.at >= start }.map { it.payee })
    }

    @Test fun electricityIsOverdueAndOthersPaid() {
        val electricity = build().bills.first { it.bill.name == "Electricity" }
        val thisMonth = electricity.occurrences.first { it.dueDate == today.withDayOfMonth(17).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }
        assertEquals("UPCOMING", thisMonth.status) // 17th has passed: overdue
        assertTrue(electricity.occurrences.filter { it.dueDate < thisMonth.dueDate }.all { it.status == "PAID" })
    }

    @Test fun goalsAddUp() {
        val japan = build().goals.first { it.goal.name == "Japan trip" }
        assertEquals(1_10_000_00L, japan.added.sumOf { it.amount })
    }

    @Test fun otherCurrenciesAreScaled() {
        val usd = build("USD")
        assertEquals(1_500_00L, usd.txns.first { it.payee == "ACME Pvt Ltd" }.amount)
        assertEquals(375_00L, usd.budgets.first().amount)
    }
}
