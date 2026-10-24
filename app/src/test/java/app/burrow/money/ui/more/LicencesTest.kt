package app.burrow.money.ui.more

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicencesTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    @Test fun hardWrappedLinesBecomeOneParagraph() {
        val text = "MIT License\n\nPermission is hereby granted, free of charge,\nto any person obtaining a copy\n   of this software.\n\n\nTHE SOFTWARE IS PROVIDED \"AS IS\"."
        assertEquals(
            listOf("MIT License", "Permission is hereby granted, free of charge, to any person obtaining a copy of this software.", "THE SOFTWARE IS PROVIDED \"AS IS\"."),
            licenceParagraphs(text),
        )
    }

    @Test fun windowsLineEndingsAndBlankLinesWithSpaces() {
        assertEquals(listOf("a b", "c"), licenceParagraphs("a\r\nb\r\n  \r\nc\r\n"))
        assertEquals(emptyList<String>(), licenceParagraphs("\n\n  \n"))
    }

    @Test fun everyBundledLicenceTextIsComplete() {
        val raw = File(root, "app/src/main/res/raw")
        val ends = mapOf(
            "licence_gpl3.txt" to "why-not-lgpl.html>.", "licence_apache2.txt" to "limitations under the License.",
            "licence_phosphor_mit.txt" to "SOFTWARE.",
        )
        for ((f, end) in ends) assertTrue("$f is cut short", File(raw, f).readText().trimEnd().endsWith(end))
        raw.listFiles().orEmpty().filter { it.name.startsWith("licence_ofl_") }.forEach {
            val t = it.readText()
            assertTrue("${it.name}: no copyright line", t.startsWith("Copyright"))
            assertTrue("${it.name}: not the whole OFL", "TERMINATION" in t && "DISCLAIMER" in t)
        }
    }

    @Test fun everyFontInTheAppHasALicenceRow() {
        val fonts = File(root, "app/src/main/res/font").listFiles().orEmpty().map { it.nameWithoutExtension }
        val source = File(root, "app/src/main/java/app/burrow/money/ui/more/SettingsScreens.kt").readText()
        for (f in fonts) assertTrue("font $f has no licence_ofl_$f in LICENCES", "R.raw.licence_ofl_$f" in source)
    }

    @Test fun repoHasTheGplLicenceFile() {
        assertTrue(File(root, "LICENSE").readText().contains("GNU GENERAL PUBLIC LICENSE"))
    }
}
