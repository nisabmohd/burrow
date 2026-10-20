package app.burrow.money.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.AddFab
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.Cookie
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.FilterHeaderButtons
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.TabScreen
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.txn.TxnRow
import java.time.LocalDate

/**
 * Activity (11): search, then every transaction grouped by day with the day's total.
 * Filters and the 14-day chart come on Day 16.
 */
@Composable
fun ActivityScreen(vm: ActivityViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.searchText.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val dates = rememberDateText()
    val today = remember { LocalDate.now() }
    TabScreen(
        title = stringResource(R.string.tab_activity),
        actions = {
            FilterHeaderButtons(
                funnel = painterResource(R.drawable.ic_filter_list_regular),
                calendar = painterResource(R.drawable.ic_calendar_month_regular),
                filterLabel = stringResource(R.string.filter),
                datesLabel = stringResource(R.string.dates),
                filterCount = 0,
                datesActive = false,
                onFilter = {}, // Day 16
                onDates = {}, // Day 16
            )
        },
        fab = { AddFab(stringResource(R.string.add_expense), onAdd) },
    ) { padding ->
        val s = state ?: return@TabScreen
        Column(Modifier.fillMaxSize()) {
            PillTextField(
                value = query,
                onValueChange = vm::search,
                placeholder = stringResource(R.string.search_hint),
                leading = painterResource(R.drawable.ic_search_regular),
                clearLabel = stringResource(R.string.clear),
                imeAction = ImeAction.Search,
                modifier = Modifier.padding(horizontal = Space.screen).padding(bottom = Space.small),
            )
            when {
                s.isEmpty -> EmptyState(
                    shape = { Cookie(painterResource(R.drawable.ic_receipt_long_regular)) },
                    title = stringResource(R.string.empty_title),
                    line = stringResource(R.string.empty_activity_line),
                )
                s.groups.isEmpty() -> EmptyState(
                    shape = { Cookie(painterResource(R.drawable.ic_search_off_regular)) },
                    title = stringResource(R.string.no_results, query.trim()),
                    line = stringResource(R.string.no_results_line),
                    button = { BurrowButton(stringResource(R.string.clear_search), { vm.search("") }, kind = ButtonKind.TONAL) },
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                    s.groups.forEach { g ->
                        item(key = "day-${g.date}", contentType = "header") {
                            SectionHeader(
                                dates.day(g.date, today),
                                meta = if (g.total != 0L) money.signed(g.total) else null,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        itemsIndexed(g.txns, key = { _, t -> t.txn.id }, contentType = { _, _ -> "txn" }) { i, t ->
                            TxnRow(
                                t, i, g.txns.size, dates, today,
                                onClick = { onOpen(t.txn.id) },
                                modifier = Modifier.animateItem().padding(top = if (i > 0) Space.row else 0.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
