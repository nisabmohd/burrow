package app.burrow.money.ui.filters

import androidx.compose.foundation.indication
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.burrow.money.R
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.CategoryEntity
import app.burrow.money.domain.DayRange
import app.burrow.money.ui.DateText
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.ButtonGroup
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.Chip
import app.burrow.money.ui.components.LinkButton
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.duotoneIcon
import app.burrow.money.ui.components.tick
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The one Filter sheet (13, 40): Type (Activity only), Accounts and Categories as wrapping chips,
 * so it fits one screen. Reset / "Show N transactions" with N counted live.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    start: Filters,
    accounts: List<AccountWithBalance>,
    categories: List<CategoryEntity>,
    showType: Boolean,
    count: (Filters) -> Int,
    onApply: (Filters) -> Unit,
    onDismiss: () -> Unit,
) {
    var f by remember { mutableStateOf(start) }
    val n = count(f)
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.filter), onDismiss)
        SheetBody {
            if (showType) {
                Label(R.string.type)
                ButtonGroup(
                    TypeFilter.entries, f.type,
                    { stringResource(when (it) { TypeFilter.ALL -> R.string.all; TypeFilter.SPENT -> R.string.spent; TypeFilter.INCOME -> R.string.type_income; TypeFilter.TRANSFERS -> R.string.transfers }) },
                    { f = f.copy(type = it) },
                )
            }
            Label(R.string.accounts)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                accounts.forEach { a ->
                    val on = a.account.id in f.accounts
                    Chip(a.account.name, on, { f = f.copy(accounts = if (on) f.accounts - a.account.id else f.accounts + a.account.id) }, icon = painterResource(duotoneIcon(a.account.icon)))
                }
            }
            Label(R.string.categories)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                categories.forEach { c ->
                    val on = c.id in f.categories
                    Chip(c.name, on, { f = f.copy(categories = if (on) f.categories - c.id else f.categories + c.id) }, icon = painterResource(duotoneIcon(c.icon)))
                }
            }
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.reset), { f = Filters(range = f.range) }, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
            BurrowButton(pluralStringResource(R.plurals.show_n, n, n), { onApply(f) }, Modifier.weight(1.4f), height = Size.buttonBottom)
        }
    }
}

@Composable
private fun Label(text: Int) = Text(stringResource(text), style = BurrowType.button, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = Space.grid, top = Space.small))

/** "1 – 19 Oct", "19 Oct", "28 Sep – 4 Oct". */
fun DateText.range(r: DayRange): String = when {
    r.start == r.end -> shortDay(r.start)
    r.start.month == r.end.month && r.start.year == r.end.year -> "${r.start.dayOfMonth} – ${shortDay(r.end)}"
    else -> "${shortDay(r.start)} – ${shortDay(r.end)}"
}

/**
 * The one Dates sheet (14): presets, a month calendar where the first tap is the start and the
 * second the end (days after today can't be picked), All dates / "Show 1 – 19 Oct".
 */
@Composable
fun DatesSheet(start: DayRange?, onApply: (DayRange?) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val dates = rememberDateText()
    var from by remember { mutableStateOf(start?.start) }
    var to by remember { mutableStateOf(start?.end) }
    var month by remember { mutableStateOf(YearMonth.from(start?.end ?: today)) }
    val haptics = LocalHapticFeedback.current
    val picked = from?.let { DayRange(it, to ?: it) }
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.dates), onDismiss, stringResource(if (from == null || to != null) R.string.pick_start else R.string.pick_end))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            listOf(
                R.string.this_month to DayRange.thisMonth(today),
                R.string.last_month to DayRange.lastMonth(today),
                R.string.last_30_days to DayRange.lastDays(30, today),
                R.string.this_year to DayRange.thisYear(today),
            ).forEach { (label, r) ->
                Chip(stringResource(label), picked == r, { from = r.start; to = r.end; month = YearMonth.from(r.end) })
            }
        }
        SheetBody {
            MonthHeader(month, { month = month.minusMonths(1) }, { if (month.isBefore(YearMonth.from(today))) month = month.plusMonths(1) })
            MonthCalendar(month, today, picked) { d ->
                haptics.tick()
                when {
                    from == null || to != null -> { from = d; to = null }
                    d.isBefore(from) -> { to = from; from = d }
                    else -> to = d
                }
            }
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.all_dates), { onApply(null) }, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
            BurrowButton(
                picked?.let { stringResource(R.string.show_range, dates.range(it)) } ?: stringResource(R.string.pick_dates),
                { picked?.let(onApply) }, Modifier.weight(1.3f), if (picked != null) ButtonKind.FILLED else ButtonKind.OFF, height = Size.buttonBottom,
            )
        }
    }
}

