package app.burrow.money.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.data.BillKind
import app.burrow.money.data.OnDue
import app.burrow.money.domain.Repeat
import app.burrow.money.ui.DateText
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ButtonGroup
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.ConfirmDialog
import app.burrow.money.ui.components.DayStepper
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LabeledField
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.pickers.AccountSheet
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.shortName
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import java.time.LocalDate
import java.time.format.TextStyle

/** "Monthly on the 19th", "Yearly on 9 Jan", "Weekly on Monday". */
@Composable
fun repeatLabel(r: Repeat, dates: DateText): String = when (r) {
    is Repeat.Monthly -> stringResource(R.string.monthly_on, dates.ordinal(r.day))
    is Repeat.Yearly -> stringResource(R.string.yearly_on, dates.shortDay(LocalDate.of(2000, r.month, r.day)))
    is Repeat.Weekly -> stringResource(R.string.weekly_on, r.day.getDisplayName(TextStyle.FULL, java.util.Locale.getDefault()))
}

@Composable
private fun onDueTitle(onDue: String) = stringResource(
    when (onDue) {
        OnDue.RECORD -> R.string.on_due_record
        OnDue.REMIND -> R.string.on_due_remind
        else -> R.string.on_due_ask
    },
)

/**
 * A bill (25; 26 when overdue): back · edit; centred header with the amount (red when overdue) and
 * when it is due; Category, Paid from, Repeats, the reminder switch; Paid before; Skip / Mark paid.
 */
@Composable
fun BillScreen(vm: BillViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val b = s.bill
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack) {
            TonalIconButton(painterResource(R.drawable.ic_edit_regular), stringResource(R.string.edit), { onEdit(b.id) })
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            val due = s.current?.occurrence?.dueDate?.toDate()
            CenterHeader(
                icon = { CategoryCircle(b.icon ?: s.category?.icon, b.colorHue ?: s.category?.colorHue, Size.iconCircleBig) },
                title = b.name,
                value = money.plain(s.current?.amount ?: b.amount),
                line = when {
                    due == null -> stringResource(R.string.all_paid)
                    s.overdue -> stringResource(R.string.was_due_cap, dates.shortDay(due))
                    due == today -> stringResource(R.string.due_today)
                    else -> stringResource(R.string.due_on, dates.weekdayDay(due))
                },
                bad = s.overdue,
            )
            val repeat = Repeat.parse(b.repeatRule)
            ListGroup {
                ListRow(
                    s.category?.name ?: kindLabel(b.kind), 0, 4, supporting = stringResource(R.string.category),
                    lead = { CategoryCircle(b.icon ?: s.category?.icon, b.colorHue ?: s.category?.colorHue) },
                )
                ListRow(
                    s.account?.account?.name.orEmpty(), 1, 4, supporting = stringResource(R.string.paid_from),
                    lead = { CategoryCircle(s.account?.account?.icon, s.account?.account?.colorHue) },
                )
                ListRow(
                    repeat?.let { repeatLabel(it, dates) }.orEmpty(), 2, 4, supporting = stringResource(R.string.repeats),
                    lead = { IconCircle(painterResource(R.drawable.ic_event_repeat_duotone)) },
                )
                ListRow(
                    onDueTitle(b.onDue), 3, 4,
                    supporting = if (b.remindersOn) androidx.compose.ui.res.pluralStringResource(R.plurals.reminder_at, b.remindDaysBefore, minutesLabel(b.remindAtMinutes), b.remindDaysBefore) else stringResource(R.string.reminders_off),
                    lead = { IconCircle(painterResource(R.drawable.ic_notifications_duotone)) },
                    trailing = { BurrowSwitch(b.remindersOn, vm::setReminders) },
                )
            }
            if (s.paidBefore.isNotEmpty()) {
                SectionHeader(stringResource(R.string.paid_before))
                ListGroup {
                    s.paidBefore.take(12).forEachIndexed { i, o ->
                        ListRow(
                            dates.fullDay(o.occurrence.dueDate.toDate()), i, minOf(12, s.paidBefore.size),
                            supporting = stringResource(R.string.paid_from_short, shortName(o.accountName)),
                            lead = { CategoryCircle(s.account?.account?.icon, s.account?.account?.colorHue) },
                            trailing = { app.burrow.money.ui.components.AmountTrailing(money.plain(o.amount)) },
                        )
                    }
                }
            }
            if (s.current != null) {
                Row(Modifier.fillMaxWidth().padding(top = Space.small), horizontalArrangement = Arrangement.spacedBy(Space.inline)) {
                    BurrowButton(stringResource(R.string.skip), { vm.skip { undo -> snackbar.show(context.getString(R.string.skipped), undo) } }, Modifier.weight(1f), ButtonKind.TONAL)
                    BurrowButton(
                        stringResource(R.string.mark_paid), { vm.markPaid { undo -> snackbar.show(context.getString(R.string.marked_paid), undo) } },
                        Modifier.weight(1f), icon = painterResource(R.drawable.ic_check_regular),
                    )
                }
            }
            BurrowButton(stringResource(R.string.delete_bill), { confirmDelete = true }, Modifier.fillMaxWidth(), ButtonKind.DANGER, painterResource(R.drawable.ic_delete_regular))
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            painterResource(R.drawable.ic_delete_regular), stringResource(R.string.delete_bill_title), stringResource(R.string.delete_bill_text),
            stringResource(R.string.delete), stringResource(R.string.cancel),
            onConfirm = { confirmDelete = false; vm.delete(onBack) }, onDismiss = { confirmDelete = false },
        )
    }
}

