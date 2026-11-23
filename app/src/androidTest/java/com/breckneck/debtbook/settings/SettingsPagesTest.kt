package com.breckneck.debtbook.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.ui.components.currencyLabel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import util.DeviceCurrency

class SettingsPagesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val currencyKey = "default_currency"
    private val themeKey = "app_theme"
    private val pinKey = "pin_code_is_enabled"
    private val ratingKey = "debts_quantity_for_app_rate_dialog_show"
    private lateinit var originalPrefs: Map<String, *>
    private var originalNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED

    @Before fun prepareSettings() {
        originalPrefs = prefs.all
        originalNightMode = AppCompatDelegate.getDefaultNightMode()
        prefs.edit().putString(currencyKey, "ANG").putString(themeKey, "Light")
            .putBoolean(pinKey, false).putInt(ratingKey, Int.MAX_VALUE).commit()
    }

    @After fun restoreSettings() {
        val edit = prefs.edit()
        for (key in listOf(currencyKey, themeKey, pinKey, ratingKey)) {
            when (val value = originalPrefs[key]) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                else -> edit.remove(key)
            }
        }
        edit.commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(originalNightMode)
        }
    }

    @Test fun currencySearchRestoresQueryAndSavesTheSelectedCode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettings()
            action(R.string.default_currency).performClick()
            assertDestination(scenario, R.id.defaultCurrencyFragment)
            val savedCurrency = awaitNode(hasText("🇨🇼 ANG")).assertIsSelected()
            val regionalCurrency = awaitNode(hasText(currencyLabel(DeviceCurrency.get(context))))
            assertTrue(
                regionalCurrency.fetchSemanticsNode().boundsInRoot.top <
                    savedCurrency.fetchSemanticsNode().boundsInRoot.top,
            )
            compose.onNode(hasSetTextAction()).assertDoesNotExist()
            contentDescription(R.string.search_currency).performClick()
            awaitNode(hasSetTextAction() and isFocused()).performTextInput("  vietnam  ")
            compose.onNodeWithText("🇻🇳 VND").assertIsDisplayed()
            compose.onNodeWithText("🇺🇸 USD").assertDoesNotExist()

            scenario.recreate()
            awaitNode(hasSetTextAction()).assertTextContains("  vietnam  ")
            compose.onNodeWithText("🇻🇳 VND").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.clear_currency_search)).performClick()
            compose.onNode(hasSetTextAction()).performTextInput("xCg")
            compose.onNodeWithText("🇨🇼 XCG").performClick()

            assertDestination(scenario, R.id.settingsFragment)
            action(R.string.default_currency).assertTextContains("🇨🇼 XCG")
            assertEquals("XCG", prefs.getString(currencyKey, null))

            scenario.recreate()
            action(R.string.default_currency).performClick()
            awaitNode(hasText("🇨🇼 XCG")).assertIsSelected()
        }
    }

    @Test fun backingOutOfEmptySearchKeepsTheCurrency() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettings()
            action(R.string.default_currency).performClick()
            contentDescription(R.string.search_currency).performClick()
            awaitNode(hasSetTextAction() and isFocused()).performTextInput("no-match")
            compose.onNodeWithText(context.getString(R.string.no_matching_currencies)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.default_currency)).assertDoesNotExist()
            contentDescription(R.string.clear_currency_search).performClick()
            compose.onNodeWithText(context.getString(R.string.no_matching_currencies)).assertDoesNotExist()
            contentDescription(R.string.close_currency_search).performClick()
            compose.onNode(hasSetTextAction()).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.default_currency)).assertIsDisplayed()

            contentDescription(R.string.search_currency).performClick()
            awaitNode(hasSetTextAction() and isFocused()).performTextInput("no-match")
            contentDescription(R.string.back).performClick()
            assertDestination(scenario, R.id.settingsFragment)
            assertEquals("ANG", prefs.getString(currencyKey, null))
        }
    }

    @Test fun appearanceAppliesSavedModesAndStaysOpenAfterChanges() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettings()
            action(R.string.app_theme).performClick()
            assertDestination(scenario, R.id.appearanceFragment)
            awaitSelectedTheme(R.string.light_theme)
            assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())

            action(R.string.dark_theme).performClick()
            awaitSelectedTheme(R.string.dark_theme)
            assertDestination(scenario, R.id.appearanceFragment)
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
            assertEquals("DARK", prefs.getString(themeKey, null))

            action(R.string.system_theme).performClick()
            awaitSelectedTheme(R.string.system_theme)
            assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
            assertEquals("SYSTEM", prefs.getString(themeKey, null))
            pressBack()
            assertDestination(scenario, R.id.settingsFragment)
            action(R.string.app_theme).assertTextContains(context.getString(R.string.system_theme))
        }
    }

    private fun openSettings() {
        action(R.string.settings).performClick()
    }

    private fun action(label: Int) = awaitNode(hasText(context.getString(label)) and hasClickAction())

    private fun contentDescription(label: Int) = awaitNode(hasContentDescription(context.getString(label)))

    private fun awaitSelectedTheme(label: Int) {
        awaitNode(hasText(context.getString(label)) and isSelected()).assertIsSelected()
    }

    private fun awaitNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1
        }
        return compose.onNode(matcher)
    }

    private fun assertDestination(scenario: ActivityScenario<MainActivity>, destination: Int) {
        compose.onNode(isDialog()).assertDoesNotExist()
        scenario.onActivity { assertEquals(destination, it.navController.currentDestination?.id) }
    }
}
