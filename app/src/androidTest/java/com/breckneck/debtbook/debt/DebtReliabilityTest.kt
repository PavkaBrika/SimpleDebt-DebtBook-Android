package com.breckneck.debtbook.debt

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import com.breckneck.debtbook.core.customview.CustomSwitchView
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
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail(message)
            Thread.sleep(50)
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
            scenario.onActivity { activity ->
                val view = current(activity).requireView()
                view.findViewById<EditText>(R.id.humanNameEditText).setText(name)
                view.findViewById<EditText>(R.id.debtSumEditText).setText("12,34")
                view.findViewById<View>(R.id.setFinanceButton).performClick()
                view.findViewById<View>(R.id.setFinanceButton).performClick() // duplicate tap
            }
            await("debt was not created") { dao.getAllHuman().any { it.name == name } && dao.getAllDebts().any { it.sum == 12.34 } }
            val human = dao.getAllHuman().single { it.name == name }
            val initial = dao.getAllDebtsById(human.id).single()
            assertEquals(12.34, human.sumDebt, 0.0)
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
            scenario.onActivity { activity ->
                val view = current(activity).requireView()
                val input = view.findViewById<EditText>(R.id.debtSumEditText)
                assertEquals(12.34, com.breckneck.deptbook.domain.util.DecimalInput.parse(input.text.toString())!!, 0.0)
                input.setText("20,55")
                view.findViewById<CustomSwitchView>(R.id.customSwitch).setChecked(false)
                view.findViewById<View>(R.id.setFinanceButton).performClick()
            }
            await("edited amount not persisted") { dao.getAllDebtsById(human.id).single().sum == -20.55 }
            assertEquals(-20.55, dao.getHumanSumDebt(human.id), 0.00000001)
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
                dao.getAllDebtsById(human.id).isEmpty() && kotlin.math.abs(dao.getHumanSumDebt(human.id)) < 0.00000001
            }
        }
    }

    @Test fun failedRelatedWriteRollsBackTheFirstWrite() {
        val transaction = RoomDataTransaction(context)
        try {
            transaction.run {
                dao.insertHuman(Human(900010, name, 45.67, "EUR"))
                dao.insertDebt(Debt(900010, 45.67, 900010, null, "14 Nov 2026"))
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
                assertTrue(activity.findViewById<View>(R.id.bottomNavigationView).isShown)
                activity.onAddButtonClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val input = current(activity).requireView().findViewById<EditText>(R.id.debtSumEditText)
                input.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
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
                val save = current(activity).requireView().findViewById<View>(R.id.setFinanceButton)
                assertTrue("save must remain visible with the keyboard", save.isShown)
                val position = IntArray(2)
                save.getLocationOnScreen(position)
                assertTrue("save must stay above IME", position[1] + save.height <= activity.window.decorView.height - ime.bottom)
                val input = current(activity).requireView().findViewById<EditText>(R.id.debtSumEditText)
                input.getLocationOnScreen(position)
                assertTrue("focused amount must stay above IME", position[1] + input.height <= activity.window.decorView.height - ime.bottom)
                assertTrue("focused amount must stay inside the viewport", position[1] >= root.paddingTop)
            }
            capture("keyboard")
        }
    }
}
