package app.burrow.money.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * Queries (docs/DATA.md). Totals are computed in SQL; screens get Flows and never block the main thread.
 * Type names are the strings in TxnType / AccountType / CategoryKind.
 */

object TxnType {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val TRANSFER = "TRANSFER"
    /** "Fix balance": moves an account's balance, never spending or income. */
    const val ADJUST_UP = "ADJUST_UP"
    const val ADJUST_DOWN = "ADJUST_DOWN"
    /** v5: money back for an expense ([TxnEntity.refundOf]): raises the account, lowers that category's spending. */
    const val REFUND = "REFUND"
}

/** A transaction's state (v4). */
object TxnStatus {
    const val POSTED = "POSTED"
    /** Dated in the future: shown in Plan, posts on its day. */
    const val UPCOMING = "UPCOMING"
}

/** Lend or borrow (v4). */
object Lend {
    const val LENT = "LENT"
    const val BORROWED = "BORROWED"
    const val SETTLE = "SETTLE"
}

object CategoryKind {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
}

/** Cards and loans start out of net worth (their dues are already in spending; 86). */
fun defaultInNetWorth(type: String) = type != AccountType.CARD && type != AccountType.LOAN

object AccountType {
    const val BANK = "BANK"
    const val CASH = "CASH"
    const val WALLET = "WALLET"
    const val CARD = "CARD"
    const val LOAN = "LOAN"
    const val SAVINGS = "SAVINGS"
}

/** A transaction with the names a row needs, joined in one query. */
data class TxnView(
    @Embedded val txn: TxnEntity,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryHue: Float?,
    val accountName: String,
    val accountIcon: String,
    val accountHue: Float,
    val toAccountName: String?,
    val toAccountIcon: String?,
    val toAccountHue: Float?,
    /** "Add money" to a goal: the goal's name. */
    val goalName: String? = null,
)

/** SUM(amount) of transfers into one account. */
data class AccountInSum(val accountId: Long, val total: Long)

data class CategorySum(val categoryId: Long, val total: Long)

data class GoalSum(val goalId: Long, val total: Long)

