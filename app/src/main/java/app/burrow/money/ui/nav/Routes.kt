package app.burrow.money.ui.nav

import kotlinx.serialization.Serializable

/* Type-safe routes. Tabs first; pushed screens are added as they are built (docs/ROADMAP.md). */

@Serializable data object TodayRoute
@Serializable data object ActivityRoute
@Serializable data object PlanRoute
@Serializable data object InsightsRoute
@Serializable data object MoreRoute

/** New transaction of [type]; or edit [editId]; or a copy of [duplicateId] dated now. */
@Serializable data class AddRoute(val type: String = "EXPENSE", val editId: Long = 0L, val duplicateId: Long = 0L)

@Serializable data class TxnRoute(val id: Long)
