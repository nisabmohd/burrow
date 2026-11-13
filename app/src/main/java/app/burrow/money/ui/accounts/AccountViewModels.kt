package app.burrow.money.ui.accounts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.R
import app.burrow.money.Strings
import app.burrow.money.data.AccountEntity
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.CardEntity
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.TxnEntity
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.domain.Instalment
import app.burrow.money.domain.TypedAmount
import app.burrow.money.domain.depositValue
import app.burrow.money.domain.loanSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How many last digits of an account number we keep; banks show 2 to 6 in their messages. */
const val MAX_DIGITS = 6

private val zone = ZoneId.systemDefault()
internal fun Long.day(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
private fun LocalDate.millis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

/** An account page shows its last 30 days with payments (unchanged), read from at most 1,000 of its own rows. */
internal const val ACCOUNT_DAYS = 30
internal const val ACCOUNT_ROWS = 1_000

/** One whole unit of [currency] in minor units (₹1 = 100). */
internal fun unit(currency: String): Long {
    var u = 1L
    repeat(app.burrow.money.data.minorDigits(currency)) { u *= 10 }
    return u
}

/** The groups on Accounts (55), in order. */
enum class AccountGroup(val types: Set<String>, val label: Int) {
    CASH(setOf(AccountType.BANK, AccountType.CASH, AccountType.WALLET), R.string.group_bank_cash),
    CARDS(setOf(AccountType.CARD), R.string.group_cards),
    LOANS(setOf(AccountType.LOAN), R.string.group_loans),
    SAVINGS(setOf(AccountType.SAVINGS), R.string.group_savings),
}

@Immutable
data class AccountsState(
    val groups: List<Pair<AccountGroup, List<AccountWithBalance>>>,
    val netWorth: Long,
    val count: Int,
    /** Accounts with "Count in net worth" off (55: "excludes ICICI, Home"). */
    val excluded: List<AccountEntity>,
    val people: List<app.burrow.money.data.PersonBalance>,
    val payAccounts: List<AccountWithBalance>,
    /** Card accounts' cards (193: "RuPay ••1234 · Visa ••5678" when a bill has two or more). */
    val cards: Map<Long, List<CardEntity>> = emptyMap(),
    /** Every month savings: instalments paid, by account (210's ring). */
    val paid: Map<Long, Int> = emptyMap(),
)

/** Accounts (55): grouped, net worth (the accounts it counts plus goals), lent and borrowed. */
class AccountsViewModel(private val money: MoneyRepository) : ViewModel() {
    val state: StateFlow<AccountsState?> = combine(money.accounts, money.netWorth, money.people, money.cards, money.savingsPaid) { accs, nw, people, cards, paid ->
        AccountsState(
            AccountGroup.entries.map { g -> g to accs.filter { it.account.type in g.types } }.filter { it.second.isNotEmpty() },
            nw.total, accs.size, nw.excluded, people,
            accs.filter { it.account.type == AccountType.BANK || it.account.type == AccountType.CASH || it.account.type == AccountType.WALLET },
            cards.groupBy { it.accountId },
            paid.associate { it.accountId to it.total.toInt() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Settle up: money from them (they owed you) or to them, neither spending nor income. */
    fun settle(person: app.burrow.money.data.PersonEntity, accountId: Long, amount: Long, owesYou: Boolean, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val account = money.accounts.first().firstOrNull { it.account.id == accountId }?.account ?: return@launch
        val id = money.save(
            TxnEntity(
                type = if (owesYou) TxnType.INCOME else TxnType.EXPENSE, amount = amount, currency = account.currency, baseAmount = amount,
                accountId = accountId, payee = person.name, at = now, createdAt = now, updatedAt = now,
                excludeFromStats = true, personId = person.id, lend = app.burrow.money.data.Lend.SETTLE,
            ),
        )
        onDone { money.delete(id) }
    }
}

/** A card's numbers (57). */
@Immutable
data class CardInfo(
    val due: Long, val dueDate: LocalDate?, val sinceStatement: Long, val statementDate: LocalDate?, val limit: Long?,
    /** Cards on one credit line: what all of them owe (the limit is used by all), and the others' names. */
    val lineDue: Long = due, val sharedWith: List<String> = emptyList(),
)

/** A loan's numbers (58). */
@Immutable
data class LoanInfo(val left: Long, val emi: Long, val paid: Int, val total: Int, val ends: LocalDate?, val interestLeft: Long, val next: List<Instalment>)

/** One deposit in a Once savings account (202). */
@Immutable
data class DepositRow(val amount: Long, val rate: String, val start: LocalDate, val maturity: LocalDate, val atMaturity: Long)

/** A savings account's numbers (202–204, 211). */
@Immutable
data class SavingsInfo(
    val mode: String,
    val market: Boolean,
    /** Worth today: deposits' value (Once, fixed), the typed value plus money since (market), else the balance. */
    val worth: Long,
    val putIn: Long,
    val payout: AccountEntity?,
    val deposits: List<DepositRow> = emptyList(),
    val atMaturity: Long = 0,
    /** Months a new deposit runs, from the first one's term (205). */
    val termMonths: Int = 12,
    val moneyIn: List<app.burrow.money.data.SavingsIn> = emptyList(),
    val addedThisYear: Long = 0,
    val yearly: Long = 0,
    val instalments: List<app.burrow.money.data.BillOccurrenceEntity> = emptyList(),
    val paid: Int = 0,
    val total: Int? = null,
    val next: LocalDate? = null,
    val atEnd: Long? = null,
    val end: LocalDate? = null,
)

@Immutable
data class AccountState(
    val account: AccountEntity,
    val balance: Long,
    val txns: List<TxnView>,
    /** The page's history: its last [ACCOUNT_DAYS] days with payments, newest first. */
    val days: List<Pair<LocalDate, List<TxnView>>> = emptyList(),
    val others: List<AccountWithBalance>,
    val card: CardInfo?,
    val loan: LoanInfo?,
    /** A card account's cards (186) and each one's payments this month. */
    val cards: List<CardEntity> = emptyList(),
    val cardCounts: Map<Long, Int> = emptyMap(),
)

/** One account (56–59). */
/** [onDepositChanged] sets the reminder alarm again after a deposit's "Remind me a week before" changes. */
class AccountViewModel(
    private val money: MoneyRepository,
    private val strings: Strings,
    val id: Long,
    private val plan: app.burrow.money.data.PlanRepository? = null,
    private val onDepositChanged: suspend () -> Unit = {},
) : ViewModel() {
    private val today = LocalDate.now(zone)

    private val monthStart = YearMonth.from(today).atDay(1).millis()

    /** A card's last statement day (null: none set, or not a card). */
    private fun statementDate(a: AccountEntity): LocalDate? = a.statementDay?.takeIf { a.type == AccountType.CARD }?.let { d ->
        val here = YearMonth.from(today).atDay(d.coerceAtMost(YearMonth.from(today).lengthOfMonth()))
        if (here.isAfter(today)) here.minusMonths(1) else here
    }

    /** "₹X since statement", summed in SQL over every row since that day, not a page of rows. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val sinceStatement = money.accounts.map { l -> l.firstOrNull { it.account.id == id }?.account?.let(::statementDate) }
        .distinctUntilChanged()
        .flatMapLatest { s -> if (s == null) kotlinx.coroutines.flow.flowOf(0L) else money.expenseSince(id, s.millis()) }

    val state: StateFlow<AccountState?> = combine(
        money.accounts, money.forAccount(id, ACCOUNT_ROWS), sinceStatement, money.cards, money.cardCounts(monthStart),
    ) { accs, mine, since, cards, counts ->
        val me = accs.firstOrNull { it.account.id == id } ?: return@combine null
        val a = me.account
        AccountState(
            account = a,
            balance = me.balance,
            txns = mine,
            days = mine.groupBy { it.txn.at.day() }.entries.take(ACCOUNT_DAYS).map { it.key to it.value },
            others = accs.filter { it.account.id != id },
            card = if (a.type == AccountType.CARD) card(a, me.balance, since, accs) else null,
            loan = if (a.type == AccountType.LOAN) loan(a, me.balance) else null,
            cards = cards.filter { it.accountId == id },
            cardCounts = counts,
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun card(a: AccountEntity, balance: Long, sinceStatement: Long, accs: List<AccountWithBalance>): CardInfo {
        val statement = statementDate(a)
        val due = a.dueDay?.let { app.burrow.money.domain.Repeat.Monthly(it).nextOnOrAfter(today) }
        val since = if (statement != null) sinceStatement else 0
        // One credit line: the limit is the line's first card's, and every card on it uses it up.
        val line = creditLine(a.id, accs.map { it.account })
        val owner = accs.firstOrNull { it.account.id == line }?.account ?: a
        val cards = accs.filter { it.account.type == AccountType.CARD && creditLine(it.account.id, accs.map { x -> x.account }) == line }
        // Due on this bill: what is owed less what was spent after the statement (that's the next bill's; QA 12 Nov).
        val owed = (-balance).coerceAtLeast(0)
        return CardInfo(
            if (statement != null) (owed - since).coerceAtLeast(0) else owed, due, since, statement, owner.creditLimit,
            lineDue = cards.sumOf { (-it.balance).coerceAtLeast(0) },
            sharedWith = cards.filter { it.account.id != a.id }.map { it.account.name },
        )
    }

    private fun loan(a: AccountEntity, balance: Long): LoanInfo? = loanInfo(a, balance, today)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val occurrences = money.savingsBill(id).flatMapLatest { b ->
        if (b == null || plan == null) kotlinx.coroutines.flow.flowOf(emptyList()) else plan.occurrencesOf(b.id).map { l -> l.map { it.occurrence } }
    }

    val savings: StateFlow<SavingsInfo?> = combine(money.accounts, money.moneyIn(id), occurrences) { accs, ins, occ ->
        val me = accs.firstOrNull { it.account.id == id } ?: return@combine null
        val a = me.account
        if (a.type != AccountType.SAVINGS) return@combine null
        savingsInfo(me, ins, occ, accs.firstOrNull { it.account.id == a.payoutAccountId }?.account, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Fix balance: one adjustment row so the balance matches the bank. Returns how to undo it. */
    fun fixBalance(actual: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        val s = state.value ?: return@launch
        val diff = actual - s.balance
        if (diff == 0L) return@launch
        val now = System.currentTimeMillis()
        val txnId = money.save(
            TxnEntity(
                type = if (diff > 0) TxnType.ADJUST_UP else TxnType.ADJUST_DOWN, amount = kotlin.math.abs(diff), currency = s.account.currency,
                baseAmount = kotlin.math.abs(diff), accountId = id, payee = strings(R.string.balance_fixed), excludeFromStats = true,
                isSample = s.account.isSample, at = now, createdAt = now, updatedAt = now,
            ),
        )
        onDone { money.delete(txnId) }
    }

    /**
     * Pay EMI / Pay card bill: pays the next unpaid due date the same way Plan's Paid does (an EMI's
     * interest is an expense, the rest a transfer into the loan) and marks it Paid, so Coming up doesn't
     * ask again (QA 12 Nov). With no bill set up it is a plain transfer in.
     */
    fun payDue(kind: String, from: Long, amount: Long, day: java.time.LocalDate, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        val due = plan?.nextDueInto(id, kind)
        val undo = due?.let { plan?.markPaid(it, atMillis(day), from = from, paid = amount) }
        if (undo != null) onDone(undo) else payIn(from, amount, day, onDone)
    }

    /** Card Pay bill / loan Prepay: a transfer from [from] into this account. */
    /** A transfer into this card or loan from [from], on [day] (now, if today). */
    fun payIn(from: Long, amount: Long, day: java.time.LocalDate = java.time.LocalDate.now(), onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        val s = state.value ?: return@launch
        val now = System.currentTimeMillis()
        val at = if (day == java.time.LocalDate.now()) now
            else day.atTime(java.time.LocalTime.NOON).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val txnId = money.save(
            TxnEntity(
                type = TxnType.TRANSFER, amount = amount, currency = s.account.currency, baseAmount = amount, accountId = from, toAccountId = id,
                isSample = s.account.isSample, at = at, createdAt = now, updatedAt = now,
            ),
        )
        onDone { money.delete(txnId) }
    }

    private fun atMillis(day: LocalDate) = if (day == LocalDate.now()) System.currentTimeMillis()
        else day.atTime(java.time.LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()

    /** Add money (205, 207): a transfer in; to a Once account at a fixed rate it starts a new deposit. */
    fun addMoney(from: Long, amount: Long, day: LocalDate, rate: String?, maturity: LocalDate?, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        val s = state.value ?: return@launch
        val sv = savings.value
        if (sv == null || sv.mode != app.burrow.money.data.SavingsMode.ONCE || sv.market || rate == null || maturity == null) {
            payIn(from, amount, day, onDone)
            return@launch
        }
        val now = System.currentTimeMillis()
        val txnId = money.addDeposit(
            TxnEntity(
                type = TxnType.TRANSFER, amount = amount, currency = s.account.currency, baseAmount = amount, accountId = from, toAccountId = id,
                isSample = s.account.isSample, at = atMillis(day), createdAt = now, updatedAt = now,
            ),
            rate, maturity.millis(),
        )
        onDepositChanged()
        onDone { money.delete(txnId) }
    }

    /** Pay instalment (206): marks the next instalment Paid. */
    fun payInstalment(from: Long, amount: Long, day: LocalDate, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        val p = plan ?: return@launch
        onDone(p.payInstalment(id, from, amount, atMillis(day)))
    }

    /** Update value (212): what it is worth today, typed; kept as the gain over what was put in. */
    fun updateValue(value: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        val s = state.value ?: return@launch
        val before = s.account
        money.updateAccount(before.copy(marketGain = value - s.balance, valueUpdatedAt = System.currentTimeMillis()))
        onDone { money.updateAccount(before) }
    }

    /** Add another card on this bill (189). */
    fun addCard(name: String, number: String, onDone: () -> Unit) = viewModelScope.launch {
        money.addCard(id, name.trim(), number)
        onDone()
    }

    /** Count in net worth (86). */
    fun setInNetWorth(on: Boolean) = viewModelScope.launch { state.value?.account?.let { money.updateAccount(it.copy(includeInNetWorth = on)) } }
}

@Immutable
data class AccountForm(
    val type: String = AccountType.BANK,
    val name: String = "",
    val balance: String = "",
    val number: String = "",
    val limit: String = "",
    val statementDay: Int = 5,
    val dueDay: Int = 25,
    val emi: String = "",
    val rate: String = "",
    val maturity: LocalDate? = null,
    val tried: Boolean = false,
    /** Look (106/107): colour and icon; the icon follows the kind until one is picked. */
    val hue: Float = app.burrow.money.ui.theme.CategoryHues.all.random(),
    val icon: String? = null,
    /** Count in net worth; null = the kind's default (cards and loans off). */
    val inNetWorth: Boolean? = null,
    /** Edit only: the opening balance, shown with "fix it from Fix balance". */
    val opening: Long = 0,
    /** CARD: uses this other card's credit limit (null: its own). */
    val sharesLimitWith: Long? = null,
    /** Edit, CARD: the cards on its bill (188); id 0 = added here. */
    val cards: List<CardDraft> = emptyList(),
    /** New CARD (191/192): the bank it is from (null: another bank), and whether it joins that bank's card's bill. */
    val bank: String? = null,
    val sameBill: Boolean = true,
    /** SAVINGS (208, 209, 213): how money goes in, how it grows, the account money comes from. */
    val savingsMode: String = app.burrow.money.data.SavingsMode.ONCE,
    val market: Boolean = false,
    val fromId: Long? = null,
)
 {
    val shownIcon get() = icon ?: defaultAccountIcon(type)
    val countsInNetWorth get() = inNetWorth ?: app.burrow.money.data.defaultInNetWorth(type)
}

/** A card on a bill while the form is open. */
@Immutable
@kotlinx.serialization.Serializable
data class CardDraft(val id: Long = 0, val name: String, val number: String)

/** The icon an account gets from its kind. */
fun defaultAccountIcon(type: String) = when (type) {
    AccountType.CASH -> "payments"
    AccountType.WALLET -> "account_balance_wallet"
    AccountType.CARD -> "credit_card"
    AccountType.LOAN -> "real_estate_agent"
    AccountType.SAVINGS -> "savings"
    else -> "account_balance"
}

/** Icons an account can have (106), in picker order. */
val ACCOUNT_ICONS = listOf(
    "account_balance", "credit_card", "account_balance_wallet", "payments", "savings", "real_estate_agent",
    "smartphone", "home", "shield", "flight", "school", "redeem",
    "calendar_month", "bar_chart", "flag", "gold", "investment", "child_care",
)

/** Icons a savings account can have (217). */

/**
 * The credit line [cardId] is on: the id of the card that holds the limit. A card shares with one
 * card; if that one shares too, its line wins (one step, so a loop can't form).
 */
fun creditLine(cardId: Long, accounts: List<AccountEntity>): Long {
    val me = accounts.firstOrNull { it.id == cardId } ?: return cardId
    val other = me.sharesLimitWith?.let { id -> accounts.firstOrNull { it.id == id && it.type == AccountType.CARD && !it.archived } } ?: return cardId
    return other.sharesLimitWith?.takeIf { it != cardId } ?: other.id
}

/** New account / edit account: the fields that apply to its type. */
class NewAccountViewModel(
    private val money: MoneyRepository,
    private val strings: Strings,
    private val editId: Long,
    private val digits: Int,
    private val currency: String,
    /** Welcome step 2's "+ Card", Add account (190): the type to start with. */
    val startType: String? = null,
    private val plan: app.burrow.money.data.PlanRepository? = null,
    /** Sets the reminder alarm again after a deposit is saved (its maturity may have moved). */
    private val onDepositChanged: suspend () -> Unit = {},
) : ViewModel() {
    val form = MutableStateFlow<AccountForm?>(null)
    private var original: AccountEntity? = null

    /** Banks to pick for a new card (191), read from the rule packs off the main thread. */
    val banks = MutableStateFlow<List<String>>(emptyList())
    fun loadBanks(read: () -> List<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) { banks.value = read() }
    }

    /** Other cards, for "Shares the limit of" (one credit line for several cards). */
    val otherCards: StateFlow<List<AccountEntity>> = money.accounts
        // Never a sample card: a real card joined to one went away with the samples (QA 12 Nov).
        .map { list -> list.map { it.account }.filter { it.type == AccountType.CARD && it.id != editId && !it.archived && !it.isSample } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isEdit get() = editId != 0L

    /** Accounts money can come from (New savings › From, 208). */
    val payAccounts: StateFlow<List<AccountWithBalance>> = money.accounts
        .map { list -> list.filter { it.account.type == AccountType.BANK || it.account.type == AccountType.CASH || it.account.type == AccountType.WALLET } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Your accounts' names, for "Your banks" in the Bank sheet (196). */
    val accountNames: StateFlow<List<String>> = money.accounts
        .map { list -> list.filter { !it.account.archived && !it.account.isSample }.map { it.account.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val a = if (editId != 0L) money.account(editId).first() else null
            original = a
            fun txt(v: Long?) = v?.let { java.math.BigDecimal.valueOf(it, digits).stripTrailingZeros().toPlainString() }.orEmpty()
            form.value = a?.let {
                AccountForm(
                    it.type, it.name, "", it.number.orEmpty(), txt(it.creditLimit), it.statementDay ?: 5, it.dueDay ?: 25,
                    txt(it.emi), it.interestRate.orEmpty(), it.maturityDate?.day(),
                    hue = it.colorHue, icon = it.icon, inNetWorth = it.includeInNetWorth, opening = it.openingBalance,
                    sharesLimitWith = it.sharesLimitWith,
                    cards = money.cards.first().filter { c -> c.accountId == it.id }.map { c -> CardDraft(c.id, c.name, c.number) },
                    savingsMode = app.burrow.money.data.savingsModeOf(it), market = it.marketValue, fromId = it.payoutAccountId,
                ).let { f ->
                    // Edit savings (216): the amount row shows the instalment, or the first deposit / starting amount.
                    if (it.type != AccountType.SAVINGS) f
                    else f.copy(balance = txt(if (f.savingsMode == app.burrow.money.data.SavingsMode.MONTHLY) it.emi else it.principal ?: it.openingBalance))
                }
            } ?: AccountForm(type = startType ?: AccountType.BANK).let { f ->
                if (f.type != AccountType.SAVINGS) f
                else f.copy(maturity = LocalDate.now().plusYears(2), fromId = money.accounts.first().firstOrNull { it.account.type == AccountType.BANK }?.account?.id)
            }
        }
    }

    fun edit(change: (AccountForm) -> AccountForm) = form.update { it?.let(change) }

    /** A new card needs its name and 4 digits (191/192); everything else a name. */
    fun canSave(f: AccountForm) = f.name.isNotBlank() && (isEdit || f.type != AccountType.CARD || f.number.length == 4) &&
        (f.type != AccountType.SAVINGS || (TypedAmount.parse(f.balance, digits) > 0 && (f.market || f.rate.isNotBlank())))

    /** How money goes in / how it grows (208, 213): Any time and market value start with no end date. */
    fun setSavingsMode(mode: String) = edit {
        val open = mode == app.burrow.money.data.SavingsMode.ANY || it.market
        it.copy(savingsMode = mode, maturity = if (open) null else it.maturity ?: LocalDate.now().plusYears(2))
    }
    fun setMarket(on: Boolean) = edit {
        it.copy(market = on, maturity = if (on || it.savingsMode == app.burrow.money.data.SavingsMode.ANY) null else it.maturity ?: LocalDate.now().plusYears(2))
    }

    /** New card from a bank you already have a card from (191): the card account it may join. */
    fun cardOfBank(f: AccountForm, cards: List<AccountEntity>): AccountEntity? = f.bank?.let { b ->
        app.burrow.money.capture.cardOfBank(b, cards.map { app.burrow.money.capture.AccountRef(it.id, it.name, it.number, it.type) })
            ?.let { r -> cards.firstOrNull { it.id == r.id } }
    }

    /** Bank picked (191): a bank you already have a card from starts on Same bill and shares its limit. */
    fun pickBank(bank: String?) {
        val f = form.value ?: return
        val existing = cardOfBank(f.copy(bank = bank), otherCards.value)
        edit { it.copy(bank = bank, sameBill = existing != null, sharesLimitWith = existing?.id) }
    }

    /** Edit account › Cards on this bill (188): add, change or remove; saved with the account. */
    fun setCards(cards: List<CardDraft>) = edit { it.copy(cards = cards) }

    /** Archive account (107): gone from lists and pickers, its history kept; hands back how to undo. */
    /** What the account holds now: archiving one that isn't empty asks first (user, 2026-11-12). */
    val balance: StateFlow<Long> = money.accounts
        .map { list -> list.firstOrNull { it.account.id == editId }?.balance ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun archive(onDone: (suspend () -> Unit) -> Unit) {
        val a = original ?: return
        viewModelScope.launch {
            money.updateAccount(a.copy(archived = true))
            plan?.syncSavingsBill(a.copy(archived = true))
            val bills = plan?.endBillsOf(a.id)
            onDone { money.updateAccount(a); bills?.invoke(); plan?.syncSavingsBill(a) }
        }
    }

    /** Saves; [onSaved] gets the snackbar text when it isn't "Saved" (a card added to a bill). */
    fun save(onSaved: (String?) -> Unit) {
        val f = form.value ?: return
        if (!canSave(f)) {
            edit { it.copy(tried = true) }
            return
        }
        viewModelScope.launch {
            // Same bill (191): only a card on the existing account's bill; nothing else changes.
            val joins = if (!isEdit && f.type == AccountType.CARD && f.sameBill) cardOfBank(f, otherCards.value) else null
            if (joins != null) {
                money.addCard(joins.id, f.name.trim(), f.number)
                onSaved(strings(R.string.card_added_to, f.name.trim(), joins.name))
                return@launch
            }
            fun amt(t: String) = TypedAmount.parse(t, digits).takeIf { it > 0 }
            val entered = TypedAmount.parse(f.balance, digits)
            // A card's and a loan's "balance" is what is owed: stored as a negative balance.
            val opening = if (f.type == AccountType.CARD || f.type == AccountType.LOAN) -entered else entered
            val base = original ?: AccountEntity(
                name = "", type = f.type, currency = currency, openingBalance = opening, colorHue = f.hue,
                icon = f.shownIcon, createdAt = System.currentTimeMillis(), sortOrder = 50,
                includeInNetWorth = f.countsInNetWorth,
            )
            // A new card is named by its bank and its own name ("ICICI" + "Sapphiro").
            val name = if (!isEdit && f.type == AccountType.CARD && f.bank != null) "${app.burrow.money.capture.bankShortName(f.bank)} ${f.name.trim()}" else f.name.trim()
            val firstCard = if (isEdit && f.type == AccountType.CARD) f.cards.firstOrNull()?.number else null
            val a = base.copy(
                name = name, type = f.type, icon = f.shownIcon, colorHue = f.hue, includeInNetWorth = f.countsInNetWorth,
                number = firstCard ?: f.number.filter { it.isDigit() }.takeLast(MAX_DIGITS).ifEmpty { null },
                // A card on another's line keeps no limit of its own.
                creditLimit = if (f.type == AccountType.CARD && f.sharesLimitWith == null) amt(f.limit) else null,
                sharesLimitWith = if (f.type == AccountType.CARD) f.sharesLimitWith else null,
                statementDay = if (f.type == AccountType.CARD) f.statementDay else null,
                dueDay = if (f.type == AccountType.CARD) f.dueDay else null,
                emi = if (f.type == AccountType.LOAN) amt(f.emi) else null,
                interestRate = if (f.type == AccountType.LOAN || f.type == AccountType.SAVINGS) f.rate.trim().ifEmpty { null } else null,
                principal = if (f.type == AccountType.SAVINGS) (base.principal ?: entered) else null,
                startDate = base.startDate ?: if (f.type == AccountType.LOAN || f.type == AccountType.SAVINGS) LocalDate.now().millis() else null,
                maturityDate = if (f.type == AccountType.SAVINGS) f.maturity?.millis() else null,
                isSample = false,
            )
            val saved = if (a.type == AccountType.SAVINGS) savingsAccount(a, f, entered) else a
            val newId = if (original == null) money.addAccount(saved, cardName = if (a.type == AccountType.CARD) f.name.trim() else "") else { money.updateAccount(saved); saved.id }
            if (saved.type == AccountType.SAVINGS || original?.type == AccountType.SAVINGS) plan?.syncSavingsBill(saved.copy(id = newId))
            if (isEdit && a.type == AccountType.CARD) money.setCards(a.id, f.cards.map { CardEntity(it.id, a.id, it.name, it.number) })
            if (a.type == AccountType.SAVINGS || original?.type == AccountType.SAVINGS) onDepositChanged()
            onSaved(if (!isEdit && a.type == AccountType.CARD) strings(R.string.card_added) else null)
        }
    }
}

/**
 * A savings account from its form (208, 209, 213): Once and Any time start with the amount as the
 * balance (no money moves); Every month starts at 0 and pays the amount each month from the from account.
 */
private fun savingsAccount(a: AccountEntity, f: AccountForm, amount: Long): AccountEntity {
    val monthly = f.savingsMode == app.burrow.money.data.SavingsMode.MONTHLY
    val new = a.id == 0L
    val look = when {
        f.market -> "bar_chart" to 140f
        monthly -> "calendar_month" to 168f
        f.savingsMode == app.burrow.money.data.SavingsMode.ANY -> "flag" to 245f
        else -> "savings" to 50f
    }
    return a.copy(
        savingsMode = f.savingsMode, marketValue = f.market,
        // Editing changes the first deposit / starting amount; money added later keeps its values.
        openingBalance = if (monthly) (if (new) 0 else a.openingBalance) else amount,
        principal = if (f.savingsMode == app.burrow.money.data.SavingsMode.ONCE) amount else null,
        emi = if (monthly) amount.takeIf { it > 0 } ?: a.emi else null,
        interestRate = if (f.market) null else f.rate.trim().ifEmpty { null },
        maturityDate = f.maturity?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        payoutAccountId = f.fromId,
        icon = if (new) look.first else a.icon, colorHue = if (new) look.second else a.colorHue,
        marketGain = if (f.market) a.marketGain ?: 0 else null,
        valueUpdatedAt = if (f.market) a.valueUpdatedAt ?: System.currentTimeMillis() else null,
    )
}

/** A loan's EMIs paid, left, interest left and the next three (58; Plan › EMIs widget, 22). */
fun loanInfo(a: AccountEntity, balance: Long, today: LocalDate = LocalDate.now()): LoanInfo? {
    val emi = a.emi ?: return null
    val rate = a.interestRate?.toBigDecimalOrNull() ?: return null
    val zone = java.time.ZoneId.systemDefault()
    val start = a.startDate?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val paid = start?.let { (ChronoUnit.MONTHS.between(YearMonth.from(it), YearMonth.from(today)).toInt() + if (today.dayOfMonth >= it.dayOfMonth) 1 else 0).coerceAtLeast(0) } ?: 0
    val nextDate = start?.let { app.burrow.money.domain.Repeat.Monthly(it.dayOfMonth).nextOnOrAfter(today) } ?: today
    var unit = 1L
    repeat(app.burrow.money.data.minorDigits(a.currency)) { unit *= 10 }
    val schedule = loanSchedule(-balance, rate, emi, nextDate, paid + 1, unit = unit)
    return LoanInfo((-balance).coerceAtLeast(0), emi, paid, paid + schedule.size, schedule.lastOrNull()?.date, schedule.sumOf { it.interest }, schedule.take(3))
}

/** A savings account's numbers on [today], from its money in and its instalments (202–204, 211). */
fun savingsInfo(
    me: AccountWithBalance,
    ins: List<app.burrow.money.data.SavingsIn>,
    occ: List<app.burrow.money.data.BillOccurrenceEntity>,
    payout: AccountEntity?,
    today: LocalDate,
): SavingsInfo {
    val a = me.account
    val mode = app.burrow.money.data.savingsModeOf(a)
    val u = unit(a.currency)
    val rate = a.interestRate?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
    val start = a.startDate?.day() ?: today
    val end = a.maturityDate?.day()
    val base = SavingsInfo(mode, a.marketValue, me.worth, me.balance, payout, moneyIn = ins)
    if (a.marketValue) return base.copy(
        paid = occ.count { it.status == app.burrow.money.data.OccurrenceStatus.PAID },
        instalments = occ, next = occ.filter { it.status == app.burrow.money.data.OccurrenceStatus.UPCOMING }.minOfOrNull { it.dueDate }?.day(),
        end = end, addedThisYear = ins.filter { it.at.day().year == today.year }.sumOf { it.amount },
    )
    return when (mode) {
        app.burrow.money.data.SavingsMode.ONCE -> {
            val term = end?.let { ChronoUnit.MONTHS.between(start, it).toInt() }?.takeIf { it > 0 } ?: 12
            val first = end?.let { DepositRow(a.principal ?: a.openingBalance, a.interestRate.orEmpty(), start, it, 0) }
            val rows = (listOfNotNull(first) + ins.reversed().map { i ->
                val s = i.at.day()
                DepositRow(i.amount, i.rate ?: a.interestRate.orEmpty(), s, i.maturity?.day() ?: s.plusMonths(term.toLong()), 0)
            }).map { d ->
                val r = d.rate.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                d.copy(atMaturity = depositValue(d.amount, r, d.start, d.maturity, d.maturity, u))
            }
            val worth = if (me.balance <= 0) 0 else rows.sumOf { d -> depositValue(d.amount, d.rate.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO, d.start, d.maturity, today, u) }
            base.copy(worth = if (rows.isEmpty()) me.balance else worth, deposits = rows, atMaturity = rows.sumOf { it.atMaturity }, termMonths = term, end = end)
        }
        app.burrow.money.data.SavingsMode.MONTHLY -> {
            val total = end?.let { app.burrow.money.domain.instalmentCount(start, it) }
            base.copy(
                instalments = occ, paid = occ.count { it.status == app.burrow.money.data.OccurrenceStatus.PAID }, total = total,
                next = occ.filter { it.status == app.burrow.money.data.OccurrenceStatus.UPCOMING }.minOfOrNull { it.dueDate }?.day(),
                atEnd = if (end != null && total != null) app.burrow.money.domain.recurringValue(a.emi ?: 0, rate, start, total, end, u) else null, end = end,
            )
        }
        else -> base.copy(addedThisYear = ins.filter { it.at.day().year == today.year }.sumOf { it.amount }, yearly = app.burrow.money.domain.yearlyInterest(me.balance, rate, u), end = end)
    }
}
