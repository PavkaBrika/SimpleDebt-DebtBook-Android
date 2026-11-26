package com.breckneck.debtbook.debt

import com.breckneck.debtbook.core.ui.awaitRoute
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.debt.main.DebtViewModel
import com.breckneck.deptbook.domain.model.AppDataLists
import com.breckneck.deptbook.domain.model.HumanDomain
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
                compose.openDebts()
                val title = compose.awaitTag("bubbleRootTitle").assertIsDisplayed()
                val titleTop = title.fetchSemanticsNode().boundsInWindow.top
                compose.awaitTag("debtBalance").assertIsDisplayed()
                compose.onAllNodesWithText("—", useUnmergedTree = true).assertCountEquals(3)
                database.endTransaction()
                await { compose.onNodeWithText(context.getString(R.string.debt_empty_currency)).isDisplayed() }
                assertEquals(titleTop, title.fetchSemanticsNode().boundsInWindow.top, 0f)
                compose.awaitTag("addHumanButton").performClick()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                compose.awaitTag("humanNameEditText").performTextReplacement("Refresh test")
                compose.awaitTag("debtSumEditText").performTextReplacement("123.45")
                compose.awaitTag("setFinanceButton").performClick()
                compose.awaitRoute("DebtPerson")
                androidx.test.espresso.Espresso.pressBack()
                await { compose.onNodeWithText("Refresh test").isDisplayed() }

                fun visitSettingsAndReplace(data: AppDataLists) {
                    compose.awaitTag("bottomTab-settings").performClick()
                    compose.awaitRoute("Settings")
                    repo.replaceAllAppData(data)
                    androidx.test.espresso.Espresso.pressBack()
                    compose.awaitRoute("Debts")
                }
                visitSettingsAndReplace(empty.copy(humanList = listOf(HumanDomain(990001, "Replaced data", BigDecimal.TEN, "EUR"))))
                await { compose.onNodeWithText("Replaced data").isDisplayed() }
                visitSettingsAndReplace(empty)
                await { compose.onNodeWithText(context.getString(R.string.debt_empty_currency)).isDisplayed() }
                visitSettingsAndReplace(empty.copy(humanList = listOf(HumanDomain(990002, "After empty", BigDecimal.ONE, "EUR"))))
                await { compose.onNodeWithText("After empty").isDisplayed() }
                compose.onNodeWithContentDescription(context.getString(R.string.find_person)).performClick()
                compose.onNode(hasSetTextAction()).performTextReplacement("absent")
                await { compose.onNodeWithText(context.getString(R.string.debt_empty_search)).isDisplayed() }
                scenario.moveToState(Lifecycle.State.STARTED)
                repo.replaceAllAppData(empty.copy(humanList = listOf(HumanDomain(990003, "Absent match", BigDecimal.ONE, "EUR"))))
                scenario.moveToState(Lifecycle.State.RESUMED)
                await { compose.onNodeWithText("Absent match").isDisplayed() }
            }
        } finally {
            if (database.inTransaction()) database.endTransaction()
            repo.replaceAllAppData(original)
            prefs.edit().putBoolean("pin_code_is_enabled", pin).putBoolean("ads_disabled", ads)
                .putInt("debts_quantity_for_app_rate_dialog_show", rate).putString("default_currency", currency).commit()
        }
    }
}
