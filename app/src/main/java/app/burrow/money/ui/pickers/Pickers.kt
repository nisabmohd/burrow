package app.burrow.money.ui.pickers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.LaunchedEffect
import app.burrow.money.R
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.CategoryEntity
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.duotoneIcon
import app.burrow.money.ui.components.tick
import app.burrow.money.ui.theme.Size
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * The pickers every form shares (docs/DESIGN.md rule 8: every picker is a bottom sheet).
 * Each is a BurrowSheet: header with title, one line and close; rows; selected row tinted with a check.
 */

/** The 40 dp circle of a category, in its own colours. */
@Composable
fun CategoryCircle(icon: String?, hue: Float?, size: androidx.compose.ui.unit.Dp = Size.iconCircle) =
    IconCircle(painterResource(duotoneIcon(icon)), hue = hue, size = size)

/** Category (19): every category of one kind, with what was spent on it this month. */
@Composable
fun CategorySheet(
    categories: List<CategoryEntity>,
    spent: Map<Long, Long>,
    selectedId: Long?,
    onPick: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    val haptics = LocalHapticFeedback.current
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.category), onDismiss, stringResource(R.string.category_sheet_line))
        SheetBody {
            ListGroup {
                categories.forEachIndexed { i, cat ->
                    val total = spent[cat.id] ?: 0L
                    ListRow(
                        headline = cat.name,
                        index = i,
                        count = categories.size,
                        supporting = if (total > 0) stringResource(R.string.spent_this_month, money.plain(total)) else stringResource(R.string.nothing_this_month),
                        selected = cat.id == selectedId,
                        lead = { CategoryCircle(cat.icon, cat.colorHue) },
                        trailing = { if (cat.id == selectedId) SelectedCheck() },
                        onClick = { haptics.tick(); onPick(cat) },
                    )
                }
            }
        }
    }
}

/** Account (20): accounts with their balance and kind. */
@Composable
fun AccountSheet(
    accounts: List<AccountWithBalance>,
    selectedId: Long?,
    onPick: (AccountWithBalance) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    val haptics = LocalHapticFeedback.current
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.account), onDismiss, stringResource(R.string.account_sheet_line))
        SheetBody {
            ListGroup {
                accounts.forEachIndexed { i, a ->
                    ListRow(
                        headline = a.account.name,
                        index = i,
                        count = accounts.size,
                        supporting = stringResource(R.string.dot_join, money.balance(a.balance), accountTypeLabel(a.account.type)),
                        selected = a.account.id == selectedId,
                        lead = { CategoryCircle(a.account.icon, a.account.colorHue) },
                        trailing = { if (a.account.id == selectedId) SelectedCheck() },
                        onClick = { haptics.tick(); onPick(a) },
                    )
                }
            }
        }
    }
}

@Composable
fun accountTypeLabel(type: String): String = stringResource(
    when (type) {
        AccountType.CASH -> R.string.account_cash
        AccountType.WALLET -> R.string.account_wallet
        AccountType.CARD -> R.string.account_card
        AccountType.LOAN -> R.string.account_loan
        AccountType.SAVINGS -> R.string.account_savings
        else -> R.string.account_bank
    },
)

@Composable
private fun SelectedCheck() =
    Icon(painterResource(R.drawable.ic_check_regular), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Size.icon))

/** One line of text (payee, note): a field that opens focused, and Done. */
@Composable
fun TextSheet(title: String, value: String, placeholder: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val focus = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    val done = { haptics.confirm(); onDone(text.text.trim()) }
    BurrowSheet(onDismiss) {
        SheetHeader(title, onDismiss)
        SheetBody {
            PillTextFieldValue(text, { text = it }, placeholder, Modifier.focusRequester(focus), onDone = done)
        }
        SheetFooter { BurrowButton(stringResource(R.string.done), done, Modifier.fillMaxWidth(), height = Size.buttonBottom) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun PillTextFieldValue(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, placeholder: String, modifier: Modifier, onDone: () -> Unit) {
    // PillTextField works on String; keep the cursor at the end by re-wrapping.
    PillTextField(
        value = value.text,
        onValueChange = { onChange(TextFieldValue(it, TextRange(it.length))) },
        placeholder = placeholder,
        modifier = modifier,
        clearLabel = stringResource(R.string.clear),
        onDone = onDone,
    )
}

/** A day on or before today (transaction dates), or from today on with [future] (goal dates). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSheet(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, future: Boolean = false, title: String? = null, anyDay: Boolean = false) {
    val today = remember { LocalDate.now() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = anyDay || if (future) utcTimeMillis >= today.utcMillis() else utcTimeMillis <= today.utcMillis()
            override fun isSelectableYear(year: Int) = anyDay || if (future) year >= today.year else year <= today.year
        },
    )
    val haptics = LocalHapticFeedback.current
    BurrowSheet(onDismiss) {
        SheetHeader(title ?: stringResource(R.string.date), onDismiss)
        SheetBody {
            DatePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            )
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.done), {
                haptics.confirm()
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }, Modifier.fillMaxWidth(), height = Size.buttonBottom)
        }
    }
}

/** Material's date picker counts days in UTC. */
private fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
