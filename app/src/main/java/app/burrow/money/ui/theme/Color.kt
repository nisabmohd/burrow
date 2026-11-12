package app.burrow.money.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Burrow colour, exactly as the prototype draws it (design/tokens.json, docs/TOKENS.md).
 * Everything comes from one seed hue; the default is Rose, 345.
 *
 * Roles (never use a colour outside its role):
 * - background: the page.
 * - surfaceContainerLow ("c1"): cards, list rows, widgets, tiles.
 * - surfaceContainer ("c2"): search bar, keypad keys, icon buttons, neutral icon circles, chart tracks.
 * - surfaceContainerHigh ("c3"): pressed states, dividers inside charts.
 * - primary: the one strong action per screen, selected nav icon, chart highlight.
 * - primaryContainer: Today's hero only, summary icon circles, the welcome panel.
 * - secondaryContainer: selected chip / row / tab pill, info notes.
 * - error / errorContainer: overdue, over budget, failures. Nothing else is red.
 * - warning container (Burrow extra): paused / needs attention notes.
 * Amounts are never coloured, except "₹710 over" in red.
 */
private fun tone(hue: Float, s: Int, l: Float): Color = Color.hsl(hue, s / 100f, l / 100f)

fun seedColorScheme(hue: Float, dark: Boolean): ColorScheme = if (dark) {
    val bg = tone(hue, 12, 6.5f)
    val text = tone(hue, 15, 91f)
    darkColorScheme(
        primary = tone(hue, 86, 82f),
        onPrimary = tone(hue, 60, 17f),
        primaryContainer = tone(hue, 40, 27f),
        onPrimaryContainer = tone(hue, 90, 92f),
        secondary = tone(hue, 86, 82f),
        onSecondary = tone(hue, 60, 17f),
        secondaryContainer = tone(hue, 14, 25f),
        onSecondaryContainer = tone(hue, 40, 90f),
        background = bg,
        onBackground = text,
        surface = bg,
        onSurface = text,
        surfaceVariant = tone(hue, 9, 15f),
        onSurfaceVariant = tone(hue, 9, 71f),
        surfaceContainerLowest = bg,
        surfaceContainerLow = tone(hue, 10, 11f),
        surfaceContainer = tone(hue, 9, 15f),
        surfaceContainerHigh = tone(hue, 8, 20f),
        surfaceContainerHighest = tone(hue, 8, 24f),
        outline = tone(hue, 6, 34f),
        outlineVariant = tone(hue, 8, 20f),
        error = tone(356f, 90, 78f),
        errorContainer = tone(356f, 42, 27f),
        onErrorContainer = tone(356f, 90, 92f),
        scrim = Color(0x99000000),
    )
} else {
    val bg = tone(hue, 45, 98.4f)
    val text = tone(hue, 14, 11f)
    lightColorScheme(
        primary = tone(hue, 55, 40f),
        onPrimary = Color.White,
        primaryContainer = tone(hue, 88, 91f),
        onPrimaryContainer = tone(hue, 62, 19f),
        secondary = tone(hue, 55, 40f),
        onSecondary = Color.White,
        secondaryContainer = tone(hue, 34, 87f),
        onSecondaryContainer = tone(hue, 30, 17f),
        background = bg,
        onBackground = text,
        surface = bg,
        onSurface = text,
        surfaceVariant = tone(hue, 24, 91.5f),
        onSurfaceVariant = tone(hue, 7, 35f),
        surfaceContainerLowest = bg,
        surfaceContainerLow = tone(hue, 32, 95f),
        surfaceContainer = tone(hue, 24, 91.5f),
        surfaceContainerHigh = tone(hue, 18, 88f),
        surfaceContainerHighest = tone(hue, 16, 84f),
        outline = tone(hue, 9, 72f),
        outlineVariant = tone(hue, 18, 88f),
        error = tone(356f, 64, 44f),
        errorContainer = tone(356f, 86, 92f),
        onErrorContainer = tone(356f, 60, 22f),
        scrim = Color(0x6B120C0E),
    )
}

/** The warning pair Material lacks (amber; for "Auto-capture is paused"). */
@Immutable
data class WarningColors(val container: Color, val onContainer: Color)

fun warningColors(dark: Boolean) = if (dark) {
    WarningColors(tone(36f, 34, 24f), tone(38f, 88, 87f))
} else {
    WarningColors(tone(38f, 88, 87f), tone(30f, 62, 20f))
}

/**
 * Fixed colours of one category, from its hue. They follow light/dark only, never the seed or
 * dynamic colour, so Food is always orange.
 * container + onContainer: the 40 dp icon circle. solid: chart bars, rings, the colour swatch.
 * track: the empty part of a ring or bar.
 */
