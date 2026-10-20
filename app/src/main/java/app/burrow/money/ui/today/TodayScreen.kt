package app.burrow.money.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.burrow.money.R
import app.burrow.money.ui.components.AddFab
import app.burrow.money.ui.components.TabScreen
import app.burrow.money.ui.components.TonalIconButton

/** Today (design/screens/04-today.png). Hero, notes, widgets and Recent come on Day 5 and Day 19. */
@Composable
fun TodayScreen(onAdd: () -> Unit) {
    TabScreen(
        title = stringResource(R.string.tab_today),
        actions = {
            // Accounts (Day 20) and Add expense (Day 3).
            TonalIconButton(painterResource(R.drawable.ic_account_balance_wallet_regular), stringResource(R.string.accounts), onClick = {})
            TonalIconButton(painterResource(R.drawable.ic_add_regular), stringResource(R.string.add_expense), onClick = onAdd)
        },
        fab = { AddFab(stringResource(R.string.add_expense), onAdd) },
    ) { }
}
