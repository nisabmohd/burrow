package app.burrow.money.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code paths of a typical session: open the app and visit every tab.
 * Grows with the app (docs/PERFORMANCE.md: Today → Activity scroll → Insights → add expense).
 * Run with `./gradlew :app:generateBaselineProfile`, then commit the result.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.visitTabs()
    }

    private fun UiDevice.visitTabs() {
        for (tab in listOf("Activity", "Plan", "Insights", "More", "Today")) {
            wait(Until.findObject(By.text(tab)), TIMEOUT)?.click()
            waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "app.burrow.money"
        const val TIMEOUT = 5_000L
    }
}
