package app.burrow.money.capture

/**
 * What a payment message means (Teach's Type row and sheet, 235/236). Banks say it with many words
 * ("debited", "spent", "withdrawn", "credited", "refund"…), so each language has a word list per
 * type, and a rule may add its own ([Rule.typeWords]). Pure Kotlin: unit-tested on the JVM.
 */
enum class MoneyType {
    EXPENSE, INCOME,
    /** Money back for something bought: lowers that category's spending, never income. */
    REFUND,
    /** Between the user's own accounts: never spending or income. */
    TRANSFER,
    /** Not a payment ("requested", "is due"): messages like it are skipped. */
    NOT_PAYMENT;

    val direction get() = if (this == INCOME || this == REFUND) Direction.IN else Direction.OUT

    /** What a capture keeps in its `direction` column: OUT / IN as before, else the type's name. */
    val stored get() = when (this) { EXPENSE -> Direction.OUT.name; INCOME -> Direction.IN.name; else -> name }

    companion object {
        fun ofStored(s: String?): MoneyType = when (s) {
            Direction.IN.name -> INCOME
            REFUND.name -> REFUND
            TRANSFER.name -> TRANSFER
            NOT_PAYMENT.name -> NOT_PAYMENT
            else -> EXPENSE
        }
    }
}

/** A type word found in a message, and where it sits (highlighted in Teach). */
data class TypeWord(val type: MoneyType, val word: String, val range: IntRange)

object TypeWords {
    /**
     * Built-in words for English messages. Order matters: a more specific type is tried first, so
     * "refund of Rs 499 credited" is a refund and "Rs 2,000 sent to your own account" a transfer.
     */
    private val EN: Map<MoneyType, List<String>> = linkedMapOf(
        MoneyType.NOT_PAYMENT to listOf("requested", "is due", "will be debited"),
        MoneyType.REFUND to listOf("refund", "refunded", "reversal", "cashback"),
        MoneyType.TRANSFER to listOf("self transfer", "to your own"),
        MoneyType.INCOME to listOf("credited", "received", "deposited"),
        MoneyType.EXPENSE to listOf("debited", "spent", "paid", "sent", "withdrawn", "purchase"),
    )

    private fun words(np: List<String>, refund: List<String>, transfer: List<String>, income: List<String>, expense: List<String>) =
        linkedMapOf(
            MoneyType.NOT_PAYMENT to np, MoneyType.REFUND to refund, MoneyType.TRANSFER to transfer,
            MoneyType.INCOME to income, MoneyType.EXPENSE to expense,
        )

    /** The app's other languages (user, 2026-11-12). Same order as English. */
    private val BY_LANGUAGE: Map<String, Map<MoneyType, List<String>>> = mapOf(
        "de" to words(
            listOf("angefordert", "fällig", "wird abgebucht"), listOf("Erstattung", "erstattet", "Rückerstattung", "Rückbuchung"),
            listOf("Umbuchung", "Übertrag"), listOf("gutgeschrieben", "Gutschrift", "erhalten", "eingegangen"),
            listOf("belastet", "abgebucht", "bezahlt", "gezahlt", "abgehoben", "Lastschrift", "Kauf"),
        ),
        "es" to words(
            listOf("solicitado", "vence"), listOf("reembolso", "reembolsado", "devolución"),
            listOf("traspaso", "entre tus cuentas"), listOf("abono", "abonado", "recibido", "depósito", "ingreso"),
            listOf("cargo", "cargado", "debitado", "pagado", "compra", "retiro", "retirado"),
        ),
        "fr" to words(
            listOf("demandé", "échéance"), listOf("remboursement", "remboursé"),
            listOf("virement interne", "entre vos comptes"), listOf("crédité", "reçu", "versement"),
            listOf("débité", "débit", "payé", "paiement", "achat", "retrait", "prélevé", "prélèvement"),
        ),
        "pt" to words(
            listOf("solicitado", "vence em"), listOf("estorno", "estornado", "reembolso"),
            listOf("entre contas", "entre suas contas"), listOf("creditado", "crédito", "recebido", "depósito"),
            listOf("debitado", "débito", "pago", "compra", "saque", "pagamento"),
        ),
        "hi" to words(
            listOf("अनुरोध", "देय"), listOf("रिफंड", "वापस"),
            listOf("अपने खाते"), listOf("क्रेडिट", "जमा", "प्राप्त"),
            listOf("डेबिट", "कटे", "भुगतान", "खर्च", "निकाले"),
        ),
        // No spaces between Japanese words: these match anywhere (see [find]).
        "ja" to words(
            listOf("請求", "予定"), listOf("返金", "払い戻し"),
            listOf("振替"), listOf("入金", "受取"),
            listOf("引き落とし", "引落", "出金", "支払い", "ご利用", "購入"),
        ),
    )

    /**
     * The built-in words for [language]: its own list, then English (banks in many countries write in
     * English). English alone for a language without a list.
     */
    fun builtIn(language: String = "en"): Map<MoneyType, List<String>> {
        val own = BY_LANGUAGE[language.substringBefore('-').substringBefore('_').lowercase()] ?: return EN
        return EN.keys.associateWith { t -> (own[t].orEmpty() + EN[t].orEmpty()).distinct() }
    }

    /** Built-in words plus a rule's own, less the built-in ones it took away, in the built-in order. */
    fun merged(
        extra: Map<MoneyType, List<String>>,
        language: String = "en",
        removed: Map<MoneyType, List<String>> = emptyMap(),
    ): Map<MoneyType, List<String>> {
        val base = builtIn(language)
        return MoneyType.entries.filter { it in base || it in extra }
            .sortedBy { t -> base.keys.indexOf(t).let { if (it < 0) Int.MAX_VALUE else it } }
            .associateWith { t -> (base[t].orEmpty().filter { w -> removed[t].orEmpty().none { it.equals(w, ignoreCase = true) } } + extra[t].orEmpty()).distinct() }
    }

    /** The first type whose word is in [text] (whole words, any case), or null when there is none. */
    fun detect(
        text: String,
        extra: Map<MoneyType, List<String>> = emptyMap(),
        language: String = "en",
        removed: Map<MoneyType, List<String>> = emptyMap(),
    ): TypeWord? {
        for ((type, words) in merged(extra, language, removed)) {
            val hit = words.mapNotNull { w -> find(text, w)?.let { TypeWord(type, w, it) } }.minByOrNull { it.range.first }
            if (hit != null) return hit
        }
        return null
    }

    /**
     * Where [word] first appears in [text] as whole words, ignoring case (vowel signs count as letters,
     * so Hindi words aren't cut). Japanese and Chinese have no spaces: their words match anywhere.
     */
    fun find(text: String, word: String): IntRange? {
        val w = word.trim()
        if (w.any { Character.UnicodeScript.of(it.code).let { s -> s == Character.UnicodeScript.HAN || s == Character.UnicodeScript.HIRAGANA || s == Character.UnicodeScript.KATAKANA } }) {
            val i = text.indexOf(w)
            return if (i < 0) null else i until i + w.length
        }
        return Regex("(?iu)(?<![\\p{L}\\p{M}\\p{N}])" + Regex.escape(w) + "(?![\\p{L}\\p{M}\\p{N}])").find(text)?.range
    }
}
