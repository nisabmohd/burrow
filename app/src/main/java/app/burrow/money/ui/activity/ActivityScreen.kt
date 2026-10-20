package app.burrow.money.ui.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.burrow.money.R
import app.burrow.money.ui.components.FilterHeaderButtons
import app.burrow.money.ui.components.AddFab
import app.burrow.money.ui.components.TabScreen

/** Activity (design/screens/11-activity.png). Day groups on Day 4, filters and the chart on Day 16. */
@Composable
fun ActivityScreen(onAdd: () -> Unit) {
    TabScreen(
        title = stringResource(R.string.tab_activity),
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
        fab = { AddFab(stringResource(R.string.add_expense), onAdd) },
    ) { }
}