/** A bill's due date with what a row needs from its bill. */
data class OccurrenceView(
    @Embedded val occurrence: BillOccurrenceEntity,
    val name: String,
    val kind: String,
    val billAmount: Long,
    val accountId: Long,
    val accountName: String,
    val categoryId: Long?,
    val categoryName: String?,
    /** The bill's own circle, else its category's. */
    val icon: String?,
    val hue: Float?,
    val repeatRule: String,
    val onDue: String,
    val remindersOn: Boolean,
    val remindDaysBefore: Int,
    val remindAtMinutes: Int,
) {
    val amount get() = occurrence.amount ?: billAmount
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM AccountEntity WHERE archived = 0 ORDER BY sortOrder, id")
    fun all(): Flow<List<AccountEntity>>

    /** Upcoming transactions count only with "Upcoming in balances" (Customise › Transactions). */
    @Query("SELECT accountId, type, SUM(amount) AS total FROM TxnEntity WHERE status = 'POSTED' OR :withUpcoming GROUP BY accountId, type")
    fun outSums(withUpcoming: Boolean): Flow<List<app.burrow.money.domain.TypeSum>>

    @Query("SELECT toAccountId AS accountId, SUM(amount) AS total FROM TxnEntity WHERE type = 'TRANSFER' AND toAccountId IS NOT NULL AND (status = 'POSTED' OR :withUpcoming) GROUP BY toAccountId")
    fun inSums(withUpcoming: Boolean): Flow<List<AccountInSum>>

    @Query("SELECT * FROM AccountEntity WHERE id = :id")
    fun get(id: Long): Flow<AccountEntity?>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    /** A sample account the user really used stays, starting from zero (its sample history is gone). */
    @Query("UPDATE AccountEntity SET isSample = 0, openingBalance = 0 WHERE isSample = 1 AND id IN (SELECT accountId FROM TxnEntity WHERE isSample = 0 UNION SELECT toAccountId FROM TxnEntity WHERE isSample = 0 AND toAccountId IS NOT NULL)")
    suspend fun keepUsedSampleAccounts()

    @Query("DELETE FROM AccountEntity WHERE isSample = 1")
    suspend fun deleteSamples()

    /** The user's own accounts in [currency], for "Move your accounts to …?". */
    @Query("SELECT COUNT(*) FROM AccountEntity WHERE currency = :currency AND isSample = 0 AND archived = 0")
    suspend fun countIn(currency: String): Int

    @Query("UPDATE AccountEntity SET currency = :to WHERE currency = :from")
    suspend fun moveCurrency(from: String, to: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM CategoryEntity WHERE archived = 0 ORDER BY sortOrder, id")
    fun all(): Flow<List<CategoryEntity>>

    /** Every category id in default order (expense, then income), as the sample month expects. */
    @Query("SELECT id FROM CategoryEntity ORDER BY sortOrder, id")
    suspend fun idsInOrder(): List<Long>

    @Query("SELECT COUNT(*) FROM CategoryEntity")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Query("UPDATE CategoryEntity SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    /** A deleted category's transactions, bills and budgets move to [to] ("Other"). */
    @Query("UPDATE TxnEntity SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveTxns(from: Long, to: Long)

    @Query("UPDATE BillEntity SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveBills(from: Long, to: Long)

    @Query("UPDATE SplitEntity SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveSplits(from: Long, to: Long)

    @Query("UPDATE FavouriteEntity SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveFavourites(from: Long, to: Long)

    @Query("DELETE FROM BudgetEntity WHERE categoryId = :id")
    suspend fun deleteBudgets(id: Long)
}

@Dao
interface TxnDao {
    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.icon AS categoryIcon, c.colorHue AS categoryHue,
               a.name AS accountName, a.icon AS accountIcon, a.colorHue AS accountHue,
               ta.name AS toAccountName, ta.icon AS toAccountIcon, ta.colorHue AS toAccountHue, g.name AS goalName
        FROM TxnEntity t
        JOIN AccountEntity a ON a.id = t.accountId
        LEFT JOIN CategoryEntity c ON c.id = t.categoryId
        LEFT JOIN AccountEntity ta ON ta.id = t.toAccountId
        LEFT JOIN GoalEntity g ON g.id = t.goalId
        ORDER BY t.at DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun latest(limit: Int): Flow<List<TxnView>>

    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.icon AS categoryIcon, c.colorHue AS categoryHue,
               a.name AS accountName, a.icon AS accountIcon, a.colorHue AS accountHue,
               ta.name AS toAccountName, ta.icon AS toAccountIcon, ta.colorHue AS toAccountHue, g.name AS goalName
        FROM TxnEntity t
        JOIN AccountEntity a ON a.id = t.accountId
        LEFT JOIN CategoryEntity c ON c.id = t.categoryId
        LEFT JOIN AccountEntity ta ON ta.id = t.toAccountId
        LEFT JOIN GoalEntity g ON g.id = t.goalId
        WHERE t.id = :id
        """,
    )
    fun view(id: Long): Flow<TxnView?>

    @Query("SELECT * FROM TxnEntity WHERE id = :id")
    suspend fun get(id: Long): TxnEntity?

    /** Spending in [from, to): expenses less refunds. */
    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'REFUND' THEN -amount ELSE amount END), 0) FROM TxnEntity WHERE type IN ('EXPENSE', 'REFUND') AND excludeFromStats = 0 AND status = 'POSTED' AND at >= :from AND at < :to")
    fun spent(from: Long, to: Long): Flow<Long>

    /** Spending by category: a split payment counts in each part's category; refunds lower theirs. */
    @Query(
        """
        SELECT categoryId, SUM(total) AS total FROM (
            SELECT t.categoryId AS categoryId, CASE WHEN t.type = 'REFUND' THEN -t.amount ELSE t.amount END AS total FROM TxnEntity t
            WHERE t.type IN ('EXPENSE', 'REFUND') AND t.excludeFromStats = 0 AND t.status = 'POSTED' AND t.at >= :from AND t.at < :to
              AND NOT EXISTS (SELECT 1 FROM SplitEntity s WHERE s.txnId = t.id)
            UNION ALL
            SELECT s.categoryId AS categoryId, s.amount AS total FROM SplitEntity s JOIN TxnEntity t ON t.id = s.txnId
            WHERE t.type = 'EXPENSE' AND t.excludeFromStats = 0 AND t.status = 'POSTED' AND t.at >= :from AND t.at < :to
        ) WHERE categoryId IS NOT NULL GROUP BY categoryId
        """,
    )
    fun spentByCategory(from: Long, to: Long): Flow<List<CategorySum>>

    @Query("SELECT COUNT(*) FROM TxnEntity")
    fun count(): Flow<Int>

    /**
     * The categories used for a payee, the one picked most often first (ties: the latest). Counts
     * entries saved under [payee] and entries saved from a message whose payee was [raw], so a
     * payee renamed on save ("thirdwave@okicici" kept as "Third Wave Coffee") is still known.
     */
    @Query(
        """
        SELECT categoryId FROM TxnEntity
        WHERE type = :type AND categoryId IS NOT NULL AND (
            payee = :payee COLLATE NOCASE OR
            id IN (SELECT txnId FROM CaptureEntity WHERE txnId IS NOT NULL AND payee = :raw COLLATE NOCASE)
        )
        GROUP BY categoryId ORDER BY COUNT(*) DESC, MAX(at) DESC LIMIT :limit
        """,
    )
    suspend fun usualCategoriesFor(payee: String, raw: String, type: String, limit: Int): List<Long>

    @Query("SELECT categoryId FROM TxnEntity WHERE type = :type AND categoryId IS NOT NULL AND isSample = 0 ORDER BY at DESC LIMIT 1")
    suspend fun lastCategory(type: String): Long?

    /** Categories used most often for [type], for the other suggestions. */
    @Query("SELECT categoryId FROM TxnEntity WHERE type = :type AND categoryId IS NOT NULL GROUP BY categoryId ORDER BY COUNT(*) DESC LIMIT :limit")
    suspend fun topCategories(type: String, limit: Int): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM TxnEntity WHERE isSample = 1)")
    fun hasSamples(): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM TxnEntity WHERE isSample = 0")
    suspend fun realCount(): Int

    /** Transactions of the accounts just moved to [to] follow them. */
    @Query("UPDATE TxnEntity SET currency = :to WHERE currency = :from AND accountId IN (SELECT id FROM AccountEntity WHERE currency = :to)")
    suspend fun moveCurrency(from: String, to: String)

    /** Transactions in [from, to), newest first, with names: filters, Insights, budgets, accounts. */
    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.icon AS categoryIcon, c.colorHue AS categoryHue,
               a.name AS accountName, a.icon AS accountIcon, a.colorHue AS accountHue,
               ta.name AS toAccountName, ta.icon AS toAccountIcon, ta.colorHue AS toAccountHue, g.name AS goalName
        FROM TxnEntity t
        JOIN AccountEntity a ON a.id = t.accountId
        LEFT JOIN CategoryEntity c ON c.id = t.categoryId
        LEFT JOIN AccountEntity ta ON ta.id = t.toAccountId
        LEFT JOIN GoalEntity g ON g.id = t.goalId
        WHERE t.at >= :from AND t.at < :to
        ORDER BY t.at DESC, t.id DESC
        """,
    )
    fun between(from: Long, to: Long): Flow<List<TxnView>>

    /** Transactions dated in the future (Plan › Bills › Upcoming), soonest first. */
    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.icon AS categoryIcon, c.colorHue AS categoryHue,
               a.name AS accountName, a.icon AS accountIcon, a.colorHue AS accountHue,
               ta.name AS toAccountName, ta.icon AS toAccountIcon, ta.colorHue AS toAccountHue, g.name AS goalName
        FROM TxnEntity t
        JOIN AccountEntity a ON a.id = t.accountId
        LEFT JOIN CategoryEntity c ON c.id = t.categoryId
        LEFT JOIN AccountEntity ta ON ta.id = t.toAccountId
        LEFT JOIN GoalEntity g ON g.id = t.goalId
        WHERE t.status = 'UPCOMING'
        ORDER BY t.at, t.id
        """,
    )
    fun upcoming(): Flow<List<TxnView>>

    @Query("SELECT * FROM TxnEntity WHERE goalId = :goalId ORDER BY at DESC")
    fun forGoal(goalId: Long): Flow<List<TxnEntity>>

    @Query("SELECT goalId, SUM(amount) AS total FROM TxnEntity WHERE goalId IS NOT NULL AND status = 'POSTED' GROUP BY goalId")
    fun goalSums(): Flow<List<GoalSum>>

    @Query("SELECT MIN(at) FROM TxnEntity")
    fun firstAt(): Flow<Long?>

    @Query("DELETE FROM TxnEntity WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Upsert
    suspend fun upsert(txn: TxnEntity): Long

    @Insert
    suspend fun insertAll(txns: List<TxnEntity>)

    @Delete
    suspend fun delete(txn: TxnEntity)

    @Query("DELETE FROM TxnEntity WHERE isSample = 1")
    suspend fun deleteSamples()

    /** Upcoming transactions whose day has come, before they are posted (for the "added today" note). */
    @Query("SELECT * FROM TxnEntity WHERE status = 'UPCOMING' AND at <= :now ORDER BY at")
    suspend fun dueUpcoming(now: Long): List<TxnEntity>

    /** Upcoming transactions whose day has come become posted. */
    @Query("UPDATE TxnEntity SET status = 'POSTED' WHERE status = 'UPCOMING' AND at <= :now")
    suspend fun postDue(now: Long): Int

    @Query("SELECT MIN(at) FROM TxnEntity WHERE status = 'UPCOMING'")
    suspend fun nextUpcoming(): Long?

    /** Payees used most, for the Paid to suggestions (87). */
    @Query("SELECT payee FROM TxnEntity WHERE type = :type AND payee IS NOT NULL AND payee != '' GROUP BY payee COLLATE NOCASE ORDER BY COUNT(*) DESC, MAX(at) DESC LIMIT :limit")
    suspend fun topPayees(type: String, limit: Int): List<String>

    @Query("SELECT payee FROM TxnEntity WHERE payee LIKE :prefix || '%' AND payee IS NOT NULL GROUP BY payee COLLATE NOCASE ORDER BY COUNT(*) DESC LIMIT :limit")
    suspend fun payeesStarting(prefix: String, limit: Int): List<String>

    /** Expense categories used by a payee in each of the last months, for the subscription hint. */
    @Query("SELECT * FROM TxnEntity WHERE type = 'EXPENSE' AND at >= :since AND payee IS NOT NULL")
    suspend fun expensesSince(since: Long): List<TxnEntity>
}

@Dao
interface BudgetDao {
    /** The overall budget in force for [month] (yyyy-MM): the latest one set on or before it. */
    @Query("SELECT * FROM BudgetEntity WHERE categoryId IS NULL AND month <= :month ORDER BY month DESC LIMIT 1")
    fun overall(month: String): Flow<BudgetEntity?>

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Upsert
    suspend fun upsert(budget: BudgetEntity): Long

    /** Budgets of every category in force for [month]: the latest one set on or before it, per category. */
    @Query(
        """
        SELECT b.* FROM BudgetEntity b
        WHERE b.categoryId IS NOT NULL AND b.month = (
            SELECT MAX(b2.month) FROM BudgetEntity b2 WHERE b2.categoryId = b.categoryId AND b2.month <= :month
        ) AND b.amount > 0
        """,
    )
    fun categories(month: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM BudgetEntity WHERE month = :month AND ((:categoryId IS NULL AND categoryId IS NULL) OR categoryId = :categoryId) LIMIT 1")
    suspend fun exact(month: String, categoryId: Long?): BudgetEntity?

    @Query("DELETE FROM BudgetEntity WHERE isSample = 1")
    suspend fun deleteSamples()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM BillEntity WHERE archived = 0 ORDER BY name")
    fun all(): Flow<List<BillEntity>>

    @Query("SELECT * FROM BillEntity WHERE archived = 0")
    suspend fun list(): List<BillEntity>

    @Query("SELECT * FROM BillEntity WHERE id = :id")
    fun get(id: Long): Flow<BillEntity?>

    @Query("SELECT * FROM BillEntity WHERE id = :id")
    suspend fun find(id: Long): BillEntity?

    @Upsert
    suspend fun upsert(bill: BillEntity): Long

    @Query("UPDATE BillEntity SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query(
        """
        SELECT o.*, b.name AS name, b.kind AS kind, b.amount AS billAmount, b.accountId AS accountId, a.name AS accountName,
               b.categoryId AS categoryId, c.name AS categoryName, COALESCE(b.icon, c.icon) AS icon, COALESCE(b.colorHue, c.colorHue) AS hue,
               b.repeatRule AS repeatRule, b.onDue AS onDue, b.remindersOn AS remindersOn,
               b.remindDaysBefore AS remindDaysBefore, b.remindAtMinutes AS remindAtMinutes
        FROM BillOccurrenceEntity o
        JOIN BillEntity b ON b.id = o.billId
        JOIN AccountEntity a ON a.id = b.accountId
        LEFT JOIN CategoryEntity c ON c.id = b.categoryId
        WHERE b.archived = 0 AND o.dueDate >= :from AND o.dueDate < :to
        ORDER BY o.dueDate, b.name
        """,
    )
    fun occurrences(from: Long, to: Long): Flow<List<OccurrenceView>>

    @Query(
        """
        SELECT o.*, b.name AS name, b.kind AS kind, b.amount AS billAmount, b.accountId AS accountId, a.name AS accountName,
               b.categoryId AS categoryId, c.name AS categoryName, COALESCE(b.icon, c.icon) AS icon, COALESCE(b.colorHue, c.colorHue) AS hue,
               b.repeatRule AS repeatRule, b.onDue AS onDue, b.remindersOn AS remindersOn,
               b.remindDaysBefore AS remindDaysBefore, b.remindAtMinutes AS remindAtMinutes
        FROM BillOccurrenceEntity o
        JOIN BillEntity b ON b.id = o.billId
        JOIN AccountEntity a ON a.id = b.accountId
        LEFT JOIN CategoryEntity c ON c.id = b.categoryId
        WHERE b.archived = 0 AND o.billId = :billId
        ORDER BY o.dueDate DESC
        """,
    )
    fun occurrencesOf(billId: Long): Flow<List<OccurrenceView>>

    @Query("SELECT * FROM BillOccurrenceEntity WHERE id = :id")
    suspend fun occurrence(id: Long): BillOccurrenceEntity?

    @Query("SELECT MAX(dueDate) FROM BillOccurrenceEntity WHERE billId = :billId")
    suspend fun lastDue(billId: Long): Long?

    @Insert
    suspend fun insertOccurrences(list: List<BillOccurrenceEntity>)

    @Update
    suspend fun updateOccurrence(o: BillOccurrenceEntity)

    /** Unpaid future dates go when a bill changes; they are made again from its new rule. */
    @Query("DELETE FROM BillOccurrenceEntity WHERE billId = :billId AND status = 'UPCOMING' AND dueDate >= :from")
    suspend fun deleteUpcoming(billId: Long, from: Long)

    @Query("DELETE FROM BillOccurrenceEntity WHERE billId IN (SELECT id FROM BillEntity WHERE isSample = 1)")
    suspend fun deleteSampleOccurrences()

    @Query("DELETE FROM BillEntity WHERE isSample = 1")
    suspend fun deleteSamples()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM GoalEntity WHERE archived = 0 ORDER BY id")
    fun all(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM GoalEntity WHERE id = :id")
    fun get(id: Long): Flow<GoalEntity?>

    @Query("SELECT * FROM GoalEntity WHERE id = :id")
    suspend fun find(id: Long): GoalEntity?

    @Upsert
    suspend fun upsert(goal: GoalEntity): Long

    @Query("UPDATE GoalEntity SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM GoalEntity WHERE isSample = 1")
    suspend fun deleteSamples()
}

object CaptureStatus {
    const val REVIEW = "REVIEW"
    const val SAVED = "SAVED"
    const val IGNORED = "IGNORED"
    const val MERGED = "MERGED"
    /** A money message no rule could read: waits in "messages not understood" for Teach. */
    const val UNREAD = "UNREAD"
}

@Dao
interface CaptureDao {
    @Insert
    suspend fun insert(capture: CaptureEntity): Long

    @Update
    suspend fun update(capture: CaptureEntity)

    @Query("SELECT * FROM CaptureEntity WHERE id = :id")
    suspend fun get(id: Long): CaptureEntity?

    @Query("SELECT * FROM CaptureEntity WHERE status = :status ORDER BY receivedAt DESC")
    fun withStatus(status: String): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM CaptureEntity WHERE status = :status ORDER BY receivedAt DESC")
    suspend fun listWithStatus(status: String): List<CaptureEntity>

    @Query("SELECT COUNT(*) FROM CaptureEntity WHERE status = :status")
    fun count(status: String): Flow<Int>

    /** Which rule read each message (built-in rules keep no count of their own). */
    @Query("SELECT ruleId FROM CaptureEntity WHERE ruleId IS NOT NULL AND status != 'UNREAD'")
    fun ruleIds(): Flow<List<String>>

    /** Read captures since [since], for spotting the same payment twice (SMS and app). */
    @Query("SELECT * FROM CaptureEntity WHERE receivedAt >= :since AND amount IS NOT NULL")
    suspend fun since(since: Long): List<CaptureEntity>

    @Query("SELECT COUNT(*) FROM CaptureEntity WHERE status = 'SAVED' AND receivedAt >= :since")
    fun savedSince(since: Long): Flow<Int>

    @Query("SELECT DISTINCT ruleId FROM CaptureEntity WHERE status = 'SAVED' AND source = :source AND ruleId IS NOT NULL")
    fun savedRuleIds(source: String): Flow<List<String>>

    /** The 30-day promise (docs/CAPTURE.md § Keep): message text goes, the transaction stays. */
    @Query("UPDATE CaptureEntity SET body = '' WHERE receivedAt < :before AND body != ''")
    suspend fun forgetTextBefore(before: Long)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM CaptureRuleEntity ORDER BY createdAt DESC")
    fun all(): Flow<List<CaptureRuleEntity>>

    @Query("SELECT * FROM CaptureRuleEntity WHERE id = :id")
    fun get(id: String): Flow<CaptureRuleEntity?>

    @Upsert
    suspend fun upsert(rule: CaptureRuleEntity)

    @Query("UPDATE CaptureRuleEntity SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE CaptureRuleEntity SET uses = uses + 1 WHERE id = :id")
    suspend fun used(id: String)

    /** Rename and other edits: the rule's JSON only, so its uses, switch and place in the list stay. */
    @Query("UPDATE CaptureRuleEntity SET json = :json WHERE id = :id")
    suspend fun setJson(id: String, json: String)

    @Query("DELETE FROM CaptureRuleEntity WHERE id = :id")
    suspend fun delete(id: String)
}

/** Everything at once, for encrypted backups (docs/DATA.md § Backup). */
/** Lend / borrow, tags, attachments and exchange rates (v4). */
@Dao
interface ExtrasDao {
    @Query("SELECT * FROM PersonEntity ORDER BY name") fun people(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM PersonEntity WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun personNamed(name: String): PersonEntity?
    @Query("SELECT * FROM PersonEntity WHERE id = :id") suspend fun person(id: Long): PersonEntity?
    @Insert suspend fun addPerson(p: PersonEntity): Long
    @Update suspend fun updatePerson(p: PersonEntity)

    /** Each person's lent, borrowed and settled totals (signed in Kotlin). */
    @Query("SELECT personId, lend, type, SUM(amount) AS total FROM TxnEntity WHERE personId IS NOT NULL AND status = 'POSTED' GROUP BY personId, lend, type")
    fun lendSums(): Flow<List<LendSum>>

    @Query("SELECT personId, MIN(at) AS since FROM TxnEntity WHERE personId IS NOT NULL GROUP BY personId")
    fun lendSince(): Flow<List<PersonSince>>

    @Query("SELECT * FROM TagEntity ORDER BY name") fun tags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM TagEntity WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun tagNamed(name: String): TagEntity?
    @Insert suspend fun addTag(t: TagEntity): Long
    @Query("SELECT t.* FROM TagEntity t JOIN TxnTagEntity x ON x.tagId = t.id WHERE x.txnId = :txnId ORDER BY t.name") suspend fun tagsOf(txnId: Long): List<TagEntity>
    @Query("DELETE FROM TxnTagEntity WHERE txnId = :txnId") suspend fun clearTags(txnId: Long)
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun tag(links: List<TxnTagEntity>)
    @Query("SELECT txnId FROM TxnTagEntity WHERE tagId IN (:tagIds)") fun txnsTagged(tagIds: List<Long>): Flow<List<Long>>

    @Query("SELECT * FROM AttachmentEntity WHERE txnId = :txnId ORDER BY id") suspend fun attachments(txnId: Long): List<AttachmentEntity>
    @Query("SELECT * FROM AttachmentEntity WHERE txnId = :txnId ORDER BY id") fun attachmentsFlow(txnId: Long): Flow<List<AttachmentEntity>>
    @Insert suspend fun attach(a: AttachmentEntity): Long
    @Delete suspend fun detach(a: AttachmentEntity)
    @Query("DELETE FROM AttachmentEntity WHERE txnId = :txnId") suspend fun detachAll(txnId: Long)

    @Query("SELECT * FROM RateEntity ORDER BY currency") fun rates(): Flow<List<RateEntity>>
    @Query("SELECT * FROM RateEntity WHERE currency = :currency") suspend fun rate(currency: String): RateEntity?
    @Upsert suspend fun setRate(r: RateEntity)
    @Query("DELETE FROM RateEntity WHERE currency = :currency") suspend fun removeRate(currency: String)
}

data class LendSum(val personId: Long, val lend: String?, val type: String, val total: Long)

/** A split part with its category's name and circle. */
data class SplitView(
    @Embedded val split: SplitEntity,
    val categoryName: String,
    val categoryIcon: String,
    val categoryHue: Float,
)

/** A favourite with what its chip and row need. */
data class FavouriteView(
    @Embedded val favourite: FavouriteEntity,
    val categoryName: String,
    val categoryIcon: String,
    val categoryHue: Float,
    val accountName: String,
)

/** How many payments this period came from each source (MANUAL, SMS, NOTIFICATION, BILL…). */
data class SourceCount(val source: String, val count: Int)

/** Day, amount and payee of a transaction: enough to spot one already here (Import). */
data class TxnKey(val at: Long, val amount: Long, val payee: String?)

/** The next round (docs/IDEAS.md, burrow-next): split parts, refunds, favourites, import mappings, trust. */
@Dao
interface NextDao {
    // Import › Replace: the user's own transactions and what hangs off them (samples are left alone).
    @Query("DELETE FROM SplitEntity WHERE txnId IN (SELECT id FROM TxnEntity WHERE isSample = 0)") suspend fun clearRealSplits()
    @Query("DELETE FROM TxnTagEntity WHERE txnId IN (SELECT id FROM TxnEntity WHERE isSample = 0)") suspend fun clearRealTags()
    @Query("DELETE FROM AttachmentEntity WHERE txnId IN (SELECT id FROM TxnEntity WHERE isSample = 0)") suspend fun clearRealAttachments()
    @Query("DELETE FROM TxnEntity WHERE isSample = 0") suspend fun clearRealTxns()
    @Query("SELECT COUNT(*) FROM TxnEntity WHERE isSample = 0") suspend fun realTxnCount(): Int

    @Query(
        """
        SELECT s.*, c.name AS categoryName, c.icon AS categoryIcon, c.colorHue AS categoryHue
        FROM SplitEntity s JOIN CategoryEntity c ON c.id = s.categoryId JOIN TxnEntity t ON t.id = s.txnId
        ORDER BY s.txnId, s.id
        """,
    )
    fun splits(): Flow<List<SplitView>>

    @Query("SELECT * FROM SplitEntity WHERE txnId = :txnId ORDER BY id")
    fun splitsOf(txnId: Long): Flow<List<SplitEntity>>

    @Query("SELECT * FROM SplitEntity WHERE txnId = :txnId ORDER BY id")
    suspend fun splitsFor(txnId: Long): List<SplitEntity>

    @Query("DELETE FROM SplitEntity WHERE txnId = :txnId")
    suspend fun clearSplits(txnId: Long)

    @Insert
    suspend fun insertSplits(parts: List<SplitEntity>)

    /** Parts of payments that are gone (deleted, or the sample month) — run at app start. */
    @Query("DELETE FROM SplitEntity WHERE txnId NOT IN (SELECT id FROM TxnEntity)")
    suspend fun cleanOrphanSplits()

    @Query("SELECT * FROM TxnEntity WHERE refundOf = :txnId AND type = 'REFUND' ORDER BY at")
    fun refundsOf(txnId: Long): Flow<List<TxnEntity>>

    @Query(
        """
        SELECT f.*, c.name AS categoryName, c.icon AS categoryIcon, c.colorHue AS categoryHue, a.name AS accountName
        FROM FavouriteEntity f JOIN CategoryEntity c ON c.id = f.categoryId JOIN AccountEntity a ON a.id = f.accountId
        WHERE a.archived = 0
        ORDER BY f.sortOrder, f.id
        """,
    )
    fun favourites(): Flow<List<FavouriteView>>

    @Query("SELECT * FROM FavouriteEntity WHERE id = :id")
    suspend fun favourite(id: Long): FavouriteEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM FavouriteEntity")
    suspend fun nextFavouriteOrder(): Int

    @Upsert
    suspend fun upsertFavourite(f: FavouriteEntity): Long

    @Query("DELETE FROM FavouriteEntity WHERE id = :id")
    suspend fun deleteFavourite(id: Long)

    @Query("UPDATE FavouriteEntity SET sortOrder = :order WHERE id = :id")
    suspend fun setFavouriteOrder(id: Long, order: Int)

    @Query("SELECT * FROM ImportMapEntity WHERE source = :source")
    suspend fun importMaps(source: String): List<ImportMapEntity>

    @Upsert
    suspend fun putImportMaps(maps: List<ImportMapEntity>)

    @Query("SELECT at, amount, payee FROM TxnEntity WHERE at >= :from AND at < :to AND isSample = 0")
    suspend fun keysBetween(from: Long, to: Long): List<TxnKey>

    /** The message a payment was recorded from (21 "Recorded from SMS"). */
    @Query("SELECT * FROM CaptureEntity WHERE txnId = :txnId ORDER BY receivedAt DESC LIMIT 1")
    fun captureFor(txnId: Long): Flow<CaptureEntity?>

    /** Real payments in the period by source, for "Recorded by itself" (15). Imports and transfers aren't payments typed or found. */
    @Query("SELECT source, COUNT(*) AS count FROM TxnEntity WHERE at >= :from AND at < :to AND type IN ('EXPENSE', 'INCOME') AND isSample = 0 AND status = 'POSTED' AND source != 'IMPORT' GROUP BY source")
    fun sourceCounts(from: Long, to: Long): Flow<List<SourceCount>>

    /** Senders of the messages it couldn't read ("From Kotak and SBI Card"). */
    @Query("SELECT DISTINCT sender FROM CaptureEntity WHERE status = 'UNREAD' ORDER BY receivedAt DESC LIMIT 3")
    fun unreadSenders(): Flow<List<String>>
}
data class PersonSince(val personId: Long, val since: Long)

@Dao
interface BackupDao {
    @Query("SELECT * FROM AccountEntity") suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM CategoryEntity") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM TxnEntity") suspend fun txns(): List<TxnEntity>
    @Query("SELECT * FROM BillEntity") suspend fun bills(): List<BillEntity>
    @Query("SELECT * FROM BillOccurrenceEntity") suspend fun occurrences(): List<BillOccurrenceEntity>
    @Query("SELECT * FROM BudgetEntity") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM GoalEntity") suspend fun goals(): List<GoalEntity>
    @Query("SELECT * FROM CaptureRuleEntity") suspend fun rules(): List<CaptureRuleEntity>
    @Query("SELECT COUNT(*) FROM TxnEntity WHERE isSample = 0") suspend fun realTxnCount(): Int
    @Query("SELECT * FROM PersonEntity") suspend fun people(): List<PersonEntity>
    @Query("SELECT * FROM TagEntity") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM TxnTagEntity") suspend fun txnTags(): List<TxnTagEntity>
    @Query("SELECT * FROM AttachmentEntity") suspend fun attachments(): List<AttachmentEntity>
    @Query("SELECT * FROM RateEntity") suspend fun rates(): List<RateEntity>
    @Query("SELECT * FROM SplitEntity") suspend fun splits(): List<SplitEntity>
    @Query("SELECT * FROM FavouriteEntity") suspend fun favourites(): List<FavouriteEntity>
    @Query("SELECT * FROM ImportMapEntity") suspend fun importMaps(): List<ImportMapEntity>
    @Insert suspend fun putSplits(l: List<SplitEntity>)
    @Insert suspend fun putFavourites(l: List<FavouriteEntity>)
    @Insert suspend fun putImportMaps(l: List<ImportMapEntity>)
    @Query("DELETE FROM SplitEntity") suspend fun clearSplits()
    @Query("DELETE FROM FavouriteEntity") suspend fun clearFavourites()
    @Query("DELETE FROM ImportMapEntity") suspend fun clearImportMaps()
    @Insert suspend fun putPeople(l: List<PersonEntity>)
    @Insert suspend fun putTags(l: List<TagEntity>)
    @Insert suspend fun putTxnTags(l: List<TxnTagEntity>)
    @Insert suspend fun putAttachments(l: List<AttachmentEntity>)
    @Insert suspend fun putRates(l: List<RateEntity>)
    @Query("DELETE FROM PersonEntity") suspend fun clearPeople()
    @Query("DELETE FROM TagEntity") suspend fun clearTags()
    @Query("DELETE FROM TxnTagEntity") suspend fun clearTxnTags()
    @Query("DELETE FROM AttachmentEntity") suspend fun clearAttachments()
    @Query("DELETE FROM RateEntity") suspend fun clearRates()

    @Insert suspend fun putAccounts(l: List<AccountEntity>)
    @Insert suspend fun putCategories(l: List<CategoryEntity>)
    @Insert suspend fun putTxns(l: List<TxnEntity>)
    @Insert suspend fun putBills(l: List<BillEntity>)
    @Insert suspend fun putOccurrences(l: List<BillOccurrenceEntity>)
    @Insert suspend fun putBudgets(l: List<BudgetEntity>)
    @Insert suspend fun putGoals(l: List<GoalEntity>)
    @Insert suspend fun putRules(l: List<CaptureRuleEntity>)

    @Query("DELETE FROM AccountEntity") suspend fun clearAccounts()
    @Query("DELETE FROM CategoryEntity") suspend fun clearCategories()
    @Query("DELETE FROM TxnEntity") suspend fun clearTxns()
    @Query("DELETE FROM BillEntity") suspend fun clearBills()
    @Query("DELETE FROM BillOccurrenceEntity") suspend fun clearOccurrences()
    @Query("DELETE FROM BudgetEntity") suspend fun clearBudgets()
    @Query("DELETE FROM GoalEntity") suspend fun clearGoals()
    @Query("DELETE FROM CaptureRuleEntity") suspend fun clearRules()
    @Query("DELETE FROM CaptureEntity") suspend fun clearCaptures()
}
