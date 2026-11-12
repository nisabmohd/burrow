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

    /** One place for sizes: change Dimens.kt and the whole app follows (as colours, text and motion). 0.dp is not a size. */
    @Test fun sizesOnlyInDimensKt() = assertNone("raw .dp outside Dimens.kt: add a token to Space / Size / Chart",
        violations { f, _, l ->
            f.path.replace('\\', '/').contains("/ui/theme/").not() &&
                Regex("""(?<![\w.])(?!0\.dp)\d+(\.\d+)?\.dp\b""").containsMatchIn(l) && !l.trim().startsWith("//") && !l.trim().startsWith("*")
        })

    @Test fun noHardCodedUserText() = assertNone("Text(\"…\") with words: use strings.xml",
        violations { _, _, l ->
            Regex("""\bText\(\s*"([^"]*)"""").find(l)?.groupValues?.get(1)
                ?.replace(Regex("""\$\{[^}]*\}|\$[A-Za-z_]+"""), "") // "${'$'}{n}%", "${'$'}badge" are numbers, not words
                ?.let { it != "Aa" && Regex("[A-Za-z]{2,}").containsMatchIn(it) } == true
        })

    @Test fun dialogsOnlyFromDialogsKt() = assertNone("AlertDialog outside Dialogs.kt (choices are bottom sheets)",
        violations { f, _, l -> f.name != "Dialogs.kt" && "AlertDialog(" in l })

    @Test fun noMaterialIconsLibrary() = assertNone("material-icons (use Solar drawables)",
        violations { _, _, l -> "androidx.compose.material.icons" in l })

    @Test fun notificationsUseTheBurrowMark() = assertNone("setSmallIcon not R.drawable.ic_notification",
        violations { _, _, l -> "setSmallIcon(" in l && "ic_notification" !in l })

    /**
     * main and foss strip INTERNET; github (update check + Drive) and play (Drive backup only, user
     * 2026-11-08) put it back. Only code in src/drive and src/github may open a connection.
     */
    @Test fun noInternetOutsideGithubAndDrive() {
        for (flavour in listOf("main", "foss")) {
            val m = File(root, "app/src/$flavour/AndroidManifest.xml")
            if (!m.exists()) continue
            m.readLines().filter { "android.permission.INTERNET" in it }.forEach {
                assertTrue("$flavour manifest may only strip INTERNET: $it", "tools:node=\"remove\"" in it)
            }
        }
        val opens = File(root, "app/src").walkTopDown()
            .filter { it.extension == "kt" && "openConnection(" in it.readText() }
            .map { it.relativeTo(File(root, "app/src")).path }
            .filterNot { it.startsWith("test/") || it.startsWith("drive/") || it.startsWith("main/java/app/burrow/money/data/update/") }
            .toList()
        assertTrue("network calls outside src/drive and the update check: $opens", opens.isEmpty())
    }

    @Test fun noPlusMinusSteppers() = assertNone(
        "− / + stepper buttons (every number is the value sheet: COMPONENTS § Value sheet)",
        violations { f, _, l -> f.name != "Keypad.kt" && "TonalIconButton(" in l && ("R.string.key_minus" in l || "R.string.key_plus" in l) },
    )

    @Test fun pickOneRowsAreNeverTinted() = assertNone(
        "ListRow(selected = …) — pick-one lists use PickRow with the round check (C1)",
        // Teach tints the step being picked: that is "now", not a choice.
        violations { f, _, l -> f.name != "TeachScreen.kt" && f.name != "Customise.kt" && f.name != "Lists.kt" && Regex("""selected = [^,)]+==""").containsMatchIn(l) && "ListRow" in l },
    )

    @Test fun tabsArePillTabs() = assertNone(
        "a tab row built from ButtonGroup or chips (tabs are PillTabs)",
        violations { _, _, l -> "PlanSection.entries" in l && "ButtonGroup" in l || "CustomiseTab.entries" in l && "Chip(" in l },
    )

    // --- consistency (user, 2026-11-09: "make sure this stays consistent") ------------------------

    /** The Cookie is only for a status (a deleted payment, a failed import) and the lock; every empty list is the Ghost. */
    @Test fun emptyListsUseTheGhost() = assertNone(
        "Cookie( outside the status screens — an empty list or no results is EmptyState + Ghost (DESIGN § Empty states)",
        violations { f, _, l -> "Cookie(" in l && f.name !in setOf("Blocks.kt", "TxnScreen.kt", "ImportScreens.kt", "LockScreens.kt") && !l.trim().startsWith("//") && !l.trim().startsWith("*") },
    )

    /** Empty states share one spacing: only the status screens move the picture down. */
    @Test fun emptyStatesShareOneSpacing() = assertNone(
        "EmptyState(top = …) outside the status screens",
        violations { f, i, l -> "top = app.burrow.money.ui.theme.Space.empty" in l && f.name !in setOf("TxnScreen.kt", "ImportScreens.kt") &&
            f.readLines().subList(maxOf(0, i - 2), i).any { "EmptyState(" in it } },
    )

    /** A sheet of chips stays chips: Filter's Type is ChoiceChips, never a ButtonGroup of tall pills. */
    @Test fun filterSheetsAreAllChips() = assertNone(
        "ButtonGroup in a filter sheet (Type, Accounts and Categories are all chips)",
        violations { f, _, l -> f.parentFile.name == "filters" && "ButtonGroup(" in l },
    )

    /** ButtonGroup never falls back to a tall stack of full-width pills; it wraps as ChoiceChips. */
    @Test fun buttonGroupNeverStacks() {
        val src = File(main, "java/app/burrow/money/ui/components/Controls.kt").readText()
        val body = src.substringAfter("fun <T> ButtonGroup(").substringBefore("\n}\n")
        assertTrue("ButtonGroup must wrap as ChoiceChips when it can't fit one row", "ChoiceChips(" in body && "fillMaxWidth()) }" !in body)
    }

    /**
     * A one-line label never wraps out of sight: "Show 1914 transactions" lost "transactions" and sat
     * off-centre (user, 2026-11-09). Every Text(…, maxLines = 1) ends in "…" or shrinks to fit.
     */
    @Test fun oneLineTextNeverHidesWords() = assertNone(
        "Text(maxLines = 1) without overflow / softWrap = false / autoSize",
        violations { _, _, l -> "Text(" in l && "measure(" !in l && Regex("""maxLines = 1\b""").containsMatchIn(l) && !Regex("overflow|softWrap|autoSize").containsMatchIn(l) },
    )

    /** Every button label is one line that shrinks, never wraps (BurrowButton). */
    @Test fun buttonLabelsShrinkNotWrap() {
        val body = File(main, "java/app/burrow/money/ui/components/Controls.kt").readText().substringAfter("fun BurrowButton(").substringBefore("\n}\n")
        assertTrue("BurrowButton's label must be softWrap = false with autoSize", "softWrap = false" in body && "autoSize" in body)
    }

    /** Every widget shows a real picture in the launcher's picker, light and dark (tools/widget_previews.py), never the app icon. */
    @Test fun everyWidgetHasAPreviewImage() {
        val providers = File(main, "res/xml").listFiles().orEmpty().filter { "<appwidget-provider" in it.readText() }
        assertTrue("no widgets found", providers.isNotEmpty())
        val missing = providers.flatMap { f ->
            val name = Regex("""android:previewImage="@drawable/([a-z_]+)"""").find(f.readText())?.groupValues?.get(1)
                ?: return@flatMap listOf("${f.name}: no android:previewImage")
            listOf("drawable-nodpi", "drawable-night-nodpi").filter { !File(main, "res/$it/$name.webp").exists() }.map { "${f.name}: no $it/$name.webp" }
        }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
    }

    /** The Ghost's cards take ghostColors(), so they show in dark mode too (cards, not just bars). */
    @Test fun ghostFollowsDarkMode() {
        val src = File(main, "java/app/burrow/money/ui/components/Blocks.kt").readText()
        assertTrue("Ghost must use ghostColors()", "ghostColors()" in src.substringAfter("fun Ghost(").substringBefore("\n}\n"))
    }

    /** A list on a detail page shows the Ghost when it is empty, never a header over nothing. */
    @Test fun detailListsHaveAnEmptyState() {
        val need = mapOf(
            "ui/plan/PlanDetailScreens.kt" to listOf("s.rows.isEmpty()", "s.txns.isEmpty()", "s.added.isEmpty()"),
            "ui/insights/InsightsScreen.kt" to listOf("s.txns.isEmpty()"),
            "ui/accounts/AccountScreens.kt" to listOf("s.txns.isEmpty()"),
            // Both Review tabs: To review and Not understood (44, 117).
            "ui/review/ReviewScreen.kt" to listOf("items.isEmpty()", "missed.isEmpty()"),
        )
        val missing = need.flatMap { (path, checks) ->
            val t = File(main, "java/app/burrow/money/$path").readText()
            checks.filter { it !in t }.map { "$path: no `$it` empty state" }
        }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
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

    // --- Long data (docs/PERFORMANCE.md): years of payments over many accounts and cards stay fast. ---

    /** Only the CSV export reads every row; screens read the days they show (a quiet card lost its history, 0.4.1). */
    @Test fun noScreenReadsAllHistory() = assertNone("whole-history read outside the CSV export: read a range (between) or one account (forAccount)",
        violations { f, _, l ->
            !l.trim().startsWith("//") && !l.trim().startsWith("*") && (
                (Regex("""\.everything\(\)""").containsMatchIn(l) && f.name != "CsvTransfer.kt") ||
                    (Regex("""latest\(\s*(Int\.MAX_VALUE)?\s*\)""").containsMatchIn(l) && f.name != "MoneyRepository.kt") ||
                    Regex("""\bnext\.stats\b""").containsMatchIn(l)
                )
        })

    /** Every query that lists transactions with names is bounded: a WHERE (range, account, status) or a LIMIT. */
    @Test fun transactionListQueriesAreBounded() {
        val daos = File(main, "java/app/burrow/money/data/Daos.kt").readText()
        val found = Regex("""@Query\(\s*\"\"\"(.*?)\"\"\",?\s*\)\s*fun (\w+)\([^)]*\):\s*Flow<List<TxnView>>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(daos).filter { m -> m.groupValues[1].let { "WHERE" !in it && "LIMIT" !in it } }.map { it.groupValues[2] }.toList()
        assertNone("Flow<List<TxnView>> query with neither WHERE nor LIMIT", found)
    }

    /** A transaction row is drawn inside a lazy list item (Activity, accounts, budgets, category insights, Today). */
    @Test fun transactionRowsAreInLazyLists() {
        val found = kotlin.filter { it.name != "TxnRow.kt" }.flatMap { f ->
            val lines = f.readLines()
            lines.indices.filter { "TxnRow(" in lines[it] && !lines[it].trim().startsWith("//") }.mapNotNull { i ->
                val before = (maxOf(0, i - 16)..i).joinToString("\n") { lines[it] }
                if (Regex("""\b(item|items|itemsIndexed)\(""").containsMatchIn(before)) null else "${rel(f)}:${i + 1}: ${lines[i].trim().take(110)}"
            }
        }
        assertNone("TxnRow outside a LazyColumn item: use LazyDetailFrame / LazyColumn with keys", found)
    }

    /** Lazy items have stable keys, so rows keep their state and skip when the list changes. */
    @Test fun lazyItemsHaveKeys() = assertNone("item/items/itemsIndexed over data without key = (TimeWheel, tabs and static text lists excepted)",
        violations { f, _, l ->
            f.name !in setOf("TimeWheel.kt", "Controls.kt", "SettingsScreens.kt") && !l.trim().startsWith("//") &&
                Regex("""\b(itemsIndexed|items)\(""").containsMatchIn(l) && "key" !in l
        })

    // --- UI consistency (user, 12 Nov: "minor things make a lot of difference"; Weft's constants approach). ---

    private fun code(l: String) = l.substringBefore("//").takeIf { !l.trim().startsWith("*") } ?: ""
    private fun inTheme(f: File) = f.path.replace('\\', '/').contains("/ui/theme/")
    private fun inComponents(f: File) = f.path.replace('\\', '/').contains("/ui/components/")

    /** One strength per meaning: see-through colours are `Alpha.*` in Color.kt, never a number on a screen. */
    @Test fun alphasOnlyInColorKt() = assertNone("raw alpha: add or use a token in Alpha (Color.kt)",
        violations { f, _, l -> !inTheme(f) && Regex("""alpha\s*=\s*\d*\.\d|\.alpha\(\s*\d*\.\d""").containsMatchIn(code(l)) })

    /** Colours come from the scheme by role; Color.Gray and friends ignore the accent and dark mode. */
    @Test fun noNamedColours() = assertNone("named colour (Color.Gray, Color.Black…): use a colour role or a Color.kt token",
        violations { f, _, l -> !inTheme(f) && Regex("""\bColor\.(Black|White|Gray|Grey|DarkGray|LightGray|Red|Green|Blue|Yellow|Cyan|Magenta)\b""").containsMatchIn(code(l)) })

    /** The one-shape rule: shapes are `Shapes.*` (24 dp surfaces, pills, the logo tile, the ghost). */
    @Test fun shapesOnlyInDimensKt() = assertNone("RoundedCornerShape / CutCornerShape outside Dimens.kt: use Shapes.*",
        violations { f, _, l -> !inTheme(f) && Regex("""\b(RoundedCornerShape|CutCornerShape|AbsoluteRoundedCornerShape)\(""").containsMatchIn(code(l)) })

    /** Outlines are one of four widths, by name: an outlined chip, a ring, a focus ring, the ghost's hairline. */
    @Test fun borderWidthsAreNamed() = assertNone(".border( width that isn't Size.outline / ring / focusRing / ghostHairline",
        violations { f, _, l ->
            !inTheme(f) && Regex("""\.border\(\s*(?!Size\.(outline|ring|focusRing|ghostHairline)\b)[^)]""").containsMatchIn(code(l))
        })

    /**
     * Space.* is a gap, Size.* is a size: a spacing token never sizes a box or draws a border, and screens don't do
     * sums on tokens (`Space.sheet * 2`): a gap the design needs gets its own name. Components may measure (layout maths).
     */
    @Test fun spacingTokensAreGapsNotSizes() = assertNone("spacing token used as a size, or token arithmetic on a screen: add a named token",
        violations { f, _, l ->
            val c = code(l)
            !inTheme(f) && (
                Regex("""\.(size|width|height|requiredSize|border)\(\s*Space\.""").containsMatchIn(c) && !Regex("""Spacer\(Modifier\.(height|width|size)\(Space\.\w+\)\)""").containsMatchIn(c) ||
                    !inComponents(f) && Regex("""(Spacer|padding|size|height|width|spacedBy)\([^)]*\b(Space|Size)\.\w+\s*[-+*/]\s*[\w.]""").containsMatchIn(c)
                )
        })

    /** Screens build from the shared components only: Material widgets are wrapped once in ui/components. */
    @Test fun materialWidgetsOnlyInComponents() = assertNone("Material widget on a screen: use the Burrow component (BurrowButton, Chip, BurrowSwitch, BurrowSheet…)",
        violations { f, _, l ->
            !inComponents(f) && !inTheme(f) &&
                Regex("""(?<![\w.])(Button|OutlinedButton|TextButton|FilledTonalButton|ElevatedButton|IconButton|FilledIconButton|FilterChip|AssistChip|SuggestionChip|InputChip|Switch|Checkbox|RadioButton|Slider|Card|ElevatedCard|OutlinedCard|AlertDialog|ModalBottomSheet|HorizontalDivider|Divider|NavigationBar|TopAppBar|CenterAlignedTopAppBar)\(""").containsMatchIn(code(l))
        })

    /** Every Text names its style (BurrowType); the Material default would bring another size and weight. */
    @Test fun everyTextHasAStyle() {
        val found = kotlin.flatMap { f ->
            val t = f.readText()
            Regex("""(?<![\w.])Text\(""").findAll(t).mapNotNull { m ->
                var i = m.range.last + 1; var depth = 1
                while (i < t.length && depth > 0) { if (t[i] == '(') depth++ else if (t[i] == ')') depth--; i++ }
                val call = t.substring(m.range.first, i)
                if ("style" in call) null else "${rel(f)}:${t.substring(0, m.range.first).count { it == '\n' } + 1}: ${call.take(90)}"
            }.toList()
        }
        assertNone("Text( without style =", found)
    }

    /**
     * The one alignment rule (TOKENS): heading text (section labels, field labels; `button`, `label` or `titleSmall`
     * on its own line) starts 24 dp from the edge, i.e. `Space.headingInset` inside the 16 dp content. A heading
     * indented by any other step drifts off that line (the Filter sheet's labels sat at 20, 12 Nov).
     */
    @Test fun headingsStartAtTheHeadingInset() = assertNone("heading Text indented by something other than Space.headingInset",
        violations { f, _, l ->
            val c = code(l)
            !inTheme(f) && Regex("""private fun \w*(Label|Header|Heading)\w*\(""").containsMatchIn(c) &&
                Regex("""padding\(start = Space\.(?!headingInset)\w+""").containsMatchIn(c)
        })

    /**
     * DESIGN rule 4: no buttons inside cards; tap the row or card to open it (kept over the 12 Nov kit's Review
     * CheckCards). A composable named …Card, …Row or …Tile never draws a button of its own.
     */
    @Test fun noButtonsInsideCards() {
        val button = Regex("""(?<![\w.])(BurrowButton|TextButton|LinkButton|TonalIconButton)\(""")
        val found = kotlin.flatMap { f ->
            val t = f.readText()
            Regex("""fun (\w*(Card|Row|Tile))\(""").findAll(t).mapNotNull { m ->
                val open = t.indexOf('{', m.range.last).takeIf { it >= 0 } ?: return@mapNotNull null
                var i = open + 1; var depth = 1
                while (i < t.length && depth > 0) { if (t[i] == '{') depth++ else if (t[i] == '}') depth--; i++ }
                val body = t.substring(open, i)
                if (button.containsMatchIn(body)) "${rel(f)}: ${m.groupValues[1]} draws a button" else null
            }.toList()
        }
        assertNone("button inside a card / row / tile (DESIGN rule 4)", found)
    }
}
