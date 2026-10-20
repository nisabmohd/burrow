package app.burrow.money.data

import androidx.compose.runtime.Immutable
import androidx.room.withTransaction
import app.burrow.money.R
import app.burrow.money.Strings
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.DueBill
import app.burrow.money.domain.Repeat
import app.burrow.money.domain.loanSchedule
import app.burrow.money.domain.matchBill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object BillKind {
    const val BILL = "BILL"
    const val SUBSCRIPTION = "SUBSCRIPTION"
    const val EMI = "EMI"
    const val CARD = "CARD"
}

object OnDue {
    const val ASK = "ASK"
    const val RECORD = "RECORD"
    const val REMIND = "REMIND"
}

object OccurrenceStatus {
    const val UPCOMING = "UPCOMING"
    const val PAID = "PAID"
    const val SKIPPED = "SKIPPED"
}

/** A budget with what was spent against it this period. */
@Immutable
data class BudgetLine(val budget: BudgetEntity, val amount: Long, val spent: Long, val category: CategoryEntity?) {
    val left get() = amount - spent
    val over get() = spent > amount
    val fraction get() = if (amount <= 0) 0f else spent.toFloat() / amount
}

@Immutable
data class GoalLine(val goal: GoalEntity, val saved: Long) {
    val reached get() = saved >= goal.target
    val fraction get() = if (goal.target <= 0) 0f else (saved.toFloat() / goal.target).coerceAtMost(1f)
}

/** A payee that looks like a subscription: the same amount in each of the last 3 months, no bill for it yet. */
@Immutable
data class SubscriptionHint(val payee: String, val amount: Long, val categoryId: Long?, val accountId: Long)

/**
 * Plan (docs/DESIGN.md § Plan): bills and their due dates, budgets and goals. Due dates are made
 * from each bill's repeat rule, one row per date, and never skipped on their own: a past one stays
 * "Not paid yet" until it is marked (CLAUDE.md § Behaviour).
 */
