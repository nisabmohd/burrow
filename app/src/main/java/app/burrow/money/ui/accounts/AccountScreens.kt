package app.burrow.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.AmountStepperSheet
import app.burrow.money.ui.components.AmountTrailing
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.ChipWrap
import app.burrow.money.ui.components.DayStepper
import app.burrow.money.ui.components.HeaderIcon
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LabeledField
import app.burrow.money.ui.components.LinkButton
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.currencyIcon
import app.burrow.money.ui.pickers.AccountSheet
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.DateSheet
import app.burrow.money.ui.pickers.accountTypeLabel
import app.burrow.money.ui.plan.DetailFrame
import app.burrow.money.ui.plan.digitsUnit
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.txn.TxnRow
import java.time.LocalDate

/** "Bank · ••1234". */
@Composable
fun accountLine(a: app.burrow.money.data.AccountEntity): String =
    listOfNotNull(accountTypeLabel(a.type), a.number?.let { "••$it" }).joinToString(" · ")

/** Accounts (55): net worth, then Bank and cash, Cards, Loans, Savings. */
@Composable
fun AccountsScreen(vm: AccountsViewModel, onBack: () -> Unit, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    var settling by rememberSaveable { mutableStateOf<Long?>(null) }
    DetailFrame(onBack, stringResource(R.string.accounts), actions = {
        TonalIconButton(painterResource(R.drawable.ic_add_regular), stringResource(R.string.add_account), onAdd)
    }) {
        CenterHeader(
            icon = { HeaderIcon(painterResource(R.drawable.ic_account_balance_regular)) },
            title = stringResource(R.string.net_worth),
            value = money.balance(s.netWorth),
            line = if (s.excluded.isEmpty()) pluralStringResource(R.plurals.across_accounts, s.count, s.count)
            else stringResource(R.string.excludes_accounts, s.excluded.joinToString(", ") { app.burrow.money.ui.shortName(it.name) }),
        )
        s.groups.forEach { (group, list) ->
            SectionHeader(stringResource(group.label))
            ListGroup {
                list.forEachIndexed { i, a ->
                    ListRow(
                        a.account.name, i, list.size, supporting = accountLine(a.account),
                        lead = { CategoryCircle(a.account.icon, a.account.colorHue) },
                        trailing = {
                            AmountTrailing(
                                when (a.account.type) {
                                    AccountType.CARD -> stringResource(R.string.amount_due, money.plain(-a.balance))
                                    AccountType.LOAN -> money.short(-a.balance)
                                    else -> money.balance(a.balance)
                                },
                            )
                        },
                        onClick = { onOpen(a.account.id) },
                    )
                }
            }
        }
        // Lent and borrowed (86): who owes whom; tap to settle up.
        if (s.people.isNotEmpty()) {
            SectionHeader(stringResource(R.string.lent_and_borrowed), meta = s.people.size.toString())
            ListGroup {
                s.people.forEachIndexed { i, p ->
                    val since = p.since?.let { dates.shortDay(dates.date(it)) }.orEmpty()
                    ListRow(
                        p.person.name, i, s.people.size,
                        supporting = stringResource(if (p.balance > 0) R.string.owes_you_since else R.string.you_owe_since, since),
                        lead = { IconCircle(painterResource(R.drawable.ic_person_duotone), hue = p.person.colorHue) },
                        trailing = { AmountTrailing(money.plain(kotlin.math.abs(p.balance))) },
                        onClick = { settling = p.person.id },
                    )
                }
            }
        }
    }
    val p = s.people.firstOrNull { it.person.id == settling } ?: return
    val owesYou = p.balance > 0
    PayInSheet(
        stringResource(R.string.settle_up_with, p.person.name), stringResource(if (owesYou) R.string.settle_in_line else R.string.settle_out_line, p.person.name),
        kotlin.math.abs(p.balance), money.digitsUnit() * 100, s.payAccounts, { settling = null },
        accountLabel = if (owesYou) R.string.into_account else R.string.from_account, accountLine = R.string.not_spending_or_income,
    ) { account, amount ->
        settling = null
        vm.settle(p.person, account, amount, owesYou) { undo -> snackbar.show(context.getString(R.string.settled), undo) }
    }
}

