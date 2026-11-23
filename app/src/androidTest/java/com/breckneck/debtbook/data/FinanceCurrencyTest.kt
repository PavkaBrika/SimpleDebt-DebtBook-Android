package com.breckneck.debtbook.data

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.finance.main.FinanceViewModel
import com.breckneck.deptbook.domain.model.*
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.LedgerDate
import org.junit.Assert.*
import org.junit.Test
import repository.AppDataRepositoryImpl
import java.util.Date

class FinanceCurrencyTest {
    @Test fun switchingCurrencyFiltersStoredAmountsInsteadOfRelabelingThem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AppDataRepositoryImpl(context); val original = repo.getAllAppData()
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
        val oldPin = prefs.getBoolean("pin_code_is_enabled", false); val oldAds = prefs.getBoolean("ads_disabled", false)
        val oldCurrency = prefs.getString("finance_currency", "USD")
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true).commit()
        val data = AppDataLists(emptyList(), emptyList(),
            listOf(Finance(900027, "10.01".toBigDecimal(), Date(), null, 900027, "EUR", LedgerDate.today()),
                Finance(900028, "20.02".toBigDecimal(), Date(), null, 900027, "USD", LedgerDate.today())),
            listOf(FinanceCategory(900027, "Currency test", FinanceCategoryState.EXPENSE, "#123456", 123)), emptyList(), emptyList())
        fun await(condition: () -> Boolean) {
            val end = System.currentTimeMillis() + 10000
            while (!condition()) { if (System.currentTimeMillis() > end) fail("Currency totals timed out"); Thread.sleep(50) }
        }
        try {
            repo.replaceAllAppData(data)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    (activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment).navController.navigate(R.id.financeFragment)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                lateinit var vm: FinanceViewModel
                scenario.onActivity { activity ->
                    val fragment = (activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment).childFragmentManager.primaryNavigationFragment!!
                    vm = ViewModelProvider(fragment)[FinanceViewModel::class.java]
                    vm.setCurrency("EUR")
                }
                await { vm.summary.value.totalsFor(FinanceCategoryState.EXPENSE).total.compareTo("10.01".toBigDecimal()) == 0 }
                scenario.onActivity {
                    val loaded = vm.summary.value
                    vm.stepPeriod(-1)
                    vm.setDirection(FinanceCategoryState.INCOME)
                    val duringChange = vm.summary.value
                    assertFalse(duringChange.loading)
                    val expenses = duringChange.totalsFor(FinanceCategoryState.EXPENSE)
                    if (duringChange.period == loaded.period) {
                        assertEquals(loaded.totalsFor(FinanceCategoryState.EXPENSE), expenses)
                    } else {
                        assertEquals(loaded.period.shifted(-1).day, duringChange.period.day)
                        assertTrue(expenses.categories.isEmpty())
                        assertEquals(0, expenses.total.signum())
                    }
                    vm.stepPeriod(1)
                    vm.setCurrency("USD")
                    vm.setCurrency("EUR")
                    vm.setCurrency("USD")
                }
                await { vm.summary.value.totalsFor(FinanceCategoryState.EXPENSE).total.compareTo("20.02".toBigDecimal()) == 0 }
                assertEquals(listOf("EUR", "USD"), repo.getAllAppData().financeList.sortedBy { it.id }.map { it.currency })
            }
        } finally {
            repo.replaceAllAppData(original)
            prefs.edit().putBoolean("pin_code_is_enabled", oldPin).putBoolean("ads_disabled", oldAds).putString("finance_currency", oldCurrency).commit()
        }
    }
}
