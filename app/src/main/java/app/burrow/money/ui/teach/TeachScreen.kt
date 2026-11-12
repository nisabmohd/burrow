package app.burrow.money.ui.teach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.withStyle
import app.burrow.money.ui.LocalMoney
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
 * Check a message (52, 240; final 12 Nov): ✕ · "Check a message" · "1 of 2". **Burrow read it as**: a 64 dp circle with
 * the type's icon, the signed amount, "Expense · to Zomato", "from Kotak ••4471 · Sat, 18 Oct". **From your SMS** with
 * what it read highlighted. **Is this right?** (or, when the words don't say, **Did money go out or in?** with type
 * chips) · footer No, fix it / Yes, save. Fix it (235, 239) lists the parts; one tapped rings what it could be. When
 * every message is checked: the Ghost "All checked" · Back to Review (149).
 */
@Composable
fun TeachScreen(vm: TeachViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var typeSheet by rememberSaveable { mutableStateOf(false) }
    val save = {
        if (s.canSave) {
            haptics.confirm()
            vm.save {
                snackbar.show(context.getString(R.string.saved_read_from_now))
                if (s.total == 0) onClose() // a pasted message: one rule, then back
            }
        } else if (s.typeUnknown) snackbar.show(context.getString(R.string.pick_type_first))
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(
            painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose,
            title = stringResource(if (s.mode == TeachMode.FIX && !s.done) R.string.fix_it else R.string.check_a_message),
        ) {
            if (s.total > 0 && !s.done) Text(
                stringResource(R.string.n_of_m, s.position, s.total), style = BurrowType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Space.block),
            )
        }
        when {
            s.done -> app.burrow.money.ui.components.EmptyState(
                shape = { app.burrow.money.ui.components.Ghost(painterResource(R.drawable.ic_task_alt_regular)) },
                title = stringResource(R.string.all_checked),
                line = stringResource(R.string.all_checked_line),
                button = { BurrowButton(stringResource(R.string.back_to_review), onClose, kind = ButtonKind.TONAL) },
            )
            s.mode == TeachMode.CHECK -> {
                CheckBody(s, Modifier.weight(1f), onType = vm::pickType)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.block),
                    horizontalArrangement = Arrangement.spacedBy(Space.inline),
                ) {
                    BurrowButton(stringResource(R.string.no_fix_it), vm::fix, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
                    BurrowButton(
                        stringResource(R.string.yes_save), save, Modifier.weight(1f), if (s.canSave) ButtonKind.FILLED else ButtonKind.OFF,
                        icon = painterResource(R.drawable.ic_check_regular), height = Size.buttonBottom,
                    )
                }
            }
            else -> {
                FixBody(s, Modifier.weight(1f), onChoose = vm::choose, onType = { typeSheet = true }, onTap = { offset -> haptics.tick(); vm.tap(offset) })
                BurrowButton(
                    stringResource(R.string.done), vm::doneFixing,
                    Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.block),
                    icon = painterResource(R.drawable.ic_check_regular), height = Size.buttonBottom,
                )
            }
        }
    }
    if (typeSheet) TypeSheet(s, vm, onDismiss = { typeSheet = false })
}

