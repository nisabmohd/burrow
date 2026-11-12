package app.burrow.money.ui.accounts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.burrow.money.R
import app.burrow.money.data.AccountEntity
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.data.SavingsMode
import app.burrow.money.data.savingsModeOf
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.AmountTrailing
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.Ghost
import app.burrow.money.ui.components.duotoneIcon
import app.burrow.money.ui.components.ButtonGroup
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.Chevron
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LabeledField
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import java.time.LocalDate

/** "Once" / "Every month" / "Any time". */
@Composable
fun savingsModeLabel(mode: String): String = stringResource(
    when (mode) {
        SavingsMode.MONTHLY -> R.string.mode_monthly
        SavingsMode.ANY -> R.string.mode_any
        else -> R.string.mode_once
    },
)

/** A savings row's line on Accounts (210): "Once · 7.1% · matures Mar 2027", "Every month · market value · updated 5 Oct". */
@Composable
fun savingsLine(a: AccountEntity): String {
    val dates = rememberDateText()
    val mode = savingsModeOf(a)
    val end = a.maturityDate?.let { dates.monthYear(dates.date(it)) }
    return listOfNotNull(
        savingsModeLabel(mode),
        if (a.marketValue) stringResource(R.string.market_short) else a.interestRate?.let { stringResource(R.string.rate_pct, it) },
        when {
            a.marketValue -> a.valueUpdatedAt?.let { stringResource(R.string.updated_short, dates.shortDay(dates.date(it))) }
            end == null -> stringResource(R.string.no_end_short)
            mode == SavingsMode.ONCE -> stringResource(R.string.matures_short, end)
            else -> stringResource(R.string.ends_short, end)
        },
    ).joinToString(" · ")
}

/** "2 years" / "18 months". */
@Composable
private fun termText(months: Int): String =
    if (months % 12 == 0) pluralStringResource(R.plurals.n_years, months / 12, months / 12) else pluralStringResource(R.plurals.n_months, months, months)

/**
 * Savings (202 Once, 203 Every month, 204 Any time, 211/218/219 market value) = the bank page (56):
 * centred header; one list of action rows, at most one info row, then Count in net worth ([netWorth]);
 * one history section built like transaction rows. An empty history (222, 223) is the illustrated empty
 * state with the next step as its button, and that step's row is hidden.
 */
