package app.burrow.money.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.burrow.money.R
import app.burrow.money.ui.components.TabScreen

/** Plan (design/screens/22-plan-bills.png). Bills | Budgets | Goals come in week 3. */
@Composable
fun PlanScreen() {
    TabScreen(title = stringResource(R.string.tab_plan)) { }
}
