package cc.stkmn.kalplan

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking
import cc.stkmn.kalplan.data.AppRepository
import cc.stkmn.kalplan.ui.generateDebugCases

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun syntheticDetailSafeConfirmationAndModernSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val repository = AppRepository.get(context)
            repository.load()
            repository.update { it.copy(settings = it.settings.copy(debug = true)) }
            generateDebugCases(context, repository)
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Testauftrag, erreichbar").fetchSemanticsNodes().isNotEmpty() }
        screenshot("list")
        compose.onNodeWithText("Testauftrag, erreichbar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Info").fetchSemanticsNodes().isNotEmpty() }
        screenshot("detail")
        compose.onNodeWithText("Edit details").performScrollTo().performClick()
        compose.onNodeWithText("Date (MM/DD/YYYY)").performTextReplacement(java.time.LocalDate.now().plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy")))
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
        compose.onNodeWithText("Design and language").assertExists()
        screenshot("settings")
        compose.onNodeWithText("Design and language").performClick()
        compose.onNodeWithText("Labels and sorting").performScrollTo().performClick()
        compose.onNodeWithText("Label", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Assign automatically when").assertExists()
        compose.onNodeWithText("Color, e.g. 6750A4").assertDoesNotExist()
        screenshot("label-editor")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Labels and sorting").performClick()
        compose.onNode(hasScrollAction()).performScrollToIndex(6)
        compose.onNodeWithText("Synchronization").performClick()
        compose.onNodeWithText("From").performScrollTo().performClick()
        compose.onNodeWithText("Choose time").assertExists()
        screenshot("quiet-time-picker")
        compose.onNodeWithText("Cancel").performClick()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(250)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
