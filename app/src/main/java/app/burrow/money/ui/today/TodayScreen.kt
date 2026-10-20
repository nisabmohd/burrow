package app.burrow.money.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.ui.capture.rememberCapturePaused
import app.burrow.money.ui.components.AddFab
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.Cookie
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.LinkButton
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.Note
import app.burrow.money.ui.components.NoteKind
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.TabScreen
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.txn.TxnRow
import java.time.LocalDate

/**
 * Today (04, 06, 09): notes first (sample data), the budget hero, Recent with "See all".
 * With no transactions at all it shows only the empty state. Widgets come on Day 19.
 */
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onAdd: () -> Unit,
    onAccounts: () -> Unit,
    onOpen: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onReview: () -> Unit,
    onCaptureIntro: () -> Unit,
    onFixCapture: () -> Unit,
    onBill: (Long) -> Unit,
    onCategory: (Long) -> Unit,
    onGoal: (Long) -> Unit,
) {
    var editHome by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val state by vm.state.collectAsStateWithLifecycle()
    val dates = rememberDateText()
    val today = remember { LocalDate.now() }
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    TabScreen(
        title = stringResource(R.string.tab_today),
        actions = {
            TonalIconButton(painterResource(R.drawable.ic_account_balance_wallet_regular), stringResource(R.string.accounts), onClick = onAccounts)
            TonalIconButton(painterResource(R.drawable.ic_add_regular), stringResource(R.string.add_expense), onClick = onAdd)
        },
        fab = { AddFab(stringResource(R.string.add_expense), onAdd) },
    ) { padding ->
        val s = state ?: return@TabScreen // nothing drawn until the data is in: no "₹0" flash
        val paused = rememberCapturePaused(s.smsCapture, s.appCapture)
        if (s.isEmpty) {
            EmptyState(
                shape = { Cookie(painterResource(R.drawable.ic_receipt_long_regular)) },
                title = stringResource(R.string.empty_title),
                line = stringResource(R.string.empty_today_line),
                button = if (!s.smsCapture && !s.appCapture) ({ BurrowButton(stringResource(R.string.record_for_me), onCaptureIntro, kind = ButtonKind.TONAL) }) else null,
            )
            return@TabScreen
        }
        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        app.burrow.money.ui.components.KeepTopVisible(list, paused to s.hasSamples)
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(Space.block)) {
            if (paused != null) {
                item(key = "paused", contentType = "note") {
                    Note(
                        kind = NoteKind.WARN,
                        icon = painterResource(R.drawable.ic_notifications_paused_duotone),
                        title = stringResource(R.string.capture_paused),
                        line = stringResource(paused),
                        action = stringResource(R.string.fix_it),
                        onAction = onFixCapture,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (s.hasSamples) {
                item(key = "samples", contentType = "note") {
                    Note(
                        kind = NoteKind.INFO,
                        icon = painterResource(R.drawable.ic_science_duotone),
                        title = stringResource(R.string.sample_title),
                        line = stringResource(R.string.sample_line),
                        action = stringResource(R.string.remove),
                        onAction = {
                            vm.removeSamples()
                            snackbar.show(context.getString(R.string.samples_removed))
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "hero", contentType = "hero") { BudgetHero(s.hero, Modifier.animateItem()) }
            if (s.toReview > 0) {
                item(key = "review", contentType = "note") {
                    Note(
                        kind = NoteKind.INFO,
                        icon = painterResource(R.drawable.ic_sms_duotone),
                        title = pluralStringResource(R.plurals.n_to_review, s.toReview, s.toReview),
                        line = stringResource(R.string.found_in_messages),
                        onClick = onReview,
                        arrow = painterResource(R.drawable.ic_arrow_forward_regular),
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (s.shown.isNotEmpty()) item(key = "widgets", contentType = "widgets") {
                TodayWidgets(
                    s.widgets, s.shown, s.hero,
                    onWeek = onSeeAll, onBill = onBill,
                    onMarkPaid = { id -> vm.markPaid(id) { undo -> snackbar.show(context.getString(R.string.marked_paid), undo) } },
                    onNetWorth = onAccounts, onCategory = onCategory, onGoal = onGoal,
                )
            }
            // Header and rows as one block, so the rows keep the group's 2 dp gap.
            item(key = "recent", contentType = "recent") {
                Column(Modifier.animateItem()) {
                    SectionHeader(stringResource(R.string.recent), trailing = { LinkButton(stringResource(R.string.see_all), onSeeAll) })
                    ListGroup {
                        s.recent.forEachIndexed { i, t ->
                            key(t.txn.id) { TxnRow(t, i, s.recent.size, dates, today, onClick = { onOpen(t.txn.id) }) }
                        }
                    }
                }
            }
            item(key = "edit-home", contentType = "button") {
                BurrowButton(
                    stringResource(R.string.edit_home), { editHome = true },
                    Modifier.fillMaxWidth().padding(top = Space.block), ButtonKind.TONAL, painterResource(R.drawable.ic_dashboard_customize_regular),
                )
            }
        }
    }
    val s = state ?: return
    if (editHome) EditHomeSheet(s.shown, vm::setWidget) { editHome = false }
}
