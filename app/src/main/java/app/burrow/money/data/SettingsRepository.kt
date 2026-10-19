package app.burrow.money.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.burrow.money.ui.theme.BodyFont
import app.burrow.money.ui.theme.ThemeMode
import app.burrow.money.ui.theme.ThemeSettings
import app.burrow.money.ui.theme.TextSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Everything the user sets, from DataStore (keys in docs/DATA.md § DataStore keys).
 * Grows as screens need more: main currency, month start, capture switches, app lock…
 */
@Immutable
data class Settings(
    val theme: ThemeSettings = ThemeSettings(),
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store: DataStore<Preferences> = context.dataStore

    val settings: Flow<Settings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[DYNAMIC_COLOR] = enabled }
    suspend fun setAccentHue(hue: Float) = store.edit { it[ACCENT_HUE] = hue }
    suspend fun setFont(font: BodyFont) = store.edit { it[FONT] = font.name }
    suspend fun setTextSize(size: TextSize) = store.edit { it[TEXT_SIZE] = size.name }

    private fun Preferences.toSettings(): Settings {
        val theme = ThemeSettings()
        return Settings(
            theme = ThemeSettings(
                mode = enumOrDefault(this[THEME], theme.mode),
                dynamicColor = this[DYNAMIC_COLOR] ?: theme.dynamicColor,
                accentHue = this[ACCENT_HUE] ?: theme.accentHue,
                font = enumOrDefault(this[FONT], theme.font),
                textSize = enumOrDefault(this[TEXT_SIZE], theme.textSize),
            ),
        )
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamicColor")
        val ACCENT_HUE = floatPreferencesKey("accentHue")
        val FONT = stringPreferencesKey("font")
        val TEXT_SIZE = stringPreferencesKey("textSize")
    }
}

/** A stored enum name, or [default] when it is missing or from an older version. */
private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