private enum class AccountSheet { FIX, PAY, PREPAY }

/**
 * One account: bank, cash or wallet (56) with Fix balance and Transfer; card (57) with what is due,
 * since statement, available and Pay bill; loan (58) with EMIs and Prepay; deposit (59) with what
 * it will be at maturity. Then its transactions.
 */
@Composable
fun AccountScreen(vm: AccountViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit, onTransfer: (Long) -> Unit, onOpenTxn: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf<AccountSheet?>(null) }
    val a = s.account
    DetailFrame(onBack, actions = {
        TonalIconButton(painterResource(R.drawable.ic_edit_regular), stringResource(R.string.edit), { onEdit(a.id) })
    }) {
        val icon: @Composable () -> Unit = { CategoryCircle(a.icon, a.colorHue, Size.iconCircleBig) }
        when {
            s.card != null -> {
                val card = s.card
                CenterHeader(icon, a.name, money.plain(card.due), line = card.dueDate?.let { stringResource(R.string.statement_due, dates.weekdayDay(it)) })
                ListGroup {
                    ListRow(
                        money.plain(card.sinceStatement), 0, 2,
                        supporting = card.statementDate?.let { stringResource(R.string.since_statement, dates.shortDay(it)) } ?: stringResource(R.string.since_statement_plain),
                        lead = { IconCircle(painterResource(R.drawable.ic_receipt_long_duotone)) },
                    )
                    val limit = card.limit
                    ListRow(
                        if (limit != null) stringResource(R.string.available, money.short(limit - card.due)) else stringResource(R.string.no_limit), 1, 2,
                        supporting = limit?.let { stringResource(R.string.used_of, Math.round(card.due * 100f / it), money.short(it)) },
                        lead = { IconCircle(painterResource(R.drawable.ic_account_balance_wallet_duotone)) },
                    )
                }
                BurrowButton(
                    stringResource(R.string.pay_bill), { sheet = AccountSheet.PAY },
                    Modifier.fillMaxWidth().padding(top = Space.block), icon = painterResource(R.drawable.ic_payments_regular),
                )
            }
            s.loan != null -> {
                val loan = s.loan
                CenterHeader(icon, a.name, money.short(loan.left), line = loan.ends?.let { stringResource(R.string.left_to_pay_ends, dates.monthYear(it)) } ?: stringResource(R.string.left_to_pay))
                ListGroup {
                    ListRow(
                        stringResource(R.string.emis_paid, loan.paid, loan.total), 0, 2,
                        supporting = stringResource(R.string.a_month, money.plain(loan.emi)),
                        lead = { IconCircle(painterResource(R.drawable.ic_check_circle_duotone)) },
                    )
                    ListRow(
                        stringResource(R.string.interest_left, money.short(loan.interestLeft)), 1, 2,
                        supporting = stringResource(R.string.at_rate, a.interestRate.orEmpty()),
                        lead = { IconCircle(painterResource(R.drawable.ic_percent_duotone)) },
                    )
                }
                SectionHeader(stringResource(R.string.next_emis), trailing = { LinkButton(stringResource(R.string.prepay), { sheet = AccountSheet.PREPAY }) })
                ListGroup {
                    loan.next.forEachIndexed { i, e ->
                        ListRow(
                            stringResource(R.string.emi_n, e.number, dates.shortDay(e.date)), i, loan.next.size,
                            supporting = stringResource(R.string.emi_split, money.plain(e.principal), money.plain(e.interest)),
                            lead = { CategoryCircle(a.icon, a.colorHue) },
                            trailing = { AmountTrailing(money.plain(e.principal + e.interest)) },
                        )
                    }
                }
            }
            s.deposit != null -> {
                val d = s.deposit
                CenterHeader(icon, a.name, money.plain(d.atMaturity), line = d.maturity?.let { stringResource(R.string.at_maturity, dates.fullDay(it)) })
                ListGroup {
                    ListRow(
                        stringResource(R.string.put_in, money.short(d.putIn)), 0, 3, supporting = stringResource(R.string.earned_so_far, money.plain(d.earned)),
                        lead = { IconCircle(painterResource(R.drawable.ic_savings_duotone)) },
                    )
                    ListRow(
                        stringResource(R.string.credit_to, d.payout?.name ?: stringResource(R.string.an_account)), 1, 3,
                        supporting = d.maturity?.let { stringResource(R.string.asks_you_on, dates.fullDay(it)) },
                        lead = { CategoryCircle(d.payout?.icon, d.payout?.colorHue) },
                    )
                    ListRow(
                        stringResource(R.string.remind_week_before), 2, 3,
                        lead = { IconCircle(painterResource(R.drawable.ic_notifications_duotone)) },
                        trailing = { BurrowSwitch(a.remind, vm::setRemind) },
                    )
                }
            }
            else -> {
                CenterHeader(icon, a.name, money.balance(s.balance), line = accountLine(a))
                ListGroup {
                    ListRow(
                        stringResource(R.string.fix_balance), 0, 2, supporting = stringResource(R.string.fix_balance_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_balance_duotone)) }, onClick = { sheet = AccountSheet.FIX },
                    )
                    ListRow(
                        stringResource(R.string.type_transfer), 1, 2, supporting = stringResource(R.string.transfer_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_swap_horiz_duotone)) }, onClick = { onTransfer(a.id) },
                    )
                }
            }
        }
        ListGroup(Modifier.padding(top = Space.block)) {
            ListRow(
                stringResource(R.string.count_in_net_worth), 0, 1,
                supporting = stringResource(
                    when {
                        a.type == AccountType.CARD && !a.includeInNetWorth -> R.string.net_worth_off_card
                        a.type == AccountType.LOAN && !a.includeInNetWorth -> R.string.net_worth_off_loan
                        a.includeInNetWorth -> R.string.included
                        else -> R.string.left_out
                    },
                ),
                lead = { IconCircle(painterResource(R.drawable.ic_scales_duotone)) },
                trailing = { BurrowSwitch(a.includeInNetWorth, vm::setInNetWorth) },
            )
        }
        if (s.deposit == null) {
            val today = LocalDate.now()
            s.txns.groupBy { dates.date(it.txn.at) }.entries.take(30).forEach { (day, list) ->
                SectionHeader(dates.day(day, today))
                ListGroup { list.forEachIndexed { i, t -> TxnRow(t, i, list.size, dates, today, { onOpenTxn(t.txn.id) }) } }
            }
        }
    }
    val unit = money.digitsUnit()
    val payFrom = s.others.filter { it.account.type == AccountType.BANK || it.account.type == AccountType.CASH || it.account.type == AccountType.WALLET }
    when (sheet) {
        AccountSheet.FIX -> FixBalanceSheet(s.balance, { sheet = null }) { actual ->
            sheet = null
            vm.fixBalance(actual) { undo -> snackbar.show(context.getString(R.string.balance_fixed), undo) }
        }
        AccountSheet.PAY -> PayInSheet(stringResource(R.string.pay_bill), a.name, (s.card?.due ?: 0).coerceAtLeast(unit), unit * 1_000, payFrom, { sheet = null }) { from, amount ->
            sheet = null
            vm.payIn(from, amount) { undo -> snackbar.show(context.getString(R.string.paid), undo) }
        }
        AccountSheet.PREPAY -> PayInSheet(stringResource(R.string.prepay), a.name, unit * 50_000, unit * 10_000, payFrom, { sheet = null }) { from, amount ->
            sheet = null
            vm.payIn(from, amount) { undo -> snackbar.show(context.getString(R.string.prepaid), undo) }
        }
        null -> Unit
    }
}

