package com.breckneck.debtbook.auth

import android.content.Context
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.core.widget.NestedScrollView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
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
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val file get() = File(context.filesDir, CRYPTO_FILE_NAME)
    private var originalFile: ByteArray? = null
    private var originalPrefs: Map<String, *> = emptyMap<String, Any>()

    @Before fun prepare() {
        originalPrefs = prefs.all.toMap()
        originalFile = file.takeIf { it.exists() }?.readBytes()
        prefs.edit().putBoolean("pin_code_is_enabled", true).putBoolean("ads_disabled", true)
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
        editor.commit()
        AuthSession.lock()
    }

    private fun openSecuritySettings() {
        val target = Rect()
        repeat(12) {
            var visible = false
            onView(withId(R.id.securityLayout)).check { view, _ ->
                visible = isDisplayingAtLeast(90).matches(view)
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                target.set(location[0], location[1], location[0] + view.width, location[1] + view.height)
            }
            if (visible) {
                onView(withId(R.id.securityLayout)).perform(click())
                return
            }
            // The collapsing header clips part of this scroll view. Swipe its visible
            // area, just as a user would, rather than requiring 90% of the whole view.
            val bounds = Rect()
            onView(isAssignableFrom(NestedScrollView::class.java)).check { view, _ ->
                assertTrue(view.getGlobalVisibleRect(bounds))
            }
            val from = bounds.centerY()
            val to = from + if (target.centerY() < bounds.top) bounds.height() / 3 else -bounds.height() / 3
            ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "input swipe ${bounds.centerX()} $from ${bounds.centerX()} $to 600"
                )
            ).bufferedReader().use { it.readText() }
            Thread.sleep(300) // wait for the final scroll animation before tapping a row
        }
        fail("PIN settings did not become visible after scrolling; last position: $target")
    }

    @Test fun settingPersistsAndIsShownAfterRecreation() {
        prefs.edit().remove("pin_lock_delay_millis").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.navController.navigate(R.id.settingsFragment)
            }
            openSecuritySettings()
            onView(withId(R.id.pinLockDelayText)).check(matches(withText(R.string.pin_lock_immediately)))
            onView(withId(R.id.pinLockDelayLayout)).perform(click())
            onView(withText(R.string.pin_lock_1_minute)).perform(click())
            assertEquals(60_000L, SharedPrefsSettingsStorageImpl(context).getPinLockDelayMillis())
            scenario.recreate()
            openSecuritySettings()
            onView(withId(R.id.pinLockDelayText)).check(matches(withText(R.string.pin_lock_1_minute)))
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
}
