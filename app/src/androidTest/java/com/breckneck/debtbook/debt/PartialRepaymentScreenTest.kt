package com.breckneck.debtbook.debt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.debt.details.*
import database.AppDataBase
import entity.Debt
import entity.Human
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/** Run only on a disposable emulator. No account, ads or network is involved. */
class PartialRepaymentScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = AppDataBase(context)
    private val dao get() = db.appDao()
    private val prefs get() = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val humanId = 900030
    private val originalId = 900030
    private var oldPin = false
    private var oldAds = false
    private var oldTheme: String? = null

    @Before fun setup() {
        oldPin = prefs.getBoolean("pin_code_is_enabled", false)
        oldAds = prefs.getBoolean("ads_disabled", false)
        oldTheme = prefs.getString("app_theme", null)
        val dark = InstrumentationRegistry.getArguments().getString("r2Theme") == "dark"
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true)
            .putString("app_theme", context.getString(if (dark) R.string.dark_theme else R.string.light_theme)).commit()
        cleanup()
    }
    @After fun teardown() {
        cleanup()
        prefs.edit().putBoolean("pin_code_is_enabled", oldPin).putBoolean("ads_disabled", oldAds).putString("app_theme", oldTheme).commit()
    }
    private fun cleanup() {
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS r2_fail_balance")
        dao.deleteDebtsByHumanId(humanId)
        dao.deleteHumanById(humanId)
    }
    private fun seed(sign: Int) {
        dao.insertHuman(Human(humanId, "R2 test", BigDecimal.ZERO, "EUR"))
        dao.saveDebt(Debt(originalId, (100 * sign).toBigDecimal(), humanId, "Original loan", "2026-11-16"))
    }
    private fun details(activity: MainActivity) =
        ((activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment)
            .childFragmentManager.primaryNavigationFragment as DebtDetailsFragment)
    private fun await(message: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) { if (System.currentTimeMillis() > end) fail(message); Thread.sleep(50) }
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) {
        await("history did not load") {
            var ready = false
            val count = dao.getAllDebtsById(humanId).size
            scenario.onActivity {
                val vm = ViewModelProvider(details(it))[DebtDetailsViewModel::class.java]
                ready = vm.screenState.value in listOf(DebtDetailsState.SUCCESS, DebtDetailsState.EMPTY) &&
                    vm.isDeleting.value != true && vm.history.value?.size == count
            }
            ready
        }
    }
    private fun open(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.onHumanClick(humanId, "EUR", "R2 test") }
        ready(scenario)
    }
    private fun scrollToRecord(scenario: ActivityScenario<MainActivity>, id: Int) {
        scenario.onActivity {
            val fragment = details(it)
            val position = ViewModelProvider(fragment)[DebtDetailsViewModel::class.java].visibleDebtRows.value!!.indexOfFirst { row -> row.id == id }
            check(position >= 0) { "Missing row $id in the current debt section" }
            fragment.requireView().findViewById<RecyclerView>(R.id.debtsRecyclerView).scrollToPosition(position)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    private fun openPayment(scenario: ActivityScenario<MainActivity>) {
        scrollToRecord(scenario, originalId)
        onView(withTagValue(equalTo(originalId))).perform(click())
        compose.onNodeWithTag("repaymentButton").performClick()
        compose.onNodeWithTag("repaymentAmountInput").assertIsDisplayed()
    }

    private fun saveTwice() {
        val click = checkNotNull(compose.onNodeWithTag("repaymentSaveButton")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        compose.runOnIdle {
            click()
            click()
        }
    }

    private fun assertAmount(expected: Int) = assertEquals(0, expected.toBigDecimal().compareTo(dao.getHumanSumDebt(humanId)))
    private fun screenshot(label: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "r2-$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun receiveAndRepayMultiplePaymentsCloseAndCancelFromHistory() {
        for (sign in listOf(1, -1)) {
            seed(sign)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                open(scenario)
                onView(withTagValue(equalTo(originalId))).perform(click())
                compose.onNodeWithTag("repaymentButton")
                    .assertTextEquals(context.getString(if (sign > 0) R.string.repayment_received else R.string.repayment_paid))
                    .performClick()
                compose.onNodeWithTag("repaymentAmountInput").performTextReplacement("101")
                compose.onNodeWithTag("repaymentSaveButton").performClick()
                compose.onNodeWithText(context.getString(R.string.repayment_over, "100", "EUR")).assertIsDisplayed()
                assertAmount(100 * sign)
                compose.onNodeWithTag("repaymentAmountInput").performTextReplacement("20")
                scenario.recreate()
                compose.onNodeWithTag("repaymentAmountInput").assertTextContains("20")
                saveTwice()
                await("partial payment not stored") { dao.getAllDebtsById(humanId).size == 2 }
                ready(scenario)
                assertAmount(80 * sign)
                assertEquals(0, (100 * sign).toBigDecimal().compareTo(dao.getAllDebtsById(humanId).first { it.id == originalId }.sum))
                screenshot("partial-$sign")
                openPayment(scenario)
                compose.onNodeWithTag("repaymentFullButton").performClick()
                compose.onNodeWithTag("repaymentAmountInput").assertTextContains("80")
                saveTwice()
                await("full payment not stored") { dao.getAllDebtsById(humanId).size == 3 }
                ready(scenario)
                assertAmount(0)
                scenario.recreate()
                ready(scenario)
                assertAmount(0)
                val last = dao.getAllDebtsById(humanId).maxBy { it.id }
                onView(withId(R.id.settledDebtsTab)).perform(click())
                scrollToRecord(scenario, last.id)
                onView(withTagValue(equalTo(last.id))).perform(click())
                compose.onNodeWithTag("dueSettingsButton").assertDoesNotExist()
                compose.onNodeWithTag("deleteButton").performClick()
                compose.onNode(hasText(context.getString(R.string.repayment_cancel)) and hasClickAction()).performClick()
                await("cancellation not stored") { dao.getAllDebtsById(humanId).any { it.id == last.id && it.cancelled } }
                ready(scenario)
                assertAmount(80 * sign)
                assertEquals(3, dao.getAllDebtsById(humanId).size)
                onView(withId(R.id.openDebtsTab)).perform(click())
                scrollToRecord(scenario, originalId)
                screenshot("cancelled-$sign")
            }
            cleanup()
        }
    }

    @Test fun failedSaveKeepsInputAndRetryWritesOnce() {
        seed(1)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario)
            openPayment(scenario)
            compose.onNodeWithTag("repaymentAmountInput").performTextReplacement("20")
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER r2_fail_balance BEFORE UPDATE ON Human BEGIN SELECT RAISE(ABORT, 'test save failure'); END")
            saveTwice()
            await("save failure did not reach the form") {
                var failed = false
                scenario.onActivity { failed = ViewModelProvider(details(it))[DebtDetailsViewModel::class.java].repaymentError.value == RepaymentError.SAVE_FAILED }
                failed
            }
            compose.onNodeWithText(context.getString(R.string.repayment_save_failed)).assertIsDisplayed()
            assertEquals(1, dao.getAllDebtsById(humanId).size)
            assertAmount(100)
            compose.onNodeWithTag("repaymentAmountInput").assertTextContains("20")
            scenario.recreate()
            compose.onNodeWithTag("repaymentAmountInput").assertTextContains("20")
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER r2_fail_balance")
            saveTwice()
            await("retry not stored") { dao.getAllDebtsById(humanId).size == 2 }
            ready(scenario)
            assertAmount(80)
        }
    }

    @Test fun repaymentAmountAndSaveStayAboveKeyboard() {
        seed(1)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario)
            openPayment(scenario)
            compose.onNodeWithTag("repaymentAmountInput").performTouchInput { click() }
            compose.onNodeWithTag("repaymentAmountInput").assertIsFocused().performTextReplacement("20")
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            data class ScreenBounds(val keyboard: Rect, val amount: Rect, val save: Rect)
            var bounds: ScreenBounds? = null
            var stableFrames = 0
            await("repayment keyboard and form did not become visible") {
                val windows = automation.windows
                val keyboard = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                    ?.let { Rect().also(it::getBoundsInScreen).takeUnless(Rect::isEmpty) }
                val controls = windows.asSequence().filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.mapNotNull { window ->
                    val root = window.root ?: return@mapNotNull null
                    fun boundsOf(node: AccessibilityNodeInfo, tag: String): Rect? {
                        if (node.viewIdResourceName == tag && node.isVisibleToUser) {
                            return Rect().also(node::getBoundsInScreen)
                        }
                        return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let { child -> boundsOf(child, tag) } }
                    }
                    val amount = boundsOf(root, "repaymentAmountInput") ?: return@mapNotNull null
                    val save = boundsOf(root, "repaymentSaveButton") ?: return@mapNotNull null
                    amount to save
                }.firstOrNull()
                val current = if (keyboard != null && controls != null) {
                    ScreenBounds(keyboard, controls.first, controls.second)
                } else null
                stableFrames = if (current != null && bounds == current) stableFrames + 1 else 0
                bounds = current
                stableFrames >= 5
            }
            screenshot("keyboard")
            val visible = checkNotNull(bounds)
            for (control in listOf(visible.amount, visible.save)) {
                assertFalse(control.isEmpty)
                assertTrue("Repayment control ends at ${control.bottom}, below IME top ${visible.keyboard.top}",
                    control.bottom <= visible.keyboard.top)
            }
            val downTime = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val tap = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    visible.save.centerX().toFloat(), visible.save.centerY().toFloat(), 0)
                try {
                    tap.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(automation.injectInputEvent(tap, true))
                } finally {
                    tap.recycle()
                }
            }
            await("keyboard save did not persist") { dao.getAllDebtsById(humanId).size == 2 }
            ready(scenario)
            assertAmount(80)
        }
    }

}
