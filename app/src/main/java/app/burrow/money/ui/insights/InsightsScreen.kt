package app.burrow.money.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.domain.Bucket
import app.burrow.money.domain.bucketFor
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.Bar
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CappedFontScale
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.ChartCard
import app.burrow.money.ui.components.Cookie
import app.burrow.money.ui.components.CumulativeLineChart
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.FilterHeaderButtons
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.PillBarChart
import app.burrow.money.ui.components.RingPercent
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.Sparkline
import app.burrow.money.ui.components.StackBar
import app.burrow.money.ui.components.TabScreen
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.Widget
import app.burrow.money.ui.components.heatColor
import app.burrow.money.ui.components.heatLevel
import app.burrow.money.ui.filters.ActiveFilterChips
import app.burrow.money.ui.filters.DatesSheet
import app.burrow.money.ui.filters.FilterSheet
import app.burrow.money.ui.filters.range
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.plan.DetailFrame
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.categoryColors
import app.burrow.money.ui.txn.TxnRow
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private enum class InsightsSheet { FILTER, DATES }

/**
 * Insights (36–41), everything on one page: the spend line against the period before, A day and
 * Biggest, By category, Patterns (busiest day, no-spend days, you kept, top payee), the month
 * heatmap (tap a day → Activity for that day). Empty range: the empty state with Change dates.
 */
@Composable
fun InsightsScreen(vm: InsightsViewModel, onCategory: (Long) -> Unit, onDayInActivity: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<InsightsSheet?>(null) }
    val money = LocalMoney.current
    val dates = rememberDateText()
    TabScreen(
        title = stringResource(R.string.tab_insights),
        actions = {
            FilterHeaderButtons(
                funnel = painterResource(R.drawable.ic_filter_list_regular), calendar = painterResource(R.drawable.ic_calendar_month_regular),
                filterLabel = stringResource(R.string.filter), datesLabel = stringResource(R.string.dates),
                filterCount = state?.filters?.count ?: 0, datesActive = state?.filters?.range != null,
                onFilter = { sheet = InsightsSheet.FILTER }, onDates = { sheet = InsightsSheet.DATES },
            )
        },
    ) { padding ->
        val s = state ?: return@TabScreen
        Column(Modifier.fillMaxSize()) {
            ActiveFilterChips(s.filters, s.accounts, s.categories, vm::setFilters, Modifier.padding(horizontal = Space.screen).padding(bottom = Space.small))
            if (s.isEmpty) {
                EmptyState(
                    shape = { Cookie(painterResource(R.drawable.ic_event_busy_regular)) },
                    title = stringResource(R.string.no_spending_in, dates.range(s.range)),
                    line = stringResource(R.string.no_spending_line),
                    button = { BurrowButton(stringResource(R.string.change_dates), { sheet = InsightsSheet.DATES }, kind = ButtonKind.TONAL) },
                )
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(Space.block)) {
                item(key = "line") { SpendCard(s) }
                item(key = "pair") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
                        Widget(
                            stringResource(R.string.a_day_label), painterResource(R.drawable.ic_today_duotone), money.plain(money.whole(s.perDay)), stringResource(R.string.on_average),
                            Modifier.weight(1f), visual = { Sparkline(s.daily) },
                        )
                        val big = s.biggest
                        Widget(
                            stringResource(R.string.biggest), painterResource(R.drawable.ic_arrow_upward_duotone), money.plain(big?.txn?.amount ?: 0),
                            big?.let { stringResource(R.string.dot_join, it.txn.payee ?: it.categoryName.orEmpty(), dates.shortDay(it.day())) }.orEmpty(),
                            Modifier.weight(1f),
                            visual = {
                                if (big != null) Row(verticalAlignment = Alignment.CenterVertically) {
                                    CategoryCircle(big.categoryIcon, big.categoryHue)
                                    Text(big.categoryName.orEmpty(), style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
                                }
                            },
                        )
                    }
                }
                item(key = "cats") { ByCategoryCard(s, onCategory) }
                item(key = "patterns-h") { SectionHeader(stringResource(R.string.patterns)) }
                item(key = "patterns") { Patterns(s) }
                item(key = "heat") { HeatmapCard(s) { d -> vm.showDayInActivity(d); onDayInActivity() } }
            }
        }
    }
    val s = state ?: return
    when (sheet) {
        InsightsSheet.FILTER -> FilterSheet(
            s.filters, s.accounts, s.categories.filter { it.kind == "EXPENSE" }, showType = false,
            count = { f -> s.byCategory.count { f.categories.isEmpty() || it.category.id in f.categories } },
            onApply = { vm.setFilters(it); sheet = null }, onDismiss = { sheet = null },
        )
        InsightsSheet.DATES -> DatesSheet(s.filters.range, onApply = { vm.setFilters(s.filters.copy(range = it)); sheet = null }, onDismiss = { sheet = null })
        null -> Unit
    }
}

