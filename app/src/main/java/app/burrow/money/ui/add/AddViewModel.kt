package app.burrow.money.ui.add

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.TxnEntity
import app.burrow.money.data.TxnType
import app.burrow.money.domain.AmountInput
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.MoneyFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the user has entered so far. */
@Immutable
data class AddForm(
    val type: String = TxnType.EXPENSE,
    val input: AmountInput = AmountInput(),
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val at: Long = System.currentTimeMillis(),
    val payee: String = "",
    val note: String = "",
    /** Save was tapped too early: the missing row turns red. */
    val tried: Boolean = false,
)

@Immutable
data class AddUiState(
    val form: AddForm,
    val isEdit: Boolean,
    val categories: List<CategoryEntity>,
    val accounts: List<AccountWithBalance>,
    val spentByCategory: Map<Long, Long>,
) {
    val category get() = categories.firstOrNull { it.id == form.categoryId }
    val account get() = accounts.firstOrNull { it.account.id == form.accountId }
    val toAccount get() = accounts.firstOrNull { it.account.id == form.toAccountId }
    val amount get() = form.input.minor()
    val missingCategory get() = form.type != TxnType.TRANSFER && category == null
    val missingToAccount get() = form.type == TxnType.TRANSFER && (toAccount == null || form.toAccountId == form.accountId)
    val canSave get() = amount > 0 && account != null && !missingCategory && !missingToAccount

    /** Categories that fit the type: expense ones for expenses, income ones for income. */
    val categoriesForType get() = categories.filter { it.kind == if (form.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }

    /** Where money can come from or go to; loans and deposits only take transfers. */
    val accountsForType get() = if (form.type == TxnType.TRANSFER) accounts else accounts.filter { it.account.type != AccountType.LOAN && it.account.type != AccountType.SAVINGS }
}

/** A finished save: what the snackbar says and how Undo puts things back. */
class Saved(val type: String, val wasEdit: Boolean, val undo: suspend () -> Unit)

/**
 * New, edit or duplicate a transaction (design 17, 18). [editId] edits; [duplicateId] copies one
 * with today's date; otherwise a new one of [startType].
 */
class AddViewModel(
    private val repo: MoneyRepository,
    private val editId: Long,
    private val duplicateId: Long,
    startType: String,
    private val monthStartDay: Int,
    private val currency: String,
) : ViewModel() {
    private val form = MutableStateFlow<AddForm?>(null)
    private var original: TxnEntity? = null
    private val digits = MoneyFormat.of(currency).digits

    val state: StateFlow<AddUiState?> = form.flatMapLatest { f ->
        if (f == null) flowOf(null)
        else {
            val period = BudgetPeriod.containing(LocalDate.now(), monthStartDay)
            combine(repo.categories, repo.accounts, repo.spentByCategory(period)) { cats, accs, spent ->
                AddUiState(f, editId != 0L, cats, accs, spent)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val accounts = repo.accounts.first()
            val source = (if (editId != 0L) editId else duplicateId).takeIf { it != 0L }?.let { repo.get(it) }
            original = source?.takeIf { editId != 0L }
            form.value = if (source != null) {
                AddForm(
                    type = source.type, input = AmountInput.of(source.amount, digits), categoryId = source.categoryId,
                    accountId = source.accountId, toAccountId = source.toAccountId,
                    at = if (editId != 0L) source.at else System.currentTimeMillis(),
                    payee = source.payee.orEmpty(), note = source.note.orEmpty(),
                )
            } else {
                val pay = accounts.filter { it.account.type != AccountType.LOAN && it.account.type != AccountType.SAVINGS }
                AddForm(
                    type = startType, input = AmountInput(digits = digits),
                    accountId = pay.firstOrNull()?.account?.id,
                    toAccountId = pay.getOrNull(1)?.account?.id,
                )
            }
        }
    }

    private fun edit(change: (AddForm) -> AddForm) = form.update { it?.let(change) }

    fun setType(type: String) = edit {
        // A category of the other kind no longer fits.
        val cat = state.value?.categories?.firstOrNull { c -> c.id == it.categoryId }
        val fits = cat != null && (cat.kind == CategoryKind.INCOME) == (type == TxnType.INCOME)
        it.copy(type = type, categoryId = if (fits) it.categoryId else null, tried = false)
    }

    fun setInput(input: AmountInput) = edit { it.copy(input = input) }
    fun evaluate() = edit { it.copy(input = it.input.evaluated()) }
    fun setCategory(id: Long) = edit { it.copy(categoryId = id) }
    fun setAccount(id: Long) = edit { it.copy(accountId = id) }
    fun setToAccount(id: Long) = edit { it.copy(toAccountId = id) }
    fun setPayee(text: String) = edit { it.copy(payee = text) }
    fun setNote(text: String) = edit { it.copy(note = text) }

    /** Keeps the time of day, changes the day. */
    fun setDate(date: LocalDate) = edit {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(it.at).atZone(zone).toLocalTime()
        it.copy(at = minOf(date.atTime(time).atZone(zone).toInstant().toEpochMilli(), System.currentTimeMillis()))
    }

    /** Saves, or marks what is missing. [onSaved] runs once the row is written. */
    fun save(onSaved: (Saved) -> Unit) {
        val s = state.value ?: return
        if (!s.canSave) {
            edit { it.copy(tried = true) }
            return
        }
        val f = s.form
        val now = System.currentTimeMillis()
        val account = s.account!!.account
        val base = original
        val txn = TxnEntity(
            id = base?.id ?: 0,
            type = f.type,
            amount = s.amount,
            currency = account.currency,
            baseAmount = s.amount,
            accountId = account.id,
            toAccountId = if (f.type == TxnType.TRANSFER) f.toAccountId else null,
            categoryId = if (f.type == TxnType.TRANSFER) null else f.categoryId,
            payee = f.payee.ifBlank { null },
            note = f.note.ifBlank { null },
            at = f.at,
            source = base?.source ?: "MANUAL",
            isSample = base?.isSample ?: false,
            createdAt = base?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            val id = repo.save(txn)
            onSaved(
                Saved(f.type, base != null) {
                    if (base != null) repo.restore(base) else repo.delete(id)
                },
            )
        }
    }
}
