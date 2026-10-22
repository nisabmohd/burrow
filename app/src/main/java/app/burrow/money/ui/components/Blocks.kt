package app.burrow.money.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import app.burrow.money.ui.theme.WidgetValueMinSize
import androidx.compose.ui.unit.dp
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.warningColors

/*
 * Page-level building blocks (docs/COMPONENTS.md). The "Lite" rules:
 * - ONE hero in the app (Today's budget). Every other screen starts with [CenterHeader].
 * - Notes always sit at the top of a page, before any card ([Note]).
 * - No buttons inside cards; one main action per screen.
 */

/**
 * The centred header used by every non-tab screen and list summary: 64 dp icon circle, title,
 * big plain amount, one line, optional thin progress bar. Over budget: amount and line in error.
 */
@Composable
fun CenterHeader(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    line: String? = null,
    progress: Float? = null,
    bad: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Spacer(Modifier.height(12.dp))
        Text(title, style = BurrowType.titleSmall, color = c.onSurface)
        Text(value, style = BurrowType.display, color = if (bad) c.error else c.onSurface)
        if (line != null) Text(line, style = BurrowType.body, color = if (bad) c.error else c.onSurfaceVariant, textAlign = TextAlign.Center)
        if (progress != null) {
            Spacer(Modifier.height(12.dp))
            ThinBar(progress, if (bad) c.error else c.primary, Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** 8 dp rounded progress bar. */
@Composable
fun ThinBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(Shapes.pill).background(MaterialTheme.colorScheme.secondaryContainer)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp).clip(Shapes.pill).background(color))
    }
}

enum class NoteKind { INFO, WARN, ERROR }

/**
 * The one notice component (72 dp, 24 dp corners): solid 40 dp icon circle, title + one line,
 * ONE text button or an arrow when the whole note is tappable. Always first on the page.
 */
@Composable
fun Note(
    kind: NoteKind,
    icon: Painter,
    title: String,
    line: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    arrow: Painter? = null,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val w = warningColors()
    val (bg, fg, circle, onCircle) = when (kind) {
        NoteKind.INFO -> listOf(c.secondaryContainer, c.onSecondaryContainer, c.primary, c.onPrimary)
        NoteKind.WARN -> listOf(w.container, w.onContainer, w.onContainer, w.container)
        NoteKind.ERROR -> listOf(c.errorContainer, c.onErrorContainer, c.error, c.errorContainer)
    }
    // Large text: the action moves under the words so the title never breaks mid-word.
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale > LARGE_TEXT && action != null && onAction != null
    Column(
        modifier.fillMaxWidth().heightIn(min = Size.note).clip(Shapes.surface).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.card, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.card)) {
            Box(Modifier.size(Size.iconCircle).clip(Shapes.pill).background(circle), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = onCircle, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = BurrowType.titleSmall, color = fg)
                if (line != null) Text(line, style = BurrowType.body, color = fg.copy(alpha = 0.85f))
            }
            if (!stacked && action != null && onAction != null) TextButton(onClick = onAction) { Text(action, style = BurrowType.button, color = fg) }
            else if (arrow != null) Icon(arrow, null, tint = fg)
        }
        if (stacked) TextButton(onClick = onAction!!, modifier = Modifier.align(Alignment.End)) { Text(action!!, style = BurrowType.button, color = fg) }
    }
}

/**
 * Home / Insights widget. Half width = fixed 184 dp so a row is always even. A widget alone on a
 * row becomes [wide]: text left, visual right (104 dp ring), height wraps content.
 */
@Composable
fun Widget(
    label: String,
    icon: Painter,
    value: String,
    line: String,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    onClick: (() -> Unit)? = null,
    visual: (@Composable () -> Unit)? = null,
    /** Extra content under the text (Money in and out's figures), wide widgets only. */
    body: (@Composable ColumnScope.() -> Unit)? = null,
    /** Half widgets are 184 dp; Plan's two small ones size to their content (22). */
    fixedHeight: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    // Size first, then shape and padding, so a half widget is 184 dp tall in total.
    val base = modifier.then(if (wide) Modifier.fillMaxWidth() else if (fixedHeight) Modifier.height(Size.widgetHalf) else Modifier)
        .clip(Shapes.surface).background(c.surfaceContainerLow)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(Space.card)
    if (wide) {
        Column(base) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { WidgetText(label, icon, value, line) }
                visual?.invoke()
            }
            body?.invoke(this)
        }
    } else {
        Column(base) {
            WidgetText(label, icon, value, line)
            if (fixedHeight) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(Space.block))
            visual?.invoke()
        }
    }
}

