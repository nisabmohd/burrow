package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.successSwipeColors
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/** The colour behind a swiped row: green for a success, primary, grey, or red for a delete. */
enum class SwipeTone { SUCCESS, PRIMARY, NEUTRAL, DESTRUCTIVE }

/**
 * What a swipe does: its icon, its label (drawn next to the icon, and TalkBack's), its colour and the action.
 * [showLabel] false draws the icon alone (Activity's Edit / Duplicate / Delete).
 */
@Immutable
data class SwipeAction(val icon: Painter?, val label: String, val tone: SwipeTone, val showLabel: Boolean = true, val run: () -> Unit)

/**
 * A list row with a swipe each way (either may be null). The row springs back; the data moves it.
 * Like Sprout: the action's colour, icon and label show from the first pixel of the drag, so you see
 * what the swipe does before it counts. Past [Space.swipeTrigger] it is armed (one threshold haptic) and
 * letting go runs it. Let go sooner (even with a quick flick) and nothing happens.
 * TalkBack gets the same actions as custom actions, so nothing needs a swipe.
 */
@Composable
fun SwipeRow(start: SwipeAction?, end: SwipeAction?, shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (start == null && end == null) {
        Box(modifier) { content() }
        return
    }
    val haptics = LocalHapticFeedback.current
    val trigger = with(LocalDensity.current) { Space.swipeTrigger.toPx() }
    val startNow by rememberUpdatedState(start)
    val endNow by rememberUpdatedState(end)
    val holder = remember { arrayOfNulls<SwipeToDismissBoxState>(1) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            // Only a row let go past the trigger counts; a flick from short of it springs back.
            val past = holder[0]?.let { abs(it.offsetOrZero()) >= trigger } ?: false
            if (past) when (v) {
                SwipeToDismissBoxValue.StartToEnd -> startNow?.let { haptics.confirm(); it.run() }
                SwipeToDismissBoxValue.EndToStart -> endNow?.let { haptics.confirm(); it.run() }
                else -> Unit
            }
            false
        },
        positionalThreshold = { trigger },
    )
    holder[0] = state
    val armed by remember(state) { derivedStateOf { abs(state.offsetOrZero()) >= trigger } }
    // One haptic going out past the trigger, so you know letting go now counts.
    LaunchedEffect(state) {
        var first = true
        snapshotFlow { armed }.distinctUntilChanged().collect { on ->
            if (!first && on) haptics.threshold()
            first = false
        }
    }
    SwipeToDismissBox(
        state,
        modifier = modifier.semantics {
            customActions = listOfNotNull(start, end).map { a -> CustomAccessibilityAction(a.label) { a.run(); true } }
        },
        enableDismissFromStartToEnd = start != null,
        enableDismissFromEndToStart = end != null,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val action = if (toEnd) start else end
            if (state.dismissDirection != SwipeToDismissBoxValue.Settled && action != null) {
                val c = MaterialTheme.colorScheme
                val success = successSwipeColors()
                val (bg, fg) = when (action.tone) {
                    SwipeTone.SUCCESS -> success.background to success.content
                    SwipeTone.PRIMARY -> c.primary to c.onPrimary
                    SwipeTone.NEUTRAL -> c.surfaceContainerHighest to c.onSurfaceVariant
                    SwipeTone.DESTRUCTIVE -> c.error to c.onError
                }
                Row(
                    Modifier.fillMaxSize().clip(shape).background(bg).padding(horizontal = Space.sheet),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.small, if (toEnd) Alignment.Start else Alignment.End),
                ) {
                    // Icon then label going right, label then icon going left (Sprout's habit card).
                    val icon: @Composable () -> Unit = { action.icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(Size.iconButton20)) } }
                    val label: @Composable () -> Unit = { if (action.showLabel || action.icon == null) Text(action.label, style = BurrowType.button, color = fg, maxLines = 1) }
                    if (toEnd) { icon(); label() } else { label(); icon() }
                }
            }
        },
    ) { content() }
}

private fun SwipeToDismissBoxState.offsetOrZero(): Float = runCatching { requireOffset() }.getOrDefault(0f)
