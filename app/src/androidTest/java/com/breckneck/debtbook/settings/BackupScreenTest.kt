package com.breckneck.debtbook.settings

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.os.PatternMatcher
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import org.junit.Rule
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.settings.synchronization.SynchronizationViewModel
import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.model.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import repository.AppDataRepositoryImpl
import java.io.File
import java.math.BigDecimal

class BackupScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun action(id: Int): SemanticsNodeInteraction {
        val matcher = hasText(context.getString(id)) and hasClickAction()
        compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        return compose.onNode(matcher)
    }
    private fun closeSheet() = compose.onNodeWithContentDescription(context.getString(R.string.cancel))
        .performScrollTo().performClick()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private lateinit var originalPrefs: Map<String, *>

    @Before fun suppressUnrelatedPrompts() {
        originalPrefs = prefs.all
        prefs.edit().putBoolean("ads_disabled", true).putBoolean("pin_code_is_enabled", false)
            .putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).commit()
    }

    @After fun restorePreferences() {
        val edit = prefs.edit()
        for (key in listOf("ads_disabled", "pin_code_is_enabled", "debts_quantity_for_app_rate_dialog_show", "is_authorized")) {
            when (val original = originalPrefs[key]) {
                is Int -> edit.putInt(key, original)
                is Boolean -> edit.putBoolean(key, original)
                null -> edit.remove(key)
            }
        }
        edit.commit()
    }
    private fun current(activity: MainActivity): Fragment =
        (activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment).childFragmentManager.primaryNavigationFragment!!
    private fun await(details: () -> String = { "" }, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10000
        while (!condition()) { if (System.currentTimeMillis() > end) fail("Backup UI timed out: ${details()}"); Thread.sleep(50) }
    }
    private fun snapshot(id: Int, sum: String) = AppDataLists(
        listOf(HumanDomain(id, "Backup screen", sum.toBigDecimal(), "EUR", BigDecimal.ZERO)),
        listOf(DebtDomain(id, sum.toBigDecimal(), id, null, "2026-11-15")),
        emptyList(), emptyList(), emptyList(), emptyList(),
    )
    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(250)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun signedOutNoticeLinksToThePrivacyPolicy() {
        assumeTrue(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val url = Uri.parse(context.getString(R.string.privacy_policy_url))
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme(requireNotNull(url.scheme))
            addDataAuthority(requireNotNull(url.host), null)
            addDataPath(requireNotNull(url.path), PatternMatcher.PATTERN_LITERAL)
        }
        val privacyIntent = instrumentation.addMonitor(filter, null, true)
        prefs.edit().putBoolean("is_authorized", false).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                action(R.string.settings).performClick()
                val summary = context.getString(R.string.backup_formats)
                assertTrue(summary.startsWith("Drive / "))
                action(R.string.synchronization).performScrollTo().assertTextContains(summary).performClick()
                val signIn = action(R.string.sign_in_with_google)
                val text = HtmlCompat.fromHtml(
                    context.getString(R.string.backup_drive_privacy_notice),
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                ).toString()
                val notice = compose.onNodeWithText(text).performScrollTo().assertHasClickAction().assertIsEnabled()
                val label = notice.fetchSemanticsNode().config[SemanticsProperties.Text].single()
                val link = label.spanStyles.single { it.item.textDecoration == TextDecoration.Underline }
                assertTrue(link.start > 0 && link.end < label.length)
                val fileHeading = compose.onNodeWithText(context.getString(R.string.backup_local_title)).fetchSemanticsNode()
                val noticeTop = notice.fetchSemanticsNode().positionInRoot.y
                assertTrue(noticeTop > signIn.fetchSemanticsNode().positionInRoot.y && noticeTop < fileHeading.positionInRoot.y)
                capture("settings-backup-privacy.png")
                notice.performTouchInput { click(Offset(8f, center.y / 2f)) }
                compose.waitForIdle()
                assertEquals(1, privacyIntent.hits)
            }
        } finally {
            instrumentation.removeMonitor(privacyIntent)
        }
    }

    @Test fun signInShowsProgressAndCancellationEnablesRetry() {
        assumeTrue(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS)
        prefs.edit().putBoolean("is_authorized", false).commit()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cancelledSignIn = instrumentation.addMonitor(
            "com.google.android.gms.auth.api.signin.internal.SignInHubActivity",
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null),
            true,
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                action(R.string.settings).performClick()
                action(R.string.synchronization).performScrollTo().performClick()
                val signIn = action(R.string.sign_in_with_google)
                lateinit var vm: SynchronizationViewModel
                scenario.onActivity { activity ->
                    vm = ViewModelProvider(current(activity))[SynchronizationViewModel::class.java]
                    assertTrue(vm.startDriveSignIn())
                    assertFalse(vm.startDriveSignIn())
                }

                val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
                val signInRow = hasClickAction() and hasAnyDescendant(hasText(context.getString(R.string.sign_in_with_google)))
                signIn.assertIsNotEnabled()
                compose.onNode(progress and hasAnyAncestor(signInRow), useUnmergedTree = true).assertIsDisplayed()
                scenario.onActivity { vm.finishDriveSignIn() }

                signIn.assertIsEnabled().performClick()
                compose.waitUntil(5_000) { cancelledSignIn.hits == 1 && vm.driveSignInPending.value == false }
                signIn.assertIsEnabled()
                compose.onAllNodes(progress, useUnmergedTree = true).assertCountEquals(0)
                assertFalse(vm.isAuthorized.value == true)
            }
        } finally {
            instrumentation.removeMonitor(cancelledSignIn)
        }
    }

    @Test fun edgeSwipeRevealsSettingsAndPopsOnlyOnCommit() {
        requirePredictiveGestures(context)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            action(R.string.settings).performClick()
            action(R.string.synchronization).performScrollTo().performClick()
            action(R.string.backup_export_file).assertIsEnabled()
            lateinit var outgoing: View
            scenario.onActivity { outgoing = it.findViewById(R.id.backupComposeView) }
            // Wait for the forward fragment transition before starting another gesture.
            instrumentation.waitForIdleSync()
            Thread.sleep(450)
            for (cancel in listOf(true, false)) {
                instrumentation.edgeSwipe(cancel) {
                    capture("bubble-settings-back-preview.png")
                    scenario.onActivity {
                        val incoming = it.findViewById<View>(R.id.settingsComposeView)
                        assertTrue("Settings visible during swipe", incoming != null && incoming.isShown && incoming.alpha > 0f)
                        assertTrue("Backup follows the pointer", outgoing.translationX > 0f)
                    }
                }
                compose.waitUntil(5000) {
                    var arrived = false
                    scenario.onActivity {
                        val id = if (cancel) R.id.synchronizationFragment else R.id.settingsFragment
                        arrived = it.navController.currentDestination?.id == id
                    }
                    arrived
                }
                if (cancel) {
                    action(R.string.backup_export_file).assertIsDisplayed()
                    instrumentation.waitForIdleSync()
                    Thread.sleep(350)
                }
                else action(R.string.pin_code_lock).assertExists()
            }
        }
    }

    @Test fun offlinePreviewCancelRecreateConfirmAndUndo() {
        val repo = AppDataRepositoryImpl(context); val original = repo.getAllAppData()
        val oldTheme = prefs.getString("app_theme", null)
        val dark = InstrumentationRegistry.getArguments().getString("r1bTheme") == "dark"
        prefs.edit().putString("app_theme", context.getString(if (dark) R.string.dark_theme else R.string.light_theme)).commit()
        val oldAuthorization = prefs.getBoolean("is_authorized", false)
        prefs.edit().putBoolean("is_authorized", false).commit()
        val a = snapshot(900025, "0.1"); val b = snapshot(900026, "999999999999999999.99")
        val file = File(context.cacheDir, "r1b-ui-import.json").apply { writeText(BackupCodec().encode(b)) }
        try {
            repo.replaceAllAppData(a)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                action(R.string.settings).performClick()
                action(R.string.synchronization).performScrollTo().performClick()
                action(R.string.backup_import_file).assertIsEnabled()
                action(R.string.backup_export_file).assertIsEnabled()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                lateinit var vm: SynchronizationViewModel
                scenario.onActivity { activity ->
                    val fragment = current(activity)
                    vm = ViewModelProvider(fragment)[SynchronizationViewModel::class.java]
                    vm.restoreFile(Uri.fromFile(file))
                }
                await { vm.restorePreview.value != null && vm.isRestoring.value == false }
                assertEquals(a, repo.getAllAppData())
                action(R.string.backup_confirm_restore).performScrollTo().assertIsDisplayed()
                capture("fix-backup-preview.png")
                closeSheet()
                compose.waitUntil(5000) { vm.restorePreview.value == null }
                assertEquals(a, repo.getAllAppData())
                scenario.onActivity { vm.restoreFile(Uri.fromFile(file)) }
                await { vm.restorePreview.value != null && vm.isRestoring.value == false }
                action(R.string.backup_confirm_restore).performScrollTo().assertIsDisplayed()
                scenario.recreate()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val recreated = ViewModelProvider(current(activity))[SynchronizationViewModel::class.java]
                    assertSame("Preview ViewModel changed after recreation", vm, recreated)
                    assertNotNull("Preview lost after recreation", vm.restorePreview.value)
                }
                action(R.string.backup_confirm_restore).performScrollTo().performClick()
                await(details = { "preview=${vm.restorePreview.value}, busy=${vm.isRestoring.value}, error=${vm.operationError.value}, IDs=${repo.getAllAppData().humanList.map { it.id }}" }) {
                    vm.operationError.value?.let { throw AssertionError("Restore failed", it) }
                    repo.getAllAppData().humanList.firstOrNull()?.id == 900026
                }
                action(R.string.backup_restore_previous_local).performScrollTo().performClick()
                await { vm.restorePreview.value != null && vm.isRestoring.value == false }
                action(R.string.backup_confirm_restore).performScrollTo().performClick()
                await { repo.getAllAppData().humanList.firstOrNull()?.id == 900025 }
                assertEquals(a, repo.getAllAppData())
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(context.getExternalFilesDir(null), "r1b-backup-screen.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            repo.replaceAllAppData(original); file.delete()
            prefs.edit().putString("app_theme", oldTheme).putBoolean("is_authorized", oldAuthorization).commit()
        }
    }
}
