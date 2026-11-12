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
 * A bank message with highlights behind the parts a rule read (Review, Rule, Teach): soft 6 dp-cornered marks a step
 * shorter than the line, so rows of marks never touch (the design's <mark>, 44 / 52); a marked word's text turns dark
 * (onPrimaryContainer / onSecondaryContainer); a ringed choice (Fix it, 239) stays a pill outline.
 * With [onTap], tapping reports the character offset that was tapped (Teach).
 */
@Composable
fun MessageText(
    text: String, marks: List<Mark>, modifier: Modifier = Modifier, onTap: ((Int) -> Unit)? = null, compact: Boolean = false,
    /** A CheckCard shows two lines until tapped (44, 243): the rest ends in "…" and its marks aren't drawn. */
    maxLines: Int = Int.MAX_VALUE,
    /** The text's own colour: onSurface on a page, muted onSurfaceVariant in a CheckCard (44). */
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val c = MaterialTheme.colorScheme
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val style = if (compact) BurrowType.message else BurrowType.bodyLarge
    val annotated = remember(text, marks, c) {
        androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            marks.forEach { m ->
                if (m.range.isEmpty() || m.range.last >= text.length) return@forEach
                val fg = when (m.style) { MarkStyle.STRONG -> c.onPrimaryContainer; MarkStyle.SOFT -> c.onSecondaryContainer; MarkStyle.RING -> null }
                if (fg != null) addStyle(androidx.compose.ui.text.SpanStyle(color = fg), m.range.first, m.range.last + 1)
            }
        }
    }
    Text(
        annotated,
        style = style,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        color = color,
        onTextLayout = { layout = it },
        modifier = modifier
            .then(
                if (onTap != null) Modifier.pointerInput(text) {
                    detectTapGestures { pos -> layout?.let { onTap(it.getOffsetForPosition(pos)) } }
                } else Modifier,
            )
            .drawBehind {
                val l = layout ?: return@drawBehind
                val padX = app.burrow.money.ui.theme.Size.markPad.toPx()
                // The design's mark is about 1.3 × the font size tall (18 on 14 / 22), centred on its line.
                val markH = style.fontSize.toPx() * MARK_HEIGHT
                val corner = app.burrow.money.ui.theme.Size.markCorner.toPx()
                // With a line limit, only what is on screen is marked (the rest is cut at "…").
                val shownEnd = if (l.lineCount == 0) 0 else l.getLineEnd(l.lineCount - 1, visibleEnd = true)
                marks.forEach { mark ->
                    if (mark.range.isEmpty() || mark.range.last >= text.length || mark.range.first >= shownEnd) return@forEach
                    val m = if (mark.range.last < shownEnd) mark else mark.copy(range = mark.range.first..(shownEnd - 1))
                    val first = l.getLineForOffset(m.range.first)
                    val last = l.getLineForOffset(m.range.last)
                    for (line in first..last) {
                        val start = if (line == first) m.range.first else l.getLineStart(line)
                        val end = if (line == last) m.range.last + 1 else l.getLineEnd(line, visibleEnd = true)
                        val left = l.getHorizontalPosition(start, true) - padX
                        val right = l.getHorizontalPosition(end, true) + padX
                        val lineTop = l.getLineTop(line)
                        val lineH = l.getLineBottom(line) - lineTop
                        val h = minOf(markH, lineH)
                        val top = lineTop + (lineH - h) / 2
                        when (m.style) {
                            MarkStyle.STRONG -> drawRoundRect(c.primaryContainer, Offset(left, top), GSize(right - left, h), CornerRadius(corner, corner))
                            MarkStyle.SOFT -> drawRoundRect(c.secondaryContainer, Offset(left, top), GSize(right - left, h), CornerRadius(corner, corner))
                            MarkStyle.RING -> drawRoundRect(c.primary, Offset(left, top), GSize(right - left, h), CornerRadius(h / 2, h / 2), style = Stroke(app.burrow.money.ui.theme.Size.markRing.toPx()))
                        }
                    }
                }
            },
    )
}

private const val MARK_HEIGHT = 1.3f

/**
 * The grey box a message sits in: a small header (sms icon, sender or bank, time) and the text.
 * [compact] in a sheet (Review 234, What was it for? 241): `body`; on its own page (Check a message, Rule): `bodyLarge`.
 */
@Composable
fun MessageBox(header: String?, text: String, marks: List<Mark>, modifier: Modifier = Modifier, onTap: ((Int) -> Unit)? = null, compact: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainer).padding(horizontal = Space.card, vertical = Space.block),
        verticalArrangement = Arrangement.spacedBy(Space.group),
    ) {
        // No header when the card above already says who sent it (Not understood, 116).
        if (header != null) Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            Icon(painterResource(R.drawable.ic_sms_duotone), null, tint = c.onSurfaceVariant, modifier = Modifier.size(Size.iconSmall))
            Text(header, style = BurrowType.label, color = c.onSurfaceVariant)
        }
        MessageText(text, marks, onTap = onTap, compact = compact)
    }
}

/** Marks for a parsed message: the amount strong, payee and account soft. */
fun marksFor(spans: Map<app.burrow.money.capture.Part, IntRange>): List<Mark> = spans.map { (part, range) ->
    Mark(range, if (part == app.burrow.money.capture.Part.AMOUNT) MarkStyle.STRONG else MarkStyle.SOFT)
}
