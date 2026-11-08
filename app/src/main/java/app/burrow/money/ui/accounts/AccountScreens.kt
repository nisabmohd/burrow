package app.burrow.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
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
import app.burrow.money.data.CardEntity
import androidx.compose.ui.focus.focusRequester
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.AmountTrailing
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.ChipWrap
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

/** "RuPay ••1234" (a card made before cards had names: "Card ••1234"). */
@Composable
fun cardLabel(c: CardEntity): String = stringResource(R.string.card_digits, c.name.ifEmpty { stringResource(R.string.card_unnamed) }, c.number)

/** "RuPay ••1234 · Visa ••5678": a bill's cards (186, 193). */
@Composable
fun cardsLine(cards: List<CardEntity>): String = cards.map { cardLabel(it) }.joinToString(" · ")

/** Add account (190): the kinds, each opening its form; Credit card opens New credit card (191/192). */
@Composable
private fun AddAccountSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val kinds = listOf(
        Triple(AccountType.BANK, R.string.add_bank_account, R.string.add_bank_account_line) to R.drawable.ic_account_balance_duotone,
        Triple(AccountType.CARD, R.string.account_card, R.string.add_card_line) to R.drawable.ic_credit_card_duotone,
        Triple(AccountType.CASH, R.string.cash_or_wallet, R.string.cash_or_wallet_line) to R.drawable.ic_account_balance_wallet_duotone,
        Triple(AccountType.LOAN, R.string.account_loan, R.string.add_loan_line) to R.drawable.ic_real_estate_agent_duotone,
        Triple(AccountType.SAVINGS, R.string.account_savings, R.string.add_savings_line) to R.drawable.ic_savings_duotone,
    )
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.add_account), onDismiss)
        SheetBody {
            ListGroup {
                kinds.forEachIndexed { i, (k, icon) ->
                    ListRow(
                        stringResource(k.second), i, kinds.size, supporting = stringResource(k.third),
                        lead = { IconCircle(painterResource(icon)) }, trailing = { app.burrow.money.ui.components.Chevron() },
                        onClick = { onPick(k.first) },
                    )
                }
            }
        }
    }
}

/**
 * Add another card on this bill (189), and a card's name and digits in Edit account (188): Card name,
 * Last 4 digits ("Used to match its SMS"), then the button, filled once both are there.
 */
