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
import app.burrow.money.ui.components.confirm
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
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LabeledField
import app.burrow.money.ui.components.SheetBody
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

/** The New bill due row: "Every week on Monday", "Every month on the 1st", "Every year on 9 Jan". */
@Composable
fun dueLabel(r: Repeat, dates: DateText): String = when (r) {
    is Repeat.Monthly -> stringResource(R.string.every_month_on, dates.ordinal(r.day))
    is Repeat.Yearly -> stringResource(R.string.every_year_on, dates.shortDay(LocalDate.of(2000, r.month, r.day)))
    is Repeat.Weekly -> stringResource(R.string.every_week_on, r.day.getDisplayName(TextStyle.FULL, java.util.Locale.getDefault()))
}

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
fun BillScreen(vm: BillViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit, reminderMinutes: Int = 9 * 60) {
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
        app.burrow.money.ui.components.BlockColumn(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
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
                    supporting = if (b.remindersOn) androidx.compose.ui.res.pluralStringResource(R.plurals.reminder_at, b.remindDaysBefore, minutesLabel(reminderMinutes) /* the one reminder time from Notifications (160) */, b.remindDaysBefore) else stringResource(R.string.reminders_off),
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
 * New bill (27): close · title · Save; Bill | Subscription; Name; Amount; How often (Weekly ·
 * Monthly · Yearly); Category, Paid from and the due day (the day sheet 28, a weekday sheet or the
 * date sheet); When it is due: Ask me / Record it for me / Only remind me.
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
        app.burrow.money.ui.components.BlockColumn(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
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
            LabeledField(stringResource(R.string.how_often)) {
                ButtonGroup(
                    Repeat.Every.entries, f.repeat.every,
                    { stringResource(when (it) { Repeat.Every.WEEK -> R.string.weekly; Repeat.Every.MONTH -> R.string.monthly; Repeat.Every.YEAR -> R.string.yearly }) },
                    { e -> vm.edit { it.copy(repeat = it.repeat.every(e, LocalDate.now())) } }, Modifier.fillMaxWidth(),
                )
            }
            // The due row comes first and follows How often (27, 161, 162).
            ListGroup {
                ListRow(
                    dueLabel(f.repeat, dates), 0, 3, supporting = stringResource(R.string.due),
                    lead = { IconCircle(painterResource(R.drawable.ic_calendar_month_duotone)) },
                    onClick = { sheet = BillSheet.DAY },
                )
                ListRow(
                    s.category?.name ?: stringResource(R.string.choose_category), 1, 3, supporting = stringResource(R.string.category),
                    lead = { CategoryCircle(s.category?.icon, s.category?.colorHue) }, onClick = { sheet = BillSheet.CATEGORY },
                )
                ListRow(
                    s.account?.account?.name ?: stringResource(R.string.choose_account), 2, 3, supporting = stringResource(R.string.paid_from),
                    supportingColor = if (f.tried && s.account == null) c.error else c.onSurfaceVariant,
                    lead = { CategoryCircle(s.account?.account?.icon, s.account?.account?.colorHue) }, onClick = { sheet = BillSheet.ACCOUNT },
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
                    app.burrow.money.ui.components.PickRow(
                        onDueTitle(value), i, options.size, f.onDue == value, { vm.edit { it.copy(onDue = value) } },
                        supporting = stringResource(line), lead = { IconCircle(painterResource(icon)) },
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
        BillSheet.DAY -> when (val r = f.repeat) {
            is Repeat.Monthly -> DueDaySheet(r.day, dates, { d -> vm.edit { it.copy(repeat = Repeat.Monthly(d)) }; sheet = null }, { sheet = null })
            is Repeat.Weekly -> WeekdaySheet(r.day, { d -> vm.edit { it.copy(repeat = Repeat.Weekly(d)) }; sheet = null }, { sheet = null })
            is Repeat.Yearly -> app.burrow.money.ui.pickers.DateSheet(
                r.nextOnOrAfter(LocalDate.now()), { d -> vm.edit { it.copy(repeat = Repeat.Yearly(d.monthValue, d.dayOfMonth)) }; sheet = null }, { sheet = null },
                title = stringResource(R.string.due_every_year_on), anyDay = true, yearly = true,
            )
        }
        null -> Unit
    }
}

/**
 * Due every week on (163): one row of day circles from the week-start day, "Every Monday · next on
 * Mon, 26 Oct" under it (follows each tap; the sheet stays open), Done saves the day.
 */
@Composable
private fun WeekdaySheet(start: java.time.DayOfWeek, onPick: (java.time.DayOfWeek) -> Unit, onDismiss: () -> Unit) {
    var day by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(start) }
    val dates = rememberDateText()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val next = Repeat.Weekly(day).nextOnOrAfter(LocalDate.now())
    app.burrow.money.ui.components.BurrowSheet(onDismiss) {
        app.burrow.money.ui.components.SheetHeader(stringResource(R.string.due_every_week_on), onDismiss)
        SheetBody {
            app.burrow.money.ui.components.WeekdayRow(day, app.burrow.money.ui.LocalDatePrefs.current.weekStart, { day = it })
            androidx.compose.material3.Text(
                stringResource(R.string.every_weekday_next, day.getDisplayName(TextStyle.FULL, java.util.Locale.getDefault()), dates.weekdayDay(next)),
                style = app.burrow.money.ui.theme.BurrowType.body, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        app.burrow.money.ui.components.SheetFooter {
            app.burrow.money.ui.components.BurrowButton(
                stringResource(R.string.done), { haptics.confirm(); onPick(day) }, Modifier.fillMaxWidth(), height = Size.buttonBottom,
            )
        }
    }
}

/** Due every month on (28): the day value sheet, quick days, "Next due 1 Nov", Done. */
@Composable
private fun DueDaySheet(start: Int, dates: DateText, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    app.burrow.money.ui.components.DaySheet(
        title = stringResource(R.string.due_every_month_on), day = start, onDone = onDone, onDismiss = onDismiss,
        ordinal = dates::ordinal, quick = listOf(1, 5, 10, 15, 25), max = 31,
        result = { d -> stringResource(R.string.next_due, dates.shortDay(Repeat.Monthly(d).nextOnOrAfter(LocalDate.now()))) },
    )
}
