package app.burrow.money.ui.txn

import app.burrow.money.ui.LocalListPrefs
import app.burrow.money.data.minorDigits
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.data.AccountType
import app.burrow.money.data.CategoryKind
import app.burrow.money.data.TxnStatus
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.halfOf
import app.burrow.money.domain.refundable
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.AmountKeypadSheet
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.Chip
import app.burrow.money.ui.components.ConfirmDialog
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.tick
import app.burrow.money.ui.forTxn
import app.burrow.money.ui.pickers.AccountSheet
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.title
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which sheet the long-press flow shows. */
enum class ActionSheet { MENU, CATEGORY, REFUND, DELETE }

/**
 * Long-press a transaction row anywhere (06): one sheet with Change category, Duplicate to today,
 * Split, Add a refund, Save as favourite and Delete. Hosted once by the nav host ([TxnActionsHost]);
 * rows reach it through [LocalTxnActions]. Transaction detail opens the refund sheet the same way.
 */
@Stable
class TxnActions(val app: AppContainer, val monthStartDay: Int, val onSplit: (Long) -> Unit) {
    var target by mutableStateOf<TxnView?>(null)
        private set
    var sheet by mutableStateOf<ActionSheet?>(null)

    fun open(t: TxnView) { target = t; sheet = ActionSheet.MENU }
    fun refund(t: TxnView) { target = t; sheet = ActionSheet.REFUND }
    fun close() { sheet = null; target = null }
}

val LocalTxnActions = staticCompositionLocalOf<TxnActions?> { null }

@Composable
fun TxnActionsHost(actions: TxnActions) {
    val t = actions.target ?: return
    val app = actions.app
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val askFirst = LocalListPrefs.current.confirmDelete
    fun done(message: Int, undo: (suspend () -> Unit)?) {
        actions.close()
        snackbar.show(context.getString(message), undo)
    }
    when (actions.sheet) {
        ActionSheet.MENU -> TxnMenuSheet(
            actions, t,
            onCategory = { actions.sheet = ActionSheet.CATEGORY },
            onDuplicate = {
                scope.launch {
                    val now = System.currentTimeMillis()
                    val copy = t.txn.copy(
                        id = 0, at = now, createdAt = now, updatedAt = now, status = TxnStatus.POSTED, source = "MANUAL",
                        isSample = false, billOccurrenceId = null, linkedId = null,
                    )
                    val id = app.money.save(copy)
                    done(R.string.duplicated_today) { app.money.delete(id) }
                }
            },
            onSplit = { actions.close(); actions.onSplit(t.txn.id) },
            onRefund = { actions.sheet = ActionSheet.REFUND },
            onFavourite = {
                scope.launch {
                    val id = app.next.addFavourite(t.txn.payee ?: t.categoryName.orEmpty(), t.txn.amount, t.txn.categoryId ?: return@launch, t.txn.accountId)
                    done(R.string.favourite_added) { app.next.removeFavourite(id) }
                }
            },
            onDelete = {
                if (askFirst) actions.sheet = ActionSheet.DELETE
                else scope.launch { val gone = app.money.delete(t.txn.id); done(R.string.txn_deleted, gone?.let { g -> { app.money.restore(g) } }) }
            },
        )
        ActionSheet.CATEGORY -> {
            val kind = if (t.txn.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            val cats by app.money.categories.collectAsStateWithLifecycle(emptyList())
            val period = remember { BudgetPeriod.containing(LocalDate.now(), actions.monthStartDay) }
            val spent by remember { app.money.spentByCategory(period) }.collectAsStateWithLifecycle(emptyMap())
            CategorySheet(cats.filter { it.kind == kind }, spent, t.txn.categoryId, { cat ->
                scope.launch {
                    val before = t.txn
                    app.money.save(before.copy(categoryId = cat.id, updatedAt = System.currentTimeMillis()))
                    done(R.string.saved) { app.money.restore(before) }
                }
            }, actions::close)
        }
        ActionSheet.REFUND -> RefundSheet(app, t, onSaved = { undo -> done(R.string.refund_saved, undo) }, onDismiss = actions::close)
        ActionSheet.DELETE -> ConfirmDialog(
            icon = painterResource(R.drawable.ic_delete_regular),
            title = stringResource(R.string.delete_txn_title),
            text = stringResource(R.string.delete_txn_text),
            confirm = stringResource(R.string.delete),
            dismiss = stringResource(R.string.cancel),
            onConfirm = { scope.launch { val gone = app.money.delete(t.txn.id); done(R.string.txn_deleted, gone?.let { g -> { app.money.restore(g) } }) } },
            onDismiss = actions::close,
        )
        null -> Unit
    }
}

/** The long-press sheet (06): payee and amount on top, the actions, Delete in its own group. */
@Composable
private fun TxnMenuSheet(
    actions: TxnActions,
    t: TxnView,
    onCategory: () -> Unit,
    onDuplicate: () -> Unit,
    onSplit: () -> Unit,
    onRefund: () -> Unit,
    onFavourite: () -> Unit,
    onDelete: () -> Unit,
) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val c = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val parts by remember(t.txn.id) { actions.app.next.splitsOf(t.txn.id) }.collectAsStateWithLifecycle(emptyList())
    val type = t.txn.type
    val expense = type == TxnType.EXPENSE && t.txn.amount > 0 // Insights lists show refunds as negative spending
    val categorised = expense || type == TxnType.INCOME
    BurrowSheet(actions::close) {
        SheetHeader(t.title(), actions::close, stringResource(R.string.dot_join, money.forTxn(type, t.txn.amount), dates.day(dates.date(t.txn.at), today)))
        SheetBody {
            val rows = buildList<@Composable (Int, Int) -> Unit> {
                if (categorised && parts.isEmpty()) add { i, n ->
                    ListRow(stringResource(R.string.change_category), i, n, supporting = t.categoryName, lead = { CategoryCircle(t.categoryIcon, t.categoryHue) }, onClick = onCategory)
                }
                if (categorised || type == TxnType.TRANSFER) add { i, n ->
                    ListRow(stringResource(R.string.duplicate_today), i, n, supporting = stringResource(R.string.duplicate_today_line), lead = { IconCircle(painterResource(R.drawable.ic_content_copy_duotone)) }, onClick = onDuplicate)
                }
                if (expense && t.txn.status == TxnStatus.POSTED) {
                    add { i, n ->
                        ListRow(
                            stringResource(R.string.split_across), i, n,
                            supporting = if (parts.isNotEmpty()) androidx.compose.ui.res.pluralStringResource(R.plurals.split_in_parts, parts.size, parts.size) else null,
                            lead = { IconCircle(painterResource(R.drawable.ic_call_split_duotone)) }, onClick = onSplit,
                        )
                    }
                    add { i, n ->
                        ListRow(stringResource(R.string.add_refund), i, n, supporting = stringResource(R.string.add_refund_line), lead = { IconCircle(painterResource(R.drawable.ic_refund_duotone)) }, onClick = onRefund)
                    }
                    if (t.txn.categoryId != null) add { i, n ->
                        ListRow(stringResource(R.string.save_as_favourite), i, n, supporting = stringResource(R.string.save_as_favourite_line), lead = { IconCircle(painterResource(R.drawable.ic_star_duotone)) }, onClick = onFavourite)
                    }
                }
            }
            if (rows.isNotEmpty()) ListGroup { rows.forEachIndexed { i, r -> r(i, rows.size) } }
            ListGroup {
                ListRow(
                    stringResource(R.string.delete), 0, 1, headlineColor = c.error,
                    lead = { IconCircle(painterResource(R.drawable.ic_delete_duotone), container = c.errorContainer, content = c.error) },
                    onClick = onDelete,
                )
            }
        }
    }
}

