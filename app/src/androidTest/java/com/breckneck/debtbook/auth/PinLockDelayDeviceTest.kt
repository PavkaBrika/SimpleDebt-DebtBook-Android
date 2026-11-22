package com.breckneck.debtbook.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Rule
import com.breckneck.debtbook.settings.edgeSwipe
import com.breckneck.debtbook.settings.requirePredictiveGestures
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.deptbook.domain.util.CRYPTO_FILE_NAME
import org.junit.*
import org.junit.Assert.*
import sharedprefs.SharedPrefsSettingsStorageImpl
import java.io.File

class PinLockDelayDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun action(id: Int) = awaitNode(hasText(context.getString(id)) and hasClickAction())

    private fun awaitNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.waitUntil(10_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        return compose.onNode(matcher)
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val file get() = File(context.filesDir, CRYPTO_FILE_NAME)
    private var originalFile: ByteArray? = null
    private var originalPrefs: Map<String, *> = emptyMap<String, Any>()

    @Before fun prepare() {
        originalPrefs = prefs.all.toMap()
        originalFile = file.takeIf { it.exists() }?.readBytes()
        prefs.edit().putBoolean("pin_code_is_enabled", true).putBoolean("ads_disabled", true)
            .putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE)
            .putBoolean("fingerprint_auth_is_enabled", false).putLong("pin_lock_delay_millis", 60_000L).commit()
        PinCodeStore(context).save("1234")
        AuthSession.unlock()
    }

    @After fun finish() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .distinct().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
        originalFile?.let(file::writeBytes) ?: file.delete()
        val editor = prefs.edit()
        listOf("pin_code_is_enabled", "fingerprint_auth_is_enabled", "ads_disabled").forEach { key ->
            if (key in originalPrefs) editor.putBoolean(key, originalPrefs[key] as Boolean) else editor.remove(key)
        }
        (originalPrefs["pin_lock_delay_millis"] as? Long)?.let { editor.putLong("pin_lock_delay_millis", it) }
            ?: editor.remove("pin_lock_delay_millis")
        val rateKey = "debts_quantity_for_app_rate_dialog_show"
        if (rateKey in originalPrefs) editor.putInt(rateKey, originalPrefs[rateKey] as Int) else editor.remove(rateKey)
        editor.commit()
        AuthSession.lock()
    }

    @Test fun settingPersistsAndIsShownAfterRecreation() {
        prefs.edit().remove("pin_lock_delay_millis").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProtection()
            scenario.onActivity { assertEquals(R.id.appSecurityFragment, it.navController.currentDestination?.id) }
            action(R.string.pin_lock_immediately).assertIsDisplayed()
            action(R.string.pin_lock_delay_title).performClick()
            compose.onNode(hasText(context.getString(R.string.pin_lock_immediately)) and isSelected()).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.pin_lock_delay_explanation)).assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()
            action(R.string.pin_lock_immediately).assertIsDisplayed()
            assertEquals(0L, SharedPrefsSettingsStorageImpl(context).getPinLockDelayMillis())
            action(R.string.pin_lock_delay_title).performClick()
            action(R.string.pin_lock_1_minute).performClick()
            compose.waitUntil(5000) { SharedPrefsSettingsStorageImpl(context).getPinLockDelayMillis() == 60_000L }
            assertEquals(60_000L, SharedPrefsSettingsStorageImpl(context).getPinLockDelayMillis())
            compose.onNodeWithText(context.getString(R.string.pin_lock_delay_explanation)).assertDoesNotExist()
            scenario.recreate()
            action(R.string.pin_lock_1_minute).assertIsDisplayed()
        }
    }

    @Test fun shortBackgroundAndLauncherReturnHonorGracePeriod() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(AuthSession.isUnlocked)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertEquals(View.VISIBLE, it.window.decorView.visibility) }
        }
        // Main's close is a background event; launcher must use the same timeout.
        ActivityScenario.launch(AuthorizationActivity::class.java).use {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(AuthSession.canAccess(true, SystemClock.elapsedRealtime(), 60_000))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it is MainActivity })
            }
        }
    }

    @Test fun settingsCanSetChangeAndDisablePin() {
        prefs.edit().putBoolean("pin_code_is_enabled", false).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProtection()
            action(R.string.enable_pin).performClick()
            scenario.onActivity { assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
            enter("2468")
            enter("2468")
            action(R.string.change_pin_code).performClick()
            enter("2468")
            enter("5678")
            action(R.string.disable_pin).performClick()
            enter("5678")
            action(R.string.enable_pin).assertIsDisplayed()
            assertFalse(prefs.getBoolean("pin_code_is_enabled", true))
            scenario.onActivity { assertEquals(0, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }

    @Test fun backingOutOfChangeKeepsThePinAndUnlockedSession() {
        prefs.edit().putLong("pin_lock_delay_millis", 0).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProtection()

            action(R.string.change_pin_code).performClick()
            awaitNode(hasText(context.getString(R.string.enter_pin))).assertIsDisplayed()
            pressBack()
            action(R.string.change_pin_code).assertIsDisplayed()

            action(R.string.change_pin_code).performClick()
            enter("1234")
            awaitNode(hasText(context.getString(R.string.enter_new_pin))).assertIsDisplayed()
            enter("78")
            compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            action(R.string.change_pin_code).assertIsDisplayed()

            scenario.onActivity { assertEquals(R.id.appSecurityFragment, it.navController.currentDestination?.id) }
            assertTrue(AuthSession.isUnlocked)
            assertEquals("1234", PinCodeStore(context).read(""))
            compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            action(R.string.pin_code_lock).assertIsDisplayed()
        }
    }

    @Test fun backgroundingDuringPinChangeStillLocksTheApp() {
        prefs.edit().putLong("pin_lock_delay_millis", 0).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProtection()
            action(R.string.change_pin_code).performClick()
            enter("1234")
            awaitNode(hasText(context.getString(R.string.enter_new_pin))).assertIsDisplayed()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            action(R.string.pin_recovery_title).assertIsDisplayed()
            assertFalse(AuthSession.isUnlocked)
            compose.onNodeWithText(context.getString(R.string.enter_new_pin)).assertIsNotDisplayed()

            enter("1234")
            awaitNode(hasText(context.getString(R.string.enter_new_pin))).assertIsDisplayed()
            pressBack()
            action(R.string.change_pin_code).assertIsDisplayed()
            assertTrue(AuthSession.isUnlocked)
        }
    }

    @Test fun cancelledEdgeSwipeFromLockedEntryKeepsContentHidden() {
        requirePredictiveGestures(context)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        prefs.edit().putLong("pin_lock_delay_millis", 0).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            action(R.string.settings).performClick()
            lateinit var main: MainActivity
            scenario.onActivity { main = it }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            action(R.string.pin_recovery_title).assertExists()
            instrumentation.edgeSwipe(cancel = true) {
                instrumentation.runOnMainSync {
                    assertFalse(main.window.decorView.isShown)
                    assertFalse(AuthSession.isUnlocked)
                }
            }
            action(R.string.pin_recovery_title).assertIsDisplayed()
            assertFalse(AuthSession.isUnlocked)
        }
    }

    @Test fun expiredGracePeriodShowsPinAgain() {
        prefs.edit().putLong("pin_lock_delay_millis", 30_000L).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(30_100) // real monotonic time; verifies the Activity wiring, not just the policy
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(AuthSession.isUnlocked)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it is AuthorizationActivity })
            }
        }
    }

    private fun openProtection() {
        action(R.string.settings).performClick()
        action(R.string.pin_code_lock).performScrollTo()
        // The fixed settings tabs can overlay the target on a short screen.
        awaitNode(hasScrollAction()).performTouchInput { swipeUp(startY = height * .72f, endY = height * .42f) }
        action(R.string.pin_code_lock).performScrollTo().performClick()
    }

    private fun enter(pin: String) {
        for (digit in pin) awaitNode(hasText(digit.toString()) and hasClickAction()).performClick()
    }
}