/** "9:00 AM" from minutes after midnight, in the phone's style. */
@Composable
fun minutesLabel(minutes: Int): String {
    val dates = rememberDateText()
    val at = LocalDate.now().atTime(minutes / 60, minutes % 60).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    return dates.time(at)
}

private enum class BillSheet { CATEGORY, ACCOUNT, DAY }

/**
 * New bill (27): close · title · Save; Bill | Subscription; Name; Amount; Category, Paid from and the
 * due day (DayStepper sheet, 28); When it is due: Ask me / Record it for me / Only remind me.
 */
@Composable
fun NewBillScreen(vm: NewBillViewModel, onClose: () -> Unit, onSaved: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val f = s.form
    val c = MaterialTheme.colorScheme
    val dates = rememberDateText()
    var sheet by rememberSaveable { mutableStateOf<BillSheet?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(
            painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose,
            title = stringResource(if (s.isEdit) R.string.edit_bill else R.string.new_bill),
        ) {
            BurrowButton(stringResource(R.string.save), { vm.save(onSaved) }, kind = if (s.canSave) ButtonKind.FILLED else ButtonKind.OFF)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            if (f.kind == BillKind.BILL || f.kind == BillKind.SUBSCRIPTION) {
                ButtonGroup(listOf(BillKind.BILL, BillKind.SUBSCRIPTION), f.kind, { kindLabel(it) }, { k -> vm.edit { it.copy(kind = k) } })
            }
            LabeledField(stringResource(R.string.name)) {
                PillTextField(
                    f.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(R.string.bill_name_hint),
                    leading = painterResource(R.drawable.ic_edit_note_regular), clearLabel = stringResource(R.string.clear),
                )
            }
            LabeledField(stringResource(R.string.amount)) {
                PillTextField(
                    f.amount, { v -> vm.edit { it.copy(amount = v.filter { ch -> ch.isDigit() || ch == '.' }) } }, "0",
                    leading = painterResource(app.burrow.money.ui.components.currencyIcon(LocalMoney.current.currency)), clearLabel = stringResource(R.string.clear),
                    keyboardType = KeyboardType.Decimal,
                )
            }
            ListGroup {
                ListRow(
                    s.category?.name ?: stringResource(R.string.choose_category), 0, 3, supporting = stringResource(R.string.category),
                    lead = { CategoryCircle(s.category?.icon, s.category?.colorHue) }, onClick = { sheet = BillSheet.CATEGORY },
                )
                ListRow(
                    s.account?.account?.name ?: stringResource(R.string.choose_account), 1, 3, supporting = stringResource(R.string.paid_from),
                    supportingColor = if (f.tried && s.account == null) c.error else c.onSurfaceVariant,
                    lead = { CategoryCircle(s.account?.account?.icon, s.account?.account?.colorHue) }, onClick = { sheet = BillSheet.ACCOUNT },
                )
                ListRow(
                    when (val r = f.repeat) {
                        is Repeat.Monthly -> stringResource(R.string.every_month_on, dates.ordinal(r.day))
                        else -> repeatLabel(r, dates)
                    },
                    2, 3, supporting = stringResource(R.string.due_day),
                    lead = { IconCircle(painterResource(R.drawable.ic_event_repeat_duotone)) },
                    onClick = if (f.repeat is Repeat.Monthly) ({ sheet = BillSheet.DAY }) else null,
                )
            }
            SectionHeader(stringResource(R.string.when_due))
            val options = listOf(
                Triple(OnDue.ASK, R.string.on_due_ask_line, R.drawable.ic_notifications_active_duotone),
                Triple(OnDue.RECORD, R.string.on_due_record_line, R.drawable.ic_check_circle_duotone),
                Triple(OnDue.REMIND, R.string.on_due_remind_line, R.drawable.ic_alarm_duotone),
            )
            ListGroup {
                options.forEachIndexed { i, (value, line, icon) ->
                    ListRow(
                        onDueTitle(value), i, options.size, supporting = stringResource(line), selected = f.onDue == value,
                        lead = { IconCircle(painterResource(icon)) },
                        trailing = { if (f.onDue == value) Icon(painterResource(R.drawable.ic_check_regular), null, tint = c.primary, modifier = Modifier.size(Size.icon)) },
                        onClick = { vm.edit { it.copy(onDue = value) } },
                    )
                }
            }
            if (f.tried && !s.canSave) {
                androidx.compose.material3.Text(stringResource(R.string.bill_cant_save), style = app.burrow.money.ui.theme.BurrowType.body, color = c.error, modifier = Modifier.padding(start = Space.grid))
            }
        }
    }
    when (sheet) {
        BillSheet.CATEGORY -> CategorySheet(s.categories, emptyMap(), f.categoryId, { cat -> vm.edit { it.copy(categoryId = cat.id) }; sheet = null }, { sheet = null })
        BillSheet.ACCOUNT -> AccountSheet(s.accounts, f.accountId, { a -> vm.edit { it.copy(accountId = a.account.id) }; sheet = null }, { sheet = null })
        BillSheet.DAY -> DueDaySheet((f.repeat as? Repeat.Monthly)?.day ?: 1, dates, { d -> vm.edit { it.copy(repeat = Repeat.Monthly(d)) }; sheet = null }, { sheet = null })
        null -> Unit
    }
}

/** Due every month on (28): − day +, quick days, "Next due 1 Nov", Done. */
@Composable
private fun DueDaySheet(start: Int, dates: DateText, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    var day by rememberSaveable { mutableIntStateOf(start) }
    val next = Repeat.Monthly(day).nextOnOrAfter(LocalDate.now())
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.due_every_month_on), onDismiss)
        DayStepper(
            day = day, onDay = { day = it }, quick = listOf(1, 5, 10, 15, 25),
            resultLine = stringResource(R.string.next_due, dates.shortDay(next)),
            minus = painterResource(R.drawable.ic_remove_regular), minusLabel = stringResource(R.string.key_minus),
            plus = painterResource(R.drawable.ic_add_regular), plusLabel = stringResource(R.string.key_plus),
            onDone = { onDone(day) }, doneLabel = stringResource(R.string.done), ordinal = dates::ordinal, max = 31,
        )
    }
}
