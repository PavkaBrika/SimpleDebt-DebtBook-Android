package com.breckneck.debtbook.settings

import com.breckneck.debtbook.core.ui.awaitRoute
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Rule
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.settings.synchronization.SynchronizationViewModel
import com.breckneck.debtbook.settings.synchronization.util.BackupDrive
import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.model.AppDataLists
import com.breckneck.deptbook.domain.model.HumanDomain
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.api.services.drive.model.FileList
import org.junit.Assert.*
import org.junit.Test
import repository.AppDataRepositoryImpl
import java.math.BigDecimal

/** The real screen and Room with a deterministic Drive gateway; no account/network access. */
class DriveBackupScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun action(id: Int): SemanticsNodeInteraction {
        val matcher = hasText(context.getString(id)) and hasClickAction()
        compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        return compose.onNode(matcher)
    }
    private fun assertTextSpacing(label: SemanticsMatcher, lineHeight: TextUnit) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(label, useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val style = layouts.single().layoutInput.style
        assertEquals(lineHeight, style.lineHeight)
        assertEquals(0.sp, style.letterSpacing)
    }
    private fun scrollableSheet() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(isDialog())
    )
    private fun sheetScroll() = scrollableSheet().fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun closeSheet() = compose.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = AppDataRepositoryImpl(context)
    private val fixtureCurrency = "EUR"
    private fun snapshot(id: Int) = AppDataLists(
        listOf(HumanDomain(id, "Drive fixture $id", BigDecimal("12.34"), fixtureCurrency)),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    private fun await(condition: () -> Boolean) {
        compose.waitUntil(15_000, condition)
    }
    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(250)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private class FakeDrive : BackupDrive {
        val files = linkedMapOf<String, String>()
        var writes = 0
        var reads = 0
        var queries = 0
        var pendingWrite: Task<Void?>? = null
        var pendingQuery: Task<FileList>? = null
        override fun createFile(name: String): Task<String> {
            writes++; files[name] = ""; return Tasks.forResult(name)
        }
        override fun readFile(fileId: String?): Task<Pair<String, String>> {
            reads++; return Tasks.forResult(fileId!! to files.getValue(fileId))
        }
        override fun queryFiles(fileName: String): Task<FileList> {
            queries++
            return pendingQuery ?: Tasks.forResult(FileList().setFiles(
                if (files.containsKey(fileName)) listOf(com.google.api.services.drive.model.File().setId(fileName)) else emptyList()))
        }
        override fun saveFile(fileId: String?, name: String?, content: String?): Task<Void?> {
            writes++; files[fileId!!] = content!!; return pendingWrite ?: Tasks.forResult(null)
        }
    }
    private fun withScreen(block: (ActivityScenario<MainActivity>, SynchronizationViewModel, FakeDrive) -> Unit) {
        val original = repo.getAllAppData()
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
        val oldTheme = prefs.getString("app_theme", null)
        val theme = InstrumentationRegistry.getArguments().getString("r1bTheme")
        if (theme != null) prefs.edit().putString("app_theme", context.getString(if (theme == "dark") R.string.dark_theme else R.string.light_theme)).commit()
        val pin = prefs.getBoolean("pin_code_is_enabled", false)
        val ads = prefs.getBoolean("ads_disabled", false)
        val rate = prefs.getInt("debts_quantity_for_app_rate_dialog_show", 0)
        val auth = prefs.getBoolean("is_authorized", false)
        val oldCurrency = prefs.getString("default_currency", null)
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true)
            .putBoolean("is_authorized", false).putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE)
            .putString("default_currency", fixtureCurrency).commit()
        try {
            repo.replaceAllAppData(snapshot(990010))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val drive = FakeDrive()
                lateinit var vm: SynchronizationViewModel
                action(R.string.settings).performClick()
                action(R.string.synchronization).performScrollTo().performClick()
                action(R.string.backup_export_file).assertIsEnabled()
                scenario.onActivity { activity ->
                    vm = activity.backupFixtureModel()
                    vm.setUser("Backup test", "backup-test@example.invalid")
                    vm.setDriveServiceHelper(drive)
                    vm.setFileId(null)
                    vm.setIsAuthorized(true)
                }
                block(scenario, vm, drive)
            }
        } finally {
            repo.replaceAllAppData(original)
            prefs.edit().putString("app_theme", oldTheme).putBoolean("pin_code_is_enabled", pin)
                .putBoolean("ads_disabled", ads).putBoolean("is_authorized", auth)
                .putInt("debts_quantity_for_app_rate_dialog_show", rate).putString("default_currency", oldCurrency).commit()
        }
    }
    @Test fun twoDatedCopiesPreviewCancelConfirmAndMainRefresh() = withScreen { scenario, vm, drive ->
        val latest = snapshot(990011); val previous = snapshot(990012)
        drive.files[SynchronizationViewModel.BACKUP_FILE_NAME] = BackupCodec().encode(latest)
        drive.files[SynchronizationViewModel.PREVIOUS_FILE_NAME] = BackupCodec().encode(previous)
        capture("polish-backup-menu-top.png")
        action(R.string.log_out).performScrollTo()
        capture("polish-backup-menu-bottom.png")
        action(R.string.backup_drive_choose).performScrollTo().performClick()
        await { vm.driveBackups.value?.size == 2 && vm.isRestoring.value == false }
        assertTrue(vm.driveBackups.value!!.all { it.preview?.createdAt != null })
        compose.onNodeWithContentDescription(context.getString(R.string.cancel)).assertIsDisplayed()
        capture("fix-drive-copies.png")
        assertEquals(snapshot(990010), repo.getAllAppData())
        assertEquals(0, drive.writes)
        val previousCopy = action(R.string.backup_drive_previous).performScrollTo()
        // Leave room for the dialog's viewport to resize while its insets are applied.
        scrollableSheet().performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
            scroll(0f, -16f * context.resources.displayMetrics.density)
        }
        val listScrollY = sheetScroll()
        previousCopy.assertIsDisplayed().performClick()
        await { vm.restorePreview.value != null && vm.isRestoring.value == false }
        assertEquals(SynchronizationViewModel.RestoreSource.DRIVE_PREVIOUS, vm.restorePreview.value!!.source)
        action(R.string.backup_confirm_restore).performScrollTo()
        val reads = drive.reads; val queries = drive.queries
        val copies = vm.driveBackups.value
        action(R.string.backup_back_to_copies).performScrollTo().performClick()
        await { vm.restorePreview.value == null }
        assertSame("Back must retain the downloaded list", copies, vm.driveBackups.value)
        assertEquals("Returning to the list must keep its scroll position", listScrollY, sheetScroll())
        action(R.string.backup_drive_previous).assertIsDisplayed()
        assertEquals(snapshot(990010), repo.getAllAppData())
        action(R.string.backup_drive_latest).performScrollTo().performClick()
        await { vm.restorePreview.value != null }
        assertEquals("Another preview starts at its source", 0f, sheetScroll())
        // A system Back from details returns to the same list as the explicit action.
        pressBack()
        await { vm.restorePreview.value == null }
        action(R.string.backup_drive_latest).assertExists()
        assertSame(copies, vm.driveBackups.value)
        assertEquals(reads, drive.reads)
        assertEquals(queries, drive.queries)
        action(R.string.backup_drive_latest).performScrollTo().performClick()
        await { vm.restorePreview.value != null && vm.isRestoring.value == false }
        scenario.recreate()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertNotNull("Downloaded preview must survive recreation", vm.restorePreview.value)
        action(R.string.backup_back_to_copies).performScrollTo().performClick()
        await { vm.restorePreview.value == null }
        assertSame("List lost across Activity recreation", copies, vm.driveBackups.value)
        action(R.string.backup_drive_latest).performScrollTo().performClick()
        action(R.string.backup_confirm_restore).performScrollTo().performClick()
        await { vm.isRestoring.value == false && repo.getAllAppData() == latest }
        assertEquals(0, drive.writes)
        androidx.test.espresso.Espresso.pressBack()
        compose.awaitRoute("Settings")
        androidx.test.espresso.Espresso.pressBack()
        compose.awaitRoute("Debts")
        compose.waitUntil(10_000) { compose.onNodeWithText(latest.humanList.single().name).isDisplayed() }
    }
    @Test fun savingRequiresConfirmationAndKeepsExactlyOneEarlierCopy() = withScreen { scenario, vm, drive ->
        val latest = BackupCodec().encode(snapshot(990011))
        drive.files[SynchronizationViewModel.BACKUP_FILE_NAME] = latest
        drive.files[SynchronizationViewModel.PREVIOUS_FILE_NAME] = BackupCodec().encode(snapshot(990012))
        scenario.onActivity { vm.setFileId(SynchronizationViewModel.BACKUP_FILE_NAME) }
        action(R.string.synchronize).performScrollTo().performClick()
        assertEquals(0, drive.writes)
        closeSheet()
        assertEquals(0, drive.writes)
        action(R.string.synchronize).performScrollTo().performClick()
        compose.onNode(hasText(context.getString(R.string.synchronize)) and hasClickAction() and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        await { drive.writes == 2 && vm.savingTo.value == null }
        assertEquals(2, drive.files.size)
        assertEquals(latest, drive.files[SynchronizationViewModel.PREVIOUS_FILE_NAME])
        assertEquals(snapshot(990010), BackupCodec().decode(drive.files.getValue(SynchronizationViewModel.BACKUP_FILE_NAME)).data)
        assertEquals(snapshot(990010), repo.getAllAppData())
    }
    @Test fun signingOutLeavesLocalRecordsAndRequiresConfirmation() = withScreen { _, vm, drive ->
        val local = repo.getAllAppData()
        drive.files[SynchronizationViewModel.BACKUP_FILE_NAME] = BackupCodec().encode(local)
        val copies = drive.files.toMap()
        val signOut = action(R.string.log_out).performScrollTo()
        assertTextSpacing(hasText(context.getString(R.string.log_out)), 19.sp)
        signOut.performClick()
        val sheetText = listOf(
            R.string.backup_drive_sign_out_title to 26.sp,
            R.string.backup_drive_sign_out_message to 22.sp,
            R.string.log_out to 19.sp,
        )
        for ((id, lineHeight) in sheetText) {
            val label = hasText(context.getString(id)) and hasAnyAncestor(isDialog())
            assertTextSpacing(label, lineHeight)
        }
        capture("settings-backup-sign-out.png")
        closeSheet()
        compose.waitForIdle()
        assertTrue(vm.isAuthorized.value == true)
        action(R.string.log_out).performScrollTo().performClick()
        compose.onNode(hasText(context.getString(R.string.log_out)) and hasClickAction() and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick()
        await { vm.isAuthorized.value == false }
        assertNull(vm.driveServiceHelper.value)
        assertEquals(local, repo.getAllAppData())
        assertEquals(copies, drive.files)
        assertEquals(0, drive.writes)
    }

    @Test fun pendingWritePreventsSecondOperationAndBackUntilFinished() = withScreen { scenario, vm, drive ->
        val saveToDrive = action(R.string.synchronize)
        val saveToFile = action(R.string.backup_export_file)
        assertTrue(
            "Google Drive should be above file backup",
            saveToDrive.fetchSemanticsNode().positionInRoot.y < saveToFile.fetchSemanticsNode().positionInRoot.y,
        )
        val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        val driveSaveRow = hasClickAction() and hasAnyDescendant(hasText(context.getString(R.string.synchronize)))

        val pending = TaskCompletionSource<Void?>()
        drive.pendingWrite = pending.task
        saveToDrive.performScrollTo().performClick()
        compose.onNode(hasText(context.getString(R.string.synchronize)) and hasClickAction() and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick()
        await { drive.writes == 2 && vm.savingTo.value == SynchronizationViewModel.SaveDestination.DRIVE }
        saveToDrive.assertIsNotEnabled()
        compose.onAllNodes(progress, useUnmergedTree = true).assertCountEquals(1)
        compose.onNode(progress and hasAnyAncestor(driveSaveRow), useUnmergedTree = true).assertIsDisplayed()
        scenario.onActivity {
            vm.uploadBackup()
            it.onBackPressedDispatcher.onBackPressed()
            it.onBackPressedDispatcher.onBackPressed()
        }
        compose.awaitRoute("Backup")
        val waitMessage = context.getString(R.string.backup_wait)
        compose.onNodeWithText(waitMessage).assertIsDisplayed()
        assertEquals(2, drive.writes)
        pending.setResult(null)
        await { vm.savingTo.value == null }
        compose.onAllNodes(progress, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.backup_drive_saved)).assertIsDisplayed()
        compose.onNodeWithText(waitMessage).assertDoesNotExist()
        capture("settings-drive-saved-snackbar.png")
        saveToDrive.assertIsEnabled()
        scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        compose.awaitRoute("Settings")
    }

    @Test fun connectionShowsProgressUntilDriveIsReadyOrFails() = withScreen { scenario, vm, drive ->
        val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        val indicator = compose.onNode(progress, useUnmergedTree = true)

        scenario.onActivity { vm.setDriveServiceHelper(drive) }
        compose.onNodeWithText(context.getString(R.string.backup_drive_connecting)).assertIsDisplayed()
        indicator.assertIsDisplayed()
        action(R.string.backup_drive_choose).assertIsNotEnabled()
        action(R.string.backup_export_file).assertIsEnabled()
        scenario.recreate()
        indicator.assertIsDisplayed()
        capture("settings-drive-connecting.png")

        scenario.onActivity { vm.setFileId(null) }
        action(R.string.synchronize).assertIsEnabled()
        indicator.assertDoesNotExist()

        scenario.onActivity {
            vm.setDriveServiceHelper(drive)
            vm.failDriveConnection(java.io.IOException("Offline"))
        }
        action(R.string.backup_drive_retry).assertIsEnabled()
        indicator.assertDoesNotExist()
    }

    @Test fun failedDriveReadCanBeRetriedWithoutWriting() = withScreen { _, vm, drive ->
        drive.pendingQuery = Tasks.forException(java.io.IOException("Offline"))
        action(R.string.backup_drive_choose).performScrollTo().performClick()
        await { vm.operationError.value != null && vm.driveBackups.value == null }
        compose.onNodeWithText(context.getString(R.string.backup_transfer_failed)).assertExists()
        drive.pendingQuery = null
        action(R.string.backup_drive_choose).performScrollTo().performClick()
        await { vm.driveBackups.value != null && vm.isRestoring.value == false }
        compose.onNodeWithText(context.getString(R.string.backup_drive_empty)).assertExists()
        assertEquals(0, drive.writes)
    }

    @Test fun emptyDriveAndDamagedLatestStillAllowTheValidEarlierCopy() = withScreen { _, vm, drive ->
        action(R.string.backup_drive_choose).performScrollTo().performClick()
        await { vm.driveBackups.value != null && vm.isRestoring.value == false }
        assertTrue(vm.driveBackups.value!!.isEmpty())
        closeSheet()
        drive.files[SynchronizationViewModel.BACKUP_FILE_NAME] = "{broken"
        drive.files[SynchronizationViewModel.PREVIOUS_FILE_NAME] = BackupCodec().encode(snapshot(990012))
        action(R.string.backup_drive_choose).performScrollTo().performClick()
        await { vm.driveBackups.value?.size == 2 && vm.isRestoring.value == false }
        assertNull(vm.driveBackups.value!![0].preview)
        assertNotNull(vm.driveBackups.value!![1].preview)
        assertEquals(0, drive.writes)
        action(R.string.backup_drive_previous).performScrollTo().performClick()
        await { vm.restorePreview.value != null && vm.isRestoring.value == false }
        action(R.string.backup_back_to_copies).performClick()
        closeSheet()
        assertEquals(snapshot(990010), repo.getAllAppData())
    }
    @Test fun cancellingLoadingDoesNotReopenTheSheetOrChangeData() = withScreen { _, vm, drive ->
        val pending = TaskCompletionSource<FileList>()
        drive.pendingQuery = pending.task
        action(R.string.backup_drive_choose).performScrollTo().performClick()
        await { vm.driveBackups.value != null && vm.isRestoring.value == true }
        closeSheet()
        await { vm.driveBackups.value == null && vm.isRestoring.value == false }
        pending.setResult(FileList().setFiles(emptyList()))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertNull(vm.driveBackups.value)
        assertEquals(0, drive.writes)
        assertEquals(snapshot(990010), repo.getAllAppData())
    }

}
