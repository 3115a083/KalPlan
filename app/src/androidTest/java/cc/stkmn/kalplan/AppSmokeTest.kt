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
    @Test fun systemBarsFollowAppThemeInActivityAndFullScreenDialog() {
        var dark by mutableStateOf(false)
        var dialog by mutableStateOf(false)
        var dialogWindow: android.view.Window? = null
        compose.activity.runOnUiThread { compose.activity.setContent {
            cc.stkmn.kalplan.ui.theme.KalPlanTheme(choice = cc.stkmn.kalplan.ui.theme.ThemeChoice.HIGH_CONTRAST, darkTheme = dark) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) { androidx.compose.material3.Text("System bar contrast", Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) }
                if (dialog) androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                    cc.stkmn.kalplan.ui.theme.SyncSystemBars()
                    val view = androidx.compose.ui.platform.LocalView.current
                    SideEffect {
                        var parent = view.parent
                        while (parent != null && parent !is androidx.compose.ui.window.DialogWindowProvider) parent = parent.parent
                        dialogWindow = (parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                    }
                    androidx.compose.material3.Surface(Modifier.fillMaxSize()) { androidx.compose.material3.Text("Dialog contrast", Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) }
                }
            }
        } }
        fun assertBars(window: android.view.Window, light: Boolean) = compose.runOnIdle {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            org.junit.Assert.assertEquals(light, controller.isAppearanceLightStatusBars)
            org.junit.Assert.assertEquals(light, controller.isAppearanceLightNavigationBars)
        }
        compose.waitForIdle(); assertBars(compose.activity.window, true); screenshot("system-bars-light")
        compose.runOnIdle { dark = true }; compose.waitForIdle(); assertBars(compose.activity.window, false); screenshot("system-bars-dark")
        compose.runOnIdle { dark = false; dialog = true }; compose.waitForIdle()
        compose.runOnIdle { org.junit.Assert.assertNotNull(dialogWindow) }
        assertBars(requireNotNull(dialogWindow), true); screenshot("system-bars-light-dialog")
        compose.runOnIdle { dark = true }; compose.waitForIdle(); assertBars(requireNotNull(dialogWindow), false)
        screenshot("system-bars-dark-dialog")
    }
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
        // Let the real API-26 IME finish opening before changing the selection.
        // A late platform focus/IME event otherwise moves the test cursor to the end.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        field.performTextInputSelection(TextRange(2))
        field.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange, TextRange(2)))
        for (character in "XYZ") field.performTextInput(character.toString())
        field.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("AlXYZpha")))
        field.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange, TextRange(5)))
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
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Swipe view").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("History", substring = false).assertDoesNotExist()
        compose.onNodeWithText("List", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Show history").performClick()
        compose.onNodeWithText("History", substring = false).assertExists()
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Pending requests").performClick()
        compose.onNodeWithText("Your requests").assertExists()
        val swipeBounds = compose.onNodeWithContentDescription("Swipe view").fetchSemanticsNode().boundsInRoot
        val refreshBounds = compose.onNodeWithContentDescription("Sync").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(swipeBounds.right <= refreshBounds.left)
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
        compose.onNodeWithContentDescription("Swipe view").performClick()
        compose.onNodeWithText("All details and source mail").performScrollTo().performClick()
        compose.onNodeWithText("Info").assertExists()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("All details and source mail").assertExists()
        screenshot("swipe-rich-card")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
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
        val source = "Referenz: AB-17\nDatum: 14.12.2026\nUhrzeit: 14:00-15:00\nOrt: Online"
        runBlocking {
            repository.load()
            val profile = cc.stkmn.kalplan.extraction.ExtractionProfile(id = "workbench-regression", name = "Profile save regression",
                parseDirection = cc.stkmn.kalplan.extraction.ParseDirection.BOTTOM_UP,
                multipleDateMode = cc.stkmn.kalplan.extraction.MultipleDateMode.ALTERNATIVE)
            val request = cc.stkmn.kalplan.data.StoredRequest("profile-save-regression", sender = "synthetic@example.invalid", subject = "Profile save regression",
                body = source, receivedMillis = 1_791_014_400_000, unclear = false, extractionProfileId = profile.id)
            repository.update { it.copy(requests = listOf(request), profiles = it.profiles.filterNot { p -> p.id == profile.id } + profile,
                settings = it.settings.copy(language = "EN")) }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Profile save regression").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Profile save regression").performClick()
        compose.onNodeWithText("Check", substring = false).performClick()
        compose.onNodeWithText("Refine profile with this email").performScrollTo().performClick()
        val sourceEditor = compose.onNodeWithTag("profile-source")
        sourceEditor.performScrollTo().performClick().performTextInputSelection(TextRange(source.length))
        sourceEditor.performTextInput(" ")
        sourceEditor.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(source + " ")))
        sourceEditor.performTextInput("Text")
        sourceEditor.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(source + " Text")))
        sourceEditor.performTextReplacement(source)
        val start = source.indexOf("AB-17")
        compose.onNodeWithTag("profile-source").performScrollTo().performClick().performTextInputSelection(TextRange(start, start + 5))
        compose.onNodeWithText("Create field from selection").performScrollTo().performClick()
        compose.onNodeWithText("Field name").performScrollTo().performTextReplacement("Reference")
        compose.onNodeWithTag("profile-tab-2").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Profile rule: AB-17").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile-expected").performScrollTo().performTextReplacement("AB-18")
        compose.onNodeWithTag("profile-save-expected").performScrollTo().performClick()
        compose.onNodeWithTag("profile-check-examples").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile-example-check-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile-example-check-0").assertTextContains("AB-18", substring = true)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile-expected").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile-expected").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("AB-18")))
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Profile rule: AB-17").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile-expected").performScrollTo().performTextReplacement("AB-17")
        compose.onNodeWithTag("profile-save-expected").performScrollTo().performClick()
        compose.onNodeWithTag("profile-check-examples").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile-example-check-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile-example-check-0").assertTextContains("✓", substring = true)
        screenshot("profile-workbench-preview")
        compose.onNodeWithTag("profile-tab-3").performClick()
        compose.onNodeWithText("Acceptance template, empty = global").performScrollTo().performTextReplacement("Accepted {feld.field_1}")
        compose.onNodeWithText("Decline template, empty = global").performScrollTo().performTextReplacement("Unavailable {date}")
        screenshot("guided-profile-templates")
        compose.onNodeWithTag("profile-save").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { repository.data.value.profiles.any { it.id == "workbench-regression" && it.acceptTemplate == "Accepted {feld.field_1}" } }
        val saved = repository.data.value.profiles.first { it.id == "workbench-regression" }
        org.junit.Assert.assertEquals("Unavailable {date}", saved.declineTemplate)
        org.junit.Assert.assertEquals(cc.stkmn.kalplan.extraction.ParseDirection.BOTTOM_UP, saved.parseDirection)
        org.junit.Assert.assertEquals(cc.stkmn.kalplan.extraction.MultipleDateMode.ALTERNATIVE, saved.multipleDateMode)
        org.junit.Assert.assertEquals("AB-17", saved.examples.single().expected["field_1"])
        org.junit.Assert.assertEquals("AB-17", cc.stkmn.kalplan.extraction.ProfilePreview.evaluate(
            cc.stkmn.kalplan.extraction.ExtractionInput("synthetic@example.invalid", "Profile save regression", source, java.time.Instant.ofEpochMilli(1_791_014_400_000)), saved).fields["field_1"]?.value)
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
        compose.onNodeWithText("Enable daily quiet hours").performScrollTo().performClick()
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
