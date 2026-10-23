package app.burrow.money.ui.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.data.BudgetLine
import app.burrow.money.data.GoalLine
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.data.OccurrenceView
import app.burrow.money.ui.DateText
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.AddFab
import app.burrow.money.ui.components.AmountTrailing
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonGroup
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.Cookie
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.HeaderIcon
import app.burrow.money.ui.components.LinkButton
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.IconCircle
import androidx.compose.foundation.layout.Spacer
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.Note
import app.burrow.money.ui.components.NoteKind
import app.burrow.money.ui.components.RingPercent
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.TabScreen
import app.burrow.money.ui.components.Widget
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.threshold
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.shortName
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.categoryColors
import app.burrow.money.ui.theme.fadeThrough
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Plan (22, 23, 24): Coming up | Budgets | Goals. Coming up: bills and future-dated payments in one
 * list. Budgets: the centred header and category rings. Goals: the centred header and goal rings.
 */
@Composable
fun PlanScreen(
    vm: PlanViewModel,
    start: PlanSection,
    onAddBill: () -> Unit,
    onOpenBill: (Long) -> Unit,
    onSubscriptions: () -> Unit,
    onLoan: (Long?) -> Unit,
    onBudget: (Long?) -> Unit,
    onAddGoal: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onOpenTxn: (Long) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var section by rememberSaveable(start) { mutableStateOf(start) }
    TabScreen(
        title = stringResource(R.string.tab_plan),
        fab = when (section) {
            PlanSection.BILLS -> ({ AddFab(stringResource(R.string.add_bill), onAddBill) })
            PlanSection.GOALS -> ({ AddFab(stringResource(R.string.add_goal), onAddGoal) })
            PlanSection.BUDGETS -> null
        },
    ) { padding ->
        val s = state ?: return@TabScreen
        Column(Modifier.fillMaxSize()) {
            ButtonGroup(
                PlanSection.entries, section,
                { stringResource(when (it) { PlanSection.BILLS -> R.string.coming_up; PlanSection.BUDGETS -> R.string.budgets; PlanSection.GOALS -> R.string.goals }) },
                { section = it },
                Modifier.padding(horizontal = Space.screen).padding(bottom = Space.block),
            )
            AnimatedContent(section, transitionSpec = { fadeThrough() }, label = "plan") { sec ->
                when (sec) {
                    PlanSection.BILLS -> BillsSection(s.bills, vm, padding, onAddBill, onOpenBill, onSubscriptions, onLoan, onOpenTxn)
                    PlanSection.BUDGETS -> BudgetsSection(s.budgets, vm, padding, onBudget)
                    PlanSection.GOALS -> GoalsSection(s.goals, padding, onAddGoal, onOpenGoal)
                }
            }
        }
    }
}

/**
 * Coming up (22, 113): bills and future-dated payments in one list, grouped Overdue (red) · This week
 * · This month · Later, each with its total. Swipe right: Paid (bill) / Add now (one-off); swipe
 * left: Skip / Delete; Undo after each. "Every month" sums Subscriptions and EMIs at the bottom.
 */
