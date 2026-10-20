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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import app.burrow.money.domain.Bucket
import app.burrow.money.ui.components.AddFab
import app.burrow.money.ui.components.AverageLegend
import app.burrow.money.ui.components.Bar
import app.burrow.money.ui.components.ChartCard
import app.burrow.money.ui.components.LinkButton
import app.burrow.money.ui.components.PillBarChart
import app.burrow.money.ui.filters.ActiveFilterChips
import app.burrow.money.ui.filters.DatesSheet
import app.burrow.money.ui.filters.FilterSheet
import app.burrow.money.ui.filters.Filters
import app.burrow.money.ui.filters.range
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
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
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
                filterCount = state?.filters?.count ?: 0,
                datesActive = state?.filters?.range != null,
                onFilter = { sheet = "filter" },
                onDates = { sheet = "dates" },
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
            ActiveFilterChips(s.filters, s.accounts, s.categories, vm::setFilters, Modifier.padding(horizontal = Space.screen).padding(bottom = Space.small))
            when {
                s.isEmpty -> EmptyState(
                    shape = { Cookie(painterResource(R.drawable.ic_receipt_long_regular)) },
                    title = stringResource(R.string.empty_title),
                    line = stringResource(R.string.empty_activity_line),
                )
                s.groups.isEmpty() && !s.filters.isEmpty && query.isBlank() -> EmptyState(
                    shape = { Cookie(painterResource(R.drawable.ic_filter_alt_off_regular)) },
                    title = stringResource(R.string.nothing_matches),
                    line = stringResource(R.string.nothing_matches_line),
                    button = { BurrowButton(stringResource(R.string.clear_filters), { vm.setFilters(Filters()) }, kind = ButtonKind.TONAL) },
                )
                s.groups.isEmpty() && query.isNotBlank() -> EmptyState(
                    shape = { Cookie(painterResource(R.drawable.ic_search_off_regular)) },
                    title = stringResource(R.string.no_results, query.trim()),
                    line = stringResource(R.string.no_results_line),
                    button = { BurrowButton(stringResource(R.string.clear_search), { vm.search("") }, kind = ButtonKind.TONAL) },
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                    if (query.isBlank()) item(key = "chart", contentType = "chart") { ActivityChartCard(s.chart, vm::select) }
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

    val sheetState = state ?: return
    when (sheet) {
        "filter" -> FilterSheet(sheetState.filters, sheetState.accounts, sheetState.categories, showType = true, count = vm::count, onApply = { vm.setFilters(it); sheet = null }, onDismiss = { sheet = null })
        "dates" -> DatesSheet(sheetState.filters.range, onApply = { vm.setFilters(sheetState.filters.copy(range = it)); sheet = null }, onDismiss = { sheet = null })
    }
}

/** The chart card (11): Last 14 days (or the picked dates), spent, the bars; tap a day for just that day (12). */
@Composable
private fun ActivityChartCard(chart: ActivityChart, onSelect: (Int?) -> Unit) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val selDay = chart.selected?.let { chart.bars.getOrNull(it)?.first }
    ChartCard(
        icon = painterResource(R.drawable.ic_bar_chart_duotone),
        label = if (chart.lastDays) stringResource(R.string.last_14_days) else dates.range(chart.range),
        value = money.plain(chart.total),
        line = if (selDay != null) {
            pluralStringResource(if (chart.income) R.plurals.came_in_on_n else R.plurals.spent_on_n, chart.count, dates.weekdayDay(selDay), chart.count)
        } else {
            pluralStringResource(if (chart.income) R.plurals.came_in_n else R.plurals.spent_n, chart.count, chart.count)
        },
        modifier = Modifier.padding(bottom = Space.small),
        trailing = {
            if (selDay != null) LinkButton(stringResource(R.string.all_days), { onSelect(null) })
            else AverageLegend(stringResource(R.string.a_day, money.short(chart.perDay)))
        },
    ) {
        val labels = chart.bars.map { (d, _) ->
            when (chart.bucket) {
                Bucket.DAY -> if (chart.bars.size <= 14) d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()) else d.dayOfMonth.toString()
                Bucket.WEEK -> d.dayOfMonth.toString()
                Bucket.MONTH -> d.month.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())
            }
        }
        PillBarChart(
            bars = chart.bars.mapIndexed { i, (_, v) -> Bar(labels[i], v) },
            selected = chart.selected ?: chart.bars.lastIndex,
            onSelect = if (chart.bucket == Bucket.DAY) ({ onSelect(it) }) else null,
            labelFor = { i -> chart.bars.getOrNull(i)?.let { (d, v) -> "${dates.weekdayDay(d)}, ${money.plain(v)}" }.orEmpty() },
            description = stringResource(R.string.chart_summary, if (chart.lastDays) stringResource(R.string.last_14_days) else dates.range(chart.range), money.plain(chart.total)),
        )
    }
}
