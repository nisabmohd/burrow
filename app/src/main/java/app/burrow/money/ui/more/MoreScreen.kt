package app.burrow.money.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.burrow.money.R
import app.burrow.money.ui.components.Chevron
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.TabScreen

/**
 * More (design/screens/53-more.png). Status tiles and settings rows come on Day 21; until then the
 * one row people need now: Auto-capture.
 */
@Composable
fun MoreScreen(onAutoCapture: () -> Unit) {
    TabScreen(title = stringResource(R.string.tab_more)) { padding ->
        Column(Modifier.padding(padding)) {
            ListGroup {
                ListRow(
                    stringResource(R.string.auto_capture), 0, 1,
                    lead = { IconCircle(painterResource(R.drawable.ic_sms_duotone)) },
                    trailing = { Chevron() },
                    onClick = onAutoCapture,
                )
            }
        }
    }
}
