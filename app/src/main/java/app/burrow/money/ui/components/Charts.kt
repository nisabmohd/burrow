package app.burrow.money.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.burrow.money.ui.theme.BAR_STAGGER_MS
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Easing
import app.burrow.money.ui.theme.Size

/*
 * One chart style everywhere (docs/COMPONENTS.md § Charts). Compose Canvas only, no chart library.
 * Pill bars: 16 dp wide (shrink to 4 dp when dense), full radius, 104 dp tall, labels under,
 * a dot for empty days, a dashed average line. Selected bar = primary, others = primary 22% on surface.
 * Daily bars up to 31 days; weekly beyond; monthly beyond 70 days.
 */
data class Bar(val label: String, val value: Long)

@Composable
fun PillBarChart(
    bars: List<Bar>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showEveryNthLabel: Int = if (bars.size > 14) (bars.size + 5) / 6 else 1,
) {
    val c = MaterialTheme.colorScheme
    val max = (bars.maxOfOrNull { it.value } ?: 1L).coerceAtLeast(1L)
    val avg = if (bars.isEmpty()) 0f else bars.sumOf { it.value }.toFloat() / bars.size
    val soft = c.primary.copy(alpha = 0.22f).compositeOverSurface(c.surfaceContainer)
    // Grow once per data set.
    val grow = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) { grow.animateTo(1f, tween(450 + bars.size * BAR_STAGGER_MS / 2, easing = Easing.emphasizedDecelerate)) }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(Size.chartHeight)) {
            Row(Modifier.fillMaxWidth().height(Size.chartHeight), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 14) 3.dp else 6.dp)) {
                bars.forEachIndexed { i, b ->
                    Box(Modifier.weight(1f).height(Size.chartHeight).clickable { onSelect(i) }, contentAlignment = Alignment.BottomCenter) {
                        Canvas(Modifier.fillMaxWidth().height(Size.chartHeight)) {
                            val w = minOf(Size.chartBar.toPx(), size.width)
                            if (b.value == 0L) {
                                drawCircle(c.surfaceContainerHigh, radius = 3.dp.toPx(), center = Offset(size.width / 2, size.height - 3.dp.toPx()))
                            } else {
                                val h = maxOf(8.dp.toPx(), size.height * b.value / max * grow.value)
                                drawRoundRect(
                                    if (i == selected) c.primary else soft,
                                    topLeft = Offset((size.width - w) / 2, size.height - h),
                                    size = GSize(w, h), cornerRadius = CornerRadius(w / 2, w / 2),
                                )
                            }
                        }
                    }
                }
            }
            Canvas(Modifier.fillMaxWidth().height(Size.chartHeight)) {
                val y = size.height - size.height * (avg / max)
                drawLine(c.outline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 14) 3.dp else 6.dp)) {
            bars.forEachIndexed { i, b ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    if (i % showEveryNthLabel == 0 || i == bars.lastIndex) {
                        Text(b.label, style = BurrowType.label, color = if (i == selected) c.primary else c.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun Color.compositeOverSurface(bg: Color): Color =
    Color(red * alpha + bg.red * (1 - alpha), green * alpha + bg.green * (1 - alpha), blue * alpha + bg.blue * (1 - alpha), 1f)

/** Ring with a gap, filling clockwise from 12 o'clock. Rows 40 dp, widgets 52 dp, wide widgets 104 dp. */
@Composable
fun ProgressRing(progress: Float, color: Color, track: Color, size: Dp, stroke: Dp = 4.dp, content: @Composable () -> Unit = {}) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val arc = GSize(this.size.width - s, this.size.height - s)
            val p = progress.coerceIn(0f, 1f)
            val gap = if (p in 0.01f..0.99f) 14f else 0f
            drawArc(track, -90f + 360f * p + gap / 2, 360f * (1 - p) - gap, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
            if (p > 0f) drawArc(color, -90f, 360f * p - gap / 2, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Heatmap day: 38 dp circle, 5 tonal steps of primary (0 = surfaceContainer), today has a 2 dp ring. */
fun heatLevel(value: Long, max: Long): Int = when {
    value <= 0 -> 0
    value < max * 0.25 -> 1
    value < max * 0.5 -> 2
    value < max * 0.75 -> 3
    else -> 4
}
