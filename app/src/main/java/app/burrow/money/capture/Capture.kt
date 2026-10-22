package app.burrow.money.capture

import app.burrow.money.AppContainer
import app.burrow.money.data.CaptureResult
import app.burrow.money.data.PaymentFound
import app.burrow.money.data.TxnType
import app.burrow.money.data.tidyPayee
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
        // The waiting payment stays in To review either way; the switch only stops the notification.
        val st = settings.settings.first()
        val remembered = c.payee?.let { money.usualCategoryFor(tidyPayee(it), type, raw = it) }
        when {
            // Customise › Transactions › "Save it for me": the remembered category, editable later.
            st.onPaymentFound == PaymentFound.SAVE && remembered != null -> {
                val txnId = capture.save(c.id, remembered)
                if (st.notifyCapture && txnId != null) {
                    val cat = categories.firstOrNull { it.id == remembered }?.name.orEmpty()
                    notifier.showSaved(c, cat, txnId, hideAmount = st.hideInNotifications)
                }
            }
            // "Open it to edit first": the notification opens New expense, filled in.
            st.onPaymentFound == PaymentFound.EDIT_FIRST -> if (st.notifyCapture) notifier.showEdit(c, from, hideAmount = st.hideInNotifications)
            else -> if (st.notifyCapture) notifier.show(c, suggested, from, hideAmount = st.hideInNotifications)
        }
    }
    if (result is CaptureResult.Found) app.burrow.money.home.HomeSurfaces.refresh(this) // "Review N payments" shortcut
    return result
}
