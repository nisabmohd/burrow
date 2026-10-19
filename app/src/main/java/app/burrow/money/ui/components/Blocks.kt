package app.burrow.money.ui.components

import androidx.compose.foundation.background
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
    Row(
        modifier.fillMaxWidth().heightIn(min = Size.note).clip(Shapes.surface).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.card, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.card),
    ) {
        Box(Modifier.size(Size.iconCircle).clip(Shapes.pill).background(circle), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = onCircle, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = BurrowType.titleSmall, color = fg)
            if (line != null) Text(line, style = BurrowType.body, color = fg.copy(alpha = 0.85f))
        }
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, style = BurrowType.button, color = fg) }
        else if (arrow != null) Icon(arrow, null, tint = fg)
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
) {
    val c = MaterialTheme.colorScheme
    // Size first, then shape and padding, so a half widget is 184 dp tall in total.
    val base = modifier.then(if (wide) Modifier.fillMaxWidth() else Modifier.height(Size.widgetHalf))
        .clip(Shapes.surface).background(c.surfaceContainerLow)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(Space.card)
    if (wide) {
        Row(base, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { WidgetText(label, icon, value, line) }
            visual?.invoke()
        }
    } else {
        Column(base) {
            WidgetText(label, icon, value, line)
            Spacer(Modifier.weight(1f))
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
    Spacer(Modifier.height(12.dp))
    Text(value, style = BurrowType.title, color = c.onSurface, maxLines = 1)
    Text(line, style = BurrowType.body, color = c.onSurfaceVariant, maxLines = 2)
}

/** Empty state: 128 dp wavy "cookie" shape (primaryContainer) with the icon centred at 42%, title, one line, one tonal button. */
@Composable
fun EmptyState(shape: @Composable () -> Unit, title: String, line: String, button: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        shape()
        Spacer(Modifier.height(16.dp))
        Text(title, style = BurrowType.title, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(line, style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (button != null) { Spacer(Modifier.height(20.dp)); button() }
    }
}
