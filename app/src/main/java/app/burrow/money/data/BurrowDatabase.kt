package app.burrow.money.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * The schema from docs/DATA.md, v1. Money columns are Long minor units. Times are epoch millis.
 * Add an entity here → bump version → write the Migration (never fallbackToDestructiveMigration
 * in release: people's money lives here). schemas/ is committed (ksp room.schemaLocation).
 */

@Entity
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,            // BANK, CASH, WALLET, CARD, LOAN, SAVINGS
    val currency: String,
    val openingBalance: Long,
    val colorHue: Float,
    val icon: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val isSample: Boolean = false,
    val createdAt: Long,
)

@Entity
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val kind: String,            // EXPENSE, INCOME
    val icon: String,
    val colorHue: Float,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val isSample: Boolean = false,
)

@Entity(indices = [Index("at"), Index("accountId"), Index("categoryId")])
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,            // EXPENSE, INCOME, TRANSFER, REFUND, ADJUSTMENT
    val amount: Long,            // minor units, always positive; type gives the sign
    val currency: String,
    val fxRate: String = "1",    // BigDecimal as text; history never changes
    val baseAmount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val payee: String? = null,
    val note: String? = null,
    val at: Long,
    val source: String = "MANUAL", // MANUAL, SMS, NOTIFICATION, BILL, IMPORT
    val linkedId: Long? = null,
    val billOccurrenceId: Long? = null,
    val excludeFromStats: Boolean = false,
    val isSample: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,            // BILL, SUBSCRIPTION, EMI, SAVINGS, INCOME
    val name: String,
    val amount: Long,
    val amountVaries: Boolean = false,
    val accountId: Long,
    val categoryId: Long?,
    val repeatRule: String,      // "MONTHLY:17", "YEARLY:01-09", "WEEKLY:MON"
    val startDate: Long,
    val endDate: Long? = null,
    val onDue: String = "ASK",   // ASK, RECORD, REMIND
    val remindDaysBefore: Int = 1,
    val remindAtMinutes: Int = 9 * 60,
    val archived: Boolean = false,
)

@Entity(indices = [Index("billId"), Index("dueDate")])
data class BillOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val dueDate: Long,
    val status: String = "UPCOMING", // UPCOMING, PAID, SKIPPED
    val txnId: Long? = null,
    val amount: Long? = null,
)

@Entity
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,           // yyyy-MM of the period start
    val categoryId: Long?,       // null = overall
    val amount: Long,
    val carryOver: Boolean = false,
)

@Entity
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Long,
    val targetDate: Long? = null,
    val accountId: Long,
    val icon: String,
    val colorHue: Float,
    val archived: Boolean = false,
)

/** A message rule (capture/Rule.kt), stored as JSON so the format can grow without migrations. */
@Entity
data class CaptureRuleEntity(
    @PrimaryKey val id: String,
    val json: String,
    val enabled: Boolean = true,
    val userMade: Boolean = true,
    val uses: Int = 0,
    val createdAt: Long,
)

@Entity(indices = [Index("receivedAt")])
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,          // SMS, NOTIFICATION
    val sender: String,
    val body: String,            // deleted after 30 days; only the txn stays
    val receivedAt: Long,
    val status: String,          // REVIEW, SAVED, IGNORED, MERGED, UNREAD
    val txnId: Long? = null,
)

@Dao
interface TxnDao {
    @Query("SELECT * FROM TxnEntity WHERE at BETWEEN :from AND :to ORDER BY at DESC")
    fun between(from: Long, to: Long): Flow<List<TxnEntity>>

    @Upsert
    suspend fun upsert(txn: TxnEntity): Long
}

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TxnEntity::class, BillEntity::class,
        BillOccurrenceEntity::class, BudgetEntity::class, GoalEntity::class,
        CaptureRuleEntity::class, CaptureEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BurrowDatabase : RoomDatabase() {
    abstract fun txns(): TxnDao

    companion object {
        fun create(context: Context): BurrowDatabase =
            Room.databaseBuilder(context, BurrowDatabase::class.java, "burrow.db").build()
    }
}
