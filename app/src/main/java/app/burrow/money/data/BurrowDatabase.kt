package app.burrow.money.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

/*
 * The schema from docs/DATA.md, v1. Money columns are Long minor units. Times are epoch millis.
 * Add an entity here → bump version → write the Migration (never fallbackToDestructiveMigration
 * in release: people's money lives here). schemas/ is committed (ksp room.schemaLocation).
 * Every schema change is a new version with a migration (auto where Room can), even before release.
 */

@kotlinx.serialization.Serializable
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
    /** Last digits of the account or card number ("1234"), to match "A/c XX1234" in bank messages. */
    val number: String? = null,
    // v3: details by type (null when they don't apply).
    /** CARD: credit limit. */
    val creditLimit: Long? = null,
    /** CARD: statement day and payment due day of the month. */
    val statementDay: Int? = null,
    val dueDay: Int? = null,
    /** LOAN / SAVINGS: yearly interest rate in percent, BigDecimal as text ("8.5"). */
    val interestRate: String? = null,
    /** LOAN: the monthly EMI. SAVINGS (deposit): the amount put in. */
    val emi: Long? = null,
    val principal: Long? = null,
    /** LOAN: first EMI date. SAVINGS: start date. */
    val startDate: Long? = null,
    /** SAVINGS (deposit): maturity date and the account it pays out to. */
    val maturityDate: Long? = null,
    val payoutAccountId: Long? = null,
    @ColumnInfo(defaultValue = "1") val remind: Boolean = true,
    /** v4: counted in net worth. Cards and loans start off (their dues are already in spending). */
    @ColumnInfo(defaultValue = "1") val includeInNetWorth: Boolean = true,
)

@kotlinx.serialization.Serializable
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

@kotlinx.serialization.Serializable
@Entity(indices = [Index("at"), Index("accountId"), Index("categoryId"), Index("refundOf")])
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
    /** v3: "Add money" to a goal: a transfer out of [accountId] into that goal. */
    val goalId: Long? = null,
    /** v4: POSTED, or UPCOMING (dated in the future: posts on its day). */
    @ColumnInfo(defaultValue = "'POSTED'") val status: String = TxnStatus.POSTED,
    /** v4: lend or borrow. [lend] is LENT, BORROWED or SETTLE (a settlement with [personId]). */
    val personId: Long? = null,
    val lend: String? = null,
    /** v5: a REFUND row points at the expense it pays back (lowers that category, never income). */
    val refundOf: Long? = null,
)

@kotlinx.serialization.Serializable
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
    // v3
    /** For bills without a category (EMI, card bill): their circle. */
    val icon: String? = null,
    val colorHue: Float? = null,
    /** EMI or card bill: the account the payment goes into (paying it is a transfer, not spending). */
    val toAccountId: Long? = null,
    @ColumnInfo(defaultValue = "1") val remindersOn: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isSample: Boolean = false,
)

@kotlinx.serialization.Serializable
@Entity(indices = [Index("billId"), Index("dueDate")])
data class BillOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val dueDate: Long,
    val status: String = "UPCOMING", // UPCOMING, PAID, SKIPPED
    val txnId: Long? = null,
    val amount: Long? = null,
)

@kotlinx.serialization.Serializable
@Entity
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,           // yyyy-MM of the period start
    val categoryId: Long?,       // null = overall
    val amount: Long,
    val carryOver: Boolean = false,
    val isSample: Boolean = false,
)

@kotlinx.serialization.Serializable
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
    // v3
    @ColumnInfo(defaultValue = "0") val isSample: Boolean = false,
    /** When it was saved in full (for the Goal reached note). */
    val reachedAt: Long? = null,
)

/** A message rule (capture/Rule.kt), stored as JSON so the format can grow without migrations. */
@kotlinx.serialization.Serializable
@Entity
data class CaptureRuleEntity(
    @PrimaryKey val id: String,
    val json: String,
    val enabled: Boolean = true,
    val userMade: Boolean = true,
    val uses: Int = 0,
    val createdAt: Long,
)