/** Spent this period as a running total, with the period before dashed (36). */
@Composable
private fun SpendCard(s: InsightsState) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val c = MaterialTheme.colorScheme
    val diff = s.spent - s.previousSpent
    ChartCard(
        icon = painterResource(R.drawable.ic_payments_duotone),
        label = stringResource(R.string.spent_range, dates.range(s.range)),
        value = money.plain(s.spent),
        line = when {
            s.previousSpent == 0L -> null
            diff < 0 -> stringResource(R.string.less_than, money.plain(-diff), dates.range(s.previousRange))
            else -> stringResource(R.string.more_than, money.plain(diff), dates.range(s.previousRange))
        },
    ) {
        CumulativeLineChart(s.line, s.previousLine, Modifier.semantics { contentDescription = "${money.plain(s.spent)}" })
        Spacer(Modifier.height(Space.small))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(dates.shortDay(s.range.start), style = BurrowType.label, color = c.onSurfaceVariant)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                Text("— " + stringResource(R.string.this_period), style = BurrowType.label, color = c.onSurfaceVariant)
                Spacer(Modifier.size(Space.card))
                Text("··· " + stringResource(R.string.before), style = BurrowType.label, color = c.onSurfaceVariant)
            }
            Text(dates.shortDay(s.range.end), style = BurrowType.label, color = c.onSurfaceVariant)
        }
    }
}

/** By category (37): the stacked bar and a row per category with its share; a row opens Category insights. */
@Composable
private fun ByCategoryCard(s: InsightsState, onCategory: (Long) -> Unit) {
    val money = LocalMoney.current
    val c = MaterialTheme.colorScheme
    val parts = s.byCategory.map { categoryColors(it.category.colorHue).solid to it.total }
    ChartCard(
        icon = painterResource(R.drawable.ic_pie_duotone),
        label = stringResource(R.string.by_category),
        value = "",
        line = null,
        trailing = { Text(s.byCategory.size.toString(), style = BurrowType.label, color = c.onSurfaceVariant) },
    ) {
        StackBar(parts, Modifier.padding(bottom = Space.small))
        s.byCategory.forEach { share ->
            Row(
                Modifier.fillMaxWidth().clip(Shapes.pill).clickable(role = Role.Button) { onCategory(share.category.id) }.padding(vertical = Space.group),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.card),
            ) {
                CategoryCircle(share.category.icon, share.category.colorHue)
                Text(stringResource(R.string.share_line, share.category.name, share.percent), style = BurrowType.bodyLarge, color = c.onSurface, modifier = Modifier.weight(1f), maxLines = 1)
                Text(money.plain(share.total), style = BurrowType.titleSmall, color = c.onSurface)
            }
        }
    }
}

/** Patterns (38): busiest day, no-spend days, you kept, top payee. */
@Composable
private fun Patterns(s: InsightsState) {
    val money = LocalMoney.current
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Space.block)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
            val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            Widget(
                stringResource(R.string.busiest_day), painterResource(R.drawable.ic_calendar_view_month_duotone),
                s.busiest?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "–",
                stringResource(R.string.in_total, money.plain(s.busiestTotal)), Modifier.weight(1f),
                visual = {
                    val order = (0 until 7).map { first.plus(it.toLong()) }
                    PillBarChart(
                        bars = order.map { d -> Bar(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), s.weekdays[d.value - 1]) },
                        selected = order.indexOf(s.busiest), onSelect = null, height = Size.miniChart, showAverage = false,
                    )
                },
            )
            Widget(
                stringResource(R.string.no_spend_days), painterResource(R.drawable.ic_event_available_duotone), s.noSpend.toString(),
                pluralStringResource(R.plurals.of_days, s.days, s.days), Modifier.weight(1f),
                visual = { RingPercent(if (s.days > 0) s.noSpend.toFloat() / s.days else 0f, categoryColors(140f).solid, categoryColors(140f).track, Size.ringWidget) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
            Widget(
                stringResource(R.string.you_kept), painterResource(R.drawable.ic_savings_duotone), s.kept?.let { "$it%" } ?: "–",
                if (s.income > 0) stringResource(R.string.of_came_in, money.short(s.income)) else stringResource(R.string.no_income_yet), Modifier.weight(1f),
                visual = { RingPercent((s.kept ?: 0) / 100f, c.primary, c.secondaryContainer, Size.ringWidget) },
            )
            val top = s.topPayee
            Widget(
                stringResource(R.string.top_payee), painterResource(R.drawable.ic_storefront_duotone), top?.name ?: "–",
                top?.let { pluralStringResource(R.plurals.times, it.times, money.plain(it.total), it.times) }.orEmpty(), Modifier.weight(1f),
                visual = { Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) { top?.circles?.forEach { (icon, hue) -> CategoryCircle(icon, hue) } } },
            )
        }
    }
}

