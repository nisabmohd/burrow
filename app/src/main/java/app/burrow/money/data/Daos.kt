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
    val toAccountName: String?,
)

/** SUM(amount) of transfers into one account. */
data class AccountInSum(val accountId: Long, val total: Long)

data class CategorySum(val categoryId: Long, val total: Long)

@Dao
interface AccountDao {
    @Query("SELECT * FROM AccountEntity WHERE archived = 0 ORDER BY sortOrder, id")
    fun all(): Flow<List<AccountEntity>>

    @Query("SELECT accountId, type, SUM(amount) AS total FROM TxnEntity GROUP BY accountId, type")
    fun outSums(): Flow<List<app.burrow.money.domain.TypeSum>>

    @Query("SELECT toAccountId AS accountId, SUM(amount) AS total FROM TxnEntity WHERE type = 'TRANSFER' AND toAccountId IS NOT NULL GROUP BY toAccountId")
    fun inSums(): Flow<List<AccountInSum>>

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
               a.name AS accountName, ta.name AS toAccountName
        FROM TxnEntity t
        JOIN AccountEntity a ON a.id = t.accountId
        LEFT JOIN CategoryEntity c ON c.id = t.categoryId
        LEFT JOIN AccountEntity ta ON ta.id = t.toAccountId
        ORDER BY t.at DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun latest(limit: Int): Flow<List<TxnView>>

    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.icon AS categoryIcon, c.colorHue AS categoryHue,
               a.name AS accountName, ta.name AS toAccountName
        FROM TxnEntity t
        JOIN AccountEntity a ON a.id = t.accountId
        LEFT JOIN CategoryEntity c ON c.id = t.categoryId
        LEFT JOIN AccountEntity ta ON ta.id = t.toAccountId
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

    @Query("SELECT EXISTS(SELECT 1 FROM TxnEntity WHERE isSample = 1)")
    fun hasSamples(): Flow<Boolean>

    @Upsert
    suspend fun upsert(txn: TxnEntity): Long

    @Insert
    suspend fun insertAll(txns: List<TxnEntity>)

    @Delete
    suspend fun delete(txn: TxnEntity)

    @Query("DELETE FROM TxnEntity WHERE isSample = 1")
    suspend fun deleteSamples()
}

@Dao
interface BudgetDao {
    /** The overall budget in force for [month] (yyyy-MM): the latest one set on or before it. */
    @Query("SELECT * FROM BudgetEntity WHERE categoryId IS NULL AND month <= :month ORDER BY month DESC LIMIT 1")
    fun overall(month: String): Flow<BudgetEntity?>

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Query("DELETE FROM BudgetEntity WHERE isSample = 1")
    suspend fun deleteSamples()
}
