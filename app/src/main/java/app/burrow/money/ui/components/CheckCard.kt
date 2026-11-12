package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.burrow.money.R
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/**
 * One card for every message Burrow wants you to confirm (To review 44, Not understood 116; final 12 Nov, the user
 * chose the design's cards over plain rows): the lead circle, title, line and amount; the SMS in an inset box (two
 * lines; tap to read it all, 243); an optional question line; a row of chips that scrolls sideways; then
 * Ignore (text) · [secondary] (outlined) · [primary] (filled, with ✓, off until [primaryEnabled]).
 *
 * The one card allowed to hold buttons (DESIGN rule 4's exception): you approve each item right in the list.
 */
@Composable
fun CheckCard(
    lead: @Composable () -> Unit,
    title: String,
    line: String,
    amount: String,
    message: String,
    marks: List<Mark>,
    expanded: Boolean,
    onToggleMessage: () -> Unit,
    ignore: String,
    onIgnore: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean,
    modifier: Modifier = Modifier,
    question: String? = null,
    questionColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    chips: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    // The design's gaps are 12 between the parts (PNG 44: 16 padding, header, 12, SMS, 12, chips 36, 12, buttons 40,
    // 16). Chips and buttons keep their 48 dp touch area, so the gaps around them give back the invisible part.
    val chipSlack = (Size.button - Size.chip) / 2
    val buttonSlack = (Size.button - Size.buttonSmall) / 2
    val toChips = Space.block - chipSlack                          // SMS → chips: 12 visible
    val questionToChips = (Space.small - chipSlack).coerceAtLeast(0.dp) // question → chips: close, as the design's 6
    val chipsToButtons = Space.block - chipSlack - buttonSlack      // chips → buttons: 12 visible
    val toButtons = Space.block - buttonSlack                       // SMS → buttons: 12 visible
    val questionToButtons = Space.small - buttonSlack               // question → buttons
    Column(modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainerLow).padding(top = Space.card, bottom = Space.card - buttonSlack)) {
        Row(Modifier.padding(horizontal = Space.card), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.card)) {
            lead()
            Column(Modifier.weight(1f)) {
                Text(title, style = BurrowType.bodyLarge, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = BurrowType.body, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(amount, style = BurrowType.titleSmall, color = c.onSurface, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.height(Space.block))
        // The SMS: two lines, the whole of it on a tap (243). Folded, its line breaks read as spaces (same length, so the
        // marks stay put): Android drops the "…" when a cut line ends in a hard break.
        val shown = if (expanded) message else message.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
        MessageText(
            shown, marks, compact = true, color = c.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else MESSAGE_LINES,
            modifier = Modifier.padding(horizontal = Space.card).fillMaxWidth().clip(Shapes.inset).background(c.background)
                .clickable(role = Role.Button, onClick = onToggleMessage)
                .padding(horizontal = Space.insetH, vertical = Space.insetV),
        )
        if (question != null) {
            Spacer(Modifier.height(Space.block))
            Text(question, style = BurrowType.label, color = questionColor, modifier = Modifier.padding(horizontal = Space.card + Space.grid))
        }
        if (chips != null) {
            // Under a question the chips follow closely (the design's 6); after the SMS, the usual 12.
            Spacer(Modifier.height(if (question != null) questionToChips else toChips))
            // Chips scroll sideways to the card's edges (the design's nowrap row), never wrapping into a block.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.card),
                horizontalArrangement = Arrangement.spacedBy(Space.small),
                verticalAlignment = Alignment.CenterVertically,
                content = chips,
            )
            Spacer(Modifier.height(chipsToButtons))
        } else {
            Spacer(Modifier.height(if (question != null) questionToButtons else toButtons))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.card),
            horizontalArrangement = Arrangement.spacedBy(Space.small, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinkButton(ignore, onIgnore)
            BurrowButton(secondary, onSecondary, kind = ButtonKind.OUTLINED, height = Size.buttonSmall)
            BurrowButton(
                primary, onPrimary, kind = if (primaryEnabled) ButtonKind.FILLED else ButtonKind.OFF,
                icon = painterResource(R.drawable.ic_check_regular), height = Size.buttonSmall,
            )
        }
    }
}

private const val MESSAGE_LINES = 2
