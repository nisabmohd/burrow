package app.burrow.money.ui.more

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** One language Burrow ships: its tag, its name in its own script, and its English name. */
data class Language(val tag: String, val nativeName: String, val englishName: String)

/**
 * The app's own language (65), separate from the phone's. Android 13+ keeps it per app (the same
 * setting as Settings › App languages); older versions keep it here and apply it to each context.
 * No AppCompat needed (as in Sprout).
 */
object AppLanguage {
    /** Keep in step with the values-* folders and `localeFilters` in build.gradle.kts. */
    val LANGUAGES = listOf(
        Language("en", "English", "English"),
        Language("hi", "हिन्दी", "Hindi"),
    )

    private const val PREFS = "app_language"
    private const val KEY = "tag"

    /** The chosen language's tag, or null to follow the phone. */
    fun current(context: Context): String? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.toLanguageTag()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }
        return tag?.let { match(it)?.tag }
    }

    /** The name to show in More: the chosen language, or the phone's. */
    fun currentName(context: Context): String =
        current(context)?.let { t -> LANGUAGES.first { it.tag == t }.nativeName } ?: systemLocale(context).let { it.getDisplayName(it) }

    /** Applies [tag] right away (null = the phone's language) and redraws [activity]. */
    fun set(activity: Activity, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
            apply(activity.applicationContext.resources, tag)
            activity.recreate()
        }
    }

    fun systemLocale(context: Context): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).systemLocales.get(0) ?: Locale.getDefault()
        } else {
            Resources.getSystem().configuration.locales.get(0)
        }

    /** Before Android 13: a context in the saved language, for attachBaseContext. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    @Suppress("DEPRECATION")
    private fun apply(resources: Resources, tag: String?) {
        val locale = tag?.let(Locale::forLanguageTag) ?: Resources.getSystem().configuration.locales.get(0)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration).apply { setLocale(locale) }
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun match(tag: String): Language? {
        val locale = Locale.forLanguageTag(tag)
        return LANGUAGES.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: LANGUAGES.firstOrNull { Locale.forLanguageTag(it.tag).language == locale.language }
    }
}
