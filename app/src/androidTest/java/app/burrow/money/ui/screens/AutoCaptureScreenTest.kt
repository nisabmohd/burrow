package app.burrow.money.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.burrow.money.R
import app.burrow.money.capture.CaptureStatus
import app.burrow.money.ui.capture.AutoCaptureContent
import app.burrow.money.ui.capture.AutoCaptureState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Auto-capture (48, 145, 146) from a made-up state and made-up Android access. */
class AutoCaptureScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(
        sms: Boolean = false,
        smsAllowed: Boolean = false,
        restricted: Boolean = false,
        smsRefused: Boolean = false,
        notUnderstood: Int = 0,
    ) = rule.showScreen {
        AutoCaptureContent(
            AutoCaptureState(recordedThisMonth = 12, sms = sms, apps = false, smsBanks = emptyList(), notUnderstood = notUnderstood, yourRules = 0),
            CaptureStatus(smsWanted = sms, appsWanted = false, smsAllowed = smsAllowed, appsAllowed = false),
            restricted, smsRefused,
            onSms = { calls += "sms:$it" }, onApps = { calls += "apps:$it" }, onAppInfo = { calls += "appInfo" },
            onTryIt = { calls += "try" }, onBack = {}, onTeach = { calls += "teach:$it" }, onRules = { calls += "rules" },
            onHowItWorks = { calls += "how" },
        )
    }

    private fun switches() = rule.onAllNodes(isToggleable())

    @Test fun offShowsTheNoteTheSwitchesAndHowItWorksOnly() {
        show()
        rule.onNodeWithText(text(R.string.capture_off_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.message_rules)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.try_it)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.how_it_works)).performClick()
        assertEquals(listOf("how"), calls)
    }

    @Test fun bankSmsSwitchAsksToTurnOn() {
        show()
        switches()[0].assertIsOff().performClick()
        assertEquals(listOf("sms:true"), calls)
    }

    @Test fun wantedButNotAllowedShowsOff() {
        // Never "on" with nothing recorded: the switch follows what Android allows.
        show(sms = true, smsAllowed = false)
        switches()[0].assertIsOff()
    }

    @Test fun onShowsRulesAndTryIt() {
        show(sms = true, smsAllowed = true)
        switches()[0].assertIsOn()
        rule.onNodeWithText(text(R.string.capture_off_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.message_rules)).performClick()
        rule.onNodeWithText(text(R.string.try_it)).performClick()
        assertEquals(listOf("rules", "try"), calls)
    }

    @Test fun installedFromAFileAndRefusedShowsTheWayOut() {
        show(restricted = true, smsRefused = true)
        rule.onNodeWithText(text(R.string.restricted_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.capture_off_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.app_info)).performClick()
        assertEquals(listOf("appInfo"), calls)
    }

    @Test fun refusedOnAStoreInstallShowsNoRestrictedNote() {
        show(restricted = false, smsRefused = true)
        rule.onNodeWithText(text(R.string.restricted_title)).assertDoesNotExist()
    }

    @Test fun notUnderstoodStaysReachableWithCaptureOff() {
        show(notUnderstood = 2)
        rule.onAllNodesWithText(plural(R.plurals.n_not_understood, 2, 2))[0].performClick()
        assertEquals(listOf("teach:0"), calls)
    }
}
