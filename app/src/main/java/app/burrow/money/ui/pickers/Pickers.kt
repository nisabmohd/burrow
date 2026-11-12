package app.burrow.money.ui.pickers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import app.burrow.money.ui.components.ChipRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.indication
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.LaunchedEffect
import app.burrow.money.R
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.CategoryEntity
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.duotoneIcon
import app.burrow.money.ui.components.tick
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import java.time.LocalDate

/*
 * The pickers every form shares (docs/DESIGN.md rule 8: every picker is a bottom sheet).
 * Each is a BurrowSheet: header with title, one line and close; rows; selected row tinted with a check.
 */

/** The 40 dp circle of a category, in its own colours. */
@Composable
fun CategoryCircle(icon: String?, hue: Float?, size: androidx.compose.ui.unit.Dp = Size.iconCircle) =
    IconCircle(painterResource(duotoneIcon(icon)), hue = hue, size = size)

/** Opens New category from any category sheet (167); null hides the row (previews, tests). */
val LocalNewCategory = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Category (19, 167): every category with what was spent on it this month. When both kinds are
 * listed, an Expense group then an Income group; New category last. [spent] null = the screen has no
 * spending to show (bills, budgets, review, import): no line under the rows, rather than a wrong "nothing".
 */
@Composable
fun CategorySheet(
    categories: List<CategoryEntity>,
    spent: Map<Long, Long>?,
    selectedId: Long?,
    onPick: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    val haptics = LocalHapticFeedback.current
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val shown = if (query.isBlank()) categories else categories.filter { it.name.contains(query.trim(), ignoreCase = true) }
    BurrowSheet(onDismiss) {
        val newCategory = LocalNewCategory.current
        // No match (185): the header keeps only its title, so the empty state has the room.
        SheetHeader(stringResource(R.string.category), onDismiss, if (spent != null && shown.isNotEmpty()) stringResource(R.string.category_sheet_line) else null)
        // Pinned above the list, so it stays while the categories scroll under it.
        if (categories.size > SEARCH_FROM) {
            PillTextField(
                query, { query = it }, stringResource(R.string.search_categories),
                Modifier.padding(horizontal = app.burrow.money.ui.theme.Space.screen).padding(bottom = app.burrow.money.ui.theme.Space.block),
                leading = painterResource(R.drawable.ic_search_regular), clearLabel = stringResource(R.string.clear),
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
            )
        }
        SheetBody {
            // No match: the same empty state as Activity's search (Ghost, "No results for …", Clear search).
            if (shown.isEmpty()) {
                app.burrow.money.ui.components.EmptyState(
                    shape = { app.burrow.money.ui.components.Ghost(painterResource(R.drawable.ic_search_off_regular)) },
                    title = stringResource(R.string.no_results, query.trim()),
                    line = stringResource(R.string.no_category_match),
                    // Clear search (tonal) · New category (filled), as 185.
                    button = {
                        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(app.burrow.money.ui.theme.Space.inline)) {
                            BurrowButton(stringResource(R.string.clear_search), { query = "" }, kind = app.burrow.money.ui.components.ButtonKind.TONAL)
                            newCategory?.let { onNew -> BurrowButton(stringResource(R.string.new_category), { onDismiss(); onNew() }) }
                        }
                    },
                )
            }
            // Both kinds in one list: Expense first, then Income, each under its own header.
            val groups = shown.groupBy { it.kind == app.burrow.money.data.CategoryKind.INCOME }.toSortedMap()
            groups.forEach { (income, cats) ->
                if (groups.size > 1) app.burrow.money.ui.components.SectionHeader(stringResource(if (income) R.string.type_income else R.string.type_expense))
                ListGroup {
                    cats.forEachIndexed { i, cat ->
                        val total = spent?.get(cat.id) ?: 0L
                        app.burrow.money.ui.components.PickRow(
                            cat.name, i, cats.size, cat.id == selectedId, { onPick(cat) },
                            supporting = when {
                                spent == null -> null
                                total > 0 -> stringResource(R.string.spent_this_month, money.plain(total))
                                else -> stringResource(R.string.nothing_this_month)
                            },
                            lead = { CategoryCircle(cat.icon, cat.colorHue) }, ring = false,
                        )
                    }
                }
            }
            if (shown.isNotEmpty()) newCategory?.let { onNew ->
                ListGroup {
                    ListRow(
                        stringResource(R.string.new_category), 0, 1, supporting = stringResource(R.string.new_category_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_add_regular)) },
                        onClick = { onDismiss(); onNew() },
                    )
                }
            }
        }
    }
}

