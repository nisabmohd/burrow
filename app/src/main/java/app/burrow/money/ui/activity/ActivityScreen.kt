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
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.ui.components.SwipeAction
import app.burrow.money.ui.components.SwipeRow
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
fun ActivityScreen(vm: ActivityViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit, onEdit: (Long) -> Unit = {}, onDuplicate: (Long) -> Unit = {}, focusSearch: kotlinx.coroutines.flow.MutableStateFlow<Boolean>? = null, onComingUp: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Activity opens on the list: the chart is one row until tapped (11, 110).
    var chartOpen by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val query by vm.searchText.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val dates = rememberDateText()
    val today = remember { LocalDate.now() }
    val prefs = app.burrow.money.ui.LocalListPrefs.current
    val snackbar = app.burrow.money.ui.components.LocalSnackbar.current
    val editLabel = stringResource(R.string.edit)
    val duplicateLabel = stringResource(R.string.duplicate)
    val deleteLabel = stringResource(R.string.delete)
    val deletedLabel = stringResource(R.string.txn_deleted)
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) }
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
            val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            PillTextField(
                value = query,
                onValueChange = vm::search,
                placeholder = stringResource(R.string.search_hint),
                leading = painterResource(R.drawable.ic_search_regular),
                clearLabel = stringResource(R.string.clear),
                imeAction = ImeAction.Search,
                modifier = Modifier.padding(horizontal = Space.screen).padding(bottom = Space.small).focusRequester(focus),
            )
            // The Search shortcut: straight into typing.
            val wantFocus = focusSearch?.collectAsStateWithLifecycle()?.value == true
            androidx.compose.runtime.LaunchedEffect(wantFocus) {
                if (wantFocus) { runCatching { focus.requestFocus() }; focusSearch?.value = false }
            }
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
                    if (query.isBlank() && s.comingThisWeek.isNotEmpty()) item(key = "coming", contentType = "row") {
                        val n = s.comingThisWeek.size
                        app.burrow.money.ui.components.ListGroup(Modifier.padding(bottom = Space.small)) {
                            app.burrow.money.ui.components.ListRow(
                                pluralStringResource(R.plurals.n_coming_up, n, n), 0, 1,
                                supporting = stringResource(R.string.amount_this_week, money.plain(s.comingThisWeek.filter { !it.income }.sumOf { it.amount })),
                                lead = { app.burrow.money.ui.components.IconCircle(painterResource(R.drawable.ic_event_upcoming_duotone)) },
                                trailing = { app.burrow.money.ui.components.Chevron() },
                                onClick = onComingUp,
                            )
                        }
                    }
                    if (query.isBlank()) item(key = "chart", contentType = "chart") {
                        if (chartOpen || s.chart.selected != null) ActivityChartCard(s.chart, vm::select, onFold = { vm.select(null); chartOpen = false })
                        else ChartSummaryRow(s.chart) { chartOpen = true }
                    }
                    s.groups.forEach { g ->
                        item(key = "day-${g.date}", contentType = "header") {
                            SectionHeader(
                                dates.day(g.date, today),
                                meta = if (g.total != 0L) money.signed(g.total) else null,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        itemsIndexed(g.txns, key = { _, t -> t.txn.id }, contentType = { _, _ -> "txn" }) { i, t ->
                            // Customise › Transactions › Swipe: right edits or duplicates, left deletes.
                            val right = when (prefs.swipeRight) {
                                app.burrow.money.data.SwipeRight.EDIT -> SwipeAction(painterResource(R.drawable.ic_edit_regular), editLabel, true) { onEdit(t.txn.id) }
                                app.burrow.money.data.SwipeRight.DUPLICATE -> SwipeAction(painterResource(R.drawable.ic_content_copy_regular), duplicateLabel, true) { onDuplicate(t.txn.id) }
                                else -> null
                            }
                            val left = if (prefs.swipeLeft == app.burrow.money.data.SwipeLeft.DELETE) SwipeAction(painterResource(R.drawable.ic_delete_regular), deleteLabel, false) {
                                if (prefs.confirmDelete) confirmDelete = t.txn.id else vm.delete(t.txn.id) { undo -> snackbar.show(deletedLabel, undo) }
                            } else null
                            SwipeRow(right, left, app.burrow.money.ui.theme.Shapes.grouped(i, g.txns.size), Modifier.animateItem().padding(top = if (i > 0) Space.row else 0.dp)) {
                                TxnRow(t, i, g.txns.size, dates, today, onClick = { onOpen(t.txn.id) })
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        app.burrow.money.ui.components.ConfirmDialog(
            icon = painterResource(R.drawable.ic_delete_regular),
            title = stringResource(R.string.delete_txn_title),
            text = stringResource(R.string.delete_txn_text),
            confirm = deleteLabel,
            dismiss = stringResource(R.string.cancel),
            onConfirm = { confirmDelete = null; vm.delete(id) { undo -> snackbar.show(deletedLabel, undo) } },
            onDismiss = { confirmDelete = null },
        )
    }
    val sheetState = state ?: return
    when (sheet) {
        "filter" -> FilterSheet(sheetState.filters, sheetState.accounts, sheetState.categories, showType = true, count = vm::count, onApply = { vm.setFilters(it); sheet = null }, onDismiss = { sheet = null })
        "dates" -> DatesSheet(sheetState.filters.range, onApply = { vm.setFilters(sheetState.filters.copy(range = it)); sheet = null }, onDismiss = { sheet = null })
    }
}

/** The folded chart (11): "₹13,738 spent" · "28 transactions · last 14 days" · ⌄ opens it (110). */
@Composable
private fun ChartSummaryRow(chart: ActivityChart, onOpen: () -> Unit) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val c = androidx.compose.material3.MaterialTheme.colorScheme
    app.burrow.money.ui.components.ListGroup(Modifier.padding(bottom = Space.small)) {
        app.burrow.money.ui.components.ListRow(
            stringResource(if (chart.income) R.string.came_in_amount else R.string.activity_summary, money.plain(chart.total)), 0, 1,
            supporting = if (chart.lastDays) pluralStringResource(R.plurals.activity_summary_line, chart.count, chart.count)
            else stringResource(R.string.dot_join, pluralStringResource(R.plurals.n_transactions, chart.count, chart.count), dates.range(chart.range)),
            lead = { app.burrow.money.ui.components.IconCircle(painterResource(R.drawable.ic_bar_chart_duotone)) },
            trailing = { androidx.compose.material3.Icon(painterResource(R.drawable.ic_expand_more_regular), stringResource(R.string.show_chart), tint = c.primary) },
            onClick = onOpen,
        )
    }
}

/** The chart card (11): Last 14 days (or the picked dates), spent, the bars; tap a day for just that day (12). */
@Composable
private fun ActivityChartCard(chart: ActivityChart, onSelect: (Int?) -> Unit, onFold: () -> Unit) {
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
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                // Plain text, not a TextButton: the header row is 24 dp and a button's padding clipped it.
                // The 48 dp tap area still reaches past the row.
                if (selDay != null) androidx.compose.material3.Text(
                    stringResource(R.string.all_days), style = app.burrow.money.ui.theme.BurrowType.button, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, maxLines = 1,
                    modifier = Modifier.minimumInteractiveComponentSize().clip(app.burrow.money.ui.theme.Shapes.pill)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onSelect(null) }.padding(horizontal = Space.small),
                )
                else AverageLegend(stringResource(R.string.a_day, money.short(chart.perDay)))
                androidx.compose.material3.IconButton(onClick = onFold) {
                    androidx.compose.material3.Icon(painterResource(R.drawable.ic_expand_less_regular), stringResource(R.string.hide_chart), tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            }
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
