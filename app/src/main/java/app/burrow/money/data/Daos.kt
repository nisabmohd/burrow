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
}

object CategoryKind {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
}

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

    @Query("SELECT accountId, type, SUM(amount) AS total FROM TxnEntity GROUP BY accountId, type")
    fun outSums(): Flow<List<app.burrow.money.domain.TypeSum>>

    @Query("SELECT toAccountId AS accountId, SUM(amount) AS total FROM TxnEntity WHERE type = 'TRANSFER' AND toAccountId IS NOT NULL GROUP BY toAccountId")
    fun inSums(): Flow<List<AccountInSum>>

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
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM CategoryEntity WHERE archived = 0 ORDER BY sortOrder, id")
    fun all(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM CategoryEntity")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>
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

    @Query("SELECT COALESCE(SUM(amount), 0) FROM TxnEntity WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND at >= :from AND at < :to")
    fun spent(from: Long, to: Long): Flow<Long>

    @Query("SELECT categoryId, SUM(amount) AS total FROM TxnEntity WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND categoryId IS NOT NULL AND at >= :from AND at < :to GROUP BY categoryId")
    fun spentByCategory(from: Long, to: Long): Flow<List<CategorySum>>

    @Query("SELECT COUNT(*) FROM TxnEntity")
    fun count(): Flow<Int>

    /** The category last used for [payee], so a new payment from them suggests it first. */
    @Query("SELECT categoryId FROM TxnEntity WHERE payee = :payee COLLATE NOCASE AND type = :type AND categoryId IS NOT NULL ORDER BY at DESC LIMIT 1")
    suspend fun lastCategoryFor(payee: String, type: String): Long?

    /** Categories used most often for [type], for the other suggestions. */
    @Query("SELECT categoryId FROM TxnEntity WHERE type = :type AND categoryId IS NOT NULL GROUP BY categoryId ORDER BY COUNT(*) DESC LIMIT :limit")
    suspend fun topCategories(type: String, limit: Int): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM TxnEntity WHERE isSample = 1)")
    fun hasSamples(): Flow<Boolean>

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

    @Query("SELECT * FROM TxnEntity WHERE goalId = :goalId ORDER BY at DESC")
    fun forGoal(goalId: Long): Flow<List<TxnEntity>>

    @Query("SELECT goalId, SUM(amount) AS total FROM TxnEntity WHERE goalId IS NOT NULL GROUP BY goalId")
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

    @Query("DELETE FROM CaptureRuleEntity WHERE id = :id")
    suspend fun delete(id: String)
}
