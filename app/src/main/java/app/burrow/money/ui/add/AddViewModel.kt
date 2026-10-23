package app.burrow.money.ui.add

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.AppContainer
import app.burrow.money.notify.runBillReminders
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.AttachmentEntity
import app.burrow.money.data.Attachments
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.capture.Direction
import app.burrow.money.data.FormExtra
import app.burrow.money.data.Lend
import app.burrow.money.data.TxnEntity
import app.burrow.money.data.TxnStatus
import app.burrow.money.data.TxnType
import app.burrow.money.data.tidyPayee
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
import java.time.LocalTime
import java.time.ZoneId

/** Repeat on a new expense (75): never, or a bill every week / month / year. */
object Every { const val NEVER = "NEVER"; const val WEEK = "WEEK"; const val MONTH = "MONTH"; const val YEAR = "YEAR" }

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
    // Extras (74–77)
    val tags: List<String> = emptyList(),
    val repeat: String = Every.NEVER,
    val subscription: Boolean = false,
    val repeatRemind: Boolean = true,
    val attachments: List<Attachments.Pending> = emptyList(),
    val kept: List<AttachmentEntity> = emptyList(),
    val removed: List<AttachmentEntity> = emptyList(),
    val lend: String? = null,
    val person: String = "",
    val settleRemind: Boolean = true,
    /** The Favourite chip (04): also keep this as a one-tap favourite. */
    val favourite: Boolean = false,
)

/** A Paid to suggestion (87): a payee and the category it usually goes to. */
@Immutable
data class PayeeSuggestion(val name: String, val icon: String?, val hue: Float?, val categoryId: Long?)

