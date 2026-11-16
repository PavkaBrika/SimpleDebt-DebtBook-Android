package com.breckneck.debtbook.settings

import android.content.Context
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
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
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = AppDataRepositoryImpl(context)
    private fun snapshot(id: Int) = AppDataLists(
        listOf(HumanDomain(id, "Drive fixture $id", BigDecimal("12.34"), "EUR")),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    private fun current(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment)
            .childFragmentManager.primaryNavigationFragment!!
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("Backup operation timed out")
            Thread.sleep(50)
        }
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
        var pendingQuery: Task<FileList>? = null
        override fun createFile(name: String): Task<String> {
            writes++; files[name] = ""; return Tasks.forResult(name)
        }
        override fun readFile(fileId: String?): Task<Pair<String, String>> = Tasks.forResult(fileId!! to files.getValue(fileId))
        override fun queryFiles(fileName: String): Task<FileList> = pendingQuery ?: Tasks.forResult(FileList().setFiles(
            if (files.containsKey(fileName)) listOf(com.google.api.services.drive.model.File().setId(fileName)) else emptyList()))
        override fun saveFile(fileId: String?, name: String?, content: String?): Task<Void?> {
            writes++; files[fileId!!] = content!!; return Tasks.forResult(null)
        }
    }
    private fun withScreen(block: (ActivityScenario<MainActivity>, SynchronizationViewModel, FakeDrive) -> Unit) {
        val original = repo.getAllAppData()
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
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
                scenario.onActivity { activity ->
                    activity.navController.navigate(R.id.settingsFragment)
                    activity.onAuthorizationButtonClick()
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
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
            prefs.edit().putBoolean("pin_code_is_enabled", pin).putBoolean("ads_disabled", ads).putBoolean("is_authorized", auth).putInt("debts_quantity_for_app_rate_dialog_show", rate).commit()
        }
    }
    @Test fun twoDatedCopiesPreviewCancelConfirmAndMainRefresh() = withScreen { scenario, vm, drive ->
        val latest = snapshot(990011); val previous = snapshot(990012)
        drive.files[SynchronizationViewModel.BACKUP_FILE_NAME] = BackupCodec().encode(latest)
        drive.files[SynchronizationViewModel.PREVIOUS_FILE_NAME] = BackupCodec().encode(previous)
        onView(withId(R.id.restoreButtonLayout)).perform(scrollTo(), click())
        await { vm.driveBackups.value?.size == 2 && vm.isRestoring.value == false }
        assertTrue(vm.driveBackups.value!!.all { it.preview?.createdAt != null })
        onView(withId(R.id.cancelButton)).inRoot(isDialog()).check(matches(isCompletelyDisplayed()))
        capture("fix-drive-copies.png")
        assertEquals(snapshot(990010), repo.getAllAppData())
        assertEquals(0, drive.writes)
        onView(withText(R.string.backup_drive_previous)).inRoot(isDialog()).perform(scrollTo(), click())
        await { vm.restorePreview.value != null && vm.isRestoring.value == false }
        assertEquals(SynchronizationViewModel.RestoreSource.DRIVE_PREVIOUS, vm.restorePreview.value!!.source)
        onView(withId(R.id.cancelButton)).inRoot(isDialog()).perform(click())
        assertEquals(snapshot(990010), repo.getAllAppData())
        onView(withId(R.id.restoreButtonLayout)).perform(scrollTo(), click())
        await { vm.driveBackups.value?.size == 2 && vm.isRestoring.value == false }
        onView(withText(R.string.backup_drive_latest)).inRoot(isDialog()).perform(scrollTo(), click())
        await { vm.restorePreview.value != null && vm.isRestoring.value == false }
        scenario.recreate()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertNotNull("Downloaded preview must survive recreation", vm.restorePreview.value)
        onView(withId(R.id.okButton)).inRoot(isDialog()).perform(click())
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
        onView(withId(R.id.synchronizeButtonLayout)).perform(scrollTo(), click())
        assertEquals(0, drive.writes)
        onView(withId(R.id.cancelButton)).inRoot(isDialog()).perform(click())
        assertEquals(0, drive.writes)
        onView(withId(R.id.synchronizeButtonLayout)).perform(scrollTo(), click())
        onView(withId(R.id.okButton)).inRoot(isDialog()).perform(click())
        await { drive.writes == 2 && vm.isSynchronizing.value == false }
        assertEquals(2, drive.files.size)
        assertEquals(latest, drive.files[SynchronizationViewModel.PREVIOUS_FILE_NAME])
        assertEquals(snapshot(990010), BackupCodec().decode(drive.files.getValue(SynchronizationViewModel.BACKUP_FILE_NAME)).data)
        assertEquals(snapshot(990010), repo.getAllAppData())
    }
    @Test fun emptyDriveAndDamagedLatestStillAllowTheValidEarlierCopy() = withScreen { _, vm, drive ->
        onView(withId(R.id.restoreButtonLayout)).perform(scrollTo(), click())
        await { vm.driveBackups.value != null && vm.isRestoring.value == false }
        assertTrue(vm.driveBackups.value!!.isEmpty())
        onView(withId(R.id.cancelButton)).inRoot(isDialog()).perform(click())
        drive.files[SynchronizationViewModel.BACKUP_FILE_NAME] = "{broken"
        drive.files[SynchronizationViewModel.PREVIOUS_FILE_NAME] = BackupCodec().encode(snapshot(990012))
        onView(withId(R.id.restoreButtonLayout)).perform(scrollTo(), click())
        await { vm.driveBackups.value?.size == 2 && vm.isRestoring.value == false }
        assertNull(vm.driveBackups.value!![0].preview)
        assertNotNull(vm.driveBackups.value!![1].preview)
        assertEquals(0, drive.writes)
        onView(withText(R.string.backup_drive_previous)).inRoot(isDialog()).perform(scrollTo(), click())
        await { vm.restorePreview.value != null && vm.isRestoring.value == false }
        onView(withId(R.id.cancelButton)).inRoot(isDialog()).perform(click())
        assertEquals(snapshot(990010), repo.getAllAppData())
    }
    @Test fun cancellingLoadingDoesNotReopenTheSheetOrChangeData() = withScreen { _, vm, drive ->
        val pending = TaskCompletionSource<FileList>()
        drive.pendingQuery = pending.task
        onView(withId(R.id.restoreButtonLayout)).perform(scrollTo(), click())
        await { vm.driveBackups.value != null && vm.isRestoring.value == true }
        onView(withId(R.id.cancelButton)).inRoot(isDialog()).perform(click())
        await { vm.driveBackups.value == null && vm.isRestoring.value == false }
        pending.setResult(FileList().setFiles(emptyList()))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertNull(vm.driveBackups.value)
        assertEquals(0, drive.writes)
        assertEquals(snapshot(990010), repo.getAllAppData())
    }

}
