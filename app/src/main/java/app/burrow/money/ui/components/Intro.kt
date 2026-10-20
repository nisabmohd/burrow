package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.burrow.money.R
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/** Step dots (Welcome, Auto-capture intro): the current one is a 24 dp pill. */
@Composable
fun StepDots(step: Int, steps: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val label = stringResource(R.string.step_of, step + 1, steps)
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
        repeat(steps) { i ->
            Box(Modifier.size(width = if (i == step) Size.dotActive else Size.dot, height = Size.dot).clip(Shapes.pill).background(if (i == step) c.primary else c.outline))
        }
    }
}

/** The rose panel that illustrates an intro step. */
@Composable
fun IntroPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(Shapes.surface).background(MaterialTheme.colorScheme.primaryContainer).padding(Space.hero),
        verticalArrangement = Arrangement.spacedBy(Space.block),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** A white card inside the panel, slightly tilted like the design. */
@Composable
fun IntroCard(modifier: Modifier = Modifier, tilt: Float = 0f, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.rotate(tilt).clip(Shapes.surface).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = Space.card, vertical = Space.block),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.block),
        content = content,
    )
}

/** A small tilted card with content stacked (Welcome's budget and bill, the intro's two promises). */
@Composable
fun IntroTile(modifier: Modifier = Modifier, tilt: Float = 0f, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.rotate(tilt).clip(Shapes.surface).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(Space.card),
        verticalArrangement = Arrangement.spacedBy(Space.small),
        content = content,
    )
}

/** The Burrow mark on a rounded square (Welcome, About). */
@Composable
fun BurrowLogo(size: Dp, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.25f)).background(c.primary), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_notification), null, tint = c.onPrimary, modifier = Modifier.size(size * 0.6f))
    }
}
