package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * What a swipe does (Customise › Transactions › Swipe): its icon, its TalkBack label, and the action.
 * [showLabel] draws the label instead of the icon (Coming up: "Mark paid", "Skip").
 */
@Immutable
data class SwipeAction(val icon: Painter?, val label: String, val primary: Boolean, val showLabel: Boolean = false, val run: () -> Unit)

/**
 * A list row with a swipe each way (either may be null). The row springs back; the data moves it.
 * Past [Space.swipeTrigger] the action is armed: one threshold haptic, the background fills in, and
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
            val toStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val action = if (toStart) start else end
            if (state.dismissDirection != SwipeToDismissBoxValue.Settled && action != null) {
                val c = MaterialTheme.colorScheme
                // Short of the trigger the background stays quiet; armed, it takes the action's colour.
                val bg = when {
                    !armed -> c.surfaceContainer
                    action.primary -> c.primaryContainer
                    else -> c.surfaceContainerHighest
                }
                val fg = if (armed && action.primary) c.onPrimaryContainer else c.onSurfaceVariant
                Box(
                    Modifier.fillMaxSize().clip(shape).background(bg).padding(horizontal = Space.sheet),
                    contentAlignment = if (toStart) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    // The label (or icon) shows only once armed (171–173): a short drag stays quiet.
                    if (armed) {
                        if (action.showLabel || action.icon == null) Text(action.label, style = BurrowType.button, color = fg)
                        else Icon(action.icon, null, tint = fg)
                    }
                }
            }
        },
    ) { content() }
}

private fun SwipeToDismissBoxState.offsetOrZero(): Float = runCatching { requireOffset() }.getOrDefault(0f)
