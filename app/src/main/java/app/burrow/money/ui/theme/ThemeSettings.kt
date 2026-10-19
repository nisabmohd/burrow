package app.burrow.money.ui.theme

import androidx.compose.runtime.Immutable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The fonts offered in More › Font and text size. Each bundled font costs APK size: ship only these. */
enum class BodyFont { LEXEND, OUTFIT, ATKINSON, SPACE_GROTESK, SYSTEM }

enum class TextSize(val scale: Float) { SMALL(0.92f), DEFAULT(1f), LARGE(1.12f) }

/** Everything the theme needs from user settings (stored in DataStore). */
@Immutable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    /** Off by default: Burrow's own Rose look. Opt-in on Android 12+. */
    val dynamicColor: Boolean = false,
    val accentHue: Float = DEFAULT_ACCENT_HUE,
    val font: BodyFont = BodyFont.LEXEND,
    val textSize: TextSize = TextSize.DEFAULT,
)

/** Rose. */
const val DEFAULT_ACCENT_HUE = 345f