@Composable
fun SavingsContent(
    a: AccountEntity,
    sv: SavingsInfo,
    icon: @Composable () -> Unit,
    netWorth: @Composable (Int, Int) -> Unit,
    onAdd: () -> Unit,
    onUpdateValue: () -> Unit,
) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val rate = a.interestRate.orEmpty()
    val monthly = sv.mode == SavingsMode.MONTHLY
    val from = sv.payout?.name ?: stringResource(R.string.an_account)
    val each = money.plain(a.emi ?: 0)
    val paidRows = sv.instalments.filter { it.status == OccurrenceStatus.PAID }.sortedBy { it.dueDate }
    // Which history this account keeps, and whether it is still empty.
    val history = when {
        monthly -> R.string.instalments
        sv.mode == SavingsMode.ONCE -> if (sv.market) null else R.string.deposits
        else -> R.string.money_added
    }
    val empty = when (history) {
        R.string.instalments -> paidRows.isEmpty()
        R.string.deposits -> sv.deposits.isEmpty()
        R.string.money_added -> sv.moneyIn.isEmpty()
        else -> false
    }
    val payRow: @Composable (Int, Int) -> Unit = { i, n ->
        ListRow(
            stringResource(R.string.pay_instalment), i, n,
            supporting = sv.next?.let { stringResource(R.string.due_amount_on, each, dates.shortDay(it)) } ?: stringResource(R.string.amount_a_month, each),
            lead = { IconCircle(painterResource(R.drawable.ic_payments_duotone)) }, onClick = onAdd,
        )
    }
    val addRow: @Composable (Int, Int) -> Unit = { i, n ->
        ListRow(
            stringResource(R.string.add_money), i, n,
            supporting = stringResource(if (sv.mode == SavingsMode.ONCE) R.string.starts_new_deposit else R.string.any_amount_any_time),
            lead = { IconCircle(painterResource(R.drawable.ic_add_regular)) }, onClick = onAdd,
        )
    }
    val rateRow: @Composable (Int, Int, String) -> Unit = { i, n, line ->
        ListRow(stringResource(R.string.rate_a_year, rate), i, n, supporting = line, lead = { IconCircle(painterResource(R.drawable.ic_percent_duotone)) })
    }
    val endsLine = if (sv.atEnd != null && sv.end != null) stringResource(R.string.when_it_ends, money.plain(sv.atEnd), dates.monthYear(sv.end)) else stringResource(R.string.stop_any_time)
    // The action row first (hidden while its history is empty: the empty state's button does it), then info.
    val rows = buildList<@Composable (Int, Int) -> Unit> {
        if (sv.market) {
            add { i, n ->
                ListRow(
                    stringResource(R.string.update_value), i, n,
                    supporting = a.valueUpdatedAt?.let { stringResource(R.string.last_updated, dates.shortDay(dates.date(it))) } ?: stringResource(R.string.update_value_line),
                    lead = { IconCircle(painterResource(R.drawable.ic_edit_duotone)) }, onClick = onUpdateValue,
                )
            }
            when (sv.mode) {
                SavingsMode.MONTHLY -> if (!empty) add(payRow)
                SavingsMode.ONCE -> add { i, n ->
                    ListRow(
                        stringResource(R.string.put_in_amount, money.plain(sv.putIn)), i, n,
                        supporting = stringResource(R.string.on_day, dates.fullDay(a.startDate?.let { dates.date(it) } ?: dates.date(a.createdAt))),
                        lead = { IconCircle(painterResource(R.drawable.ic_savings_duotone)) },
                    )
                }
                else -> if (!empty) add(addRow)
            }
        } else when (sv.mode) {
            SavingsMode.ONCE -> {
                if (!empty) add(addRow)
                val nextMaturity = sv.deposits.map { it.maturity }.filter { !it.isBefore(LocalDate.now()) }.minOrNull() ?: sv.end
                add { i, n -> rateRow(i, n, nextMaturity?.let { stringResource(R.string.pays_into_on, from, dates.fullDay(it)) } ?: stringResource(R.string.pays_into, from)) }
            }
            SavingsMode.MONTHLY -> {
                if (!empty) add(payRow)
                add { i, n -> rateRow(i, n, endsLine) }
            }
            else -> {
                if (!empty) add(addRow)
                // A year's interest at the rate; hidden while there is nothing to earn on (223).
                if (sv.yearly > 0) add { i, n ->
                    ListRow(
                        stringResource(R.string.amount_a_year, money.plain(sv.yearly)), i, n, supporting = stringResource(R.string.interest_at_rate),
                        lead = { IconCircle(painterResource(R.drawable.ic_percent_duotone)) },
                    )
                }
            }
        }
        add(netWorth)
    }
    val line = when {
        sv.market -> stringResource(R.string.gain_line, money.signed(sv.worth - sv.putIn), money.plain(sv.putIn))
        sv.mode == SavingsMode.ONCE -> stringResource(R.string.worth_today_line, money.plain(sv.atMaturity))
        monthly -> sv.total?.let { stringResource(R.string.a_month_paid, each, sv.paid, it) } ?: stringResource(R.string.a_month_no_end, each)
        else -> sv.end?.let { stringResource(R.string.rate_matures, rate, dates.monthYear(it)) } ?: stringResource(R.string.rate_no_end, rate)
    }
    CenterHeader(icon, a.name, money.plain(if (monthly && !sv.market) sv.putIn else sv.worth), line = line)
    ListGroup { rows.forEachIndexed { i, r -> r(i, rows.size) } }
    if (history == null) return
    if (empty) {
        val (title, body, button) = when (history) {
            R.string.instalments -> Triple(
                stringResource(R.string.no_instalments),
                sv.next?.let { stringResource(R.string.no_instalments_line, each, dates.shortDay(it)) } ?: stringResource(R.string.no_instalments_line_open, each),
                R.string.pay_first_instalment,
            )
            R.string.deposits -> Triple(stringResource(R.string.no_deposits), stringResource(R.string.no_deposits_line), R.string.add_money)
            else -> Triple(stringResource(R.string.nothing_added), stringResource(R.string.nothing_added_line, a.name), R.string.add_money)
        }
        EmptyState(
            shape = { Ghost(painterResource(duotoneIcon(a.icon)), a.colorHue) }, title = title, line = body,
            button = {
                BurrowButton(
                    stringResource(button), onAdd, kind = ButtonKind.TONAL,
                    icon = painterResource(if (monthly) R.drawable.ic_payments_regular else R.drawable.ic_add_regular),
                )
            },
        )
        return
    }
    when (history) {
        R.string.instalments -> {
            SectionHeader(stringResource(R.string.instalments), meta = pluralStringResource(R.plurals.n_paid, sv.paid, sv.paid))
            val shown = paidRows.withIndex().reversed().take(HISTORY_MAX)
            ListGroup {
                shown.forEachIndexed { i, (k, o) ->
                    ListRow(
                        stringResource(R.string.instalment_n, k + 1, dates.shortDay(dates.date(o.dueDate))), i, shown.size,
                        supporting = stringResource(R.string.from_account, from),
                        lead = { CategoryCircle(a.icon, a.colorHue) },
                        trailing = { AmountTrailing(money.plain(o.amount ?: a.emi ?: 0)) },
                    )
                }
            }
        }
        R.string.deposits -> {
            SectionHeader(stringResource(R.string.deposits), meta = sv.deposits.size.toString())
            ListGroup {
                sv.deposits.forEachIndexed { i, d ->
                    ListRow(
                        stringResource(R.string.deposit_on, dates.fullDay(d.start)), i, sv.deposits.size,
                        supporting = stringResource(R.string.rate_matures_day, d.rate, dates.fullDay(d.maturity)),
                        lead = { CategoryCircle(a.icon, a.colorHue) },
                        trailing = { AmountTrailing(money.plain(d.amount)) },
                    )
                }
            }
        }
        else -> {
            SectionHeader(stringResource(R.string.money_added), meta = money.short(sv.moneyIn.sumOf { it.amount }))
            val shown = sv.moneyIn.take(HISTORY_MAX)
            ListGroup {
                shown.forEachIndexed { i, m ->
                    ListRow(
                        stringResource(R.string.added_on, dates.shortDay(dates.date(m.at))), i, shown.size,
                        supporting = stringResource(R.string.from_account, m.fromName),
                        lead = { CategoryCircle(m.fromIcon, m.fromHue) },
                        trailing = { AmountTrailing(money.plain(m.amount)) },
                    )
                }
            }
        }
    }
}

