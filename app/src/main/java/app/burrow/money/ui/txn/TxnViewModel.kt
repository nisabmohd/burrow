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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@Immutable
data class TxnUiState(
    /** Null once the transaction is gone (deleted elsewhere). */
    val txn: TxnView?,
    val categories: List<CategoryEntity>,
    val spentByCategory: Map<Long, Long>,
)

/** Transaction detail (21): change the category or note in place, delete with Undo. */
class TxnViewModel(private val repo: MoneyRepository, private val id: Long, monthStartDay: Int) : ViewModel() {
    val state: StateFlow<TxnUiState?> = combine(
        repo.txn(id),
        repo.categories,
        repo.spentByCategory(BudgetPeriod.containing(LocalDate.now(), monthStartDay)),
    ) { t, cats, spent ->
        val kind = if (t?.txn?.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        TxnUiState(t, cats.filter { it.kind == kind }, spent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun delete(onDeleted: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch {
            val gone = repo.delete(id) ?: return@launch
            onDeleted { repo.restore(gone) }
        }
    }
}
