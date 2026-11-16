package com.breckneck.debtbook.auth

import java.math.BigDecimal

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.util.CryptoManager
import com.breckneck.deptbook.domain.model.AppDataLists
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.CRYPTO_FILE_NAME
import org.junit.*
import org.junit.Assert.*
import repository.AppDataRepositoryImpl
import java.io.File

/** Disposable AVD only. Configure its device PIN and pass -e deviceCredential <PIN>. */
class PinRecoveryDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs get() = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val file get() = File(context.filesDir, CRYPTO_FILE_NAME)
    private val repository = AppDataRepositoryImpl(context)
    private lateinit var originalData: AppDataLists
    private lateinit var fixture: AppDataLists
    private var originalFile: ByteArray? = null
    private var originalPrefs: Map<String, *> = emptyMap<String, Any>()

    @Before fun prepare() {
        originalData = repository.getAllAppData()
        fixture = originalData.copy(
            humanList = originalData.humanList + HumanDomain(910001, "PIN recovery fixture", "12.34".toBigDecimal().stripTrailingZeros(), "EUR"),
            debtList = originalData.debtList + DebtDomain(910001, "12.34".toBigDecimal().stripTrailingZeros(), 910001, null, "15 Nov 2026"))
        repository.replaceAllAppData(fixture)
        originalFile = file.takeIf { it.exists() }?.readBytes()
        originalPrefs = prefs.all.toMap()
        prefs.edit().putBoolean("pin_code_is_enabled", true)
            .putBoolean("fingerprint_auth_is_enabled", false).putBoolean("ads_disabled", true)
            .putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).commit()
        PinCodeStore(context).save("1234")
        AuthSession.lock()
    }

    @After fun finish() {
        instrumentation.runOnMainSync {
            Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .distinct().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
        repository.replaceAllAppData(originalData)
        originalFile?.let(file::writeBytes) ?: file.delete()
        val edit = prefs.edit()
        listOf("pin_code_is_enabled", "fingerprint_auth_is_enabled", "ads_disabled").forEach { key ->
            if (key in originalPrefs) edit.putBoolean(key, originalPrefs[key] as Boolean) else edit.remove(key)
        }
        val rateKey = "debts_quantity_for_app_rate_dialog_show"
        if (rateKey in originalPrefs) edit.putInt(rateKey, originalPrefs[rateKey] as Int) else edit.remove(rateKey)
        edit.commit()
        AuthSession.lock()
    }

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun waitForText(resource: Int) {
        compose.waitUntil(15_000) {
            runCatching { compose.onAllNodesWithText(context.getString(resource)).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }
    private fun credentialField(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isEditable && node.isPassword) return true
        return (0 until node.childCount).any { credentialField(node.getChild(it)) }
    }
    private fun waitForSystemCredential() {
        val deadline = System.currentTimeMillis() + 15_000
        while (!credentialField(instrumentation.uiAutomation.rootInActiveWindow)) {
            if (System.currentTimeMillis() > deadline) fail("Android device credential field did not appear")
            Thread.sleep(100)
        }
    }
    private fun devicePin(): String {
        val pin = InstrumentationRegistry.getArguments().getString("deviceCredential").orEmpty()
        Assume.assumeTrue("Pass the disposable emulator device PIN", pin.length in 4..8 && pin.all { it in '0'..'9' })
        return pin
    }
    private fun openRecovery() {
        compose.onNodeWithText(context.getString(R.string.pin_recovery_title)).performClick()
        waitForText(R.string.pin_recovery_verify)
        compose.onNodeWithTag("pinRecoverySheet").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pin_recovery_verify)).performClick()
    }
    private fun enterAppPin(pin: String) {
        pin.forEach { compose.onNodeWithText(it.toString()).performClick() }
    }
    private fun capturePinLayout() {
        // Screenshot only: production keeps FLAG_SECURE throughout this flow.
        lateinit var activity: PinRecoveryActivity
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<PinRecoveryActivity>().single()
            assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        try {
            instrumentation.waitForIdleSync()
            Thread.sleep(150)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "fix-pin-layout.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        } finally {
            instrumentation.runOnMainSync { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }

    private fun assertUnchanged() {
        assertEquals("1234", file.inputStream().use { CryptoManager().decrypt(it).decodeToString() })
        assertEquals(fixture, repository.getAllAppData())
        assertTrue(prefs.getBoolean("pin_code_is_enabled", false))
        assertFalse(AuthSession.isUnlocked)
    }

    @Test fun recoverySheetCanBeDismissedWithoutChangingPinOrRecords() {
        ActivityScenario.launch(AuthorizationActivity::class.java).use {
            compose.onNodeWithText(context.getString(R.string.pin_recovery_title)).performClick()
            waitForText(R.string.pin_recovery_verify)
            compose.onNodeWithTag("pinRecoverySheet").assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
            waitForText(R.string.pin_recovery_title)
            assertUnchanged()
        }
    }

    @Test fun recoveryActivityCannotBeStartedExternally() {
        assertFalse(context.packageManager.getActivityInfo(ComponentName(context, PinRecoveryActivity::class.java), 0).exported)
    }

    @Test fun noDeviceLockDoesNotResetPinOrData() {
        Assume.assumeFalse(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        ActivityScenario.launch(AuthorizationActivity::class.java).use {
            openRecovery()
            waitForText(R.string.pin_recovery_unavailable)
            assertUnchanged()
        }
    }

    @Test fun cancelledSystemConfirmationDoesNotResetPinOrData() {
        devicePin()
        ActivityScenario.launch(AuthorizationActivity::class.java).use {
            openRecovery()
            waitForSystemCredential()
            shell("input keyevent KEYCODE_BACK")
            Thread.sleep(500)
            // On API 29 the first Back hides the system keyboard before leaving confirmation.
            if (credentialField(instrumentation.uiAutomation.rootInActiveWindow)) shell("input keyevent KEYCODE_BACK")
            waitForText(R.string.pin_recovery_cancelled)
            assertUnchanged()
        }
    }

    @Test fun systemCredentialThenNewPinTwiceKeepsAllRecords() {
        val pin = devicePin()
        // This flow finishes the launcher and starts Main. The common teardown closes
        // all surviving Activities; no ActivityScenario is kept for the finished launcher.
        instrumentation.startActivitySync(Intent(context, AuthorizationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        run {
            openRecovery()
            waitForSystemCredential()
            assertUnchanged()
            shell("input text $pin")
            shell("input keyevent KEYCODE_ENTER")
            waitForText(R.string.enter_new_pin)
            // Every key must be reachable without scrolling on a portrait phone.
            val keys = ('0'..'9').map {
                compose.onNode(hasText(it.toString()) and hasClickAction()).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            keys.forEach { key -> assertTrue("Key must retain a usable touch target: $key", key.height >= 48 * context.resources.displayMetrics.density) }
            for (i in keys.indices) for (j in 0 until i) {
                assertFalse("PIN keys overlap", keys[i].overlaps(keys[j]))
            }
            capturePinLayout()
            enterAppPin("5678")
            waitForText(R.string.confirm_your_pin)
            assertUnchanged()
            // The verified flow survives Activity recreation, but not process death.
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<PinRecoveryActivity>().single().recreate()
            }
            waitForText(R.string.confirm_your_pin)
            enterAppPin("5678")
            compose.waitUntil(10_000) { AuthSession.isUnlocked }
            assertEquals("5678", file.inputStream().use { CryptoManager().decrypt(it).decodeToString() })
            assertEquals(fixture, repository.getAllAppData())
            assertTrue(prefs.getBoolean("pin_code_is_enabled", false))
        }
    }
}