class PlanRepository(
    private val db: BurrowDatabase,
    private val money: MoneyRepository,
    private val strings: Strings,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private fun LocalDate.millis(): Long = atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun Long.date(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    /** Every flow first makes sure the money data is seeded and due dates exist up to 2 months ahead. */
    private fun <T> ready(source: () -> Flow<T>): Flow<T> = flow {
        money.ensureSeeded()
        ensureOccurrences()
        emitAll(source())
    }

    // ---- bills ----

    val bills: Flow<List<BillEntity>> = ready { db.bills().all() }

    fun bill(id: Long): Flow<BillEntity?> = ready { db.bills().get(id) }

    /** Due dates from [from] to [to] (exclusive), with their bills. */
    fun occurrences(from: LocalDate, to: LocalDate): Flow<List<OccurrenceView>> =
        ready { db.bills().occurrences(from.minusDays(1).millis(), to.millis()) }.map { list -> list.filter { !it.occurrence.dueDate.date().isBefore(from) } }

    fun occurrencesOf(billId: Long): Flow<List<OccurrenceView>> = ready { db.bills().occurrencesOf(billId) }

    /** Makes the due dates each bill needs, from after its last one to 2 months ahead. */
    suspend fun ensureOccurrences(today: LocalDate = LocalDate.now()) {
        val until = today.plusDays(62)
        for (b in db.bills().list()) {
            val repeat = Repeat.parse(b.repeatRule) ?: continue
            val last = db.bills().lastDue(b.id)?.date()
            val from = last?.plusDays(1) ?: b.startDate.date()
            val dates = repeat.between(from, until).filter { b.endDate == null || !it.isAfter(b.endDate.date()) }
            if (dates.isNotEmpty()) db.bills().insertOccurrences(dates.map { BillOccurrenceEntity(billId = b.id, dueDate = it.millis()) })
        }
    }

    /** Saves a bill and remakes its unpaid future due dates. Returns its id. */
    suspend fun saveBill(bill: BillEntity): Long = db.withTransaction {
        val id = db.bills().upsert(bill).let { if (it == -1L) bill.id else it }
        db.bills().deleteUpcoming(id, LocalDate.now().millis() - 12 * 3_600_000L)
        ensureOccurrences()
        id
    }

    suspend fun deleteBill(id: Long) = db.bills().archive(id)

    /**
     * Marks a due date paid and records the money: an expense for a bill or subscription; for an EMI
     * the principal is a transfer into the loan and the interest an expense in Fees & interest; a
     * card bill is a transfer into the card. Returns how to undo it.
     */
    suspend fun markPaid(occurrenceId: Long, at: Long = System.currentTimeMillis()): (suspend () -> Unit)? = db.withTransaction {
        val o = db.bills().occurrence(occurrenceId) ?: return@withTransaction null
        if (o.status == OccurrenceStatus.PAID) return@withTransaction null
        val b = db.bills().find(o.billId) ?: return@withTransaction null
        val account = db.accounts().all().first().firstOrNull { it.id == b.accountId } ?: return@withTransaction null
        val amount = o.amount ?: b.amount
        fun txn(type: String, value: Long, category: Long? = null, to: Long? = null) = TxnEntity(
            type = type, amount = value, currency = account.currency, baseAmount = value, accountId = b.accountId,
            toAccountId = to, categoryId = category, payee = b.name, at = at, source = "BILL", billOccurrenceId = o.id,
            // Paying a sample bill keeps the sample month (it isn't the user's first real entry).
            isSample = b.isSample, createdAt = at, updatedAt = at,
        )
        val ids = when (b.kind) {
            BillKind.EMI -> {
                val loan = b.toAccountId?.let { id -> money.accounts.first().firstOrNull { it.account.id == id } }
                val rate = loan?.account?.interestRate?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val interest = if (loan != null) loanSchedule(-loan.balance, rate, amount, o.dueDate.date(), 1, max = 1, unit = wholeUnit(account.currency)).firstOrNull()?.interest ?: 0L else 0L
                listOfNotNull(
                    money.save(txn(TxnType.TRANSFER, amount - interest, to = b.toAccountId)),
                    if (interest > 0) money.save(txn(TxnType.EXPENSE, interest, category = feesCategory())) else null,
                )
            }
            BillKind.CARD -> listOf(money.save(txn(TxnType.TRANSFER, amount, to = b.toAccountId)))
            else -> listOf(money.save(txn(TxnType.EXPENSE, amount, category = b.categoryId)))
        }
        db.bills().updateOccurrence(o.copy(status = OccurrenceStatus.PAID, txnId = ids.first(), amount = amount))
        val undo: suspend () -> Unit = {
            db.withTransaction {
                db.txns().deleteIds(ids)
                db.bills().updateOccurrence(o)
            }
        }
        undo
    }

    /** A captured payment that matches a due bill marks it Paid, linked to that transaction. */
    suspend fun settleFromPayment(txn: TxnEntity): Boolean {
        if (txn.type != TxnType.EXPENSE) return false
        val date = txn.at.date()
        val due = db.bills().occurrences(date.minusDays(4).millis(), date.plusDays(4).millis()).first()
            .filter { it.occurrence.status == OccurrenceStatus.UPCOMING }
        val hit = matchBill(txn.payee, txn.amount, date, due.map { DueBill(it.occurrence.id, it.name, it.amount, it.occurrence.dueDate.date()) }) ?: return false
        val o = db.bills().occurrence(hit.occurrenceId) ?: return false
        db.bills().updateOccurrence(o.copy(status = OccurrenceStatus.PAID, txnId = txn.id, amount = txn.amount))
        return true
    }

    suspend fun skip(occurrenceId: Long): (suspend () -> Unit)? {
        val o = db.bills().occurrence(occurrenceId) ?: return null
        db.bills().updateOccurrence(o.copy(status = OccurrenceStatus.SKIPPED))
        return { db.bills().updateOccurrence(o) }
    }

    private fun wholeUnit(currency: String): Long {
        var u = 1L
        repeat(minorDigits(currency)) { u *= 10 }
        return u
    }

    /** "Fees & interest", made if this install's categories predate it. */
    private suspend fun feesCategory(): Long {
        val name = strings(R.string.cat_fees)
        val cats = money.categories.first()
        cats.firstOrNull { it.kind == CategoryKind.EXPENSE && (it.name == name || it.icon == "percent") }?.let { return it.id }
        return db.categories().insertAll(listOf(CategoryEntity(name = name, kind = CategoryKind.EXPENSE, icon = "percent", colorHue = 245f, sortOrder = 11))).first()
    }

    /** Payees that look like subscriptions and aren't bills yet (design 29's note). */
    suspend fun subscriptionHint(today: LocalDate = LocalDate.now()): SubscriptionHint? {
        val since = today.withDayOfMonth(1).minusMonths(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val names = db.bills().list().map { it.name.lowercase() }
        return db.txns().expensesSince(since).groupBy { it.payee!!.lowercase() }
            .filter { (payee, list) -> payee !in names && list.map { it.amount }.distinct().size == 1 && list.map { it.at.date().monthValue }.distinct().size >= 3 }
            .values.firstOrNull()?.first()?.let { SubscriptionHint(it.payee!!, it.amount, it.categoryId, it.accountId) }
    }

    // ---- budgets ----

    /**
     * The overall budget and the category budgets for [period], with what was spent. A budget
     * with carry-over adds what was left last period (never less than nothing).
     */
    fun budgets(period: BudgetPeriod): Flow<Pair<BudgetLine?, List<BudgetLine>>> = ready {
        val prev = BudgetPeriod(period.start.minusMonths(1), period.start)
        combine(
            db.budgets().overall(period.month.toString()),
            db.budgets().categories(period.month.toString()),
            money.spent(period),
            money.spentByCategory(period),
            combine(money.categories, money.spent(prev), money.spentByCategory(prev)) { a, b, c -> Triple(a, b, c) },
        ) { overall, cats, spent, byCat, (categories, prevSpent, prevByCat) ->
            fun carry(b: BudgetEntity, prevSpentOn: Long) = if (b.carryOver) (b.amount - prevSpentOn).coerceAtLeast(0) else 0
            val o = overall?.let { BudgetLine(it, it.amount + carry(it, prevSpent), spent, null) }
            val lines = cats.mapNotNull { b ->
                val c = categories.firstOrNull { it.id == b.categoryId } ?: return@mapNotNull null
                BudgetLine(b, b.amount + carry(b, prevByCat[c.id] ?: 0), byCat[c.id] ?: 0, c)
            }.sortedByDescending { it.fraction }
            o to lines
        }
    }

    /** Sets a budget from [period]'s month on (it repeats each month until changed). */
    suspend fun setBudget(period: BudgetPeriod, categoryId: Long?, amount: Long, carryOver: Boolean): (suspend () -> Unit) {
        val month = period.month.toString()
        val before = db.budgets().exact(month, categoryId)
        db.budgets().upsert((before ?: BudgetEntity(month = month, categoryId = categoryId, amount = 0)).copy(amount = amount, carryOver = carryOver, isSample = false))
        return {
            if (before != null) db.budgets().upsert(before) else db.budgets().exact(month, categoryId)?.let { db.budgets().upsert(it.copy(amount = 0)) }
        }
    }

    // ---- goals ----

    val goals: Flow<List<GoalLine>> = ready {
        combine(db.goals().all(), db.txns().goalSums()) { goals, sums -> goals.map { g -> GoalLine(g, sums.firstOrNull { it.goalId == g.id }?.total ?: 0) } }
    }

    fun goal(id: Long): Flow<GoalLine?> = goals.map { l -> l.firstOrNull { it.goal.id == id } }

    fun added(goalId: Long): Flow<List<TxnEntity>> = ready { db.txns().forGoal(goalId) }

    suspend fun saveGoal(goal: GoalEntity): Long = db.goals().upsert(goal).let { if (it == -1L) goal.id else it }

    suspend fun deleteGoal(id: Long) = db.goals().archive(id)

    /**
     * "Add money": a transfer from [fromAccountId] into the goal, not spending. Marks the goal
     * reached when it is saved in full. Returns how to undo it.
     */
    suspend fun addMoney(goalId: Long, fromAccountId: Long, amount: Long): suspend () -> Unit {
        val goal = db.goals().find(goalId) ?: return {}
        val account = money.accounts.first().first { it.account.id == fromAccountId }.account
        val now = System.currentTimeMillis()
        val id = money.save(
            TxnEntity(
                type = TxnType.TRANSFER, amount = amount, currency = account.currency, baseAmount = amount, accountId = fromAccountId,
                goalId = goalId, at = now, isSample = goal.isSample, createdAt = now, updatedAt = now,
            ),
        )
        val saved = goals.first().firstOrNull { it.goal.id == goalId }?.saved ?: 0
        if (goal.reachedAt == null && saved >= goal.target) db.goals().upsert(goal.copy(reachedAt = now))
        return {
            db.txns().deleteIds(listOf(id))
            db.goals().upsert(goal)
        }
    }
}