@Composable
fun CardSheet(
    title: String,
    line: String?,
    confirm: String,
    onDismiss: () -> Unit,
    start: CardDraft? = null,
    onSave: (name: String, number: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(start?.name.orEmpty()) }
    var digits by rememberSaveable { mutableStateOf(start?.number.orEmpty()) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val ok = name.isNotBlank() && digits.length == 4
    BurrowSheet(onDismiss) {
        SheetHeader(title, onDismiss, line)
        SheetBody {
            LabeledField(stringResource(R.string.card_name)) {
                PillTextField(
                    name, { name = it }, stringResource(R.string.card_name_hint_same), Modifier.focusRequester(focus),
                    leading = painterResource(R.drawable.ic_credit_card_regular), clearLabel = stringResource(R.string.clear),
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                )
            }
            LabeledField(stringResource(R.string.last_4_digits)) {
                PillTextField(
                    digits, { v -> digits = v.filter { it.isDigit() }.take(4) }, stringResource(R.string.used_to_match_sms),
                    leading = painterResource(R.drawable.ic_pin_regular), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Number,
                )
            }
        }
        app.burrow.money.ui.components.SheetFooter {
            BurrowButton(
                confirm, { if (ok) onSave(name.trim(), digits) }, Modifier.fillMaxWidth(),
                kind = if (ok) ButtonKind.FILLED else ButtonKind.OFF, height = Size.buttonBottom,
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (start == null) focus.requestFocus() }
}

/** Accounts (55): net worth, then Bank and cash, Cards, Loans, Savings. */
@Composable
fun AccountsScreen(vm: AccountsViewModel, onBack: () -> Unit, onAdd: (String) -> Unit, onOpen: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    var settling by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    DetailFrame(onBack, stringResource(R.string.accounts), actions = {
        TonalIconButton(painterResource(R.drawable.ic_add_regular), stringResource(R.string.add_account), { adding = true })
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
                        a.account.name, i, list.size,
                        // Two or more cards on one bill (193): "RuPay ••1234 · Visa ••5678".
                        supporting = s.cards[a.account.id]?.takeIf { it.size > 1 }?.let { cardsLine(it) } ?: accountLine(a.account),
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
    if (adding) AddAccountSheet({ type -> adding = false; onAdd(type) }, { adding = false })
    val p = s.people.firstOrNull { it.person.id == settling } ?: return
    val owesYou = p.balance > 0
    PayInSheet(
        stringResource(R.string.settle_up_with, p.person.name), stringResource(if (owesYou) R.string.settle_in_line else R.string.settle_out_line, p.person.name),
        kotlin.math.abs(p.balance), s.payAccounts, { settling = null },
        accountLabel = if (owesYou) R.string.into_account else R.string.from_account, accountLine = R.string.not_spending_or_income,
        chips = listOf(stringResource(R.string.all_of_it) to kotlin.math.abs(p.balance)),
    ) { account, amount, _ ->
        settling = null
        vm.settle(p.person, account, amount, owesYou) { undo -> snackbar.show(context.getString(R.string.settled), undo) }
    }
}

private enum class AccountSheet { FIX, PAY, PREPAY, ADD_CARD }

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
                // Two or more cards (186): their names and digits, then the one due date.
                // One card says which too (199: "Card ••5521 · due Tue, 28 Oct").
                val line = if (s.cards.isNotEmpty()) {
                    card.dueDate?.let { stringResource(R.string.cards_due, cardsLine(s.cards), dates.weekdayDay(it)) } ?: cardsLine(s.cards)
                } else card.dueDate?.let { stringResource(R.string.statement_due, dates.weekdayDay(it)) }
                CenterHeader(icon, a.name, money.plain(card.due), line = line)
                ListGroup {
                    ListRow(
                        money.plain(card.sinceStatement), 0, 2,
                        supporting = card.statementDate?.let { stringResource(R.string.since_statement, dates.shortDay(it)) } ?: stringResource(R.string.since_statement_plain),
                        lead = { IconCircle(painterResource(R.drawable.ic_receipt_long_duotone)) },
                    )
                    // On a shared line, what is left is the line's: every card on it uses the same limit.
                    val limit = card.limit
                    val used = limit?.let { stringResource(R.string.used_of, Math.round(card.lineDue * 100f / it), money.short(it)) }
                    ListRow(
                        if (limit != null) stringResource(R.string.available, money.short((limit - card.lineDue).coerceAtLeast(0))) else stringResource(R.string.no_limit), 1, 2,
                        supporting = if (card.sharedWith.isNotEmpty()) listOfNotNull(used, stringResource(R.string.shared_with, card.sharedWith.joinToString(", "))).joinToString(" · ") else used,
                        lead = { IconCircle(painterResource(R.drawable.ic_account_balance_wallet_duotone)) },
                    )
                }
                BurrowButton(
                    stringResource(R.string.pay_bill), { sheet = AccountSheet.PAY },
                    Modifier.fillMaxWidth().padding(top = Space.block), icon = painterResource(R.drawable.ic_payments_regular),
                )
                // Cards on this bill (186): one statement, one due; messages from any of them come here.
                SectionHeader(stringResource(R.string.cards_on_this_bill), meta = if (s.cards.size > 1) stringResource(R.string.one_statement_one_due) else null)
                ListGroup {
                    val n = s.cards.size + 1
                    s.cards.forEachIndexed { i, c ->
                        val count = s.cardCounts[c.id] ?: 0
                        ListRow(
                            cardLabel(c), i, n,
                            // One card: no payment says which card it was, so no count either.
                            supporting = if (s.cards.size > 1) pluralStringResource(R.plurals.card_payments_month, count, count) else accountTypeLabel(a.type),
                            lead = { IconCircle(painterResource(R.drawable.ic_credit_card_duotone)) },
                        )
                    }
                    ListRow(
                        stringResource(R.string.add_another_card), n - 1, n, supporting = stringResource(R.string.add_another_card_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_add_regular)) }, onClick = { sheet = AccountSheet.ADD_CARD },
                    )
                }
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
        AccountSheet.FIX -> FixBalanceSheet(a.name, s.balance, { sheet = null }) { actual ->
            sheet = null
            vm.fixBalance(actual) { undo -> snackbar.show(context.getString(R.string.balance_fixed), undo) }
        }
        AccountSheet.PAY -> {
            val due = (s.card?.due ?: 0).coerceAtLeast(0)
            PayInSheet(
                stringResource(R.string.pay_card_bill), stringResource(R.string.pay_card_line, a.name, money.plain(due)), due.coerceAtLeast(unit), payFrom, { sheet = null },
                // Statement and Since statement (141): pay what the bill says, or everything spent since.
                chips = listOfNotNull(
                    due.takeIf { it > 0 }?.let { stringResource(R.string.statement_chip, money.plain(it)) to it },
                    s.card?.sinceStatement?.takeIf { it > 0 }?.let { stringResource(R.string.since_statement_chip, money.short(it)) to it },
                ),
            ) { from, amount, day ->
                sheet = null
                vm.payIn(from, amount, day) { undo -> snackbar.show(context.getString(R.string.paid), undo) }
            }
        }
        AccountSheet.PREPAY -> {
            val loan = s.loan
            PayInSheet(
                stringResource(R.string.prepay),
                if (loan != null && loan.total > 0) stringResource(R.string.emi_line, a.name, loan.paid + 1, loan.total) else a.name,
                loan?.emi?.takeIf { it > 0 } ?: unit * 50_000, payFrom, { sheet = null },
                chips = loan?.emi?.takeIf { it > 0 }?.let { listOf(stringResource(R.string.emi_chip, money.plain(it)) to it) }.orEmpty(),
            ) { from, amount, day ->
                sheet = null
                vm.payIn(from, amount, day) { undo -> snackbar.show(context.getString(R.string.prepaid), undo) }
            }
        }
        AccountSheet.ADD_CARD -> CardSheet(
            stringResource(R.string.add_another_card), stringResource(R.string.add_another_card_sheet_line, a.name), stringResource(R.string.add_card),
            { sheet = null },
        ) { name, number ->
            sheet = null
            vm.addCard(name, number) { snackbar.show(context.getString(R.string.card_added_to, name, a.name)) }
        }
        null -> Unit
    }
}

/**
 * Fix balance (140): what the bank shows, typed in the value sheet; the line under it says how far
 * off the app is and the one adjustment that fixes it. A card or loan (negative balance) keeps its sign.
 */
@Composable
private fun FixBalanceSheet(name: String, current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val money = LocalMoney.current
    val sign = if (current < 0) -1 else 1
    app.burrow.money.ui.components.AmountSheet(
        title = stringResource(R.string.fix_balance),
        line = stringResource(R.string.fix_balance_shows, name, money.plain(current)),
        label = stringResource(R.string.your_bank_shows),
        amount = kotlin.math.abs(current),
        below = { v ->
            val diff = sign * v - current
            when {
                diff < 0 -> stringResource(R.string.fix_less, money.plain(-diff), money.signed(diff))
                diff > 0 -> stringResource(R.string.fix_more, money.plain(diff), money.signed(diff))
                else -> stringResource(R.string.fix_same)
            }
        },
        confirm = stringResource(R.string.save), cancel = true,
        onDone = { onSave(sign * it) }, onDismiss = onDismiss,
    )
}

/**
 * Pay card bill (141), Prepay / EMI (142), Settle up: the value sheet with quick amounts, the account
 * it comes from (a transfer, not spending) and the date, Cancel / Save.
 */
@Composable
private fun PayInSheet(
    title: String,
    line: String,
    start: Long,
    from: List<AccountWithBalance>,
    onDismiss: () -> Unit,
    accountLabel: Int = R.string.from_account,
    accountLine: Int = R.string.a_transfer_not_spending,
    chips: List<Pair<String, Long>> = emptyList(),
    onPay: (Long, Long, LocalDate) -> Unit,
) {
    val money = LocalMoney.current
    val dates = app.burrow.money.ui.rememberDateText()
    var amount by rememberSaveable { mutableLongStateOf(start) }
    var fromId by rememberSaveable { mutableStateOf(from.firstOrNull()?.account?.id) }
    var day by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val account = from.firstOrNull { it.account.id == fromId }
    when (picking) {
        "account" -> { AccountSheet(from, fromId, { fromId = it.account.id; picking = null }, { picking = null }); return }
        "date" -> { app.burrow.money.ui.pickers.DateSheet(day, { day = it; picking = null }, { picking = null }); return }
    }
    app.burrow.money.ui.components.AmountSheet(
        title = title, line = line, amount = amount, onChange = { amount = it }, chips = chips,
        confirm = stringResource(R.string.save), cancel = true, min = 1,
        onDone = { a -> fromId?.let { onPay(it, a, day) } }, onDismiss = onDismiss,
        rows = {
            ListGroup {
                ListRow(
                    stringResource(accountLabel, account?.account?.name.orEmpty()), 0, 2,
                    supporting = listOfNotNull(account?.let { money.plain(it.balance) }, stringResource(accountLine)).joinToString(" · "),
                    lead = { CategoryCircle(account?.account?.icon, account?.account?.colorHue) },
                    onClick = { picking = "account" },
                )
                ListRow(
                    dates.day(day), 1, 2, supporting = stringResource(R.string.date),
                    lead = { IconCircle(painterResource(R.drawable.ic_schedule_duotone)) },
                    onClick = { picking = "date" },
                )
            }
        },
    )
}

private val TYPES = listOf(AccountType.BANK, AccountType.CASH, AccountType.WALLET, AccountType.CARD, AccountType.LOAN, AccountType.SAVINGS)

private enum class FormSheet { STATEMENT, DUE, MATURITY, LIMIT, LIMIT_AMOUNT, DUE_AMOUNT, BANK, CARD, CARD_NAME }

/**
 * New account / edit account: type, name, balance, last digits, and the type's own details.
 * A new credit card (191/192) starts with its bank: with a card from that bank already, Same bill adds
 * it to that card's bill (name and digits only); Its own bill makes a new card that shares the limit.
 * Editing a card (188) lists the cards on its bill instead of one set of digits.
 */
@Composable
fun NewAccountScreen(vm: NewAccountViewModel, onClose: () -> Unit, onSaved: (String?) -> Unit, onArchived: (suspend () -> Unit) -> Unit = {}) {
    val formState by vm.form.collectAsStateWithLifecycle()
    val otherCards by vm.otherCards.collectAsStateWithLifecycle()
    val banks by vm.banks.collectAsStateWithLifecycle()
    val accountNames by vm.accountNames.collectAsStateWithLifecycle()
    val f = formState ?: return
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    val dates = rememberDateText()
    var sheet by rememberSaveable { mutableStateOf<FormSheet?>(null) }
    var editingCard by rememberSaveable { mutableIntStateOf(-1) }
    val typeLabels = TYPES.associateWith { accountTypeLabel(it) }
    val newCard = !vm.isEdit && f.type == AccountType.CARD
    val joins = if (newCard) vm.cardOfBank(f, otherCards) else null
    val sameBill = joins != null && f.sameBill
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(
            painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose,
            title = when {
                sameBill -> stringResource(R.string.add_card_to, app.burrow.money.ui.shortName(joins!!.name))
                newCard -> stringResource(R.string.new_credit_card)
                vm.isEdit -> stringResource(R.string.edit_account)
                else -> stringResource(R.string.new_account)
            },
        ) {
            BurrowButton(stringResource(R.string.save), { vm.save(onSaved) }, kind = if (vm.canSave(f)) ButtonKind.FILLED else ButtonKind.OFF)
        }
        // A card's statement and due days, and its limit (own, or another card's).
        val cardDays: @Composable () -> Unit = {
            ListGroup {
                ListRow(stringResource(R.string.statement_on, dates.ordinal(f.statementDay)), 0, 2, supporting = stringResource(R.string.statement_day), lead = { IconCircle(painterResource(R.drawable.ic_receipt_long_duotone)) }, onClick = { sheet = FormSheet.STATEMENT })
                ListRow(stringResource(R.string.card_due_on, dates.ordinal(f.dueDay)), 1, 2, supporting = stringResource(R.string.due_day), lead = { IconCircle(painterResource(R.drawable.ic_calendar_view_month_duotone)) }, onClick = { sheet = FormSheet.DUE })
            }
            // Several cards on one credit line: this one uses another card's limit instead of its own.
            val sharedCard = otherCards.firstOrNull { it.id == f.sharesLimitWith }
            if (otherCards.isNotEmpty()) ListGroup {
                ListRow(
                    if (newCard && sharedCard != null) stringResource(R.string.shares_limit_row, sharedCard.name) else stringResource(R.string.credit_limit), 0, 1,
                    supporting = when {
                        newCard && sharedCard != null -> stringResource(R.string.shares_limit_tap)
                        sharedCard != null -> stringResource(R.string.shares_limit_of, sharedCard.name)
                        else -> stringResource(R.string.own_limit)
                    },
                    lead = { IconCircle(painterResource(R.drawable.ic_credit_card_duotone)) },
                    onClick = { sheet = FormSheet.LIMIT },
                )
            }
            if (sharedCard == null) LabeledField(stringResource(R.string.credit_limit)) {
                app.burrow.money.ui.components.AmountField(f.limit, { v -> vm.edit { it.copy(limit = v) } }, stringResource(R.string.credit_limit))
            }
        }
        app.burrow.money.ui.components.BlockColumn(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
        ) {
            if (newCard) {
                // The bank (191/192): a card from a bank you already have a card from may join that card's bill.
                // Names only, no lead: a letter or a generic icon looks like a placeholder (196, user 2026-11-08).
                app.burrow.money.ui.components.SelectField(stringResource(R.string.bank), f.bank, stringResource(R.string.choose_bank), { sheet = FormSheet.BANK })
                if (joins != null) {
                    SectionHeader(stringResource(R.string.you_already_have, joins.name))
                    ListGroup {
                        app.burrow.money.ui.components.PickRow(
                            stringResource(R.string.same_bill), 0, 2, f.sameBill, { vm.edit { it.copy(sameBill = true) } },
                            supporting = stringResource(R.string.same_bill_line, joins.name),
                            lead = { IconCircle(painterResource(R.drawable.ic_receipt_long_duotone)) },
                        )
                        app.burrow.money.ui.components.PickRow(
                            stringResource(R.string.own_bill), 1, 2, !f.sameBill, { vm.edit { it.copy(sameBill = false, sharesLimitWith = it.sharesLimitWith ?: joins.id) } },
                            supporting = stringResource(R.string.own_bill_line),
                            lead = { IconCircle(painterResource(R.drawable.ic_credit_card_duotone)) },
                        )
                    }
                }
                LabeledField(stringResource(R.string.card_name)) {
                    PillTextField(
                        f.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(if (sameBill) R.string.card_name_hint_same else R.string.card_name_hint_own),
                        leading = painterResource(R.drawable.ic_credit_card_regular), clearLabel = stringResource(R.string.clear),
                    )
                }
                LabeledField(stringResource(R.string.last_4_digits)) {
                    PillTextField(
                        f.number, { v -> vm.edit { it.copy(number = v.filter { ch -> ch.isDigit() }.take(4)) } }, stringResource(R.string.used_to_match_sms),
                        leading = painterResource(R.drawable.ic_pin_regular), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Number,
                    )
                }
                if (sameBill) {
                    Text(
                        stringResource(R.string.same_bill_note, joins!!.name), style = app.burrow.money.ui.theme.BurrowType.body, color = c.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.headingInset),
                    )
                    return@BlockColumn
                }
                // 192/198: statement, due and limit in one group, then what is due now; each opens its sheet.
                val sharedCard = otherCards.firstOrNull { it.id == f.sharesLimitWith }
                val limit = app.burrow.money.domain.AmountInput(f.limit, money.digits).minor()
                val due = app.burrow.money.domain.AmountInput(f.balance, money.digits).minor()
                ListGroup {
                    ListRow(stringResource(R.string.statement_on, dates.ordinal(f.statementDay)), 0, 3, supporting = stringResource(R.string.statement_day), lead = { IconCircle(painterResource(R.drawable.ic_receipt_long_duotone)) }, onClick = { sheet = FormSheet.STATEMENT })
                    ListRow(stringResource(R.string.card_due_on, dates.ordinal(f.dueDay)), 1, 3, supporting = stringResource(R.string.due_day), lead = { IconCircle(painterResource(R.drawable.ic_calendar_view_month_duotone)) }, onClick = { sheet = FormSheet.DUE })
                    ListRow(
                        when {
                            sharedCard != null -> stringResource(R.string.shares_limit_row, sharedCard.name)
                            limit > 0 -> money.plain(limit)
                            else -> stringResource(R.string.credit_limit)
                        }, 2, 3,
                        supporting = when {
                            sharedCard != null -> stringResource(R.string.shares_limit_tap)
                            limit > 0 -> stringResource(R.string.credit_limit)
                            else -> stringResource(R.string.type_the_limit)
                        },
                        lead = { IconCircle(painterResource(if (sharedCard != null) R.drawable.ic_credit_card_duotone else R.drawable.ic_currency_rupee_duotone)) },
                        onClick = { sheet = if (otherCards.isNotEmpty()) FormSheet.LIMIT else FormSheet.LIMIT_AMOUNT },
                    )
                }
                ListGroup {
                    ListRow(
                        if (due > 0) money.plain(due) else stringResource(R.string.nothing_due_now), 0, 1, supporting = stringResource(R.string.amount_due_now),
                        lead = { IconCircle(painterResource(R.drawable.ic_payments_duotone)) }, onClick = { sheet = FormSheet.DUE_AMOUNT },
                    )
                }
            } else {
                // A preview of how it looks in lists (106): icon in its colour, name with the last digits, kind.
                Column(Modifier.fillMaxWidth().padding(top = Space.small), horizontalAlignment = Alignment.CenterHorizontally) {
                    app.burrow.money.ui.pickers.CategoryCircle(f.shownIcon, f.hue, Size.iconCircleBig)
                    Spacer(Modifier.height(Space.block))
                    Text(
                        listOfNotNull(f.name.ifBlank { stringResource(R.string.new_account) }, f.number.takeIf { it.isNotBlank() }?.let { "••$it" }).joinToString(" "),
                        style = app.burrow.money.ui.theme.BurrowType.titleSmall, color = c.onSurface,
                    )
                    Text(typeLabels.getValue(f.type), style = app.burrow.money.ui.theme.BurrowType.body, color = c.onSurfaceVariant)
                }
                // Add account (190) picked the kind; only Cash or wallet still chooses between the two.
                val kinds = when {
                    vm.isEdit -> emptyList()
                    vm.startType == null -> TYPES
                    f.type == AccountType.CASH || f.type == AccountType.WALLET -> listOf(AccountType.CASH, AccountType.WALLET)
                    else -> emptyList()
                }
                if (kinds.isNotEmpty()) {
                    LabeledField(stringResource(R.string.kind)) {
                        ChipWrap(kinds, { typeLabels.getValue(it) }, { it == f.type }, { t -> vm.edit { it.copy(type = t) } })
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
                        app.burrow.money.ui.components.AmountField(f.balance, { v -> vm.edit { it.copy(balance = v) } }, stringResource(label))
                    }
                }
                // A card's digits are its cards' (188), listed under Icon.
                if (f.type != AccountType.CASH && f.type != AccountType.CARD) {
                    LabeledField(stringResource(R.string.last_digits)) {
                        PillTextField(f.number, { v -> vm.edit { it.copy(number = v.filter { ch -> ch.isDigit() }.take(app.burrow.money.ui.accounts.MAX_DIGITS)) } }, "1234", leading = painterResource(R.drawable.ic_pin_regular), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Number)
                    }
                }
                when (f.type) {
                    AccountType.LOAN -> {
                        LabeledField(stringResource(R.string.emi_label)) {
                            app.burrow.money.ui.components.AmountField(f.emi, { v -> vm.edit { it.copy(emi = v) } }, stringResource(R.string.emi_label))
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
            }
            SectionHeader(stringResource(R.string.colour))
            app.burrow.money.ui.components.HuePicker(f.hue, stringResource(R.string.colour), { h -> vm.edit { it.copy(hue = h) } })
            SectionHeader(stringResource(R.string.icon))
            // A new card picks from the money icons only (198).
            app.burrow.money.ui.components.IconPicker(if (newCard) ACCOUNT_ICONS.take(6) else ACCOUNT_ICONS, f.shownIcon, f.hue, { i -> vm.edit { it.copy(icon = i) } })
            if (vm.isEdit && f.type == AccountType.CARD) {
                // Cards on this bill (188): ✕ removes one while two or more are left; a row opens its name and digits.
                run {
                    SectionHeader(stringResource(R.string.cards_on_this_bill))
                    ListGroup {
                        val n = f.cards.size + 1
                        f.cards.forEachIndexed { i, card ->
                            val label = card.name.ifEmpty { stringResource(R.string.card_unnamed) }
                            // A card from before names (200): "Card ••5521" with "Tap to name it" in primary.
                            val unnamed = card.name.isEmpty()
                            ListRow(
                                if (unnamed) stringResource(R.string.card_digits, label, card.number) else label, i, n,
                                supporting = if (unnamed) stringResource(R.string.tap_to_name_it) else "••${card.number}",
                                supportingColor = if (unnamed) c.primary else c.onSurfaceVariant,
                                lead = { IconCircle(painterResource(R.drawable.ic_credit_card_duotone)) },
                                trailing = if (f.cards.size > 1) {
                                    { TonalIconButton(painterResource(R.drawable.ic_close_regular), stringResource(R.string.remove_named, label), { vm.setCards(f.cards - card) }) }
                                } else null,
                                onClick = { editingCard = i; sheet = FormSheet.CARD_NAME },
                            )
                        }
                        ListRow(
                            stringResource(R.string.add_another_card), n - 1, n, supporting = stringResource(R.string.add_another_card_edit_line),
                            lead = { IconCircle(painterResource(R.drawable.ic_add_regular)) }, onClick = { editingCard = -1; sheet = FormSheet.CARD },
                        )
                    }
                }
                cardDays()
            }
            ListGroup {
                val rows = if (vm.isEdit) 2 else 1
                ListRow(
                    stringResource(R.string.count_in_net_worth), 0, rows,
                    supporting = stringResource(
                        when {
                            f.type == AccountType.CARD && !f.countsInNetWorth -> R.string.net_worth_off_card
                            f.type == AccountType.LOAN && !f.countsInNetWorth -> R.string.net_worth_off_loan
                            f.countsInNetWorth -> R.string.included
                            else -> R.string.not_included
                        },
                    ),
                    lead = { IconCircle(painterResource(R.drawable.ic_account_balance_duotone)) },
                    trailing = { app.burrow.money.ui.components.BurrowSwitch(f.countsInNetWorth, { on -> vm.edit { it.copy(inNetWorth = on) } }) },
                )
                if (vm.isEdit) {
                    ListRow(
                        stringResource(R.string.opening_balance), 1, rows,
                        supporting = stringResource(R.string.opening_balance_line, money.plain(f.opening)),
                        lead = { IconCircle(painterResource(R.drawable.ic_payments_duotone)) },
                    )
                }
            }
            if (vm.isEdit) {
                BurrowButton(stringResource(R.string.archive_account), { vm.archive(onArchived) }, Modifier.fillMaxWidth(), ButtonKind.DANGER, painterResource(R.drawable.ic_delete_regular))
            }
            if (f.tried && !vm.canSave(f)) Text(stringResource(R.string.account_cant_save), style = app.burrow.money.ui.theme.BurrowType.body, color = c.error)
        }
    }
    when (sheet) {
        FormSheet.STATEMENT, FormSheet.DUE -> {
            val statement = sheet == FormSheet.STATEMENT
            app.burrow.money.ui.components.DaySheet(
                title = stringResource(if (statement) R.string.statement_day else R.string.due_day),
                day = if (statement) f.statementDay else f.dueDay,
                onDone = { d -> if (statement) vm.edit { it.copy(statementDay = d) } else vm.edit { it.copy(dueDay = d) }; sheet = null },
                onDismiss = { sheet = null }, ordinal = dates::ordinal, quick = listOf(1, 5, 10, 15, 25), max = 31,
                result = { d -> stringResource(R.string.every_month_on, dates.ordinal(d)) },
            )
        }
        FormSheet.LIMIT -> BurrowSheet({ sheet = null }) {
            SheetHeader(stringResource(R.string.credit_limit), { sheet = null }, stringResource(R.string.shared_limit_line))
            SheetBody {
                ListGroup {
                    val n = otherCards.size + 1
                    app.burrow.money.ui.components.PickRow(
                        stringResource(R.string.own_limit), 0, n, f.sharesLimitWith == null,
                        { vm.edit { it.copy(sharesLimitWith = null) }; sheet = if (newCard) FormSheet.LIMIT_AMOUNT else null },
                        supporting = stringResource(R.string.own_limit_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_credit_card_duotone)) },
                    )
                    otherCards.forEachIndexed { i, card ->
                        app.burrow.money.ui.components.PickRow(
                            stringResource(R.string.shares_limit_of, card.name), i + 1, n, f.sharesLimitWith == card.id,
                            { vm.edit { it.copy(sharesLimitWith = card.id) }; sheet = null },
                            supporting = stringResource(R.string.shares_limit_line),
                            lead = { CategoryCircle(card.icon, card.colorHue) },
                        )
                    }
                }
            }
        }
        FormSheet.BANK -> BankSheet(banks, accountNames, f.bank, { b -> vm.pickBank(b); sheet = null }, { sheet = null })
        FormSheet.LIMIT_AMOUNT, FormSheet.DUE_AMOUNT -> {
            val isLimit = sheet == FormSheet.LIMIT_AMOUNT
            val title = stringResource(if (isLimit) R.string.credit_limit else R.string.amount_due_now)
            app.burrow.money.ui.components.AmountSheet(
                title = title, amount = app.burrow.money.domain.AmountInput(if (isLimit) f.limit else f.balance, money.digits).minor(),
                onDone = { m ->
                    val t = app.burrow.money.domain.AmountInput.of(m, money.digits).text
                    vm.edit { if (isLimit) it.copy(limit = t) else it.copy(balance = t) }
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }
        FormSheet.CARD_NAME -> f.cards.getOrNull(editingCard)?.let { card ->
            CardNameSheet(card, f.name, { sheet = null }) { name ->
                sheet = null
                vm.setCards(f.cards.map { if (it === card) it.copy(name = name) else it })
            }
        }
        FormSheet.CARD -> {
            val card: CardDraft? = null
            CardSheet(
                if (card == null) stringResource(R.string.add_another_card) else card.name.ifEmpty { stringResource(R.string.card_unnamed) },
                if (card == null) stringResource(R.string.add_another_card_sheet_line, f.name.ifBlank { stringResource(R.string.account_card) }) else null,
                stringResource(if (card == null) R.string.add_card else R.string.done),
                { sheet = null }, card,
            ) { name, number ->
                sheet = null
                vm.setCards(if (card == null) f.cards + CardDraft(name = name, number = number) else f.cards.map { if (it === card) it.copy(name = name, number = number) else it })
            }
        }
        FormSheet.MATURITY -> DateSheet(f.maturity ?: LocalDate.now().plusYears(1), { d -> vm.edit { it.copy(maturity = d) }; sheet = null }, { sheet = null }, future = true, title = stringResource(R.string.matures_on))
        null -> Unit
    }
}

/**
 * Bank (196/197): pill search, Your banks (the ones your accounts are named after) then All banks, text-only
 * pick rows with the short name under when it differs ("SBI"). No match: the shared empty state, and Use "…"
 * takes the typed name.
 */
@Composable
private fun BankSheet(banks: List<String>, accountNames: List<String>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val all = (banks + listOfNotNull(selected?.takeIf { it !in banks })).filter {
        q.isEmpty() || it.contains(q, ignoreCase = true) || app.burrow.money.capture.bankShortName(it).contains(q, ignoreCase = true)
    }
    val mine = app.burrow.money.capture.yourBanks(all, accountNames)
    val rest = all - mine.toSet()
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.bank), onDismiss, stringResource(R.string.bank_sheet_line))
        PillTextField(
            query, { query = it }, stringResource(R.string.search_banks),
            Modifier.padding(horizontal = Space.screen).padding(bottom = Space.block),
            leading = painterResource(R.drawable.ic_search_regular), clearLabel = stringResource(R.string.clear),
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
        )
        SheetBody {
            listOf(R.string.your_banks to mine, R.string.all_banks to rest).filter { it.second.isNotEmpty() }.forEach { (title, list) ->
                SectionHeader(stringResource(title))
                ListGroup {
                    list.forEachIndexed { i, b ->
                        app.burrow.money.ui.components.PickRow(b, i, list.size, b == selected, { onPick(b) }, supporting = app.burrow.money.capture.bankShortLine(b))
                    }
                }
            }
            if (all.isEmpty()) {
                app.burrow.money.ui.components.EmptyState(
                    shape = { app.burrow.money.ui.components.Cookie(painterResource(R.drawable.ic_search_off_regular)) },
                    title = stringResource(R.string.no_results, q),
                    line = stringResource(R.string.bank_no_match_line),
                    button = { BurrowButton(stringResource(R.string.use_typed, q), { onPick(q) }, kind = ButtonKind.TONAL) },
                )
            }
        }
    }
}

