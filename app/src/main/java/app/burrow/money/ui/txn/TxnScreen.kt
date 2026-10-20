package app.burrow.money.ui.txn

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.data.TxnType
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.ConfirmDialog
import app.burrow.money.ui.components.Cookie
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.forTxn
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.pickers.TextSheet
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.title

private enum class TxnSheet { CATEGORY, NOTE, DELETE }

/**
 * Transaction (21): back · Duplicate; centred circle, payee, amount, date; Category, account and
 * Note rows (category and note change in place, with Undo); Delete (asks first, 73) and Edit.
 * The "Recorded from SMS" box comes with auto-capture (Day 7).
 */
@Composable
fun TxnScreen(vm: TxnViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit, onDuplicate: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf<TxnSheet?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val t = s.txn
        ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack) {
            if (t != null) TonalIconButton(painterResource(R.drawable.ic_content_copy_regular), stringResource(R.string.duplicate), { onDuplicate(t.txn.id) })
        }
        if (t == null) {
            EmptyState(
                shape = { Cookie(painterResource(R.drawable.ic_delete_regular)) },
                title = stringResource(R.string.txn_deleted_title),
                line = stringResource(R.string.txn_deleted_line),
            )
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            CenterHeader(
                icon = { TxnCircle(t, Size.iconCircleBig) },
                title = t.title(),
                value = money.forTxn(t.txn.type, t.txn.amount),
                line = dates.dayTime(t.txn.at),
            )
            val transfer = t.txn.type == TxnType.TRANSFER
            val rows = buildList<@Composable (Int, Int) -> Unit> {
                if (transfer) {
                    add { i, n -> ListRow(t.accountName, i, n, supporting = stringResource(R.string.from), lead = { AccountCircle(t, false) }) }
                    add { i, n -> ListRow(t.toAccountName.orEmpty(), i, n, supporting = stringResource(R.string.to), lead = { AccountCircle(t, true) }) }
                } else {
                    add { i, n ->
                        ListRow(
                            t.categoryName.orEmpty(), i, n, supporting = stringResource(R.string.category),
                            lead = { CategoryCircle(t.categoryIcon, t.categoryHue) }, onClick = { sheet = TxnSheet.CATEGORY },
                        )
                    }
                    add { i, n ->
                        ListRow(
                            t.accountName, i, n,
                            supporting = stringResource(if (t.txn.type == TxnType.INCOME) R.string.paid_into else R.string.paid_from),
                            lead = { AccountCircle(t, false) },
                        )
                    }
                }
                add { i, n ->
                    ListRow(
                        t.txn.note ?: stringResource(R.string.add_note), i, n, supporting = stringResource(R.string.note),
                        lead = { IconCircle(painterResource(R.drawable.ic_notes_duotone)) }, onClick = { sheet = TxnSheet.NOTE },
                    )
                }
            }
            ListGroup { rows.forEachIndexed { i, r -> r(i, rows.size) } }
            Row(Modifier.fillMaxWidth().padding(top = Space.small), horizontalArrangement = Arrangement.spacedBy(Space.inline)) {
                BurrowButton(stringResource(R.string.delete), { sheet = TxnSheet.DELETE }, Modifier.weight(1f), ButtonKind.DANGER, painterResource(R.drawable.ic_delete_regular))
                BurrowButton(stringResource(R.string.edit), { onEdit(t.txn.id) }, Modifier.weight(1f), ButtonKind.FILLED, painterResource(R.drawable.ic_edit_regular))
            }
        }
        val txn = t
        when (sheet) {
            TxnSheet.CATEGORY -> CategorySheet(s.categories, s.spentByCategory, txn.txn.categoryId, { cat ->
                sheet = null
                vm.setCategory(cat.id) { undo -> snackbar.show(context.getString(R.string.saved), undo) }
            }, { sheet = null })
            TxnSheet.NOTE -> TextSheet(stringResource(R.string.note), txn.txn.note.orEmpty(), stringResource(R.string.note_hint), { text ->
                sheet = null
                vm.setNote(text) { undo -> snackbar.show(context.getString(R.string.saved), undo) }
            }, { sheet = null })
            TxnSheet.DELETE -> ConfirmDialog(
                icon = painterResource(R.drawable.ic_delete_regular),
                title = stringResource(R.string.delete_txn_title),
                text = stringResource(R.string.delete_txn_text),
                confirm = stringResource(R.string.delete),
                dismiss = stringResource(R.string.cancel),
                onConfirm = {
                    sheet = null
                    vm.delete { undo -> snackbar.show(context.getString(R.string.txn_deleted), undo) }
                    onBack()
                },
                onDismiss = { sheet = null },
            )
            null -> Unit
        }
    }
}

/** The account's (or, for [to], the receiving account's) circle in its own colour. */
@Composable
private fun AccountCircle(t: app.burrow.money.data.TxnView, to: Boolean) =
    if (to) CategoryCircle(t.toAccountIcon, t.toAccountHue) else CategoryCircle(t.accountIcon, t.accountHue)
