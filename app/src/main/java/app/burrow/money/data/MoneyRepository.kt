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
data class AccountWithBalance(val account: AccountEntity, val balance: Long)

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
        val total = counted.sumOf { app.burrow.money.domain.Fx.toMain(it.balance, it.account.currency, main, rateOf[it.account.currency]) ?: 0L } + goals
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
    suspend fun postDue(now: Long = System.currentTimeMillis()): Int = db.txns().postDue(now)

    suspend fun nextUpcoming(): Long? = db.txns().nextUpcoming()

    /** Paid to suggestions (87): the most used payees, or those starting with what was typed. */
    suspend fun payeeSuggestions(type: String, typed: String, limit: Int = 4): List<String> =
        if (typed.isBlank()) db.txns().topPayees(type, limit) else db.txns().payeesStarting(typed.trim(), limit)

    /** The category usually picked for [payee] (most often, then latest); [raw] is the message's own spelling. */
    suspend fun usualCategoryFor(payee: String, type: String, raw: String = payee): Long? =
        db.txns().usualCategoriesFor(payee.trim(), raw.trim(), type, 1).firstOrNull()

    /** The category used last for [type], for "Last category" (Customise › Expense). */
    suspend fun lastCategory(type: String): Long? = db.txns().lastCategory(type)

    val attachmentsDao get() = db.extras()

    val categories: Flow<List<CategoryEntity>> = seededFlow { db.categories().all() }

    /** Newest first. Activity reads everything for now; paging by month comes with the 20k-row test (Day 28). */
    fun latest(limit: Int = Int.MAX_VALUE): Flow<List<TxnView>> = seededFlow { db.txns().latest(limit) }

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

    suspend fun addAccount(account: AccountEntity): Long = db.accounts().insert(account)

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
    suspend fun save(txn: TxnEntity): Long {
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

    /** Deletes and returns the transaction, for the Undo snackbar. */
    suspend fun delete(id: Long): TxnEntity? = db.txns().get(id)?.also { db.txns().delete(it); changed?.invoke() }

    /** Puts a deleted transaction back with its old id. */
    suspend fun restore(txn: TxnEntity) {
        db.txns().upsert(txn)
        changed?.invoke()
    }

    suspend fun saveCategory(c: CategoryEntity): Long = db.categories().upsert(c).let { if (it == -1L) c.id else it }

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
