package app.burrow.money.ui.nav

import kotlinx.serialization.Serializable

/* Type-safe routes. Tabs first; pushed screens are added as they are built (docs/ROADMAP.md). */

@Serializable data object TodayRoute
@Serializable data object ActivityRoute
@Serializable data object PlanRoute
@Serializable data object InsightsRoute
@Serializable data object MoreRoute

/** New transaction of [type]; or edit [editId]; or a copy of [duplicateId] dated now. */
@Serializable data class AddRoute(val type: String = "EXPENSE", val editId: Long = 0L, val duplicateId: Long = 0L, val accountId: Long = 0L)

@Serializable data class TxnRoute(val id: Long)

// Auto-capture (week 2)
@Serializable data object ReviewRoute
@Serializable data object CaptureIntroRoute
@Serializable data object AutoCaptureRoute
@Serializable data object RulesRoute
@Serializable data class RuleRoute(val id: String)

/** Teach a waiting message ([captureId]) or a pasted one ([sender], [text]). */
@Serializable data class TeachRoute(val captureId: Long = 0L, val sender: String = "", val text: String = "")

// Plan (week 3)
@Serializable data class BillRoute(val id: Long)

/** New bill, prefilled when the fields are set (Subscriptions' hint); or edit [editId]. */
@Serializable data class NewBillRoute(
    val editId: Long = 0L,
    val kind: String = "BILL",
    val name: String = "",
    val amount: String = "",
    val categoryId: Long = 0L,
    val accountId: Long = 0L,
)
@Serializable data object SubscriptionsRoute

/** The overall budget when [categoryId] is -1, else that category's. */
@Serializable data class BudgetRoute(val categoryId: Long = -1L)
@Serializable data class GoalRoute(val id: Long)
@Serializable data class NewGoalRoute(val editId: Long = 0L)

// Accounts (week 4)
@Serializable data object AccountsRoute
@Serializable data class AccountRoute(val id: Long)
@Serializable data class NewAccountRoute(val editId: Long = 0L)

/** Where a notification asks the app to go. */
sealed interface OpenFromNotification {
    data object Review : OpenFromNotification
    data class Bill(val id: Long) : OpenFromNotification
    data object Budgets : OpenFromNotification
}

// Insights (week 4)
@Serializable data class CategoryInsightsRoute(val categoryId: Long)
