package app.burrow.money.data

import androidx.room.withTransaction
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.ImportRow
import app.burrow.money.domain.Importer
import app.burrow.money.domain.recordedByItself
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * Transactions as the numbers see them: a split payment becomes one row per part (each in its own
 * category), a refund becomes negative spending in the category it paid back. Activity still shows
 * the real rows; Insights, Today's widgets and Category insights read these.
 */
fun expandForStats(views: List<TxnView>, parts: List<SplitView>): List<TxnView> {
    val byTxn = parts.groupBy { it.split.txnId }
    return views.flatMap { v ->
        val mine = byTxn[v.txn.id]
        when {
            v.txn.type == TxnType.REFUND -> listOf(v.copy(txn = v.txn.copy(type = TxnType.EXPENSE, amount = -v.txn.amount, baseAmount = -v.txn.baseAmount)))
            mine != null && v.txn.type == TxnType.EXPENSE -> mine.map { p ->
                val base = if (v.txn.amount == 0L) p.split.amount else v.txn.baseAmount * p.split.amount / v.txn.amount
                v.copy(
                    txn = v.txn.copy(categoryId = p.split.categoryId, amount = p.split.amount, baseAmount = base),
                    categoryName = p.categoryName, categoryIcon = p.categoryIcon, categoryHue = p.categoryHue,
                )
            }
            else -> listOf(v)
        }
    }
}

/** "131 of 142 payments this month · you typed 11" (15). */
data class Trust(val found: Int, val total: Int) {
    val typed get() = total - found
    val percent get() = recordedByItself(found, total)
}

/** An import ready to run (11): the rows, what each of their names maps to, what to skip. */
data class ImportPlan(
    val source: String,
    val rows: List<ImportRow>,
    val categoryFor: Map<String, Long>,
    val accountFor: Map<String, Long>,
    /** Where rows without an account (or with one not mapped) go; null when every row names one. */
    val fallbackAccount: Long?,
    val skipDuplicates: Boolean,
    /** Their names with nothing to match: made as new categories / accounts first. */
    val newCategories: Map<String, CategoryEntity> = emptyMap(),
    val newAccounts: Map<String, AccountEntity> = emptyMap(),
    /** Replace: the user's transactions go first (accounts, categories, budgets, bills and goals stay). */
    val replace: Boolean = false,
)

/**
 * The next round (burrow-next): splits, refunds, favourites, import, trust in capture. Uses the
 * same database and saves through [MoneyRepository] so samples go and budgets are checked.
 */
class NextRepository(private val db: BurrowDatabase, private val money: MoneyRepository) {
    private val zone = ZoneId.systemDefault()
    private fun <T> seeded(source: () -> Flow<T>): Flow<T> = flow { money.ensureSeeded(); emitAll(source()) }

    /** Every transaction, newest first, with splits and refunds unfolded for the numbers. */
    val stats: Flow<List<TxnView>> = combine(money.latest(), seeded { db.next().splits() }) { views, parts -> expandForStats(views, parts) }

    // Splits (07, 08)
    fun splitsOf(txnId: Long): Flow<List<SplitEntity>> = seeded { db.next().splitsOf(txnId) }

    /** Replaces [txnId]'s parts (an empty list un-splits it); hands back how to undo. */
    suspend fun saveSplits(txnId: Long, parts: List<SplitEntity>): suspend () -> Unit {
        val before = db.next().splitsFor(txnId)
        val txn = db.txns().get(txnId)
        db.withTransaction {
            db.next().clearSplits(txnId)
            db.next().insertSplits(parts.filter { it.amount > 0 }.map { it.copy(id = 0, txnId = txnId) })
            // The payment's own category becomes its biggest part, so lists and filters show something sensible.
            val main = parts.maxByOrNull { it.amount }?.categoryId
            if (txn != null && main != null) db.txns().upsert(txn.copy(categoryId = main, updatedAt = System.currentTimeMillis()))
        }
        return {
            db.withTransaction {
                db.next().clearSplits(txnId)
                db.next().insertSplits(before.map { it.copy(id = 0) })
                if (txn != null) db.txns().upsert(txn)
            }
        }
    }

