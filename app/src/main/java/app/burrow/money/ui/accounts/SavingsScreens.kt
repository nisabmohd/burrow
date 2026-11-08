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
import app.burrow.money.ui.components.BurrowSwitch
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
 * Savings (202 Once, 203 Every month, 204 Any time, 211 market value): centred header, the rows that
 * say how it grows, one action button (Add money / Pay instalment), then Deposits, Instalments or Money added.
 */
@Composable
fun SavingsContent(
    a: AccountEntity,
    sv: SavingsInfo,
    icon: @Composable () -> Unit,
    onRemind: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onUpdateValue: () -> Unit,
) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val c = MaterialTheme.colorScheme
    val rate = a.interestRate.orEmpty()
    val monthly = sv.mode == SavingsMode.MONTHLY
    val from = sv.payout?.name ?: stringResource(R.string.an_account)
    val each = money.plain(a.emi ?: 0)
    if (sv.market) {
        val gain = sv.worth - sv.putIn
        CenterHeader(icon, a.name, money.plain(sv.worth), line = stringResource(R.string.gain_line, money.signed(gain), money.plain(sv.putIn)))
        ListGroup {
            val n = if (monthly) 2 else 1
            if (monthly) ListRow(
                sv.next?.let { stringResource(R.string.savings_next_on, dates.shortDay(it)) } ?: stringResource(R.string.no_end_date), 0, n,
                supporting = stringResource(R.string.from_line_paid, each, from, sv.paid),
                lead = { IconCircle(painterResource(R.drawable.ic_event_available_duotone)) },
            )
            ListRow(
                a.valueUpdatedAt?.let { stringResource(R.string.value_updated, dates.shortDay(dates.date(it))) } ?: stringResource(R.string.market_value), n - 1, n,
                supporting = stringResource(R.string.market_never),
                lead = { IconCircle(painterResource(R.drawable.ic_bar_chart_duotone)) },
            )
        }
        ListGroup(Modifier.padding(top = Space.block)) {
            ListRow(
                stringResource(R.string.update_value), 0, 1, supporting = stringResource(R.string.update_value_line),
                lead = { IconCircle(painterResource(R.drawable.ic_edit_duotone)) }, trailing = { Chevron() }, onClick = onUpdateValue,
            )
        }
    } else when (sv.mode) {
        SavingsMode.ONCE -> {
            CenterHeader(icon, a.name, money.plain(sv.worth), line = stringResource(R.string.worth_today_line, money.plain(sv.atMaturity)))
            ListGroup {
                ListRow(
                    stringResource(R.string.put_in, money.short(sv.putIn)), 0, 3,
                    supporting = if (sv.deposits.size > 1) pluralStringResource(R.plurals.n_deposits, sv.deposits.size, sv.deposits.size) else stringResource(R.string.rate_a_year, rate),
                    lead = { IconCircle(painterResource(R.drawable.ic_savings_duotone)) },
                )
                ListRow(
                    stringResource(R.string.pays_into, from), 1, 3, supporting = stringResource(R.string.asks_each_matures),
                    lead = { CategoryCircle(sv.payout?.icon, sv.payout?.colorHue) },
                )
                ListRow(
                    stringResource(R.string.remind_week_before), 2, 3,
                    lead = { IconCircle(painterResource(R.drawable.ic_notifications_duotone)) },
                    trailing = { BurrowSwitch(a.remind, onRemind) },
                )
            }
        }
        SavingsMode.MONTHLY -> {
            CenterHeader(
                icon, a.name, money.plain(sv.putIn),
                line = sv.total?.let { stringResource(R.string.every_month_paid, each, sv.paid, it) } ?: stringResource(R.string.every_month_paid_open, each, sv.paid),
            )
            ListGroup {
                ListRow(
                    sv.next?.let { stringResource(R.string.savings_next_on, dates.shortDay(it)) } ?: stringResource(R.string.no_end_date), 0, 3,
                    supporting = stringResource(R.string.from_line, each, from),
                    lead = { IconCircle(painterResource(R.drawable.ic_event_available_duotone)) },
                )
                ListRow(
                    stringResource(R.string.rate_a_year, rate), 1, 3,
                    supporting = if (sv.atEnd != null && sv.end != null) stringResource(R.string.when_it_ends, money.plain(sv.atEnd), dates.monthYear(sv.end)) else stringResource(R.string.no_end_date),
                    lead = { IconCircle(painterResource(R.drawable.ic_savings_duotone)) },
                )
                ListRow(
                    stringResource(R.string.remind_day_before), 2, 3,
                    lead = { IconCircle(painterResource(R.drawable.ic_notifications_duotone)) },
                    trailing = { BurrowSwitch(a.remind, onRemind) },
                )
            }
        }
        else -> {
            CenterHeader(icon, a.name, money.plain(sv.worth), line = stringResource(R.string.grows_at, rate))
            ListGroup {
                ListRow(
                    stringResource(R.string.added_this_year, money.short(sv.addedThisYear)), 0, 2,
                    supporting = stringResource(R.string.a_year_at_rate, money.plain(sv.yearly)),
                    lead = { IconCircle(painterResource(R.drawable.ic_savings_duotone)) },
                )
                ListRow(
                    sv.end?.let { stringResource(R.string.matures_month, dates.monthYear(it)) } ?: stringResource(R.string.no_end_date), 1, 2,
                    supporting = stringResource(R.string.take_it_out),
                    lead = { IconCircle(painterResource(R.drawable.ic_schedule_duotone)) },
                )
            }
        }
    }
    BurrowButton(
        stringResource(if (monthly) R.string.pay_instalment else R.string.add_money), onAdd,
        Modifier.fillMaxWidth().padding(top = Space.block),
        icon = painterResource(if (monthly) R.drawable.ic_payments_regular else R.drawable.ic_add_regular),
    )
    when {
        monthly -> {
            // Next first ("Next"), then paid ones newest first with a round check.
            val upcoming = sv.instalments.filter { it.status == OccurrenceStatus.UPCOMING }.sortedBy { it.dueDate }.take(1)
            val done = sv.instalments.filter { it.status != OccurrenceStatus.UPCOMING && it.dueDate <= System.currentTimeMillis() + 86_400_000L }.sortedByDescending { it.dueDate }
            val rows = upcoming + done
            if (rows.isNotEmpty()) {
                SectionHeader(stringResource(R.string.instalments))
                ListGroup {
                    rows.forEachIndexed { i, o ->
                        val day = dates.shortDay(dates.date(o.dueDate))
                        ListRow(
                            money.plain(o.amount ?: a.emi ?: 0), i, rows.size,
                            supporting = stringResource(
                                when (o.status) { OccurrenceStatus.PAID -> R.string.paid_on_short; OccurrenceStatus.SKIPPED -> R.string.skipped_on_short; else -> R.string.due_on_short }, day,
                            ),
                            lead = { CategoryCircle(a.icon, a.colorHue) },
                            trailing = when (o.status) {
                                OccurrenceStatus.UPCOMING -> ({ Text(stringResource(R.string.next), style = BurrowType.label, color = c.primary) })
                                OccurrenceStatus.PAID -> ({ app.burrow.money.ui.components.CheckBadge(true) })
                                else -> null
                            },
                        )
                    }
                }
            }
        }
        sv.mode == SavingsMode.ONCE && !sv.market -> if (sv.deposits.isNotEmpty()) {
            SectionHeader(stringResource(R.string.deposits), meta = sv.deposits.size.toString())
            ListGroup {
                sv.deposits.forEachIndexed { i, d ->
                    ListRow(
                        stringResource(R.string.deposit_row, money.plain(d.amount), d.rate), i, sv.deposits.size,
                        supporting = stringResource(R.string.deposit_dates, dates.fullDay(d.start), dates.fullDay(d.maturity)),
                        lead = { CategoryCircle(a.icon, a.colorHue) },
                        trailing = { AmountTrailing(money.short(d.atMaturity)) },
                    )
                }
            }
        }
        else -> if (sv.moneyIn.isNotEmpty()) {
            SectionHeader(stringResource(R.string.money_added), meta = money.short(sv.moneyIn.sumOf { it.amount }))
            ListGroup {
                sv.moneyIn.take(30).forEachIndexed { i, m ->
                    ListRow(
                        money.plain(m.amount), i, minOf(30, sv.moneyIn.size),
                        supporting = stringResource(R.string.added_from, dates.shortDay(dates.date(m.at)), m.fromName),
                        lead = { CategoryCircle(m.fromIcon, m.fromHue) },
                    )
                }
            }
        }
    }
}

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
fun PercentSheet(title: String, rate: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(rate) }
    var fresh by rememberSaveable { mutableStateOf(true) }
    val ok = text.toBigDecimalOrNull()?.let { it.signum() >= 0 && it < java.math.BigDecimal(100) } == true
    app.burrow.money.ui.components.ValueSheet(
        title = title, value = stringResource(R.string.rate_pct, text.ifEmpty { "0" }),
        onKey = { key -> text = app.burrow.money.ui.components.typeRate(if (fresh && key != app.burrow.money.ui.components.Key.Backspace) "" else text, key); fresh = false },
        confirm = stringResource(R.string.done), confirmEnabled = ok, onConfirm = { if (ok) onDone(text) }, onDismiss = onDismiss,
    )
}

