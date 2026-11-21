package com.breckneck.debtbook.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Rule
import android.content.Context
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.debt.main.DebtViewModel
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
    private fun sheetScroll() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(isDialog())
    ).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun closeSheet() = compose.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = AppDataRepositoryImpl(context)
    private fun snapshot(id: Int) = AppDataLists(
        listOf(HumanDomain(id, "Drive fixture $id", BigDecimal("12.34"), "EUR")),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    private fun current(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment)
            .childFragmentManager.primaryNavigationFragment!!
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
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true).putBoolean("is_authorized", false).putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).commit()
        try {
            repo.replaceAllAppData(snapshot(990010))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val drive = FakeDrive()
                lateinit var vm: SynchronizationViewModel
                action(R.string.settings).performClick()
                action(R.string.synchronization).performScrollTo().performClick()
                action(R.string.backup_export_file).assertIsEnabled()
                scenario.onActivity { activity ->
                    vm = ViewModelProvider(current(activity))[SynchronizationViewModel::class.java]
                    vm.setUser("Backup test", "backup-test@example.invalid")
                    vm.setDriveServiceHelper(drive)
                    vm.setFileId(null)
                    vm.setIsAuthorized(true)
                }
                block(scenario, vm, drive)
            }
        } finally {
            repo.replaceAllAppData(original)
            prefs.edit().putString("app_theme", oldTheme).putBoolean("pin_code_is_enabled", pin).putBoolean("ads_disabled", ads).putBoolean("is_authorized", auth).putInt("debts_quantity_for_app_rate_dialog_show", rate).commit()
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
        action(R.string.backup_drive_previous).performScrollTo()
        val listScrollY = sheetScroll()
        action(R.string.backup_drive_previous).performClick()
        await { vm.restorePreview.value != null && vm.isRestoring.value == false }
        assertEquals(SynchronizationViewModel.RestoreSource.DRIVE_PREVIOUS, vm.restorePreview.value!!.source)
        action(R.string.backup_confirm_restore).performScrollTo()
        val reads = drive.reads; val queries = drive.queries
        val copies = vm.driveBackups.value
        action(R.string.backup_back_to_copies).performClick()
        await { vm.restorePreview.value == null }
        assertSame("Back must retain the downloaded list", copies, vm.driveBackups.value)
        assertEquals("Returning to the list must keep its scroll position", listScrollY, sheetScroll())
        assertEquals(snapshot(990010), repo.getAllAppData())
        action(R.string.backup_drive_latest).performScrollTo().performClick()
        await { vm.restorePreview.value != null }
        assertEquals("Another preview starts at its source", 0f, sheetScroll())
        // A system Back from details returns to the same list as the explicit action.
        pressBack()
        await { vm.restorePreview.value == null }
        compose.onNodeWithContentDescription(context.getString(R.string.cancel)).assertIsDisplayed()
        assertSame(copies, vm.driveBackups.value)
        assertEquals(reads, drive.reads)
        assertEquals(queries, drive.queries)
        action(R.string.backup_drive_latest).performScrollTo().performClick()
        await { vm.restorePreview.value != null && vm.isRestoring.value == false }
        scenario.recreate()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertNotNull("Downloaded preview must survive recreation", vm.restorePreview.value)
        action(R.string.backup_back_to_copies).performClick()
        await { vm.restorePreview.value == null }
        assertSame("List lost across Activity recreation", copies, vm.driveBackups.value)
        action(R.string.backup_drive_latest).performScrollTo().performClick()
        action(R.string.backup_confirm_restore).performScrollTo().performClick()
        await { vm.isRestoring.value == false && repo.getAllAppData() == latest }
        assertEquals(0, drive.writes)
        scenario.onActivity { activity ->
            activity.onBackButtonClick()
            activity.onBackButtonClick()
        }
        lateinit var debts: DebtViewModel
        scenario.onActivity { debts = ViewModelProvider(current(it))[DebtViewModel::class.java] }
        await { debts.resultedHumanList.value == latest.humanList }
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
        await { drive.writes == 2 && vm.isSynchronizing.value == false }
        assertEquals(2, drive.files.size)
        assertEquals(latest, drive.files[SynchronizationViewModel.PREVIOUS_FILE_NAME])
        assertEquals(snapshot(990010), BackupCodec().decode(drive.files.getValue(SynchronizationViewModel.BACKUP_FILE_NAME)).data)
        assertEquals(snapshot(990010), repo.getAllAppData())
    }
    @Test fun signingOutLeavesLocalRecordsAndRequiresConfirmation() = withScreen { _, vm, drive ->
        val local = repo.getAllAppData()
        action(R.string.log_out).performScrollTo().performClick()
        closeSheet()
        compose.waitForIdle()
        assertTrue(vm.isAuthorized.value == true)
        action(R.string.log_out).performScrollTo().performClick()
        compose.onNode(hasText(context.getString(R.string.log_out)) and hasClickAction() and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick()
        await { vm.isAuthorized.value == false }
        assertNull(vm.driveServiceHelper.value)
        assertEquals(local, repo.getAllAppData())
        assertEquals(0, drive.writes)
    }

    @Test fun pendingWritePreventsSecondOperationAndBackUntilFinished() = withScreen { scenario, vm, drive ->
        val pending = TaskCompletionSource<Void?>()
        drive.pendingWrite = pending.task
        action(R.string.synchronize).performScrollTo().performClick()
        compose.onNode(hasText(context.getString(R.string.synchronize)) and hasClickAction() and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick()
        await { drive.writes == 2 && vm.isSynchronizing.value == true }
        action(R.string.synchronize).assertIsNotEnabled()
        scenario.onActivity {
            vm.uploadBackup()
            it.onBackPressedDispatcher.onBackPressed()
            assertEquals(R.id.synchronizationFragment, it.navController.currentDestination?.id)
        }
        assertEquals(2, drive.writes)
        pending.setResult(null)
        await { vm.isSynchronizing.value == false }
        scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
            assertEquals(R.id.settingsFragment, it.navController.currentDestination?.id)
        }
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
