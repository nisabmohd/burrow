package app.burrow.money.ui.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.components.Chip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * New expense / income / transfer (17, 18, 79, 87, 108, 109). Close · title · Save; the type group;
 * the amount (tap it for the keypad, ⌄ puts it away); favourites; Paid to with suggestions; one list
 * (category, account, date, note, receipt); the type pills Once · Repeat · Subscription · Lent ·
 * Borrowed. Which of these show follows Customise › Expense. Save looks off until it can save, and
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
    // The keypad opens on tapping the amount (17 → 108); an amount already there (edit, a found
    // payment) opens with it put away.
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
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
            Column(
                Modifier.fillMaxWidth().clip(Shapes.surface)
                    .clickable(onClickLabel = stringResource(R.string.tap_to_type)) { keypadOpen = true }
                    .padding(top = Space.amountTop, bottom = Space.block),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                        !keypadOpen && s.amount == 0L -> stringResource(R.string.tap_to_type)
                        else -> " "
                    },
                    style = BurrowType.body,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            // Favourites (91): while nothing is typed, one tap saves a usual expense.
            if (f.type == TxnType.EXPENSE && !s.isEdit && s.amount == 0L && s.favourites.isNotEmpty()) {
                Favourites(s.favourites, onFavourites) { fav, label -> vm.spendFavourite(fav, label, onSaved) }
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
                if (s.shows(FormExtra.NOTE)) add { i, n ->
                    val tags = f.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }
                    ListRow(
                        headline = f.note.ifBlank { tags ?: stringResource(R.string.add_note) },
                        supporting = if (f.note.isNotBlank()) listOfNotNull(stringResource(R.string.note), tags).joinToString(" · ") else if (tags != null) stringResource(R.string.tags) else null,
                        index = i, count = n,
                        lead = { IconCircle(painterResource(R.drawable.ic_notes_duotone)) },
                        onClick = { sheet = AddSheet.NOTE },
                    )
                }
                val files = f.kept.size + f.attachments.size
                if (s.shows(FormExtra.ATTACH)) add { i, n ->
                    ListRow(
                        headline = if (files > 0) pluralStringResource(R.plurals.n_files, files, files) else stringResource(R.string.attach_receipt),
                        supporting = if (files > 0) stringResource(R.string.receipt) else null,
                        index = i, count = n,
                        lead = { IconCircle(painterResource(R.drawable.ic_attach_file_duotone)) },
                        onClick = { sheet = AddSheet.ATTACH },
                    )
                }
            }
            ListGroup { rows.forEachIndexed { i, row -> row(i, rows.size) } }
            // Type (109): one choice, like Cashew. Repeat and Subscription open the frequency sheet,
            // Lent and Borrowed the person sheet; the pill then reads "Repeat · Monthly ▾".
            TypePills(
                s, onOnce = { vm.setRepeat(Every.NEVER); vm.setSubscription(false); vm.setLend(null) },
                onRepeat = { sub ->
                    vm.setLend(null); vm.setSubscription(sub)
                    if (f.repeat == Every.NEVER) vm.setRepeat(Every.MONTH)
                    sheet = AddSheet.REPEAT
                },
                onLend = { kind -> vm.setRepeat(Every.NEVER); vm.setSubscription(false); vm.setLend(kind); sheet = AddSheet.LEND },
                onFavourite = { vm.setFavourite(!f.favourite) },
            )
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
                // Portrait: the form scrolls; the keypad opens under it (108) with a ⌄ to put it away,
                // and the list fades where it runs on under the keypad.
                val showKeypad = keypadOpen && !imeUp
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        formContent(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Space.block))
                        if (showKeypad) {
                            Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(Size.listFade)
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.surface.copy(alpha = 0f), c.surface))),
                            )
                        }
                    }
                    if (showKeypad) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.clip(Shapes.pill).background(c.surfaceContainerHigh)
                                    .clickable(onClickLabel = stringResource(R.string.close_keypad)) { keypadOpen = false }
                                    .padding(horizontal = Space.card, vertical = Space.group),
                            ) {
                                androidx.compose.material3.Icon(painterResource(R.drawable.ic_expand_more_regular), stringResource(R.string.close_keypad), tint = c.onSurfaceVariant, modifier = Modifier.size(Size.iconSmall))
                            }
                        }
                        keypad(Modifier)
                    }
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

/**
 * Once · Repeat · Subscription · Lent · Borrowed (109), one choice; income has no Subscription and a
 * transfer none of these. Each shows only when its extra is on in Customise › Expense. The chosen
 * pill names what it set ("Repeat · Monthly ▾", "Lent · Rahul ▾"). The Favourite chip sits at the end.
 */
@Composable
private fun TypePills(s: AddUiState, onOnce: () -> Unit, onRepeat: (subscription: Boolean) -> Unit, onLend: (String) -> Unit, onFavourite: () -> Unit) {
    val f = s.form
    if (f.type == TxnType.TRANSFER) return
    val repeatOn = s.shows(FormExtra.REPEAT) && !s.isEdit
    val lendOn = s.shows(FormExtra.LEND)
    val favouriteOn = s.shows(FormExtra.FAVOURITE) && f.type == TxnType.EXPENSE && !s.isEdit
    if (!repeatOn && !lendOn && !favouriteOn) return
    val caret = painterResource(R.drawable.ic_expand_more_regular)
    val repeating = f.repeat != Every.NEVER
    val every = if (repeating) stringResource(
        when (f.repeat) { Every.WEEK -> R.string.weekly; Every.YEAR -> R.string.yearly; else -> R.string.monthly },
    ) else ""
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        if (repeatOn || lendOn) Chip(stringResource(R.string.once), !repeating && f.lend == null, onOnce)
        if (repeatOn) {
            val on = repeating && !f.subscription
            Chip(if (on) stringResource(R.string.pill_value, stringResource(R.string.extra_repeat), every) else stringResource(R.string.extra_repeat), on, { onRepeat(false) }, trailing = if (on) caret else null)
            if (f.type == TxnType.EXPENSE) {
                val sub = repeating && f.subscription
                Chip(if (sub) stringResource(R.string.pill_value, stringResource(R.string.subscription), every) else stringResource(R.string.subscription), sub, { onRepeat(true) }, trailing = if (sub) caret else null)
            }
        }
        if (lendOn) {
            for (kind in listOf(Lend.LENT, Lend.BORROWED)) {
                val on = f.lend == kind
                val name = stringResource(if (kind == Lend.LENT) R.string.lent else R.string.borrowed)
                Chip(if (on && f.person.isNotBlank()) stringResource(R.string.pill_value, name, f.person) else name, on, { onLend(kind) }, trailing = if (on) caret else null)
            }
        }
        if (favouriteOn) Chip(stringResource(R.string.favourite), f.favourite, onFavourite, icon = painterResource(R.drawable.ic_star_regular))
    }
}