/** Fix balance: type what the bank says; one adjustment row makes it match. */
@Composable
private fun FixBalanceSheet(current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val money = LocalMoney.current
    var text by rememberSaveable { mutableStateOf(java.math.BigDecimal.valueOf(current, money.digits).stripTrailingZeros().toPlainString()) }
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.fix_balance), onDismiss, stringResource(R.string.fix_balance_line))
        SheetBody {
            PillTextField(
                text, { v -> text = v.filter { it.isDigit() || it == '.' || it == '-' } }, "0",
                leading = painterResource(currencyIcon(money.currency)), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Decimal,
            )
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
            BurrowButton(stringResource(R.string.save), {
                val negative = text.startsWith("-")
                val v = app.burrow.money.domain.TypedAmount.parse(text, money.digits)
                onSave(if (negative) -v else v)
            }, Modifier.weight(1f), height = Size.buttonBottom)
        }
    }
}

/** Pay bill (card) and Prepay (loan): an amount and the account it comes from. */
@Composable
private fun PayInSheet(
    title: String,
    line: String,
    start: Long,
    step: Long,
    from: List<AccountWithBalance>,
    onDismiss: () -> Unit,
    accountLabel: Int = R.string.from_account,
    accountLine: Int = R.string.a_transfer_not_spending,
    onPay: (Long, Long) -> Unit,
) {
    var amount by rememberSaveable { mutableLongStateOf(start) }
    var fromId by rememberSaveable { mutableStateOf(from.firstOrNull()?.account?.id) }
    var picking by remember { mutableStateOf(false) }
    val account = from.firstOrNull { it.account.id == fromId }
    if (picking) {
        AccountSheet(from, fromId, { fromId = it.account.id; picking = false }, { picking = false })
        return
    }
    AmountStepperSheet(
        title = title, line = line, amount = amount, onAmount = { amount = it }, step = step,
        chips = listOf(start, start + step, start + 2 * step).distinct(),
        confirm = title, onConfirm = { fromId?.let { onPay(it, amount) } }, onDismiss = onDismiss,
        extra = {
            ListGroup {
                ListRow(
                    stringResource(accountLabel, account?.account?.name.orEmpty()), 0, 1,
                    supporting = stringResource(accountLine),
                    lead = { CategoryCircle(account?.account?.icon, account?.account?.colorHue) },
                    onClick = { picking = true },
                )
            }
        },
    )
}