@Composable
private fun BillsSection(
    s: BillsState,
    vm: PlanViewModel,
    padding: PaddingValues,
    onAddBill: () -> Unit,
    onOpenBill: (Long) -> Unit,
    onSubscriptions: () -> Unit,
    onLoan: (Long?) -> Unit,
    onOpenTxn: (Long) -> Unit,
) {
    val money = LocalMoney.current
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    if (s.isEmpty) {
        EmptyState(
            shape = { Cookie(painterResource(R.drawable.ic_event_upcoming_regular)) },
            title = stringResource(R.string.nothing_coming_up),
            line = stringResource(R.string.nothing_coming_up_line),
            button = { BurrowButton(stringResource(R.string.add_bill), onAddBill, kind = ButtonKind.TONAL) },
        )
        return
    }
    val right: (ComingItem) -> Unit = { item ->
        item.occurrence?.let { o -> vm.markPaid(o.occurrence.id) { undo -> snackbar.show(context.getString(R.string.marked_paid), undo) } }
        item.txn?.let { t -> vm.addNow(t.txn.id) { undo -> snackbar.show(context.getString(R.string.added_now), undo) } }
    }
    val left: (ComingItem) -> Unit = { item ->
        item.occurrence?.let { o -> vm.skip(o.occurrence.id) { undo -> snackbar.show(context.getString(R.string.skipped), undo) } }
        item.txn?.let { t -> vm.deleteOnce(t.txn.id) { undo -> snackbar.show(context.getString(R.string.txn_deleted), undo) } }
    }
    val open: (ComingItem) -> Unit = { item -> item.occurrence?.let { onOpenBill(it.occurrence.billId) } ?: item.txn?.let { onOpenTxn(it.txn.id) } }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    app.burrow.money.ui.components.KeepTopVisible(list, s.items.filter { it.group == ComingGroup.OVERDUE }.map { it.key })
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = padding) {
        ComingGroup.entries.forEach { g ->
            val items = s.items.filter { it.group == g }
            if (items.isEmpty()) return@forEach
            item(key = "h-$g", contentType = "header") {
                val out = items.filter { !it.income }.sumOf { it.amount }
                val inn = items.filter { it.income }.sumOf { it.amount }
                val total = when {
                    inn > 0 && out > 0 -> stringResource(R.string.coming_total_in, money.plain(out), money.short(inn))
                    inn > 0 -> "+" + money.plain(inn)
                    else -> money.plain(out)
                }
                SectionHeader(
                    stringResource(when (g) { ComingGroup.OVERDUE -> R.string.overdue; ComingGroup.THIS_WEEK -> R.string.this_week; ComingGroup.THIS_MONTH -> R.string.this_month; ComingGroup.LATER -> R.string.later }),
                    meta = total, titleColor = if (g == ComingGroup.OVERDUE) c.error else c.onSurface,
                )
            }
            item(key = "g-$g", contentType = "group") {
                ListGroup { items.forEachIndexed { i, it -> androidx.compose.runtime.key(it.key) { ComingRow(it, i, items.size, open, right, left) } } }
            }
        }
        item(key = "hint", contentType = "hint") {
            Text(stringResource(R.string.swipe_hint), style = BurrowType.body, color = c.onSurfaceVariant, modifier = Modifier.padding(start = Space.headingInset, top = Space.block))
        }
        if (s.subsCount > 0 || s.emiMonthly > 0) {
            item(key = "h-every", contentType = "header") { SectionHeader(stringResource(R.string.every_month)) }
            item(key = "g-every", contentType = "group") {
                val rows = buildList<@Composable (Int, Int) -> Unit> {
                    if (s.subsCount > 0) add { i, n ->
                        ListRow(
                            stringResource(R.string.subscriptions), i, n,
                            supporting = money.plain(money.whole(s.subsMonthly)) + " " + pluralStringResource(R.plurals.subs_line, s.subsCount, s.subsCount),
                            lead = { IconCircle(painterResource(R.drawable.ic_autorenew_duotone)) }, onClick = onSubscriptions,
                        )
                    }
                    if (s.emiMonthly > 0) add { i, n ->
                        ListRow(
                            stringResource(R.string.emis), i, n,
                            supporting = if (s.emisTotal > 0) stringResource(R.string.emis_month_paid, money.plain(s.emiMonthly), s.emisPaid, s.emisTotal) else stringResource(R.string.a_month, money.plain(s.emiMonthly)),
                            lead = { IconCircle(painterResource(R.drawable.ic_real_estate_agent_duotone)) }, onClick = { onLoan(s.loanAccountId) },
                        )
                    }
                }
                ListGroup { rows.forEachIndexed { i, r -> r(i, rows.size) } }
            }
        }
        item(key = "end") { Spacer(Modifier.height(Size.fabClearance)) }
    }
}

