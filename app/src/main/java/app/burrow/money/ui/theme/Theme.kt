package app.burrow.money.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ThemeSettings.isDark(): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun BurrowTheme(settings: ThemeSettings = ThemeSettings(), content: @Composable () -> Unit) {
    val dark = settings.isDark()
    val context = LocalContext.current
    val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        else -> remember(settings.accentHue, dark) { seedColorScheme(settings.accentHue, dark) }
    }
    val type = remember(settings.font, settings.textSize) { burrowTypography(settings.font, settings.textSize.scale) }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, typography = type, content = content)
    }
}

/** Category colours follow light/dark only. */
@Composable @ReadOnlyComposable
fun categoryColors(hue: Float): CategoryColors = categoryColors(hue, LocalDarkTheme.current)

@Composable @ReadOnlyComposable
fun warningColors(): WarningColors = warningColors(LocalDarkTheme.current)
