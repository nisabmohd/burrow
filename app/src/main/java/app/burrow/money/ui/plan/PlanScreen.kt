package app.burrow.money.ui.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
 * Plan (22, 23, 24): Bills | Budgets | Goals. Bills: overdue notes first, the Next up card,
 * Subscriptions and EMIs widgets, This week and Later (swipe right Paid, left Skip). Budgets: the
 * centred header and category rings. Goals: the centred header and goal rings.
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
                { stringResource(when (it) { PlanSection.BILLS -> R.string.bills; PlanSection.BUDGETS -> R.string.budgets; PlanSection.GOALS -> R.string.goals }) },
                { section = it },
                Modifier.padding(horizontal = Space.screen).padding(bottom = Space.block),
            )
            AnimatedContent(section, transitionSpec = { fadeThrough() }, label = "plan") { sec ->
                when (sec) {
                    PlanSection.BILLS -> BillsSection(s.bills, vm, padding, onAddBill, onOpenBill, onSubscriptions, onLoan)
                    PlanSection.BUDGETS -> BudgetsSection(s.budgets, vm, padding, onBudget)
                    PlanSection.GOALS -> GoalsSection(s.goals, padding, onAddGoal, onOpenGoal)
                }
            }
        }
    }
}

private fun OccurrenceView.date(): LocalDate = Instant.ofEpochMilli(occurrence.dueDate).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun BillsSection(
    s: BillsState,
    vm: PlanViewModel,
    padding: PaddingValues,
    onAddBill: () -> Unit,
    onOpenBill: (Long) -> Unit,
    onSubscriptions: () -> Unit,
    onLoan: (Long?) -> Unit,
) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val paid: (Long) -> Unit = { id -> vm.markPaid(id) { undo -> snackbar.show(context.getString(R.string.marked_paid), undo) } }
    val skip: (Long) -> Unit = { id -> vm.skip(id) { undo -> snackbar.show(context.getString(R.string.skipped), undo) } }
    if (!s.hasBills) {
        EmptyState(
            shape = { Cookie(painterResource(R.drawable.ic_event_repeat_regular)) },
            title = stringResource(R.string.no_bills),
            line = stringResource(R.string.no_bills_line),
            button = { BurrowButton(stringResource(R.string.add_bill), onAddBill, kind = ButtonKind.TONAL) },
        )
        return
    }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    app.burrow.money.ui.components.KeepTopVisible(list, s.overdue.map { it.occurrence.id })
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(Space.block)) {
        s.overdue.forEach { o ->
            item(key = "overdue-${o.occurrence.id}", contentType = "note") {
                Note(
                    kind = NoteKind.ERROR,
                    icon = painterResource(R.drawable.ic_error_duotone),
                    title = stringResource(R.string.bill_overdue, o.name),
                    line = stringResource(R.string.dot_join, money.plain(o.amount), stringResource(R.string.was_due, dates.shortDay(o.date()))),
                    action = stringResource(R.string.mark_paid),
                    onAction = { paid(o.occurrence.id) },
                    onClick = { onOpenBill(o.occurrence.billId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        s.nextUp?.let { o -> item(key = "next", contentType = "next") { NextUpCard(o, dates, today, { onOpenBill(o.occurrence.billId) }, { paid(o.occurrence.id) }) } }
        item(key = "widgets", contentType = "widgets") {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
                Widget(
                    stringResource(R.string.subscriptions), painterResource(R.drawable.ic_autorenew_duotone), money.plain(money.whole(s.subsMonthly)),
                    pluralStringResource(R.plurals.subs_line, s.subsCount, s.subsCount), Modifier.weight(1f), onClick = onSubscriptions,
                )
                Widget(
                    stringResource(R.string.emis), painterResource(R.drawable.ic_real_estate_agent_duotone), money.plain(s.emiMonthly),
                    pluralStringResource(R.plurals.emis_line, s.loans, s.loans), Modifier.weight(1f), onClick = { onLoan(s.loanAccountId) },
                )
            }
        }
        billGroup(R.string.this_week, s.thisWeek, dates, today, onOpenBill, paid, skip)
        billGroup(R.string.later, s.later, dates, today, onOpenBill, paid, skip)
    }
}

/** A section of bill rows, swipeable. */
private fun LazyListScope.billGroup(
    title: Int,
    list: List<OccurrenceView>,
    dates: DateText,
    today: LocalDate,
    onOpen: (Long) -> Unit,
    paid: (Long) -> Unit,
    skip: (Long) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h-$title", contentType = "header") { SectionHeader(androidx.compose.ui.res.stringResource(title)) }
    item(key = "g-$title", contentType = "group") {
        ListGroup { list.forEachIndexed { i, o -> androidx.compose.runtime.key(o.occurrence.id) { BillRow(o, i, list.size, dates, today, onOpen, paid, skip) } } }
    }
}

@Composable
private fun BillRow(o: OccurrenceView, index: Int, count: Int, dates: DateText, today: LocalDate, onOpen: (Long) -> Unit, paid: (Long) -> Unit, skip: (Long) -> Unit) {
    val money = LocalMoney.current
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val isPaid = o.occurrence.status == OccurrenceStatus.PAID
    val paidLabel = stringResource(R.string.mark_paid)
    val skipLabel = stringResource(R.string.skip)
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> { haptics.confirm(); paid(o.occurrence.id) }
            SwipeToDismissBoxValue.EndToStart -> { haptics.threshold(); skip(o.occurrence.id) }
            else -> Unit
        }
        false // the row stays; the data moves it
    })
    SwipeToDismissBox(
        swipe,
        enableDismissFromStartToEnd = !isPaid,
        enableDismissFromEndToStart = !isPaid,
        backgroundContent = {
            val toPaid = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            if (swipe.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Row(
                    Modifier.fillMaxSize().clip(Shapes.grouped(index, count)).background(if (toPaid) c.primaryContainer else c.surfaceContainerHigh).padding(horizontal = Space.sheet),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (toPaid) Arrangement.Start else Arrangement.End,
                ) {
                    Text(if (toPaid) paidLabel else skipLabel, style = BurrowType.button, color = if (toPaid) c.onPrimaryContainer else c.onSurfaceVariant)
                }
            }
        },
    ) {
        ListRow(
            o.name, index, count,
            supporting = stringResource(R.string.dot_join, o.categoryName ?: kindLabel(o.kind), shortName(o.accountName)),
            modifier = Modifier.semantics {
                if (!isPaid) customActions = listOf(CustomAccessibilityAction(paidLabel) { paid(o.occurrence.id); true }, CustomAccessibilityAction(skipLabel) { skip(o.occurrence.id); true })
            },
            lead = { CategoryCircle(o.icon, o.hue) },
            trailing = {
                AmountTrailing(
                    money.plain(o.amount),
                    if (isPaid) stringResource(R.string.paid) else dates.due(o.date(), today),
                    if (isPaid) c.primary else c.onSurfaceVariant,
                )
            },
            onClick = { onOpen(o.occurrence.billId) },
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

/** The one card with a button (DESIGN rule 4's exception): the next bill and Mark paid. */
@Composable
private fun NextUpCard(o: OccurrenceView, dates: DateText, today: LocalDate, onOpen: () -> Unit, onPaid: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    Column(
        Modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainerLow).clickable(onClick = onOpen).padding(Space.card),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            Icon(painterResource(R.drawable.ic_event_available_duotone), null, tint = c.primary, modifier = Modifier.size(Size.iconSmall))
            Text(stringResource(R.string.next_up), style = BurrowType.label, color = c.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.card)) {
            CategoryCircle(o.icon, o.hue, Size.iconCircleBig)
            Column {
                Text(o.name, style = BurrowType.titleSmall, color = c.onSurface)
                Text(money.plain(o.amount), style = BurrowType.title, color = c.onSurface)
                Text(stringResource(R.string.due_from, dates.due(o.date(), today), o.accountName.substringBefore(' ')), style = BurrowType.body, color = c.onSurfaceVariant)
            }
        }
        BurrowButton(stringResource(R.string.mark_paid), onPaid, Modifier.fillMaxWidth(), icon = painterResource(R.drawable.ic_check_regular))
    }
}


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
    var amount by remember(e) { mutableStateOf(e.amount) }
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
