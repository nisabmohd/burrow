package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.burrow.money.R
import app.burrow.money.ui.theme.Space
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
fun <T> ChipWrap(items: List<T>, label: (T) -> String, selected: (T) -> Boolean, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small)) {
        items.forEach { item -> Chip(label(item), selected(item), { onToggle(item) }) }
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
    max: Int = 28,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TonalIconButton(minus, minusLabel, { haptics.tick(); onDay((day - 1).coerceAtLeast(1)) })
            Text(ordinal(day), style = BurrowType.display, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            TonalIconButton(plus, plusLabel, { haptics.tick(); onDay((day + 1).coerceAtMost(max)) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { d -> Chip(ordinal(d), d == day, { onDay(d) }) }
        }
        Text(resultLine, style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = { haptics.confirm(); onDone() }, modifier = Modifier.fillMaxWidth().height(Size.buttonBottom), shape = Shapes.pill) { Text(doneLabel, style = BurrowType.button) }
    }
}

/** The kinds of button (docs/COMPONENTS.md § Buttons). [OFF] looks disabled but still takes taps. */
enum class ButtonKind { FILLED, TONAL, DANGER, OFF }

/**
 * Every button: a 48 dp pill (56 dp at the bottom of a screen or sheet: pass [height]).
 * An [OFF] button still calls [onClick], so a form can show what is missing ("can't save yet").
 */
@Composable
fun BurrowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.FILLED,
    icon: Painter? = null,
    height: androidx.compose.ui.unit.Dp = Size.button,
) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (kind) {
        ButtonKind.FILLED -> c.primary to c.onPrimary
        ButtonKind.TONAL -> c.secondaryContainer to c.onSecondaryContainer
        ButtonKind.DANGER -> c.errorContainer to c.onErrorContainer
        ButtonKind.OFF -> c.onSurface.copy(alpha = 0.10f) to c.onSurface.copy(alpha = 0.38f)
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = Shapes.pill,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        contentPadding = PaddingValues(horizontal = Space.sheet),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Size.iconButton20))
            Spacer(Modifier.width(Space.small))
        }
        Text(text, style = BurrowType.button, maxLines = 1)
    }
}

/**
 * Connected button group (Expense / Income / Transfer, Bills / Budgets / Goals): 48 dp pills 4 dp
 * apart; the selected one is primary with a check.
 */
@Composable
fun <T> ButtonGroup(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.group)) {
        options.forEach { option ->
            val on = option == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(Size.button)
                    .clip(Shapes.pill)
                    .background(if (on) c.primary else c.surfaceContainer)
                    .selectable(selected = on, role = Role.RadioButton) { if (!on) { haptics.tick(); onSelect(option) } },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (on) c.onPrimary else c.onSurfaceVariant
                if (on) {
                    Icon(painterResource(R.drawable.ic_check_regular), null, tint = fg, modifier = Modifier.size(Size.iconSmall))
                    Spacer(Modifier.width(Space.group))
                }
                // Long translations and large text shrink to fit instead of clipping (never below 10 sp).
                androidx.compose.foundation.text.BasicText(
                    label(option), style = BurrowType.button.copy(color = fg), maxLines = 1,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = ButtonMinText, maxFontSize = BurrowType.button.fontSize),
                )
            }
        }
    }
}

private val ButtonMinText = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)

/**
 * The one text field: a 56 dp pill on surfaceContainer (search bar, payee, note, names), optional
 * leading icon, and a clear button while it has text.
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: Painter? = null,
    clearLabel: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    /** Dots instead of the text (backup password). */
    password: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = BurrowType.bodyLarge.copy(color = c.onSurface),
        cursorBrush = SolidColor(c.primary),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = imeAction, keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onAny = { onDone() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Size.search).clip(Shapes.pill).background(c.surfaceContainer)
                    .padding(start = if (leading != null) Space.card else Space.sheet, end = Space.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, null, tint = c.onSurfaceVariant, modifier = Modifier.size(Size.icon))
                    Spacer(Modifier.width(Space.card))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = BurrowType.bodyLarge, color = c.onSurfaceVariant, maxLines = 1)
                    field()
                }
                if (value.isNotEmpty() && clearLabel != null) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(painterResource(R.drawable.ic_close_regular), clearLabel, tint = c.onSurfaceVariant)
                    }
                }
            }
        },
    )
}

/** A text button in primary ("See all", "Clear all"): the only coloured text besides errors. */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = BurrowType.button, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The one chip: a 36 dp pill. Selected = secondaryContainer with a check; otherwise an outline.
 * [icon] shows when not selected ("More").
 */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Painter? = null) {
    val haptics = LocalHapticFeedback.current
    FilterChip(
        selected = selected,
        onClick = { haptics.tick(); onClick() },
        label = { Text(label, style = BurrowType.button, maxLines = 1) },
        modifier = modifier.height(Size.chip),
        shape = Shapes.pill,
        leadingIcon = when {
            selected -> { { Icon(painterResource(R.drawable.ic_check_regular), null, modifier = Modifier.size(Size.iconSmall)) } }
            icon != null -> { { Icon(icon, null, modifier = Modifier.size(Size.iconSmall)) } }
            else -> null
        },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

/** Material 3 switch with the app's toggle haptic. TalkBack reads the row it sits in. */
@Composable
fun BurrowSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    androidx.compose.material3.Switch(checked = checked, onCheckedChange = { haptics.toggle(it); onChange(it) }, modifier = modifier)
}

/** The trailing arrow of a row that opens another screen (the drawable mirrors in RTL: autoMirrored). */
@Composable
fun Chevron() = Icon(
    painterResource(R.drawable.ic_chevron_right_regular), null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(Size.icon),
)
