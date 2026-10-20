package app.burrow.money.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.burrow.money.R
import app.burrow.money.domain.keptPercent
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.Bar
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.DonutChart
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.PillBarChart
import app.burrow.money.ui.components.RingPercent
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.Sparkline
import app.burrow.money.ui.components.SplitBar
import app.burrow.money.ui.components.Widget
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.categoryColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** One widget slot on Today: half ones pair up two to a row; wide ones take a row. */
private class Slot(val key: String, val wide: Boolean, val content: @Composable (Modifier, Boolean) -> Unit)

/**
 * Today's widgets (05) in the order This week, Next bill, Net worth, Top category, Goal, Money in
 * and out. Half widgets are 184 dp and pair up; one left alone switches to the wide layout.
 */
@Composable
fun TodayWidgets(
    w: Widgets,
    shown: Set<String>,
    order: List<String>,
    hero: HeroState,
    onWeek: () -> Unit,
    onBill: (Long) -> Unit,
    onMarkPaid: (Long) -> Unit,
    onNetWorth: () -> Unit,
    onCategory: (Long) -> Unit,
    onGoal: (Long) -> Unit,
) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val c = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val slots = buildList {
        add(Slot("week", false) { m, wide ->
            Widget(
                stringResource(R.string.this_week), painterResource(R.drawable.ic_bar_chart_duotone), money.plain(w.week.sumOf { it.second }),
                stringResource(R.string.spent_in_7_days), m, wide, onClick = onWeek,
                visual = {
                    PillBarChart(
                        bars = w.week.map { (d, v) -> Bar(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), v) },
                        selected = w.week.lastIndex, onSelect = null, height = Size.miniChart, showAverage = false,
                        modifier = if (wide) Modifier.fillMaxWidth(0.5f) else Modifier,
                    )
                },
            )
        })
        w.nextBill?.let { o ->
            add(Slot("bill", false) { m, wide ->
                val due = Instant.ofEpochMilli(o.occurrence.dueDate).atZone(ZoneId.systemDefault()).toLocalDate()
                Widget(
                    stringResource(R.string.next_bill), painterResource(R.drawable.ic_event_available_duotone), money.plain(o.amount),
                    stringResource(R.string.dot_join, o.name, if (due.isBefore(today)) stringResource(R.string.was_due, dates.shortDay(due)) else dates.due(due, today).replaceFirstChar { it.lowercase() }), m, wide, onClick = { onBill(o.occurrence.billId) },
                    visual = {
                        BurrowButton(stringResource(R.string.mark_paid), { onMarkPaid(o.occurrence.id) }, if (wide) Modifier else Modifier.fillMaxWidth(), ButtonKind.TONAL, painterResource(R.drawable.ic_check_regular))
                    },
                )
            })
        }
        add(Slot("networth", false) { m, wide ->
            Widget(
                stringResource(R.string.net_worth), painterResource(R.drawable.ic_account_balance_duotone), (if (w.netWorth < 0) app.burrow.money.domain.MoneyFormat.MINUS else "") + money.short(w.netWorth),
                stringResource(R.string.this_month_change, money.signed(w.netWorthChange)), m, wide, onClick = onNetWorth,
                visual = { Sparkline(w.netWorthLine, if (wide) Modifier.fillMaxWidth(0.5f) else Modifier, fill = true) },
            )
        })
        w.topCategory?.let { cat ->
            add(Slot("topcat", false) { m, wide ->
                Widget(
                    stringResource(R.string.top_category), painterResource(R.drawable.ic_pie_duotone), cat.name,
                    stringResource(R.string.of_spending, w.topShare), m, wide, onClick = { onCategory(cat.id) },
                    visual = {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                            DonutChart(w.categoryParts.map { (hue, v) -> categoryColors(hue).solid to v })
                            CategoryCircle(cat.icon, cat.colorHue)
                        }
                    },
                )
            })
        }
        w.goal?.let { g ->
            add(Slot("goal", true) { m, _ ->
                Widget(
                    stringResource(R.string.goal), painterResource(R.drawable.ic_flag_duotone), money.short(g.saved),
                    stringResource(R.string.goal_widget_line, money.short(g.goal.target), g.goal.name) +
                        (w.goalPerMonth?.let { "\n" + stringResource(R.string.a_month_to_finish, money.short(it)) } ?: ""),
                    m, wide = true, onClick = { onGoal(g.goal.id) },
                    visual = { RingPercent(g.fraction, c.primary, c.secondaryContainer, Size.ringWide) },
                )
            })
        }
        add(Slot("inout", true) { m, _ ->
            MoneyInOut(w, dates.month(hero.period.start), m)
        })
    }.filter { it.key in shown }.sortedBy { order.indexOf(it.key) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.block)) {
        var i = 0
        while (i < slots.size) {
            val a = slots[i]
            val b = slots.getOrNull(i + 1)
            if (!a.wide && b != null && !b.wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
                    a.content(Modifier.weight(1f), false)
                    b.content(Modifier.weight(1f), false)
                }
                i += 2
            } else {
                a.content(Modifier.fillMaxWidth(), true)
                i += 1
            }
        }
    }
}

/** Money in and out this month: came in, went out, kept; a split bar; what is still with you. */
@Composable
private fun MoneyInOut(w: Widgets, month: String, modifier: Modifier) {
    val money = LocalMoney.current
    val c = MaterialTheme.colorScheme
    Widget(
        stringResource(R.string.money_in_out, month), painterResource(R.drawable.ic_swap_vert_duotone), "", "", modifier, wide = true,
        visual = null,
        body = {
            Row(Modifier.fillMaxWidth().padding(top = Space.small)) {
                Figure(stringResource(R.string.came_in), money.short(w.cameIn), Modifier.weight(1f))
                Figure(stringResource(R.string.went_out), money.short(w.wentOut), Modifier.weight(1f))
                Figure(stringResource(R.string.kept), keptPercent(w.cameIn, w.wentOut)?.let { "$it%" } ?: "–", Modifier.weight(1f))
            }
            SplitBar(w.wentOut, w.cameIn.coerceAtLeast(w.wentOut), Modifier.padding(vertical = Space.block))
            Text(
                if (w.cameIn >= w.wentOut) stringResource(R.string.still_with_you, money.short(w.cameIn - w.wentOut)) else stringResource(R.string.more_out_than_in, money.short(w.wentOut - w.cameIn)),
                style = BurrowType.body, color = c.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier) {
        Text(label, style = BurrowType.label, color = c.onSurfaceVariant)
        Text(value, style = BurrowType.title, color = c.onSurface, maxLines = 1)
    }
}

/** Today's widgets: key, name, icon (Customise › Today, 80). */
val HOME_WIDGET_ROWS = mapOf(
    "week" to (R.string.this_week to R.drawable.ic_bar_chart_duotone),
    "bill" to (R.string.next_bill to R.drawable.ic_event_available_duotone),
    "networth" to (R.string.net_worth to R.drawable.ic_account_balance_duotone),
    "topcat" to (R.string.top_category to R.drawable.ic_pie_duotone),
    "inout" to (R.string.money_in_and_out to R.drawable.ic_swap_vert_duotone),
    "goal" to (R.string.goal to R.drawable.ic_flag_duotone),
)
