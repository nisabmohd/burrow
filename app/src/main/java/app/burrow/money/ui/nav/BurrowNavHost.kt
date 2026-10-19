package app.burrow.money.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.burrow.money.R
import app.burrow.money.ui.activity.ActivityScreen
import app.burrow.money.ui.components.BurrowNavBar
import app.burrow.money.ui.components.NavItem
import app.burrow.money.ui.insights.InsightsScreen
import app.burrow.money.ui.more.MoreScreen
import app.burrow.money.ui.plan.PlanScreen
import app.burrow.money.ui.theme.NavMotion
import app.burrow.money.ui.today.TodayScreen
import kotlin.reflect.KClass

/** The five tabs, in nav order (docs/DESIGN.md § Screens, by tab). */
private enum class Tab(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    TODAY(TodayRoute, TodayRoute::class, R.string.tab_today, R.drawable.ic_home_regular, R.drawable.ic_home_fill),
    ACTIVITY(ActivityRoute, ActivityRoute::class, R.string.tab_activity, R.drawable.ic_receipt_long_regular, R.drawable.ic_receipt_long_fill),
    PLAN(PlanRoute, PlanRoute::class, R.string.tab_plan, R.drawable.ic_calendar_month_regular, R.drawable.ic_calendar_month_fill),
    INSIGHTS(InsightsRoute, InsightsRoute::class, R.string.tab_insights, R.drawable.ic_insights_regular, R.drawable.ic_insights_fill),
    MORE(MoreRoute, MoreRoute::class, R.string.tab_more, R.drawable.ic_more_horiz_regular, R.drawable.ic_more_horiz_fill),
}

private fun NavDestination?.tab(): Tab? = this?.let { d -> Tab.entries.firstOrNull { t -> d.hierarchy.any { it.hasRoute(t.routeClass) } } }

private fun NavBackStackEntry.isTab() = destination.tab() != null

/**
 * One NavHost for the app. Motion from Motion.kt only (docs/MOTION.md): switching tabs fades
 * through; pushing a screen uses shared axis X, reversed on back (predictive back is on in the
 * manifest). The bottom nav shows on tabs only.
 */
@Composable
fun BurrowNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination.tab()
    Column(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = TodayRoute,
            modifier = Modifier.weight(1f),
            enterTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabEnter(this) else NavMotion.push(this) },
            exitTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabExit(this) else NavMotion.pushExit(this) },
            popEnterTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabEnter(this) else NavMotion.pop(this) },
            popExitTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabExit(this) else NavMotion.popExit(this) },
        ) {
            composable<TodayRoute> { TodayScreen() }
            composable<ActivityRoute> { ActivityScreen() }
            composable<PlanRoute> { PlanScreen() }
            composable<InsightsRoute> { InsightsScreen() }
            composable<MoreRoute> { MoreScreen() }
        }
        if (current != null) {
            val items = Tab.entries.map { NavItem(stringResource(it.label), it.icon, it.selectedIcon) }
            BurrowNavBar(items, selected = current.ordinal, onSelect = { i ->
                nav.navigate(Tab.entries[i].route) {
                    // One copy of each tab; each keeps its scroll position when you come back.
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            })
        }
    }
}
