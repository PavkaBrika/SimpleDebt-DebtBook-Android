package com.breckneck.debtbook.debt

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.EditText
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
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
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/** Run only on a disposable emulator. No account, ads or network is involved. */
class PartialRepaymentScreenTest {
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
                ready = vm.screenState.value == DebtDetailsState.SUCCESS && vm.isDeleting.value != true && vm.resultedDebtList.value?.size == count
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
            val position = ViewModelProvider(fragment)[DebtDetailsViewModel::class.java].resultedDebtList.value!!.indexOfFirst { row -> row.id == id }
            fragment.requireView().findViewById<RecyclerView>(R.id.debtsRecyclerView).scrollToPosition(position)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    private fun openPayment(scenario: ActivityScenario<MainActivity>) {
        scrollToRecord(scenario, originalId)
        onView(withTagValue(equalTo(originalId))).perform(click())
        onView(withId(R.id.repaymentButton)).perform(click())
        onView(withId(R.id.repaymentAmountInput)).check(matches(isDisplayed()))
    }
    private fun saveTwice(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity {
            val dialog = details(it).childFragmentManager.findFragmentByTag(RepaymentDialogFragment.TAG) as RepaymentDialogFragment
            val button = (dialog.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            button.performClick(); button.performClick()
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
                onView(withId(R.id.repaymentButton)).check(matches(withText(if (sign > 0) R.string.repayment_received else R.string.repayment_paid)))
                    .perform(click())
                onView(withId(R.id.repaymentAmountInput)).perform(replaceText("101"), closeSoftKeyboard())
                onView(withText(R.string.save)).perform(click())
                onView(withText(containsString(context.getString(R.string.repayment_over, "100", "EUR")))).check(matches(isDisplayed()))
                assertAmount(100 * sign)
                onView(withId(R.id.repaymentAmountInput)).perform(replaceText("20"), closeSoftKeyboard())
                scenario.recreate()
                onView(withId(R.id.repaymentAmountInput)).check(matches(withText("20")))
                saveTwice(scenario)
                await("partial payment not stored") { dao.getAllDebtsById(humanId).size == 2 }
                ready(scenario)
                assertAmount(80 * sign)
                assertEquals(0, (100 * sign).toBigDecimal().compareTo(dao.getAllDebtsById(humanId).first { it.id == originalId }.sum))
                screenshot("partial-$sign")
                openPayment(scenario)
                onView(withId(R.id.repaymentFullButton)).perform(click())
                onView(withId(R.id.repaymentAmountInput)).check(matches(withText("80")))
                saveTwice(scenario)
                await("full payment not stored") { dao.getAllDebtsById(humanId).size == 3 }
                ready(scenario)
                assertAmount(0)
                scenario.recreate()
                ready(scenario)
                assertAmount(0)
                val last = dao.getAllDebtsById(humanId).maxBy { it.id }
                scrollToRecord(scenario, last.id)
                onView(withTagValue(equalTo(last.id))).perform(click())
                onView(withId(R.id.deleteButton)).check(matches(withText(R.string.repayment_cancel))).perform(click())
                onView(withId(android.R.id.button1)).perform(click())
                await("cancellation not stored") { dao.getAllDebtsById(humanId).any { it.id == last.id && it.cancelled } }
                ready(scenario)
                assertAmount(80 * sign)
                assertEquals(3, dao.getAllDebtsById(humanId).size)
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
            onView(withId(R.id.repaymentAmountInput)).perform(replaceText("20"), closeSoftKeyboard())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER r2_fail_balance BEFORE UPDATE ON Human BEGIN SELECT RAISE(ABORT, 'test save failure'); END")
            saveTwice(scenario)
            await("save failure did not reach the form") {
                var failed = false
                scenario.onActivity { failed = ViewModelProvider(details(it))[DebtDetailsViewModel::class.java].repaymentError.value == RepaymentError.SAVE_FAILED }
                failed
            }
            onView(withText(R.string.repayment_save_failed)).check(matches(isDisplayed()))
            assertEquals(1, dao.getAllDebtsById(humanId).size)
            assertAmount(100)
            onView(withId(R.id.repaymentAmountInput)).check(matches(withText("20")))
            scenario.recreate()
            onView(withId(R.id.repaymentAmountInput)).check(matches(withText("20")))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER r2_fail_balance")
            saveTwice(scenario)
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
            onView(withId(R.id.repaymentAmountInput)).perform(replaceText("20"))
            scenario.onActivity {
                val dialog = (details(it).childFragmentManager.findFragmentByTag(RepaymentDialogFragment.TAG) as RepaymentDialogFragment).requireDialog()
                val input = dialog.findViewById<EditText>(R.id.repaymentAmountInput)
                input.requestFocus()
                (it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            var previousBottom = 0
            var stableFrames = 0
            await("repayment keyboard did not open") {
                var shown = false
                scenario.onActivity {
                    val dialog = (details(it).childFragmentManager.findFragmentByTag(RepaymentDialogFragment.TAG) as RepaymentDialogFragment).requireDialog()
                    val insets = ViewCompat.getRootWindowInsets(dialog.window!!.decorView)
                    val activityInsets = ViewCompat.getRootWindowInsets(it.window.decorView)
                    val bottom = maxOf(insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0,
                        activityInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)
                    stableFrames = if (bottom > 0 && previousBottom == bottom) stableFrames + 1 else 0
                    previousBottom = bottom
                    shown = (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true ||
                        activityInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true) && stableFrames >= 5
                }
                shown
            }
            scenario.onActivity {
                val dialog = (details(it).childFragmentManager.findFragmentByTag(RepaymentDialogFragment.TAG) as RepaymentDialogFragment).requireDialog() as AlertDialog
                val screenBottom = it.window.decorView.height
                val imeBottom = maxOf(ViewCompat.getRootWindowInsets(dialog.window!!.decorView)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom,
                    ViewCompat.getRootWindowInsets(it.window.decorView)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                for (control in listOf<View>(requireNotNull(dialog.findViewById<EditText>(R.id.repaymentAmountInput)), dialog.getButton(AlertDialog.BUTTON_POSITIVE))) {
                    val position = IntArray(2)
                    control.getLocationOnScreen(position)
                    assertTrue("Repayment control is hidden behind IME", position[1] + control.height <= screenBottom - imeBottom)
                    assertTrue(control.isShown)
                }
            }
            screenshot("keyboard")
            saveTwice(scenario)
            await("keyboard save did not persist") { dao.getAllDebtsById(humanId).size == 2 }
            ready(scenario)
            assertAmount(80)
        }
    }

}
