package app.burrow.money.rules

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * CLAUDE.md's hard rules, checked on the source so a whole class of bug can't come back:
 * square tap ripples, raw colours/sizes/animations outside the theme files, broken translations,
 * the wrong notification icon, INTERNET in a build that must not have it.
 */
class ProjectRulesTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val main = File(root, "app/src/main")
    private val kotlin: List<File> = File(main, "java").walkTopDown().filter { it.extension == "kt" }.toList()
    private fun rel(f: File) = f.relativeTo(root).path

    private fun violations(predicate: (File, Int, String) -> Boolean): List<String> =
        kotlin.flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (predicate(f, i, l)) "${rel(f)}:${i + 1}: ${l.trim().take(120)}" else null } }

    private fun assertNone(what: String, found: List<String>) {
        if (found.isNotEmpty()) fail("$what (${found.size}):\n" + found.joinToString("\n"))
    }

    @Test fun everyTapRippleIsClippedToItsShape() {
        // A clickable must sit after a clip(...) in its modifier chain, or draw its ripple itself
        // (indication = null + .indication on the visible shape, as FormChip does).
        val tap = Regex("""\.(clickable|combinedClickable|selectable|toggleable)\(""")
        val found = kotlin.flatMap { f ->
            val lines = f.readLines()
            lines.indices.mapNotNull { i ->
                val m = tap.find(lines[i]) ?: return@mapNotNull null
                if ("indication = null" in lines[i]) return@mapNotNull null
                // The chain this clickable belongs to: the lines before it, with the inner
                // "Modifier"s of `.then(if (x) Modifier.clickable … else Modifier)` and
                // `when { a -> Modifier… }` taken out, so the last "Modifier" left is the chain's root.
                val before = (maxOf(0, i - 10) until i).joinToString("\n") { lines[it] } + "\n" + lines[i].substring(0, m.range.first)
                val outer = before.trimEnd().removeSuffix("Modifier")
                    .replace(Regex("""(->\s*|if\s*\([^()]*\)\s*|else\s+)Modifier"""), "")
                val fromStart = outer.substring(outer.lastIndexOf("Modifier").coerceAtLeast(0))
                if ("clip(" in fromStart) null else "${rel(f)}:${i + 1}: ${lines[i].trim().take(110)}"
            }
        }
        assertNone("clickable without a clip before it: its press ripple is a square", found)
    }

    @Test fun animationsOnlyInMotion() = assertNone("tween( outside Motion.kt",
        violations { f, _, l -> f.name != "Motion.kt" && Regex("""\btween\(""").containsMatchIn(l) && !l.trim().startsWith("//") })

    @Test fun coloursOnlyInColorKt() = assertNone("raw Color(0x…) outside Color.kt",
        violations { f, _, l -> f.name != "Color.kt" && Regex("""Color\(0x""").containsMatchIn(l) })

    @Test fun textSizesOnlyInTypeKt() = assertNone("raw .sp outside Type.kt",
        violations { f, _, l -> f.name != "Type.kt" && Regex("""\d\.sp\b|\d+\.sp\b""").containsMatchIn(l) && !l.trim().startsWith("//") && !l.trim().startsWith("*") })

    @Test fun noHardCodedUserText() = assertNone("Text(\"…\") with words: use strings.xml",
        violations { _, _, l ->
            Regex("""\bText\(\s*"([^"]*)"""").find(l)?.groupValues?.get(1)
                ?.replace(Regex("""\$\{[^}]*\}|\$[A-Za-z_]+"""), "") // "${'$'}{n}%", "${'$'}badge" are numbers, not words
                ?.let { it != "Aa" && Regex("[A-Za-z]{2,}").containsMatchIn(it) } == true
        })

    @Test fun dialogsOnlyFromDialogsKt() = assertNone("AlertDialog outside Dialogs.kt (choices are bottom sheets)",
        violations { f, _, l -> f.name != "Dialogs.kt" && "AlertDialog(" in l })

    @Test fun noMaterialIconsLibrary() = assertNone("material-icons (use Phosphor drawables)",
        violations { _, _, l -> "androidx.compose.material.icons" in l })

    @Test fun notificationsUseTheBurrowMark() = assertNone("setSmallIcon not R.drawable.ic_notification",
        violations { _, _, l -> "setSmallIcon(" in l && "ic_notification" !in l })

    @Test fun noInternetOutsideTheGithubBuild() {
        for (flavour in listOf("main", "play", "foss")) {
            val m = File(root, "app/src/$flavour/AndroidManifest.xml")
            if (!m.exists()) continue
            m.readLines().filter { "android.permission.INTERNET" in it }.forEach {
                assertTrue("$flavour manifest may only strip INTERNET: $it", "tools:node=\"remove\"" in it)
            }
        }
    }

    // --- strings ---------------------------------------------------------------------------------

    private val stringRe = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private fun strings(dir: String): Map<String, String> =
        File(main, "res/$dir/strings.xml").takeIf { it.exists() }?.readText()?.let { t -> stringRe.findAll(t).associate { it.groupValues[1] to it.groupValues[2] } }.orEmpty()
    private val languages = File(main, "res").listFiles().orEmpty().map { it.name }.filter { it.startsWith("values-") && File(main, "res/$it/strings.xml").exists() }.sorted()
    private val english = strings("values")
    private val untranslatable = Regex("""<string name="([^"]+)"[^>]*translatable="false"""").findAll(File(main, "res/values/strings.xml").readText()).map { it.groupValues[1] }.toSet()
    private fun placeholders(s: String) = Regex("""%(\d+\$)?[sdf]""").findAll(s).map { it.value }.sorted().toList()

    @Test fun everyTranslationHasTheSamePlaceholders() {
        // A missing or extra %1$s crashes (or prints garbage) only in that language.
        val bad = languages.flatMap { lang ->
            strings(lang).mapNotNull { (k, v) -> english[k]?.let { en -> if (placeholders(en) != placeholders(v)) "$lang/$k: en=${placeholders(en)} vs ${placeholders(v)}" else null } }
        }
        assertNone("placeholders differ from English", bad)
    }

    @Test fun everyEnglishStringIsTranslated() {
        val bad = languages.flatMap { lang -> val t = strings(lang); english.keys.filter { it !in t && it !in untranslatable }.map { "$lang/$it" } }
        assertNone("missing translations", bad)
    }

    @Test fun noStrayTranslations() {
        val bad = languages.flatMap { lang -> strings(lang).keys.filter { it !in english }.map { "$lang/$it" } }
        assertNone("translation for a string English doesn't have", bad)
    }

    @Test fun apostrophesAreEscaped() {
        val bad = (listOf("values") + languages).flatMap { lang ->
            strings(lang).filter { (_, v) -> Regex("""(?<!\\)'""").containsMatchIn(v) && !v.startsWith("\"") }.map { "$lang/${it.key}" }
        }
        assertNone("unescaped ' (aapt drops the text after it)", bad)
    }
}
