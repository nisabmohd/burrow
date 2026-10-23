package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.categoryColors

/*
 * Lists and rows (docs/COMPONENTS.md § List group, § Row, § Icon circle, § Section header).
 * A group = rows 2 dp apart, 24 dp outer corners, 6 dp between rows. Never a divider line.
 */

/** 40 dp circle with a 22 dp duotone icon. Category: its colours. Neutral: surfaceContainer. */
@Composable
fun IconCircle(
    icon: Painter,
    modifier: Modifier = Modifier,
    hue: Float? = null,
    size: Dp = Size.iconCircle,
    container: Color? = null,
    content: Color? = null,
) {
    val c = hue?.let { categoryColors(it) }
    val bg = container ?: c?.container ?: MaterialTheme.colorScheme.surfaceContainer
    val fg = content ?: c?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.size(size).clip(Shapes.pill).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * The gap the enclosing column already puts between its items (a page column spaced by
 * [Space.block]). [SectionHeader] takes it off its own padding, so a header sits the same distance
 * from the block above and from its group below on every screen. Provide it with [BlockGap].
 */
val LocalBlockGap = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

/**
 * A page body: blocks [Space.block] apart, and section headers inside it keep the standard gaps.
 * Use it for every scrolling page column instead of a hand-spaced Column.
 */
@Composable
fun BlockColumn(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) = BlockGap {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.block), content = content)
}

/** Wrap a lazy list spaced by [Space.block] that holds [SectionHeader]s. */
@Composable
fun BlockGap(content: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalBlockGap provides Space.block, content = content)

/**
 * Section header: 14 Medium title left, meta (body, muted) or a text button right. Its text starts
 * 24 dp from the screen edge like every title; [Space.sectionTop] above, [Space.sectionBottom] to
 * its group, whatever column it sits in.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, meta: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null, titleColor: Color = MaterialTheme.colorScheme.onSurface) {
    val gap = LocalBlockGap.current
    Row(
        modifier.fillMaxWidth()
            // The column adds `gap` below as well: report that much less height, so header → group stays sectionBottom.
            .layout { m, c ->
                val p = m.measure(c)
                layout(p.width, (p.height - gap.roundToPx()).coerceAtLeast(0)) { p.place(0, 0) }
            }
            .padding(start = Space.headingInset, end = Space.headingInset, top = (Space.sectionTop - gap).coerceAtLeast(0.dp), bottom = Space.sectionBottom)
            .heightIn(min = Size.chip),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = BurrowType.button, color = titleColor, modifier = Modifier.weight(1f))
        when {
            trailing != null -> trailing()
            meta != null -> Text(meta, style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The one list row: 72 dp (56 when there is no supporting line), lead circle, headline + one
 * supporting line, trailing slot (amount, ring, switch, chevron). Selected rows use secondaryContainer.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    headline: String,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selected: Boolean = false,
    lead: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    /** Long-press (450 ms): a transaction's actions sheet (06). */
    onLongClick: (() -> Unit)? = null,
    /** Being dragged (16): it lifts onto surfaceContainerHigh with all corners round. */
    lifted: Boolean = false,
    headlineColor: Color = MaterialTheme.colorScheme.onSurface,
    /** A small icon before the supporting line (Coming up: ↻ repeats, 📅 one-off). */
    supportingIcon: androidx.compose.ui.graphics.painter.Painter? = null,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shape = if (lifted) Shapes.surface else Shapes.grouped(index, count)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting == null) Size.rowOneLine else Size.row)
            .then(if (lifted) Modifier.shadow(Size.dragLift, shape) else Modifier)
            .clip(shape)
            .background(when { lifted -> colors.surfaceContainerHigh; selected -> colors.secondaryContainer; else -> colors.surfaceContainerLow })
            .then(
                when {
                    onLongClick != null -> Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLongClick() })
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
            )
            .padding(horizontal = Space.card, vertical = Space.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.card),
    ) {
        lead?.invoke()
        Column(Modifier.weight(1f)) {
            // Large text (Day 26): rows grow to two lines instead of cutting words off.
            val lines = if (LocalDensity.current.fontScale > LARGE_TEXT) 2 else 1
            Text(headline, style = BurrowType.bodyLarge, color = headlineColor, maxLines = lines, overflow = TextOverflow.Ellipsis)
            if (supporting != null && supportingIcon != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.grid)) {
                    androidx.compose.material3.Icon(supportingIcon, null, tint = supportingColor, modifier = Modifier.size(Size.iconSmall))
                    Text(supporting, style = BurrowType.body, color = supportingColor, maxLines = lines, overflow = TextOverflow.Ellipsis)
                }
            } else if (supporting != null) Text(supporting, style = BurrowType.body, color = supportingColor, maxLines = lines, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** Lays out a group: items with the 2 dp gap. Use with [ListRow] (index/count for the corners). */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.row)) { content() }
}

/** Trailing amount over a small status ("₹649" over "in 6 days"), right-aligned. */
@Composable
fun AmountTrailing(amount: String, status: String? = null, statusColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Column(horizontalAlignment = Alignment.End) {
        Text(amount, style = BurrowType.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (status != null) Text(status, style = BurrowType.label, color = statusColor)
    }
}


/**
 * A note or group that appears at the very top of a list (overdue, paused, an Undo that brings an
 * overdue bill back) would land above the first visible item, and a lazy list keeps that item in
 * place, so it would sit just off screen. When [topKey] changes while the list is at the very top,
 * stay at the very top. Decided during composition, from the layout before the change.
 */
@Composable
fun KeepTopVisible(state: androidx.compose.foundation.lazy.LazyListState, topKey: Any?) {
    // A plain holder, not state: noting the key mustn't cause another recomposition.
    val last = androidx.compose.runtime.remember { arrayOf(topKey) }
    if (last[0] != topKey) {
        last[0] = topKey
        if (state.firstVisibleItemIndex <= 1 && state.firstVisibleItemScrollOffset == 0) state.requestScrollToItem(0)
    }
}

/** From this font scale, text is "large": rows wrap to two lines, notes put their action below. */
const val LARGE_TEXT = 1.3f

/** A 40 dp circle with a letter (Import: Cashew, Walnut, Money Manager; no brand logos, 10). */
@Composable
fun LetterCircle(letter: String, modifier: Modifier = Modifier) {
    Box(modifier.size(Size.iconCircle).clip(Shapes.pill).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        Text(letter, style = BurrowType.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
