package app.burrow.money.ui.review

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.capture.Direction
import app.burrow.money.capture.Part
import app.burrow.money.capture.RuleEngine
import app.burrow.money.data.CaptureEntity
import app.burrow.money.data.CaptureRepository
import app.burrow.money.data.CaptureStatus
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.TxnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One payment waiting in To review. */
@Immutable
data class ReviewItem(
    val capture: CaptureEntity,
    val category: CategoryEntity?,
    /** Chips: the suggestions, with the chosen category first. */
    val chips: List<CategoryEntity>,
    /** Every category of the payment's kind, for "More". */
    val allCategories: List<CategoryEntity>,
    val accountName: String?,
    /** The bank or app the message came from ("HDFC Bank"). */
    val from: String,
    val spans: Map<Part, IntRange>,
    /**
     * Ready to save (44): Burrow knows this shop (its usual category, or the message's words), the user picked a
     * category, or it needs none (a transfer). Otherwise it waits under Pick a category ("Burrow isn't sure").
     */
    val sure: Boolean = true,
) {
    /** Money in (income, refund): shown with +. */
    val isIncome get() = app.burrow.money.capture.MoneyType.ofStored(capture.direction).direction == Direction.IN
    val type get() = app.burrow.money.capture.MoneyType.ofStored(capture.direction)
}

/** To review (44): payments the app found, each saved with one tap, or all at once. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(
    private val capture: CaptureRepository,
    private val money: MoneyRepository,
    private val engine: RuleEngine,
    /** Play's rating card at a calm moment (play build; AppContainer.maybeAskForReview). */
    private val reviewPrompt: suspend (android.app.Activity) -> Unit = {},
) : ViewModel() {

    fun askForReview(activity: android.app.Activity) {
        viewModelScope.launch { reviewPrompt(activity) }
    }


    /** Messages no rule could read: a row to Not understood, so they are one tap away from here. */
    val notUnderstood: StateFlow<Int> = capture.notUnderstoodCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val state: StateFlow<List<ReviewItem>?> = combine(capture.toReview, money.categories, money.accounts, capture.activeRules) { list, cats, accounts, rules ->
        Quad(list, cats, accounts, rules)
    }.mapLatest { (list, cats, accounts, rules) ->
        list.map { c ->
            // Income picks income categories; a refund the spending one it lowers; a transfer none.
            val type = app.burrow.money.data.categoryTypeOf(app.burrow.money.capture.MoneyType.ofStored(c.direction))
            val kind = if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            val ofKind = if (type == null) emptyList() else cats.filter { it.kind == kind }
            val chosen = cats.firstOrNull { it.id == c.categoryId }
            val suggested = if (type == null) emptyList() else capture.suggestions(c.payee, type, c.body).mapNotNull { id -> ofKind.firstOrNull { it.id == id } }
            val rule = rules.firstOrNull { it.id == c.ruleId }
            val accountId = capture.accountFor(c)
            val sure = isSure(c, type)
            ReviewItem(
                capture = c,
                category = chosen,
                chips = (listOfNotNull(chosen) + suggested).distinctBy { it.id }.take(3),
                allCategories = ofKind,
                accountName = accounts.firstOrNull { it.account.id == accountId }?.account?.name,
                from = rule?.name ?: c.sender,
                spans = rule?.let { engine.parseWith(it, null, c.body)?.spans }.orEmpty(),
                sure = sure,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun choose(captureId: Long, categoryId: Long) {
        viewModelScope.launch { capture.setCategory(captureId, categoryId) }
    }

    fun save(captureId: Long, onDone: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch {
            capture.save(captureId, null)
            onDone { capture.unsave(captureId) }
        }
    }

    fun ignore(captureId: Long, onDone: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch {
            capture.setStatus(captureId, CaptureStatus.IGNORED)
            onDone { capture.setStatus(captureId, CaptureStatus.REVIEW) }
        }
    }

    /** Ready to save: no category needed, picked by the user, or the one Burrow knows for this shop. */
    private suspend fun isSure(c: CaptureEntity, type: String?): Boolean = when {
        type == null || c.categoryPicked -> true
        c.categoryId == null -> false
        app.burrow.money.capture.MoneyType.ofStored(c.direction) == app.burrow.money.capture.MoneyType.REFUND -> true
        else -> capture.knownCategory(c.payee, type, c.body) == c.categoryId
    }

    /** "Save 2 ready" / "Save all 3" (44, 242): every payment that is ready, never one Burrow isn't sure of. */
    fun saveReady(onDone: (Int, suspend () -> Unit) -> Unit) {
        viewModelScope.launch {
            // Read the list fresh (the screen's copy may lag a save a moment ago); count only what saved.
            val ready = capture.toReview.first().filter { c ->
                isSure(c, app.burrow.money.data.categoryTypeOf(app.burrow.money.capture.MoneyType.ofStored(c.direction)))
            }
            val ids = ready.map { it.id }.filter { capture.save(it, null) != null }
            onDone(ids.size) { ids.forEach { capture.unsave(it) } }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
