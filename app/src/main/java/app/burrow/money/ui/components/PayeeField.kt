package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import app.burrow.money.R
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/**
 * Paid to (17, 79, 87): a 56 dp pill right under the amount; free text; [lead] is the storefront,
 * or the category circle once a known payee is picked. A 2 dp primary ring while typing; ✕ clears.
 */
@Composable
fun PayeeField(value: String, onChange: (String) -> Unit, placeholder: String, clearLabel: String, lead: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val text = rememberFieldText(value)
    BasicTextField(
        value = text.field, onValueChange = { text.typed(it, onChange) }, singleLine = true,
        textStyle = BurrowType.bodyLarge.copy(color = c.onSurface),
        cursorBrush = SolidColor(c.primary), interactionSource = interaction,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.widthIn(max = Size.payeeMaxWidth).fillMaxWidth(),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Size.payeeField).clip(Shapes.pill).background(c.surfaceContainerLow)
                    .then(if (focused) Modifier.border(Size.focusRing, c.primary, Shapes.pill) else Modifier)
                    .padding(horizontal = Space.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.block),
            ) {
                lead()
                Box(Modifier.weight(1f)) {
                    if (text.field.text.isEmpty()) Text(placeholder, style = BurrowType.bodyLarge, color = c.onSurfaceVariant, maxLines = 1)
                    field()
                }
                if (text.field.text.isNotEmpty()) {
                    Box(Modifier.size(Size.iconCircle).clip(Shapes.pill).background(c.surfaceContainerHigh).clickable(role = Role.Button) { text.set(""); onChange(""); focus.clearFocus() }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_close_regular), clearLabel, tint = c.onSurfaceVariant, modifier = Modifier.size(Size.iconButton20))
                    }
                }
            }
        },
    )
}

/** Paid to suggestions: up to 4 quiet chips, centred; while typing, each with its category circle. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SuggestionChips(items: List<T>, label: (T) -> String, circle: (@Composable (T) -> Unit)?, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.small, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Space.small)) {
        items.take(4).forEach { item ->
            Row(
                Modifier.heightIn(min = Size.suggestion).clip(Shapes.pill).border(Space.group / 4, c.outline, Shapes.pill)
                    .clickable(role = Role.Button) { haptics.tick(); onPick(item) }
                    .padding(start = if (circle != null) Space.grid else Space.card, end = Space.card),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                circle?.invoke(item)
                Text(label(item), style = BurrowType.button, color = c.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** One extra under the rows (Note, Repeat, Attach, Lend): an outlined chip; [value] set = selected style. */
@Composable
fun ExtraChip(icon: Painter, label: String, value: String?, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val set = value != null
    Row(
        Modifier.height(Size.chip).clip(Shapes.pill)
            .then(if (set) Modifier.background(c.secondaryContainer) else Modifier.border(Space.group / 4, c.outline, Shapes.pill))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.block),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        Icon(icon, null, tint = if (set) c.onSecondaryContainer else c.onSurfaceVariant, modifier = Modifier.size(Size.iconSmall))
        Text(value ?: label, style = BurrowType.button, color = if (set) c.onSecondaryContainer else c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = Size.payeeMaxWidth / 2))
    }
}

/** A row of [ExtraChip]s that scrolls sideways when it doesn't fit. */
@Composable
fun ExtraChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.small)) { content() }
}

/**
 * A one-tap favourite on New expense (04): 24 dp category circle, "Chai ₹20", outlined pill.
 * One tap saves it (the caller does that, with Undo).
 */
@Composable
fun FavouriteChip(label: String, circle: @Composable () -> Unit, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.heightIn(min = Size.button).clip(Shapes.pill).border(Space.group / 4, c.outline, Shapes.pill)
            .clickable(role = Role.Button) { haptics.confirm(); onClick() }
            .padding(start = Space.small, end = Space.card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        circle()
        Text(label, style = BurrowType.button, color = c.onSurface, maxLines = 1)
    }
}
