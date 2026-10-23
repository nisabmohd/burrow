package app.burrow.money.ui.activity

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.data.minorDigits
import app.burrow.money.domain.Bucket
import app.burrow.money.domain.DayRange
import app.burrow.money.domain.bars
import app.burrow.money.domain.bucketFor
import app.burrow.money.ui.filters.FilterStore
import app.burrow.money.ui.filters.Filters
import app.burrow.money.ui.filters.TypeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day in Activity: its header total (transfers left out) and its transactions. */
@Immutable
data class DayGroup(val date: LocalDate, val total: Long, val txns: List<TxnView>)

/** The chart card (11, 12): a bar per day (or week, or month), the average, and the tapped bar. */
@Immutable
data class ActivityChart(
    val range: DayRange,
    /** True when no dates are picked: the card says "Last 14 days". */
    val lastDays: Boolean,
    val bucket: Bucket,
    val bars: List<Pair<LocalDate, Long>>,
    val total: Long,
    val count: Int,
    val perDay: Long,
    /** Money in instead of spent (Type › Income). */
    val income: Boolean,
    val selected: Int?,
)

@Immutable
data class ActivityUiState(
    val query: String,
    val filters: Filters,
    val chart: ActivityChart,
    val groups: List<DayGroup>,
    val isEmpty: Boolean,
    val accounts: List<AccountWithBalance>,
    val categories: List<CategoryEntity>,
    /** Overdue and this week's coming-up items: the folded "3 coming up" row (11). */
    val comingThisWeek: List<app.burrow.money.ui.plan.ComingItem> = emptyList(),
)

/**
 * Activity (11–16): every transaction, newest first, grouped by day; search; the Filter and Dates
 * sheets; the chart card, where tapping a bar shows only that day. Work runs off the main thread.
 */
class ActivityViewModel(private val repo: MoneyRepository, private val store: FilterStore, plan: app.burrow.money.data.PlanRepository? = null) : ViewModel() {
    /** Swipe to delete (Customise › Transactions); Undo puts it back. */
    fun delete(id: Long, onDeleted: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch { repo.delete(id)?.let { t -> onDeleted { repo.restore(t) } } }
    }

    private val zone = ZoneId.systemDefault()
    private val query = MutableStateFlow("")
    val searchText: StateFlow<String> = query.asStateFlow()
    private val selected = MutableStateFlow<Int?>(null)
    @Volatile private var all: List<TxnView> = emptyList()

    private val coming = plan?.let { p ->
        val today = LocalDate.now(zone)
        combine(p.occurrences(today.minusDays(62), today.plusDays(63)), repo.upcoming) { o, t -> app.burrow.money.ui.plan.comingUp(o, t, today, zone) }
    } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    val state: StateFlow<ActivityUiState?> = combine(
        combine(repo.latest(), coming) { a, b -> a to b }, query, store.activity, selected, combine(repo.accounts, repo.categories) { a, c -> a to c },
    ) { (everything, comingUp), q, f, sel, (accounts, categories) ->
        // Upcoming payments live in Coming up; they never mix into the history.
        val txns = everything.filter { it.txn.status == app.burrow.money.data.TxnStatus.POSTED }
        all = txns
        val today = LocalDate.now(zone)
        val range = f.range ?: DayRange.lastDays(14, today)
        val income = f.type == TypeFilter.INCOME
        val chartType = if (income) TxnType.INCOME else TxnType.EXPENSE
        // The chart counts money out (or in), whatever the Type filter, within the other filters.
        // Refunds lower the spending of their day.
        val inChart = txns.filter { f.copy(type = TypeFilter.ALL).matches(it, zone, withRange = false) && (it.txn.type == chartType || (!income && it.txn.type == TxnType.REFUND)) && !it.txn.excludeFromStats && it.date() in range }
        val perDay = inChart.groupBy { it.date() }.mapValues { (_, l) -> l.sumOf { if (it.txn.type == TxnType.REFUND) -it.txn.amount else it.txn.amount }.coerceAtLeast(0) }
        val chartBars = bars(range, perDay)
        val bucket = bucketFor(range)
        val total = perDay.values.sum()
        val elapsed = range.dates().count { !it.isAfter(today) }.coerceAtLeast(1)
        val selDay = sel?.let { chartBars.getOrNull(it) }?.takeIf { bucket == Bucket.DAY }?.first
        val hits = txns.filter { t ->
            f.matches(t, zone) && (selDay == null || t.date() == selDay) && (q.isBlank() || t.matches(q.trim()))
        }
        val groups = hits.groupBy { it.date() }.map { (date, list) -> DayGroup(date, list.sumOf { it.signedAmount() }, list) }
        val unit = unit(accounts.firstOrNull()?.account?.currency ?: "INR")
        ActivityUiState(
            query = q,
            filters = f,
            chart = ActivityChart(
                range, f.range == null, bucket, chartBars,
                if (selDay != null) perDay[selDay] ?: 0 else total,
                if (selDay != null) inChart.count { it.date() == selDay } else inChart.size,
                (total / elapsed + unit / 2) / unit * unit,
                income, if (bucket == Bucket.DAY) sel else null,
            ),
            groups = groups,
            isEmpty = txns.isEmpty(),
            accounts = accounts,
            categories = categories,
            comingThisWeek = comingUp.filter { it.group == app.burrow.money.ui.plan.ComingGroup.OVERDUE || it.group == app.burrow.money.ui.plan.ComingGroup.THIS_WEEK },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun TxnView.date(): LocalDate = Instant.ofEpochMilli(txn.at).atZone(zone).toLocalDate()

    fun search(text: String) {
        query.value = text
    }

    fun setFilters(f: Filters) {
        selected.value = null
        store.activity.value = f
    }

    /** Tap a bar: just that day; tap it again (or All days): every day. */
    fun select(index: Int?) {
        selected.value = if (selected.value == index) null else index
    }

    /** How many transactions [f] would show, for the sheet's button. */
    fun count(f: Filters): Int = all.count { f.matches(it, zone) }
}

private fun unit(currency: String): Long {
    var u = 1L
    repeat(minorDigits(currency)) { u *= 10 }
    return u
}

/** Money out is negative, in is positive; a transfer moves money without changing the total. */
fun TxnView.signedAmount(): Long = when (txn.type) {
    TxnType.EXPENSE -> -txn.amount
    TxnType.INCOME, TxnType.REFUND -> txn.amount
    else -> 0
}

/** Payee, note, category, account, or the amount's digits ("240" finds ₹240 and ₹1,240). */
private fun TxnView.matches(q: String): Boolean {
    val text = listOfNotNull(txn.payee, txn.note, categoryName, accountName, toAccountName, goalName)
    if (text.any { it.contains(q, ignoreCase = true) }) return true
    val digits = q.filter { it.isDigit() }
    return digits.isNotEmpty() && digits.length == q.count { it.isDigit() || it == ',' || it == '.' || it == '₹' } &&
        (txn.amount / unit(txn.currency)).toString().contains(digits)
}
