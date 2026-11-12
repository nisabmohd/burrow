package app.burrow.money.ui.teach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.burrow.money.R
import app.burrow.money.data.CaptureEntity
import app.burrow.money.data.CaptureRepository
import app.burrow.money.data.CaptureStatus
import app.burrow.money.ui.capture.senderName
import app.burrow.money.ui.components.Chip
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.MessageBox
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.threshold
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A message not understood (116): the bank by name ("Kotak Bank", never "AX-KOTAKB") and Burrow's own reading of it. */
@Immutable
data class MissedRow(val capture: CaptureEntity, val bank: String?, val reading: TeachState)

/**
 * Not understood as CheckCards (116, 244; final 12 Nov): every message Burrow couldn't read, newest first, each with
 * what Burrow reads in it now. A type picked on the card ([pickType]) reads it again; Yes, save keeps the rule.
 */
class MissedViewModel(
    private val capture: CaptureRepository,
    private val engine: app.burrow.money.capture.RuleEngine,
    private val fallbackName: String,
) : ViewModel() {
    private val picked = kotlinx.coroutines.flow.MutableStateFlow<Map<Long, app.burrow.money.capture.MoneyType>>(emptyMap())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<List<MissedRow>?> = kotlinx.coroutines.flow.combine(capture.notUnderstood, picked) { list, p -> list to p }
        .mapLatest { (list, p) ->
            list.map { c ->
                val bank = capture.bankName(c.sender)
                val name = capture.ruleNameFor(c.sender) ?: fallbackName
                val start = TeachState(text = c.body, sender = c.sender, receivedAt = c.receivedAt, step = null, picks = emptyMap(), bank = bank ?: capture.ruleNameFor(c.sender).orEmpty())
                val read = readMessage(start, engine, name)
                MissedRow(c, bank, p[c.id]?.let { t -> rebuildRule(read.copy(pickedType = t), engine, name) } ?: read)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Did money go out or in? (244): the chip picked on the card. */
    fun pickType(id: Long, type: app.burrow.money.capture.MoneyType) = picked.update { it + (id to type) }

    /**
     * Yes, save (116): the rule is kept and every waiting message it reads moves on (to To review). Undo removes the
     * rule and puts those messages back here.
     */
    fun save(id: Long, onDone: (suspend () -> Unit) -> Unit) {
        val row = state.value?.firstOrNull { it.capture.id == id } ?: return
        val rule = row.reading.rule ?: return
        if (!row.reading.canSave) return
        viewModelScope.launch {
            val before = capture.notUnderstood.first().map { it.id }.toSet()
            capture.saveRule(rule)
            capture.retryNotUnderstood()
            val moved = before - capture.notUnderstood.first().map { it.id }.toSet()
            onDone {
                capture.deleteRule(rule.id)
                moved.forEach { capture.setStatus(it, CaptureStatus.UNREAD) }
            }
        }
    }

    /** Not a payment, or not worth a rule: out of the list, with Undo. */
    fun ignore(id: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        capture.setStatus(id, CaptureStatus.IGNORED)
        onDone { capture.setStatus(id, CaptureStatus.UNREAD) }
    }
}
