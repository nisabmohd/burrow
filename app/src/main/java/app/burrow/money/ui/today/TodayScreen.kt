package app.burrow.money.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.ui.components.AddFab
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
fun TodayScreen(vm: TodayViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit, onSeeAll: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val dates = rememberDateText()
    val today = remember { LocalDate.now() }
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    TabScreen(
        title = stringResource(R.string.tab_today),
        actions = {
            // Accounts opens on Day 20.
            TonalIconButton(painterResource(R.drawable.ic_account_balance_wallet_regular), stringResource(R.string.accounts), onClick = {})
            TonalIconButton(painterResource(R.drawable.ic_add_regular), stringResource(R.string.add_expense), onClick = onAdd)
        },
        fab = { AddFab(stringResource(R.string.add_expense), onAdd) },
    ) { padding ->
        val s = state ?: return@TabScreen // nothing drawn until the data is in: no "₹0" flash
        if (s.isEmpty) {
            EmptyState(
                shape = { Cookie(painterResource(R.drawable.ic_receipt_long_regular)) },
                title = stringResource(R.string.empty_title),
                line = stringResource(R.string.empty_today_line),
                // "Record payments for me" arrives with the auto-capture intro (Day 8).
            )
            return@TabScreen
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(Space.block)) {
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
        }
    }
}
