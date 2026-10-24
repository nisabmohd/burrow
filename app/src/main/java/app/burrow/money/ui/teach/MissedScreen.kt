package app.burrow.money.ui.teach

import app.burrow.money.ui.components.MessageCard
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
import app.burrow.money.ui.components.Cookie
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

/**
 * Not understood (116, 117): the same card as To review (44) — who sent it and when, ✕ to ignore
 * (Undo), the message, a full-width tonal Teach button. No swipe (C2). Empty: Nothing to teach.
 */
@Composable
fun MissedScreen(vm: MissedViewModel, onBack: () -> Unit, onTeach: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val items = state ?: return
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack, title = stringResource(R.string.not_understood_title))
        if (items.isEmpty()) {
            EmptyState(
                shape = { Cookie(painterResource(R.drawable.ic_task_alt_regular)) },
                title = stringResource(R.string.nothing_to_teach),
                line = stringResource(R.string.nothing_to_teach_line),
            )
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = Space.block),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            item(key = "line") {
                Text(stringResource(R.string.not_understood_line), style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.headingInset))
            }
            items(items, key = { it.id }) { m ->
                val ignore = { vm.ignore(m.id) { undo -> snackbar.show(context.getString(R.string.ignored), undo) } }
                MissedCard(m, Modifier.animateItem(), onTeach = { onTeach(m.id) }, onIgnore = { ignore() })
            }
        }
    }
}

@Composable
private fun MissedCard(m: CaptureEntity, modifier: Modifier, onTeach: () -> Unit, onIgnore: () -> Unit) {
    val dates = rememberDateText()
    val from = if (m.source == "SMS") senderName(m.sender) else m.sender.substringAfterLast('.')
    val day = dates.day(dates.date(m.receivedAt)).let { d -> if (d == stringResource(R.string.today)) dates.time(m.receivedAt) else d }
    MessageCard(
        lead = { IconCircle(painterResource(if (m.source == "SMS") R.drawable.ic_sms_duotone else R.drawable.ic_notifications_duotone)) },
        title = m.sender.ifBlank { from }, line = day,
        ignoreLabel = stringResource(R.string.ignore), onIgnore = onIgnore,
        button = stringResource(R.string.teach), buttonIcon = painterResource(R.drawable.ic_hand_tap_regular), onButton = onTeach,
        modifier = modifier,
    ) { MessageBox(null, m.body, emptyList()) }
}