private const val HISTORY_MAX = 30

/** Update value (212): today's value typed; the line under it is the gain or loss in words, never coloured. */
@Composable
fun UpdateValueSheet(a: AccountEntity, sv: SavingsInfo, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    app.burrow.money.ui.components.AmountSheet(
        title = stringResource(R.string.update_value),
        line = stringResource(R.string.update_value_sheet_line, a.name, money.plain(sv.worth), a.valueUpdatedAt?.let { dates.shortDay(dates.date(it)) }.orEmpty()),
        amount = sv.worth,
        below = { v -> stringResource(R.string.gain_line, money.signed(v - sv.putIn), money.plain(sv.putIn)) },
        confirm = stringResource(R.string.save), cancel = true, onDone = onSave, onDismiss = onDismiss,
    )
}

/** A rate typed in the value sheet ("7.1%"): Interest on New savings (208) and a new deposit (205). */
@Composable
fun PercentSheet(rate: String, onDone: (String) -> Unit, onDismiss: () -> Unit, title: String = stringResource(R.string.interest_rate_title), line: String? = stringResource(R.string.rate_sheet_sub)) {
    var text by rememberSaveable { mutableStateOf(rate) }
    var fresh by rememberSaveable { mutableStateOf(true) }
    val ok = text.toBigDecimalOrNull()?.let { it.signum() >= 0 && it < java.math.BigDecimal(100) } == true
    app.burrow.money.ui.components.ValueSheet(
        title = title, line = line, value = stringResource(R.string.rate_pct, text.ifEmpty { "0" }),
        onKey = { key -> text = app.burrow.money.ui.components.typeRate(if (fresh && key != app.burrow.money.ui.components.Key.Backspace) "" else text, key); fresh = false },
        confirm = stringResource(R.string.done), confirmEnabled = ok, onConfirm = { if (ok) onDone(text) }, onDismiss = onDismiss,
    )
}