/** The category sheet shows its search field once there are more categories than this. */
private const val SEARCH_FROM = 8

/** Account (20): accounts with their balance and kind. */
@Composable
fun AccountSheet(
    accounts: List<AccountWithBalance>,
    selectedId: Long?,
    onPick: (AccountWithBalance) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    val haptics = LocalHapticFeedback.current
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.account), onDismiss, stringResource(R.string.account_sheet_line))
        SheetBody {
            // No account to pick (only a card, say): say what to add instead of an empty sheet (QA 13 Nov).
            if (accounts.isEmpty()) app.burrow.money.ui.components.EmptyState(
                shape = { app.burrow.money.ui.components.Ghost(painterResource(R.drawable.ic_account_balance_regular)) },
                title = stringResource(R.string.no_accounts_to_pick),
                line = stringResource(R.string.no_accounts_to_pick_line),
                top = app.burrow.money.ui.theme.Space.block,
            )
            ListGroup {
                accounts.forEachIndexed { i, a ->
                    app.burrow.money.ui.components.PickRow(
                        a.account.name, i, accounts.size, a.account.id == selectedId, { onPick(a) },
                        supporting = stringResource(R.string.dot_join, money.balance(a.balance), accountTypeLabel(a.account.type)),
                        lead = { CategoryCircle(a.account.icon, a.account.colorHue) },
                    )
                }
            }
        }
    }
}

@Composable
fun accountTypeLabel(type: String): String = stringResource(
    when (type) {
        AccountType.CASH -> R.string.account_cash
        AccountType.WALLET -> R.string.account_wallet
        AccountType.CARD -> R.string.account_card
        AccountType.LOAN -> R.string.account_loan
        AccountType.SAVINGS -> R.string.account_savings
        else -> R.string.account_bank
    },
)

