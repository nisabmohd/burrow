package app.burrow.money.ui.txn

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.burrow.money.R
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.ui.DateText
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.AmountTrailing
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.forTxn
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.shortName
import app.burrow.money.ui.title
import java.time.LocalDate

/** The lead circle of a transaction: its category, or the neutral swap circle for a transfer. */
@Composable
fun TxnCircle(t: TxnView, size: androidx.compose.ui.unit.Dp = app.burrow.money.ui.theme.Size.iconCircle) =
    when (t.txn.type) {
        TxnType.TRANSFER -> IconCircle(painterResource(R.drawable.ic_swap_horiz_duotone), size = size)
        TxnType.ADJUST_UP, TxnType.ADJUST_DOWN -> IconCircle(painterResource(R.drawable.ic_balance_duotone), size = size)
        else -> CategoryCircle(t.categoryIcon, t.categoryHue, size)
    }

/**
 * One transaction in a list (Today › Recent, Activity): circle, payee, "Food & drinks · HDFC"
 * (plus the time for today's), and the amount (never coloured).
 */
@Composable
fun TxnRow(t: TxnView, index: Int, count: Int, dates: DateText, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val money = LocalMoney.current
    val kind = when (t.txn.type) {
        TxnType.TRANSFER -> stringResource(R.string.type_transfer)
        TxnType.ADJUST_UP, TxnType.ADJUST_DOWN -> stringResource(R.string.balance_fixed)
        else -> t.categoryName.orEmpty()
    }
    val base = stringResource(R.string.dot_join, kind, shortName(t.accountName))
    val line = if (dates.date(t.txn.at) == today) stringResource(R.string.dot_join, base, dates.time(t.txn.at)) else base
    ListRow(
        headline = t.title(),
        supporting = line,
        index = index,
        count = count,
        modifier = modifier,
        lead = { TxnCircle(t) },
        trailing = { AmountTrailing(money.forTxn(t.txn.type, t.txn.amount)) },
        onClick = onClick,
    )
}