/**
 * New deposit (214): its own rate (value sheet), Matures in 1 · 2 · 3 · 5 years, and the maturity day
 * (tap for the calendar, an exact date). Done goes back to Add money with the amount kept (215).
 */
@Composable
fun NewDepositSheet(
    accountName: String, payout: String, rate: String, years: Int?, maturity: LocalDate,
    onRate: () -> Unit, onYears: (Int) -> Unit, onMaturity: () -> Unit, onDone: () -> Unit,
) {
    val dates = rememberDateText()
    BurrowSheet(onDone) {
        SheetHeader(stringResource(R.string.new_deposit), onDone, accountName)
        SheetBody {
            ListGroup {
                ListRow(
                    if (rate.isNotBlank()) stringResource(R.string.rate_a_year, rate) else stringResource(R.string.add_the_rate), 0, 1,
                    supporting = stringResource(R.string.interest),
                    lead = { IconCircle(painterResource(R.drawable.ic_percent_duotone)) }, onClick = onRate,
                )
            }
            LabeledField(stringResource(R.string.matures_in)) {
                ButtonGroup(listOf(1, 2, 3, 5), years ?: -1, { pluralStringResource(R.plurals.n_years, it, it) }, onYears)
            }
            ListGroup {
                ListRow(
                    stringResource(R.string.matures_month, dates.fullDay(maturity)), 0, 1, supporting = stringResource(R.string.pays_into, payout),
                    lead = { IconCircle(painterResource(R.drawable.ic_event_available_duotone)) }, onClick = onMaturity,
                )
            }
        }
        app.burrow.money.ui.components.SheetFooter {
            BurrowButton(stringResource(R.string.done), onDone, Modifier.fillMaxWidth(), height = Size.buttonBottom)
        }
    }
}

/** The extra row in Add money for a Once account (205, 215): "New deposit · 7.1% · matures 19 Oct 2028 ›". */
@Composable
fun NewDepositRow(rate: String, maturity: LocalDate, i: Int, n: Int, onClick: () -> Unit) = ListRow(
    stringResource(R.string.new_deposit), i, n,
    supporting = stringResource(R.string.rate_matures_day, rate.ifBlank { "–" }, rememberDateText().fullDay(maturity)),
    lead = { IconCircle(painterResource(R.drawable.ic_savings_duotone)) }, trailing = { Chevron() }, onClick = onClick,
)

/**
 * New savings (208, 209, 213) and Edit savings (216, 217): Name; on a new one, Money goes in (Once ·
 * Every month · Any time) and Grows by (Fixed rate · Market value), with no helper lines; then one list:
 * amount, rate (not for market value), ends and the account it's from, each opening its sheet. Editing
 * can't change how money goes in or grows, so [middle] (Colour and Icon) takes the groups' place.
 */
