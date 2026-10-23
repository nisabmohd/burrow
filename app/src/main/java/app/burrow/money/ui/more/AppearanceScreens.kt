package app.burrow.money.ui.more

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.burrow.money.R
import app.burrow.money.data.Settings
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ButtonGroup
import app.burrow.money.ui.components.Chevron
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.plan.DetailFrame
import app.burrow.money.ui.theme.AccentSeeds
import app.burrow.money.ui.theme.BodyFont
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.TextSize
import app.burrow.money.ui.theme.ThemeMode
import app.burrow.money.ui.theme.family
import app.burrow.money.ui.theme.isDark
import app.burrow.money.ui.theme.seedColorScheme

/** What the appearance screens change. */
interface AppearanceActions {
    fun mode(m: ThemeMode)
    fun accent(hue: Float)
    fun dynamic(on: Boolean)
    fun font(f: BodyFont)
    fun size(s: TextSize)
}

/** Appearance (63): theme, colour swatches, dynamic colour (Android 12+), font and language rows. */
@Composable
fun AppearanceScreen(settings: Settings, actions: AppearanceActions, onBack: () -> Unit, onFont: () -> Unit, onLanguage: () -> Unit) {
    val t = settings.theme
    DetailFrame(onBack, stringResource(R.string.appearance)) {
        SectionHeader(stringResource(R.string.theme))
        ButtonGroup(listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK), t.mode, { themeLabel(it) }, actions::mode)
        SectionHeader(stringResource(R.string.colour))
        val dark = t.isDark()
        // Seven equal columns: every swatch fits the card on a narrow phone, none is cut off.
        app.burrow.money.ui.components.SwatchGrid(AccentSeeds, columns = AccentSeeds.size) { (hue, _) ->
            val on = !t.dynamicColor && hue == t.accentHue
            app.burrow.money.ui.components.Swatch(on, accentName(hue, false), { actions.accent(hue) }) {
                val swatch = seedColorScheme(hue, dark)
                Box(Modifier.fillMaxSize().padding(Space.group).clip(Shapes.pill).background(swatch.primary), contentAlignment = Alignment.Center) {
                    if (on) Icon(painterResource(R.drawable.ic_check_regular), null, tint = swatch.onPrimary, modifier = Modifier.size(Size.iconSmall))
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListGroup(Modifier.padding(top = Space.block)) {
                ListRow(
                    stringResource(R.string.dynamic_colour), 0, 1, supporting = stringResource(R.string.dynamic_colour_line),
                    lead = { IconCircle(painterResource(R.drawable.ic_wallpaper_duotone)) },
                    trailing = { BurrowSwitch(t.dynamicColor, actions::dynamic) },
                )
            }
        }
        // Language lives in More › Customise › Language and region: one place for each setting.
        SectionHeader(stringResource(R.string.font))
        ListGroup {
            ListRow(stringResource(R.string.font_and_size), 0, 1, supporting = stringResource(R.string.dot_join, fontName(t.font), textSizeName(t.textSize)), lead = { IconCircle(painterResource(R.drawable.ic_text_fields_duotone)) }, trailing = { Chevron() }, onClick = onFont)
        }
    }
}

/** Font and text size (64): a preview, the fonts (each drawn in itself), Small / Default / Large. */
@Composable
fun FontScreen(settings: Settings, actions: AppearanceActions, onBack: () -> Unit) {
    val t = settings.theme
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    DetailFrame(onBack, stringResource(R.string.font_and_size)) {
        Column(Modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainerLow).padding(Space.card)) {
            Text(stringResource(R.string.preview), style = BurrowType.label, color = c.onSurfaceVariant)
            Text(stringResource(R.string.preview_title, money.plain(money.digitsUnitValue() * 120_000)), style = BurrowType.title, color = c.onSurface)
            Text(stringResource(R.string.preview_line), style = BurrowType.body, color = c.onSurfaceVariant)
        }
        SectionHeader(stringResource(R.string.font))
        val fonts = listOf(
            BodyFont.LEXEND to R.string.font_lexend_line,
            BodyFont.OUTFIT to R.string.font_outfit_line,
            BodyFont.ATKINSON to R.string.font_atkinson_line,
            BodyFont.SPACE_GROTESK to R.string.font_space_grotesk_line,
            BodyFont.SYSTEM to R.string.font_system_line,
        )
        ListGroup {
            fonts.forEachIndexed { i, (f, line) ->
                val on = f == t.font
                ListRow(
                    fontName(f), i, fonts.size, supporting = stringResource(line), selected = on,
                    lead = {
                        // Font previews are the one place a font is set by hand (TYPOGRAPHY exception).
                        Box(Modifier.size(Size.iconCircle).clip(Shapes.pill).background(c.surfaceContainer), contentAlignment = Alignment.Center) {
                            Text("Aa", style = BurrowType.titleSmall.copy(fontFamily = f.family()), color = c.onSurfaceVariant)
                        }
                    },
                    trailing = { if (on) Icon(painterResource(R.drawable.ic_check_regular), null, tint = c.primary, modifier = Modifier.size(Size.icon)) },
                    onClick = { actions.font(f) },
                )
            }
        }
        SectionHeader(stringResource(R.string.text_size))
        ButtonGroup(TextSize.entries, t.textSize, { textSizeName(it) }, actions::size)
    }
}

private fun app.burrow.money.domain.MoneyFormat.digitsUnitValue(): Long {
    var u = 1L
    repeat(digits) { u *= 10 }
    return u
}

/** Language (65): the phone's, or one Burrow ships. Applies at once. */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val current = AppLanguage.current(context)
    val system = AppLanguage.systemLocale(context)
    DetailFrame(onBack, stringResource(R.string.language)) {
        Text(stringResource(R.string.language_line), style = BurrowType.body, color = c.onSurfaceVariant, modifier = Modifier.padding(start = Space.grid, bottom = Space.block))
        val rows = listOf<Triple<String?, String, String>>(Triple(null, stringResource(R.string.system_default), system.getDisplayName(system))) +
            AppLanguage.LANGUAGES.map { Triple(it.tag, it.nativeName, it.englishName) }
        ListGroup {
            rows.forEachIndexed { i, (tag, name, line) ->
                val on = tag == current
                ListRow(
                    name, i, rows.size, supporting = line, selected = on,
                    lead = {
                        if (tag == null) IconCircle(painterResource(R.drawable.ic_smartphone_duotone))
                        else Box(Modifier.size(Size.iconCircle).clip(Shapes.pill).background(c.surfaceContainer), contentAlignment = Alignment.Center) {
                            Text(name.take(1), style = BurrowType.button, color = c.onSurfaceVariant)
                        }
                    },
                    trailing = { if (on) Icon(painterResource(R.drawable.ic_check_regular), null, tint = c.primary, modifier = Modifier.size(Size.icon)) },
                    onClick = { (context as? Activity)?.let { AppLanguage.set(it, tag) } },
                )
            }
        }
    }
}
