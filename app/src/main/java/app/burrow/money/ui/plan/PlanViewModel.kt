package app.burrow.money.ui.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.data.BillKind
import app.burrow.money.data.BudgetLine
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.GoalLine
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.data.OccurrenceView
import app.burrow.money.data.PlanRepository
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.Repeat
import app.burrow.money.domain.perDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class PlanSection { BILLS, BUDGETS, GOALS }

/** Plan › Coming up (22, 113): bills and future-dated payments in one list, and "Every month". */
@Immutable
data class BillsState(
    val items: List<ComingItem>,
    val subsMonthly: Long,
    val subsCount: Int,
    val emiMonthly: Long,
    val loanAccountId: Long?,
    val hasBills: Boolean,
    /** EMIs paid of all, of the first loan ("41 of 240 paid"). */
    val emisPaid: Int,
    val emisTotal: Int,
) {
    val isEmpty get() = items.isEmpty() && !hasBills
}

@Immutable
data class BudgetsState(
    val period: BudgetPeriod,
    val daysLeft: Int,
    val overall: BudgetLine?,
    val lines: List<BudgetLine>,
    val perDay: Long,
    /** Expense categories, for adding a category budget. */
    val categories: List<CategoryEntity>,
)

@Immutable
data class GoalsState(val goals: List<GoalLine>) {
    val saved get() = goals.sumOf { it.saved.coerceAtMost(it.goal.target) }
    val target get() = goals.sumOf { it.goal.target }
}

@Immutable
data class PlanUiState(val bills: BillsState, val budgets: BudgetsState, val goals: GoalsState)

/** The Plan tab (22, 23, 24). */
class PlanViewModel(private val plan: PlanRepository, private val money: MoneyRepository, monthStartDay: Int, digits: Int) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val period = BudgetPeriod.containing(today, monthStartDay)

    private fun OccurrenceView.date(): LocalDate = Instant.ofEpochMilli(occurrence.dueDate).atZone(zone).toLocalDate()

    val state: StateFlow<PlanUiState?> = combine(
        plan.occurrences(today.minusDays(62), today.plusDays(63)),
        plan.bills,
        plan.budgets(period),
        plan.goals,
        combine(money.categories, money.accounts, money.upcoming) { c, a, l -> Triple(c, a, l) },
    ) { occ, bills, (overall, cats), goals, (categories, accounts, txns) ->
        val subs = bills.filter { it.kind == BillKind.SUBSCRIPTION }
        val monthly = { amount: Long, rule: String -> if (Repeat.parse(rule) is Repeat.Yearly) amount / 12 else amount }
        val emis = bills.filter { it.kind == BillKind.EMI }
        val left = overall?.left ?: 0
        val loanAccount = emis.firstNotNullOfOrNull { it.toAccountId }?.let { id -> accounts.firstOrNull { it.account.id == id } }
        val loan = loanAccount?.let { app.burrow.money.ui.accounts.loanInfo(it.account, it.balance, today) }
        PlanUiState(
            bills = BillsState(
                items = comingUp(occ, txns, today, zone),
                subsMonthly = subs.sumOf { monthly(it.amount, it.repeatRule) },
                subsCount = subs.size,
                emiMonthly = emis.sumOf { it.amount },
                loanAccountId = emis.firstNotNullOfOrNull { it.toAccountId },
                hasBills = bills.isNotEmpty(),
                emisPaid = loan?.paid ?: 0,
                emisTotal = loan?.total ?: 0,
            ),
            budgets = BudgetsState(period, period.daysLeft(today), overall, cats, perDay(left, period.daysLeft(today), digits), categories.filter { it.kind == CategoryKind.EXPENSE }),
            goals = GoalsState(goals),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markPaid(occurrenceId: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        plan.markPaid(occurrenceId)?.let(onDone)
    }

    fun skip(occurrenceId: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        plan.skip(occurrenceId)?.let(onDone)
    }

    /** A one-off upcoming payment, added now (swipe right, 114 "Add now"). */
    fun addNow(txnId: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        money.addNow(txnId)?.let(onDone)
    }

    /** A one-off upcoming payment, deleted (swipe left), with Undo. */
    fun deleteOnce(txnId: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        money.delete(txnId)?.let { gone -> onDone { money.restore(gone) } }
    }

    fun setBudget(categoryId: Long?, amount: Long, carryOver: Boolean, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        onDone(plan.setBudget(period, categoryId, amount, carryOver))
    }
}
