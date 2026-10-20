package app.burrow.money.ui.accounts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.R
import app.burrow.money.Strings
import app.burrow.money.data.AccountEntity
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val zone = ZoneId.systemDefault()
internal fun Long.day(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
private fun LocalDate.millis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

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
)

/** Accounts (55): grouped, net worth (the accounts it counts plus goals), lent and borrowed. */
class AccountsViewModel(private val money: MoneyRepository) : ViewModel() {
    val state: StateFlow<AccountsState?> = combine(money.accounts, money.netWorth, money.people) { accs, nw, people ->
        AccountsState(
            AccountGroup.entries.map { g -> g to accs.filter { it.account.type in g.types } }.filter { it.second.isNotEmpty() },
            nw.total, accs.size, nw.excluded, people,
            accs.filter { it.account.type == AccountType.BANK || it.account.type == AccountType.CASH || it.account.type == AccountType.WALLET },
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
data class CardInfo(val due: Long, val dueDate: LocalDate?, val sinceStatement: Long, val statementDate: LocalDate?, val limit: Long?)

/** A loan's numbers (58). */
@Immutable
data class LoanInfo(val left: Long, val emi: Long, val paid: Int, val total: Int, val ends: LocalDate?, val interestLeft: Long, val next: List<Instalment>)

/** A deposit's numbers (59). */
@Immutable
data class DepositInfo(val putIn: Long, val atMaturity: Long, val earned: Long, val maturity: LocalDate?, val payout: AccountEntity?)

@Immutable
data class AccountState(
    val account: AccountEntity,
    val balance: Long,
    val txns: List<TxnView>,
    val others: List<AccountWithBalance>,
    val card: CardInfo?,
    val loan: LoanInfo?,
    val deposit: DepositInfo?,
)

/** One account (56–59). */
class AccountViewModel(private val money: MoneyRepository, private val strings: Strings, val id: Long) : ViewModel() {
    private val today = LocalDate.now(zone)

    val state: StateFlow<AccountState?> = combine(money.accounts, money.latest(400)) { accs, txns ->
        val me = accs.firstOrNull { it.account.id == id } ?: return@combine null
        val a = me.account
        val mine = txns.filter { it.txn.accountId == id || it.txn.toAccountId == id }
        AccountState(
            account = a,
            balance = me.balance,
            txns = mine,
            others = accs.filter { it.account.id != id },
            card = if (a.type == AccountType.CARD) card(a, me.balance, mine) else null,
            loan = if (a.type == AccountType.LOAN) loan(a, me.balance) else null,
            deposit = if (a.type == AccountType.SAVINGS && a.maturityDate != null) deposit(a, accs) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun card(a: AccountEntity, balance: Long, txns: List<TxnView>): CardInfo {
        val statement = a.statementDay?.let { d ->
            val here = YearMonth.from(today).atDay(d.coerceAtMost(YearMonth.from(today).lengthOfMonth()))
            if (here.isAfter(today)) here.minusMonths(1) else here
        }
        val due = a.dueDay?.let { app.burrow.money.domain.Repeat.Monthly(it).nextOnOrAfter(today) }
        val since = statement?.let { s -> txns.filter { it.txn.accountId == a.id && it.txn.type == TxnType.EXPENSE && !it.txn.at.day().isBefore(s) }.sumOf { it.txn.amount } } ?: 0
        return CardInfo((-balance).coerceAtLeast(0), due, since, statement, a.creditLimit)
    }

    private fun loan(a: AccountEntity, balance: Long): LoanInfo? = loanInfo(a, balance, today)

    private fun deposit(a: AccountEntity, accs: List<AccountWithBalance>): DepositInfo {
        val putIn = a.principal ?: 0
        val rate = a.interestRate?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        val start = a.startDate?.day() ?: today
        val maturity = a.maturityDate!!.day()
        val u = unit(a.currency)
        return DepositInfo(putIn, depositValue(putIn, rate, start, maturity, maturity, u), depositValue(putIn, rate, start, maturity, today, u) - putIn, maturity, accs.firstOrNull { it.account.id == a.payoutAccountId }?.account)
    }

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

    /** Card Pay bill / loan Prepay: a transfer from [from] into this account. */
    fun payIn(from: Long, amount: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        val s = state.value ?: return@launch
        val now = System.currentTimeMillis()
        val txnId = money.save(
            TxnEntity(
                type = TxnType.TRANSFER, amount = amount, currency = s.account.currency, baseAmount = amount, accountId = from, toAccountId = id,
                isSample = s.account.isSample, at = now, createdAt = now, updatedAt = now,
            ),
        )
        onDone { money.delete(txnId) }
    }

    fun setRemind(on: Boolean) = viewModelScope.launch { state.value?.account?.let { money.updateAccount(it.copy(remind = on)) } }

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
)

/** New account / edit account: the fields that apply to its type. */
class NewAccountViewModel(
    private val money: MoneyRepository,
    private val editId: Long,
    private val digits: Int,
    private val currency: String,
    /** Welcome step 2's "+ Card" etc.: the type to start with. */
    private val startType: String? = null,
) : ViewModel() {
    val form = MutableStateFlow<AccountForm?>(null)
    private var original: AccountEntity? = null
    val isEdit get() = editId != 0L

    init {
        viewModelScope.launch {
            val a = if (editId != 0L) money.account(editId).first() else null
            original = a
            fun txt(v: Long?) = v?.let { java.math.BigDecimal.valueOf(it, digits).stripTrailingZeros().toPlainString() }.orEmpty()
            form.value = a?.let {
                AccountForm(
                    it.type, it.name, "", it.number.orEmpty(), txt(it.creditLimit), it.statementDay ?: 5, it.dueDay ?: 25,
                    txt(it.emi), it.interestRate.orEmpty(), it.maturityDate?.day(),
                )
            } ?: AccountForm(type = startType ?: AccountType.BANK)
        }
    }

    fun edit(change: (AccountForm) -> AccountForm) = form.update { it?.let(change) }

    fun canSave(f: AccountForm) = f.name.isNotBlank()

    fun save(onSaved: () -> Unit) {
        val f = form.value ?: return
        if (!canSave(f)) {
            edit { it.copy(tried = true) }
            return
        }
        viewModelScope.launch {
            fun amt(t: String) = TypedAmount.parse(t, digits).takeIf { it > 0 }
            val entered = TypedAmount.parse(f.balance, digits)
            // A card's and a loan's "balance" is what is owed: stored as a negative balance.
            val opening = if (f.type == AccountType.CARD || f.type == AccountType.LOAN) -entered else entered
            val icon = when (f.type) {
                AccountType.CASH -> "payments"
                AccountType.WALLET -> "account_balance_wallet"
                AccountType.CARD -> "credit_card"
                AccountType.LOAN -> "real_estate_agent"
                AccountType.SAVINGS -> "savings"
                else -> "account_balance"
            }
            val base = original ?: AccountEntity(
                name = "", type = f.type, currency = currency, openingBalance = opening, colorHue = app.burrow.money.ui.theme.CategoryHues.all.random(),
                icon = icon, createdAt = System.currentTimeMillis(), sortOrder = 50,
                includeInNetWorth = app.burrow.money.data.defaultInNetWorth(f.type),
            )
            val a = base.copy(
                name = f.name.trim(), type = f.type, icon = if (original == null) icon else base.icon,
                number = f.number.filter { it.isDigit() }.takeLast(4).ifEmpty { null },
                creditLimit = if (f.type == AccountType.CARD) amt(f.limit) else null,
                statementDay = if (f.type == AccountType.CARD) f.statementDay else null,
                dueDay = if (f.type == AccountType.CARD) f.dueDay else null,
                emi = if (f.type == AccountType.LOAN) amt(f.emi) else null,
                interestRate = if (f.type == AccountType.LOAN || f.type == AccountType.SAVINGS) f.rate.trim().ifEmpty { null } else null,
                principal = if (f.type == AccountType.SAVINGS) (base.principal ?: entered) else null,
                startDate = base.startDate ?: if (f.type == AccountType.LOAN || f.type == AccountType.SAVINGS) LocalDate.now().millis() else null,
                maturityDate = if (f.type == AccountType.SAVINGS) f.maturity?.millis() else null,
                isSample = false,
            )
            if (original == null) money.addAccount(a) else money.updateAccount(a)
            onSaved()
        }
    }
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
