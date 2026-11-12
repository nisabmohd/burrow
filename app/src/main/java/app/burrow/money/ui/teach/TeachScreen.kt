package app.burrow.money.ui.teach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.capture.MoneyType
import app.burrow.money.capture.Part
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CheckBadge
import app.burrow.money.ui.components.Chevron
import app.burrow.money.ui.components.Chip
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.Mark
import app.burrow.money.ui.components.MarkStyle
import app.burrow.money.ui.components.MessageBox
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.tick
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/**
 * Teach a message (52, 235): close · title · Save; one instruction line; the message, where a tap
 * picks the word for the current part; rows Amount · Paid to · Account (tapped; the current one
 * tinted, the rest "Next") and Type, filled in by Burrow from the message's words ("Expense · it says
 * “debited”") and tappable at any time: Type sheet (236). Save turns on once the amount is picked and
 * the rule reads the message back; Paid to and Account are optional, in any order.
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
    var typeSheet by rememberSaveable { mutableStateOf(false) }
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
                val count = TAPPED_PARTS.size + 1
                TAPPED_PARTS.forEachIndexed { i, part ->
                    val pick = s.picks[part]
                    val label = stringResource(partLabel(part, s.type))
                    ListRow(
                        pick?.let { s.text.substring(it) } ?: label, i, count,
                        // Picked: what it is under the word. Not yet: "Tap it in the message" for the current one, else "Next".
                        supporting = when {
                            pick != null -> label
                            s.step == part -> stringResource(R.string.tap_it_in_message)
                            else -> stringResource(R.string.next)
                        },
                        selected = s.step == part,
                        lead = { IconCircle(painterResource(partIcon(part))) },
                        onClick = { vm.choose(part) },
                    )
                }
                val word = s.found?.takeIf { it.type == s.type }?.word
                ListRow(
                    stringResource(typeName(s.type)), count - 1, count,
                    supporting = when {
                        word != null -> stringResource(R.string.type_it_says, stringResource(R.string.type), word)
                        else -> stringResource(R.string.type)
                    },
                    lead = { IconCircle(painterResource(typeIcon(s.type))) },
                    trailing = { Chevron() },
                    onClick = { typeSheet = true },
                )
            }
            // All set (149): whether the rule keeps reading the next ones. No category here: a rule reads
            // a kind of message, and each payment it reads gets its own category in To review.
            if (s.canSave) {
                ListGroup {
                    ListRow(
                        stringResource(R.string.use_for_messages_like_this), 0, 1,
                        supporting = s.sender.ifBlank { null }?.let { stringResource(R.string.rule_from, it) },
                        lead = { IconCircle(painterResource(R.drawable.ic_rule_duotone)) },
                        trailing = { BurrowSwitch(s.useRule, vm::setUseRule) },
                    )
                }
                Text(
                    stringResource(R.string.teach_category_in_review), style = BurrowType.body, color = c.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.headingInset),
                )
            }
        }
    }
    if (typeSheet) TypeSheet(s, vm, onDismiss = { typeSheet = false })
}

/**
 * Type (236): what a message like this means, with the round check; then the words that mean the
 * picked type (the built-in ones, and this rule's own with ✕) and Add a word, which closes the sheet
 * so the next tap on the message adds that word.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeSheet(s: TeachState, vm: TeachViewModel, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.type), onDismiss, stringResource(R.string.type_sheet_line))
        SheetBody {
            ListGroup {
                MoneyType.entries.forEachIndexed { i, t ->
                    ListRow(
                        stringResource(typeName(t)), i, MoneyType.entries.size,
                        supporting = stringResource(typeLine(t)),
                        lead = { IconCircle(painterResource(typeIcon(t))) },
                        trailing = { CheckBadge(s.type == t, ring = true) },
                        onClick = { vm.pickType(t) },
                    )
                }
            }
            Column {
                SectionHeader(stringResource(R.string.words_that_mean, stringResource(typeName(s.type))))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    // Every word has ✕: the user's own goes, a built-in one is taken away for this rule only.
                    // Plain outline chips sized to their word (236), not ChipRow's equal-width ones.
                    s.wordsFor(s.type).forEach { w ->
                        Chip(w, false, { vm.removeWord(s.type, w) }, icon = painterResource(R.drawable.ic_close_regular))
                    }
                    Chip(stringResource(R.string.add_a_word), false, { vm.startAddingWord(); onDismiss() }, icon = painterResource(R.drawable.ic_add_regular))
                }
                Text(
                    stringResource(R.string.type_words_line), style = BurrowType.body, color = c.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.headingInset, top = Space.block),
                )
            }
        }
        SheetFooter { BurrowButton(stringResource(R.string.done), onDismiss, Modifier.fillMaxWidth(), height = Size.buttonBottom) }
    }
}

private fun partLabel(part: Part, type: MoneyType) = when (part) {
    Part.AMOUNT -> R.string.amount
    // Who was paid, or who paid: the label follows the type.
    Part.PAYEE -> if (type.direction == app.burrow.money.capture.Direction.IN) R.string.received_from else R.string.paid_to
    else -> R.string.account
}

private fun partIcon(part: Part) = when (part) {
    Part.AMOUNT -> R.drawable.ic_payments_duotone
    Part.PAYEE -> R.drawable.ic_storefront_duotone
    else -> R.drawable.ic_account_balance_duotone
}

private fun typeName(t: MoneyType) = when (t) {
    MoneyType.EXPENSE -> R.string.type_expense
    MoneyType.INCOME -> R.string.type_income
    MoneyType.REFUND -> R.string.refund
    MoneyType.TRANSFER -> R.string.type_transfer
    MoneyType.NOT_PAYMENT -> R.string.not_a_payment
}

private fun typeLine(t: MoneyType) = when (t) {
    MoneyType.EXPENSE -> R.string.money_out
    MoneyType.INCOME -> R.string.money_in
    MoneyType.REFUND -> R.string.type_refund_line
    MoneyType.TRANSFER -> R.string.type_transfer_line
    MoneyType.NOT_PAYMENT -> R.string.type_not_payment_line
}

private fun typeIcon(t: MoneyType) = when (t) {
    MoneyType.EXPENSE -> R.drawable.ic_arrow_upward_duotone
    MoneyType.INCOME -> R.drawable.ic_arrow_downward_duotone
    MoneyType.REFUND -> R.drawable.ic_undo_duotone
    MoneyType.TRANSFER -> R.drawable.ic_swap_horiz_duotone
    MoneyType.NOT_PAYMENT -> R.drawable.ic_block_duotone
}

/** The step's instruction: its key word bold, "skip it" a link that skips the step. */
@Composable
private fun instruction(s: TeachState, onSkip: () -> Unit): AnnotatedString {
    if (s.notAmount) return AnnotatedString(stringResource(R.string.not_an_amount))
    val html = when {
        s.addingWord -> R.string.teach_add_word
        s.step == Part.AMOUNT -> R.string.teach_amount
        s.step == Part.PAYEE -> R.string.teach_payee
        s.step == Part.ACCOUNT -> R.string.teach_account
        s.canSave -> R.string.teach_check_type
        else -> R.string.teach_fix
    }
    val link = androidx.compose.ui.text.TextLinkStyles(
        androidx.compose.ui.text.SpanStyle(
            color = MaterialTheme.colorScheme.primary, fontWeight = BurrowType.button.fontWeight,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        ),
    )
    return AnnotatedString.fromHtml(stringResource(html), linkStyles = link, linkInteractionListener = { onSkip() })
}

/**
 * The picked parts highlighted (235), the word that gives the type soft, and the amount it guesses
 * ringed before the first tap (52); the part being picked again is ringed.
 */
private fun marks(s: TeachState): List<Mark> = buildList {
    s.picks.forEach { (part, range) -> add(Mark(range, if (part == s.step) MarkStyle.RING else MarkStyle.STRONG)) }
    s.guess?.takeIf { s.step == Part.AMOUNT }?.let { add(Mark(it, MarkStyle.RING)) }
    s.found?.takeIf { it.type == s.type }?.let { f -> if (s.picks.values.none { it.first <= f.range.last && f.range.first <= it.last }) add(Mark(f.range, MarkStyle.SOFT)) }
}
