package cc.stkmn.kalplan

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.compose.ui.text.TextRange
import androidx.test.espresso.Espresso.pressBack
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import cc.stkmn.kalplan.data.AppRepository
import cc.stkmn.kalplan.ui.generateDebugCases

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun inputEditsKeepCaretAndCommitOnlyOnApply() {
        var saved = ""
        var commits = 0
        compose.activity.runOnUiThread { compose.activity.setContent {
            cc.stkmn.kalplan.ui.theme.KalPlanTheme {
                cc.stkmn.kalplan.ui.TextSetting("Stable input", "") { saved = it; commits++ }
            }
        } }
        compose.onNodeWithText("Stable input").performClick()
        val field = compose.onNode(hasSetTextAction())
        for (character in "Alpha") field.performTextInput(character.toString())
        field.performTextInputSelection(TextRange(2))
        for (character in "XYZ") field.performTextInput(character.toString())
        field.assertTextEquals("AlXYZpha")
        org.junit.Assert.assertEquals(0, commits)
        compose.onNodeWithText("Apply").performClick()
        org.junit.Assert.assertEquals("AlXYZpha", saved)
        org.junit.Assert.assertEquals(1, commits)
    }
    @Test fun encryptedBackupRestoreKeepsCredentialsAndSendFences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val repository = AppRepository.get(context); repository.load()
            val original = repository.backup(true)
            try {
                val account = cc.stkmn.kalplan.data.MailAccount("backup-account", "Backup", "test", "", "imap.example.invalid", smtpHost = "", smtpEnabled = false, enabled = false)
                repository.saveAccount(account, "synthetic-test-password", "")
                val request = cc.stkmn.kalplan.data.StoredRequest("backup-fenced", sender = "", subject = "", body = "", receivedMillis = 0)
                repository.update { it.copy(requests = listOf(request)) }
                val source = repository.backup(true)
                val pass = "synthetic-backup-password".toCharArray()
                val decoded = cc.stkmn.kalplan.infrastructure.backup.PortableBackup.decode(cc.stkmn.kalplan.infrastructure.backup.PortableBackup.encode(source, pass), pass)
                repository.request(request.id) { it.copy(status = "DELIVERY_UNKNOWN", replyId = "<synthetic@test.invalid>") }
                repository.restore(decoded)
                org.junit.Assert.assertEquals("synthetic-test-password", repository.secret(account.id))
                org.junit.Assert.assertEquals("DELIVERY_UNKNOWN", repository.data.value.requests.single().status)
                org.junit.Assert.assertFalse(repository.data.value.accounts.first { it.id == account.id }.enabled)
            } finally { repository.restore(original) }
        }
    }
    @Test fun swipeDetailBackReturnsToSameSwipeAndMonthCanJumpYears() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val r = AppRepository.get(context); r.load()
            r.update { it.copy(accounts = it.accounts.map { account -> account.copy(enabled = false) }, settings = it.settings.copy(debug = true, language = "EN", syncMinutes = 0)) }
            generateDebugCases(context, r)
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Swipe").fetchSemanticsNodes().isNotEmpty() }
        val refreshRepository = AppRepository.get(context)
        val previousSync = refreshRepository.data.value.lastSyncMillis ?: 0L
        // Keep the real worker running long enough to verify its UI state and duplicate protection.
        runBlocking { refreshRepository.syncMutex.lock() }
        try {
            compose.onNodeWithTag("request-list").performTouchInput { swipeDown() }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("manual-sync-status").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Sync").assertIsNotEnabled()
            val activeWorkIds = runBlocking { cc.stkmn.kalplan.infrastructure.sync.SyncScheduler.manualStates(context).first().filter { !it.state.isFinished }.map { it.id } }
            org.junit.Assert.assertEquals(1, activeWorkIds.size)
            compose.onNodeWithTag("request-list").performTouchInput { swipeDown() }
            screenshot("pull-to-refresh")
            org.junit.Assert.assertEquals(activeWorkIds, runBlocking { cc.stkmn.kalplan.infrastructure.sync.SyncScheduler.manualStates(context).first().filter { !it.state.isFinished }.map { it.id } })
        } finally { refreshRepository.syncMutex.unlock() }
        compose.waitUntil(15_000) { (refreshRepository.data.value.lastSyncMillis ?: 0L) > previousSync }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("manual-sync-status").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Swipe").performClick()
        compose.onNodeWithText("All details and source mail").performScrollTo().performClick()
        compose.onNodeWithText("Info").assertExists()
        pressBack()
        compose.onNodeWithText("All details and source mail").assertExists()
        screenshot("swipe-rich-card")
        pressBack()
        compose.onNodeWithText("Your requests").assertExists()
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("Month", substring = false).performClick()
        val header = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.US))
        compose.onNodeWithText(header).performClick()
        compose.onNodeWithText("Year").performTextReplacement("2030")
        compose.onNodeWithText("Mar", substring = false).performClick()
        compose.onNodeWithText("March 2030").assertExists()
        screenshot("month-year-navigation")
        // Test the actual screen in a short viewport with accessibility font scaling.
        val repository = AppRepository.get(context)
        val testRequest = cc.stkmn.kalplan.data.StoredRequest("small-swipe", sender = "synthetic@example.invalid",
            subject = "A long appointment title on a small display", body = "Synthetic request content. ".repeat(100), receivedMillis = 0,
            candidates = listOf(cc.stkmn.kalplan.data.StoredCandidate(1_800_000_000_000, 1_800_003_600_000, location = "Example location")), selectedCandidate = 0)
        var model by mutableStateOf("REQUEST_FIRST")
        var clicked by mutableStateOf("")
        compose.activity.runOnUiThread { compose.activity.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f), cc.stkmn.kalplan.ui.LocalAppLanguage provides "EN") {
                cc.stkmn.kalplan.ui.theme.KalPlanTheme {
                    Box(Modifier.requiredSize(320.dp, 320.dp)) {
                        cc.stkmn.kalplan.ui.SwipeScreen(
                            cc.stkmn.kalplan.data.AppData(requests = listOf(testRequest), settings = cc.stkmn.kalplan.data.Settings(swipeDisplayModel = model)),
                            cc.stkmn.kalplan.application.Planner(context, repository), onOpen = {},
                            onAction = { _, action -> clicked = action }, onLater = { clicked = "later" }, onUnclear = {}, onClose = {})
                    }
                }
            }
        } }
        for (displayModel in listOf("REQUEST_FIRST", "CALENDAR_FIRST")) {
            compose.runOnIdle { model = displayModel }
            val accept = compose.onNodeWithContentDescription("Review acceptance")
            val before = accept.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription("Review decline").assertIsDisplayed()
            compose.onNodeWithContentDescription("Later").assertIsDisplayed()
            compose.onNodeWithText("All details and source mail").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Expand content").performScrollTo().performClick()
            compose.onNodeWithText("All details and source mail").performScrollTo().assertIsDisplayed()
            org.junit.Assert.assertEquals(before, accept.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle { clicked = "" }
            accept.performClick()
            compose.runOnIdle { org.junit.Assert.assertEquals("accept", clicked) }
            screenshot("swipe-small-large-font-$displayModel")
            compose.onNodeWithText("Show less").performScrollTo().performClick()
        }
    }
    @Test fun guidedProfileKeepsBothReplyTemplatesAfterSaving() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AppRepository.get(context)
        runBlocking {
            repository.load()
            val request = cc.stkmn.kalplan.data.StoredRequest(
                "profile-save-regression", sender = "example@example.org", subject = "Profile save regression",
                body = "Datum: 14.12.2026\nUhrzeit: 14:00\nOrt: Online", receivedMillis = 1_791_014_400_000,
                unclear = false
            )
            repository.update { it.copy(requests = listOf(request),
                settings = it.settings.copy(language = "EN")) }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Profile save regression").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Profile save regression").performClick()
        compose.onNodeWithText("Check", substring = false).performClick()
        compose.onNodeWithText("Create profile from this email").performScrollTo().performClick()
        compose.onAllNodesWithText("Add field")[0].performScrollTo().performClick()
        compose.onNodeWithText("Acceptance template, empty = global").performScrollTo().performTextReplacement("Accepted {date}")
        compose.onNodeWithText("Decline template, empty = global").performScrollTo().performTextReplacement("Unavailable {date}")
        screenshot("guided-profile-templates")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { repository.data.value.profiles.any { it.name == "Profile save regression" } }
        val saved = repository.data.value.profiles.last { it.name == "Profile save regression" }
        org.junit.Assert.assertEquals("Accepted {date}", saved.acceptTemplate)
        org.junit.Assert.assertEquals("Unavailable {date}", saved.declineTemplate)
    }
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
