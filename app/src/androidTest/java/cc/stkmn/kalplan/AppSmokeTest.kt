package cc.stkmn.kalplan

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun onboardingSamplesDetailAndSafeConfirmation() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Load samples").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Load samples").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Praxis Beispiel").fetchSemanticsNodes().isNotEmpty() }
        screenshot("list")
        compose.onNodeWithText("Praxis Beispiel").performClick()
        compose.onNodeWithText("Sample, sending disabled").assertDoesNotExist()
        screenshot("detail")
        compose.onNodeWithText("Edit details").performScrollTo().performClick()
        compose.onNodeWithText("Date (YYYY-MM-DD)").performTextReplacement(java.time.LocalDate.now().plusDays(1).toString())
        compose.onNodeWithText("Time (HH:MM)").performTextReplacement("14:00")
        compose.onNodeWithText("Confirm details").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Accept").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Accept").performScrollTo().performClick()
        compose.onNodeWithText("Review simulation").performScrollTo().performClick()
        compose.onNodeWithText("Confirm simulation").assertExists()
        screenshot("confirmation")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Back").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Mail accounts").assertExists()
        screenshot("settings")
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
