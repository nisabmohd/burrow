package app.burrow.money.ui.txn

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.TxnEntity
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.domain.BudgetPeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@Immutable
data class TxnUiState(
    /** Null once the transaction is gone (deleted elsewhere). */
    val txn: TxnView?,
    val categories: List<CategoryEntity>,
    val spentByCategory: Map<Long, Long>,
    /** Split parts (category id → amount), in order. */
    val parts: List<app.burrow.money.data.SplitEntity> = emptyList(),
    /** Refunds of this expense. */
    val refunds: List<TxnEntity> = emptyList(),
    /** A refund's expense. */
    val original: TxnView? = null,
    /** The message it was recorded from (21 "Recorded from SMS"). */
    val capture: app.burrow.money.data.CaptureEntity? = null,
    /** Every category, for naming split parts. */
    val allCategories: List<CategoryEntity> = emptyList(),
)

@androidx.compose.runtime.Immutable
data class TxnExtras(
    val attachments: List<app.burrow.money.data.AttachmentEntity> = emptyList(),
    val tags: List<String> = emptyList(),
    val person: String? = null,
)

/** Transaction detail (21): change the category or note in place, delete with Undo. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TxnViewModel(private val repo: MoneyRepository, private val id: Long, monthStartDay: Int, next: app.burrow.money.data.NextRepository) : ViewModel() {
    private val original = repo.txn(id).flatMapLatest { t -> t?.txn?.refundOf?.let { repo.txn(it) } ?: flowOf(null) }

    val state: StateFlow<TxnUiState?> = combine(
        combine(repo.txn(id), repo.categories, repo.spentByCategory(BudgetPeriod.containing(LocalDate.now(), monthStartDay))) { a, b, c -> Triple(a, b, c) },
        next.splitsOf(id), next.refundsOf(id), original, next.captureFor(id),
    ) { (t, cats, spent), parts, refunds, orig, capture ->
        val kind = if (t?.txn?.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        TxnUiState(t, cats.filter { it.kind == kind }, spent, parts, refunds, orig, capture, cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What was added while creating it: receipts, tags and who it was lent to or borrowed from. */
    val extras: StateFlow<TxnExtras> = combine(repo.txn(id), repo.attachmentsDao.attachmentsFlow(id)) { t, files ->
        TxnExtras(files, repo.tagsOf(id), t?.txn?.personId?.let { repo.attachmentsDao.person(it)?.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxnExtras())

    /** Applies [change] and hands back how to undo it. */
    private fun change(change: (TxnEntity) -> TxnEntity, onDone: (suspend () -> Unit) -> Unit) {
        val before = state.value?.txn?.txn ?: return
        viewModelScope.launch {
            repo.save(change(before).copy(updatedAt = System.currentTimeMillis()))
            onDone { repo.restore(before) }
        }
    }

    fun setCategory(categoryId: Long, onDone: (suspend () -> Unit) -> Unit) = change({ it.copy(categoryId = categoryId) }, onDone)

    fun setNote(note: String, onDone: (suspend () -> Unit) -> Unit) = change({ it.copy(note = note.ifBlank { null }) }, onDone)

    /** An upcoming payment's new day, same time of day (114 "Change date"). */
    fun setDate(date: LocalDate, onDone: (suspend () -> Unit) -> Unit) = change({ t ->
        val z = java.time.ZoneId.systemDefault()
        val time = java.time.Instant.ofEpochMilli(t.at).atZone(z).toLocalTime()
        t.copy(at = date.atTime(time).atZone(z).toInstant().toEpochMilli())
    }, onDone)

    /** An upcoming payment, added now (114). */
    fun addNow(onDone: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch { repo.addNow(id)?.let(onDone) }
    }

    fun delete(onDeleted: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch {
            val gone = repo.delete(id) ?: return@launch
            onDeleted { repo.restore(gone) }
        }
    }
}
