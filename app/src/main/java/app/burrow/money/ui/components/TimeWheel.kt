package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import app.burrow.money.R
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import java.time.LocalTime
import kotlinx.coroutines.launch

/*
 * The one time picker (TIME-PICKER.md, 2026-10-24): New expense's time (12 / 135), Bill reminder
 * time (160), Sunday review time. Three snapping columns — hour · minute in 5s · AM/PM (no AM/PM and
 * hours 00–23 on a 24-hour phone) — over a pill band, the top and bottom rows fading out. Every time
 * setting uses this sheet; the value sheet is only for numbers.
 */

private const val VISIBLE_ROWS = 5
private const val MINUTE_STEP = 5

/** The wheel's columns for [time]: hour index, minute index (5-minute steps, rounded), PM. Pure: unit-tested. */
fun wheelStart(time: LocalTime, h24: Boolean): Triple<Int, Int, Boolean> {
    var t = time
    val rounded = ((t.minute + MINUTE_STEP / 2) / MINUTE_STEP) * MINUTE_STEP
    t = if (rounded == 60) t.plusHours(1).withMinute(0) else t.withMinute(rounded)
    val hourIndex = if (h24) t.hour else (t.hour % 12).let { if (it == 0) 12 else it } - 1
    return Triple(hourIndex, t.minute / MINUTE_STEP, t.hour >= 12)
}

/** The time the wheel shows. Pure: unit-tested. */
fun wheelTime(hourIndex: Int, minuteIndex: Int, pm: Boolean, h24: Boolean): LocalTime {
    val minute = minuteIndex * MINUTE_STEP
    val hour = if (h24) hourIndex else ((hourIndex + 1) % 12) + if (pm) 12 else 0
    return LocalTime.of(hour, minute)
}

/** Pick a time (135, 160): [title] + [line], the wheel, Done. */
@Composable
fun TimeWheelSheet(title: String, line: String?, time: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val h24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val (h0, m0, pm0) = remember(time, h24) { wheelStart(time, h24) }
    val hours = remember(h24) { if (h24) (0..23).map { "%02d".format(it) } else (1..12).map { it.toString() } }
    val minutes = remember { (0 until 60 step MINUTE_STEP).map { "%02d".format(it) } }
    val halves = listOf(stringResource(R.string.am), stringResource(R.string.pm))
    val hourState = rememberLazyListState(h0)
    val minuteState = rememberLazyListState(m0)
    val halfState = rememberLazyListState(if (pm0) 1 else 0)
    val haptics = LocalHapticFeedback.current
    BurrowSheet(onDismiss) {
        SheetHeader(title, onDismiss, line)
        Box(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.small), contentAlignment = Alignment.Center) {
            // The band behind the middle row.
            Box(Modifier.fillMaxWidth().height(Size.wheelRow).clip(Shapes.pill).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Row(
                Modifier.height(Size.wheelRow * VISIBLE_ROWS).fadeEdges(),
                horizontalArrangement = Arrangement.spacedBy(Space.group),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelColumn(hours, hourState, stringResource(R.string.hour)) { haptics.stepTick() }
                Text(":", style = BurrowType.headline, color = MaterialTheme.colorScheme.onSurface)
                WheelColumn(minutes, minuteState, stringResource(R.string.minute)) { haptics.stepTick() }
                if (!h24) WheelColumn(halves, halfState, stringResource(R.string.am_pm)) { haptics.stepTick() }
            }
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.done), {
                haptics.confirm()
                onPick(wheelTime(hourState.centred(), minuteState.centred(), halfState.centred() == 1, h24))
            }, Modifier.fillMaxWidth(), height = Size.buttonBottom)
        }
    }
}

/** The row in the middle once a column has settled. */
private fun LazyListState.centred(): Int {
    val info = layoutInfo
    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - centre) }?.index ?: firstVisibleItemIndex
}

@Composable
private fun WheelColumn(items: List<String>, state: LazyListState, label: String, onSnap: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val selected by remember { derivedStateOf { state.centred() } }
    LaunchedEffect(selected) { if (state.isScrollInProgress) onSnap() }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        // Two rows of space above and below, so the first and last items can sit in the middle.
        contentPadding = PaddingValues(vertical = Size.wheelRow * (VISIBLE_ROWS / 2)),
        modifier = Modifier.width(Size.wheelColumn).height(Size.wheelRow * VISIBLE_ROWS)
            .semantics { contentDescription = label; stateDescription = items.getOrElse(selected) { "" } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(items.size) { i ->
            val on = i == selected
            Box(
                Modifier.height(Size.wheelRow).fillMaxWidth().clickable { scope.launch { state.animateScrollToItem(i) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    items[i], textAlign = TextAlign.Center, maxLines = 1,
                    style = if (on) BurrowType.headline else BurrowType.keypad,
                    color = if (on) c.onSurface else c.onSurfaceVariant,
                )
            }
        }
    }
}

/** The top and bottom rows fade out (a vertical gradient mask). */
private fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
