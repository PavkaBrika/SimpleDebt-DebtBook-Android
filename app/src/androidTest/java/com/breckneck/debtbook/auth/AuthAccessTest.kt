package com.breckneck.debtbook.auth

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import com.breckneck.debtbook.debt.awaitTag
import com.breckneck.deptbook.domain.util.CRYPTO_FILE_NAME
import database.AppDataBase
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
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
            .putBoolean("fingerprint_auth_is_enabled", false).putLong("pin_lock_delay_millis", 0L).commit()
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
        (originalPrefs["pin_lock_delay_millis"] as? Long)?.let { editor.putLong("pin_lock_delay_millis", it) }
            ?: editor.remove("pin_lock_delay_millis")
        editor.commit()
        originalFile?.let(pinFile::writeBytes) ?: pinFile.delete()
        AuthSession.lock()
    }

    @Test fun mainIsNotExported() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertFalse(info.exported)
    }

    @Test fun longPressDoesNotDisablePinAndValidPinStillUnlocks() {
        ActivityScenario.launch(AuthorizationActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(it.window.attributes.flags and FLAG_SECURE != 0) }
            compose.onNodeWithContentDescription(context.getString(R.string.app_icon))
                .performTouchInput { longClick() }
            compose.onNodeWithText(context.getString(R.string.enter_pin)).assertExists()
            assertTrue(prefs.getBoolean("pin_code_is_enabled", false))
            assertFalse(AuthSession.isUnlocked)
            "1234".forEach { digit -> compose.onNodeWithText(digit.toString()).performClick() }
            compose.waitUntil(5_000) { AuthSession.isUnlocked }
            compose.onNodeWithText(context.getString(R.string.settings)).assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val main = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                assertEquals(0, main.window.attributes.flags and FLAG_SECURE)
            }
        }
    }

    @Test fun unlockKeepsTheKeypadAtTheBottomAndExitAboveThePrompt() {
        ActivityScenario.launch(AuthorizationActivity::class.java).use {
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val prompt = compose.onNodeWithText(context.getString(R.string.enter_pin))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val exit = compose.onNodeWithText(context.getString(R.string.exit))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val zero = compose.onNodeWithText("0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("PIN prompt should be in the top half", prompt.center.y < root.height / 2)
            assertTrue("Exit should be above the PIN prompt", exit.bottom < prompt.top)
            assertTrue("The last keypad row should be near the bottom", zero.bottom > root.height * .86f)
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

    @Test fun choosingSystemContactReturnsToUnlockedDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = InstrumentationRegistry.getArguments().getString("debtContactFixture")
        assumeTrue("Seed a contact on a disposable emulator", !fixture.isNullOrBlank())
        val contact = requireNotNull(fixture)
        val pickerTap = InstrumentationRegistry.getArguments().getString("debtContactTap")
            ?.split(',')?.map(String::toInt)?.also { require(it.size == 2) }
        instrumentation.runOnMainSync { AuthSession.unlock() }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTag("addHumanButton").performClick()
            compose.awaitTag("debtSumEditText").performTextReplacement("42")
            compose.awaitTag("pickDebtContact").performClick()
            val automation = instrumentation.uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand(command),
            ).bufferedReader().use { it.readText() }
            var chosen: AccessibilityNodeInfo? = null
            compose.waitUntil(12_000) {
                if (pickerTap != null) return@waitUntil shell("dumpsys window").lineSequence().any {
                    "mCurrentFocus=" in it && ("com.google.android.contacts/" in it || "com.android.contacts/" in it)
                }
                val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
                chosen = roots.asSequence().filter { it.packageName != context.packageName }
                    .flatMap { it.findAccessibilityNodeInfosByText(contact).orEmpty().asSequence() }
                    .firstOrNull { it.text?.toString() == contact || it.contentDescription?.toString() == contact }
                chosen != null
            }
            val coordinates = pickerTap ?: Rect().also(requireNotNull(chosen)::getBoundsInScreen).let {
                listOf(it.centerX(), it.centerY())
            }
            if (pickerTap != null) {
                // Window focus can arrive before the system picker renders the seeded contact.
                android.os.SystemClock.sleep(1200)
                val screenshot = automation.takeScreenshot()
                File(context.getExternalFilesDir(null), "debt-pin-contact-picker.png").outputStream().use {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
            shell("input tap ${coordinates[0]} ${coordinates[1]}")
            compose.awaitTag("humanNameEditText").assertTextContains(contact)
            compose.awaitTag("debtSumEditText").assertTextContains("42")
            compose.onNodeWithText(context.getString(R.string.enter_pin)).assertDoesNotExist()
            assertTrue(AuthSession.isUnlocked)
        }
    }

    @Test fun choosingSystemPhotoReturnsToUnlockedDraftAndPerson() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Seed a photo on a disposable emulator", InstrumentationRegistry.getArguments().getString("debtPhotoFixture") == "true")
        val dao = AppDataBase(context).appDao()
        val name = "Photo-PIN-${System.nanoTime().toString(36)}"
        fun person() = dao.getAllHuman().firstOrNull { it.name == name }
        val choose = context.getString(R.string.debt_choose_photo)
        val replace = context.getString(R.string.debt_replace_photo)
        val remove = context.getString(R.string.debt_remove_photo)
        val manage = context.getString(R.string.debt_manage)

        try {
            instrumentation.runOnMainSync { AuthSession.unlock() }
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.awaitTag("addHumanButton").performClick()
                compose.awaitTag("debtSumEditText").performTextReplacement("42")
                compose.awaitTag("humanNameEditText").performTextReplacement(name)
                compose.awaitTag("pickDebtPhoto").performScrollTo().performClick()
                selectFixturePhoto()
                compose.waitUntil(12_000) { compose.onAllNodesWithText(replace).fetchSemanticsNodes(false).isNotEmpty() }
                compose.awaitTag("humanNameEditText").assertTextContains(name)
                compose.awaitTag("debtSumEditText").assertTextContains("42")
                compose.onNodeWithText(context.getString(R.string.enter_pin)).assertDoesNotExist()
                assertTrue(AuthSession.isUnlocked)

                compose.awaitTag("setFinanceButton").performClick()
                compose.awaitTag("personHistory")
                assertNotNull(person()?.photo)
                compose.onNodeWithContentDescription(manage).performClick()
                compose.onNodeWithText(context.getString(R.string.edit)).performClick()
                compose.onNodeWithText(remove).performClick()
                assertNotNull(person()?.photo)
                compose.awaitTag("saveDebtPerson").performClick()
                compose.waitUntil(5_000) { person()?.photo == null }
                compose.awaitTag("personHistory")
                compose.onNodeWithContentDescription(manage).performClick()
                compose.onNodeWithText(context.getString(R.string.edit)).performClick()
                compose.onNodeWithText(choose).performClick()
                selectFixturePhoto()
                compose.waitUntil(12_000) { compose.onAllNodesWithText(replace).fetchSemanticsNodes(false).isNotEmpty() }
                assertNull(person()?.photo)
                compose.onNodeWithText(context.getString(R.string.enter_pin)).assertDoesNotExist()
                compose.awaitTag("saveDebtPerson").performClick()
                compose.waitUntil(12_000) { person()?.photo != null }
                compose.awaitTag("personHistory").assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.enter_pin)).assertDoesNotExist()
                assertTrue(AuthSession.isUnlocked)
            }
        } finally {
            person()?.let {
                dao.deleteDebtsByHumanId(it.id)
                dao.deleteHumanById(it.id)
            }
        }
    }

    private fun selectFixturePhoto() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        fun nodes(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
            yield(root)
            for (index in 0 until root.childCount) root.getChild(index)?.let { yieldAll(nodes(it)) }
        }
        fun pickerNodes() = (listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root })
            .asSequence().filter { it.packageName != context.packageName }.flatMap(::nodes)
        fun findThumbnail() = pickerNodes().firstOrNull {
            it.viewIdResourceName == "com.android.documentsui:id/icon_thumb" ||
                it.contentDescription?.startsWith("Photo taken on") == true
        }
        fun tap(node: AccessibilityNodeInfo) {
            val bounds = Rect().also(node::getBoundsInScreen)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}"),
            ).use { it.readBytes() }
        }
        compose.waitUntil(12_000) { findThumbnail() != null }
        automation.waitForIdle(350, 5_000)
        val thumbnail = requireNotNull(findThumbnail())
        if (thumbnail.packageName == "com.google.android.photopicker") {
            fun done() = pickerNodes().firstOrNull { it.text?.toString() == "Done" }
            var lastTap = 0L
            compose.waitUntil(8_000) {
                if (done() != null) return@waitUntil true
                val now = SystemClock.elapsedRealtime()
                // The picker can expose its thumbnail while its window is still opening.
                if (now - lastTap >= 750) findThumbnail()?.let { tap(it); lastTap = now }
                false
            }
            tap(requireNotNull(done()))
        } else {
            tap(thumbnail)
        }
    }
}
