package app.burrow.money.domain

/** Real transactions before Play's rating card may show: the app has been useful for a while. */
const val REVIEW_AFTER_TXNS = 30

/** Days before asking again (Play has its own quota too). */
const val REVIEW_EVERY_DAYS = 120L

/**
 * Whether the play build may show Play's rating card at a calm moment (To review just emptied, as
 * Weft's support prompt): enough real payments, and not asked in the last [REVIEW_EVERY_DAYS]. Pure: unit-tested.
 */
fun shouldAskForReview(realTxns: Int, lastAskedAt: Long?, now: Long): Boolean =
    realTxns >= REVIEW_AFTER_TXNS && (lastAskedAt == null || now - lastAskedAt >= REVIEW_EVERY_DAYS * 24 * 60 * 60 * 1000)
