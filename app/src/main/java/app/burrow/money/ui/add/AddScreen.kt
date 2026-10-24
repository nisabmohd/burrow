package app.burrow.money.ui.add

import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.data.FormExtra
import app.burrow.money.data.Lend
import app.burrow.money.data.TxnType
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.FormChip
import app.burrow.money.ui.components.FormChipRow
import app.burrow.money.ui.components.FormChipStyle
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.Key
import app.burrow.money.ui.components.Keypad
import app.burrow.money.ui.components.PayeeField
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.apply
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.duotoneIcon
import app.burrow.money.ui.pickers.AccountSheet
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.pickers.DateSheet
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.caretBlinkSpec

private enum class AddSheet { TYPE, CATEGORY, ACCOUNT, TO_ACCOUNT, DATE, TIME, NOTE, REPEAT_OR_LEND, ATTACH }

/**
 * New expense / income / transfer (17, 18, 79, 87, 91, 108, 109). ✕ · "New expense ▾" (the type
 * sheet, 111) · Save. The amount with a blinking caret; Paid to; favourites (empty form) or payee
 * matches (typing); category chips (4 most used + More); one line "HDFC Savings · Today"; the extras
 * Note · Receipt · Repeat or lend. The keypad is always docked, and steps aside only while the system
 * keyboard is up for Paid to. Everything fits without scrolling on a 640 dp-tall screen.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddScreen(app: AppContainer, vm: AddViewModel, onClose: () -> Unit, onSaved: (Saved) -> Unit, onFavourites: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return // nothing drawn until the form has loaded
    val f = s.form
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    val dates = rememberDateText()
    val haptics = LocalHapticFeedback.current
    var sheet by rememberSaveable { mutableStateOf<AddSheet?>(null) }
    // "Saved · Upcoming on Sat, 24 Oct" when the date is ahead (Coming up).
    val upcomingLine = stringResource(R.string.saved_upcoming_on, dates.shortDay(dates.date(f.at)))
    val done: (Saved) -> Unit = { saved -> onSaved(if (saved.upcoming && saved.label == null) Saved(saved.type, saved.wasEdit, true, upcomingLine, saved.undo) else saved) }
    val save = {
        if (s.canSave) haptics.confirm()
        vm.save(done)
    }
    val imeUp = WindowInsets.isImeVisible

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(
            navIcon = painterResource(R.drawable.ic_close_regular),
            navLabel = stringResource(R.string.close),
            onNav = onClose,
            title = stringResource(titleFor(f.type, s.isEdit)),
            onTitle = { sheet = AddSheet.TYPE },
        ) {
            BurrowButton(stringResource(R.string.save), save, kind = if (s.canSave) ButtonKind.FILLED else ButtonKind.OFF)
        }
        val keypad: @Composable (Modifier) -> Unit = { m ->
            Keypad(
                onKey = { key ->
                    when {
                        key != Key.Done -> vm.setInput(f.input.apply(key))
                        f.input.hasOperator -> vm.evaluate()
                        else -> save()
                    }
                },
                doneEnabled = s.canSave || f.input.hasOperator,
                showDot = f.input.digits > 0,
                modifier = m.padding(start = Space.screen, end = Space.screen, top = Space.small, bottom = Space.screen),
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Short and wide (landscape phone): the keypad sits beside the form (Day 26).
            if (maxWidth > maxHeight && maxHeight < Size.landscapeMaxHeight) {
                Row(Modifier.fillMaxSize()) {
                    Form(s, vm, !imeUp, { sheet = it }, done, onFavourites, Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
                    if (!imeUp) keypad(Modifier.width(Size.keypadSide).align(Alignment.CenterVertically))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Form(s, vm, !imeUp, { sheet = it }, done, onFavourites, Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))
                    if (!imeUp) keypad(Modifier)
                }
            }
        }
    }

    when (sheet) {
        AddSheet.TYPE -> TypeSheet(f.type, { vm.setType(it); sheet = null }, { sheet = null })
        AddSheet.CATEGORY -> CategorySheet(s.categoriesForType, s.spentByCategory, f.categoryId, { vm.setCategory(it.id); sheet = null }, { sheet = null })
        AddSheet.ACCOUNT -> AccountSheet(s.accountsForType, f.accountId, { vm.setAccount(it.account.id); sheet = null }, { sheet = null })
        AddSheet.TO_ACCOUNT -> AccountSheet(s.accounts.filter { it.account.id != f.accountId }, f.toAccountId, { vm.setToAccount(it.account.id); sheet = null }, { sheet = null })
        AddSheet.DATE -> DateSheet(dates.date(f.at), { vm.setDate(it); sheet = null }, { sheet = null }, anyDay = true, quick = true)
        AddSheet.TIME -> TimeSheet(java.time.Instant.ofEpochMilli(f.at).atZone(java.time.ZoneId.systemDefault()).toLocalTime(), { vm.setTime(it); sheet = null }, { sheet = null })
        AddSheet.NOTE -> NoteSheet(vm, s) { sheet = null }
        AddSheet.REPEAT_OR_LEND -> RepeatOrLendSheet(vm, s) { sheet = null }
        AddSheet.ATTACH -> AttachSheet(app, vm, s) { sheet = null }
        null -> Unit
    }
}

/** Everything above the keypad. [caret]: the keypad is up, so the amount shows its blinking caret. */
@Composable
private fun Form(
    s: AddUiState,
    vm: AddViewModel,
    caret: Boolean,
    open: (AddSheet) -> Unit,
    onSaved: (Saved) -> Unit,
    onFavourites: () -> Unit,
    modifier: Modifier,
) {
    val f = s.form
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    val dates = rememberDateText()
    val transfer = f.type == TxnType.TRANSFER
    Column(modifier.padding(horizontal = Space.screen), horizontalAlignment = Alignment.CenterHorizontally) {
        // The amount, alone in the middle, with the caret after it (108).
        Column(Modifier.fillMaxWidth().padding(top = Space.small, bottom = Space.block), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    money.plain(s.amount),
                    style = BurrowType.display,
                    color = if (s.amount > 0) c.onSurface else c.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (caret) app.burrow.money.ui.components.Caret()
            }
            val below = when {
                f.input.hasOperator -> f.input.text
                transfer -> stringResource(R.string.not_counted)
                else -> null
            }
            if (below != null) Text(below, style = BurrowType.body, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        // Favourites, only while the form is empty (01): quick-add pills under the amount, "₹20 Chai";
        // one tap saves the expense straight away (Undo).
        val favouritesShow = f.type == TxnType.EXPENSE && !s.isEdit && s.amount == 0L && f.payee.isBlank() &&
            s.favourites.isNotEmpty() && s.shows(FormExtra.FAVOURITE)
        if (favouritesShow) FormChipRow(Modifier.padding(bottom = Space.small)) {
            s.favourites.forEach { fav ->
                val saved = stringResource(R.string.favourite_saved, fav.favourite.name, money.plain(fav.favourite.amount))
                FormChip(money.plain(fav.favourite.amount), { vm.spendFavourite(fav, saved, onSaved) }, FormChipStyle.FILLED, trailing = fav.favourite.name)
            }
        }
        if (!transfer) {
            val cat = s.category
            val known = f.payee.isNotBlank() && cat != null
            PayeeField(
                f.payee, vm::setPayee, stringResource(payeeLabel(f.type)), stringResource(R.string.clear),
                lead = { if (known) CategoryCircle(cat!!.icon, cat.colorHue) else IconCircle(painterResource(R.drawable.ic_storefront_duotone)) },
            )
            // Payee matches while typing (03).
            when {
                f.payee.isNotBlank() && s.suggestions.isNotEmpty() -> FormChipRow(Modifier.padding(top = Space.small)) {
                    s.suggestions.take(4).forEach { p -> FormChip(p.name, { vm.pickPayee(p) }) }
                }
            }
            // Category chips: the 4 most used and More (the category sheet).
            FormChipRow {
                s.quickCategories.forEach { cat2 ->
                    FormChip(
                        cat2.name, { vm.setCategory(cat2.id) },
                        style = if (cat2.id == f.categoryId) FormChipStyle.SELECTED else FormChipStyle.OUTLINED,
                        circle = { CategoryCircle(cat2.icon, cat2.colorHue, Size.favouriteCircle) },
                    )
                }
                FormChip(stringResource(R.string.more), { open(AddSheet.CATEGORY) }, icon = painterResource(R.drawable.ic_expand_more_regular))
            }
            if (f.tried && s.missingCategory) {
                Text(stringResource(R.string.pick_category_to_save), style = BurrowType.body, color = c.error, modifier = Modifier.fillMaxWidth().padding(start = Space.headingInset))
            }
        }
        // One line: "HDFC Savings · Today" (each part opens its sheet); a transfer reads From → To.
        // The time has its own chip in the extras (12), so the line says just the day.
        val day = dates.day(dates.date(f.at))
        val when_ = if (s.upcoming) stringResource(R.string.dot_join, day, stringResource(R.string.upcoming_word)) else day
        val clock = painterResource(if (s.upcoming) R.drawable.ic_event_upcoming_regular else R.drawable.ic_schedule_regular)
        val accountIcon = painterResource(duotoneIcon(s.account?.account?.icon ?: "account_balance"))
        if (transfer) {
            Row(Modifier.padding(top = Space.small), verticalAlignment = Alignment.CenterVertically) {
                LineButton(accountIcon, s.account?.account?.name ?: stringResource(R.string.choose_account)) { open(AddSheet.ACCOUNT) }
                Icon(painterResource(R.drawable.ic_arrow_forward_regular), null, tint = c.onSurfaceVariant, modifier = Modifier.size(Size.iconSmall))
                LineButton(
                    painterResource(duotoneIcon(s.toAccount?.account?.icon ?: "account_balance")),
                    s.toAccount?.account?.name ?: stringResource(R.string.choose_account),
                    error = f.tried && s.missingToAccount,
                ) { open(AddSheet.TO_ACCOUNT) }
            }
            LineButton(clock, when_) { open(AddSheet.DATE) }
        } else {
            Row(Modifier.padding(top = Space.small), verticalAlignment = Alignment.CenterVertically) {
                LineButton(accountIcon, s.account?.account?.name ?: stringResource(R.string.choose_account)) { open(AddSheet.ACCOUNT) }
                Text("·", style = BurrowType.body, color = c.onSurfaceVariant)
                LineButton(clock, when_) { open(AddSheet.DATE) }
            }
        }
        // The extras: Note · Time · Receipt · Repeat or lend (01, 17). Which show follows Customise › Expense.
        FormChipRow(Modifier.padding(top = Space.small)) {
            if (s.shows(FormExtra.NOTE)) {
                val tags = f.tags.takeIf { it.isNotEmpty() }?.joinToString(", ")
                val value = f.note.ifBlank { tags.orEmpty() }.ifBlank { null }
                FormChip(value ?: stringResource(R.string.note), { open(AddSheet.NOTE) }, if (value != null) FormChipStyle.SELECTED else FormChipStyle.TONAL, icon = painterResource(R.drawable.ic_notes_regular))
            }
            if (s.shows(FormExtra.TIME)) {
                FormChip(dates.time(f.at), { open(AddSheet.TIME) }, FormChipStyle.TONAL, icon = painterResource(R.drawable.ic_schedule_regular))
            }
            if (s.shows(FormExtra.ATTACH)) {
                val files = f.kept.size + f.attachments.size
                FormChip(
                    if (files > 0) pluralStringResource(R.plurals.n_files, files, files) else stringResource(R.string.receipt), { open(AddSheet.ATTACH) },
                    if (files > 0) FormChipStyle.SELECTED else FormChipStyle.TONAL, icon = painterResource(R.drawable.ic_attach_file_regular),
                )
            }
            if (!transfer && (s.repeatOffered || s.shows(FormExtra.LEND))) {
                val value = repeatOrLendValue(s)
                FormChip(
                    value ?: stringResource(R.string.repeat_or_lend), { open(AddSheet.REPEAT_OR_LEND) },
                    if (value != null) FormChipStyle.SELECTED else FormChipStyle.TONAL,
                    icon = painterResource(if (f.lend != null) R.drawable.ic_handshake_regular else R.drawable.ic_calendar_month_regular),
                )
            }
        }
    }
}

/** What the Repeat or lend chip says once set: "Monthly", "Subscription · Monthly", "Lent · Rahul". */
@Composable
private fun repeatOrLendValue(s: AddUiState): String? {
    val f = s.form
    return when {
        f.lend != null -> {
            val kind = stringResource(if (f.lend == Lend.LENT) R.string.lent else R.string.borrowed)
            if (f.person.isNotBlank()) stringResource(R.string.pill_value, kind, f.person) else kind
        }
        f.repeat != Every.NEVER -> {
            val every = stringResource(when (f.repeat) { Every.WEEK -> R.string.weekly; Every.YEAR -> R.string.yearly; else -> R.string.monthly })
            if (f.subscription) stringResource(R.string.pill_value, stringResource(R.string.subscription), every) else every
        }
        else -> null
    }
}

/** A part of the "HDFC Savings · Today" line: icon + text, a 36 dp text button in a 48 dp target. */
@Composable
private fun LineButton(icon: Painter, text: String, error: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val fg = if (error) c.error else c.onSurface
    Row(
        Modifier.minimumInteractiveComponentSize().clip(Shapes.pill).clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Size.chip).padding(horizontal = Space.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(Size.iconSmall))
        Text(text, style = BurrowType.button, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun everyLabel(every: String) = when (every) {
    Every.WEEK -> R.string.every_week
    Every.YEAR -> R.string.every_year
    else -> R.string.every_month
}

fun typeLabel(type: String) = when (type) {
    TxnType.INCOME -> R.string.type_income
    TxnType.TRANSFER -> R.string.type_transfer
    else -> R.string.type_expense
}

private fun titleFor(type: String, edit: Boolean) = when (type) {
    TxnType.INCOME -> if (edit) R.string.edit_income else R.string.new_income
    TxnType.TRANSFER -> if (edit) R.string.edit_transfer else R.string.new_transfer
    else -> if (edit) R.string.edit_expense else R.string.new_expense
}

/** The snackbar line after a save. */
fun savedMessage(saved: Saved) = when {
    saved.upcoming -> R.string.saved_upcoming
    saved.wasEdit -> R.string.saved
    saved.type == TxnType.INCOME -> R.string.saved_income
    saved.type == TxnType.TRANSFER -> R.string.saved_transfer
    else -> R.string.saved_expense
}
