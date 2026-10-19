package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size

/*
 * Controls (docs/COMPONENTS.md § Buttons, § Icon button, § Chips, § Filter header, § Day stepper).
 */

/** Every icon button: 48 dp tonal circle (surfaceContainer). Active = primaryContainer. Optional count badge. */
@Composable
fun TonalIconButton(icon: Painter, label: String, onClick: () -> Unit, active: Boolean = false, badge: Int = 0) {
    val c = MaterialTheme.colorScheme
    Box {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(Size.iconButton),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (active) c.primaryContainer else c.surfaceContainer,
                contentColor = if (active) c.onPrimaryContainer else c.onSurface,
            ),
        ) { Icon(icon, contentDescription = label) }
        if (badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset((-2).dp, 2.dp).sizeIn(minWidth = 16.dp, minHeight = 16.dp).clip(Shapes.pill).background(c.primary),
                contentAlignment = Alignment.Center,
            ) { Text("$badge", style = BurrowType.label, color = c.onPrimary, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
    }
}

/**
 * Filters are ALWAYS these two header buttons (funnel + calendar), never tiles in the page.
 * Active filters show as removable chips under the search bar, plus "Clear all".
 */
@Composable
fun FilterHeaderButtons(funnel: Painter, calendar: Painter, filterLabel: String, datesLabel: String, filterCount: Int, datesActive: Boolean, onFilter: () -> Unit, onDates: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TonalIconButton(funnel, filterLabel, onFilter, active = filterCount > 0, badge = filterCount)
        TonalIconButton(calendar, datesLabel, onDates, active = datesActive)
    }
}

/** Wrapping chips (filter sheet: accounts, categories). Fits one screen: never a long list of rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipWrap(items: List<T>, label: (T) -> String, selected: (T) -> Boolean, onToggle: (T) -> Unit, haptics: HapticFeedback = LocalHapticFeedback.current) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(
                selected = selected(item),
                onClick = { haptics.tick(); onToggle(item) },
                label = { Text(label(item), style = BurrowType.button) },
                shape = Shapes.pill,
            )
        }
    }
}

/**
 * Every day choice in the app (month start, bill due day): − big day + , quick chips, one result
 * line, Done. Put it inside [BurrowSheet].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayStepper(
    day: Int,
    onDay: (Int) -> Unit,
    quick: List<Int>,
    resultLine: String,
    minus: Painter,
    minusLabel: String,
    plus: Painter,
    plusLabel: String,
    onDone: () -> Unit,
    doneLabel: String,
    ordinal: (Int) -> String,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TonalIconButton(minus, minusLabel, { haptics.tick(); onDay((day - 1).coerceAtLeast(1)) })
            Text(ordinal(day), style = BurrowType.display, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            TonalIconButton(plus, plusLabel, { haptics.tick(); onDay((day + 1).coerceAtMost(28)) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { d -> FilterChip(selected = d == day, onClick = { haptics.tick(); onDay(d) }, label = { Text(ordinal(d)) }, shape = Shapes.pill) }
        }
        Text(resultLine, style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = { haptics.confirm(); onDone() }, modifier = Modifier.fillMaxWidth().height(Size.buttonBottom), shape = Shapes.pill) { Text(doneLabel, style = BurrowType.button) }
    }
}
