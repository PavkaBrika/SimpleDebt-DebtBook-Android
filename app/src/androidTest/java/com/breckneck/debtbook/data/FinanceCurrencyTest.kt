package com.breckneck.debtbook.data

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.breckneck.debtbook.core.ui.awaitRoute
import com.breckneck.debtbook.debt.awaitTag
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.deptbook.domain.model.*
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.LedgerDate
import org.junit.Assert.*
import org.junit.Test
import repository.AppDataRepositoryImpl
import java.util.Date

class FinanceCurrencyTest {
    @get:org.junit.Rule val compose = createEmptyComposeRule()
    @Test fun switchingCurrencyFiltersStoredAmountsInsteadOfRelabelingThem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AppDataRepositoryImpl(context); val original = repo.getAllAppData()
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
        val oldPin = prefs.getBoolean("pin_code_is_enabled", false); val oldAds = prefs.getBoolean("ads_disabled", false)
        val oldCurrency = prefs.getString("finance_currency", "USD")
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true).putString("finance_currency", "USD").commit()
        val data = AppDataLists(emptyList(), emptyList(),
            listOf(Finance(900027, "10.01".toBigDecimal(), Date(), null, 900027, "EUR", LedgerDate.today()),
                Finance(900028, "20.02".toBigDecimal(), Date(), null, 900027, "USD", LedgerDate.today())),
            listOf(FinanceCategory(900027, "Currency test", FinanceCategoryState.EXPENSE, "#123456", 123)), emptyList(), emptyList())
        try {
            repo.replaceAllAppData(data)
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.awaitTag("bottomTab-finances").performClick()
                fun currency(code: String) {
                    val current = prefs.getString("finance_currency", null) ?: "USD"
                    if (current == code) return
                    compose.onNode(hasText(current) and hasClickAction()).performClick()
                    compose.awaitRoute("Currencies")
                    compose.onNodeWithText(code).performScrollTo().performClick()
                    compose.awaitRoute("Finances")
                }
                compose.awaitRoute("Finances")
                currency("EUR")
                compose.onNode(hasText(context.getString(R.string.expenses)) and hasClickAction()).performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("10.01", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() }
                val previous = compose.onNodeWithContentDescription(context.getString(R.string.finance_previous))
                val next = compose.onNodeWithContentDescription(context.getString(R.string.finance_next))
                previous.performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("10.01", substring = true)
                    .fetchSemanticsNodes().isEmpty() }
                next.performClick()
                currency("USD")
                currency("EUR")
                currency("USD")
                compose.waitUntil(10_000) { compose.onAllNodesWithText("20.02", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() }
                assertEquals(listOf("EUR", "USD"), repo.getAllAppData().financeList.sortedBy { it.id }.map { it.currency })
            }
        } finally {
            repo.replaceAllAppData(original)
            prefs.edit().putBoolean("pin_code_is_enabled", oldPin).putBoolean("ads_disabled", oldAds).putString("finance_currency", oldCurrency).commit()
        }
    }
}