/** Is this right? (52, 240): Burrow's reading, the message, the question. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckBody(s: TeachState, modifier: Modifier, onType: (MoneyType) -> Unit) {
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    val dates = rememberDateText()
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        // Burrow read it as: the type's circle, the signed amount, what it was and where from.
        Column(
            Modifier.fillMaxWidth().padding(top = Space.small, bottom = Space.group),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.group),
        ) {
            Text(stringResource(R.string.burrow_read_it_as), style = BurrowType.label, color = c.onSurfaceVariant)
            Box(Modifier.padding(vertical = Space.group)) { app.burrow.money.ui.components.HeaderIcon(painterResource(typeIcon(s.type))) }
            Text(amountText(s, money), style = BurrowType.display, color = c.onSurface, textAlign = TextAlign.Center)
            val payee = s.picks[Part.PAYEE]?.let { payeeName(s.text.substring(it)) }
            val inward = s.type.direction == app.burrow.money.capture.Direction.IN
            val typeWord = if (s.typeUnknown) stringResource(R.string.type_unknown) else stringResource(typeName(s.type))
            val rest = payee?.let { stringResource(if (inward) R.string.from_name else R.string.to_name, it) }
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    if (s.typeUnknown) withStyle(androidx.compose.ui.text.SpanStyle(color = c.error)) { append(typeWord) } else append(typeWord)
                    if (rest != null) append(stringResource(R.string.dot_join, "", rest))
                },
                style = BurrowType.body, color = c.onSurface, textAlign = TextAlign.Center,
            )
            val account = accountText(s)
            val line = listOfNotNull(
                account?.let { stringResource(if (inward) R.string.to_name else R.string.from_name, it) },
                s.receivedAt?.let { dates.weekdayDay(dates.date(it)) },
            ).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = BurrowType.body, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Column {
            SectionHeader(stringResource(R.string.from_your_sms))
            MessageBox(s.bank.ifBlank { stringResource(R.string.message) }, s.text, readMarks(s))
        }
        if (s.picks[Part.AMOUNT] != null && s.rule == null) {
            Text(stringResource(R.string.teach_fails), style = BurrowType.body, color = c.error, modifier = Modifier.padding(start = Space.headingInset))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.small, vertical = Space.group),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.group),
        ) {
            Text(stringResource(if (s.asksType) R.string.did_money_go else R.string.is_this_right), style = BurrowType.title, color = c.onSurface, textAlign = TextAlign.Center)
            if (s.asksType) Text(stringResource(R.string.message_doesnt_say), style = BurrowType.body, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        // Out or in (240): the message doesn't say, so the four kinds as chips; Yes, save waits for one.
        if (s.asksType) FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.small, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Space.small),
        ) {
            listOf(MoneyType.EXPENSE, MoneyType.INCOME, MoneyType.REFUND, MoneyType.TRANSFER).forEach { t ->
                Chip(stringResource(typeName(t)), s.pickedType == t, { onType(t) })
            }
        }
    }
}

/** Fix it (235, 239): What's wrong? with the four parts; a part being fixed rings what it could be in the message. */
@Composable
private fun FixBody(s: TeachState, modifier: Modifier, onChoose: (Part) -> Unit, onType: () -> Unit, onTap: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.small, vertical = Space.group), verticalArrangement = Arrangement.spacedBy(Space.group)) {
            Text(
                when {
                    s.addingWord -> AnnotatedString.fromHtml(stringResource(R.string.teach_add_word))
                    s.step == Part.AMOUNT -> AnnotatedString(stringResource(R.string.tap_right_amount))
                    s.step == Part.PAYEE -> AnnotatedString(stringResource(R.string.tap_right_name))
                    s.step == Part.ACCOUNT -> AnnotatedString(stringResource(R.string.tap_right_account))
                    else -> AnnotatedString(stringResource(R.string.whats_wrong))
                },
                style = BurrowType.title, color = c.onSurface,
            )
            Text(
                stringResource(
                    when {
                        s.notAmount -> R.string.not_an_amount
                        s.step != null -> R.string.ringed_line
                        else -> R.string.tap_part_to_fix
                    },
                ),
                style = BurrowType.body, color = if (s.notAmount) c.error else c.onSurfaceVariant,
            )
        }
        ListGroup {
            val count = TAPPED_PARTS.size + 1
            TAPPED_PARTS.forEachIndexed { i, part ->
                val label = stringResource(partLabel(part, s.type))
                ListRow(
                    partValue(s, part, money) ?: stringResource(R.string.not_set), i, count,
                    supporting = label,
                    selected = s.step == part,
                    lead = { IconCircle(painterResource(partIcon(part))) },
                    trailing = if (s.step == part) null else { { Chevron() } },
                    onClick = { onChoose(part) },
                )
            }
            ListRow(
                if (s.typeUnknown) stringResource(R.string.not_set) else stringResource(typeName(s.type)), count - 1, count,
                supporting = stringResource(R.string.type),
                lead = { IconCircle(painterResource(typeIcon(s.type))) },
                trailing = { Chevron() },
                onClick = onType,
            )
        }
        Column {
            SectionHeader(stringResource(R.string.from_your_sms))
            MessageBox(
                s.bank.ifBlank { stringResource(R.string.message) }, s.text, fixMarks(s),
                onTap = if (s.step != null || s.addingWord) onTap else null,
            )
        }
    }
}

/** "−₹2,350" (expense), "+₹500" (income, refund), "₹8,120" (transfer); the word itself until the rule reads it. */
private fun amountText(s: TeachState, money: app.burrow.money.domain.MoneyFormat): String {
    val read = s.read ?: return s.picks[Part.AMOUNT]?.let { s.text.substring(it) } ?: "?"
    return when (s.type) {
        MoneyType.EXPENSE -> money.signed(-read.amountMinor)
        MoneyType.INCOME, MoneyType.REFUND -> money.signed(read.amountMinor)
        else -> money.plain(read.amountMinor)
    }
}

/** "zomato@hdfcbank" → "Zomato", "ZOMATO" → "Zomato": the name as Review shows it. */
private fun payeeName(word: String): String = app.burrow.money.data.upiName(word) ?: app.burrow.money.data.tidyPayee(word)

/** "Kotak ••4471": the bank's short name and the account's last digits. */
private fun accountText(s: TeachState): String? {
    val word = s.picks[Part.ACCOUNT]?.let { s.text.substring(it) } ?: return null
    val digits = word.filter { it.isDigit() }.takeLast(4).ifEmpty { return word }
    val bank = s.bank.takeIf { it.isNotBlank() }?.let { app.burrow.money.capture.bankShortName(it) }
    return listOfNotNull(bank, "••$digits").joinToString(" ")
}

/** A part's value in Fix it: "₹2,350", "Zomato", "Kotak ••4471"; null = not read. */
private fun partValue(s: TeachState, part: Part, money: app.burrow.money.domain.MoneyFormat): String? = when (part) {
    Part.AMOUNT -> s.read?.let { money.plain(it.amountMinor) } ?: s.picks[Part.AMOUNT]?.let { s.text.substring(it) }
    Part.PAYEE -> s.picks[Part.PAYEE]?.let { payeeName(s.text.substring(it)) }
    else -> accountText(s)
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

/** Is this right? (52): the parts Burrow read strongly, the word that gives the type softly. */
private fun readMarks(s: TeachState): List<Mark> = buildList {
    s.picks.values.forEach { add(Mark(it, MarkStyle.STRONG)) }
    s.found?.takeIf { it.type == s.type }?.let { f -> if (s.picks.values.none { it.first <= f.range.last && f.range.first <= it.last }) add(Mark(f.range, MarkStyle.SOFT)) }
}

/** Fix it (235, 239): the part being fixed and what it could be ringed; the rest as read. */
private fun fixMarks(s: TeachState): List<Mark> = buildList {
    // 239: only the part being fixed stays filled, so the ringed choices stand out; 235: every part as read.
    s.picks.forEach { (part, range) -> if (s.step == null || part == s.step) add(Mark(range, MarkStyle.STRONG)) }
    s.ringed.forEach { add(Mark(it, MarkStyle.RING)) }
}
