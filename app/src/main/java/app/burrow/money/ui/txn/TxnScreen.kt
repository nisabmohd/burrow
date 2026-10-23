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
fun TxnScreen(
    vm: TxnViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onSplit: (Long) -> Unit = {},
    onOpenTxn: (Long) -> Unit = {},
) {
    val extras by vm.extras.collectAsStateWithLifecycle()
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
            if (t != null && t.txn.type != TxnType.REFUND) TonalIconButton(painterResource(R.drawable.ic_content_copy_regular), stringResource(R.string.duplicate), { onDuplicate(t.txn.id) })
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
                            // A split payment's categories are changed on the split screen.
                            lead = { CategoryCircle(t.categoryIcon, t.categoryHue) }, onClick = { if (s.parts.isNotEmpty()) onSplit(t.txn.id) else sheet = TxnSheet.CATEGORY },
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
                // What was added while creating it (every type, transfers too).
                extras.person?.let { who ->
                    add { i, n ->
                        ListRow(
                            who, i, n, supporting = stringResource(if (t.txn.lend == app.burrow.money.data.Lend.BORROWED) R.string.borrowed_from else R.string.lent_to),
                            lead = { IconCircle(painterResource(R.drawable.ic_handshake_duotone)) },
                        )
                    }
                }
                if (extras.tags.isNotEmpty()) {
                    add { i, n ->
                        ListRow(
                            extras.tags.joinToString("  ") { "#$it" }, i, n, supporting = stringResource(R.string.tags),
                            lead = { IconCircle(painterResource(R.drawable.ic_tag_duotone)) },
                        )
                    }
                }
                extras.attachments.forEach { a ->
                    add { i, n ->
                        ListRow(
                            a.name, i, n, supporting = stringResource(R.string.receipt_line, android.text.format.Formatter.formatShortFileSize(context, a.bytes)),
                            lead = { IconCircle(painterResource(R.drawable.ic_attach_file_duotone)) },
                            onClick = {
                                val uri = (context.applicationContext as app.burrow.money.BurrowApp).container.attachments.uriFor(a)
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, a.mime)
                                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    )
                                }
                            },
                        )
                    }
                }
            }
            ListGroup { rows.forEachIndexed { i, r -> r(i, rows.size) } }
            // Split and refund (07): an expense can be shared across categories or get money back.
            val actions = LocalTxnActions.current
            if (t.txn.type == TxnType.EXPENSE && t.txn.status == app.burrow.money.data.TxnStatus.POSTED) {
                ListGroup {
                    ListRow(
                        stringResource(R.string.split_across), 0, 2,
                        supporting = if (s.parts.isEmpty()) stringResource(R.string.split_line)
                        else s.parts.joinToString(" · ") { p -> "${s.allCategories.firstOrNull { it.id == p.categoryId }?.name.orEmpty()} ${money.plain(p.amount)}" },
                        lead = { IconCircle(painterResource(R.drawable.ic_call_split_duotone)) },
                        onClick = { onSplit(t.txn.id) },
                    )
                    ListRow(
                        stringResource(R.string.add_refund), 1, 2,
                        supporting = if (s.refunds.isEmpty()) stringResource(R.string.add_refund_line)
                        else stringResource(R.string.refunded_on, money.plain(s.refunds.sumOf { it.amount }), dates.shortDay(dates.date(s.refunds.last().at))),
                        lead = { IconCircle(painterResource(R.drawable.ic_refund_duotone)) },
                        onClick = { actions?.refund(t) },
                    )
                }
            }
            s.original?.let { o ->
                ListGroup {
                    ListRow(
                        stringResource(R.string.refund_for, o.title()), 0, 1, supporting = money.forTxn(o.txn.type, o.txn.amount),
                        lead = { TxnCircle(o) }, trailing = { app.burrow.money.ui.components.Chevron() }, onClick = { onOpenTxn(o.txn.id) },
                    )
                }
            }
            s.capture?.let { m ->
                app.burrow.money.ui.components.SectionHeader(stringResource(if (m.source == "SMS") R.string.recorded_from_sms else R.string.recorded_from_app))
                if (m.body.isBlank()) {
                    androidx.compose.material3.Text(stringResource(R.string.message_forgotten), style = app.burrow.money.ui.theme.BurrowType.body, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    app.burrow.money.ui.components.MessageBox(stringResource(R.string.dot_join, m.sender, dates.time(m.receivedAt)), m.body, emptyList())
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.small), horizontalArrangement = Arrangement.spacedBy(Space.inline)) {
                val askFirst = app.burrow.money.ui.LocalListPrefs.current.confirmDelete
                BurrowButton(stringResource(R.string.delete), {
                    // Customise › Transactions › Ask before delete; otherwise Undo is the safety net.
                    if (askFirst) sheet = TxnSheet.DELETE else {
                        // Leave only once the row is gone, so the screen's scope isn't cancelled before Undo shows.
                        vm.delete { undo -> snackbar.show(context.getString(R.string.txn_deleted), undo); onBack() }
                    }
                }, Modifier.weight(1f), ButtonKind.DANGER, painterResource(R.drawable.ic_delete_regular))
                if (t.txn.type != TxnType.REFUND) BurrowButton(stringResource(R.string.edit), { onEdit(t.txn.id) }, Modifier.weight(1f), ButtonKind.FILLED, painterResource(R.drawable.ic_edit_regular))
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
                    vm.delete { undo -> snackbar.show(context.getString(R.string.txn_deleted), undo); onBack() }
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
