package app.burrow.money.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.ConfirmDialog
import app.burrow.money.ui.components.HeaderIcon
import app.burrow.money.ui.components.LabeledField
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.Note
import app.burrow.money.ui.components.NoteKind
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.AmountTrailing
import app.burrow.money.ui.components.AmountStepperSheet
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.currencyIcon
import app.burrow.money.ui.pickers.AccountSheet
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.DateSheet
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.txn.TxnRow
import java.time.LocalDate

/** A pushed screen's frame: top bar, then a scrolling column with the screen margin. */
@Composable
fun DetailFrame(
    onBack: () -> Unit,
    title: String? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack, title = title, actions = actions)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
            content = content,
        )
        if (bottom != null) Box(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.block)) { bottom() }
    }
}

/** Subscriptions (29): the suggestion note, what they cost, the active ones. */
@Composable
fun SubscriptionsScreen(vm: SubscriptionsViewModel, onBack: () -> Unit, onAdd: (BillForm?) -> Unit, onOpen: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val dates = rememberDateText()
    DetailFrame(onBack, stringResource(R.string.subscriptions), actions = {
        TonalIconButton(painterResource(R.drawable.ic_add_regular), stringResource(R.string.add_bill), { onAdd(BillForm(kind = "SUBSCRIPTION")) })
    }) {
        s.hint?.let { h ->
            Note(
                kind = NoteKind.INFO,
                icon = painterResource(R.drawable.ic_lightbulb_duotone),
                title = stringResource(R.string.looks_like_sub, h.payee),
                line = stringResource(R.string.paid_in_a_row, money.plain(h.amount)),
                action = stringResource(R.string.add),
                onAction = { onAdd(BillForm(kind = "SUBSCRIPTION", name = h.payee, amount = java.math.BigDecimal.valueOf(h.amount, money.digits).stripTrailingZeros().toPlainString(), categoryId = h.categoryId, accountId = h.accountId)) },
                modifier = Modifier.padding(bottom = Space.block),
            )
        }
        CenterHeader(
            icon = { HeaderIcon(painterResource(R.drawable.ic_autorenew_regular)) },
            title = stringResource(R.string.every_month),
            value = money.plain(money.whole(s.monthly)),
            line = stringResource(R.string.a_year, money.plain(s.yearly)),
        )
        SectionHeader(stringResource(R.string.active), meta = s.rows.size.toString())
        ListGroup {
            s.rows.forEachIndexed { i, r ->
                val yearly = app.burrow.money.domain.Repeat.parse(r.bill.repeatRule) is app.burrow.money.domain.Repeat.Yearly
                ListRow(
                    r.bill.name, i, s.rows.size,
                    supporting = stringResource(R.string.sub_line, stringResource(if (yearly) R.string.yearly else R.string.monthly), r.next?.let { dates.shortDay(it) } ?: "–"),
                    lead = { CategoryCircle(r.icon, r.hue) },
                    trailing = { AmountTrailing(money.plain(r.bill.amount)) },
                    onClick = { onOpen(r.bill.id) },
                )
            }
        }
    }
}

/**
 * Budget (30) or a category's budget (31): centred header ("₹13,644 left" or "₹710 over" in red),
 * the bar; the category rows, or that category's transactions this period. Edit → sheet (32).
 */
@Composable
fun BudgetScreen(vm: BudgetViewModel, onBack: () -> Unit, onOpenCategory: (Long) -> Unit, onOpenTxn: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    var edit by remember { mutableStateOf(false) }
    val line = s.line
    val month = dates.month(s.period.start)
    val cat = line?.category
    DetailFrame(onBack, actions = {
        TonalIconButton(painterResource(R.drawable.ic_edit_regular), stringResource(R.string.edit), { edit = true })
    }) {
        CenterHeader(
            icon = { if (cat != null) CategoryCircle(cat.icon, cat.colorHue, Size.iconCircleBig) else HeaderIcon(painterResource(R.drawable.ic_donut_large_regular)) },
            title = cat?.name ?: stringResource(R.string.month_budget, month),
            value = when {
                line == null -> money.plain(0)
                line.over -> stringResource(R.string.amount_over, money.plain(-line.left))
                else -> stringResource(R.string.amount_left, money.plain(line.left))
            },
            line = line?.let { pluralStringResource(R.plurals.budget_days_to_go, s.daysLeft, money.plain(it.amount), s.daysLeft) },
            progress = line?.fraction,
            bad = line?.over == true,
        )
        if (vm.categoryId == null) {
            SectionHeader(stringResource(R.string.categories))
            BudgetRows(s.categories, onOpenCategory)
        } else {
            val today = LocalDate.now()
            s.txns.groupBy { dates.date(it.txn.at) }.forEach { (day, list) ->
                SectionHeader(dates.day(day, today), meta = money.signed(-list.sumOf { it.txn.amount }))
                ListGroup { list.forEachIndexed { i, t -> TxnRow(t, i, list.size, dates, today, { onOpenTxn(t.txn.id) }) } }
            }
        }
    }
    if (edit) {
        BudgetEditSheet(
            BudgetEdit(vm.categoryId, cat?.name ?: month, line?.budget?.amount ?: (money.digitsUnit() * 30_000), line?.budget?.carryOver ?: false),
            onDismiss = { edit = false },
        ) { amount, carry ->
            edit = false
            vm.save(amount, carry) { undo -> snackbar.show(context.getString(R.string.budget_saved), undo) }
        }
    }
}

