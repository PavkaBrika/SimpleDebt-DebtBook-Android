package com.breckneck.debtbook.debt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.debt.main.DebtViewModel
import com.breckneck.deptbook.domain.model.AppDataLists
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.util.ScreenState
import database.AppDataBase
import org.junit.Assert.*
import org.junit.Test
import repository.AppDataRepositoryImpl
import java.math.BigDecimal

/** Run on a disposable AVD: temporarily replaces the database and restores it afterwards. */
class DebtRefreshScreenTest {
    @get:org.junit.Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = AppDataRepositoryImpl(context)
    private val empty = AppDataLists(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    private fun current(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment)
            .childFragmentManager.primaryNavigationFragment!!
    private fun await(condition: () -> Boolean) {
        compose.waitUntil(15_000, condition)
    }
    @Test fun emptyListThenCreateThenReplaceThenEmptyRefreshWithoutRestart() {
        val original = repo.getAllAppData()
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
        val pin = prefs.getBoolean("pin_code_is_enabled", false)
        val ads = prefs.getBoolean("ads_disabled", false)
        val rate = prefs.getInt("debts_quantity_for_app_rate_dialog_show", 0)
        val currency = prefs.getString("default_currency", null)
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true)
            .putString("default_currency", "EUR")
            .putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).commit()
        val database = AppDataBase(context).openHelper.writableDatabase
        try {
            repo.replaceAllAppData(empty)
            // Hold the writer used by DebtViewModel's read transaction until the first layout is visible.
            database.beginTransactionNonExclusive()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var vm: DebtViewModel
                scenario.onActivity { vm = ViewModelProvider(current(it))[DebtViewModel::class.java] }
                val title = compose.awaitTag("bubbleRootTitle").assertIsDisplayed()
                val titleTop = title.fetchSemanticsNode().boundsInWindow.top
                compose.awaitTag("debtBalance").assertIsDisplayed()
                compose.onAllNodesWithText("—", useUnmergedTree = true).assertCountEquals(3)
                assertNull(vm.balance.value)
                database.endTransaction()
                await { vm.screenState.value == ScreenState.EMPTY }
                assertEquals(titleTop, title.fetchSemanticsNode().boundsInWindow.top, 0f)
                scenario.onActivity { it.onAddButtonClick() }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                compose.awaitTag("humanNameEditText").performTextReplacement("Refresh test")
                compose.awaitTag("debtSumEditText").performTextReplacement("123.45")
                compose.awaitTag("setFinanceButton").performClick()
                await {
                    var ready = false
                    scenario.onActivity { ready = it.navController.currentDestination?.id == R.id.debtDetailsFragment }
                    ready
                }
                scenario.onActivity { it.onBackButtonClick() }
                await { vm.resultedHumanList.value?.singleOrNull()?.name == "Refresh test" && vm.screenState.value == ScreenState.SUCCESS }
                scenario.onActivity {
                    assertSame(vm, ViewModelProvider(current(it))[DebtViewModel::class.java])
                    vm.selectCurrency("EUR")
                    it.navController.navigate(R.id.settingsFragment)
                }
                repo.replaceAllAppData(empty.copy(humanList = listOf(HumanDomain(990001, "Replaced data", BigDecimal.TEN, "EUR"))))
                scenario.onActivity { it.onBackButtonClick() }
                await { vm.resultedHumanList.value?.singleOrNull()?.name == "Replaced data" }
                scenario.onActivity { it.navController.navigate(R.id.settingsFragment) }
                repo.replaceAllAppData(empty)
                scenario.onActivity { it.onBackButtonClick() }
                await {
                    var resumed = false
                    scenario.onActivity {
                        val entry = it.navController.currentBackStackEntry
                        resumed = entry?.destination?.id == R.id.mainFragment &&
                            entry.lifecycle.currentState == Lifecycle.State.RESUMED
                    }
                    resumed && vm.resultedHumanList.value?.isEmpty() == true && vm.screenState.value == ScreenState.EMPTY
                }
                // A second refresh from EMPTY must not be swallowed by equal StateFlow values.
                repo.replaceAllAppData(empty.copy(humanList = listOf(HumanDomain(990002, "After empty", BigDecimal.ONE, "EUR"))))
                scenario.onActivity {
                    vm.setListStateLoading()
                    assertEquals(ScreenState.EMPTY, vm.screenState.value)
                }
                await { vm.resultedHumanList.value?.singleOrNull()?.name == "After empty" }
                scenario.onActivity {
                    vm.onStartSearch()
                    vm.setSearchQuery("absent")
                }
                await { vm.resultedHumanList.value?.isEmpty() == true }
                repo.replaceAllAppData(empty.copy(humanList = listOf(HumanDomain(990003, "Absent match", BigDecimal.ONE, "EUR"))))
                scenario.onActivity { vm.setListStateLoading() }
                await { vm.resultedHumanList.value?.singleOrNull()?.name == "Absent match" }
                scenario.onActivity { vm.onStopSearch() }
                assertEquals(1, vm.resultedHumanList.value!!.size)
            }
        } finally {
            if (database.inTransaction()) database.endTransaction()
            repo.replaceAllAppData(original)
            prefs.edit().putBoolean("pin_code_is_enabled", pin).putBoolean("ads_disabled", ads)
                .putInt("debts_quantity_for_app_rate_dialog_show", rate).putString("default_currency", currency).commit()
        }
    }
}
