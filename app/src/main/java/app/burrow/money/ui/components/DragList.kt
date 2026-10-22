package app.burrow.money.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import app.burrow.money.R
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.standardSpec
import kotlinx.coroutines.launch

/**
 * Reorder by drag (16): hold the ⠿ handle and drag; the row lifts and the others make room. The new
 * order is saved on drop ([onMove]). TalkBack gets "Move up" / "Move down" on every row instead.
 * For short lists inside a scrolling page (Today widgets, favourites), so a plain Column.
 */
@Composable
fun <T> DragColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (List<T>) -> Unit,
    moveUpLabel: (T) -> String,
    moveDownLabel: (T) -> String,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, index: Int, count: Int, handle: @Composable () -> Unit, lifted: Boolean) -> Unit,
) {
    var order by remember(items) { mutableStateOf(items) }
    var dragging by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val shifts = remember { mutableMapOf<Any, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val gap = with(LocalDensity.current) { Space.row.toPx() }

    fun swap(from: Int, to: Int) {
        val moved = order[from]
        val other = order[to]
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        val h = (heights[key(moved)] ?: 0) + gap
        // The row that made room slides into its new place.
        val anim = shifts.getOrPut(key(other)) { Animatable(0f) }
        scope.launch { anim.snapTo(if (to > from) h else -h); anim.animateTo(0f, standardSpec()) }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun drop() {
        if (dragging != null && order != items) onMove(order)
        dragging = null
        offset = 0f
    }

    fun moveBy(item: T, step: Int) {
        val i = order.indexOf(item)
        val j = i + step
        if (i < 0 || j !in order.indices) return
        order = order.toMutableList().apply { add(j, removeAt(i)) }
        onMove(order)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.row)) {
        order.forEachIndexed { i, item ->
            val k = key(item)
            key(k) {
                val lifted = dragging == k
                val shift = shifts.getOrPut(k) { Animatable(0f) }
                Box(
                    Modifier
                        .zIndex(if (lifted) 1f else 0f)
                        .onSizeChanged { heights[k] = it.height }
                        .graphicsLayer { translationY = if (lifted) offset else shift.value }
                        .semantics {
                            customActions = listOfNotNull(
                                if (i > 0) CustomAccessibilityAction(moveUpLabel(item)) { moveBy(item, -1); true } else null,
                                if (i < order.lastIndex) CustomAccessibilityAction(moveDownLabel(item)) { moveBy(item, 1); true } else null,
                            )
                        },
                ) {
                    val handle = @Composable {
                        Box(
                            Modifier.size(Size.iconButton).pointerInput(k) {
                                detectDragGestures(
                                    onDragStart = { dragging = k; offset = 0f; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragEnd = { drop() },
                                    onDragCancel = { drop() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        offset += amount.y
                                        val at = order.indexOfFirst { key(it) == k }
                                        if (offset > 0 && at < order.lastIndex) {
                                            val next = (heights[key(order[at + 1])] ?: 0) + gap
                                            if (offset > next / 2) { swap(at, at + 1); offset -= next }
                                        } else if (offset < 0 && at > 0) {
                                            val prev = (heights[key(order[at - 1])] ?: 0) + gap
                                            if (-offset > prev / 2) { swap(at, at - 1); offset += prev }
                                        }
                                    },
                                )
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_drag_indicator_regular), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Size.icon))
                        }
                    }
                    row(item, i, order.size, handle, lifted)
                }
            }
        }
    }
}
