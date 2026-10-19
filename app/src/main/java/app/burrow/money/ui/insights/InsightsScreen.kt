package app.burrow.money.ui.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.burrow.money.R
import app.burrow.money.ui.components.FilterHeaderButtons
import app.burrow.money.ui.components.TabScreen

/** Insights (design/screens/36-insights.png). Charts and patterns come in week 4. */
@Composable
fun InsightsScreen() {
    TabScreen(
        title = stringResource(R.string.tab_insights),
        actions = {
            FilterHeaderButtons(
                funnel = painterResource(R.drawable.ic_filter_list_regular),
                calendar = painterResource(R.drawable.ic_calendar_month_regular),
                filterLabel = stringResource(R.string.filter),
                datesLabel = stringResource(R.string.dates),
                filterCount = 0,
                datesActive = false,
                onFilter = {},
                onDates = {},
            )
        },
    ) { }
}
