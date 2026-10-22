package app.burrow.money.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.data.TxnType
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonGroup
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.Key
import app.burrow.money.ui.components.Keypad
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.apply
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.pickers.AccountSheet
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.pickers.DateSheet
import app.burrow.money.data.FormExtra
import app.burrow.money.data.Lend
import app.burrow.money.ui.components.ExtraChip
import app.burrow.money.ui.components.ExtraChipRow
import app.burrow.money.ui.components.PayeeField
import app.burrow.money.ui.components.SuggestionChips
import app.burrow.money.ui.theme.Size
import androidx.compose.ui.res.pluralStringResource
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Space

private enum class AddSheet { CATEGORY, ACCOUNT, TO_ACCOUNT, WHEN, DATE, TIME, NOTE, REPEAT, ATTACH, LEND }

private val TYPES = listOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.TRANSFER)

/**
 * New expense / income / transfer (17, 18, 74–79, 87). Close · title · Save; the type group; the
 * amount; Paid to with suggestions; three rows (category, account, date); the extras chips chosen
 * in Customise › Expense; the keypad pinned at the bottom. Save looks off until it can save, and
 * tapping it early turns the missing row red.
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
    val save = {
        if (s.canSave) haptics.confirm()
        vm.save(onSaved)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(
            navIcon = painterResource(R.drawable.ic_close_regular),
            navLabel = stringResource(R.string.close),
            onNav = onClose,
            title = stringResource(titleFor(f.type, s.isEdit)),
        ) {
            BurrowButton(stringResource(R.string.save), save, kind = if (s.canSave) ButtonKind.FILLED else ButtonKind.OFF)
        }
        // The keypad steps aside while the system keyboard is up (typing Paid to).
        val imeUp = androidx.compose.foundation.layout.WindowInsets.Companion.isImeVisible
        val formContent: @Composable (Modifier) -> Unit = { m ->
        Column(
            m.padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            ButtonGroup(TYPES, f.type, { stringResource(typeLabel(it)) }, vm::setType)
            // Favourites (04): while nothing is typed, one tap saves a usual expense.
            if (f.type == TxnType.EXPENSE && !s.isEdit && s.amount == 0L && s.favourites.isNotEmpty()) {
                Favourites(s.favourites, onFavourites) { fav, label -> vm.spendFavourite(fav, label, onSaved) }
            }
            Column(Modifier.fillMaxWidth().padding(top = Space.amountTop, bottom = Space.block), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    money.plain(s.amount),
                    style = BurrowType.display,
                    color = if (s.amount > 0) c.onSurface else c.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    when {
                        f.input.hasOperator -> f.input.text
                        f.type == TxnType.TRANSFER -> stringResource(R.string.not_counted)
                        else -> " "
                    },
                    style = BurrowType.body,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (f.type != TxnType.TRANSFER) {
                val cat = s.category
                val known = f.payee.isNotBlank() && cat != null
                PayeeField(
                    f.payee, vm::setPayee, stringResource(payeeLabel(f.type)), stringResource(R.string.clear),
                    lead = { if (known) CategoryCircle(cat!!.icon, cat.colorHue) else IconCircle(painterResource(R.drawable.ic_storefront_duotone)) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                if (s.suggestions.isNotEmpty()) {
                    SuggestionChips(
                        s.suggestions, { it.name },
                        circle = if (f.payee.isNotBlank()) ({ p -> CategoryCircle(p.icon, p.hue, Size.suggestionCircle) }) else null,
                        onPick = vm::pickPayee,
                    )
                }
            }
            val rows = buildList<@Composable (Int, Int) -> Unit> {
                if (f.type == TxnType.TRANSFER) {
                    add { i, n -> AccountRow(s.account, R.string.from, i, n) { sheet = AddSheet.ACCOUNT } }
                    add { i, n -> AccountRow(s.toAccount, R.string.to, i, n, missing = f.tried && s.missingToAccount) { sheet = AddSheet.TO_ACCOUNT } }
                } else {
                    add { i, n ->
                        val cat = s.category
                        ListRow(
                            headline = cat?.name ?: stringResource(R.string.choose_category),
                            supporting = stringResource(R.string.category),
                            supportingColor = if (f.tried && cat == null) c.error else c.onSurfaceVariant,
                            index = i, count = n,
                            lead = { if (cat != null) CategoryCircle(cat.icon, cat.colorHue) else IconCircle(painterResource(R.drawable.ic_category_duotone)) },
                            onClick = { sheet = AddSheet.CATEGORY },
                        )
                    }
                    add { i, n -> AccountRow(s.account, if (f.type == TxnType.INCOME) R.string.paid_into else R.string.paid_from, i, n) { sheet = AddSheet.ACCOUNT } }
                }
                add { i, n ->
                    val withTime = s.shows(FormExtra.TIME)
                    ListRow(
                        headline = if (withTime) dates.dayTime(f.at) else dates.day(dates.date(f.at)),
                        supporting = stringResource(if (s.upcoming) R.string.upcoming_added_that_day else R.string.date), index = i, count = n,
                        lead = { IconCircle(painterResource(if (s.upcoming) R.drawable.ic_event_upcoming_duotone else R.drawable.ic_schedule_duotone)) },
                        onClick = { sheet = AddSheet.WHEN },
                    )
                }
            }
            ListGroup { rows.forEachIndexed { i, row -> row(i, rows.size) } }
            // Extras (Customise › Expense): each opens a sheet and shows its value once set.
            val transfer = f.type == TxnType.TRANSFER
            val files = f.kept.size + f.attachments.size
            ExtraChipRow {
                if (s.shows(FormExtra.NOTE)) ExtraChip(painterResource(R.drawable.ic_notes_regular), stringResource(R.string.note),
                    f.note.takeIf { it.isNotBlank() }?.let { stringResource(R.string.note_value, it.take(14)) } ?: f.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }) { sheet = AddSheet.NOTE }
                if (s.shows(FormExtra.REPEAT) && !transfer && !s.isEdit) ExtraChip(painterResource(R.drawable.ic_event_repeat_regular), stringResource(R.string.extra_repeat),
                    f.repeat.takeIf { it != Every.NEVER }?.let { stringResource(everyLabel(it)) }) { sheet = AddSheet.REPEAT }
                if (s.shows(FormExtra.ATTACH)) ExtraChip(painterResource(R.drawable.ic_attach_file_regular), stringResource(R.string.extra_attach),
                    files.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.n_files, it, it) }) { sheet = AddSheet.ATTACH }
                if (s.shows(FormExtra.FAVOURITE) && f.type == TxnType.EXPENSE) ExtraChip(painterResource(R.drawable.ic_star_regular), stringResource(R.string.favourite),
                    if (f.favourite) stringResource(R.string.favourite) else null) { vm.setFavourite(!f.favourite) }
                if (s.shows(FormExtra.LEND) && !transfer) ExtraChip(painterResource(R.drawable.ic_handshake_regular), stringResource(R.string.extra_lend),
                    f.lend?.takeIf { f.person.isNotBlank() }?.let { stringResource(if (it == Lend.LENT) R.string.lent_to_name else R.string.borrowed_from_name, f.person) }) { sheet = AddSheet.LEND }
            }
        }
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
            modifier = m.padding(start = Space.screen, end = Space.screen, top = Space.block, bottom = Space.screen),
        )
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Short and wide (landscape phone): the keypad sits beside the form (Day 26).
            if (maxWidth > maxHeight && maxHeight < Size.landscapeMaxHeight) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                    formContent(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
                    if (!imeUp) keypad(Modifier.width(Size.keypadSide).align(Alignment.CenterVertically))
                }
            } else {
                // Portrait: the form scrolls; the keypad stays pinned so ✓ is always in reach.
                Column(Modifier.fillMaxSize()) {
                    formContent(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))
                    if (!imeUp) keypad(Modifier)
                }
            }
        }
    }

    when (sheet) {
        AddSheet.CATEGORY -> CategorySheet(s.categoriesForType, s.spentByCategory, f.categoryId, { vm.setCategory(it.id); sheet = null }, { sheet = null })
        AddSheet.ACCOUNT -> AccountSheet(s.accountsForType, f.accountId, { vm.setAccount(it.account.id); sheet = null }, { sheet = null })
        AddSheet.TO_ACCOUNT -> AccountSheet(s.accounts.filter { it.account.id != f.accountId }, f.toAccountId, { vm.setToAccount(it.account.id); sheet = null }, { sheet = null })
        AddSheet.WHEN -> WhenSheet(f.at, s.shows(FormExtra.TIME), { vm.setDate(it); sheet = null }, { sheet = AddSheet.DATE }, { sheet = null })
        AddSheet.DATE -> DateSheet(dates.date(f.at), { vm.setDate(it); sheet = if (s.shows(FormExtra.TIME)) AddSheet.TIME else null }, { sheet = null }, anyDay = true)
        AddSheet.TIME -> TimeSheet(java.time.Instant.ofEpochMilli(f.at).atZone(java.time.ZoneId.systemDefault()).toLocalTime(), { vm.setTime(it); sheet = null }, { sheet = null })
        AddSheet.NOTE -> NoteSheet(vm, s) { sheet = null }
        AddSheet.REPEAT -> RepeatSheet(vm, s) { sheet = null }
        AddSheet.ATTACH -> AttachSheet(app, vm, s) { sheet = null }
        AddSheet.LEND -> LendSheet(vm, s) { sheet = null }
        null -> Unit
    }
}

@Composable
private fun AccountRow(account: app.burrow.money.data.AccountWithBalance?, label: Int, index: Int, count: Int, missing: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ListRow(
        headline = account?.account?.name ?: stringResource(R.string.choose_account),
        supporting = stringResource(label),
        supportingColor = if (missing) c.error else c.onSurfaceVariant,
        index = index, count = count,
        lead = {
            if (account != null) CategoryCircle(account.account.icon, account.account.colorHue)
            else IconCircle(painterResource(R.drawable.ic_account_balance_duotone))
        },
        onClick = onClick,
    )
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

/** "★ Favourites · one tap saves" · Edit, then the chips in a row that scrolls sideways (04). */
@Composable
private fun Favourites(list: List<app.burrow.money.data.FavouriteView>, onEdit: () -> Unit, onSpend: (app.burrow.money.data.FavouriteView, String) -> Unit) {
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(painterResource(R.drawable.ic_star_regular), null, tint = c.primary, modifier = Modifier.padding(end = Space.small).size(Size.iconSmall))
        Text(stringResource(R.string.favourites_one_tap), style = BurrowType.label, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
        app.burrow.money.ui.components.LinkButton(stringResource(R.string.edit), onEdit)
    }
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        list.forEach { f ->
            val label = stringResource(R.string.favourite_chip, f.favourite.name, money.plain(f.favourite.amount))
            val saved = stringResource(R.string.favourite_saved, f.favourite.name, money.plain(f.favourite.amount))
            app.burrow.money.ui.components.FavouriteChip(label, { CategoryCircle(f.categoryIcon, f.categoryHue, Size.favouriteCircle) }) { onSpend(f, saved) }
        }
    }
}
