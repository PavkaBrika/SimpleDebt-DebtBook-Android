package com.breckneck.debtbook.debt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.debt.details.DebtDetailsViewModel
import com.breckneck.deptbook.domain.model.DebtDomain
import database.AppDataBase
import entity.Debt
import entity.Human
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import repository.RoomDataTransaction

/** Disposable emulator integration tests; database fixtures use a reserved test-only name/ID. */
class DebtReliabilityTest {
    @get:org.junit.Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao get() = AppDataBase(context).appDao()
    private val prefs get() = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val name = "R1a test"
    private var oldPin = false
    private var oldAds = false
    private var oldTheme: String? = null

    @Before fun prepare() {
        oldTheme = prefs.getString("app_theme", null)
        val dark = InstrumentationRegistry.getArguments().getString("r1aTheme") == "dark"
        prefs.edit().putString("app_theme", context.getString(if (dark) R.string.dark_theme else R.string.light_theme)).commit()
        oldPin = prefs.getBoolean("pin_code_is_enabled", false)
        oldAds = prefs.getBoolean("ads_disabled", false)
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true)
            .putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).commit()
        cleanup()
    }
    @After fun finish() {
        cleanup()
        prefs.edit().putString("app_theme", oldTheme).putBoolean("pin_code_is_enabled", oldPin).putBoolean("ads_disabled", oldAds).commit()
    }
    private fun cleanup() {
        dao.getAllHuman().filter { it.name == name }.forEach {
            dao.deleteDebtsByHumanId(it.id)
            dao.deleteHumanById(it.id)
        }
    }
    private fun await(message: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(12_000, condition)
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError(message, timeout)
        }
    }
    private fun capture(label: String) {
        val suffix = InstrumentationRegistry.getArguments().getString("r1aTheme") ?: "light"
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "r1a-$label-$suffix.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun current(activity: MainActivity): Fragment =
        (activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment)
            .childFragmentManager.primaryNavigationFragment!!

    @Test fun createEditDeleteAndActivityRecreationPreserveAmounts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.onAddButtonClick() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            compose.awaitTag("humanNameEditText").performTextReplacement(name)
            compose.awaitTag("debtSumEditText").performTextReplacement("12,34")
            val save = compose.awaitTag("setFinanceButton").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
            compose.runOnIdle { save(); save() }
            await("debt was not created") { dao.getAllHuman().any { it.name == name } && dao.getAllDebts().any { it.sum == "12.34".toBigDecimal().stripTrailingZeros() } }
            val human = dao.getAllHuman().single { it.name == name }
            val initial = dao.getAllDebtsById(human.id).single()
            assertEquals(0, ("12.34".toBigDecimal().stripTrailingZeros()).compareTo(human.sumDebt!!))
            await("details navigation") {
                var ready = false
                scenario.onActivity { ready = it.navController.currentDestination?.id == R.id.debtDetailsFragment }
                ready
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(R.id.debtDetailsFragment, activity.navController.currentDestination?.id)
                activity.editDebt(DebtDomain(initial.id, initial.sum, human.id, null, initial.date), human.currency, name)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val input = compose.awaitTag("debtSumEditText")
            input.assertTextContains(com.breckneck.deptbook.domain.util.DecimalInput.format(initial.sum, currency = human.currency))
            input.performTextReplacement("20,55")
            compose.onNodeWithTag("directionNegative").performClick()
            compose.awaitTag("setFinanceButton").performClick()
            await("edited amount not persisted") { dao.getAllDebtsById(human.id).single().sum == -"20.55".toBigDecimal().stripTrailingZeros() }
            assertEquals(0, (-"20.55".toBigDecimal().stripTrailingZeros()).compareTo(dao.getHumanSumDebt(human.id)!!))
            await("details after edit") {
                var ready = false
                scenario.onActivity { ready = it.navController.currentDestination?.id == R.id.debtDetailsFragment }
                ready
            }
            val edited = dao.getAllDebtsById(human.id).single()
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(current(activity))[DebtDetailsViewModel::class.java]
                val record = DebtDomain(edited.id, edited.sum, edited.idHuman, edited.info, edited.date)
                vm.deleteDebt(record)
                vm.deleteDebt(record)
            }
            await("deleted record or balance survived") {
                dao.getAllDebtsById(human.id).isEmpty() && com.breckneck.deptbook.domain.util.abs(dao.getHumanSumDebt(human.id)) < "0.00000001".toBigDecimal().stripTrailingZeros()
            }
        }
    }

    @Test fun failedRelatedWriteRollsBackTheFirstWrite() {
        val transaction = RoomDataTransaction(context)
        try {
            transaction.run {
                dao.insertHuman(Human(900010, name, "45.67".toBigDecimal().stripTrailingZeros(), "EUR"))
                dao.insertDebt(Debt(900010, "45.67".toBigDecimal().stripTrailingZeros(), 900010, null, "14 Nov 2026"))
                throw IllegalStateException("simulated second write failure")
            }
            fail("transaction did not throw")
        } catch (_: IllegalStateException) { }
        assertFalse(dao.getAllHuman().any { it.id == 900010 })
        assertFalse(dao.getAllDebts().any { it.id == 900010 })
    }

    @Test fun rootInsetsKeepNavigationAndSaveAboveSystemBarsAndKeyboard() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            capture("main")
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(R.id.rootLayout)
                val insets = ViewCompat.getRootWindowInsets(root)!!
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                assertTrue(root.paddingTop >= bars.top)
                assertTrue(root.paddingBottom >= bars.bottom)

            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            compose.onNode(hasText(context.getString(R.string.settings)) and hasClickAction()).assertIsDisplayed()
            compose.awaitTag("addHumanButton").performClick()
            compose.awaitTag("debtSumEditText").performClick()
            var lastImeBottom = 0
            var stableImeFrames = 0
            await("keyboard did not finish opening") {
                var shown = false
                scenario.onActivity {
                    val insets = ViewCompat.getRootWindowInsets(it.findViewById(R.id.rootLayout))!!
                    val bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                    stableImeFrames = if (bottom > bars && bottom == lastImeBottom) stableImeFrames + 1 else 0
                    lastImeBottom = bottom
                    shown = insets.isVisible(WindowInsetsCompat.Type.ime()) && stableImeFrames >= 5
                }
                shown
            }
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(R.id.rootLayout)
                val ime = ViewCompat.getRootWindowInsets(root)!!.getInsets(WindowInsetsCompat.Type.ime())
                assertTrue("root must avoid IME", root.paddingBottom >= ime.bottom)
            }
            val save = compose.awaitTag("setFinanceButton").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
            val amount = compose.awaitTag("debtSumEditText").assertIsFocused().fetchSemanticsNode().boundsInWindow
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(R.id.rootLayout)
                val keyboardTop = activity.window.decorView.height - lastImeBottom
                assertTrue(save.bottom <= keyboardTop + 1 && amount.bottom <= keyboardTop + 1)
                assertTrue(amount.top >= root.paddingTop)
            }
            capture("keyboard")
        }
    }
}