/**
 * A goal (33; 34 once reached, with the Goal reached note first): centred header, the bar, what
 * was added and from where, Add money (35).
 */
@Composable
fun GoalScreen(vm: GoalViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val g = s.line.goal
    DetailFrame(
        onBack,
        actions = { TonalIconButton(painterResource(R.drawable.ic_edit_regular), stringResource(R.string.edit), { onEdit(g.id) }) },
        bottom = if (!s.line.reached) ({
            BurrowButton(stringResource(R.string.add_money), { adding = true }, Modifier.fillMaxWidth(), icon = painterResource(R.drawable.ic_add_regular), height = Size.buttonBottom)
        }) else null,
    ) {
        if (s.line.reached) {
            Note(
                kind = NoteKind.INFO,
                icon = painterResource(R.drawable.ic_celebration_duotone),
                title = stringResource(R.string.goal_reached),
                line = g.reachedAt?.let { stringResource(R.string.saved_in_full_on, dates.shortDay(it.toDate())) },
                modifier = Modifier.padding(bottom = Space.block),
            )
        }
        val by = g.targetDate?.toDate()
        CenterHeader(
            icon = { CategoryCircle(g.icon, g.colorHue, Size.iconCircleBig) },
            title = g.name,
            value = money.plain(if (s.line.reached) minOf(s.line.saved, g.target) else s.line.saved),
            line = listOfNotNull(
                stringResource(R.string.of_target, money.plain(g.target)),
                by?.let { stringResource(R.string.by_date, dates.monthYear(it)) },
            ).joinToString(" · "),
            progress = s.line.fraction,
        )
        if (s.added.isNotEmpty()) {
            SectionHeader(stringResource(R.string.added))
            ListGroup {
                s.added.forEachIndexed { i, t ->
                    val from = s.accounts.firstOrNull { it.account.id == t.accountId }?.account
                    ListRow(
                        dates.fullDay(t.at.toDate()), i, s.added.size,
                        supporting = stringResource(R.string.from_account, from?.name.orEmpty()),
                        lead = { CategoryCircle(from?.icon, from?.colorHue) },
                        trailing = { AmountTrailing(money.plain(t.amount)) },
                    )
                }
            }
        }
        BurrowButton(
            stringResource(R.string.delete_goal), { confirmDelete = true },
            Modifier.fillMaxWidth().padding(top = Space.sectionTop), ButtonKind.DANGER, painterResource(R.drawable.ic_delete_regular),
        )
    }
    if (adding) AddMoneySheet(s, g.name, onDismiss = { adding = false }) { from, amount ->
        adding = false
        vm.addMoney(from, amount) { undo -> snackbar.show(context.getString(R.string.money_added), undo) }
    }
    if (confirmDelete) {
        ConfirmDialog(
            painterResource(R.drawable.ic_delete_regular), stringResource(R.string.delete_goal_title), stringResource(R.string.delete_goal_text),
            stringResource(R.string.delete), stringResource(R.string.cancel),
            onConfirm = { confirmDelete = false; vm.delete(onBack) }, onDismiss = { confirmDelete = false },
        )
    }
}