/** One Coming up row (22): circle · name · ↻/📅 + "in 5 days · ICICI" · amount over the date. Swipes. */
@Composable
private fun ComingRow(item: ComingItem, index: Int, count: Int, onOpen: (ComingItem) -> Unit, right: (ComingItem) -> Unit, left: (ComingItem) -> Unit) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val today = remember { LocalDate.now() }
    val late = item.group == ComingGroup.OVERDUE
    val rightLabel = stringResource(if (item.repeats) R.string.mark_paid else R.string.add_now)
    val leftLabel = stringResource(if (item.repeats) R.string.skip else R.string.delete)
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> { haptics.confirm(); right(item) }
            SwipeToDismissBoxValue.EndToStart -> { haptics.threshold(); left(item) }
            else -> Unit
        }
        false // the row stays; the data moves it
    })
    SwipeToDismissBox(
        swipe,
        backgroundContent = {
            val toRight = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            if (swipe.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Row(
                    Modifier.fillMaxSize().clip(Shapes.grouped(index, count)).background(if (toRight) c.primaryContainer else c.surfaceContainerHigh).padding(horizontal = Space.sheet),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (toRight) Arrangement.Start else Arrangement.End,
                ) {
                    Text(if (toRight) rightLabel else leftLabel, style = BurrowType.button, color = if (toRight) c.onPrimaryContainer else c.onSurfaceVariant)
                }
            }
        },
    ) {
        val whenText = if (late) stringResource(R.string.was_due_cap, dates.shortDay(item.date)) else dates.due(item.date, today)
        ListRow(
            item.name, index, count,
            supporting = stringResource(R.string.dot_join, whenText, shortName(item.accountName)),
            supportingColor = if (late) c.error else c.onSurfaceVariant,
            supportingIcon = painterResource(if (item.repeats) R.drawable.ic_autorenew_regular else R.drawable.ic_event_upcoming_regular),
            modifier = Modifier.semantics {
                customActions = listOf(CustomAccessibilityAction(rightLabel) { right(item); true }, CustomAccessibilityAction(leftLabel) { left(item); true })
            },
            lead = { CategoryCircle(item.icon, item.hue) },
            trailing = {
                AmountTrailing(
                    (if (item.income) "+" else "") + money.plain(item.amount),
                    if (late) stringResource(R.string.late) else dates.shortDay(item.date),
                    if (late) c.error else c.onSurfaceVariant,
                )
            },
            onClick = { onOpen(item) },
        )
    }
}

@Composable
fun kindLabel(kind: String): String = stringResource(
    when (kind) {
        "SUBSCRIPTION" -> R.string.subscription
        "EMI" -> R.string.emi
        "CARD" -> R.string.card_bill
        else -> R.string.bill
    },
)

@Composable
private fun BudgetsSection(s: BudgetsState, vm: PlanViewModel, padding: PaddingValues, onBudget: (Long?) -> Unit) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    var edit by remember { mutableStateOf<BudgetEdit?>(null) }
    var pickCategory by remember { mutableStateOf(false) }
    val month = dates.month(s.period.start)
    val o = s.overall
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "header") {
            if (o == null) {
                EmptyState(
                    shape = { Cookie(painterResource(R.drawable.ic_donut_large_regular)) },
                    title = stringResource(R.string.no_budget),
                    line = stringResource(R.string.no_budget_line),
                    button = { BurrowButton(stringResource(R.string.set_budget), { edit = BudgetEdit(null, month, money.digitsUnit() * 30_000) }, kind = ButtonKind.TONAL) },
                )
            } else {
                Column(Modifier.clickable { onBudget(null) }) {
                    CenterHeader(
                        icon = { HeaderIcon(painterResource(R.drawable.ic_donut_large_regular)) },
                        title = stringResource(R.string.month_budget, month),
                        value = if (o.over) stringResource(R.string.amount_over, money.plain(-o.left)) else stringResource(R.string.amount_left, money.plain(o.left)),
                        line = stringResource(R.string.budget_line, money.plain(o.amount), money.plain(s.perDay)),
                        progress = o.fraction,
                        bad = o.over,
                    )
                }
            }
        }
        if (o != null) {
            item(key = "cats-h") { SectionHeader(stringResource(R.string.categories), trailing = { LinkButton(stringResource(R.string.add), { pickCategory = true }) }) }
            item(key = "cats") { BudgetRows(s.lines) { onBudget(it) } }
        }
    }
    edit?.let { e ->
        BudgetEditSheet(e, onDismiss = { edit = null }) { amount, carry ->
            vm.setBudget(e.categoryId, amount, carry) { undo -> snackbar.show(context.getString(R.string.budget_saved), undo) }
            edit = null
        }
    }
    if (pickCategory) {
        val used = s.lines.mapNotNull { it.category?.id }.toSet()
        CategorySheet(s.categories.filter { it.id !in used }, emptyMap(), null, { cat ->
            pickCategory = false
            edit = BudgetEdit(cat.id, cat.name, money.digitsUnit() * 5_000)
        }, { pickCategory = false })
    }
}

