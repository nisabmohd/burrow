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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
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
    /** Tapping picks the bar under the finger; null for charts that only show (widgets). */
    onSelect: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    showEveryNthLabel: Int = if (bars.size > 14) (bars.size + 5) / 6 else 1,
    /** Shorter for the mini charts in widgets (Busiest day, This week). */
    height: Dp = Size.chartHeight,
    showAverage: Boolean = true,
    labelFor: ((Int) -> String)? = null,
    /** What TalkBack reads for the whole chart ("Last 14 days, ₹13,738 spent…"). */
    description: String? = null,
) {
    val c = MaterialTheme.colorScheme
    // Refund-heavy days can net below zero: a bar never points down.
    val max = (bars.maxOfOrNull { it.value } ?: 1L).coerceAtLeast(1L)
    // One tap area over the whole chart (bars are too narrow to be 48 dp targets each): the tap's
    // x picks the bar. TalkBack gets the summary and one action per bar.
    val tapArea = if (onSelect == null || bars.isEmpty()) Modifier else Modifier
        .pointerInput(bars) { detectTapGestures { pos -> onSelect(barIndexAt(pos.x, size.width.toFloat(), bars.size)) } }
        .semantics {
            description?.let { contentDescription = it }
            customActions = bars.indices.map { i -> CustomAccessibilityAction(labelFor?.invoke(i) ?: bars[i].label) { onSelect(i); true } }
        }
    val avg = if (bars.isEmpty()) 0f else bars.sumOf { it.value.coerceAtLeast(0L) }.toFloat() / bars.size
    val soft = c.primary.copy(alpha = 0.22f).compositeOverSurface(c.surfaceContainer)
    // Grow once per data set.
    val grow = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) { grow.animateTo(1f, app.burrow.money.ui.theme.barGrowSpec(bars.size)) }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(height).then(tapArea)) {
            Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 14) 3.dp else 6.dp)) {
                bars.forEachIndexed { i, b ->
                    Box(Modifier.weight(1f).height(height), contentAlignment = Alignment.BottomCenter) {
                        Canvas(Modifier.fillMaxWidth().height(height)) {
                            val w = minOf(Size.chartBar.toPx(), size.width)
                            if (b.value <= 0L) {
                                drawCircle(c.surfaceContainerHigh, radius = 3.dp.toPx(), center = Offset(size.width / 2, size.height - 3.dp.toPx()))
                            } else {
                                val h = pillBarHeight(b.value, max, size.height, w, grow.value)
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
            if (showAverage && avg > 0f) Canvas(Modifier.fillMaxWidth().height(height)) {
                val y = size.height - size.height * (avg / max)
                drawLine(c.outline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 14) 3.dp else 6.dp)) {
            bars.forEachIndexed { i, b ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    // Every nth label plus the last one; a label too close to the last one gives way ("21 22").
                    val n = showEveryNthLabel.coerceAtLeast(1)
                    val shown = i == bars.lastIndex || (i % n == 0 && bars.lastIndex - i >= (n + 1) / 2)
                    if (shown) {
                        // Wider than its slot on dense charts (31 days on a 360 dp phone): centred, never clipped.
                        Text(
                            b.label, style = BurrowType.label, color = if (i == selected) c.primary else c.onSurfaceVariant, maxLines = 1, softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
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
            val p = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
            val gap = ringGapDegrees(s, arc.width / 2, 3.dp.toPx())
            when {
                p <= 0f -> drawArc(track, -90f, 360f, false, tl, arc, style = Stroke(s))
                p >= 1f -> drawArc(color, -90f, 360f, false, tl, arc, style = Stroke(s))
                else -> {
                    val done = 360f * p
                    val rest = 360f - done - gap
                    if (rest > 0f) drawArc(track, -90f + done + gap / 2, rest, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
                    // A sliver still shows as a dot rather than vanishing.
                    drawArc(color, -90f + gap / 2, (done - gap).coerceAtLeast(0.01f), false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
                }
            }
        }
        content()
    }
}

/**
 * The angle two round-capped arcs must leave between them so their caps don't touch: one stroke
 * width (half a cap at each end) plus [visible] of clear space, measured on the ring's radius.
 */
/** Which of [count] equal bars a tap at [x] (of [width]) lands on; taps past either end pick the end bar. */
internal fun barIndexAt(x: Float, width: Float, count: Int): Int =
    if (count <= 0 || width <= 0f) 0 else (x / width * count).toInt().coerceIn(0, count - 1)

/**
 * A pill bar's height: never shorter than it is wide (a small day is a round dot, not a flat blob),
 * never taller than the chart. 0 for days with nothing (those draw a small dot instead).
 */
internal fun pillBarHeight(value: Long, max: Long, chartHeight: Float, barWidth: Float, grow: Float = 1f): Float =
    if (value <= 0L || max <= 0L) 0f
    else maxOf(barWidth, chartHeight * value / max * grow).coerceAtMost(chartHeight)

internal fun ringGapDegrees(stroke: Float, radius: Float, visible: Float): Float =
    if (radius <= 0f) 0f else Math.toDegrees(((stroke + visible) / radius).toDouble()).toFloat().coerceAtMost(60f)

/** Heatmap day: 38 dp circle, 5 tonal steps of primary (0 = surfaceContainer), today has a 2 dp ring. */
fun heatLevel(value: Long, max: Long): Int = when {
    value <= 0 -> 0
    value < max * 0.25 -> 1
    value < max * 0.5 -> 2
    value < max * 0.75 -> 3
    else -> 4
}

/**
 * Today's wavy progress (prototype `wavy()`): a sine line for the part used, then a faint straight
 * track and a dot marking the end. Colour is the content colour of the hero.
 */
@Composable
fun WavyProgress(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(Size.wavyHeight)) {
        val stroke = 4.dp.toPx()
        val mid = size.height / 2
        val amp = 3.2.dp.toPx()
        val wave = 26.dp.toPx()
        val inset = 3.dp.toPx()
        val end = size.width * p
        if (end > inset * 2) {
            val path = androidx.compose.ui.graphics.Path()
            var x = inset
            path.moveTo(x, mid)
            while (x <= end - inset) {
                path.lineTo(x, mid + amp * kotlin.math.sin(2 * Math.PI * x / wave).toFloat())
                x += 2.dp.toPx()
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        val gap = 8.dp.toPx()
        if (end < size.width - 14.dp.toPx()) {
            drawLine(color.copy(alpha = 0.22f), Offset(end + gap, mid), Offset(size.width - gap, mid), strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(color, radius = 2.dp.toPx(), center = Offset(size.width - 2.dp.toPx(), mid))
        }
    }
}

/**
 * The ring at the end of a budget or goal row (designs 23, 24): 40 dp, the category's colour (red
 * when over), the percent inside, or a check when a goal is reached. Moves to a new value with
 * the progress motion.
 */
@Composable
fun RingPercent(fraction: Float, color: Color, track: Color, size: Dp = Size.iconCircle, done: Boolean = false, over: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val shown by androidx.compose.animation.core.animateFloatAsState(fraction.coerceIn(0f, 1f), app.burrow.money.ui.theme.progressSpec(), label = "ring")
    val ringColor = if (over) c.error else color
    val stroke = when { size >= Size.ringWide -> 10.dp; size > Size.iconCircle -> 5.dp; else -> 3.dp }
    ProgressRing(if (done) 1f else shown, ringColor, if (over) c.error.copy(alpha = 0.25f) else track, size, stroke = stroke) {
        if (done) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(app.burrow.money.R.drawable.ic_check_regular), null,
                tint = c.onSurface, modifier = Modifier.size(Size.iconSmall),
            )
        } else {
            CappedFontScale(1f) {
                Text(
                    "${Math.round(fraction * 100)}%",
                    style = when { size >= Size.ringWide -> BurrowType.title; size > Size.iconCircle -> BurrowType.label; else -> BurrowType.tiny },
                    color = c.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}


/**
 * The Insights line (36): running total this period in primary with a soft fill, and the previous
 * period dashed in outline grey, on one scale. Grows in once per data set.
 */
@Composable
fun CumulativeLineChart(current: List<Long>, previous: List<Long>, modifier: Modifier = Modifier, height: Dp = Size.lineChart) {
    val c = MaterialTheme.colorScheme
    val grow = remember(current) { Animatable(0f) }
    LaunchedEffect(current) { grow.animateTo(1f, app.burrow.money.ui.theme.progressSpec()) }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val n = maxOf(current.size, previous.size, 2)
        val max = maxOf(current.maxOrNull() ?: 0, previous.maxOrNull() ?: 0, 1).toFloat()
        val pad = 2.dp.toPx() // half the line, so the top and ends aren't cut off
        fun x(i: Int) = pad + (size.width - 2 * pad) * i / (n - 1)
        fun y(v: Long) = size.height - pad - (size.height - 2 * pad) * (v.coerceAtLeast(0L) / max) * grow.value
        fun path(values: List<Long>) = androidx.compose.ui.graphics.Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        if (previous.size > 1) drawPath(path(previous), c.outline, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)), cap = StrokeCap.Round))
        if (current.size > 1) {
            val line = path(current)
            val area = androidx.compose.ui.graphics.Path().apply {
                addPath(line)
                lineTo(x(current.lastIndex), size.height)
                lineTo(x(0), size.height)
                close()
            }
            drawPath(area, c.primary.copy(alpha = 0.12f))
            drawPath(line, c.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        } else if (current.size == 1) {
            drawCircle(c.primary, 3.dp.toPx(), Offset(x(0), y(current[0])))
        }
    }
}

/** A small line of daily values (A day widget, Net worth widget). */
@Composable
fun Sparkline(values: List<Long>, modifier: Modifier = Modifier, fill: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Canvas(modifier.fillMaxWidth().height(Size.sparkline)) {
        if (values.size < 2) return@Canvas
        val min = values.min().toFloat()
        val flat = values.max() == values.min()
        val span = (values.max() - values.min()).toFloat().coerceAtLeast(1f)
        val pad = 1.dp.toPx()
        val p = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { i, v ->
            val px = pad + (size.width - 2 * pad) * i / (values.size - 1)
            val py = if (flat) size.height / 2 else size.height - size.height * ((v - min) / span) * 0.9f - size.height * 0.05f
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        if (fill) {
            val area = androidx.compose.ui.graphics.Path().apply { addPath(p); lineTo(size.width - pad, size.height); lineTo(pad, size.height); close() }
            drawPath(area, c.primary.copy(alpha = 0.12f))
        }
        drawPath(p, c.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * Categories as one stacked bar of pills, 3 dp apart (By category, 37). The parts always fill the
 * width exactly: a tiny share gets a 3 dp sliver (taken from the big ones), never an overflow.
 */
@Composable
fun StackBar(parts: List<Pair<Color, Long>>, modifier: Modifier = Modifier) {
    val shown = parts.filter { it.second > 0 }
    val total = shown.sumOf { it.second }.coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().height(Size.stackBar)) {
        if (shown.isEmpty()) {
            drawRoundRect(Color.Gray.copy(alpha = 0.15f), Offset.Zero, size, CornerRadius(size.height / 2, size.height / 2))
            return@Canvas
        }
        val gap = 3.dp.toPx()
        val usable = (size.width - gap * (shown.size - 1)).coerceAtLeast(0f)
        val widths = stackWidths(shown.map { it.second }, usable, minOf(3.dp.toPx(), usable / shown.size))
        var x = 0f
        shown.forEachIndexed { i, (color, _) ->
            val w = widths[i]
            val r = minOf(w, size.height) / 2
            drawRoundRect(color, Offset(x, 0f), GSize(w, size.height), CornerRadius(r, r))
            x += w + gap
        }
    }
}

/** Widths for [values] that add up to exactly [usable], each at least [min]. */
internal fun stackWidths(values: List<Long>, usable: Float, min: Float): List<Float> {
    val total = values.sum().toFloat().coerceAtLeast(1f)
    val raw = values.map { usable * it / total }
    val small = raw.count { it < min }
    val borrowed = raw.filter { it < min }.sumOf { (min - it).toDouble() }.toFloat()
    val bigTotal = raw.filter { it >= min }.sum()
    if (small == 0 || bigTotal <= 0f) return raw
    return raw.map { if (it < min) min else it - borrowed * (it / bigTotal) }
}

/** Five tonal steps of primary for the heatmap: none, then four levels (heatLevel). */
@Composable
fun heatColor(level: Int): Color {
    val c = MaterialTheme.colorScheme
    return when (level) {
        0 -> c.surfaceContainer
        1 -> c.primary.copy(alpha = 0.22f).compositeOverSurface(c.surfaceContainerLow)
        2 -> c.primary.copy(alpha = 0.42f).compositeOverSurface(c.surfaceContainerLow)
        3 -> c.primary.copy(alpha = 0.68f).compositeOverSurface(c.surfaceContainerLow)
        else -> c.primary
    }
}

/** A ring split by category (Top category widget): arcs in each category's colour, gaps sized to the caps. */
@Composable
fun DonutChart(parts: List<Pair<Color, Long>>, size: Dp = Size.ringWidget, stroke: Dp = 6.dp) {
    val shown = parts.filter { it.second > 0 }
    val total = shown.sumOf { it.second }.coerceAtLeast(1)
    Canvas(Modifier.size(size)) {
        val s = stroke.toPx()
        val tl = Offset(s / 2, s / 2)
        val arc = GSize(this.size.width - s, this.size.height - s)
        if (shown.size <= 1) {
            drawArc(shown.firstOrNull()?.first ?: Color.Gray.copy(alpha = 0.2f), -90f, 360f, false, tl, arc, style = Stroke(s))
            return@Canvas
        }
        val gap = ringGapDegrees(s, arc.width / 2, 2.dp.toPx())
        var start = -90f
        shown.forEach { (color, v) ->
            val sweep = 360f * v / total
            // A share smaller than the gap still shows as a dot.
            drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.01f), false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
            start += sweep
        }
    }
}

/** Two numbers as one split bar (Money in and out): [part] of [whole] in primary, the rest tonal. */
@Composable
fun SplitBar(part: Long, whole: Long, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val f = if (whole <= 0) 0f else (part.toFloat() / whole).coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(Size.stackBar)) {
        val gap = 4.dp.toPx()
        val h = size.height
        val w1 = when { f <= 0f -> 0f; f >= 1f -> size.width; else -> ((size.width - gap) * f).coerceAtLeast(h) }
        if (w1 > 0) drawRoundRect(c.primary, Offset.Zero, GSize(w1, h), CornerRadius(h / 2, h / 2))
        val x2 = if (w1 > 0) w1 + gap else 0f
        if (f < 1f && x2 < size.width - h / 2) drawRoundRect(c.secondaryContainer, Offset(x2, 0f), GSize(size.width - x2, h), CornerRadius(h / 2, h / 2))
    }
}
