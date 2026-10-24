package app.burrow.money.ui.components

import androidx.compose.foundation.indication
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
        modifier = modifier.fillMaxWidth(), // same width as the rows under it
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

/** How a [FormChip] looks: outlined (a choice), selected (the choice made), tonal (an extra). */
enum class FormChipStyle { OUTLINED, SELECTED, TONAL }

/**
 * The one chip on New expense (17): 36 dp pill inside a 48 dp touch target. Favourites ("Chai ₹20",
 * the amount quieter), payee matches, category chips (24 dp circle + name) and the extras (Note ·
 * Receipt · Repeat or lend, tonal with a small icon).
 */
@Composable
fun FormChip(
    label: String,
    onClick: () -> Unit,
    style: FormChipStyle = FormChipStyle.OUTLINED,
    circle: (@Composable () -> Unit)? = null,
    icon: Painter? = null,
    trailing: String? = null,
) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val fg = if (style == FormChipStyle.SELECTED) c.onSecondaryContainer else c.onSurface
    // 48 dp tap target, but the press ripple stays inside the 36 dp pill (never a square around it).
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier.minimumInteractiveComponentSize()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(Size.chip).clip(Shapes.pill).indication(interaction, androidx.compose.material3.ripple())
                .then(
                    when (style) {
                        FormChipStyle.OUTLINED -> Modifier.border(Space.group / 4, c.outline, Shapes.pill)
                        FormChipStyle.SELECTED -> Modifier.background(c.secondaryContainer)
                        FormChipStyle.TONAL -> Modifier.background(c.surfaceContainer)
                    },
                )
                .padding(start = if (circle != null) Space.grid else Space.block, end = Space.block),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.small),
        ) {
            circle?.invoke()
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(Size.iconSmall))
            Text(label, style = BurrowType.button, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = Size.payeeMaxWidth / 2))
            if (trailing != null) Text(trailing, style = BurrowType.body, color = c.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A row of [FormChip]s that scrolls sideways when it doesn't fit; chips sit 8 dp apart. */
@Composable
fun FormChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.small), verticalAlignment = Alignment.CenterVertically) { content() }
}