/** A new deposit's rate and maturity (205 › "tap to change"). Design review: no PNG. */
@Composable
fun NewDepositSheet(rate: String, maturity: LocalDate, onRate: () -> Unit, onMaturity: () -> Unit, onDismiss: () -> Unit) {
    val dates = rememberDateText()
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.new_deposit), onDismiss)
        SheetBody {
            ListGroup {
                ListRow(
                    stringResource(R.string.rate_a_year, rate), 0, 2, supporting = stringResource(R.string.interest),
                    lead = { IconCircle(painterResource(R.drawable.ic_percent_duotone)) }, onClick = onRate,
                )
                ListRow(
                    stringResource(R.string.matures_month, dates.fullDay(maturity)), 1, 2, supporting = stringResource(R.string.ends_tap_change),
                    lead = { IconCircle(painterResource(R.drawable.ic_event_available_duotone)) }, onClick = onMaturity,
                )
            }
        }
    }
}

/** The extra row in Add money for a Once account (205). */
@Composable
fun NewDepositRow(rate: String, months: Int, i: Int, n: Int, onClick: () -> Unit) = ListRow(
    stringResource(R.string.starts_new_deposit), i, n, supporting = stringResource(R.string.new_deposit_line, rate, termText(months)),
    lead = { IconCircle(painterResource(R.drawable.ic_savings_duotone)) }, onClick = onClick,
)

