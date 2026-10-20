package app.burrow.money.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.burrow.money.ui.theme.countUpSpec

/**
 * A hero number that counts up once, the first time the screen shows it (docs/MOTION.md).
 * Coming back to the screen or a later change of value just shows the number. Animates a 0–1
 * fraction, so large amounts stay exact; "Remove animations" makes it jump straight to the end.
 */
@Composable
fun rememberCountUp(target: Long): Long {
    var shown by rememberSaveable { mutableStateOf(false) }
    val fraction = remember { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!shown) {
            fraction.animateTo(1f, countUpSpec())
            shown = true
        }
    }
    return if (fraction.value >= 1f) target else (target * fraction.value).toLong()
}
