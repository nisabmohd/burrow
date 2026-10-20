package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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

/** Section header: 14 Medium title left, meta (body, muted) or a text button right. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, meta: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 4.dp, end = 4.dp, top = Space.sectionTop, bottom = Space.sectionBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = BurrowType.button, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
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
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting == null) Size.rowOneLine else Size.row)
            .clip(Shapes.grouped(index, count))
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.card, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.card),
    ) {
        lead?.invoke()
        Column(Modifier.weight(1f)) {
            Text(headline, style = BurrowType.bodyLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) Text(supporting, style = BurrowType.body, color = supportingColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
 * A note that appears at the very top of a list (overdue, paused) would land above the first
 * visible item, and a lazy list keeps that item in place, so the note would sit just off screen.
 * When [topKey] changes and the list is at (or next to) the top, scroll back to the top.
 */
@Composable
fun KeepTopVisible(state: androidx.compose.foundation.lazy.LazyListState, topKey: Any?) {
    androidx.compose.runtime.LaunchedEffect(topKey) {
        if (state.firstVisibleItemIndex <= 1) state.scrollToItem(0)
    }
}