/**
 * Refund (09): how much came back (− amount +, All of it / Half, tap the amount to type it), the
 * account it went into; it lowers the category's spending and is never income.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RefundSheet(app: AppContainer, t: TxnView, onSaved: (suspend () -> Unit) -> Unit, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val refunds by remember(t.txn.id) { app.next.refundsOf(t.txn.id) }.collectAsStateWithLifecycle(null)
    val accounts by app.money.accounts.collectAsStateWithLifecycle(emptyList())
    val done = refunds ?: return
    val max = refundable(t.txn.amount, done.sumOf { it.amount })
    var amount by rememberSaveable { mutableLongStateOf(-1L) }
    if (amount < 0) amount = max
    var accountId by rememberSaveable { mutableLongStateOf(t.txn.accountId) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val digits = minorDigits(t.txn.currency)
    var unit = 1L
    repeat(digits) { unit *= 10 }
    val step = (max / 20 / unit * unit).coerceAtLeast(unit)
    val account = accounts.firstOrNull { it.account.id == accountId }?.account
    val ok = amount in 1..max
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.refund), onDismiss, stringResource(R.string.dot_join, t.title(), money.plain(t.txn.amount)))
        SheetBody {
            if (max == 0L) {
                Text(stringResource(R.string.refunded_in_full), style = BurrowType.body, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = Space.small), verticalAlignment = Alignment.CenterVertically) {
                    TonalIconButton(painterResource(R.drawable.ic_remove_regular), stringResource(R.string.key_minus), { haptics.tick(); amount = (amount - step).coerceAtLeast(unit.coerceAtMost(max)) })
                    Text(
                        money.plain(amount), style = BurrowType.display, color = c.onSurface, textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = stringResource(R.string.type_amount)) { picker = "amount" },
                    )
                    TonalIconButton(painterResource(R.drawable.ic_add_regular), stringResource(R.string.key_plus), { haptics.tick(); amount = (amount + step).coerceAtMost(max) })
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.small, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    Chip(stringResource(R.string.all_of_it), amount == max, { amount = max })
                    val half = halfOf(max, digits)
                    Chip(stringResource(R.string.half), amount == half, { amount = half })
                }
            }
            ListGroup {
                ListRow(
                    stringResource(R.string.back_into, account?.name.orEmpty()), 0, 1,
                    supporting = stringResource(R.string.refund_lowers, t.categoryName.orEmpty()),
                    lead = { CategoryCircle(account?.icon, account?.colorHue) },
                    onClick = { picker = "account" },
                )
            }
            if (done.isNotEmpty()) Text(
                stringResource(R.string.refunded_so_far, money.plain(done.sumOf { it.amount })),
                style = BurrowType.body, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.small),
            )
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
            BurrowButton(stringResource(R.string.save_refund), {
                if (!ok) return@BurrowButton
                haptics.confirm()
                scope.launch { onSaved(app.next.addRefund(t.txn, amount, accountId)) }
            }, Modifier.weight(1f), if (ok) ButtonKind.FILLED else ButtonKind.OFF, height = Size.buttonBottom)
        }
    }
    when (picker) {
        "amount" -> AmountKeypadSheet(stringResource(R.string.refund), amount, digits, { amount = it; picker = null }, { picker = null }, max = max)
        "account" -> AccountSheet(accounts.filter { it.account.type != AccountType.LOAN && it.account.type != AccountType.SAVINGS }, accountId, { accountId = it.account.id; picker = null }, { picker = null })
    }
}