@Immutable
data class CategoryColors(val container: Color, val onContainer: Color, val solid: Color, val track: Color)

fun categoryColors(hue: Float, dark: Boolean) = if (dark) {
    CategoryColors(tone(hue, 30, 24f), tone(hue, 72, 84f), tone(hue, 62, 70f), tone(hue, 40, 26f))
} else {
    CategoryColors(tone(hue, 72, 90f), tone(hue, 62, 27f), tone(hue, 56, 48f), tone(hue, 40, 88f))
}

/**
 * Behind a row swiped to a success (Mark paid, Add now): a solid green and the text on it (like
 * Sprout's swipe). The only green in the app besides the Green category; never on amounts.
 */
@Immutable
data class SwipeColors(val background: Color, val content: Color)

fun successSwipeColors(dark: Boolean) =
    if (dark) SwipeColors(tone(CategoryHues.GREEN, 42, 40f), tone(CategoryHues.GREEN, 60, 94f))
    else SwipeColors(tone(CategoryHues.GREEN, 48, 38f), Color.White)

/** The 12 category hues, in picker order. */
object CategoryHues {
    const val LAVENDER = 275f
    const val INDIGO = 245f
    const val BLUE = 215f
    const val CYAN = 192f
    const val TEAL = 168f
    const val GREEN = 140f
    const val LIME = 95f
    const val MUSTARD = 50f
    const val ORANGE = 28f
    const val CORAL = 10f
    const val ROSE = 345f
    const val PINK = 318f
    val all = listOf(LAVENDER, INDIGO, BLUE, CYAN, TEAL, GREEN, LIME, MUSTARD, ORANGE, CORAL, ROSE, PINK)
}

/** Accent swatches offered in Appearance. */
val AccentSeeds = listOf(345f to "Rose", 12f to "Rust", 38f to "Amber", 150f to "Green", 192f to "Teal", 215f to "Blue", 275f to "Purple")

/**
 * The empty state's ghost cards (COMPONENTS § Empty state). Light: both cards surfaceContainerLowest,
 * the back one at 70%. Dark (user, 9 Nov): one tone above the page so the cards show, not just the
 * bars — front surfaceContainer, back surfaceContainerLow, no opacity, a hairline (~24% lightness),
 * a deeper shadow, bars at ~27% (front) and ~21% (back).
 */
@Immutable
data class GhostColors(
    val front: Color, val back: Color, val backAlpha: Float, val line: Color?,
    val barFront: Color, val barBack: Color, val shadowAlpha: Float,
)

fun ghostColors(c: androidx.compose.material3.ColorScheme, dark: Boolean) = if (dark) {
    GhostColors(
        front = c.surfaceContainer, back = c.surfaceContainerLow, backAlpha = 1f, line = c.surfaceContainerHighest,
        barFront = androidx.compose.ui.graphics.lerp(c.surfaceContainerHighest, c.outline, 0.3f),
        barBack = androidx.compose.ui.graphics.lerp(c.surfaceContainerHigh, c.surfaceContainerHighest, 0.25f),
        shadowAlpha = 0.4f,
    )
} else {
    GhostColors(c.surfaceContainerLowest, c.surfaceContainerLowest, 0.7f, null, c.surfaceContainerHighest, c.surfaceContainerHighest, 0.12f)
}

/**
 * Every see-through colour in the app has one of these names (as sizes live in Dimens.kt): the same
 * meaning is always the same strength, light and dark. `ProjectRulesTest` keeps raw alphas out of screens.
 */
object Alpha {
    /** The fill under Insights' running-total line. */
    const val area = 0.12f
    /** A soft primary: chart bars not picked, the hero wave's track, the first heatmap step. */
    const val soft = 0.22f
    /** The second and third heatmap steps (the fourth is primary itself). */
    const val heat2 = 0.42f
    const val heat3 = 0.68f
    /** The track of a ring that has gone over (error at this strength). */
    const val overTrack = 0.25f
    /** Days that haven't come yet in a calendar or the heatmap. */
    const val future = 0.5f
    /** The line under the big number on a coloured header (Today's hero, CenterHeader on a tint). */
    const val onTintLine = 0.85f
    /** Material's disabled container and content (an "off" button, the keypad's ✓ before an amount). */
    const val disabledContainer = 0.10f
    const val disabledContent = 0.38f
}

/** Opaque for an alpha mask (the time wheel's fade); never drawn as a colour. */
val MaskSolid = Color.Black
