package app.burrow.money.data

import androidx.compose.runtime.Immutable
import androidx.room.withTransaction
import app.burrow.money.Strings
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.domain.balances
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

@Immutable
data class AccountWithBalance(val account: AccountEntity, val balance: Long)

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
                db.withTransaction {
                    val ids = db.categories().insertAll(defaultCategories(strings))
                    val sample = SampleData.build(
                        today = LocalDate.now(), now = System.currentTimeMillis(), currency = s.mainCurrency,
                        digits = digits, monthStartDay = s.monthStartDay, categoryIds = ids,
                    )
                    val accountIds = sample.accounts.map { db.accounts().insert(it) }
                    db.txns().insertAll(
                        sample.txns.map { t -> t.copy(accountId = accountIds[t.accountId.toInt()], toAccountId = t.toAccountId?.let { accountIds[it.toInt()] }) },
                    )
                    db.budgets().insert(sample.budget)
                }
            }
            seeded = true
        }
    }

    private fun <T> seededFlow(source: () -> Flow<T>): Flow<T> = flow { ensureSeeded(); emitAll(source()) }

    val accounts: Flow<List<AccountWithBalance>> = seededFlow {
        val a = db.accounts()
        combine(a.all(), a.outSums(), a.inSums()) { accounts, out, into ->
            val b = balances(accounts.associate { it.id to it.openingBalance }, out, into.associate { it.accountId to it.total })
            accounts.map { AccountWithBalance(it, b[it.id] ?: it.openingBalance) }
        }
    }

    val categories: Flow<List<CategoryEntity>> = seededFlow { db.categories().all() }

    /** Newest first. Activity reads everything for now; paging by month comes with the 20k-row test (Day 28). */
    fun latest(limit: Int = Int.MAX_VALUE): Flow<List<TxnView>> = seededFlow { db.txns().latest(limit) }

    fun txn(id: Long): Flow<TxnView?> = seededFlow { db.txns().view(id) }

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
    suspend fun save(txn: TxnEntity): Long = db.withTransaction {
        val id = db.txns().upsert(txn).let { if (it == -1L) txn.id else it }
        if (!txn.isSample) removeSamplesInTransaction()
        id
    }

    /** Deletes and returns the transaction, for the Undo snackbar. */
    suspend fun delete(id: Long): TxnEntity? = db.txns().get(id)?.also { db.txns().delete(it) }

    /** Puts a deleted transaction back with its old id. */
    suspend fun restore(txn: TxnEntity) {
        db.txns().upsert(txn)
    }

    /** "Remove" on the sample note. */
    suspend fun removeSamples() = db.withTransaction { removeSamplesInTransaction() }

    private suspend fun removeSamplesInTransaction() {
        db.txns().deleteSamples()
        db.budgets().deleteSamples()
        db.accounts().keepUsedSampleAccounts()
        db.accounts().deleteSamples()
    }
}

fun BudgetPeriod.startMillis(zone: ZoneId = ZoneId.systemDefault()) = start.atStartOfDay(zone).toInstant().toEpochMilli()
fun BudgetPeriod.endMillis(zone: ZoneId = ZoneId.systemDefault()) = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

private fun Flow<List<CategorySum>>.mapToMap(): Flow<Map<Long, Long>> = flow { collect { list -> emit(list.associate { it.categoryId to it.total }) } }
