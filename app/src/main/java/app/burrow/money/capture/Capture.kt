package app.burrow.money.capture

import app.burrow.money.AppContainer
import app.burrow.money.data.CaptureResult
import app.burrow.money.data.TxnType
import kotlinx.coroutines.flow.first

/**
 * One incoming message, from either source (docs/CAPTURE.md § Pipeline): store it, then ask with
 * the capture notification when it is a new payment.
 */
suspend fun AppContainer.onMessage(source: String, sender: String, body: String, at: Long = System.currentTimeMillis()): CaptureResult {
    val result = capture.handle(source, sender, body, at)
    if (result is CaptureResult.Found) {
        val c = result.capture
        val type = if (c.direction == Direction.IN.name) TxnType.INCOME else TxnType.EXPENSE
        val ids = capture.suggestions(c.payee, type)
        val categories = money.categories.first()
        val suggested = ids.mapNotNull { id -> categories.firstOrNull { it.id == id } }
        val account = capture.accountFor(c)?.let { id -> money.accounts.first().firstOrNull { it.account.id == id }?.account }
        val from = account?.let { a -> listOfNotNull(app.burrow.money.ui.shortName(a.name), c.account?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.isNotEmpty() }?.let { "••$it" }).joinToString(" ") }
        notifier.show(c, suggested, from)
    }
    return result
}
