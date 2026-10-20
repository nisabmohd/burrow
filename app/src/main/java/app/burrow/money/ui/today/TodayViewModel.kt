package app.burrow.money.ui.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.data.CaptureRepository
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.GoalLine
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.data.OccurrenceView
import app.burrow.money.data.PlanRepository
import app.burrow.money.data.SettingsRepository
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.DayRange
import app.burrow.money.domain.goalPerMonth
import app.burrow.money.domain.perDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The budget block: [budget] is null when none is set. */
@Immutable
data class HeroState(val period: BudgetPeriod, val daysLeft: Int, val spent: Long, val budget: Long?, val digits: Int = 2) {
    val left get() = (budget ?: 0) - spent
    val over get() = budget != null && left < 0
    val perDay get() = perDay(left, daysLeft, digits)
    val progress get() = if (budget == null || budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f)
}

/** Today's widgets (05): each null when there is nothing to show. */
@Immutable
data class Widgets(
    val week: List<Pair<LocalDate, Long>>,
    val nextBill: OccurrenceView?,
    val netWorth: Long,
    val netWorthLine: List<Long>,
    val netWorthChange: Long,
    val topCategory: CategoryEntity?,
    val topShare: Int,
    val categoryParts: List<Pair<Float, Long>>,
    val goal: GoalLine?,
    val goalPerMonth: Long?,
    val cameIn: Long,
    val wentOut: Long,
)

@Immutable
data class TodayUiState(
    val hero: HeroState,
    val recent: List<TxnView>,
    val hasSamples: Boolean,
    /** No transactions at all: Today shows only the empty state. */
    val isEmpty: Boolean,
    val toReview: Int,
    val smsCapture: Boolean,
    val appCapture: Boolean,
    val shown: Set<String>,
    val widgets: Widgets,
)

/** Today (04–10): the budget hero, notes, widgets, Recent. */
class TodayViewModel(
    private val repo: MoneyRepository,
    capture: CaptureRepository,
    private val settings: SettingsRepository,
    private val plan: PlanRepository,
    monthStartDay: Int,
    private val digits: Int,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val period = BudgetPeriod.containing(today, monthStartDay)
    private fun Long.day(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    private val widgets = combine(
        repo.latest(),
        combine(repo.accounts, repo.goalsSaved) { a, g -> a.sumOf { it.balance } + g },
        plan.occurrences(today.minusDays(62), today.plusDays(62)),
        plan.goals,
    ) { all, netWorth, occ, goals ->
        val spend = all.filter { it.txn.type == TxnType.EXPENSE && !it.txn.excludeFromStats }
        val week = DayRange.lastDays(7, today)
        val weekTotals = week.dates().map { d -> d to spend.filter { it.txn.at.day() == d }.sumOf { it.txn.amount } }
        val inPeriod = all.filter { val d = it.txn.at.day(); !d.isBefore(period.start) && d.isBefore(period.endExclusive) }
        val spentPeriod = inPeriod.filter { it.txn.type == TxnType.EXPENSE && !it.txn.excludeFromStats }
        val byCat = spentPeriod.filter { it.txn.categoryId != null }.groupBy { it.txn.categoryId!! }
            .map { (_, l) -> l.first() to l.sumOf { it.txn.amount } }.sortedByDescending { it.second }
        val total = byCat.sumOf { it.second }
        // Net worth only moves with money in and out; transfers move it between accounts and goals.
        fun effect(t: TxnView) = when (t.txn.type) {
            TxnType.INCOME, TxnType.ADJUST_UP -> t.txn.amount
            TxnType.EXPENSE, TxnType.ADJUST_DOWN -> -t.txn.amount
            else -> 0L
        }
        val points = (7 downTo 0).map { w ->
            val end = today.minusWeeks(w.toLong())
            netWorth - all.filter { it.txn.at.day().isAfter(end) }.sumOf(::effect)
        }
        val goal = goals.filter { !it.reached }.sortedWith(compareBy({ it.goal.targetDate == null }, { it.goal.targetDate })).firstOrNull()
        Widgets(
            week = weekTotals,
            nextBill = occ.filter { it.occurrence.status == OccurrenceStatus.UPCOMING }.minByOrNull { it.occurrence.dueDate },
            netWorth = netWorth,
            netWorthLine = points,
            netWorthChange = inPeriod.sumOf(::effect),
            topCategory = byCat.firstOrNull()?.first?.let { t -> CategoryEntity(t.txn.categoryId!!, t.categoryName.orEmpty(), kind = "EXPENSE", icon = t.categoryIcon.orEmpty(), colorHue = t.categoryHue ?: 0f) },
            topShare = if (total > 0) Math.round(byCat.first().second * 100f / total) else 0,
            categoryParts = byCat.take(4).map { (t, v) -> (t.categoryHue ?: 0f) to v },
            goal = goal,
            goalPerMonth = goal?.let { goalPerMonth(it.goal.target, it.saved, today, it.goal.targetDate?.day()) },
            cameIn = inPeriod.filter { it.txn.type == TxnType.INCOME }.sumOf { it.txn.amount },
            wentOut = spentPeriod.sumOf { it.txn.amount },
        )
    }.flowOn(Dispatchers.Default)

    private val money = combine(
        repo.spent(period),
        plan.budgets(period),
        repo.latest(RECENT),
        repo.hasSamples,
        repo.txnCount,
    ) { spent, (overall, _), recent, samples, count ->
        Triple(HeroState(period, period.daysLeft(today), spent, overall?.amount, digits), recent, samples to (count == 0))
    }

    val state: StateFlow<TodayUiState?> = combine(money, capture.reviewCount, settings.settings, widgets) { (hero, recent, flags), review, s, w ->
        TodayUiState(hero, recent, flags.first, flags.second, review, s.smsCapture, s.notificationCapture, s.homeWidgets, w)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun removeSamples() {
        viewModelScope.launch { repo.removeSamples() }
    }

    fun markPaid(occurrenceId: Long, onDone: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch { plan.markPaid(occurrenceId)?.let(onDone) }
    }

    fun setWidget(key: String, on: Boolean) {
        viewModelScope.launch { settings.setHomeWidget(key, on) }
    }

    private companion object {
        const val RECENT = 4
    }
}