@Composable
fun SavingsFormFields(
    f: AccountForm,
    isEdit: Boolean,
    from: AccountWithBalance?,
    onName: (String) -> Unit,
    onMode: (String) -> Unit,
    onMarket: (Boolean) -> Unit,
    onAmount: () -> Unit,
    onRate: () -> Unit,
    onEnds: () -> Unit,
    onFrom: () -> Unit,
    amountMissing: Boolean,
    rateMissing: Boolean = false,
    middle: @Composable () -> Unit = {},
) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val c = MaterialTheme.colorScheme
    LabeledField(stringResource(R.string.name)) {
        PillTextField(f.name, onName, stringResource(R.string.savings_name_hint), leading = painterResource(R.drawable.ic_edit_note_regular), clearLabel = stringResource(R.string.clear))
    }
    if (isEdit) middle() else {
        LabeledField(stringResource(R.string.money_goes_in)) {
            ButtonGroup(listOf(SavingsMode.ONCE, SavingsMode.MONTHLY, SavingsMode.ANY), f.savingsMode, { savingsModeLabel(it) }, onMode)
        }
        LabeledField(stringResource(R.string.grows_by)) {
            ButtonGroup(listOf(false, true), f.market, { stringResource(if (it) R.string.market_value else R.string.fixed_rate) }, onMarket)
        }
    }
    val amount = app.burrow.money.domain.AmountInput(f.balance, money.digits).minor()
    val rows = buildList<@Composable (Int, Int) -> Unit> {
        add { i, n ->
            ListRow(
                if (amount > 0) money.plain(amount) else stringResource(R.string.type_the_amount), i, n,
                supporting = stringResource(when (f.savingsMode) { SavingsMode.MONTHLY -> R.string.each_month; SavingsMode.ANY -> R.string.starting_amount; else -> R.string.amount }),
                supportingColor = if (amountMissing) c.error else c.onSurfaceVariant,
                lead = { IconCircle(painterResource(R.drawable.ic_payments_duotone)) }, onClick = onAmount,
            )
        }
        if (!f.market) add { i, n ->
            ListRow(
                if (f.rate.isNotBlank()) stringResource(R.string.rate_a_year, f.rate) else stringResource(R.string.add_the_rate), i, n,
                supporting = stringResource(if (f.rate.isNotBlank()) R.string.interest else R.string.rate_needed_short),
                supportingColor = if (rateMissing) c.error else c.onSurfaceVariant,
                lead = { IconCircle(painterResource(R.drawable.ic_percent_duotone)) }, onClick = onRate,
            )
        }
        add { i, n ->
            ListRow(
                f.maturity?.let { stringResource(R.string.matures_month, dates.monthYear(it)) } ?: stringResource(R.string.no_end_date), i, n,
                supporting = stringResource(if (f.maturity != null) R.string.ends else R.string.tap_to_set_one),
                lead = { IconCircle(painterResource(if (f.maturity != null) R.drawable.ic_event_available_duotone else R.drawable.ic_schedule_duotone)) }, onClick = onEnds,
            )
        }
        add { i, n ->
            ListRow(
                from?.account?.name ?: stringResource(R.string.an_account), i, n,
                supporting = stringResource(R.string.money_comes_from),
                lead = { CategoryCircle(from?.account?.icon, from?.account?.colorHue) }, onClick = onFrom,
            )
        }
    }
    ListGroup { rows.forEachIndexed { i, r -> r(i, rows.size) } }
}

/** Sunday review (101): market-value accounts whose value is 30+ days old. */
fun staleInvestments(accounts: List<AccountWithBalance>, now: Long = System.currentTimeMillis()): List<AccountWithBalance> =
    accounts.filter { it.account.type == app.burrow.money.data.AccountType.SAVINGS && it.account.marketValue && !it.account.archived && (it.account.valueUpdatedAt ?: 0) < now - 30L * 86_400_000L }
        .sortedBy { it.account.valueUpdatedAt ?: 0 }

/** Update values (228): each market-value account with its worth and date; a row opens Update value (212). */
@Composable
fun UpdateValuesSheet(list: List<AccountWithBalance>, onPick: (AccountWithBalance) -> Unit, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.update_values), onDismiss, stringResource(R.string.update_values_line))
        SheetBody {
            ListGroup {
                list.forEachIndexed { i, a ->
                    ListRow(
                        a.account.name, i, list.size,
                        supporting = stringResource(R.string.value_from_short, money.plain(a.worth), a.account.valueUpdatedAt?.let { dates.shortDay(dates.date(it)) }.orEmpty()),
                        lead = { CategoryCircle(a.account.icon, a.account.colorHue) }, trailing = { Chevron() }, onClick = { onPick(a) },
                    )
                }
            }
        }
    }
}
