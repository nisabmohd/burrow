package app.burrow.money.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.burrow.money.R
import app.burrow.money.ui.components.TabScreen

/** More (design/screens/53-more.png). Status tiles and settings rows come on Day 21. */
@Composable
fun MoreScreen() {
    TabScreen(title = stringResource(R.string.tab_more)) { }
}
