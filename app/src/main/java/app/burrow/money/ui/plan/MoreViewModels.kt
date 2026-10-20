package app.burrow.money.ui.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.BillEntity
import app.burrow.money.data.BillKind
import app.burrow.money.data.BudgetLine
import app.burrow.money.data.GoalEntity
import app.burrow.money.data.GoalLine
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.data.PlanRepository
import app.burrow.money.data.SubscriptionHint
import app.burrow.money.data.TxnEntity
import app.burrow.money.data.TxnView
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.Repeat
import app.burrow.money.data.endMillis
import app.burrow.money.data.startMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Immutable
data class SubRow(val bill: BillEntity, val next: LocalDate?, val icon: String?, val hue: Float?)

@Immutable
data class SubscriptionsState(val rows: List<SubRow>, val monthly: Long, val yearly: Long, val hint: SubscriptionHint?)

/** Subscriptions (29): every subscription, what they cost a month and a year, and a hint. */
class SubscriptionsViewModel(plan: PlanRepository, money: MoneyRepository) : ViewModel() {
    private val hint = MutableStateFlow<SubscriptionHint?>(null)
    val state: StateFlow<SubscriptionsState?> = combine(plan.bills, plan.occurrences(LocalDate.now(), LocalDate.now().plusDays(63)), money.categories, hint) { bills, occ, cats, h ->
        val subs = bills.filter { it.kind == BillKind.SUBSCRIPTION }
        val rows = subs.map { b ->
            val cat = cats.firstOrNull { it.id == b.categoryId }
            // The next unpaid date if one exists, else the rule's next date (yearly ones may be months away).
            val unpaid = occ.filter { it.occurrence.billId == b.id && it.occurrence.status == OccurrenceStatus.UPCOMING }.minOfOrNull { it.occurrence.dueDate }?.toDate()
            SubRow(b, unpaid ?: Repeat.parse(b.repeatRule)?.nextOnOrAfter(LocalDate.now()), b.icon ?: cat?.icon, b.colorHue ?: cat?.colorHue)
        }
        val monthly = subs.sumOf { if (Repeat.parse(it.repeatRule) is Repeat.Yearly) it.amount / 12 else it.amount }
        val yearly = subs.sumOf { if (Repeat.parse(it.repeatRule) is Repeat.Yearly) it.amount else it.amount * 12 }
        SubscriptionsState(rows, monthly, yearly, h)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { hint.value = plan.subscriptionHint() }
    }
}

@Immutable
data class BudgetDetailState(
    val period: BudgetPeriod,
    val daysLeft: Int,
    /** The overall budget, or the one category's. */
    val line: BudgetLine?,
    /** Overall: every category budget. */
    val categories: List<BudgetLine>,
    /** One category: its transactions this period. */
    val txns: List<TxnView>,
)

/** Budget (30) and a category's budget (31). [categoryId] is null for the overall budget. */
class BudgetViewModel(private val plan: PlanRepository, money: MoneyRepository, val categoryId: Long?, monthStartDay: Int) : ViewModel() {
    private val today = LocalDate.now()
    private val period = BudgetPeriod.containing(today, monthStartDay)
    val state: StateFlow<BudgetDetailState?> = combine(
        plan.budgets(period),
        if (categoryId != null) money.between(period.startMillis(), period.endMillis()) else flowOf(emptyList()),
    ) { (overall, cats), txns ->
        val line = if (categoryId == null) overall else cats.firstOrNull { it.category?.id == categoryId }
        BudgetDetailState(period, period.daysLeft(today), line, cats, txns.filter { it.txn.categoryId == categoryId && it.txn.type == "EXPENSE" })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(amount: Long, carry: Boolean, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        onDone(plan.setBudget(period, categoryId, amount, carry))
    }
}

@Immutable
data class GoalState(val line: GoalLine, val added: List<TxnEntity>, val accounts: List<AccountWithBalance>)

/** A goal (33, 34) and Add money (35). */
class GoalViewModel(private val plan: PlanRepository, money: MoneyRepository, val id: Long) : ViewModel() {
    val state: StateFlow<GoalState?> = combine(plan.goal(id), plan.added(id), money.accounts) { g, added, accs ->
        g?.let { GoalState(it, added, accs) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addMoney(from: Long, amount: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        onDone(plan.addMoney(id, from, amount))
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        plan.deleteGoal(id)
        onDone()
    }
}

@Immutable
data class GoalForm(val name: String = "", val target: String = "", val by: LocalDate? = null, val icon: String = "flag", val hue: Float = 345f, val tried: Boolean = false)

/** New goal / edit goal: name, target, an optional date, and its circle. */
class NewGoalViewModel(private val plan: PlanRepository, private val money: MoneyRepository, private val editId: Long, private val digits: Int) : ViewModel() {
    val form = MutableStateFlow<GoalForm?>(null)
    private var original: GoalEntity? = null
    private val zone = ZoneId.systemDefault()

    init {
        viewModelScope.launch {
            val g = if (editId != 0L) plan.goal(editId).first()?.goal else null
            original = g
            form.value = g?.let {
                GoalForm(it.name, java.math.BigDecimal.valueOf(it.target, digits).stripTrailingZeros().toPlainString(), it.targetDate?.toDate(), it.icon, it.colorHue)
            } ?: GoalForm()
        }
    }

    fun edit(change: (GoalForm) -> GoalForm) = form.update { it?.let(change) }

    fun canSave(f: GoalForm) = f.name.isNotBlank() && app.burrow.money.domain.TypedAmount.parse(f.target, digits) > 0

    fun save(onSaved: () -> Unit) {
        val f = form.value ?: return
        if (!canSave(f)) {
            edit { it.copy(tried = true) }
            return
        }
        viewModelScope.launch {
            val from = money.accounts.first().firstOrNull { it.account.type == AccountType.BANK }?.account?.id ?: money.accounts.first().first().account.id
            plan.saveGoal(
                (original ?: GoalEntity(name = "", target = 0, accountId = from, icon = f.icon, colorHue = f.hue)).copy(
                    name = f.name.trim(), target = app.burrow.money.domain.TypedAmount.parse(f.target, digits),
                    targetDate = f.by?.atTime(12, 0)?.atZone(zone)?.toInstant()?.toEpochMilli(), icon = f.icon, colorHue = f.hue, isSample = false,
                ),
            )
            onSaved()
        }
    }
}
