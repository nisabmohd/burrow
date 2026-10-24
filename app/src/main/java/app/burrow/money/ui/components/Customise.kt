package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.burrow.money.R
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/*
 * The four blocks every Customise tab is built from (docs/DESIGN.md § Simple, not bare):
 * choice tiles (pick one), toggle tiles (pick many), ordered rows (show/hide + move), switch rows
 * (a ListRow with a BurrowSwitch). Plus PickList: a grouped list with round radio marks (80, 85).
 */

/**
 * The round check (COMPONENTS § Round check): every pick-one list, sheet or screen, Teach rows and
 * selected tiles. 24 dp: filled primary with a ✓ when [on]; an empty ring when off and [ring] is set.
 */
@Composable
fun CheckBadge(on: Boolean, modifier: Modifier = Modifier, ring: Boolean = false) {
    val c = MaterialTheme.colorScheme
    when {
        on -> Box(modifier.size(Size.checkBadge).clip(Shapes.pill).background(c.primary), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_check_regular), null, tint = c.onPrimary, modifier = Modifier.size(Size.checkInBadge))
        }
        ring -> Box(modifier.size(Size.checkBadge).clip(Shapes.pill).border(Space.group / 2, c.outline, Shapes.pill))
    }
}

/** One option of [ChoiceTiles]: a title, an optional line or value, an optional icon. */
@Immutable
data class Choice<T>(val key: T, val title: String, val line: String? = null, val icon: Painter? = null)

/**
 * Pick one (81 "Opens as", 82 "When a payment is found", 83 "Show amounts as" / "Decimal places").
 * With icons: full-width rows (icon circle, title, line, check). Otherwise equal tiles, [columns] a
 * row; a tile with a line shows the line big (the value: "₹1,240").
 */
@Composable
fun <T> ChoiceTiles(options: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, columns: Int = 3) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    if (options.any { it.icon != null }) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.small)) {
            options.forEach { o ->
                val on = o.key == selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Size.row).clip(Shapes.surface)
                        .background(if (on) c.primaryContainer else c.surfaceContainerLow)
                        .semantics { this.selected = on }
                        .clickable(role = Role.RadioButton) { haptics.tick(); onSelect(o.key) }
                        .padding(horizontal = Space.card, vertical = Space.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.card),
                ) {
                    o.icon?.let { IconCircle(it, container = if (on) c.primary else null, content = if (on) c.onPrimary else null) }
                    Column(Modifier.weight(1f)) {
                        Text(o.title, style = BurrowType.titleSmall, color = if (on) c.onPrimaryContainer else c.onSurface)
                        o.line?.let { Text(it, style = BurrowType.body, color = if (on) c.onPrimaryContainer else c.onSurfaceVariant) }
                    }
                    CheckBadge(on)
                }
            }
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.small)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                row.forEach { o ->
                    val on = o.key == selected
                    Box(
                        Modifier.weight(1f).heightIn(min = if (o.line != null) Size.choiceTile else Size.button)
                            .clip(Shapes.surface).background(if (on) c.primaryContainer else c.surfaceContainerLow)
                            .semantics { this.selected = on }
                            .clickable(role = Role.RadioButton) { haptics.tick(); onSelect(o.key) }
                            .padding(horizontal = Space.block, vertical = Space.block),
                    ) {
                        val fg = if (on) c.onPrimaryContainer else c.onSurface
                        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth()) {
                            // The check sits on the title's line only; the value below gets the full
                            // width and shrinks to fit instead of clipping ("1,240.00" in a third).
                            Row(Modifier.fillMaxWidth().heightIn(min = Size.checkBadge), verticalAlignment = Alignment.CenterVertically) {
                                Text(o.title, style = if (o.line != null) BurrowType.label else BurrowType.button, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                CheckBadge(on)
                            }
                            o.line?.let {
                                androidx.compose.foundation.text.BasicText(
                                    it, style = BurrowType.title.copy(color = fg), maxLines = 1,
                                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = BurrowType.label.fontSize, maxFontSize = BurrowType.title.fontSize),
                                )
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** One option of [ToggleTiles]. */
@Immutable
data class Toggle(val key: String, val title: String, val icon: Painter, val on: Boolean)

/** Pick many (81 extras, 82 lists): 3 tiles a row, icon circle + name, a check badge when on. */
@Composable
fun ToggleTiles(options: List<Toggle>, onToggle: (String, Boolean) -> Unit, onLabel: String, offLabel: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.small)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                row.forEach { o ->
                    Box(
                        Modifier.weight(1f).heightIn(min = Size.toggleTile).clip(Shapes.surface)
                            .background(if (o.on) c.secondaryContainer else c.surfaceContainerLow)
                            .semantics { stateDescription = if (o.on) onLabel else offLabel }
                            .clickable(role = Role.Checkbox) { haptics.toggle(!o.on); onToggle(o.key, !o.on) }
                            .padding(Space.small),
                    ) {
                        CheckBadge(o.on, Modifier.align(Alignment.TopEnd), ring = true)
                        Column(Modifier.align(Alignment.Center).padding(top = Space.small), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.small)) {
                            IconCircle(o.icon, container = if (o.on) c.primaryContainer else c.surfaceContainer, content = if (o.on) c.onPrimaryContainer else c.onSurfaceVariant)
                            Text(o.title, style = BurrowType.button, color = if (o.on) c.onSecondaryContainer else c.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Show/hide and order (80 widgets, 16): ⠿ handle · icon · name · switch. Drag the handle to reorder
 * (inside [DragColumn]); hidden rows dim. No up/down arrows anywhere.
 */
@Composable
fun OrderedRow(
    icon: Painter,
    name: String,
    index: Int,
    count: Int,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    handle: @Composable () -> Unit,
    lifted: Boolean,
) {
    ListRow(
        name, index, count, lifted = lifted,
        lead = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                handle()
                Box(Modifier.alpha(if (on) 1f else HIDDEN_ALPHA)) { IconCircle(icon) }
            }
        },
        trailing = { BurrowSwitch(on, onToggle) },
    )
}

/**
 * One row of a pick-one list (C1, 2026-10-24): every pick sheet and screen (Type 111, Account 20,
 * Option 138, currency, language, backup schedule, lock after…). A plain row, never tinted; the round
 * check on the right says which is chosen. [ring] false = only the chosen row shows its check (the long
 * Category sheet, 19).
 */
@Composable
fun PickRow(
    headline: String,
    index: Int,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
    lead: (@Composable () -> Unit)? = null,
    ring: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    ListRow(
        headline, index, count, supporting = supporting,
        modifier = Modifier.semantics { this.selected = selected },
        lead = lead,
        trailing = { CheckBadge(selected, ring = ring) },
        onClick = { haptics.tick(); onClick() },
    )
}

/** Pick one from a grouped list with round radio marks on the right (80 top card, 85 numbers / dates). */
@Composable
fun <T> PickList(options: List<Choice<T>>, selected: T, onSelect: (T) -> Unit) {
    ListGroup {
        options.forEachIndexed { i, o -> PickRow(o.title, i, options.size, o.key == selected, { onSelect(o.key) }, supporting = o.line) }
    }
}

private const val HIDDEN_ALPHA = 0.55f
