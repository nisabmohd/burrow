package app.burrow.money.ui.components

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import app.burrow.money.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The one notice component: paused capture, sample data, restricted settings. */
@RunWith(Parameterized::class)
class NoteTest(private val width: Dp, private val fontScale: Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} at {1}x") fun sizes() = SIZES.map { arrayOf<Any>(it.first, it.second) }
    }

    @get:Rule val rule = createComposeRule()
    private val title = "Auto-capture is paused"
    private val line = "Notification access is off"

    @Test fun wrappedTitleNeverRunsIntoTheLine() {
        // 2026-10-24: the title wrapped to two lines and "Notification access is off" sat on top of it.
        rule.show(width, fontScale) { Note(NoteKind.WARN, painterResource(R.drawable.ic_info_regular), title, line = line, action = "Fix it", onAction = {}) }
        val t = rule.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed().assertNotClipped()
        val l = rule.onNodeWithText(line, useUnmergedTree = true).assertIsDisplayed().assertNotClipped()
        assertNoOverlap(t, l)
        assertNoOverlap(t, rule.onNodeWithText("Fix it", useUnmergedTree = true))
        assertNoOverlap(l, rule.onNodeWithText("Fix it", useUnmergedTree = true))
    }

    @Test fun longTextGrowsTheNoteInsteadOfClipping() {
        val long = "Burrow was installed from a file. In App info tap ⋮ › Allow restricted settings, then turn it on here again"
        rule.show(width, fontScale) { Note(NoteKind.WARN, painterResource(R.drawable.ic_info_regular), "Android is blocking access", line = long, action = "App info", onAction = {}) }
        rule.onNodeWithText(long, useUnmergedTree = true).assertNotClipped().assertTextFits()
        rule.onNodeWithText("App info", useUnmergedTree = true).assertIsDisplayed().assertNotClipped()
    }

    @Test fun actionFiresAndANoteWithoutOnClickIsNotTappable() {
        var fixes = 0
        rule.show(width, fontScale) { Note(NoteKind.INFO, painterResource(R.drawable.ic_info_regular), title, line = line, action = "Fix it", onAction = { fixes++ }) }
        rule.onNodeWithText("Fix it").performClick()
        assertEquals(1, fixes)
        rule.onNodeWithText(title).assertHasNoClickAction()
    }

    @Test fun wholeNoteTapsWhenItHasOnClick() {
        var taps = 0
        rule.show(width, fontScale) { Note(NoteKind.INFO, painterResource(R.drawable.ic_info_regular), title, line = line, onClick = { taps++ }) }
        rule.onNodeWithText(title).performClick()
        assertEquals(1, taps)
    }

    @Test fun titleOnlyNoteHasNoEmptyLine() {
        rule.show(width, fontScale) { Note(NoteKind.ERROR, painterResource(R.drawable.ic_info_regular), title) }
        rule.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed().assertNotClipped()
    }
}