/**
 * New savings (208, 209, 213): Name; How money goes in (Once · Every month · Any time) and How it grows
 * (Fixed rate · Market value), each a button group with one helper line; then amount, interest (not for
 * market value), ends and from, each opening its sheet.
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
) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val c = MaterialTheme.colorScheme
    LabeledField(stringResource(R.string.name)) {
        PillTextField(f.name, onName, stringResource(R.string.savings_name_hint), leading = painterResource(R.drawable.ic_edit_note_regular), clearLabel = stringResource(R.string.clear))
    }
    val helper: @Composable (Int) -> Unit = { Text(stringResource(it), style = BurrowType.body, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.headingInset)) }
    LabeledField(stringResource(R.string.how_money_goes_in)) {
        ButtonGroup(listOf(SavingsMode.ONCE, SavingsMode.MONTHLY, SavingsMode.ANY), f.savingsMode, { savingsModeLabel(it) }, onMode)
    }
    helper(when (f.savingsMode) { SavingsMode.MONTHLY -> R.string.mode_monthly_line; SavingsMode.ANY -> R.string.mode_any_line; else -> R.string.mode_once_line })
    LabeledField(stringResource(R.string.how_it_grows)) {
        ButtonGroup(listOf(false, true), f.market, { stringResource(if (it) R.string.market_value else R.string.fixed_rate) }, onMarket)
    }
    helper(if (f.market) R.string.market_value_line else R.string.fixed_rate_line)
    val amount = app.burrow.money.domain.AmountInput(f.balance, money.digits).minor()
    val monthly = f.savingsMode == SavingsMode.MONTHLY
    // Editing Once or Any time: what was put in is the opening balance (below), not typed again.
    val showAmount = !isEdit || monthly
    val rows = buildList<@Composable (Int, Int) -> Unit> {
        if (showAmount) add { i, n ->
            ListRow(
                if (amount > 0) money.plain(amount) else stringResource(R.string.type_the_amount), i, n,
                supporting = stringResource(when (f.savingsMode) { SavingsMode.MONTHLY -> R.string.each_month; SavingsMode.ANY -> R.string.starting_amount; else -> R.string.amount }),
                supportingColor = if (amountMissing) c.error else c.onSurfaceVariant,
                lead = { IconCircle(painterResource(R.drawable.ic_payments_duotone)) }, onClick = onAmount,
            )
        }
        if (!f.market) add { i, n ->
            ListRow(
                if (f.rate.isNotBlank()) stringResource(R.string.rate_a_year, f.rate) else stringResource(R.string.type_the_rate), i, n,
                supporting = stringResource(R.string.interest),
                lead = { IconCircle(painterResource(R.drawable.ic_percent_duotone)) }, onClick = onRate,
            )
        }
        add { i, n ->
            ListRow(
                f.maturity?.let { stringResource(R.string.matures_month, dates.monthYear(it)) } ?: stringResource(R.string.no_end_date), i, n,
                supporting = stringResource(if (f.maturity != null) R.string.ends_tap_change else R.string.ends_tap_set),
                lead = { IconCircle(painterResource(if (f.maturity != null) R.drawable.ic_event_available_duotone else R.drawable.ic_schedule_duotone)) }, onClick = onEnds,
            )
        }
        add { i, n ->
            ListRow(
                stringResource(R.string.from_account, from?.account?.name ?: stringResource(R.string.an_account)), i, n,
                supporting = stringResource(R.string.money_comes_from),
                lead = { CategoryCircle(from?.account?.icon, from?.account?.colorHue) }, onClick = onFrom,
            )
        }
    }
    ListGroup { rows.forEachIndexed { i, r -> r(i, rows.size) } }
}

/** Sunday review (101): market-value accounts whose value is 30+ days old. */
fun staleInvestments(accounts: List<AccountWithBalance>, now: Long = System.currentTimeMillis()): List<AccountEntity> =
    accounts.map { it.account }.filter { it.type == app.burrow.money.data.AccountType.SAVINGS && it.marketValue && !it.archived && (it.valueUpdatedAt ?: 0) < now - 30L * 86_400_000L }
