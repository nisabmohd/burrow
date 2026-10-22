package app.burrow.money.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay

/**
 * A text field's own copy of its text and cursor. Screens send each change through a ViewModel and
 * get it back a frame or two later; a text field fed only a String then puts the cursor back at the
 * start ("Blue" came out as "lueB"). Here a late copy of what was typed is ignored for a moment, and
 * any other new value (a suggestion picked, Clear, a value the screen trimmed) replaces the text
 * with the cursor at its end.
 */
@Stable
internal class FieldText(initial: String) {
    var field by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    val recent = mutableListOf(initial)

    /** The user typed or moved the cursor. */
    fun typed(v: TextFieldValue, onChange: (String) -> Unit) {
        val changed = v.text != field.text
        field = v
        if (changed) {
            recent += v.text
            onChange(v.text)
        }
    }

    fun set(text: String) {
        field = TextFieldValue(text, TextRange(text.length))
        recent.clear()
        recent += text
    }
}

@Composable
internal fun rememberFieldText(value: String): FieldText {
    val s = remember { FieldText(value) }
    LaunchedEffect(value, s.field.text) {
        if (value == s.field.text) {
            s.recent.clear()
            s.recent += value
            return@LaunchedEffect
        }
        if (value in s.recent) delay(ECHO_WAIT_MS) // most likely a late copy of a keystroke
        s.set(value)
    }
    return s
}

private const val ECHO_WAIT_MS = 400L