/** Add money (35): − amount +, chips, From account (a transfer, not spending), Cancel / Add. */
@Composable
private fun AddMoneySheet(s: GoalState, goalName: String, onDismiss: () -> Unit, onAdd: (Long, Long) -> Unit) {
    val money = LocalMoney.current
    val unit = money.digitsUnit()
    val pay = s.accounts.filter { it.account.type != "LOAN" && it.account.type != "SAVINGS" }
    var amount by rememberSaveable { mutableLongStateOf(5_000 * unit) }
    var from by rememberSaveable { mutableStateOf(pay.firstOrNull { it.account.id == s.line.goal.accountId }?.account?.id ?: pay.firstOrNull()?.account?.id) }
    var picking by remember { mutableStateOf(false) }
    val account = pay.firstOrNull { it.account.id == from }
    if (picking) {
        AccountSheet(pay, from, { from = it.account.id; picking = false }, { picking = false })
        return
    }
    AmountStepperSheet(
        title = stringResource(R.string.add_money),
        line = goalName,
        amount = amount,
        onAmount = { amount = it },
        step = 1_000 * unit,
        chips = listOf(2_000, 5_000, 10_000, 20_000).map { it * unit },
        confirm = stringResource(R.string.add),
        onConfirm = { from?.let { onAdd(it, amount) } },
        onDismiss = onDismiss,
        extra = {
            ListGroup {
                ListRow(
                    stringResource(R.string.from_account, account?.account?.name.orEmpty()), 0, 1,
                    supporting = stringResource(R.string.a_transfer_not_spending),
                    lead = { CategoryCircle(account?.account?.icon, account?.account?.colorHue) },
                    onClick = { picking = true },
                )
            }
        },
    )
}

/** Circles a goal can have: icon and hue. */
private val GOAL_LOOKS = listOf("flight" to 168f, "shield" to 10f, "laptop_mac" to 215f, "home" to 50f, "school" to 245f, "redeem" to 345f, "directions_car" to 28f, "flag" to 275f)

/** New goal / edit goal: Name, Target, By (optional), and its circle. */
@Composable
fun NewGoalScreen(vm: NewGoalViewModel, onClose: () -> Unit, onSaved: () -> Unit) {
    val formState by vm.form.collectAsStateWithLifecycle()
    val f = formState ?: return
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    val dates = rememberDateText()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose, title = stringResource(R.string.new_goal)) {
            BurrowButton(stringResource(R.string.save), { vm.save(onSaved) }, kind = if (vm.canSave(f)) ButtonKind.FILLED else ButtonKind.OFF)
        }
        app.burrow.money.ui.components.BlockColumn(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
        ) {
            LabeledField(stringResource(R.string.name)) {
                PillTextField(f.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(R.string.goal_name_hint), leading = painterResource(R.drawable.ic_edit_note_regular), clearLabel = stringResource(R.string.clear))
            }
            LabeledField(stringResource(R.string.target)) {
                PillTextField(
                    f.target, { v -> vm.edit { it.copy(target = v.filter { ch -> ch.isDigit() || ch == '.' }) } }, "0",
                    leading = painterResource(currencyIcon(money.currency)), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Decimal,
                )
            }
            ListGroup {
                ListRow(
                    f.by?.let { dates.monthYear(it) } ?: stringResource(R.string.no_date), 0, 1, supporting = stringResource(R.string.by_when),
                    lead = { IconCircle(painterResource(R.drawable.ic_calendar_view_month_duotone)) }, onClick = { pickDate = true },
                )
            }
            LabeledField(stringResource(R.string.icon)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GOAL_LOOKS.forEach { (icon, hue) ->
                        val on = f.icon == icon
                        val label = icon.replace('_', ' ')
                        Box(
                            Modifier.clip(Shapes.pill)
                                .then(if (on) Modifier.border(Space.group / 2, c.primary, Shapes.pill) else Modifier)
                                .clickable(role = Role.RadioButton) { vm.edit { it.copy(icon = icon, hue = hue) } }
                                .semantics { selected = on; contentDescription = label },
                        ) { CategoryCircle(icon, hue) }
                    }
                }
            }
            if (f.tried && !vm.canSave(f)) {
                androidx.compose.material3.Text(stringResource(R.string.goal_cant_save), style = app.burrow.money.ui.theme.BurrowType.body, color = c.error)
            }
        }
    }
    if (pickDate) DateSheet(f.by ?: LocalDate.now().plusMonths(6), { d -> vm.edit { it.copy(by = d) }; pickDate = false }, { pickDate = false }, future = true, title = stringResource(R.string.by_when))
}
