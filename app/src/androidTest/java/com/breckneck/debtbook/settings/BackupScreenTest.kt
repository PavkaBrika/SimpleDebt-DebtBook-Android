package com.breckneck.debtbook.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.settings.synchronization.SynchronizationViewModel
import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import repository.AppDataRepositoryImpl
import java.io.File
import java.math.BigDecimal

class BackupScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
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
    @Test fun offlinePreviewCancelRecreateConfirmAndUndo() {
        val repo = AppDataRepositoryImpl(context); val original = repo.getAllAppData()
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
        val oldTheme = prefs.getString("app_theme", null)
        val dark = InstrumentationRegistry.getArguments().getString("r1bTheme") == "dark"
        prefs.edit().putString("app_theme", context.getString(if (dark) R.string.dark_theme else R.string.light_theme)).commit()
        val oldPin = prefs.getBoolean("pin_code_is_enabled", false)
        val oldAds = prefs.getBoolean("ads_disabled", false)
        val oldAuthorization = prefs.getBoolean("is_authorized", false)
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true).putBoolean("is_authorized", false).commit()
        val a = snapshot(900025, "0.1"); val b = snapshot(900026, "999999999999999999.99")
        val file = File(context.cacheDir, "r1b-ui-import.json").apply { writeText(BackupCodec().encode(b)) }
        try {
            repo.replaceAllAppData(a)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment
                    host.navController.navigate(R.id.settingsFragment)
                    activity.onAuthorizationButtonClick()
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                lateinit var vm: SynchronizationViewModel
                scenario.onActivity { activity ->
                    val fragment = current(activity)
                    assertTrue(fragment.requireView().findViewById<Button>(R.id.importBackupFileButton).isEnabled)
                    assertTrue(fragment.requireView().findViewById<Button>(R.id.exportBackupFileButton).isEnabled)
                    vm = ViewModelProvider(fragment)[SynchronizationViewModel::class.java]
                    vm.restoreFile(Uri.fromFile(file))
                }
                await { vm.restorePreview.value != null && vm.isRestoring.value == false }
                assertEquals(a, repo.getAllAppData())
                onView(withId(R.id.cancelButton)).inRoot(isDialog()).perform(click())
                assertEquals(a, repo.getAllAppData())
                scenario.onActivity { vm.restoreFile(Uri.fromFile(file)) }
                await { vm.restorePreview.value != null && vm.isRestoring.value == false }
                scenario.recreate()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val recreated = ViewModelProvider(current(activity))[SynchronizationViewModel::class.java]
                    assertSame("Preview ViewModel changed after recreation", vm, recreated)
                    assertNotNull("Preview lost after recreation", vm.restorePreview.value)
                }
                onView(withId(R.id.okButton)).inRoot(isDialog()).perform(click())
                await(details = { "preview=${vm.restorePreview.value}, busy=${vm.isRestoring.value}, error=${vm.operationError.value}, IDs=${repo.getAllAppData().humanList.map { it.id }}" }) {
                    vm.operationError.value?.let { throw AssertionError("Restore failed", it) }
                    repo.getAllAppData().humanList.firstOrNull()?.id == 900026
                }
                scenario.onActivity { activity ->
                    current(activity).requireView().findViewById<Button>(R.id.previousLocalBackupButton).performClick()
                }
                await { vm.restorePreview.value != null && vm.isRestoring.value == false }
                onView(withId(R.id.okButton)).inRoot(isDialog()).perform(click())
                await { repo.getAllAppData().humanList.firstOrNull()?.id == 900025 }
                assertEquals(a, repo.getAllAppData())
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(context.getExternalFilesDir(null), "r1b-backup-screen.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            repo.replaceAllAppData(original); file.delete()
            prefs.edit().putString("app_theme", oldTheme).putBoolean("pin_code_is_enabled", oldPin).putBoolean("ads_disabled", oldAds).putBoolean("is_authorized", oldAuthorization).commit()
        }
    }
}
