package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.burrow.money.R
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Space

/**
 * A message the app found, as a card (To review 44, Not understood 116): [lead] circle, [title] and
 * [line], an optional [amount], ✕ to ignore; the message ([message], usually a [MessageBox]);
 * optional [extra] (category chips); one full-width tonal [button]. The only cards with a button
 * (user, 2026-10-24) — they never swipe (C2). TalkBack gets the button and Ignore as actions.
 */
@Composable
fun MessageCard(
    lead: @Composable () -> Unit,
    title: String,
    line: String,
    ignoreLabel: String,
    onIgnore: () -> Unit,
    button: String,
    buttonIcon: Painter,
    onButton: () -> Unit,
    modifier: Modifier = Modifier,
    amount: String? = null,
    extra: (@Composable () -> Unit)? = null,
    message: @Composable () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainerLow).padding(Space.card)
            .semantics { customActions = listOf(CustomAccessibilityAction(button) { onButton(); true }, CustomAccessibilityAction(ignoreLabel) { onIgnore(); true }) },
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.card)) {
            lead()
            // Large text: the amount goes under the payee so the name keeps the width.
            val large = isLargeText()
            Column(Modifier.weight(1f)) {
                Text(title, style = BurrowType.bodyLarge, color = c.onSurface, maxLines = if (large) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (large && amount != null) Text(amount, style = BurrowType.titleSmall, color = c.onSurface)
                Text(line, style = BurrowType.body, color = c.onSurfaceVariant, maxLines = 2)
            }
            if (!large && amount != null) Text(amount, style = BurrowType.titleSmall, color = c.onSurface)
            TonalIconButton(painterResource(R.drawable.ic_close_regular), ignoreLabel, onIgnore)
        }
        message()
        extra?.invoke()
        BurrowButton(button, onButton, Modifier.fillMaxWidth(), ButtonKind.TONAL, buttonIcon)
    }
}
