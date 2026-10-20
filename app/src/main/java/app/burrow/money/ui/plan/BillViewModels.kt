package app.burrow.money.ui.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.BillEntity
import app.burrow.money.data.BillKind
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.data.OccurrenceView
import app.burrow.money.data.OnDue
import app.burrow.money.data.PlanRepository
import app.burrow.money.domain.Repeat
import app.burrow.money.domain.TypedAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Immutable
data class BillState(
    val bill: BillEntity,
    /** The due date to act on: the oldest unpaid one (overdue first), else the next. */
    val current: OccurrenceView?,
    val paidBefore: List<OccurrenceView>,
    val category: CategoryEntity?,
    val account: AccountWithBalance?,
) {
    val overdue get() = current != null && current.occurrence.dueDate.toDate().isBefore(LocalDate.now())
}

internal fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** Bill detail (25, 26). */
class BillViewModel(private val plan: PlanRepository, money: MoneyRepository, val id: Long) : ViewModel() {
    val state: StateFlow<BillState?> = combine(plan.bill(id), plan.occurrencesOf(id), money.categories, money.accounts) { b, occ, cats, accs ->
        b ?: return@combine null
        val unpaid = occ.filter { it.occurrence.status == OccurrenceStatus.UPCOMING }.minByOrNull { it.occurrence.dueDate }
        BillState(
            bill = b,
            current = unpaid,
            paidBefore = occ.filter { it.occurrence.status == OccurrenceStatus.PAID },
            category = cats.firstOrNull { it.id == b.categoryId },
            account = accs.firstOrNull { it.account.id == b.accountId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markPaid(onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        state.value?.current?.let { o -> plan.markPaid(o.occurrence.id)?.let(onDone) }
    }

    fun skip(onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        state.value?.current?.let { o -> plan.skip(o.occurrence.id)?.let(onDone) }
    }

    fun setReminders(on: Boolean) = viewModelScope.launch {
        state.value?.bill?.let { plan.saveBill(it.copy(remindersOn = on)) }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        plan.deleteBill(id)
        onDone()
    }
}

@Immutable
data class BillForm(
    val kind: String = BillKind.BILL,
    val name: String = "",
    val amount: String = "",
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val repeat: Repeat = Repeat.Monthly(1),
    val onDue: String = OnDue.ASK,
    val tried: Boolean = false,
)

@Immutable
data class NewBillState(val form: BillForm, val isEdit: Boolean, val categories: List<CategoryEntity>, val accounts: List<AccountWithBalance>) {
    val category get() = categories.firstOrNull { it.id == form.categoryId }
    val account get() = accounts.firstOrNull { it.account.id == form.accountId }
    val canSave get() = form.name.isNotBlank() && amountMinor() > 0 && account != null
    fun amountMinor(digits: Int = 2): Long = TypedAmount.parse(form.amount, digits)
}

/** New bill / edit bill (27, 28). Prefilled from a subscription hint when [prefill] is given. */
class NewBillViewModel(
    private val plan: PlanRepository,
    money: MoneyRepository,
    private val editId: Long,
    prefill: BillForm?,
    private val digits: Int,
) : ViewModel() {
    private val form = MutableStateFlow<BillForm?>(null)
    private var original: BillEntity? = null

    val state: StateFlow<NewBillState?> = combine(form, money.categories, money.accounts) { f, cats, accs ->
        f ?: return@combine null
        NewBillState(f, editId != 0L, cats.filter { it.kind == CategoryKind.EXPENSE }, accs.filter { it.account.type != AccountType.LOAN && it.account.type != AccountType.SAVINGS })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val b = if (editId != 0L) plan.bill(editId).first() else null
            original = b
            val accounts = money.accounts.first()
            val cats = money.categories.first()
            form.value = when {
                b != null -> BillForm(
                    b.kind, b.name, java.math.BigDecimal.valueOf(b.amount, digits).stripTrailingZeros().toPlainString(), b.categoryId, b.accountId,
                    Repeat.parse(b.repeatRule) ?: Repeat.Monthly(1), b.onDue,
                )
                prefill != null -> prefill.copy(accountId = prefill.accountId ?: accounts.firstOrNull()?.account?.id)
                else -> BillForm(
                    categoryId = cats.firstOrNull { it.icon == "bolt" }?.id,
                    accountId = accounts.firstOrNull { it.account.type == AccountType.BANK }?.account?.id ?: accounts.firstOrNull()?.account?.id,
                )
            }
        }
    }

    fun edit(change: (BillForm) -> BillForm) = form.update { it?.let(change) }

    fun save(onSaved: () -> Unit) {
        val s = state.value ?: return
        if (!s.canSave) {
            edit { it.copy(tried = true) }
            return
        }
        val f = s.form
        val base = original
        val today = LocalDate.now()
        viewModelScope.launch {
            plan.saveBill(
                (base ?: BillEntity(kind = f.kind, name = "", amount = 0, accountId = 0, categoryId = null, repeatRule = "", startDate = 0)).copy(
                    kind = f.kind, name = f.name.trim(), amount = s.amountMinor(digits), accountId = f.accountId!!, categoryId = f.categoryId,
                    repeatRule = f.repeat.encode(), onDue = f.onDue,
                    startDate = base?.startDate ?: today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    isSample = false,
                ),
            )
            onSaved()
        }
    }
}
