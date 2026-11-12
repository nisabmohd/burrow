package app.burrow.money.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/**
 * A card with a chart (Activity 11, Insights 36, category 42): a small icon + label row with an
 * optional trailing bit (legend, "All days"), the big number (none when empty), one line, then [chart].
 */
@Composable
fun ChartCard(
    icon: Painter,
    label: String,
    value: String,
    line: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    chart: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainerLow).padding(Space.card)) {
        // At least 24 dp; taller at large text so the label's descenders aren't clipped.
        Row(Modifier.fillMaxWidth().heightIn(min = Size.icon), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            Icon(icon, null, tint = c.primary, modifier = Modifier.size(Size.iconSmall))
            Text(label, style = BurrowType.label, color = c.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            trailing?.invoke()
        }
        Spacer(Modifier.height(Space.small))
        if (value.isNotEmpty()) Text(value, style = BurrowType.headline, color = c.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (line != null) Text(line, style = BurrowType.body, color = c.onSurfaceVariant)
        Spacer(Modifier.height(Space.card))
        chart()
    }
}

/** "--- ₹981 a day": the dashed average's legend. */
@Composable
fun AverageLegend(text: String) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.group)) {
        Text("- - -", style = BurrowType.label, color = c.outline)
        Text(text, style = BurrowType.label, color = c.onSurfaceVariant)
    }
}

/**
 * One legend entry under a line chart (36): a line sample, the period and its total. This period is
 * a solid primary line and reads in the text colour; [before] is a dashed outline line and muted.
 */
@Composable
fun LineLegend(text: String, before: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
        androidx.compose.foundation.Canvas(Modifier.size(app.burrow.money.ui.theme.Chart.legendSwatch, app.burrow.money.ui.theme.Chart.legendSolid)) {
            val y = size.height / 2
            if (before) drawLine(
                c.outline, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = app.burrow.money.ui.theme.Chart.lineBefore.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(size.width / 5, size.width / 5)),
            ) else drawLine(
                c.primary, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = size.height, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        Text(
            text, style = BurrowType.label, color = if (before) c.onSurfaceVariant else c.onSurface,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/**
 * A text link for a [ChartCard] header ("All days"): plain text in primary, because the header row
 * is 24 dp and a TextButton's padding clipped it. The 48 dp tap area still reaches past the row.
 */
@Composable
fun ChartHeaderLink(text: String, onClick: () -> Unit) {
    Text(
        text, style = BurrowType.button, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier.minimumInteractiveComponentSize().clip(Shapes.pill)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = Space.small),
    )
}