@Composable
private fun ColumnScope.WidgetText(label: String, icon: Painter, value: String, line: String) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(18.dp))
        Text(label, style = BurrowType.label, color = c.onSurfaceVariant)
    }
    if (value.isNotEmpty()) {
        Spacer(Modifier.height(Space.block))
        // One line always: a long name ("Food & drinks" at large text) shrinks to fit, then ends in "…".
        BasicText(
            value, style = BurrowType.title.copy(color = c.onSurface), maxLines = 1, overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = WidgetValueMinSize, maxFontSize = BurrowType.title.fontSize),
        )
    }
    if (line.isNotEmpty()) Text(line, style = BurrowType.body, color = c.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

/** Empty state: 128 dp wavy "cookie" shape (primaryContainer) with the icon centred at 42%, title, one line, one tonal button. */
@Composable
fun EmptyState(shape: @Composable () -> Unit, title: String, line: String, button: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        shape()
        Spacer(Modifier.height(16.dp))
        Text(title, style = BurrowType.title, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(line, style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (button != null) { Spacer(Modifier.height(Space.hero)); button() }
    }
}

/**
 * The wavy "cookie" behind an empty state's icon (prototype `cookie()`): 9 soft bumps,
 * primaryContainer, the icon centred at 42% in onPrimaryContainer.
 */
@Composable
fun Cookie(icon: Painter, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(modifier.size(Size.emptyShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r0 = size.minDimension / 2
            val path = Path()
            for (i in 0..180) {
                val t = 2 * Math.PI * i / 180
                val r = r0 * (1 - COOKIE_AMP + COOKIE_AMP * cos(COOKIE_BUMPS * t))
                val x = (center.x + r * cos(t)).toFloat()
                val y = (center.y + r * sin(t)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, c.primaryContainer)
        }
        Icon(icon, null, tint = c.onPrimaryContainer, modifier = Modifier.fillMaxSize(0.42f))
    }
}

private const val COOKIE_BUMPS = 9
private const val COOKIE_AMP = 0.075

/** The 64 dp icon circle that opens a centred header (primaryContainer). */
@Composable
fun HeaderIcon(icon: Painter) {
    val c = MaterialTheme.colorScheme
    IconCircle(icon, size = Size.iconCircleBig, container = c.primaryContainer, content = c.onPrimaryContainer)
}

/**
 * The centred header when the big line is words, not an amount (Auto-capture: "142 payments
 * recorded"): 64 dp icon circle, `title`, one muted line.
 */
@Composable
fun CenterTitleHeader(icon: Painter, title: String, line: String? = null, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(vertical = Space.small), horizontalAlignment = Alignment.CenterHorizontally) {
        HeaderIcon(icon)
        Spacer(Modifier.height(Space.block))
        Text(title, style = BurrowType.title, color = c.onSurface, textAlign = TextAlign.Center)
        if (line != null) Text(line, style = BurrowType.body, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * A More status tile (53): two to a row, 24 dp, a coloured circle top-left, the status top-right
 * ("On", "2:10 PM"), a title and one line. Tinted while the thing is on.
 */
@Composable
fun StatusTile(icon: Painter, hue: Float, status: String?, title: String, line: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.clip(Shapes.surface).background(if (on) c.secondaryContainer else c.surfaceContainerLow).clickable(onClick = onClick).padding(Space.card),
        verticalArrangement = Arrangement.spacedBy(Space.group),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            IconCircle(icon, hue = hue)
            Spacer(Modifier.weight(1f))
            if (status != null) Text(status, style = BurrowType.label, color = c.onSurfaceVariant)
        }
        Spacer(Modifier.height(Space.small))
        Text(title, style = BurrowType.titleSmall, color = c.onSurface, maxLines = 1)
        Text(line, style = BurrowType.body, color = c.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
