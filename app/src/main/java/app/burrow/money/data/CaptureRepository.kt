package app.burrow.money.data

import android.content.Context
import androidx.room.withTransaction
import app.burrow.money.capture.AccountRef
import app.burrow.money.capture.CardRef
import app.burrow.money.capture.matchCard
import app.burrow.money.capture.withCards
import app.burrow.money.capture.BUILT_IN_PACKS
import app.burrow.money.capture.Direction
import app.burrow.money.capture.Rule
import app.burrow.money.capture.RuleBuilder
import app.burrow.money.capture.RuleEngine
import app.burrow.money.capture.RuleFile
import app.burrow.money.capture.RuleFiles
import app.burrow.money.capture.Seen
import app.burrow.money.capture.categoryWords
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

    /** Banks a new card can be from (191): the enabled packs' bank names. */
    fun cardBanks(enabled: Set<String>): List<String> =
        app.burrow.money.capture.cardBanks(enabled.mapNotNull { packs[it] }.flatMap { f -> f.rules.map { it.name } })

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
        // "Not a payment" (236): messages like this are skipped, never stored.
        if (parsed.type == app.burrow.money.capture.MoneyType.NOT_PAYMENT) return@withContext CaptureResult.Ignored
        val reference = referenceIn(body)
        val seen = db.captures().since(at - app.burrow.money.capture.DEDUPE_WINDOW_MS)
            .map { Seen(it.id, it.source, it.amount ?: 0, it.account, it.reference, it.receivedAt) }
        val dup = findDuplicate(source, parsed.amountMinor, parsed.account, reference, at, seen)
        val rule = rules.firstOrNull { it.id == parsed.ruleId }
        if (rule?.userMade == true) db.rules().used(rule.id)
        val type = categoryTypeOf(parsed.type)
        val capture = CaptureEntity(
            source = source, sender = sender, body = body, receivedAt = at,
            status = if (dup != null) CaptureStatus.MERGED else CaptureStatus.REVIEW,
            amount = parsed.amountMinor, currency = parsed.currency ?: main, payee = parsed.payee, account = parsed.account,
            direction = parsed.type.stored, ruleId = parsed.ruleId, reference = reference,
            categoryId = if (type == null) null else suggestions(parsed.payee, type, body).firstOrNull(),
            name = nameFor(parsed.payee, body),
        )
        val id = db.captures().insert(capture)
        if (dup != null) CaptureResult.Duplicate else CaptureResult.Found(capture.copy(id = id))
    }

    /**
     * Two or three categories to offer: the ones usually picked for this merchant first (most often,
     * then latest: what you chose wins), then the one the words in the message point at ("petrol",
     * "medicines", "Swiggy"), then the most used overall, then the defaults.
     */
    suspend fun suggestions(payee: String?, type: String, body: String = "", count: Int = 3): List<Long> {
        val usual = payee?.let { db.txns().usualCategoriesFor(tidyPayee(it), it.trim(), type, count) }.orEmpty()
        val ofKind = money.categories.first().filter { !it.archived && it.kind == if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }
        val words = categoryWords(payee, body)?.let { w -> ofKind.filter { it.icon == w.icon }.minByOrNull { it.sortOrder }?.id }
        val top = db.txns().topCategories(type, count + 1)
        return (usual + listOfNotNull(words) + top + ofKind.map { it.id }).distinct().take(count)
    }

    /**
     * The expense's own name: what you called this merchant last time (its alias), else the message's
     * plain word ("Petrol"), else a readable UPI id ("third.wave@okicici" → "Third Wave"). Null keeps the merchant.
     */
    suspend fun nameFor(merchant: String?, body: String): String? {
        val alias = merchant?.let { db.txns().aliasFor(tidyPayee(it), it.trim()) }
        return (alias ?: categoryWords(merchant, body)?.name ?: merchant?.let(::upiName))
            ?.takeIf { merchant == null || !it.equals(tidyPayee(merchant), ignoreCase = true) }
    }

    /** The account a capture is about, from its digits or its bank's name. */
    suspend fun accountFor(capture: CaptureEntity): Long? {
        // Your own accounts only, once you have one: a sample's ••1234 matched real messages (QA 12 Nov).
        val all = money.accounts.first()
        val mine = all.filter { !it.account.isSample }.ifEmpty { all }
        val accounts = mine.map { AccountRef(it.account.id, it.account.name, it.account.number, it.account.type) }
        val bank = activeRules.first().firstOrNull { it.id == capture.ruleId }?.name
        return matchAccount(capture.account, bank, withCards(accounts, cardRefs()))
    }

    private suspend fun cardRefs() = db.cards().allNow().map { CardRef(it.id, it.accountId, it.number) }

    /**
     * Runs when a payment leaves To review (saved, ignored, opened in New expense), so its
     * notification goes too: a payment already dealt with never waits in the shade.
     */
    var onResolved: (Long) -> Unit = {}

    /** Saves a capture as a transaction with [categoryId]; returns the new transaction's id. */
    suspend fun save(captureId: Long, categoryId: Long?): Long? {
        val c = db.captures().get(captureId) ?: return null
        if (c.status != CaptureStatus.REVIEW || c.amount == null) return c.txnId
        val accountId = accountFor(c) ?: return null
        val now = System.currentTimeMillis()
        val kind = app.burrow.money.capture.MoneyType.ofStored(c.direction)
        // A refund links to the expense it pays back (same payee, the 60 days before) and takes its
        // category, so it lowers that spending; never income. A transfer has no category.
        val original = if (kind == app.burrow.money.capture.MoneyType.REFUND) c.payee?.let { p ->
            db.txns().refundMatch(tidyPayee(p), p.trim(), c.receivedAt - REFUND_WINDOW_MS, c.receivedAt, c.amount)
        } else null
        val type = txnTypeOf(kind)
        val category = when {
            kind == app.burrow.money.capture.MoneyType.TRANSFER -> null
            original != null -> original.categoryId
            else -> categoryId ?: c.categoryId
        }
        val txnId = money.save(
            TxnEntity(
                type = type, amount = c.amount, currency = c.currency ?: "INR", baseAmount = c.amount,
                accountId = accountId, categoryId = category, refundOf = original?.id,
                // The expense is named by the message's words when it had some; the merchant stays hidden.
                payee = c.name ?: c.payee?.let(::tidyPayee), merchant = c.payee?.let(::tidyPayee),
                at = c.receivedAt, source = c.source, createdAt = now, updatedAt = now,
                cardId = matchCard(c.account, accountId, cardRefs()),
            ),
        )
        db.captures().update(c.copy(status = CaptureStatus.SAVED, txnId = txnId, categoryId = category))
        onResolved(captureId)
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
        if (status != CaptureStatus.REVIEW) onResolved(captureId)
    }

    /** The user picked the category (Review sheet, What was it for?): the payment is ready to save. */
    suspend fun setCategory(captureId: Long, categoryId: Long) {
        db.captures().get(captureId)?.let { db.captures().update(it.copy(categoryId = categoryId, categoryPicked = true)) }
    }

    /**
     * The category Burrow *knows* for a payment (To review's Ready to save, 44): the one usually picked for this
     * merchant, else the one the message's words point at ("petrol", "Swiggy"). Null = a new shop: the guess is only
     * "your most used", so To review asks (Pick a category).
     */
    suspend fun knownCategory(payee: String?, type: String, body: String = ""): Long? {
        payee?.let { db.txns().usualCategoriesFor(tidyPayee(it), it.trim(), type, 1).firstOrNull() }?.let { return it }
        val ofKind = money.categories.first().filter { !it.archived && it.kind == if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }
        return categoryWords(payee, body)?.let { w -> ofKind.filter { it.icon == w.icon }.minByOrNull { it.sortOrder }?.id }
    }

    /**
     * The bank a message came from, by name ("AX-KOTAKB" → "Kotak Bank"): the first rule whose sender matches, the
     * catch-all "any bank" rules left out. Null when no rule knows the sender (show the sender itself).
     */
    suspend fun bankName(sender: String): String? = activeRules.first().firstOrNull { r ->
        ".any." !in r.id && runCatching { Regex(r.sender).containsMatchIn(sender) }.getOrDefault(false)
    }?.name

    suspend fun get(id: Long): CaptureEntity? = db.captures().get(id)

    /** "Open it to edit first": the capture was saved from New expense as [txnId]. */
    suspend fun markSaved(captureId: Long, txnId: Long, categoryId: Long?) {
        db.captures().get(captureId)?.let { db.captures().update(it.copy(status = CaptureStatus.SAVED, txnId = txnId, categoryId = categoryId)) }
        onResolved(captureId)
    }

    /** Drops message text older than 30 days (docs/CAPTURE.md § Keep). Run on app start. */
    suspend fun forgetOldText(now: Long = System.currentTimeMillis()) = db.captures().forgetTextBefore(now - 30L * 24 * 60 * 60 * 1000)

    // --- your rules ---

    suspend fun saveRule(rule: Rule) = db.rules().upsert(
        CaptureRuleEntity(id = rule.id, json = RuleFiles.encodeRule(rule.copy(userMade = true)), enabled = rule.enabled, userMade = true, createdAt = System.currentTimeMillis()),
    )

    /**
     * A new rule's name from its sender: the bank a built-in pack knows it as ("JM-HDFCBK-S" →
     * "HDFC Bank"), else the app's name for a notification, else the sender's own name ("JIOPAY").
     * A second rule from the same bank gets "HDFC Bank 2". Null without a sender.
     */
    suspend fun ruleNameFor(sender: String): String? = withContext(Dispatchers.IO) {
        val s = sender.trim().ifBlank { return@withContext null }
        val base = bankRules().firstOrNull { r -> runCatching { Regex(r.sender).containsMatchIn(s) }.getOrDefault(false) }?.name
            ?: if (RuleBuilder.isPackage(s)) runCatching {
                context.packageManager.run { getApplicationLabel(getApplicationInfo(s, 0)).toString() }
            }.getOrElse { s.substringAfterLast('.').replaceFirstChar { it.uppercase() } }
            else RuleBuilder.senderCore(s)
        uniqueName(base)
    }

    /** Built-in rules for one bank or app (not the "Any bank" ones), in pack order. */
    private fun bankRules(): Sequence<Rule> = packs.values.asSequence().flatMap { it.rules }
        .filter { !it.id.contains(".any.") && !it.name.startsWith("Any ", ignoreCase = true) && it.name != "Bank" }

    /** [base], or "HDFC Bank 2" when one of your rules already has that name. */
    private suspend fun uniqueName(base: String, except: String? = null): String {
        val taken = userRules.first().filter { it.id != except }.map { it.name.lowercase() }.toSet()
        return (sequenceOf(base) + generateSequence(2) { it + 1 }.map { "$base $it" })
            .first { it.lowercase() !in taken }.take(RULE_NAME_MAX)
    }

    /**
     * Rules taught from a sender like "JM-HDFCBK-S" before 0.4.0 matched any sender ending in "S" and
     * were named "S". Each gets its bank back: from the first message it read, else from its example
     * message (a built-in rule that reads it, or the bank's name in the text). Run on app start and after a restore.
     */
    suspend fun repairShortSenders() = withContext(Dispatchers.IO) {
        val short = Regex("""\(\?i\)[A-Za-z0-9]\$""")
        for (e in db.backup().rules()) {
            if (!e.userMade) continue
            val rule = runCatching { RuleFiles.decodeRule(e.json) }.getOrNull() ?: continue
            if (!short.matches(rule.sender)) continue
            val fixed: Pair<String, String>? = db.captures().firstSenderFor(rule.id)
                ?.takeIf { RuleBuilder.senderCore(it).length >= 2 }
                ?.let { sender -> RuleBuilder.senderPattern(sender) to (bankRules().firstOrNull { r -> runCatching { Regex(r.sender).containsMatchIn(sender) }.getOrDefault(false) }?.name ?: RuleBuilder.senderCore(sender)) }
                ?: rule.example?.let { text ->
                    val bank = bankRules().firstOrNull { r -> runCatching { Regex(r.pattern).containsMatchIn(text) }.getOrDefault(false) }
                        ?: bankRules().firstOrNull { r -> text.contains(r.name, ignoreCase = true) }
                    bank?.let { it.sender to it.name }
                }
            fixed ?: continue
            val name = if (rule.name.length <= 1) uniqueName(fixed.second, except = rule.id) else rule.name
            db.rules().setJson(rule.id, RuleFiles.encodeRule(rule.copy(sender = fixed.first, name = name, userMade = true)))
        }
    }

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
            // Taught "Not a payment": the waiting message goes, like one skipped on arrival.
            if (p.type == app.burrow.money.capture.MoneyType.NOT_PAYMENT) { db.captures().update(c.copy(status = CaptureStatus.IGNORED)); continue }
            val type = categoryTypeOf(p.type)
            db.captures().update(
                c.copy(
                    status = CaptureStatus.REVIEW, amount = p.amountMinor, currency = p.currency ?: main, payee = p.payee,
                    account = p.account, direction = p.type.stored, ruleId = p.ruleId, reference = referenceIn(c.body),
                    categoryId = if (type == null) null else suggestions(p.payee, type, c.body).firstOrNull(), name = nameFor(p.payee, c.body),
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

/**
 * A readable name from a UPI id: "third.wave@okicici" → "Third Wave", "thirdwave@okicici" → "Thirdwave".
 * Phone numbers and machine ids ("paytmqr281005050101@paytm", "9876543210@ybl") give null. Pure: unit-tested.
 */
fun upiName(raw: String): String? {
    val id = raw.trim()
    val at = id.indexOf('@')
    if (at <= 0 || id.any { it.isWhitespace() }) return null
    val local = id.substring(0, at)
    if (local.count { it.isDigit() } >= 4) return null
    val words = local.split('.', '_', '-').filter { it.isNotBlank() && it.any(Char::isLetter) }
    if (words.isEmpty()) return null
    return words.joinToString(" ") { w -> w.filter { it.isLetterOrDigit() }.lowercase().replaceFirstChar { it.uppercase() } }
}

/** Longest rule name kept (it shows as a row title and a header). */
const val RULE_NAME_MAX = 40

/** How far back a refund looks for the expense it pays back. */
const val REFUND_WINDOW_MS = 60L * 24 * 3_600_000

/** The transaction a capture of [type] becomes. */
fun txnTypeOf(type: app.burrow.money.capture.MoneyType): String = when (type) {
    app.burrow.money.capture.MoneyType.INCOME -> TxnType.INCOME
    app.burrow.money.capture.MoneyType.REFUND -> TxnType.REFUND
    app.burrow.money.capture.MoneyType.TRANSFER -> TxnType.TRANSFER
    else -> TxnType.EXPENSE
}

/** Which categories a capture of [type] picks from: income ones for income, none for a transfer, else spending. */
fun categoryTypeOf(type: app.burrow.money.capture.MoneyType): String? = when (type) {
    app.burrow.money.capture.MoneyType.INCOME -> TxnType.INCOME
    app.burrow.money.capture.MoneyType.TRANSFER, app.burrow.money.capture.MoneyType.NOT_PAYMENT -> null
    else -> TxnType.EXPENSE
}