/** The month by day (39): the top day and the month's total, then a circle per day in 5 tones. */
@Composable
private fun HeatmapCard(s: InsightsState, onDay: (LocalDate) -> Unit) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val c = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val max = s.heat.values.maxOrNull() ?: 0
    val top = s.heat.maxByOrNull { it.value }
    ChartCard(
        icon = painterResource(R.drawable.ic_grid_view_duotone),
        label = stringResource(R.string.month_by_day, dates.month(s.heatMonth.atDay(1))),
        value = "",
        line = null,
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = Space.card)) {
            Column(Modifier.weight(1f)) {
                Text(top?.let { dates.shortDay(it.key) } ?: "–", style = BurrowType.title, color = c.onSurface)
                Text(stringResource(R.string.top_day, money.plain(top?.value ?: 0)), style = BurrowType.body, color = c.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(money.short(s.monthTotal), style = BurrowType.title, color = c.onSurface)
                Text(stringResource(R.string.spent_this_month_plain), style = BurrowType.body, color = c.onSurfaceVariant)
            }
        }
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val lead = (s.heatMonth.atDay(1).dayOfWeek.value - first.value + 7) % 7
        val cells = List(lead) { null } + (1..s.heatMonth.lengthOfMonth()).map { s.heatMonth.atDay(it) }
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                Text(first.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = BurrowType.label, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Space.small))
        Column(verticalArrangement = Arrangement.spacedBy(Space.group)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { i ->
                        val d = week.getOrNull(i)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (d != null) {
                                val v = s.heat[d] ?: 0
                                val level = heatLevel(v, max)
                                val future = d.isAfter(today)
                                val label = stringResource(R.string.day_spent, dates.shortDay(d), money.plain(v))
                                Box(
                                    Modifier.size(Size.heatmapDay).clip(Shapes.pill)
                                        .background(if (future) androidx.compose.ui.graphics.Color.Transparent else heatColor(level))
                                        .then(if (d == today) Modifier.border(Space.group / 2, c.primary, Shapes.pill) else Modifier)
                                        .then(if (!future) Modifier.clickable(role = Role.Button, onClickLabel = label) { onDay(d) } else Modifier)
                                        .semantics { contentDescription = label },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CappedFontScale {
                                        Text(
                                            d.dayOfMonth.toString(), style = BurrowType.label,
                                            color = when { future -> c.onSurfaceVariant.copy(alpha = 0.5f); level >= 3 -> c.onPrimary; else -> c.onSurface },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.card), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.less), style = BurrowType.label, color = c.onSurfaceVariant)
            (0..4).forEach { l -> Box(Modifier.padding(horizontal = Space.group / 2).size(Space.card).clip(Shapes.pill).background(heatColor(l))) }
            Text(stringResource(R.string.more_word), style = BurrowType.label, color = c.onSurfaceVariant)
        }
    }
}

/** Category insights (42): the category's total against before, by day, and its transactions. */
@Composable
fun CategoryInsightsScreen(vm: CategoryInsightsViewModel, onBack: () -> Unit, onOpenTxn: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val dates = rememberDateText()
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val today = remember { LocalDate.now() }
    DetailFrame(onBack) {
        val diff = s.total - s.previousTotal
        CenterHeader(
            icon = { CategoryCircle(s.category.icon, s.category.colorHue, Size.iconCircleBig) },
            title = s.category.name,
            value = money.plain(s.total),
            line = listOfNotNull(
                dates.range(s.range),
                if (s.previousTotal > 0) stringResource(if (diff < 0) R.string.less_than_before else R.string.more_than_before, money.plain(kotlin.math.abs(diff))) else null,
            ).joinToString(" · "),
        )
        val sel = selected ?: s.bars.indices.maxByOrNull { s.bars[it].second }
        val selBar = sel?.let { s.bars.getOrNull(it) }
        val day = bucketFor(s.range) == Bucket.DAY
        ChartCard(
            icon = painterResource(R.drawable.ic_bar_chart_duotone),
            label = stringResource(R.string.by_day),
            value = money.plain(selBar?.second ?: 0),
            line = selBar?.first?.let { dates.shortDay(it) },
            modifier = Modifier.padding(top = Space.block),
            trailing = { app.burrow.money.ui.components.AverageLegend(stringResource(R.string.avg, money.short(if (s.bars.isEmpty()) 0 else s.total / s.bars.size))) },
        ) {
            PillBarChart(
                bars = s.bars.map { (d, v) -> Bar(if (day) d.dayOfMonth.toString() else dates.shortDay(d), v) },
                selected = sel ?: 0, onSelect = { selected = it },
            )
        }
        s.txns.groupBy { it.day() }.forEach { (d, list) ->
            SectionHeader(dates.day(d, today), meta = money.signed(-list.sumOf { it.txn.amount }))
            ListGroup { list.forEachIndexed { i, t -> TxnRow(t, i, list.size, dates, today, { onOpenTxn(t.txn.id) }) } }
        }
    }
}