/** Card name (201): one field with ✕, Save once something is typed; "••5521 · HDFC Regalia" under the title. */
@Composable
private fun CardNameSheet(card: CardDraft, accountName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(card.name) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val ok = name.isNotBlank()
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.card_name), onDismiss, stringResource(R.string.card_name_line, card.number, accountName))
        SheetBody {
            PillTextField(
                name, { name = it }, stringResource(R.string.card_name_hint_same), Modifier.focusRequester(focus),
                leading = painterResource(R.drawable.ic_credit_card_regular), clearLabel = stringResource(R.string.clear),
                onDone = { if (ok) onSave(name.trim()) },
            )
        }
        app.burrow.money.ui.components.SheetFooter {
            BurrowButton(
                stringResource(R.string.save), { if (ok) onSave(name.trim()) }, Modifier.fillMaxWidth(),
                kind = if (ok) ButtonKind.FILLED else ButtonKind.OFF, height = Size.buttonBottom,
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun RateField(rate: String, onRate: (String) -> Unit) {
    LabeledField(stringResource(R.string.interest_rate)) {
        PillTextField(rate, { v -> onRate(v.filter { it.isDigit() || it == '.' }) }, "8.5", leading = painterResource(R.drawable.ic_percent_regular), clearLabel = stringResource(R.string.clear), keyboardType = KeyboardType.Decimal)
    }
}