    // Refunds (09)
    fun refundsOf(txnId: Long): Flow<List<TxnEntity>> = seeded { db.next().refundsOf(txnId) }

    /**
     * Money back for [original] into [accountId]: lowers its category's spending, never income.
     * A refund of a sample payment is a sample too: as a real entry it would clear the sample month,
     * the refunded payment with it ("This transaction was deleted" right after saving).
     */
    suspend fun addRefund(original: TxnEntity, amount: Long, accountId: Long): suspend () -> Unit {
        val now = System.currentTimeMillis()
        val id = money.save(
            TxnEntity(
                type = TxnType.REFUND, amount = amount, currency = original.currency,
                baseAmount = if (original.amount == 0L) amount else original.baseAmount * amount / original.amount,
                accountId = accountId, categoryId = original.categoryId, payee = original.payee,
                at = now.coerceAtLeast(original.at), source = "MANUAL", excludeFromStats = original.excludeFromStats,
                createdAt = now, updatedAt = now, refundOf = original.id, isSample = original.isSample,
            ),
        )
        return { money.delete(id) }
    }

    // Favourites (04, 05)
    val favourites: Flow<List<FavouriteView>> = seeded { db.next().favourites() }

    suspend fun addFavourite(name: String, amount: Long, categoryId: Long, accountId: Long): Long =
        db.next().upsertFavourite(FavouriteEntity(name = name.trim(), amount = amount, categoryId = categoryId, accountId = accountId, sortOrder = db.next().nextFavouriteOrder()))

    suspend fun removeFavourite(id: Long): (suspend () -> Unit)? {
        val f = db.next().favourite(id) ?: return null
        db.next().deleteFavourite(id)
        return { db.next().upsertFavourite(f) }
    }

    /** Saves the order the user dragged the favourites into. */
    suspend fun reorderFavourites(ids: List<Long>) = db.withTransaction { ids.forEachIndexed { i, id -> db.next().setFavouriteOrder(id, i) } }

    /** One tap on a favourite chip: the expense is saved to now. Returns its id. */
    suspend fun spendFavourite(f: FavouriteEntity): Long {
        val now = System.currentTimeMillis()
        val account = db.accounts().get(f.accountId).first()
        return money.save(
            TxnEntity(
                type = TxnType.EXPENSE, amount = f.amount, currency = account?.currency ?: "INR", baseAmount = f.amount,
                accountId = f.accountId, categoryId = f.categoryId, payee = f.name, at = now, createdAt = now, updatedAt = now,
            ),
        )
    }

    // Trust in capture (15)
    fun trust(period: BudgetPeriod): Flow<Trust> = seeded { db.next().sourceCounts(period.startMillis(), period.endMillis()) }.map { l ->
        Trust(found = l.filter { it.source == "SMS" || it.source == "NOTIFICATION" }.sumOf { it.count }, total = l.sumOf { it.count })
    }


    fun captureFor(txnId: Long): Flow<CaptureEntity?> = seeded { db.next().captureFor(txnId) }

