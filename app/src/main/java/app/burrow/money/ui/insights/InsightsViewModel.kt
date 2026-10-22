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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Immutable
data class CategoryShare(val category: CategoryEntity, val total: Long, val percent: Int)

@Immutable
data class TopPayee(val name: String, val total: Long, val times: Int, val circles: List<Pair<String?, Float?>>)

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
internal fun TxnView.isSpending() = txn.type == TxnType.EXPENSE && !txn.excludeFromStats

/**
 * Insights (36–41): spending in the picked dates (this month by default) against the period before,
 * a day on average, the biggest payment, by category, patterns and the month heatmap. One page.
 */
class InsightsViewModel(money: MoneyRepository, private val store: FilterStore, monthStartDay: Int, stats: kotlinx.coroutines.flow.Flow<List<TxnView>> = money.latest()) : ViewModel() {
    private val today = LocalDate.now(zone)
    private val defaultRange = DayRange(BudgetPeriod.containing(today, monthStartDay).start, today)

    val state: StateFlow<InsightsState?> = combine(stats, store.insights, money.accounts, money.categories) { all, f, accounts, categories ->
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
        val income = matching.filter { it.txn.type == TxnType.INCOME && it.day() in range }.sumOf { it.txn.amount }
        val payee = now.filter { it.txn.payee != null }.groupBy { it.txn.payee!! }.maxByOrNull { (_, l) -> l.sumOf { it.txn.amount } }
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
            topPayee = payee?.let { (name, l) -> TopPayee(name, l.sumOf { it.txn.amount }, l.size, l.take(3).map { it.categoryIcon to it.categoryHue }) },
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
class CategoryInsightsViewModel(money: MoneyRepository, store: FilterStore, monthStartDay: Int, val categoryId: Long, stats: kotlinx.coroutines.flow.Flow<List<TxnView>> = money.latest()) : ViewModel() {
    private val today = LocalDate.now(zone)
    private val defaultRange = DayRange(BudgetPeriod.containing(today, monthStartDay).start, today)

    val state: StateFlow<CategoryInsightsState?> = combine(stats, store.insights, money.categories) { all, f, cats ->
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
