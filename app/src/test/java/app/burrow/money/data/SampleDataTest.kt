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
    private val ids = (1L..15L).toList()

    private fun build(currency: String = "INR") =
        SampleData.build(today, now, currency, digits = 2, monthStartDay = 1, categoryIds = ids, zone = ZoneOffset.UTC)

    private fun balancesOf(s: SampleData): List<Long> {
        val out = s.txns.groupBy { it.accountId to it.type }.map { (k, v) -> TypeSum(k.first, k.second, v.sumOf { it.amount }) }
        val into = s.txns.filter { it.toAccountId != null }.groupBy { it.toAccountId!! }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val b = balances(s.accounts.withIndex().associate { (i, a) -> i.toLong() to a.openingBalance }, out, into)
        return s.accounts.indices.map { b.getValue(it.toLong()) }
    }

    @Test fun accountsShowTheDesignBalances() {
        assertEquals(listOf(142_300_00L, -8_120_00L, 1_500_00L, 850_00L, 16_600_00L, -21_40_560_00L, 2_00_000_00L), balancesOf(build()))
    }

    @Test fun sameDataEveryTime() = assertEquals(build().txns, build().txns)

    @Test fun nothingInTheFuture() = assertTrue(build().txns.all { it.at <= now })

    @Test fun todayHasTheTwoDesignPayments() {
        val start = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(listOf("Uber", "Third Wave Coffee"), build().txns.filter { it.at >= start }.map { it.payee })
    }

    @Test fun otherCurrenciesAreScaled() {
        val usd = build("USD")
        assertEquals(1_500_00L, usd.txns.first { it.payee == "ACME Pvt Ltd" }.amount)
        assertEquals(375_00L, usd.budget.amount)
    }
}
