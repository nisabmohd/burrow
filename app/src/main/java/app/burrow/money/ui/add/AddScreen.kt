package app.burrow.money.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.burrow.money.ui.pickers.TextSheet
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Space

private enum class AddSheet { CATEGORY, ACCOUNT, TO_ACCOUNT, DATE, PAYEE, NOTE }

private val TYPES = listOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.TRANSFER)

/**
 * New expense / income / transfer (designs 17, 18). Close · title · Save; the type group; the
 * amount; rows for category, account, date, payee and note; the keypad pinned at the bottom.
 * Save looks off until there is an amount and a category, and tapping it early turns the missing
 * row red.
 */
@Composable
fun AddScreen(vm: AddViewModel, onClose: () -> Unit, onSaved: (Saved) -> Unit) {
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

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(
            navIcon = painterResource(R.drawable.ic_close_regular),
            navLabel = stringResource(R.string.close),
            onNav = onClose,
            title = stringResource(titleFor(f.type, s.isEdit)),
        ) {
            BurrowButton(stringResource(R.string.save), save, kind = if (s.canSave) ButtonKind.FILLED else ButtonKind.OFF)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            ButtonGroup(TYPES, f.type, { stringResource(typeLabel(it)) }, vm::setType)
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
                    ListRow(
                        headline = dates.dayTime(f.at), supporting = stringResource(R.string.date), index = i, count = n,
                        lead = { IconCircle(painterResource(R.drawable.ic_schedule_duotone)) },
                        onClick = { sheet = AddSheet.DATE },
                    )
                }
                add { i, n ->
                    ListRow(
                        headline = f.payee.ifBlank { stringResource(R.string.add_payee) }, supporting = stringResource(R.string.payee), index = i, count = n,
                        lead = { IconCircle(painterResource(R.drawable.ic_storefront_duotone)) },
                        onClick = { sheet = AddSheet.PAYEE },
                    )
                }
                add { i, n ->
                    ListRow(
                        headline = f.note.ifBlank { stringResource(R.string.add_note) }, supporting = stringResource(R.string.note), index = i, count = n,
                        lead = { IconCircle(painterResource(R.drawable.ic_notes_duotone)) },
                        onClick = { sheet = AddSheet.NOTE },
                    )
                }
            }
            ListGroup { rows.forEachIndexed { i, row -> row(i, rows.size) } }
        }
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
            modifier = Modifier.padding(start = Space.screen, end = Space.screen, top = Space.block, bottom = Space.screen),
        )
    }

    when (sheet) {
        AddSheet.CATEGORY -> CategorySheet(s.categoriesForType, s.spentByCategory, f.categoryId, { vm.setCategory(it.id); sheet = null }, { sheet = null })
        AddSheet.ACCOUNT -> AccountSheet(s.accountsForType, f.accountId, { vm.setAccount(it.account.id); sheet = null }, { sheet = null })
        AddSheet.TO_ACCOUNT -> AccountSheet(s.accounts.filter { it.account.id != f.accountId }, f.toAccountId, { vm.setToAccount(it.account.id); sheet = null }, { sheet = null })
        AddSheet.DATE -> DateSheet(dates.date(f.at), { vm.setDate(it); sheet = null }, { sheet = null })
        AddSheet.PAYEE -> TextSheet(stringResource(R.string.payee), f.payee, stringResource(R.string.payee_hint), { vm.setPayee(it); sheet = null }, { sheet = null })
        AddSheet.NOTE -> TextSheet(stringResource(R.string.note), f.note, stringResource(R.string.note_hint), { vm.setNote(it); sheet = null }, { sheet = null })
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
    saved.wasEdit -> R.string.saved
    saved.type == TxnType.INCOME -> R.string.saved_income
    saved.type == TxnType.TRANSFER -> R.string.saved_transfer
    else -> R.string.saved_expense
}
