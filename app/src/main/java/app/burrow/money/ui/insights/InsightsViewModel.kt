package app.burrow.money.ui.insights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.DayRange
import app.burrow.money.domain.byWeekday
import app.burrow.money.domain.cumulative
import app.burrow.money.domain.keptPercent
import app.burrow.money.domain.noSpendDays
import app.burrow.money.ui.filters.FilterStore
import app.burrow.money.ui.filters.Filters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Immutable
data class CategoryShare(val category: CategoryEntity, val total: Long, val percent: Int)

@Immutable
/** Top payee (38) and the next two after it, with their totals ("Blinkit ₹2,110"). */
data class TopPayee(val name: String, val total: Long, val times: Int, val next: List<Pair<String, Long>>)

@Immutable
data class InsightsState(
    val filters: Filters,
    val accounts: List<AccountWithBalance>,
    val categories: List<CategoryEntity>,
    val range: DayRange,
    val spent: Long,
    val previousRange: DayRange,
    val previousSpent: Long,
    val line: List<Long>,
    val previousLine: List<Long>,
    val perDay: Long,
    val daily: List<Long>,
    val biggest: TxnView?,
    val byCategory: List<CategoryShare>,
    val busiest: DayOfWeek?,
    val busiestTotal: Long,
    val weekdays: List<Long>,
    val noSpend: Int,
    val days: Int,
    val income: Long,
    val kept: Int?,
    val topPayee: TopPayee?,
    val heatMonth: YearMonth,
    val heat: Map<LocalDate, Long>,
    val monthTotal: Long,
) {
    val isEmpty get() = spent == 0L
}

private val zone = ZoneId.systemDefault()
internal fun TxnView.day(): LocalDate = Instant.ofEpochMilli(txn.at).atZone(zone).toLocalDate()

/** Payments dated in [from, to) (epoch ms), splits expanded: NextRepository.statsBetween. */
typealias StatsBetween = (from: Long, to: Long) -> Flow<List<TxnView>>

/** The days from the first to the last of [ranges], as [from, to) in epoch ms. */
private fun window(vararg ranges: DayRange): Pair<Long, Long> {
    val start = ranges.minOf { it.start }
    val end = ranges.maxOf { it.end }.plusDays(1)
    return start.atStartOfDay(zone).toInstant().toEpochMilli() to end.atStartOfDay(zone).toInstant().toEpochMilli()
}
/** Spending that has happened: not a future-dated payment, not money lent (QA 12 Nov). */
internal fun TxnView.isSpending() = txn.type == TxnType.EXPENSE && !txn.excludeFromStats && txn.status == app.burrow.money.data.TxnStatus.POSTED

/** Income that has happened: borrowed money and future-dated income don't count. */
internal fun TxnView.isIncome() = txn.type == TxnType.INCOME && !txn.excludeFromStats && txn.status == app.burrow.money.data.TxnStatus.POSTED

