package app.burrow.money.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.burrow.money.R
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Space
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** The share of the screen a sheet may take; the rest stays visible above it. */
private const val MAX_SHEET_FRACTION = 0.9f

/** The sheet's drag handle area (8 dp above a 4 dp handle), which sits above the content. */
private val DRAG_HANDLE = 12.dp

/**
 * Every bottom sheet in the app: opens fully, sits on the bottom edge, and leaves the top 10% of
 * the screen visible however much it holds (just the status bar while the keyboard is up). Long
 * content scrolls inside it.
 *
 * The limit is on the content, not the sheet: a height limit on the sheet itself lifts it off
 * the bottom edge. It uses the room the sheet really has, because before Android 9 the sheet's
 * window shrinks to fit above the keyboard.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BurrowSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val screen = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val navBar = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val statusBar = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    // While typing (e.g. searching icons) the keyboard takes half the screen, so the sheet may
    // grow to just under the status bar; otherwise it leaves the top 10% free.
    val topGap = if (WindowInsets.isImeVisible) statusBar + Space.small else screen * (1 - MAX_SHEET_FRACTION)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = Shapes.sheet,
        dragHandle = {
            Box(Modifier.padding(top = Space.small).size(width = app.burrow.money.ui.theme.Size.handle, height = Space.grid).clip(Shapes.pill).background(MaterialTheme.colorScheme.outline))
        },
    ) {
        BoxWithConstraints {
            val byScreen = screen - topGap - DRAG_HANDLE - navBar
            val max = if (constraints.hasBoundedHeight) minOf(byScreen, maxHeight - topGap - DRAG_HANDLE) else byScreen
            // The sheet takes focus itself, so opening one never pops up the keyboard: before
            // Android 9 the first text field in it would get focus on its own.
            Column(Modifier.heightIn(max = max).focusable(), content = content)
        }
    }
}

/**
 * The top of every sheet: `title` and one muted line on the left, a round close button on the right
 * (prototype: 24 dp in, 16 dp above the title).
 */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit, line: String? = null) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = Space.sheet, end = Space.screen, top = Space.screen, bottom = Space.block),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = Space.small)) {
            Text(title, style = BurrowType.title, color = c.onSurface, modifier = Modifier.semantics { heading() })
            if (line != null) Text(line, style = BurrowType.body, color = c.onSurfaceVariant)
        }
        TonalIconButton(painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose)
    }
}

/**
 * Takes whatever scroll and fling a scrolling part leaves over, so none of it reaches the sheet: the
 * sheet doesn't move or close when the part runs past its top or end (time wheels, Update's notes).
 */
val KeepScrollInside: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * A sheet's scrolling body: 16 dp sides, takes what room is left, so the footer stays pinned. With
 * [keepScroll], scrolling back to the top stops there instead of dragging the sheet closed (long
 * text, like Update's notes); the header still drags it.
 */
@Composable
fun ColumnScope.SheetBody(keepScroll: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f, fill = false).then(if (keepScroll) Modifier.nestedScroll(KeepScrollInside) else Modifier).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.small),
        verticalArrangement = Arrangement.spacedBy(Space.block),
        content = content,
    )
}

/** A sheet's pinned footer (Done, Show 14 transactions): 16 dp sides, 12 dp above and below. */
@Composable
fun SheetFooter(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.block),
        horizontalArrangement = Arrangement.spacedBy(Space.inline),
        content = content,
    )
}