/** Category rows with rings (23, 30): "₹710 over" in red or "₹1,292 left". */
@Composable
fun BudgetRows(lines: List<BudgetLine>, onOpen: (Long) -> Unit) {
    val money = LocalMoney.current
    val c = MaterialTheme.colorScheme
    ListGroup {
        lines.forEachIndexed { i, l ->
            val cat = l.category ?: return@forEachIndexed
            val colors = categoryColors(cat.colorHue)
            ListRow(
                cat.name, i, lines.size,
                supporting = if (l.over) stringResource(R.string.amount_over, money.plain(-l.left)) else stringResource(R.string.amount_left, money.plain(l.left)),
                supportingColor = if (l.over) c.error else c.onSurfaceVariant,
                lead = { CategoryCircle(cat.icon, cat.colorHue) },
                trailing = { RingPercent(l.fraction, colors.solid, colors.track, over = l.over) },
                onClick = { onOpen(cat.id) },
            )
        }
    }
}

/** What the budget edit sheet is for: the overall budget (null) or one category. */
data class BudgetEdit(val categoryId: Long?, val name: String, val amount: Long, val carryOver: Boolean = false)

/** The budget edit sheet (32): − amount +, chips, carry over, Cancel / Save. */
@Composable
fun BudgetEditSheet(e: BudgetEdit, onDismiss: () -> Unit, onSave: (Long, Boolean) -> Unit) {
    val money = LocalMoney.current
    val unit = money.digitsUnit()
    var amount by remember(e) { mutableLongStateOf(e.amount) }
    var carry by remember(e) { mutableStateOf(e.carryOver) }
    val step = (if (e.categoryId == null) 1_000 else 500) * unit
    val base = (amount / step).coerceAtLeast(1) * step
    app.burrow.money.ui.components.AmountStepperSheet(
        title = if (e.categoryId == null) stringResource(R.string.month_budget, e.name) else e.name,
        line = stringResource(R.string.monthly_budget),
        amount = amount,
        onAmount = { amount = it },
        step = step,
        chips = listOf(base - 5 * step, base, base + 5 * step, base + 10 * step).filter { it > 0 }.distinct(),
        confirm = stringResource(R.string.save),
        onConfirm = { onSave(amount, carry) },
        onDismiss = onDismiss,
        extra = {
            ListGroup {
                ListRow(
                    stringResource(R.string.carry_over), 0, 1, supporting = stringResource(R.string.carry_over_line),
                    lead = { app.burrow.money.ui.components.IconCircle(painterResource(R.drawable.ic_redo_duotone)) },
                    trailing = { app.burrow.money.ui.components.BurrowSwitch(carry, { carry = it }) },
                )
            }
        },
    )
}

/** One unit of the main currency in minor units (₹1 = 100). */
fun app.burrow.money.domain.MoneyFormat.digitsUnit(): Long {
    var u = 1L
    repeat(digits) { u *= 10 }
    return u
}

@Composable
private fun GoalsSection(s: GoalsState, padding: PaddingValues, onAddGoal: () -> Unit, onOpenGoal: (Long) -> Unit) {
    val money = LocalMoney.current
    if (s.goals.isEmpty()) {
        EmptyState(
            shape = { Cookie(painterResource(R.drawable.ic_flag_regular)) },
            title = stringResource(R.string.no_goals),
            line = stringResource(R.string.no_goals_line),
            button = { BurrowButton(stringResource(R.string.add_goal), onAddGoal, kind = ButtonKind.TONAL) },
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "header") {
            CenterHeader(
                icon = { HeaderIcon(painterResource(R.drawable.ic_flag_regular)) },
                title = stringResource(R.string.saved_for_goals),
                value = money.plain(s.saved),
                line = pluralStringResource(R.plurals.goals_line, s.goals.size, money.plain(s.target), s.goals.size),
                progress = if (s.target > 0) s.saved.toFloat() / s.target else 0f,
            )
        }
        item(key = "gap") { androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Space.block)) }
        item(key = "goals") { GoalRows(s.goals, onOpenGoal) }
    }
}

@Composable
private fun GoalRows(goals: List<GoalLine>, onOpen: (Long) -> Unit) {
    val money = LocalMoney.current
    ListGroup {
        goals.forEachIndexed { i, g ->
            val colors = categoryColors(g.goal.colorHue)
            ListRow(
                g.goal.name, i, goals.size,
                supporting = if (g.reached) stringResource(R.string.reached) else stringResource(R.string.of_amount, money.plain(g.saved), money.short(g.goal.target)),
                lead = { CategoryCircle(g.goal.icon, g.goal.colorHue) },
                trailing = { RingPercent(g.fraction, colors.solid, colors.track, done = g.reached) },
                onClick = { onOpen(g.goal.id) },
            )
        }
    }
}