/** One line of text (payee, note): a field that opens focused, and Done. */
@Composable
fun TextSheet(title: String, value: String, placeholder: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val focus = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    val done = { haptics.confirm(); onDone(text.text.trim()) }
    BurrowSheet(onDismiss) {
        SheetHeader(title, onDismiss)
        SheetBody {
            PillTextFieldValue(text, { text = it }, placeholder, Modifier.focusRequester(focus), onDone = done)
        }
        SheetFooter { BurrowButton(stringResource(R.string.done), done, Modifier.fillMaxWidth(), height = Size.buttonBottom) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun PillTextFieldValue(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, placeholder: String, modifier: Modifier, onDone: () -> Unit) {
    // PillTextField works on String; keep the cursor at the end by re-wrapping.
    PillTextField(
        value = value.text,
        onValueChange = { onChange(TextFieldValue(it, TextRange(it.length))) },
        placeholder = placeholder,
        modifier = modifier,
        clearLabel = stringResource(R.string.clear),
        onDone = onDone,
    )
}

/**
 * The one date sheet (78, 176): an optional Yesterday · Today · Tomorrow row ([quick], New expense;
 * a chip picks the day, Done saves), a month calendar in equal columns, an optional Time row that opens
 * the time wheel ([time]), Done. Days on or before today (transaction dates), from today on with [future]
 * (goal dates), or any day with [anyDay] (a future one makes a transaction upcoming, and the line says
 * when it is added). [yearly] (164): the month name alone and "Next: 9 Jan 2027" (a yearly due date).
 */
@Composable
fun DateSheet(
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    future: Boolean = false,
    title: String? = null,
    anyDay: Boolean = false,
    quick: Boolean = false,
    yearly: Boolean = false,
    line: String? = null,
    time: java.time.LocalTime? = null,
    onTime: (java.time.LocalTime) -> Unit = {},
) {
    val today = remember { LocalDate.now() }
    var picked by remember { mutableStateOf(date) }
    var pickedTime by remember { mutableStateOf(time) }
    var wheel by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(java.time.YearMonth.from(date)) }
    val haptics = LocalHapticFeedback.current
    val dates = app.burrow.money.ui.rememberDateText()
    val selectable: (LocalDate) -> Boolean = { d -> anyDay || if (future) !d.isBefore(today) else !d.isAfter(today) }
    if (wheel && pickedTime != null) {
        app.burrow.money.ui.components.TimeWheelSheet(stringResource(R.string.time), line, pickedTime!!, { pickedTime = it; wheel = false }, { wheel = false })
        return
    }
    val header = when {
        yearly -> stringResource(R.string.next_on, dates.fullDay(app.burrow.money.domain.Repeat.Yearly(picked.monthValue, picked.dayOfMonth).nextOnOrAfter(today)))
        quick && picked.isAfter(today) -> stringResource(R.string.upcoming_added_on, dates.weekdayDay(picked))
        else -> line
    }
    BurrowSheet(onDismiss) {
        SheetHeader(title ?: stringResource(R.string.date), onDismiss, header)
        SheetBody {
            if (quick) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(app.burrow.money.ui.theme.Space.small, androidx.compose.ui.Alignment.CenterHorizontally),
                ) {
                    listOf(R.string.yesterday to today.minusDays(1), R.string.today to today, R.string.tomorrow to today.plusDays(1)).forEach { (label, day) ->
                        app.burrow.money.ui.components.Chip(stringResource(label), picked == day, {
                            haptics.tick(); picked = day; month = java.time.YearMonth.from(day)
                        })
                    }
                }
            }
            app.burrow.money.ui.filters.MonthHeader(
                month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) },
                title = if (yearly) dates.month(month.atDay(1)) else null,
            )
            app.burrow.money.ui.filters.MonthCalendar(month, today, app.burrow.money.domain.DayRange(picked, picked), selectable) { d ->
                haptics.tick(); picked = d
            }
            pickedTime?.let { t ->
                ListGroup {
                    ListRow(
                        dates.clock(t), 0, 1, supporting = stringResource(R.string.time),
                        lead = { IconCircle(painterResource(R.drawable.ic_schedule_duotone)) },
                        onClick = { wheel = true },
                    )
                }
            }
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.done), {
                haptics.confirm()
                pickedTime?.let { if (it != time) onTime(it) }
                onPick(picked)
            }, Modifier.fillMaxWidth(), height = Size.buttonBottom)
        }
    }
}

/** A category's short name in the grid: "Food & drinks" → "Food" (241; the prototype's `split(' &')`). */
fun gridLabel(name: String): String = name.substringBefore(" &").ifBlank { name }

/**
 * Pick a category from a grid (What was it for?, 241; a CheckCard's More): four equal columns of 56 dp category
 * circles with the short name under each. One tap picks. Tiles are 4 dp tall-padded, 6 dp circle → name, rows 12
 * apart. The press ripple is drawn on a layer behind, so a long name is never clipped by the tile's corners.
 */
@Composable
fun CategoryGrid(categories: List<CategoryEntity>, onPick: (CategoryEntity) -> Unit, modifier: Modifier = Modifier) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.block)) {
        categories.chunked(GRID_COLUMNS).forEach { row ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.group)) {
                row.forEach { cat ->
                    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        androidx.compose.foundation.layout.Spacer(
                            Modifier.matchParentSize().clip(app.burrow.money.ui.theme.Shapes.surface)
                                .indication(interaction, androidx.compose.material3.ripple()),
                        )
                        androidx.compose.foundation.layout.Column(
                            Modifier.fillMaxWidth()
                                .clickable(interactionSource = interaction, indication = null, role = androidx.compose.ui.semantics.Role.Button) { haptics.tick(); onPick(cat) }
                                .padding(vertical = Space.grid),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.tileLabel),
                        ) {
                            CategoryCircle(cat.icon, cat.colorHue, Size.categoryTile)
                            androidx.compose.material3.Text(
                                gridLabel(cat.name), style = app.burrow.money.ui.theme.BurrowType.label,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2,
                            )
                        }
                    }
                }
                repeat(GRID_COLUMNS - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val GRID_COLUMNS = 4

