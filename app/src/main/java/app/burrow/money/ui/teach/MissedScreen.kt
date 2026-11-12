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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Messages with money that no rule could read, newest first (they wait here for Teach). */
class MissedViewModel(private val capture: CaptureRepository) : ViewModel() {
    val state: StateFlow<List<CaptureEntity>?> = capture.notUnderstood.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Not a payment, or not worth a rule: out of the list, with Undo. */
    fun ignore(id: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        capture.setStatus(id, CaptureStatus.IGNORED)
        onDone { capture.setStatus(id, CaptureStatus.UNREAD) }
    }
}
