package com.breckneck.debtbook.auth

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.util.CryptoManager
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.deptbook.domain.util.CRYPTO_FILE_NAME
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Run only on a disposable test installation; fixtures preserve the previous auth preferences. */
class AuthAccessTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val pinFile get() = File(context.filesDir, CRYPTO_FILE_NAME)
    private var originalFile: ByteArray? = null
    private var originalPrefs: Map<String, *> = emptyMap<String, Any>()

    @Before fun setUp() {
        originalPrefs = prefs.all.toMap()
        originalFile = pinFile.takeIf(File::exists)?.readBytes()
        AuthSession.lock()
        prefs.edit().putBoolean("pin_code_is_enabled", true)
            .putBoolean("fingerprint_auth_is_enabled", false).commit()
        pinFile.outputStream().use { CryptoManager().encrypt("1234".encodeToByteArray(), it) }
    }

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Stage.values().flatMap {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
            }.distinct().forEach { it.finish() }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val editor = prefs.edit()
        listOf("pin_code_is_enabled", "fingerprint_auth_is_enabled").forEach { key ->
            if (key in originalPrefs) editor.putBoolean(key, originalPrefs[key] as Boolean)
            else editor.remove(key)
        }
        (originalPrefs["pin_code"] as? String)?.let { editor.putString("pin_code", it) }
            ?: editor.remove("pin_code")
        editor.commit()
        originalFile?.let(pinFile::writeBytes) ?: pinFile.delete()
        AuthSession.lock()
    }

    @Test fun mainIsNotExported() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertFalse(info.exported)
    }

    @Test fun longPressDoesNotDisablePinAndValidPinStillUnlocks() {
        ActivityScenario.launch(AuthorizationActivity::class.java).use {
            compose.onNodeWithContentDescription(context.getString(R.string.app_icon))
                .performTouchInput { longClick() }
            compose.onNodeWithText(context.getString(R.string.enter_pin)).assertExists()
            assertTrue(prefs.getBoolean("pin_code_is_enabled", false))
            assertFalse(AuthSession.isUnlocked)
            "1234".forEach { digit -> compose.onNodeWithText(digit.toString()).performClick() }
            compose.waitUntil(5_000) { AuthSession.isUnlocked }
        }
    }

    @Test fun untrustedEnableRequestCannotReplaceExistingPin() {
        val intent = Intent(context, AuthorizationActivity::class.java).putExtra("PINCodeState", "ENABLE")
        ActivityScenario.launch<AuthorizationActivity>(intent).use {
            "9999".forEach { digit -> compose.onNodeWithText(digit.toString()).performClick() }
            compose.onNodeWithText(context.getString(R.string.pin_code_is_incorrect)).assertExists()
            assertFalse(AuthSession.isUnlocked)
            assertTrue(prefs.getBoolean("pin_code_is_enabled", false))
            assertEquals("1234", pinFile.inputStream().use { CryptoManager().decrypt(it).decodeToString() })
        }
    }

    @Test fun missingPinFileFailsClosed() {
        pinFile.delete()
        prefs.edit().remove("pin_code").commit()
        ActivityScenario.launch(AuthorizationActivity::class.java).use {
            compose.onNodeWithText(context.getString(R.string.pin_unavailable)).assertExists()
            assertTrue(prefs.getBoolean("pin_code_is_enabled", false))
            assertFalse(AuthSession.isUnlocked)
        }
    }

    @Test fun corruptedPinFileFailsClosed() {
        pinFile.writeBytes(byteArrayOf(127, 1, 2))
        ActivityScenario.launch(AuthorizationActivity::class.java).use {
            compose.onNodeWithText(context.getString(R.string.pin_unavailable)).assertExists()
            assertTrue(prefs.getBoolean("pin_code_is_enabled", false))
            assertFalse(AuthSession.isUnlocked)
        }
    }

    @Test fun mainWithoutSessionShowsAuthentication() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText(context.getString(R.string.enter_pin)).assertExists()
            assertFalse(AuthSession.isUnlocked)
        }
    }

    @Test fun returningFromBackgroundRequiresAuthentication() {
        AuthSession.unlock()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(AuthSession.isUnlocked)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText(context.getString(R.string.enter_pin)).assertExists()
        }
    }
}