/**
 * Insights (36–41): spending in the picked dates (this month by default) against the period before,
 * a day on average, the biggest payment, by category, patterns and the month heatmap. One page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(money: MoneyRepository, private val store: FilterStore, monthStartDay: Int, stats: StatsBetween) : ViewModel() {
    private val today = LocalDate.now(zone)
    private val defaultRange = DayRange(BudgetPeriod.containing(today, monthStartDay).start, today)

    /** The range, the one before it and the heatmap's month: all Insights shows. */
    private fun shown(f: Filters): Array<DayRange> {
        val range = f.range ?: defaultRange
        val month = YearMonth.from(range.end)
        return arrayOf(range, range.previous(), DayRange(month.atDay(1), month.atEndOfMonth()))
    }

    // Only the days on screen come from the database: loading every payment ever made took
    // seconds with 20,000 of them (2026-11-02).
    private val inWindow: Flow<Pair<Filters, List<TxnView>>> = store.insights.flatMapLatest { f ->
        val (from, to) = window(*shown(f))
        stats(from, to).map { f to it }
    }

    val state: StateFlow<InsightsState?> = combine(inWindow, money.accounts, money.categories) { (f, all), accounts, categories ->
        val range = f.range ?: defaultRange
        val prev = range.previous()
        val matching = all.filter { f.copy(range = null).matches(it, zone, withRange = false) }
        val spend = matching.filter { it.isSpending() }
        val now = spend.filter { it.day() in range }
        val before = spend.filter { it.day() in prev }
        val perDay = now.groupBy { it.day() }.mapValues { (_, l) -> l.sumOf { it.txn.amount } }
        val prevPerDay = before.groupBy { it.day() }.mapValues { (_, l) -> l.sumOf { it.txn.amount } }
        val spent = perDay.values.sum()
        val elapsed = range.dates().filter { !it.isAfter(today) }
        val byCat = now.filter { it.txn.categoryId != null }.groupBy { it.txn.categoryId!! }
            .mapNotNull { (id, l) -> categories.firstOrNull { it.id == id }?.let { c -> c to l.sumOf { it.txn.amount } } }
            .sortedByDescending { it.second }
            .map { (c, t) -> CategoryShare(c, t, if (spent > 0) Math.round(t * 100f / spent) else 0) }
        val weekdays = byWeekday(perDay, range)
        val busiest = weekdays.maxByOrNull { it.value }?.takeIf { it.value > 0 }
        val (noSpend, days) = noSpendDays(perDay, range, today)
        val income = matching.filter { it.isIncome() && it.day() in range }.sumOf { it.txn.amount }
        val payees = now.filter { it.txn.payee != null }.groupBy { it.txn.payee!! }.map { (n, l) -> Triple(n, l.sumOf { it.txn.amount }, l.size) }.sortedByDescending { it.second }
        val payee = payees.firstOrNull()
        val heatMonth = YearMonth.from(range.end)
        val heatRange = DayRange(heatMonth.atDay(1), heatMonth.atEndOfMonth())
        val heat = spend.filter { it.day() in heatRange }.groupBy { it.day() }.mapValues { (_, l) -> l.sumOf { it.txn.amount } }
        InsightsState(
            filters = f, accounts = accounts, categories = categories, range = range, spent = spent,
            previousRange = prev, previousSpent = prevPerDay.values.sum(),
            line = cumulative(DayRange(range.start, minOf(range.end, today)), perDay),
            previousLine = cumulative(prev, prevPerDay),
            perDay = if (elapsed.isEmpty()) 0 else spent / elapsed.size,
            daily = elapsed.map { perDay[it] ?: 0 },
            biggest = now.maxByOrNull { it.txn.amount },
            byCategory = byCat,
            busiest = busiest?.key, busiestTotal = busiest?.value ?: 0,
            weekdays = DayOfWeek.entries.map { weekdays[it] ?: 0 },
            noSpend = noSpend, days = days,
            income = income, kept = keptPercent(income, spent),
            topPayee = payee?.let { (name, total, times) -> TopPayee(name, total, times, payees.drop(1).take(2).map { it.first to it.second }) },
            heatMonth = heatMonth, heat = heat, monthTotal = heat.values.sum(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFilters(f: Filters) {
        store.insights.value = f
    }

    /** Heatmap tap: Activity, just that day. */
    fun showDayInActivity(day: LocalDate) {
        store.activity.value = Filters(range = DayRange(day, day))
    }
}

@Immutable
data class CategoryInsightsState(
    val category: CategoryEntity,
    val range: DayRange,
    val total: Long,
    val previousTotal: Long,
    val bars: List<Pair<LocalDate, Long>>,
    val txns: List<TxnView>,
)

/** Category insights (42): one category in the picked dates, by day, and its transactions. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryInsightsViewModel(money: MoneyRepository, private val store: FilterStore, monthStartDay: Int, val categoryId: Long, stats: StatsBetween) : ViewModel() {
    /** The calendar button: the same range as Insights (one Dates sheet, one range). */
    fun setRange(r: DayRange?) { store.insights.value = store.insights.value.copy(range = r) }

    private val today = LocalDate.now(zone)
    private val defaultRange = DayRange(BudgetPeriod.containing(today, monthStartDay).start, today)

    private val inWindow: Flow<Pair<Filters, List<TxnView>>> = store.insights.flatMapLatest { f ->
        val range = f.range ?: defaultRange
        val (from, to) = window(range, range.previous())
        stats(from, to).map { f to it }
    }

    val state: StateFlow<CategoryInsightsState?> = combine(inWindow, money.categories) { (f, all), cats ->
        val cat = cats.firstOrNull { it.id == categoryId } ?: return@combine null
        val range = f.range ?: defaultRange
        val mine = all.filter { it.isSpending() && it.txn.categoryId == categoryId && f.copy(categories = emptySet(), range = null).matches(it, zone, withRange = false) }
        val now = mine.filter { it.day() in range }
        val perDay = now.groupBy { it.day() }.mapValues { (_, l) -> l.sumOf { it.txn.amount } }
        CategoryInsightsState(
            cat, range, perDay.values.sum(), mine.filter { it.day() in range.previous() }.sumOf { it.txn.amount },
            app.burrow.money.domain.bars(range, perDay), now,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
