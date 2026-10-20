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
) {
    val isIncome get() = capture.direction == Direction.IN.name
}

/** To review (44): payments the app found, each saved with one tap, or all at once. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(private val capture: CaptureRepository, private val money: MoneyRepository, private val engine: RuleEngine) : ViewModel() {

    val state: StateFlow<List<ReviewItem>?> = combine(capture.toReview, money.categories, money.accounts, capture.activeRules) { list, cats, accounts, rules ->
        Quad(list, cats, accounts, rules)
    }.mapLatest { (list, cats, accounts, rules) ->
        list.map { c ->
            val type = if (c.direction == Direction.IN.name) TxnType.INCOME else TxnType.EXPENSE
            val kind = if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            val ofKind = cats.filter { it.kind == kind }
            val chosen = cats.firstOrNull { it.id == c.categoryId }
            val suggested = capture.suggestions(c.payee, type).mapNotNull { id -> ofKind.firstOrNull { it.id == id } }
            val rule = rules.firstOrNull { it.id == c.ruleId }
            val accountId = capture.accountFor(c)
            ReviewItem(
                capture = c,
                category = chosen,
                chips = (listOfNotNull(chosen) + suggested).distinctBy { it.id }.take(3),
                allCategories = ofKind,
                accountName = accounts.firstOrNull { it.account.id == accountId }?.account?.name,
                from = rule?.name ?: c.sender,
                spans = rule?.let { engine.parseWith(it, null, c.body)?.spans }.orEmpty(),
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

    /** "Save N payments": every one with its chosen category. */
    fun saveAll(onDone: (Int, suspend () -> Unit) -> Unit) {
        viewModelScope.launch {
            val ids = state.value.orEmpty().map { it.capture.id }
            ids.forEach { capture.save(it, null) }
            onDone(ids.size) { ids.forEach { capture.unsave(it) } }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