/** "October 2026" with ‹ › : the line above every [MonthCalendar]. */
@Composable
fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    val dates = rememberDateText()
    Row(Modifier.fillMaxWidth().padding(top = Space.small), verticalAlignment = Alignment.CenterVertically) {
        Text(dates.monthYear(month.atDay(1)), style = BurrowType.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(start = Space.small))
        TonalIconButton(painterResource(R.drawable.ic_chevron_left_regular), stringResource(R.string.previous_month), onPrevious)
        Box(Modifier.size(Space.small))
        TonalIconButton(painterResource(R.drawable.ic_chevron_right_regular), stringResource(R.string.next_month), onNext)
    }
}

/**
 * A month of 38 dp day circles in seven equal columns (never wider than the sheet), weeks starting
 * on the chosen first day. The picked range is a tonal band with primary ends; today is ringed; days
 * that aren't [selectable] (by default, after today) are muted and can't be tapped.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    picked: DayRange?,
    selectable: (LocalDate) -> Boolean = { !it.isAfter(today) },
    onPick: (LocalDate) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val first = app.burrow.money.ui.LocalDatePrefs.current.weekStart
    val days = (0 until 7).map { first.plus(it.toLong()) }
    val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.group)) {
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = BurrowType.label, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (d != null) {
                            val future = !selectable(d)
                            val inRange = picked != null && d in picked
                            val end = picked != null && (d == picked.start || d == picked.end)
                            val label = d.dayOfMonth.toString()
                            Box(
                                Modifier.size(Size.heatmapDay).clip(Shapes.pill)
                                    .background(if (end) c.primary else if (inRange) c.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                    .then(if (d == today && !end) Modifier.border(Space.group / 2, c.primary, Shapes.pill) else Modifier)
                                    .then(if (!future) Modifier.clickable(role = Role.Button) { onPick(d) } else Modifier)
                                    .semantics { selected = inRange; contentDescription = label },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label, style = BurrowType.body,
                                    color = when { end -> c.onPrimary; future -> c.onSurfaceVariant.copy(alpha = 0.5f); else -> c.onSurface },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Active filters under the search bar (15, 41): the dates chip, then one chip per filter, each
 * with ✕, and Clear all.
 */
@Composable
fun ActiveFilterChips(f: Filters, accounts: List<AccountWithBalance>, categories: List<CategoryEntity>, onChange: (Filters) -> Unit, modifier: Modifier = Modifier) {
    if (f.isEmpty) return
    val dates = rememberDateText()
    val close = painterResource(R.drawable.ic_close_regular)
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.small), verticalAlignment = Alignment.CenterVertically) {
        f.range?.let { r -> RemovableChip(dates.range(r), close) { onChange(f.copy(range = null)) } }
        if (f.type != TypeFilter.ALL) {
            RemovableChip(stringResource(when (f.type) { TypeFilter.SPENT -> R.string.spent; TypeFilter.INCOME -> R.string.type_income; else -> R.string.transfers }), close) { onChange(f.copy(type = TypeFilter.ALL)) }
        }
        f.accounts.forEach { id -> accounts.firstOrNull { it.account.id == id }?.let { a -> RemovableChip(a.account.name, close) { onChange(f.copy(accounts = f.accounts - id)) } } }
        f.categories.forEach { id -> categories.firstOrNull { it.id == id }?.let { c -> RemovableChip(c.name, close) { onChange(f.copy(categories = f.categories - id)) } } }
        LinkButton(stringResource(R.string.clear_all), { onChange(Filters()) })
    }
}

@Composable
private fun RemovableChip(label: String, close: androidx.compose.ui.graphics.painter.Painter, onRemove: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val remove = stringResource(R.string.remove_filter, label)
    // 48 dp tap target around the 36 dp pill (it was 36 dp to tap); the ripple stays on the pill.
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    androidx.compose.foundation.layout.Box(
        Modifier.minimumInteractiveComponentSize()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = remove, onClick = onRemove)
            .semantics(mergeDescendants = true) { contentDescription = remove },
        contentAlignment = Alignment.Center,
    ) {
    Row(
        Modifier.clip(Shapes.pill).background(c.secondaryContainer).indication(interaction, androidx.compose.material3.ripple())
            .height(Size.chip).padding(start = Space.card, end = Space.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        Text(label, style = BurrowType.button, color = c.onSecondaryContainer, maxLines = 1)
        androidx.compose.material3.Icon(close, null, tint = c.onSecondaryContainer, modifier = Modifier.size(Size.iconSmall))
    }
    }
}

