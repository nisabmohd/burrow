package app.burrow.money.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.burrow.money.R
import app.burrow.money.domain.AmountInput
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/** What a keypad key does. */
sealed interface Key {
    data class Digit(val char: Char) : Key
    data object DoubleZero : Key
    data object Dot : Key
    data object Plus : Key
    data object Minus : Key
    data object Backspace : Key
    data object Clear : Key
    data object Done : Key
}

/** Applies a key to the amount being typed. [Key.Done] is handled by the screen. */
fun AmountInput.apply(key: Key): AmountInput = when (key) {
    is Key.Digit -> press(key.char)
    Key.DoubleZero -> pressDoubleZero()
    Key.Dot -> press(AmountInput.DOT)
    Key.Plus -> press(AmountInput.PLUS)
    Key.Minus -> press(AmountInput.MINUS)
    Key.Backspace -> backspace()
    Key.Clear -> AmountInput(digits = digits)
    Key.Done -> this
}

/**
 * The amount keypad (docs/COMPONENTS.md § Keypad): 4 × 4 of 52 dp pill keys, 6 dp apart.
 * Digits on surfaceContainerLow, operators on secondaryContainer, ✓ primary (looks off until
 * [doneEnabled], but still taps). Hold ⌫ to clear. [showDot] is false for currencies without decimals.
 */
@Composable
fun Keypad(onKey: (Key) -> Unit, doneEnabled: Boolean, modifier: Modifier = Modifier, showDot: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val rows = listOf(
        listOf(Key.Digit('1'), Key.Digit('2'), Key.Digit('3'), Key.Backspace),
        listOf(Key.Digit('4'), Key.Digit('5'), Key.Digit('6'), Key.Plus),
        listOf(Key.Digit('7'), Key.Digit('8'), Key.Digit('9'), Key.Minus),
        listOf(if (showDot) Key.Dot else null, Key.Digit('0'), Key.DoubleZero, Key.Done),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.keypad)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.keypad)) {
                row.forEach { key ->
                    val mod = Modifier.weight(1f)
                    when (key) {
                        null -> Box(mod)
                        is Key.Digit -> TextKey(key.char.toString(), mod) { onKey(key) }
                        Key.DoubleZero -> TextKey("00", mod) { onKey(key) }
                        Key.Dot -> TextKey(".", mod) { onKey(key) }
                        Key.Backspace -> IconKey(R.drawable.ic_backspace_regular, stringResource(R.string.key_delete), c.secondaryContainer, c.onSecondaryContainer, mod, onLongClick = { onKey(Key.Clear) }) { onKey(key) }
                        Key.Plus -> IconKey(R.drawable.ic_add_regular, stringResource(R.string.key_plus), c.secondaryContainer, c.onSecondaryContainer, mod) { onKey(key) }
                        Key.Minus -> IconKey(R.drawable.ic_remove_regular, stringResource(R.string.key_minus), c.secondaryContainer, c.onSecondaryContainer, mod) { onKey(key) }
                        Key.Done -> IconKey(
                            R.drawable.ic_check_regular, stringResource(R.string.save),
                            if (doneEnabled) c.primary else c.onSurface.copy(alpha = 0.10f),
                            if (doneEnabled) c.onPrimary else c.onSurface.copy(alpha = 0.38f), mod,
                        ) { onKey(key) }
                        Key.Clear -> Unit
                    }
                }
            }
        }
    }
}

/**
 * The value sheet's keypad (COMPONENTS § Value sheet): 3 × 4 of the same 52 dp pill keys, 1–9, then
 * `.` (or a gap when [showDot] is false: days, time), 0, ⌫ (hold to clear). Done lives in the footer.
 */
@Composable
fun NumberPad(onKey: (Key) -> Unit, modifier: Modifier = Modifier, showDot: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val rows = listOf(
        listOf<Key?>(Key.Digit('1'), Key.Digit('2'), Key.Digit('3')),
        listOf<Key?>(Key.Digit('4'), Key.Digit('5'), Key.Digit('6')),
        listOf<Key?>(Key.Digit('7'), Key.Digit('8'), Key.Digit('9')),
        listOf(if (showDot) Key.Dot else null, Key.Digit('0'), Key.Backspace),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.keypad)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.keypad)) {
                row.forEach { key ->
                    val mod = Modifier.weight(1f)
                    when (key) {
                        null -> Box(mod)
                        is Key.Digit -> TextKey(key.char.toString(), mod) { onKey(key) }
                        Key.Dot -> TextKey(".", mod) { onKey(key) }
                        Key.Backspace -> IconKey(R.drawable.ic_backspace_regular, stringResource(R.string.key_delete), c.secondaryContainer, c.onSecondaryContainer, mod, onLongClick = { onKey(Key.Clear) }) { onKey(key) }
                        else -> Box(mod)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextKey(text: String, modifier: Modifier, onClick: () -> Unit) {
    KeyShape(MaterialTheme.colorScheme.surfaceContainerLow, modifier, label = null, onClick = onClick) {
        CappedFontScale { Text(text, style = BurrowType.keypad, color = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun IconKey(icon: Int, label: String, bg: Color, fg: Color, modifier: Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    KeyShape(bg, modifier, label, onLongClick, onClick) { Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(Size.icon)) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyShape(bg: Color, modifier: Modifier, label: String?, onLongClick: (() -> Unit)? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier.height(Size.keypadKey).clip(Shapes.pill).background(bg)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = { haptics.tick(); onClick() })
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}
