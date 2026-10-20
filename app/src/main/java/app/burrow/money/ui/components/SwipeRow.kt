package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import app.burrow.money.ui.theme.Space

/** What a swipe does (Customise › Transactions › Swipe): its icon, its TalkBack label, and the action. */
@Immutable
data class SwipeAction(val icon: Painter, val label: String, val primary: Boolean, val run: () -> Unit)

/**
 * A list row with a swipe each way (either may be null). The row springs back; the data moves it.
 * TalkBack gets the same actions as custom actions, so nothing needs a swipe.
 */
@Composable
fun SwipeRow(start: SwipeAction?, end: SwipeAction?, shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (start == null && end == null) {
        Box(modifier) { content() }
        return
    }
    val haptics = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> start?.let { haptics.confirm(); it.run() }
            SwipeToDismissBoxValue.EndToStart -> end?.let { haptics.threshold(); it.run() }
            else -> Unit
        }
        false
    })
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
                Box(
                    Modifier.fillMaxSize().clip(shape).background(if (action.primary) c.primaryContainer else c.surfaceContainerHigh).padding(horizontal = Space.sheet),
                    contentAlignment = if (toStart) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Icon(action.icon, null, tint = if (action.primary) c.onPrimaryContainer else c.onSurfaceVariant)
                }
            }
        },
    ) { content() }
}
