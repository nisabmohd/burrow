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
data class HeroState(val period: BudgetPeriod, val daysLeft: Int, val spent: Long, val budget: Long?, val digits: Int = 2, val spentToday: Long = 0, val comingUp: Long = 0) {
    val left get() = (budget ?: 0) - spent
    val over get() = budget != null && left < 0
    val perDay get() = perDay(left, daysLeft, digits)
    /** The daily number (NEXT.md): same on Today, the home-screen widget and the Sunday review. */
    val safeToday get() = app.burrow.money.domain.safeToSpendToday(left, spentToday, daysLeft, digits, comingUp)
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
    // Customise › Today (80)
    val topCard: String,
    val order: List<String>,
    val showReview: Boolean,
    /** For the Balance top card: net worth now. */
    val balance: Long,
    val showTime: Boolean,
    /** "₹35,449 coming up this week · 3 payments · Netflix today" (04): this week's coming-up items. */
    val comingThisWeek: List<app.burrow.money.ui.plan.ComingItem> = emptyList(),
)

/** Today (04–10): the budget hero, notes, widgets, Recent. */
class TodayViewModel(
    private val repo: MoneyRepository,
    capture: CaptureRepository,
    private val settings: SettingsRepository,
    private val plan: PlanRepository,
    monthStartDay: Int,
    private val digits: Int,
    next: app.burrow.money.data.NextRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val period = BudgetPeriod.containing(today, monthStartDay)
    private fun Long.day(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    private val widgets = combine(
        next.stats, // splits count in their own categories, refunds lower spending
        repo.netWorth,
        plan.occurrences(today.minusDays(62), today.plusDays(62)),
        plan.goals,
    ) { everything, nw, occ, goals ->
        // Upcoming transactions haven't happened yet: they stay out of every figure.
        val all = everything.filter { it.txn.status == app.burrow.money.data.TxnStatus.POSTED }
        val netWorth = nw.total
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
            // Top three in colour plus the rest as one grey part (hue NaN), so the ring shows true shares.
            categoryParts = byCat.take(3).map { (t, v) -> (t.categoryHue ?: 0f) to v } +
                listOfNotNull(byCat.drop(3).sumOf { it.second }.takeIf { it > 0 }?.let { Float.NaN to it }),
            goal = goal,
            goalPerMonth = goal?.let { goalPerMonth(it.goal.target, it.saved, today, it.goal.targetDate?.day()) },
            cameIn = inPeriod.filter { it.txn.type == TxnType.INCOME }.sumOf { it.txn.amount },
            wentOut = spentPeriod.sumOf { it.txn.amount },
        )
    }.flowOn(Dispatchers.Default)

    /** Bills and future-dated payments (Plan › Coming up): the hero's daily number and the Today row. */
    private val coming = combine(plan.occurrences(today.minusDays(62), today.plusDays(63)), repo.upcoming) { o, t -> app.burrow.money.ui.plan.comingUp(o, t, today, zone) }

    private val money = combine(
        combine(repo.spent(period), next.spentToday(today), combine(coming, settings.settings) { c, s -> app.burrow.money.ui.plan.comingUpDue(c, period, s.upcomingLowersSafe) }) { a, b, c -> Triple(a, b, c) },
        plan.budgets(period),
        repo.latest(RECENT_MAX),
        repo.hasSamples,
        repo.txnCount,
    ) { (spent, spentToday, due), (overall, _), recent, samples, count ->
        Triple(HeroState(period, period.daysLeft(today), spent, overall?.amount, digits, spentToday, due), recent, samples to (count == 0))
    }

    val state: StateFlow<TodayUiState?> = combine(money, capture.reviewCount, settings.settings, widgets, coming) { (hero, recent, flags), review, s, w, c ->
        TodayUiState(
            hero, recent.filter { it.txn.status == app.burrow.money.data.TxnStatus.POSTED }.take(s.homeRecentCount), flags.first, flags.second,
            review, s.smsCapture, s.notificationCapture, s.homeWidgets, w,
            s.homeTopCard, s.homeWidgetOrder, s.homeShowReview, w.netWorth, s.showTime,
            c.filter { it.group == app.burrow.money.ui.plan.ComingGroup.OVERDUE || it.group == app.burrow.money.ui.plan.ComingGroup.THIS_WEEK },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun removeSamples() {
        viewModelScope.launch { repo.removeSamples() }
    }

    fun markPaid(occurrenceId: Long, onDone: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch { plan.markPaid(occurrenceId)?.let(onDone) }
    }



    private companion object {
        /** Enough rows to fill the largest Recent choice (10) after upcoming ones are left out. */
        const val RECENT_MAX = 20
    }
}
