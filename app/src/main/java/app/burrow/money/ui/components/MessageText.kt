package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.burrow.money.R
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/** How a part of a message is marked: the amount strongly, other parts softly, the part being taught with a ring. */
enum class MarkStyle { STRONG, SOFT, RING }

@Immutable
data class Mark(val range: IntRange, val style: MarkStyle)

/**
 * A bank message with pill highlights behind the parts a rule read (Review, Rule, Teach).
 * With [onTap], tapping reports the character offset that was tapped (Teach).
 */
@Composable
fun MessageText(text: String, marks: List<Mark>, modifier: Modifier = Modifier, onTap: ((Int) -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = BurrowType.bodyLarge,
        color = c.onSurface,
        onTextLayout = { layout = it },
        modifier = modifier
            .then(
                if (onTap != null) Modifier.pointerInput(text) {
                    detectTapGestures { pos -> layout?.let { onTap(it.getOffsetForPosition(pos)) } }
                } else Modifier,
            )
            .drawBehind {
                val l = layout ?: return@drawBehind
                val padX = 4.dp.toPx()
                marks.forEach { m ->
                    if (m.range.isEmpty() || m.range.last >= text.length) return@forEach
                    val first = l.getLineForOffset(m.range.first)
                    val last = l.getLineForOffset(m.range.last)
                    for (line in first..last) {
                        val start = if (line == first) m.range.first else l.getLineStart(line)
                        val end = if (line == last) m.range.last + 1 else l.getLineEnd(line, visibleEnd = true)
                        val left = l.getHorizontalPosition(start, true) - padX
                        val right = l.getHorizontalPosition(end, true) + padX
                        val top = l.getLineTop(line)
                        val h = l.getLineBottom(line) - top
                        val r = CornerRadius(h / 2, h / 2)
                        when (m.style) {
                            MarkStyle.STRONG -> drawRoundRect(c.primaryContainer, Offset(left, top), GSize(right - left, h), r)
                            MarkStyle.SOFT -> drawRoundRect(c.secondaryContainer, Offset(left, top), GSize(right - left, h), r)
                            MarkStyle.RING -> drawRoundRect(c.primary, Offset(left, top), GSize(right - left, h), r, style = Stroke(1.5.dp.toPx()))
                        }
                    }
                }
            },
    )
}

/**
 * The grey box a message sits in: a small header (sms icon, sender or bank, time) and the text.
 */
@Composable
fun MessageBox(header: String, text: String, marks: List<Mark>, modifier: Modifier = Modifier, onTap: ((Int) -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainer).padding(horizontal = Space.card, vertical = Space.block),
        verticalArrangement = Arrangement.spacedBy(Space.group),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            Icon(painterResource(R.drawable.ic_sms_duotone), null, tint = c.onSurfaceVariant, modifier = Modifier.size(Size.iconSmall))
            Text(header, style = BurrowType.label, color = c.onSurfaceVariant)
        }
        MessageText(text, marks, onTap = onTap)
    }
}

/** Marks for a parsed message: the amount strong, payee and account soft. */
fun marksFor(spans: Map<app.burrow.money.capture.Part, IntRange>): List<Mark> = spans.map { (part, range) ->
    Mark(range, if (part == app.burrow.money.capture.Part.AMOUNT) MarkStyle.STRONG else MarkStyle.SOFT)
}