private val TYPES = listOf(AccountType.BANK, AccountType.CASH, AccountType.WALLET, AccountType.CARD, AccountType.LOAN, AccountType.SAVINGS)

private enum class FormSheet { STATEMENT, DUE, MATURITY }

/** New account / edit account: type, name, balance, last digits, and the type's own details. */
@Composable
fun NewAccountScreen(vm: NewAccountViewModel, onClose: () -> Unit, onSaved: () -> Unit) {
    val formState by vm.form.collectAsStateWithLifecycle()
    val f = formState ?: return
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    val dates = rememberDateText()
    var sheet by rememberSaveable { mutableStateOf<FormSheet?>(null) }
    val typeLabels = TYPES.associateWith { accountTypeLabel(it) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(
            painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose,
            title = stringResource(if (vm.isEdit) R.string.edit_account else R.string.new_account),
        ) {
            BurrowButton(stringResource(R.string.save), { vm.save(onSaved) }, kind = if (vm.canSave(f)) ButtonKind.FILLED else ButtonKind.OFF)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            if (!vm.isEdit) {
                LabeledField(stringResource(R.string.kind)) {
                    ChipWrap(TYPES, { typeLabels.getValue(it) }, { it == f.type }, { t -> vm.edit { it.copy(type = t) } })
                }
            }
            LabeledField(stringResource(R.string.name)) {
                PillTextField(f.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(R.string.account_name_hint), leading = painterResource(R.drawable.ic_edit_note_regular), clearLabel = stringResource(R.string.clear))
            }
            if (!vm.isEdit) {
                val label = when (f.type) {
                    AccountType.CARD -> R.string.amount_due_now
                    AccountType.LOAN -> R.string.left_to_pay_now
                    AccountType.SAVINGS -> R.string.put_in_label
                    else -> R.string.balance_now
                }
                LabeledField(stringResource(label)) {
                    PillTextField(f.balance, { v -> vm.edit { it.copy(balance = v.filter { ch -> ch.isDigit() || ch == '.' }) } }, "0", leading = painterResource(currencyIcon(money.currency)), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Decimal)
                }
            }
            if (f.type != AccountType.CASH) {
                LabeledField(stringResource(R.string.last_digits)) {
                    PillTextField(f.number, { v -> vm.edit { it.copy(number = v.filter { ch -> ch.isDigit() }.take(app.burrow.money.ui.accounts.MAX_DIGITS)) } }, "1234", leading = painterResource(R.drawable.ic_pin_regular), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Number)
                }
            }
            when (f.type) {
                AccountType.CARD -> {
                    LabeledField(stringResource(R.string.credit_limit)) {
                        PillTextField(f.limit, { v -> vm.edit { it.copy(limit = v.filter { ch -> ch.isDigit() || ch == '.' }) } }, "0", leading = painterResource(currencyIcon(money.currency)), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Decimal)
                    }
                    ListGroup {
                        ListRow(stringResource(R.string.every_month_on, dates.ordinal(f.statementDay)), 0, 2, supporting = stringResource(R.string.statement_day), lead = { IconCircle(painterResource(R.drawable.ic_receipt_long_duotone)) }, onClick = { sheet = FormSheet.STATEMENT })
                        ListRow(stringResource(R.string.every_month_on, dates.ordinal(f.dueDay)), 1, 2, supporting = stringResource(R.string.due_day), lead = { IconCircle(painterResource(R.drawable.ic_calendar_view_month_duotone)) }, onClick = { sheet = FormSheet.DUE })
                    }
                }
                AccountType.LOAN -> {
                    LabeledField(stringResource(R.string.emi_label)) {
                        PillTextField(f.emi, { v -> vm.edit { it.copy(emi = v.filter { ch -> ch.isDigit() || ch == '.' }) } }, "0", leading = painterResource(currencyIcon(money.currency)), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Decimal)
                    }
                    RateField(f.rate) { r -> vm.edit { it.copy(rate = r) } }
                }
                AccountType.SAVINGS -> {
                    RateField(f.rate) { r -> vm.edit { it.copy(rate = r) } }
                    ListGroup {
                        ListRow(f.maturity?.let { dates.fullDay(it) } ?: stringResource(R.string.no_date), 0, 1, supporting = stringResource(R.string.matures_on), lead = { IconCircle(painterResource(R.drawable.ic_calendar_view_month_duotone)) }, onClick = { sheet = FormSheet.MATURITY })
                    }
                }
            }
            if (f.tried && !vm.canSave(f)) Text(stringResource(R.string.account_cant_save), style = app.burrow.money.ui.theme.BurrowType.body, color = c.error)
        }
    }
    when (sheet) {
        FormSheet.STATEMENT, FormSheet.DUE -> {
            var day by rememberSaveable { mutableIntStateOf(if (sheet == FormSheet.STATEMENT) f.statementDay else f.dueDay) }
            BurrowSheet({ sheet = null }) {
                SheetHeader(stringResource(if (sheet == FormSheet.STATEMENT) R.string.statement_day else R.string.due_day), { sheet = null })
                DayStepper(
                    day = day, onDay = { day = it }, quick = listOf(1, 5, 10, 15, 25), resultLine = stringResource(R.string.every_month_on, dates.ordinal(day)),
                    minus = painterResource(R.drawable.ic_remove_regular), minusLabel = stringResource(R.string.key_minus),
                    plus = painterResource(R.drawable.ic_add_regular), plusLabel = stringResource(R.string.key_plus),
                    onDone = {
                        val d = day
                        if (sheet == FormSheet.STATEMENT) vm.edit { it.copy(statementDay = d) } else vm.edit { it.copy(dueDay = d) }
                        sheet = null
                    },
                    doneLabel = stringResource(R.string.done), ordinal = dates::ordinal, max = 31,
                )
            }
        }
        FormSheet.MATURITY -> DateSheet(f.maturity ?: LocalDate.now().plusYears(1), { d -> vm.edit { it.copy(maturity = d) }; sheet = null }, { sheet = null }, future = true, title = stringResource(R.string.matures_on))
        null -> Unit
    }
}

@Composable
private fun RateField(rate: String, onRate: (String) -> Unit) {
    LabeledField(stringResource(R.string.interest_rate)) {
        PillTextField(rate, { v -> onRate(v.filter { it.isDigit() || it == '.' }) }, "8.5", leading = painterResource(R.drawable.ic_percent_regular), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Decimal)
    }
}
