package app.burrow.money.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewAskTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    @Test fun notBeforeEnoughPayments() = assertFalse(shouldAskForReview(REVIEW_AFTER_TXNS - 1, null, now))

    @Test fun firstTimeOnceUseful() = assertTrue(shouldAskForReview(REVIEW_AFTER_TXNS, null, now))

    @Test fun notAgainSoon() = assertFalse(shouldAskForReview(500, now - 10 * day, now))

    @Test fun againAfterTheGap() = assertTrue(shouldAskForReview(500, now - REVIEW_EVERY_DAYS * day, now))
}
