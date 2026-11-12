package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.burrow.money.R
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/**
 * One card for every message Burrow wants you to confirm (To review 44, Not understood 116; final 12 Nov kit): the
 * lead circle, title, line, amount and ✕ (ignore); the whole SMS with its bank's name ([MessageBox] inCard: the one
 * message text); an optional question; chips that wrap (category chips as New expense, or type chips); then two
 * equal buttons, [secondary] (outlined, pencil) and [primary] (tonal, ✓, off until [primaryEnabled]).
 *
 * The one card allowed to hold buttons (DESIGN rule 4's exception): you approve each item right in the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckCard(
    lead: @Composable () -> Unit,
    title: String,
    line: String,
    amount: String,
    messageFrom: String?,
    message: String,
    marks: List<Mark>,
    onIgnore: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean,
    modifier: Modifier = Modifier,
    question: String? = null,
    questionColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    chips: (@Composable FlowRowScope.() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    // The design's gaps are 12 between the parts; chips and buttons keep their 48 dp touch area, so the gaps around
    // them give back its invisible part (chips 36 → 6 a side, buttons 40 → 4 a side).
    val chipSlack = (Size.button - Size.chip) / 2
    val buttonSlack = (Size.button - Size.buttonSmall) / 2
    val toChips = Space.block - chipSlack
    val questionToChips = Space.small - chipSlack
    val chipsToButtons = Space.block - chipSlack - buttonSlack
    val toButtons = Space.block - buttonSlack
    val questionToButtons = Space.small - buttonSlack
    Column(modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainerLow).padding(start = Space.card, end = Space.card, top = Space.block, bottom = Space.card - buttonSlack)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.block)) {
            lead()
            // Large text: the amount goes under the name, so the name and its line keep the row (rows wrap, CLAUDE.md).
            val large = isLargeText()
            Column(Modifier.weight(1f)) {
                Text(title, style = BurrowType.bodyLarge, color = c.onSurface, maxLines = if (large) 2 else 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = BurrowType.body, color = c.onSurfaceVariant, maxLines = if (large) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (large) Text(amount, style = BurrowType.titleSmall, color = c.onSurface, maxLines = 1, softWrap = false)
            }
            if (!large) Text(amount, style = BurrowType.titleSmall, color = c.onSurface, maxLines = 1, softWrap = false)
            TonalIconButton(painterResource(R.drawable.ic_close_regular), stringResource(R.string.ignore), onIgnore)
        }
        Spacer(Modifier.height(Space.small))
        MessageBox(messageFrom, message, marks, inCard = true)
        if (question != null) {
            Spacer(Modifier.height(Space.block))
            Text(question, style = BurrowType.body, color = questionColor, modifier = Modifier.padding(horizontal = Space.grid))
        }
        if (chips != null) {
            Spacer(Modifier.height(if (question != null) questionToChips else toChips))
            // Chips wrap onto lines (the final kit), never a sideways scroll.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.small),
                content = chips,
            )
            Spacer(Modifier.height(chipsToButtons))
        } else {
            Spacer(Modifier.height(if (question != null) questionToButtons else toButtons))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            BurrowButton(
                secondary, onSecondary, Modifier.weight(1f), ButtonKind.OUTLINED,
                icon = painterResource(R.drawable.ic_edit_regular), height = Size.buttonSmall,
            )
            BurrowButton(
                primary, onPrimary, Modifier.weight(1f), if (primaryEnabled) ButtonKind.TONAL else ButtonKind.OFF,
                icon = painterResource(R.drawable.ic_check_regular), height = Size.buttonSmall,
            )
        }
    }
}
