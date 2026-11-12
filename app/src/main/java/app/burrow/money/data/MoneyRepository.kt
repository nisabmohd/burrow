package app.burrow.money.data

import androidx.compose.runtime.Immutable
import androidx.room.withTransaction
import app.burrow.money.Strings
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.domain.balances
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

@Immutable
data class AccountWithBalance(val account: AccountEntity, val balance: Long) {
    /** What it is worth: the balance, and for a market-value savings account the gain typed last (211). */
    val worth: Long get() = balance + if (account.marketValue) account.marketGain ?: 0L else 0L
}

/** Someone money was lent to or borrowed from: what they owe (positive) or you owe (negative), since when. */
@Immutable
data class PersonBalance(val person: PersonEntity, val balance: Long, val since: Long?)

/** What net worth counts: the total, and the accounts left out (Accounts header, 55). */
@Immutable
data class NetWorth(val total: Long, val excluded: List<AccountEntity>, val count: Int)

/**
 * Accounts, categories and transactions (docs/DATA.md). Every Flow waits for the first-open seeding,
 * so a screen never sees an empty database for a moment (no "₹0" flash, no empty state flash).
 */
class MoneyRepository(
    private val db: BurrowDatabase,
    private val settings: SettingsRepository,
    private val strings: Strings,
) {
    private val seedLock = Mutex()
    @Volatile private var seeded = false

    /** First open: default categories and the sample month. Runs once; later calls return at once. */
    suspend fun ensureSeeded() {
        if (seeded) return
        seedLock.withLock {
            if (seeded) return
            if (db.categories().count() == 0) {
                val s = settings.settings.first()
                settings.setMainCurrency(s.mainCurrency) // pin the region's currency from now on
                val digits = MoneyFormat.of(s.mainCurrency).digits
                val withSamples = !settings.samplesDone()
                db.withTransaction {
                    val ids = db.categories().insertAll(defaultCategories(strings))
                    if (withSamples) insertSamples(ids, s.mainCurrency, digits, s.monthStartDay)
                }
                settings.setSamplesDone()
            }
            seeded = true
        }
    }


    /** The sample month (docs/DATA.md § Sample data), once per install, mapped onto the real ids. */
    private suspend fun insertSamples(ids: List<Long>, currency: String, digits: Int, monthStartDay: Int) {
        val sample = SampleData.build(
            today = LocalDate.now(), now = System.currentTimeMillis(), currency = currency,
            digits = digits, monthStartDay = monthStartDay, categoryIds = ids,
        )
        val accountIds = sample.accounts.map { db.accounts().insert(it) }
        fun acc(i: Long) = accountIds[i.toInt()]
        // Accounts that point at another account (a deposit's payout) get the real id.
        sample.accounts.forEachIndexed { i, a ->
            a.payoutAccountId?.let { db.accounts().update(a.copy(id = accountIds[i], payoutAccountId = acc(it))) }
        }
        db.txns().insertAll(sample.txns.map { t -> t.copy(accountId = acc(t.accountId), toAccountId = t.toAccountId?.let(::acc)) })
        sample.budgets.forEach { db.budgets().insert(it) }
        sample.bills.forEach { sb ->
            val billId = db.bills().upsert(sb.bill.copy(accountId = acc(sb.bill.accountId), toAccountId = sb.bill.toAccountId?.let(::acc)))
            db.bills().insertOccurrences(sb.occurrences.map { it.copy(billId = billId) })
        }
        sample.goals.forEach { sg ->
            val goalId = db.goals().upsert(sg.goal.copy(accountId = acc(sg.goal.accountId)))
            db.txns().insertAll(sg.added.map { it.copy(accountId = acc(it.accountId), goalId = goalId) })
        }
    }

    private fun <T> seededFlow(source: () -> Flow<T>): Flow<T> = flow { ensureSeeded(); emitAll(source()) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val accounts: Flow<List<AccountWithBalance>> = seededFlow {
        val a = db.accounts()
        settings.settings.map { it.countUpcoming }.distinctUntilChanged().flatMapLatest { up ->
            combine(a.all(), a.outSums(up), a.inSums(up)) { accounts, out, into -> accounts to (out to into) }
        }.map { (accounts, sums) ->
            app.burrow.money.ui.AccountNames.names = accounts.map { it.name }
            val (out, into) = sums
            val b = balances(accounts.associate { it.id to it.openingBalance }, out, into.associate { it.accountId to it.total })
            accounts.map { AccountWithBalance(it, b[it.id] ?: it.openingBalance) }
        }
    }

    val rates: Flow<List<RateEntity>> = seededFlow { db.extras().rates() }

    /**
     * Net worth: accounts with "Count in net worth" on, in the main currency (typed-in rates; an
     * account in a currency without a rate is left out), plus money set aside in goals.
     */
    val netWorth: Flow<NetWorth> = combine(accounts, goalsSavedFlow(), rates, settings.settings.map { it.mainCurrency }.distinctUntilChanged()) { accs, goals, rates, main ->
        val rateOf = rates.associate { it.currency to it.perPrimary }
        val counted = accs.filter { it.account.includeInNetWorth }
        val total = counted.sumOf { app.burrow.money.domain.Fx.toMain(it.worth, it.account.currency, main, rateOf[it.account.currency]) ?: 0L } + goals
        NetWorth(total, accs.filter { !it.account.includeInNetWorth }.map { it.account }, accs.size)
    }

    /** People with money between you, biggest first; settled ones (0) are left out. */
    val people: Flow<List<PersonBalance>> = seededFlow {
        val x = db.extras()
        combine(x.people(), x.lendSums(), x.lendSince()) { people, sums, since ->
            val sinceOf = since.associate { it.personId to it.since }
            people.map { p ->
                PersonBalance(p, app.burrow.money.domain.personBalance(sums.filter { it.personId == p.id }.map { Triple(it.lend, it.type, it.total) }), sinceOf[p.id])
            }.filter { it.balance != 0L }.sortedByDescending { kotlin.math.abs(it.balance) }
        }
    }

    val tags: Flow<List<TagEntity>> = seededFlow { db.extras().tags() }

    /** The person named [name] (case ignored), made if new. */
    suspend fun personFor(name: String): Long {
        val n = name.trim()
        db.extras().personNamed(n)?.let { return it.id }
        return db.extras().addPerson(PersonEntity(name = n, colorHue = app.burrow.money.ui.theme.CategoryHues.all.random()))
    }

    suspend fun setPersonReminder(personId: Long, at: Long?) {
        db.extras().person(personId)?.let { db.extras().updatePerson(it.copy(remindAt = at)) }
    }

    /** Replaces [txnId]'s tags with [names] (new ones are made). */
    suspend fun setTags(txnId: Long, names: List<String>) = db.withTransaction {
        db.extras().clearTags(txnId)
        val ids = names.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinct().map { n ->
            db.extras().tagNamed(n)?.id ?: db.extras().addTag(TagEntity(name = n))
        }
        db.extras().tag(ids.map { TxnTagEntity(txnId, it) })
    }

    suspend fun tagsOf(txnId: Long): List<String> = db.extras().tagsOf(txnId).map { it.name }

    suspend fun setRate(currency: String, perPrimary: String) = db.extras().setRate(RateEntity(currency, perPrimary, System.currentTimeMillis()))

    suspend fun removeRate(currency: String) = db.extras().removeRate(currency)

    suspend fun rateFor(currency: String): String? = db.extras().rate(currency)?.perPrimary

    /** Upcoming transactions whose day has come become posted (app start, the daily check). */
    /** An upcoming one-off, added now: posted with today's time. Returns its Undo. */
    suspend fun addNow(id: Long): (suspend () -> Unit)? {
        val before = db.txns().get(id) ?: return null
        save(before.copy(status = TxnStatus.POSTED, at = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        return { restore(before) }
    }

    /** Posts the upcoming ones whose day has come and returns them (115: "Rent ₹18,000 added today"). */
    suspend fun postDueList(now: Long = System.currentTimeMillis()): List<TxnEntity> {
        val due = db.txns().dueUpcoming(now)
        if (due.isNotEmpty()) { db.txns().postDue(now); changed?.invoke() }
        return due
    }

    suspend fun postDue(now: Long = System.currentTimeMillis()): Int = db.txns().postDue(now)

    suspend fun nextUpcoming(): Long? = db.txns().nextUpcoming()

    /** New expense's Recent chips: the last few different entries of [type] (see [distinctRecent]). */
    suspend fun recentEntries(type: String, limit: Int): List<TxnEntity> =
        distinctRecent(db.txns().recentOfType(type, RECENT_LOOKBACK), limit)

    /** Paid to suggestions (87): the most used payees, or those starting with what was typed. */
    suspend fun payeeSuggestions(type: String, typed: String, limit: Int = 4): List<String> =
        if (typed.isBlank()) db.txns().topPayees(type, limit) else db.txns().payeesStarting(typed.trim(), limit)

    /** The category usually picked for [payee] (most often, then latest); [raw] is the message's own spelling. */
    suspend fun usualCategoryFor(payee: String, type: String, raw: String = payee): Long? =
        db.txns().usualCategoriesFor(payee.trim(), raw.trim(), type, 1).firstOrNull()

    /** The category used last for [type], for "Last category" (Customise › Expense). */
    /** Categories used most often for [type], most first (New expense's category chips, 17). */
    suspend fun topCategories(type: String, limit: Int): List<Long> = db.txns().topCategories(type, limit)

    suspend fun lastCategory(type: String): Long? = db.txns().lastCategory(type)

    val attachmentsDao get() = db.extras()

    val categories: Flow<List<CategoryEntity>> = seededFlow { db.categories().all() }

    /** The newest [limit] rows (Today's Recent). Screens never read the whole history: see [between], [forAccount]. */
    fun latest(limit: Int): Flow<List<TxnView>> = seededFlow { db.txns().latest(limit) }

    /** Every row, for the CSV export only (one read, off the main thread). */
    fun everything(): Flow<List<TxnView>> = seededFlow { db.txns().latest(Int.MAX_VALUE) }

    /** One account's rows, newest first, at most [limit]. */
    fun forAccount(accountId: Long, limit: Int): Flow<List<TxnView>> = seededFlow { db.txns().forAccount(accountId, limit) }

    /** A card's spending from [from] (its statement day) on. */
    fun expenseSince(accountId: Long, from: Long): Flow<Long> = seededFlow { db.txns().expenseSince(accountId, from) }

    /** Activity's filter columns for every posted row: the Filter sheet's count and "older months to load". */
    val postedKeys: Flow<List<FilterKey>> = seededFlow { db.txns().postedKeys() }

    fun txn(id: Long): Flow<TxnView?> = seededFlow { db.txns().view(id) }

    /** Transactions in [from, to), newest first. */
    fun between(from: Long, to: Long): Flow<List<TxnView>> = seededFlow { db.txns().between(from, to) }

    /** Dated in the future, soonest first. */
    val upcoming: Flow<List<TxnView>> = seededFlow { db.txns().upcoming() }

    fun account(id: Long): Flow<AccountEntity?> = seededFlow { db.accounts().get(id) }

    /** Money set aside in goals (part of net worth, out of the accounts it came from). */
    val goalsSaved: Flow<Long> = goalsSavedFlow()

    private fun goalsSavedFlow(): Flow<Long> = seededFlow { db.txns().goalSums() }.map { l -> l.sumOf { it.total } }

    suspend fun updateAccount(account: AccountEntity) = db.accounts().update(account)

    /** Savings (202–204, 211): money put in, newest first; its instalment bill; instalments paid by account. */
    fun moneyIn(accountId: Long): Flow<List<SavingsIn>> = seededFlow { db.savings().moneyIn(accountId) }
    fun savingsBill(accountId: Long): Flow<BillEntity?> = seededFlow { db.savings().billFlow(accountId) }
    val savingsPaid: Flow<List<AccountInSum>> = seededFlow { db.savings().paidCounts() }

    /** Add money to a Once savings account (205): the transfer and its new deposit. */
    suspend fun addDeposit(txn: TxnEntity, rate: String, maturity: Long): Long = db.withTransaction {
        val id = save(txn)
        db.savings().putDeposit(DepositEntity(id, txn.toAccountId!!, rate, maturity))
        id
    }

    /** A new account; a card's digits become its first card, named [cardName] ("Sapphiro"; "" shows as "Card"). */
    suspend fun addAccount(account: AccountEntity, cardName: String = ""): Long = db.withTransaction {
        val id = db.accounts().insert(account)
        // A card's digits are its first card, so messages from it keep finding the account.
        if (account.type == AccountType.CARD && !account.number.isNullOrEmpty()) db.cards().insert(CardEntity(accountId = id, name = cardName, number = account.number))
        id
    }

    /** Cards on every card account's bill (v8), in order. */
    val cards: Flow<List<CardEntity>> = seededFlow { db.cards().all() }

    /** How many expenses each card paid since [from] (186). */
    fun cardCounts(from: Long): Flow<Map<Long, Int>> = seededFlow { db.cards().counts(from) }.map { l -> l.associate { it.cardId to it.count } }

    /** Add another card on this bill (189), or a same-bill card from New credit card (191). */
    suspend fun addCard(accountId: Long, name: String, number: String) = db.withTransaction {
        val existing = db.cards().of(accountId)
        db.cards().insert(CardEntity(accountId = accountId, name = name, number = number, sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1))
        syncCardNumber(accountId)
    }

    /**
     * Edit account (188): the account's cards become [cards] (id 0 = new). Removed cards' payments
     * lose their "Paid with"; with one card left, none of the account's payments keep one.
     */
    suspend fun setCards(accountId: Long, cards: List<CardEntity>) = db.withTransaction {
        val before = db.cards().of(accountId)
        before.filter { b -> cards.none { it.id == b.id } }.forEach { db.cards().forgetCard(it.id); db.cards().delete(it.id) }
        cards.forEachIndexed { i, c ->
            val row = c.copy(accountId = accountId, sortOrder = i)
            if (c.id == 0L) db.cards().insert(row) else db.cards().update(row)
        }
        if (cards.size < 2) db.cards().forgetCards(accountId)
        syncCardNumber(accountId)
    }

    /** The account keeps its first card's digits as its number (lists, older code paths). */
    private suspend fun syncCardNumber(accountId: Long) {
        val a = db.accounts().get(accountId).first() ?: return
        val first = db.cards().of(accountId).firstOrNull()?.number
        if (first != null && first != a.number) db.accounts().update(a.copy(number = first))
    }

    suspend fun get(id: Long): TxnEntity? = db.txns().get(id)

    fun spent(period: BudgetPeriod): Flow<Long> = seededFlow { db.txns().spent(period.startMillis(), period.endMillis()) }

    fun spentByCategory(period: BudgetPeriod): Flow<Map<Long, Long>> =
        seededFlow { db.txns().spentByCategory(period.startMillis(), period.endMillis()) }.mapToMap()

    fun overallBudget(period: BudgetPeriod): Flow<BudgetEntity?> = seededFlow { db.budgets().overall(period.month.toString()) }

    val txnCount: Flow<Int> = seededFlow { db.txns().count() }

    val hasSamples: Flow<Boolean> = seededFlow { db.txns().hasSamples() }

    /**
     * Saves a new or edited transaction and returns its id. The first real entry removes the sample
     * month (CLAUDE.md § Behaviour), keeping any sample account the entry uses.
     */
    suspend fun save(input: TxnEntity): Long {
        val txn = withBase(input)
        val before = if (txn.id != 0L) db.txns().get(txn.id) else null
        val id = db.withTransaction {
            val id = db.txns().upsert(txn).let { if (it == -1L) txn.id else it }
            // A split no longer adds up once the amount or type changes: it becomes one category again.
            if (before != null && (before.amount != txn.amount || txn.type != TxnType.EXPENSE)) db.next().clearSplits(id)
            if (!txn.isSample) removeSamplesInTransaction()
            id
        }
        afterSave?.invoke(txn.copy(id = id), before)
        changed?.invoke()
        return id
    }

    /**
     * Runs after every save (set up in AppContainer): a captured payment may settle a due bill;
     * budgets may cross 80% / 100%. [before] is the row it replaced, if any.
     */
    var afterSave: (suspend (saved: TxnEntity, before: TxnEntity?) -> Unit)? = null

    /** Runs after any change to transactions (the home-screen widget and the Review shortcut refresh). */
    var changed: (suspend () -> Unit)? = null

    /** What went with a deleted transaction: its refunds, and the bill due dates it paid. */
    private class WentWith(val refunds: List<TxnEntity>, val paid: List<BillOccurrenceEntity>)
    private val wentWith = java.util.concurrent.ConcurrentHashMap<Long, WentWith>()

    /**
     * Deletes and returns the transaction, for the Undo snackbar. Its refunds go with it (a refund of
     * nothing made spending negative), and a bill due date it paid is unpaid again so it comes back to
     * Coming up (QA 12 Nov). [restore] puts all of it back.
     */
    suspend fun delete(id: Long): TxnEntity? = db.withTransaction {
        val t = db.txns().get(id) ?: return@withTransaction null
        val paid = db.bills().occurrencesPaidBy(id)
        // The payment that paid a due date takes the rest of it along (an EMI's interest row); deleting
        // just the interest leaves the principal and the due date paid.
        val refunds = db.next().refundsOf(id).first() +
            paid.flatMap { o -> db.txns().byOccurrence(o.id) }.filter { it.id != id }
        refunds.forEach { db.txns().delete(it) }
        paid.forEach { db.bills().updateOccurrence(it.copy(status = OccurrenceStatus.UPCOMING, txnId = null, amount = null)) }
        db.txns().delete(t)
        if (refunds.isNotEmpty() || paid.isNotEmpty()) wentWith[id] = WentWith(refunds, paid)
        t
    }.also { if (it != null) changed?.invoke() }

    /** Puts a deleted transaction back with its old id, with what went with it. */
    suspend fun restore(txn: TxnEntity) {
        db.withTransaction {
            db.txns().upsert(txn)
            wentWith.remove(txn.id)?.let { w ->
                w.refunds.forEach { db.txns().upsert(it) }
                w.paid.forEach { db.bills().updateOccurrence(it) }
            }
        }
        changed?.invoke()
    }

    suspend fun saveCategory(c: CategoryEntity): Long = db.categories().upsert(c).let { if (it == -1L) c.id else it }

    /**
     * [txn] with its baseAmount in the main currency, at the rate typed in Customise › Currency (no
     * rate: as it is, like net worth). Every spending and income total adds baseAmount.
     */
    suspend fun withBase(txn: TxnEntity): TxnEntity {
        val main = settings.settings.first().mainCurrency
        if (txn.currency == main) return if (txn.baseAmount == txn.amount) txn else txn.copy(baseAmount = txn.amount)
        val rate = db.extras().rate(txn.currency)?.perPrimary
        val base = app.burrow.money.domain.Fx.toMain(txn.amount, txn.currency, main, rate) ?: return txn
        return txn.copy(baseAmount = base)
    }

    /** Once at start: payments in another currency saved with baseAmount = amount get their converted value. */
    suspend fun fixBaseAmounts() {
        val main = settings.settings.first().mainCurrency
        for (r in db.extras().rates().first()) {
            if (r.currency == main) continue
            for (t in db.txns().unconverted(r.currency)) {
                app.burrow.money.domain.Fx.toMain(t.amount, t.currency, main, r.perPrimary)?.let { b -> if (b != t.baseAmount) db.txns().setBase(t.id, b) }
            }
        }
    }

    /** Deletes a category: its transactions and bills move to Other, its budgets go. */
    suspend fun deleteCategory(id: Long) = db.withTransaction {
        val cats = db.categories().all().first()
        val target = cats.firstOrNull { it.id != id && it.kind == cats.first { c -> c.id == id }.kind && it.icon == "category" }
            ?: cats.first { it.id != id }
        db.categories().moveTxns(id, target.id)
        db.categories().moveBills(id, target.id)
        db.categories().moveSplits(id, target.id)
        db.categories().moveFavourites(id, target.id)
        db.categories().deleteBudgets(id)
        db.categories().archive(id)
    }

    /**
     * Delete all data (More › About): every table emptied. The default categories come back on the
     * next read; the sample month does not.
     */
    suspend fun deleteAll() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        seedLock.withLock { seeded = false }
    }

    /**
     * Welcome's currency step: the sample month again, sized for [currency], while nothing real has
     * been added. It was made on first open in the region's currency (₹47 samples otherwise).
     */
    suspend fun resampleIn(currency: String) = db.withTransaction {
        if (!samplesShown() || db.txns().realCount() > 0) return@withTransaction
        removeSamplesInTransaction()
        val s = settings.settings.first()
        insertSamples(db.categories().idsInOrder(), currency, MoneyFormat.of(currency).digits, s.monthStartDay)
    }

    private suspend fun samplesShown() = db.txns().hasSamples().first()

    /** Accounts of yours in [currency]: asked about when the main currency changes. */
    suspend fun accountsIn(currency: String): Int = db.accounts().countIn(currency)

    /**
     * Moves accounts (and their transactions, bills and favourites) from [from] to [to], for a new main
     * currency. A currency with other decimals rescales the amounts first, so ₹75,000 stays 75,000.
     */
    suspend fun moveCurrency(from: String, to: String) {
        db.withTransaction {
            val (mul, div) = scale(from, to)
            if (mul != div) db.currencyMove().run {
                splits(from, mul, div); occurrences(from, mul, div); bills(from, mul, div)
                favourites(from, mul, div); txns(from, mul, div); accounts(from, mul, div)
            }
            db.accounts().moveCurrency(from, to)
            db.txns().moveCurrency(from, to)
        }
        changed?.invoke()
    }

    /** A new main currency: budgets and goals (kept in it) follow its decimals, whether accounts move or not. */
    suspend fun rescaleMain(from: String, to: String) {
        val (mul, div) = scale(from, to)
        if (mul != div) db.withTransaction { db.currencyMove().budgets(mul, div); db.currencyMove().goals(mul, div) }
    }

    /** 10^(to's decimals) over 10^(from's): ₹ → ¥ is 1 / 100. */
    private fun scale(from: String, to: String): Pair<Long, Long> {
        fun pow(n: Int): Long { var u = 1L; repeat(n) { u *= 10 }; return u }
        val a = minorDigits(from); val b = minorDigits(to)
        return if (b >= a) pow(b - a) to 1L else 1L to pow(a - b)
    }

    /** "Remove" on the sample note. */
    suspend fun removeSamples() = db.withTransaction { removeSamplesInTransaction() }

    private suspend fun removeSamplesInTransaction() {
        db.txns().deleteSamples()
        db.budgets().deleteSamples()
        db.bills().deleteSampleOccurrences()
        db.bills().deleteSamples()
        db.goals().deleteSamples()
        db.accounts().keepUsedSampleAccounts()
        db.accounts().deleteSamples()
    }
}

fun BudgetPeriod.startMillis(zone: ZoneId = ZoneId.systemDefault()) = start.atStartOfDay(zone).toInstant().toEpochMilli()
fun BudgetPeriod.endMillis(zone: ZoneId = ZoneId.systemDefault()) = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

private fun Flow<List<CategorySum>>.mapToMap(): Flow<Map<Long, Long>> = flow { collect { list -> emit(list.associate { it.categoryId to it.total }) } }

/** How many of the latest entries New expense looks through for its Recent chips. */
private const val RECENT_LOOKBACK = 40

/**
 * The newest entry for each payee (or category, when there is none), newest first: "₹250 Swiggy"
 * once, even after ten Swiggy orders, so the chips cover different things.
 */
fun distinctRecent(latest: List<TxnEntity>, limit: Int): List<TxnEntity> =
    latest.distinctBy { it.payee?.trim()?.lowercase()?.ifBlank { null } ?: "#${it.categoryId}" }.take(limit)
