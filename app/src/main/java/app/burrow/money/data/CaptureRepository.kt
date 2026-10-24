package app.burrow.money.data

import android.content.Context
import androidx.room.withTransaction
import app.burrow.money.capture.AccountRef
import app.burrow.money.capture.BUILT_IN_PACKS
import app.burrow.money.capture.Direction
import app.burrow.money.capture.Rule
import app.burrow.money.capture.RuleEngine
import app.burrow.money.capture.RuleFile
import app.burrow.money.capture.RuleFiles
import app.burrow.money.capture.Seen
import app.burrow.money.capture.findDuplicate
import app.burrow.money.capture.looksLikePayment
import app.burrow.money.capture.matchAccount
import app.burrow.money.capture.referenceIn
import app.burrow.money.domain.BudgetPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What happened to an incoming message. */
sealed interface CaptureResult {
    /** Read and waiting for a category: post the capture notification. */
    data class Found(val capture: CaptureEntity) : CaptureResult
    data object Duplicate : CaptureResult
    data object NotUnderstood : CaptureResult
    data object Ignored : CaptureResult
}

/**
 * Auto-capture storage (docs/CAPTURE.md): message rules (yours + built-in packs), captured
 * messages, and turning a capture into a transaction.
 */
class CaptureRepository(
    private val context: Context,
    private val db: BurrowDatabase,
    private val settings: SettingsRepository,
    private val money: MoneyRepository,
    private val engine: RuleEngine,
) {
    private val packs: Map<String, RuleFile> by lazy {
        BUILT_IN_PACKS.associate { p -> p.id to context.assets.open("rules/${p.id}.json").bufferedReader().use { RuleFiles.decode(it.readText()) } }
    }

    fun pack(id: String): RuleFile? = packs[id]

    /** Your rules, newest first. */
    val userRules: Flow<List<Rule>> = db.rules().all().map { list ->
        list.mapNotNull { e -> runCatching { RuleFiles.decodeRule(e.json).copy(enabled = e.enabled, userMade = true) }.getOrNull() }
    }

    fun rule(id: String): Flow<Rule?> = combine(userRules, settings.settings) { mine, _ ->
        mine.firstOrNull { it.id == id } ?: packs.values.firstNotNullOfOrNull { f -> f.rules.firstOrNull { it.id == id } }
    }

    /** How many payments each built-in rule has read, from the messages it read. */
    val packRuleUses: Flow<Map<String, Int>> = db.captures().ruleIds().map { ids -> ids.groupingBy { it }.eachCount() }

    /** Uses of each of your rules. */
    val ruleUses: Flow<Map<String, Int>> = db.rules().all().map { l -> l.associate { it.id to it.uses } }

    /** The order the engine tries: your rules first (newest first), then the packs that are on. */
    val activeRules: Flow<List<Rule>> = combine(userRules, settings.settings) { mine, s ->
        mine.filter { it.enabled } + BUILT_IN_PACKS.filter { it.id in s.enabledPacks }.flatMap { packs[it.id]?.rules.orEmpty() }
    }

    val toReview: Flow<List<CaptureEntity>> = db.captures().withStatus(CaptureStatus.REVIEW)
    val reviewCount: Flow<Int> = db.captures().count(CaptureStatus.REVIEW)
    val notUnderstood: Flow<List<CaptureEntity>> = db.captures().withStatus(CaptureStatus.UNREAD)
    val notUnderstoodCount: Flow<Int> = db.captures().count(CaptureStatus.UNREAD)

    fun savedThisMonth(monthStartDay: Int): Flow<Int> =
        db.captures().savedSince(BudgetPeriod.containing(LocalDate.now(), monthStartDay).startMillis())

    /** Bank names that recorded payments from [source] ("HDFC, ICICI, Kotak"). */
    fun banksSeen(source: String): Flow<List<String>> = combine(db.captures().savedRuleIds(source), activeRules) { ids, rules ->
        ids.mapNotNull { id -> rules.firstOrNull { it.id == id }?.name?.substringBefore(' ') }.distinct()
    }

    /**
     * One message from the SMS receiver or the notification listener. Noise is dropped and never
     * stored; a payment is stored for review (or merged into the same payment seen a moment ago);
     * a money message no rule reads waits for Teach.
     */
    suspend fun handle(source: String, sender: String, body: String, at: Long, extra: List<Rule> = emptyList()): CaptureResult = withContext(Dispatchers.IO) {
        if (engine.isNoise(body)) return@withContext CaptureResult.Ignored
        val rules = activeRules.first() + extra
        val main = settings.settings.first().mainCurrency
        val parsed = engine.parse(sender, body, rules) { c -> minorDigits(c ?: main) }
        if (parsed == null) {
            if (!looksLikePayment(sender, body)) return@withContext CaptureResult.Ignored
            db.captures().insert(CaptureEntity(source = source, sender = sender, body = body, receivedAt = at, status = CaptureStatus.UNREAD))
            return@withContext CaptureResult.NotUnderstood
        }
        val reference = referenceIn(body)
        val seen = db.captures().since(at - app.burrow.money.capture.DEDUPE_WINDOW_MS)
            .map { Seen(it.id, it.source, it.amount ?: 0, it.account, it.reference, it.receivedAt) }
        val dup = findDuplicate(source, parsed.amountMinor, parsed.account, reference, at, seen)
        val rule = rules.firstOrNull { it.id == parsed.ruleId }
        if (rule?.userMade == true) db.rules().used(rule.id)
        val type = if (parsed.direction == Direction.IN) TxnType.INCOME else TxnType.EXPENSE
        val capture = CaptureEntity(
            source = source, sender = sender, body = body, receivedAt = at,
            status = if (dup != null) CaptureStatus.MERGED else CaptureStatus.REVIEW,
            amount = parsed.amountMinor, currency = parsed.currency ?: main, payee = parsed.payee, account = parsed.account,
            direction = parsed.direction.name, ruleId = parsed.ruleId, reference = reference,
            categoryId = suggestions(parsed.payee, type).firstOrNull(),
        )
        val id = db.captures().insert(capture)
        if (dup != null) CaptureResult.Duplicate else CaptureResult.Found(capture.copy(id = id))
    }

    /**
     * Two or three categories to offer: the ones usually picked for this payee first (most often,
     * then latest), then the most used overall, then the defaults.
     */
    suspend fun suggestions(payee: String?, type: String, count: Int = 3): List<Long> {
        val usual = payee?.let { db.txns().usualCategoriesFor(tidyPayee(it), it.trim(), type, count) }.orEmpty()
        val top = db.txns().topCategories(type, count + 1)
        val fallback = money.categories.first().filter { it.kind == if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }.map { it.id }
        return (usual + top + fallback).distinct().take(count)
    }

    /** The account a capture is about, from its digits or its bank's name. */
    suspend fun accountFor(capture: CaptureEntity): Long? {
        val accounts = money.accounts.first().map { AccountRef(it.account.id, it.account.name, it.account.number, it.account.type) }
        val bank = activeRules.first().firstOrNull { it.id == capture.ruleId }?.name
        return matchAccount(capture.account, bank, accounts)
    }

    /** Saves a capture as a transaction with [categoryId]; returns the new transaction's id. */
    suspend fun save(captureId: Long, categoryId: Long?): Long? {
        val c = db.captures().get(captureId) ?: return null
        if (c.status != CaptureStatus.REVIEW || c.amount == null) return c.txnId
        val accountId = accountFor(c) ?: return null
        val now = System.currentTimeMillis()
        val type = if (c.direction == Direction.IN.name) TxnType.INCOME else TxnType.EXPENSE
        val txnId = money.save(
            TxnEntity(
                type = type, amount = c.amount, currency = c.currency ?: "INR", baseAmount = c.amount,
                accountId = accountId, categoryId = categoryId ?: c.categoryId, payee = c.payee?.let(::tidyPayee),
                at = c.receivedAt, source = c.source, createdAt = now, updatedAt = now,
            ),
        )
        db.captures().update(c.copy(status = CaptureStatus.SAVED, txnId = txnId, categoryId = categoryId ?: c.categoryId))
        return txnId
    }

    /** Undo of a save: the transaction goes, the capture waits in To review again. */
    suspend fun unsave(captureId: Long) = db.withTransaction {
        val c = db.captures().get(captureId) ?: return@withTransaction
        c.txnId?.let { money.delete(it) }
        db.captures().update(c.copy(status = CaptureStatus.REVIEW, txnId = null))
    }

    suspend fun setStatus(captureId: Long, status: String) {
        db.captures().get(captureId)?.let { db.captures().update(it.copy(status = status)) }
    }

    suspend fun setCategory(captureId: Long, categoryId: Long) {
        db.captures().get(captureId)?.let { db.captures().update(it.copy(categoryId = categoryId)) }
    }

    suspend fun get(id: Long): CaptureEntity? = db.captures().get(id)

    /** "Open it to edit first": the capture was saved from New expense as [txnId]. */
    suspend fun markSaved(captureId: Long, txnId: Long, categoryId: Long?) {
        db.captures().get(captureId)?.let { db.captures().update(it.copy(status = CaptureStatus.SAVED, txnId = txnId, categoryId = categoryId)) }
    }

    /** Drops message text older than 30 days (docs/CAPTURE.md § Keep). Run on app start. */
    suspend fun forgetOldText(now: Long = System.currentTimeMillis()) = db.captures().forgetTextBefore(now - 30L * 24 * 60 * 60 * 1000)

    // --- your rules ---

    suspend fun saveRule(rule: Rule) = db.rules().upsert(
        CaptureRuleEntity(id = rule.id, json = RuleFiles.encodeRule(rule.copy(userMade = true)), enabled = rule.enabled, userMade = true, createdAt = System.currentTimeMillis()),
    )

    suspend fun setRuleEnabled(id: String, on: Boolean) = db.rules().setEnabled(id, on)

    /** Gives one of your rules a new name; blank names are ignored. */
    suspend fun renameRule(rule: Rule, name: String) {
        val clean = name.trim().take(RULE_NAME_MAX)
        if (clean.isEmpty() || !rule.userMade) return
        db.rules().setJson(rule.id, RuleFiles.encodeRule(rule.copy(name = clean, userMade = true)))
    }

    suspend fun deleteRule(id: String) = db.rules().delete(id)

    /**
     * After a new rule: messages that were waiting are tried again; the ones it now reads move to
     * To review. Returns how many.
     */
    suspend fun retryNotUnderstood(): Int {
        val waiting = db.captures().listWithStatus(CaptureStatus.UNREAD)
        val rules = activeRules.first()
        val main = settings.settings.first().mainCurrency
        var moved = 0
        for (c in waiting) {
            val p = engine.parse(c.sender, c.body, rules) { cur -> minorDigits(cur ?: main) } ?: continue
            if (rules.firstOrNull { it.id == p.ruleId }?.userMade == true) db.rules().used(p.ruleId)
            val type = if (p.direction == Direction.IN) TxnType.INCOME else TxnType.EXPENSE
            db.captures().update(
                c.copy(
                    status = CaptureStatus.REVIEW, amount = p.amountMinor, currency = p.currency ?: main, payee = p.payee,
                    account = p.account, direction = p.direction.name, ruleId = p.ruleId, reference = referenceIn(c.body),
                    categoryId = suggestions(p.payee, type).firstOrNull(),
                ),
            )
            moved++
        }
        return moved
    }
}

/** "THIRD WAVE COFFE" → "Third Wave Coffe"; UPI ids ("thirdwave@okicici") stay as they are. */
fun tidyPayee(raw: String): String =
    if (raw.contains('@') || raw.any { it.isLowerCase() }) raw.trim()
    else raw.trim().lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** Longest rule name kept (it shows as a row title and a header). */
const val RULE_NAME_MAX = 40
