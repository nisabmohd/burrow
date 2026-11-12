package app.burrow.money.ui.theme

import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The tokens are the design (Weft's constants approach): the spacing scale stays on the 4 dp grid, and
 * every size docs/TOKENS.md names equals the one in Dimens.kt, so the doc and the app can't drift apart
 * (TOKENS said keypad key 60 while the app and PNG 17 are 52 until 12 Nov).
 */
class TokensTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val tokens = File(root, "docs/TOKENS.md").readText()
    private val dimens = File(root, "app/src/main/java/app/burrow/money/ui/theme/Dimens.kt").readText()

    /** Gaps measured off a PNG that sit off the grid, with where they were measured. */
    private val offGrid = mapOf(
        "keypad" to "6 dp between keypad keys, PNG 17 (measured 12 Nov)",
    )

    @Test fun everySpaceIsOnTheGrid() {
        val space = Regex("""object Space \{(.*?)\n\}""", RegexOption.DOT_MATCHES_ALL).find(dimens)!!.groupValues[1]
        val values = Regex("""val (\w+) = ([\d.]+)\.dp""").findAll(space).map { it.groupValues[1] to it.groupValues[2].toFloat() }.toList()
        assertTrue("no Space tokens read", values.size > 10)
        val bad = values.filter { (name, v) -> name !in offGrid && !(v == 2f || v % 4f == 0f) }
        if (bad.isNotEmpty()) fail("Space off the 4 dp grid (2 or a multiple of 4), or add it to offGrid with the PNG it comes from: $bad")
    }

    @Test fun spaceTableMatchesDimens() {
        val rows = mapOf(
            "Space.screen" to Space.screen, "Space.block" to Space.block, "Space.row" to Space.row,
            "Space.card" to Space.card, "Space.sheet" to Space.sheet, "Space.headingInset" to Space.headingInset,
        )
        rows.forEach { (name, value) ->
            val doc = Regex("""\| `$name` \| (\d+) \|""").find(tokens)?.groupValues?.get(1) ?: fail("TOKENS.md has no row for $name")
            assertEquals("TOKENS.md $name", value, (doc as String).toInt().dp)
        }
        val (top, bottom) = Regex("""`Space\.sectionTop` / `sectionBottom` \| (\d+) / (\d+)""").find(tokens)!!.destructured
        assertEquals(Space.sectionTop, top.toInt().dp)
        assertEquals(Space.sectionBottom, bottom.toInt().dp)
    }

    @Test fun sizesLineMatchesDimens() {
        val line = tokens.substringAfter("## Sizes").substringBefore("\n\n").replace(Regex("""\s+"""), " ")
        fun doc(pattern: String): Dp = Regex(pattern).find(line)?.groupValues?.get(1)?.toFloat()?.dp ?: fail("TOKENS.md § Sizes: no \"$pattern\"").let { 0.dp }
        mapOf(
            """Row (\d+)""" to Size.row, """\((\d+) one-line\)""" to Size.rowOneLine,
            """icon circle (\d+)""" to Size.iconCircle, """\(big (\d+)\)""" to Size.iconCircleBig,
            """· icon (\d+)""" to Size.icon, """\((\d+) in circles\)""" to Size.iconInCircle,
            """button (\d+) \(""" to Size.button, """\((\d+) at the bottom""" to Size.buttonBottom,
            """icon button (\d+)""" to Size.iconButton, """chip (\d+)""" to Size.chip, """keypad key (\d+)""" to Size.keypadKey,
            """top bar (\d+)""" to Size.topBar, """bottom nav (\d+)""" to Size.nav, """search (\d+)""" to Size.search,
            """note (\d+)""" to Size.note, """half widget (\d+)""" to Size.widgetHalf, """heatmap day (\d+)""" to Size.heatmapDay,
            """chart bar (\d+) wide""" to Size.chartBar, """chart (\d+) tall""" to Size.chartHeight,
        ).forEach { (pattern, value) -> assertEquals("TOKENS.md § Sizes \"$pattern\"", value, doc(pattern)) }
    }

    @Test fun chartLineMatchesDimens() {
        val line = tokens.substringAfter("**Chart**").substringBefore("\n\n").replace(Regex("""\s+"""), " ")
        mapOf(
            """bars ([\d.]+) apart""" to Chart.barGap, """\(([\d.]+) past 14 bars\)""" to Chart.barGapDense,
            """dot ([\d.]+)""" to Chart.emptyDot, """average line ([\d.]+)""" to Chart.average,
            """rings ([\d.]+) \(""" to Chart.ring, """\(([\d.]+) in rows""" to Chart.ringRow, """([\d.]+) in widgets""" to Chart.ringWidget,
            """([\d.]+) wide\)""" to Chart.ringWide, """with a ([\d.]+) gap, donut""" to Chart.ringGap, """donut ([\d.]+)""" to Chart.donut,
            """donut [\d.]+ with a ([\d.]+) gap""" to Chart.donutGap, """hero wave ([\d.]+)""" to Chart.wavy,
            """running total ([\d.]+)""" to Chart.line, """parts ([\d.]+) apart""" to Chart.stackGap, """split bar ([\d.]+)""" to Chart.splitGap,
        ).forEach { (pattern, value) ->
            val v = Regex(pattern).find(line)?.groupValues?.get(1) ?: fail("TOKENS.md **Chart**: no \"$pattern\"")
            assertEquals("TOKENS.md **Chart** \"$pattern\"", value, (v as String).toFloat().dp)
        }
    }

    /** Touch targets are never under 48 dp (TOKENS § Sizes). */
    @Test fun tapTargetsAreAtLeast48() {
        listOf(Size.button, Size.buttonBottom, Size.iconButton, Size.keypadKey, Size.wheelRow, Size.pinKey, Size.fab).forEach {
            assertTrue("$it is under 48 dp", it >= 48.dp)
        }
    }

    @Test fun theOneShapeRule() {
        val d = Density(1f)
        fun corner(s: androidx.compose.foundation.shape.RoundedCornerShape, side: Float = 400f) = s.topStart.toPx(GSize(side, side), d)
        assertEquals(24f, corner(Shapes.surface))
        assertEquals(24f, Shapes.sheet.topStart.toPx(GSize(400f, 400f), d))
        assertEquals(0f, Shapes.sheet.bottomStart.toPx(GSize(400f, 400f), d))
        // A group: 24 outside, 6 between its rows.
        val first = Shapes.grouped(0, 3)
        assertEquals(24f, corner(first)); assertEquals(6f, first.bottomStart.toPx(GSize(400f, 400f), d))
        val middle = Shapes.grouped(1, 3)
        assertEquals(6f, corner(middle)); assertEquals(6f, middle.bottomStart.toPx(GSize(400f, 400f), d))
        // The logo tile: the app icon's 112/512 corner (PNG 124: ~24 on 112).
        assertEquals(24.5f, corner(Shapes.logo(112.dp), 112f), 0.01f)
        // Outlines: a chip 1, a ring 2 (round check, today, picked swatch, PIN dot).
        assertEquals(1.dp, Size.outline)
        assertEquals(2.dp, Size.ring)
    }

    @Test fun alphaLevelsAreOrdered() {
        // Stronger meaning, stronger colour: the heatmap steps rise, disabled content shows more than its container.
        assertTrue(Alpha.area < Alpha.soft && Alpha.soft < Alpha.heat2 && Alpha.heat2 < Alpha.heat3 && Alpha.heat3 < 1f)
        assertTrue(Alpha.disabledContainer < Alpha.disabledContent)
        assertEquals("Material's disabled content", 0.38f, Alpha.disabledContent)
    }
}