    /** Spending today (expenses less refunds), for the daily number. */
    fun spentToday(today: LocalDate = LocalDate.now(zone)): Flow<Long> = seeded {
        db.txns().spent(today.atStartOfDay(zone).toInstant().toEpochMilli(), today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    // Import (10, 11)
    /** How many of the user's own transactions there are, for Import's "Replace your N transactions". */
    suspend fun realTxnCount(): Int = db.next().realTxnCount()

    suspend fun importMaps(source: String): List<ImportMapEntity> = db.next().importMaps(source)

    /** Rows of [rows] already here: same day, amount and payee. */
    suspend fun duplicates(rows: List<ImportRow>): Set<ImportRow> {
        if (rows.isEmpty()) return emptySet()
        val from = rows.minOf { it.date }.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val to = rows.maxOf { it.date }.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val here = db.next().keysBetween(from, to).map { k ->
            Triple(java.time.Instant.ofEpochMilli(k.at).atZone(zone).toLocalDate(), k.amount, Importer.key(k.payee))
        }.toSet()
        return rows.filter { Importer.isDuplicate(it, here) }.toSet()
    }

    /** Adds the rows (skipping ones already here when asked), remembers the mappings. Returns how many were added. */
    suspend fun runImport(plan: ImportPlan): Int {
        val skip = if (plan.skipDuplicates && !plan.replace) duplicates(plan.rows) else emptySet()
        val now = System.currentTimeMillis()
        var added = 0
        // Read before the transaction: flows don't see rows it hasn't committed yet.
        val currencyOf = money.accounts.first().associate { it.account.id to it.account.currency }.toMutableMap()
        val kindOf = money.categories.first().associate { it.id to it.kind }.toMutableMap()
        val otherOf = money.categories.first().filter { it.icon == "category" }.associate { it.kind to it.id }
        db.withTransaction {
            if (plan.replace) {
                db.next().clearRealSplits()
                db.next().clearRealTags()
                db.next().clearRealAttachments()
                db.next().clearRealTxns()
            }
            // New categories and accounts first, so their rows can point at them.
            val categoryFor = plan.categoryFor + plan.newCategories.mapValues { (_, c) -> db.categories().upsert(c).also { kindOf[it] = c.kind } }
            val accountFor = plan.accountFor + plan.newAccounts.mapValues { (_, a) -> db.accounts().insert(a).also { currencyOf[it] = a.currency } }
            fun accountOf(r: ImportRow) = r.account?.let { accountFor[it] } ?: plan.fallbackAccount
            fun base(r: ImportRow, type: String, accountId: Long) = TxnEntity(
                type = type, amount = r.amount, currency = r.currency ?: currencyOf[accountId] ?: "INR",
                baseAmount = r.amount, accountId = accountId, payee = r.payee?.take(80), note = r.note?.take(500),
                at = r.date.atZone(zone).toInstant().toEpochMilli(), source = "IMPORT", createdAt = now, updatedAt = now,
            )
            val rows = plan.rows.filter { it !in skip }
            val payments = rows.filter { !it.adjustment }.mapNotNull { r ->
                val accountId = accountOf(r) ?: return@mapNotNull null
                val kind = if (r.income) CategoryKind.INCOME else CategoryKind.EXPENSE
                // A row on the other side of its category (one refund in "Misc") goes to Other.
                val cat = r.category?.let { categoryFor[it] }?.takeIf { kindOf[it] == kind } ?: otherOf[kind]
                base(r, if (r.income) TxnType.INCOME else TxnType.EXPENSE, accountId).copy(categoryId = cat)
            }
            // Moves between their own accounts: transfers when both halves are here, else "Fix balance".
            val (pairs, single) = Importer.pairTransfers(rows)
            val transfers = pairs.mapNotNull { t ->
                val from = accountOf(t.from) ?: return@mapNotNull null
                val to = accountOf(t.to) ?: return@mapNotNull null
                if (from == to) null else base(t.from, TxnType.TRANSFER, from).copy(toAccountId = to)
            }
            val fixes = single.mapNotNull { r ->
                val accountId = accountOf(r) ?: return@mapNotNull null
                base(r, if (r.income) TxnType.ADJUST_UP else TxnType.ADJUST_DOWN, accountId)
            }
            val txns = payments + transfers + fixes
            if (txns.isNotEmpty()) db.txns().insertAll(txns)
            added = txns.size
            db.next().putImportMaps(
                categoryFor.map { (name, id) -> ImportMapEntity(plan.source, "CATEGORY", name, id) } +
                    accountFor.map { (name, id) -> ImportMapEntity(plan.source, "ACCOUNT", name, id) },
            )
        }
        if (added > 0) money.removeSamples()
        return added
    }

    /** App start: parts of payments that no longer exist. */
    suspend fun cleanOrphans() = db.next().cleanOrphanSplits()
}
