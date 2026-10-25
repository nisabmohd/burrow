package app.burrow.money.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code paths of a typical session (docs/PERFORMANCE.md): open the app on Today, visit
 * every tab, scroll Activity, open Add expense with its keypad and category sheet. Grows with the app.
 * Run with `./gradlew :app:generateBaselineProfile`, then commit the result.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, maxIterations = 3, stableIterations = 2, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.finishWelcome()
        device.visitTabs()
    }

    /** A fresh install opens on Welcome: Get started → currency → budget → accounts → Today. */
    private fun UiDevice.finishWelcome() {
        if (!wait(Until.hasObject(By.text("Get started")), 2_000L)) return
        for (button in listOf("Get started", "Continue", "Continue", "Done")) tap(By.text(button))
    }

    private fun UiDevice.visitTabs() {
        wait(Until.hasObject(By.text("Recent")), TIMEOUT)
        for (tab in listOf("Activity", "Plan", "Insights", "More", "Today")) {
            tap(By.text(tab))
            if (tab == "Activity") {
                stable { findObject(By.scrollable(true))?.fling(Direction.DOWN) }
                waitForIdle()
                stable { findObject(By.scrollable(true))?.fling(Direction.UP) }
                waitForIdle()
            }
        }
        // Add expense: the form, the keypad and the category sheet, then close without saving.
        tap(By.desc("Add expense"))
        tap(By.text("2"))
        tap(By.text("More")) // the category chips' More: the category sheet
        tap(By.desc("Close"))
        pressBack()
        waitForIdle()
    }

    /** Waits for [selector], then clicks it; retries when Compose replaced the view meanwhile. */
    private fun UiDevice.tap(selector: androidx.test.uiautomator.BySelector) {
        stable { wait(Until.findObject(selector), TIMEOUT)?.click() }
        waitForIdle()
    }

    /** Runs [action] again if the object it found went stale (an animation or recomposition). */
    private fun UiDevice.stable(action: () -> Unit) {
        repeat(3) {
            try {
                action()
                return
            } catch (_: StaleObjectException) {
                waitForIdle()
            }
        }
    }

    private companion object {
        const val PACKAGE = "app.burrow.money"
        const val TIMEOUT = 5_000L
    }
}