@Immutable
data class AddUiState(
    val form: AddForm,
    val isEdit: Boolean,
    val categories: List<CategoryEntity>,
    val accounts: List<AccountWithBalance>,
    val spentByCategory: Map<Long, Long>,
    /** Customise › Expense: which extras show. */
    val extras: Set<String>,
    val suggestions: List<PayeeSuggestion>,
    val knownTags: List<String>,
    /** One-tap favourites (04), shown while the amount is still empty. */
    val favourites: List<app.burrow.money.data.FavouriteView> = emptyList(),
    /** Categories used most for this type, most first. */
    val topCategoryIds: List<Long> = emptyList(),
) {
    /** The category chips (17): the 4 most used (then the rest in order); the chosen one always shows. */
    val quickCategories: List<CategoryEntity> get() {
        val fits = categoriesForType
        val ordered = (topCategoryIds.mapNotNull { id -> fits.firstOrNull { it.id == id } } + fits).distinctBy { it.id }.take(QUICK_CATEGORIES)
        val chosen = category?.takeIf { c -> fits.any { it.id == c.id } && ordered.none { it.id == c.id } }
        return listOfNotNull(chosen) + ordered.take(if (chosen != null) QUICK_CATEGORIES - 1 else QUICK_CATEGORIES)
    }

    val category get() = categories.firstOrNull { it.id == form.categoryId }
    val account get() = accounts.firstOrNull { it.account.id == form.accountId }
    val toAccount get() = accounts.firstOrNull { it.account.id == form.toAccountId }
    val amount get() = form.input.minor()
    val missingCategory get() = form.type != TxnType.TRANSFER && category == null
    val missingToAccount get() = form.type == TxnType.TRANSFER && (toAccount == null || form.toAccountId == form.accountId)
    val canSave get() = amount > 0 && account != null && !missingCategory && !missingToAccount

    /** A date after now: saved as upcoming, added on its day (78). */
    val upcoming get() = form.at > System.currentTimeMillis() + UPCOMING_SLACK

    /** Categories that fit the type: expense ones for expenses, income ones for income. */
    val categoriesForType get() = categories.filter { it.kind == if (form.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }

    /** Where money can come from or go to; loans and deposits only take transfers. */
    val accountsForType get() = if (form.type == TxnType.TRANSFER) accounts else accounts.filter { it.account.type != AccountType.LOAN && it.account.type != AccountType.SAVINGS }

    fun shows(extra: String) = extra in extras

    /** Repeat (a bill) is offered on a new income or expense when Customise › Expense has it on. */
    val repeatOffered get() = shows(FormExtra.REPEAT) && !isEdit && form.type != TxnType.TRANSFER
}

private const val UPCOMING_SLACK = 60_000L
private const val QUICK_CATEGORIES = 4

/** A finished save: what the snackbar says and how Undo puts things back. */
class Saved(val type: String, val wasEdit: Boolean, val upcoming: Boolean = false, val label: String? = null, val undo: suspend () -> Unit)

/**
 * New, edit or duplicate a transaction (17, 18, 74–79, 87). [editId] edits; [duplicateId] copies
 * one with today's date; [captureId] starts from a found payment ("Open it to edit first");
 * otherwise a new one of [startType] (blank = Customise › Expense › Opens as).
 */
class AddViewModel(
    private val app: AppContainer,
    private val editId: Long,
    private val duplicateId: Long,
    private val startType: String,
    private val monthStartDay: Int,
    currency: String,
    /** Start with this account (Account › Transfer). */
    private val startAccountId: Long = 0L,
    private val captureId: Long = 0L,
) : ViewModel() {
    private val repo = app.money
    private val form = MutableStateFlow<AddForm?>(null)
    private val suggestions = MutableStateFlow<List<PayeeSuggestion>>(emptyList())
    private val topCategories = MutableStateFlow<List<Long>>(emptyList())
    private var original: TxnEntity? = null
    private val digits = MoneyFormat.of(currency).digits
    private var picked = false

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<AddUiState?> = form.flatMapLatest { f ->
        if (f == null) flowOf(null)
        else {
            val period = BudgetPeriod.containing(LocalDate.now(), monthStartDay)
            combine(
                combine(repo.categories, repo.accounts, repo.spentByCategory(period)) { a, b, c -> Triple(a, b, c) },
                combine(app.settings.settings, topCategories) { a, b -> a to b }, suggestions, repo.tags, app.next.favourites,
            ) { (cats, accs, spent), (s, top), sug, tags, favs ->
                AddUiState(f, editId != 0L, cats, accs, spent, s.formExtras, sug, tags.map { it.name }, favs, top)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val s = app.settings.settings.first()
            val accounts = repo.accounts.first()
            val pay = accounts.filter { it.account.type != AccountType.LOAN && it.account.type != AccountType.SAVINGS }
            val source = (if (editId != 0L) editId else duplicateId).takeIf { it != 0L }?.let { repo.get(it) }
            original = source?.takeIf { editId != 0L }
            val capture = captureId.takeIf { it != 0L }?.let { app.capture.get(it) }
            form.value = when {
                source != null -> AddForm(
                    type = source.type, input = AmountInput.of(source.amount, digits), categoryId = source.categoryId,
                    accountId = source.accountId, toAccountId = source.toAccountId,
                    at = if (editId != 0L) source.at else System.currentTimeMillis(),
                    payee = source.payee.orEmpty(), note = source.note.orEmpty(),
                    tags = source.id.let { repo.tagsOf(it) },
                    kept = if (editId != 0L) repo.attachmentsDao.attachments(source.id) else emptyList(),
                    lend = source.lend?.takeIf { it != Lend.SETTLE },
                    person = source.personId?.let { repo.attachmentsDao.person(it)?.name }.orEmpty(),
                    settleRemind = false,
                )
                capture != null -> {
                    val type = if (capture.direction == Direction.IN.name) TxnType.INCOME else TxnType.EXPENSE
                    val payee = capture.payee?.let(::tidyPayee).orEmpty()
                    AddForm(
                        type = type, input = AmountInput.of(capture.amount ?: 0, digits),
                        categoryId = capture.categoryId ?: payee.takeIf { it.isNotBlank() }?.let { repo.usualCategoryFor(it, type, raw = capture.payee.orEmpty()) },
                        accountId = app.capture.accountFor(capture) ?: pay.firstOrNull()?.account?.id,
                        at = capture.receivedAt, payee = payee, note = capture.sender,
                    )
                }
                else -> {
                    val type = startType.ifBlank { s.formDefaultType }
                    val preferred = s.formDefaultAccount?.takeIf { id -> pay.any { it.account.id == id } }
                    val from = startAccountId.takeIf { it != 0L } ?: preferred ?: pay.firstOrNull()?.account?.id
                    AddForm(
                        type = type, input = AmountInput(digits = digits),
                        accountId = from,
                        toAccountId = pay.firstOrNull { it.account.id != from }?.account?.id,
                        categoryId = if (FormExtra.LAST_CATEGORY in s.formExtras && type != TxnType.TRANSFER) repo.lastCategory(type) else null,
                    )
                }
            }
            refreshSuggestions()
        }
    }

    private fun edit(change: (AddForm) -> AddForm) = form.update { it?.let(change) }

    fun setType(type: String) {
        edit {
            // A category of the other kind no longer fits.
            val cat = state.value?.categories?.firstOrNull { c -> c.id == it.categoryId }
            val fits = cat != null && (cat.kind == CategoryKind.INCOME) == (type == TxnType.INCOME)
            it.copy(type = type, categoryId = if (fits) it.categoryId else null, tried = false, repeat = if (type == TxnType.TRANSFER) Every.NEVER else it.repeat)
        }
        refreshSuggestions()
    }

    fun setInput(input: AmountInput) = edit { it.copy(input = input) }
    fun evaluate() = edit { it.copy(input = it.input.evaluated()) }
    fun setCategory(id: Long) { picked = true; edit { it.copy(categoryId = id) } }
    fun setAccount(id: Long) = edit { it.copy(accountId = id) }
    fun setToAccount(id: Long) = edit { it.copy(toAccountId = id) }
    fun setNote(text: String) = edit { it.copy(note = text) }

    /** Typing in Paid to: the suggestions follow what was typed. */
    fun setPayee(text: String) {
        edit { it.copy(payee = text) }
        refreshSuggestions()
    }

    /** A suggestion chip: the payee, and their usual category unless one was picked by hand. */
    fun pickPayee(s: PayeeSuggestion) {
        edit { it.copy(payee = s.name, categoryId = if (picked || s.categoryId == null) it.categoryId else s.categoryId) }
        refreshSuggestions()
    }

    private fun refreshSuggestions() = viewModelScope.launch {
        val f = form.value ?: return@launch
        if (f.type != TxnType.TRANSFER) topCategories.value = repo.topCategories(f.type, QUICK_CATEGORIES)
        if (f.type == TxnType.TRANSFER) { suggestions.value = emptyList(); return@launch }
        val names = repo.payeeSuggestions(f.type, f.payee).filter { !it.equals(f.payee.trim(), true) }
        val cats = repo.categories.first()
        suggestions.value = names.map { n ->
            val cat = repo.usualCategoryFor(n, f.type)?.let { id -> cats.firstOrNull { it.id == id } }
            PayeeSuggestion(n, cat?.icon, cat?.colorHue, cat?.id)
        }
    }

    fun toggleTag(tag: String) = edit { it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag) }
    fun setRepeat(every: String) = edit { it.copy(repeat = every) }
    fun setSubscription(on: Boolean) = edit { it.copy(subscription = on) }
    fun setRepeatRemind(on: Boolean) = edit { it.copy(repeatRemind = on) }
    fun setLend(kind: String?) = edit { it.copy(lend = kind) }
    fun setPerson(name: String) = edit { it.copy(person = name) }
    fun setSettleRemind(on: Boolean) = edit { it.copy(settleRemind = on) }
    fun setFavourite(on: Boolean) = edit { it.copy(favourite = on) }

    /** One tap on a favourite chip (04): saved to now, with Undo. */
    fun spendFavourite(f: app.burrow.money.data.FavouriteView, label: String, onSaved: (Saved) -> Unit) {
        viewModelScope.launch {
            val id = app.next.spendFavourite(f.favourite)
            onSaved(Saved(TxnType.EXPENSE, wasEdit = false, label = label) { repo.delete(id) })
        }
    }

    fun addAttachment(p: Attachments.Pending) = edit { it.copy(attachments = it.attachments + p) }
    fun removePending(p: Attachments.Pending) = edit { p.file.delete(); it.copy(attachments = it.attachments - p) }
    fun removeKept(a: AttachmentEntity) = edit { it.copy(kept = it.kept - a, removed = it.removed + a) }

    /** Keeps the time of day, changes the day (any day: a future one makes it upcoming). */
    fun setDate(date: LocalDate) = edit {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(it.at).atZone(zone).toLocalTime()
        it.copy(at = date.atTime(time).atZone(zone).toInstant().toEpochMilli())
    }

    fun setTime(time: LocalTime) = edit {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate()
        it.copy(at = day.atTime(time).atZone(zone).toInstant().toEpochMilli())
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
        val lend = f.lend?.takeIf { f.type != TxnType.TRANSFER && f.person.isNotBlank() }
        viewModelScope.launch {
            val personId = lend?.let { repo.personFor(f.person) }
            val txn = TxnEntity(
                id = base?.id ?: 0,
                type = f.type,
                amount = s.amount,
                currency = account.currency,
                baseAmount = s.amount,
                accountId = account.id,
                toAccountId = if (f.type == TxnType.TRANSFER) f.toAccountId else null,
                categoryId = if (f.type == TxnType.TRANSFER) null else f.categoryId,
                payee = f.payee.trim().ifBlank { null },
                note = f.note.trim().ifBlank { null },
                at = f.at,
                source = base?.source ?: if (captureId != 0L) app.capture.get(captureId)?.source ?: "MANUAL" else "MANUAL",
                // Money lent out isn't spending; money borrowed isn't income.
                excludeFromStats = (base?.excludeFromStats ?: false) || (lend == Lend.LENT && f.type == TxnType.EXPENSE) || (lend == Lend.BORROWED && f.type == TxnType.INCOME),
                isSample = base?.isSample ?: false,
                createdAt = base?.createdAt ?: now,
                updatedAt = now,
                status = if (s.upcoming) TxnStatus.UPCOMING else TxnStatus.POSTED,
                personId = personId,
                lend = lend,
                billOccurrenceId = base?.billOccurrenceId,
                goalId = base?.goalId,
                linkedId = base?.linkedId,
            )
            val id = repo.save(txn)
            repo.setTags(id, f.tags)
            f.removed.forEach { app.attachments.remove(it) }
            if (f.attachments.isNotEmpty()) app.attachments.keep(id, f.attachments)
            val billId = if (base == null && f.repeat != Every.NEVER && f.type != TxnType.TRANSFER) {
                app.plan.repeatFrom(txn.copy(id = id), f.repeat, f.subscription, f.repeatRemind, txn.payee ?: s.category?.name.orEmpty())
            } else null
            if (personId != null && f.settleRemind) repo.setPersonReminder(personId, now + SETTLE_AFTER)
            if (captureId != 0L) app.capture.markSaved(captureId, id, txn.categoryId)
            // An upcoming payment: the alarm wakes on its day to add it (115).
            if (s.upcoming) with(app) { runBillReminders() }
            // The Favourite chip: "Chai ₹20 · Cash" for one tap next time.
            val favouriteId = if (f.favourite && f.type == TxnType.EXPENSE && txn.categoryId != null) {
                app.next.addFavourite(txn.payee ?: s.category?.name.orEmpty(), txn.amount, txn.categoryId, txn.accountId)
            } else null
            onSaved(
                Saved(f.type, base != null, s.upcoming) {
                    if (base != null) repo.restore(base) else repo.delete(id)
                    billId?.let { app.plan.deleteBill(it) }
                    favouriteId?.let { app.next.removeFavourite(it) }
                },
            )
        }
    }

    private companion object {
        /** "Remind me to settle · In 2 weeks" (77). */
        const val SETTLE_AFTER = 14L * 24 * 60 * 60 * 1000
    }
}
