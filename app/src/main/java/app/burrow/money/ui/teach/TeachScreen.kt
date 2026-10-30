package app.burrow.money.ui.teach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.capture.Part
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LinkButton
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.Mark
import app.burrow.money.ui.components.MarkStyle
import app.burrow.money.ui.components.MessageBox
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.tick
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Space

/**
 * Teach a message (52): close · title · Save; one instruction line; the message, where a tap picks
 * the word for the current part; rows for Amount, From / to, Account and Money in or out (the current one tinted).
 * Save turns on once the rule built from the taps reads the message back.
 */
@Composable
fun TeachScreen(vm: TeachViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = MaterialTheme.colorScheme
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val save = {
        if (s.canSave) {
            haptics.confirm()
            vm.save { moved ->
                snackbar.show(
                    if (moved > 0) context.resources.getQuantityString(R.plurals.teach_saved_moved, moved, moved)
                    else context.getString(R.string.teach_saved),
                )
                onClose()
            }
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose, title = stringResource(R.string.teach_title)) {
            // Save only (52): ignoring a message is the ✕ on its card in Not understood (116).
            BurrowButton(stringResource(R.string.save), save, kind = if (s.canSave) ButtonKind.FILLED else ButtonKind.OFF)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            // One line; "skip it" is a link inside it (52), not a separate button.
            Text(
                instruction(s, onSkip = vm::skip),
                style = BurrowType.body,
                color = if (s.notAmount) c.error else c.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = Space.grid),
            )
            val header = listOfNotNull(s.sender.ifBlank { null }, s.receivedAt?.let { dates.day(dates.date(it)) }).joinToString(" · ")
            MessageBox(header.ifBlank { stringResource(R.string.message) }, s.text, marks(s), onTap = { offset -> haptics.tick(); vm.tap(offset) })
            if (s.picks[Part.AMOUNT] != null && s.rule == null) {
                Text(stringResource(R.string.teach_fails), style = BurrowType.body, color = c.error, modifier = Modifier.padding(start = Space.grid))
            }
            ListGroup {
                PARTS.forEachIndexed { i, (part, label, icon) ->
                    val pick = s.picks[part]
                    val word = pick?.let { s.text.substring(it) }
                    ListRow(
                        when {
                            word == null -> stringResource(R.string.not_picked)
                            // "debited · Money out": what the tapped word means.
                            part == Part.DIRECTION -> word + " · " + stringResource(if (app.burrow.money.capture.RuleBuilder.directionOf(word) == app.burrow.money.capture.Direction.IN) R.string.money_in else R.string.money_out)
                            else -> word
                        },
                        i, PARTS.size,
                        supporting = stringResource(label),
                        selected = s.step == part,
                        lead = { IconCircle(painterResource(icon)) },
                        // Picked parts show a check, the rest an empty ring, like every other list that picks.
                        trailing = { app.burrow.money.ui.components.CheckBadge(pick != null, ring = true) },
                        onClick = { vm.choose(part) },
                    )
                }
            }
            // All set (149): whether the rule keeps reading the next ones. No category here: a rule reads
            // a kind of message, and each payment it reads gets its own category in To review.
            if (s.canSave) {
                ListGroup {
                    ListRow(
                        stringResource(R.string.use_for_messages_like_this), 0, 1,
                        supporting = s.sender.ifBlank { null }?.let { stringResource(R.string.rule_from, it) },
                        lead = { IconCircle(painterResource(R.drawable.ic_rule_duotone)) },
                        trailing = { app.burrow.money.ui.components.BurrowSwitch(s.useRule, vm::setUseRule) },
                    )
                }
                androidx.compose.material3.Text(
                    stringResource(R.string.teach_category_in_review), style = app.burrow.money.ui.theme.BurrowType.body,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.headingInset),
                )
            }
        }
    }
}

private val PARTS = listOf(
    Triple(Part.AMOUNT, R.string.amount, R.drawable.ic_payments_duotone),
    Triple(Part.PAYEE, R.string.from_or_to, R.drawable.ic_storefront_duotone),
    Triple(Part.ACCOUNT, R.string.account, R.drawable.ic_account_balance_duotone),
    Triple(Part.DIRECTION, R.string.money_in_or_out, R.drawable.ic_swap_vert_duotone),
)

/** The step's instruction: its key word bold, "skip it" a link that skips the step. */
@Composable
private fun instruction(s: TeachState, onSkip: () -> Unit): AnnotatedString {
    if (s.notAmount) return AnnotatedString(stringResource(R.string.not_an_amount))
    val html = when (s.step) {
        Part.AMOUNT -> R.string.teach_amount
        Part.PAYEE -> R.string.teach_payee
        Part.ACCOUNT -> R.string.teach_account
        Part.DIRECTION -> R.string.teach_direction
        null -> if (s.canSave) R.string.teach_done else R.string.teach_fix
    }
    val link = androidx.compose.ui.text.TextLinkStyles(
        androidx.compose.ui.text.SpanStyle(
            color = MaterialTheme.colorScheme.primary, fontWeight = BurrowType.button.fontWeight,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        ),
    )
    return AnnotatedString.fromHtml(stringResource(html), linkStyles = link, linkInteractionListener = { onSkip() })
}

/** Picked parts: the amount strong, others soft; the part being picked is ringed when already chosen. */
private fun marks(s: TeachState): List<Mark> = s.picks.map { (part, range) ->
    Mark(range, if (part == s.step) MarkStyle.RING else if (part == Part.AMOUNT) MarkStyle.STRONG else MarkStyle.SOFT)
}
