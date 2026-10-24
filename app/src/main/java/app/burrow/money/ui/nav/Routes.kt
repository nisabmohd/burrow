package app.burrow.money.ui.nav

import kotlinx.serialization.Serializable

/* Type-safe routes. Tabs first; pushed screens are added as they are built (docs/ROADMAP.md). */

@Serializable data object TodayRoute
@Serializable data object ActivityRoute
@Serializable data object PlanRoute
@Serializable data object InsightsRoute
@Serializable data object MoreRoute

/**
 * New transaction of [type] (blank = Customise › Expense › Opens as); or edit [editId]; or a copy of
 * [duplicateId] dated now; or a found payment ([captureId], "Open it to edit first").
 */
@Serializable data class AddRoute(val type: String = "", val editId: Long = 0L, val duplicateId: Long = 0L, val accountId: Long = 0L, val captureId: Long = 0L)

@Serializable data class TxnRoute(val id: Long)

// Auto-capture (week 2)
@Serializable data object ReviewRoute
@Serializable data object CaptureIntroRoute
@Serializable data object AutoCaptureRoute
@Serializable data object RulesRoute
@Serializable data class RuleRoute(val id: String)

/** Teach a waiting message ([captureId]) or a pasted one ([sender], [text]). */
@Serializable data object MissedRoute
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
@Serializable data class NewAccountRoute(val editId: Long = 0L, val type: String = "")

/** Where a notification asks the app to go. */
sealed interface OpenFromNotification {
    data object Review : OpenFromNotification
    data class Bill(val id: Long) : OpenFromNotification
    data object Budgets : OpenFromNotification
    data object Insights : OpenFromNotification
    data class EditCapture(val captureId: Long) : OpenFromNotification
    data class Txn(val id: Long) : OpenFromNotification
    data object Accounts : OpenFromNotification
    /** Widget, shortcut or quick settings tile. */
    data class NewTxn(val type: String) : OpenFromNotification
    data object Search : OpenFromNotification
    data object SundayReview : OpenFromNotification
}

// Insights (week 4)
@Serializable data class CategoryInsightsRoute(val categoryId: Long)

// Welcome and More (week 5)
@Serializable data object WelcomeRoute
@Serializable data object CategoriesRoute
/** Edit a category; 0 = a new spending one. */
@Serializable data class EditCategoryRoute(val id: Long = 0L)
@Serializable data object CurrencyRoute
@Serializable data object AppearanceRoute
@Serializable data object FontRoute
@Serializable data object LanguageRoute
@Serializable data object NotificationsRoute
@Serializable data object BackupRoute
@Serializable data object AppLockRoute
/** Choose a new PIN (twice). */
@Serializable data object SetPinRoute
@Serializable data object AboutRoute
@Serializable data object LicencesRoute
/** One licence's full text ([index] into LICENCES). */
@Serializable data class LicenceRoute(val index: Int)

// Customise (design update 2026-10-20)
/** Customise (80–85), opened on [tab] (a CustomiseTab name). */
@Serializable data class CustomiseRoute(val tab: String = "TODAY")

// Next round (burrow-next, 2026-10-22)
@Serializable data object FavouritesRoute
@Serializable data class SplitRoute(val txnId: Long)
@Serializable data object SundayReviewRoute
@Serializable data object ImportRoute
/** Check before import (11): the file the user picked, read from [uri], as [source] (an ImportSource name). */
@Serializable data class ImportCheckRoute(val source: String, val uri: String)