@kotlinx.serialization.Serializable
@Entity(indices = [Index("receivedAt")])
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,          // SMS, NOTIFICATION
    val sender: String,
    val body: String,            // deleted after 30 days; only the txn stays
    val receivedAt: Long,
    val status: String,          // REVIEW, SAVED, IGNORED, MERGED, UNREAD
    val txnId: Long? = null,
    // What the rule read (null for UNREAD: messages not understood yet).
    val amount: Long? = null,
    val currency: String? = null,
    val payee: String? = null,
    val account: String? = null,
    val direction: String? = null, // OUT, IN
    val ruleId: String? = null,
    /** UPI / transaction reference, for spotting the same payment from SMS and app. */
    val reference: String? = null,
    /** The category the capture notification or To review will save with. */
    val categoryId: Long? = null,
)

/** v4: someone money was lent to or borrowed from. */
@kotlinx.serialization.Serializable
@Entity
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHue: Float,
    /** When to remind about settling up (null = no reminder). */
    val remindAt: Long? = null,
)

/** v4: a receipt or bill kept with a transaction, inside app storage only. */
@kotlinx.serialization.Serializable
@Entity(indices = [Index("txnId")])
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: Long,
    val path: String,             // files/attachments/<txnId>/<name>
    val name: String,
    val mime: String,
    val bytes: Long,
)

/** v4: #Work, #Trip… */
@kotlinx.serialization.Serializable
@Entity(indices = [Index(value = ["name"], unique = true)])
data class TagEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@kotlinx.serialization.Serializable
@Entity(primaryKeys = ["txnId", "tagId"], indices = [Index("tagId")])
data class TxnTagEntity(val txnId: Long, val tagId: Long)

/** v4: a typed-in exchange rate: 1 [currency] = [perPrimary] of the main currency (BigDecimal as text). */
@kotlinx.serialization.Serializable
@Entity
data class RateEntity(@PrimaryKey val currency: String, val perPrimary: String, val setAt: Long)

/**
 * v5: one part of a split payment (08). The payment stays one row in Activity; its parts count
 * toward their own categories and budgets. Parts always add up to the payment's amount.
 */
@kotlinx.serialization.Serializable
@Entity(indices = [Index("txnId")])
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: Long,
    val categoryId: Long,
    val amount: Long,
)

/** v5: a one-tap expense on New expense (04, 05): "Chai ₹20 · Cash". */
@kotlinx.serialization.Serializable
@Entity
data class FavouriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val sortOrder: Int = 0,
)

/**
 * v5: how another app's names map onto Burrow's (Import, 11), remembered for the next import.
 * [kind] is CATEGORY or ACCOUNT; [targetId] the Burrow category or account.
 */
@kotlinx.serialization.Serializable
@Entity(primaryKeys = ["source", "kind", "theirName"])
data class ImportMapEntity(val source: String, val kind: String, val theirName: String, val targetId: Long)

/** v3 → v4: cards and loans start out of net worth. */
class V3To4 : androidx.room.migration.AutoMigrationSpec {
    override fun onPostMigrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("UPDATE AccountEntity SET includeInNetWorth = 0 WHERE type IN ('CARD', 'LOAN')")
    }
}

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TxnEntity::class, BillEntity::class,
        BillOccurrenceEntity::class, BudgetEntity::class, GoalEntity::class,
        CaptureRuleEntity::class, CaptureEntity::class,
        PersonEntity::class, AttachmentEntity::class, TagEntity::class, TxnTagEntity::class, RateEntity::class,
        SplitEntity::class, FavouriteEntity::class, ImportMapEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // account number, parsed capture fields
        AutoMigration(from = 2, to = 3), // account details, goal transfers, bill circles and flags
        AutoMigration(from = 3, to = 4, spec = V3To4::class), // customise: net worth, upcoming, lend, tags, files, rates
        AutoMigration(from = 4, to = 5), // next: refunds, split parts, favourites, import mappings
    ],
)
abstract class BurrowDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun txns(): TxnDao
    abstract fun budgets(): BudgetDao
    abstract fun bills(): BillDao
    abstract fun goals(): GoalDao
    abstract fun captures(): CaptureDao
    abstract fun backup(): BackupDao
    abstract fun rules(): RuleDao
    abstract fun extras(): ExtrasDao
    abstract fun next(): NextDao

    companion object {
        fun create(context: Context): BurrowDatabase =
            Room.databaseBuilder(context, BurrowDatabase::class.java, "burrow.db").build()
    }
}
