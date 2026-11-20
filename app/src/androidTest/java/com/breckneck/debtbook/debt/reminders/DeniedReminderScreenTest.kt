package com.breckneck.debtbook.debt.reminders

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.core.MainActivity
import database.AppDataBase
import entity.Debt
import entity.Human
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Run separately after pm revoke + clear-permission-flags, API33+. */
class DeniedReminderScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun systemPermissionResponseKeepsDebtAndDueDateUsable() {
        val response = InstrumentationRegistry.getArguments().getString("r22PermissionResponse")
        assumeTrue("Run on API 33+ with r22PermissionResponse=allow or deny", Build.VERSION.SDK_INT >= 33 &&
            response in setOf("allow", "deny"))
        val allow = response == "allow"
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(DebtReminders.get(context).allowed())
        val dao = AppDataBase(context).appDao()
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
        val pin = prefs.getBoolean("pin_code_is_enabled", false); val ads = prefs.getBoolean("ads_disabled", false)
        val id = 900068
        fun waitFor(message: String, condition: () -> Boolean) {
            val end = System.currentTimeMillis() + 15000
            while (!condition()) { if (System.currentTimeMillis() > end) fail(message); Thread.sleep(100) }
        }
        try {
            prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true).commit()
            dao.deleteDebtsByHumanId(id); dao.deleteHumanById(id)
            dao.insertHuman(Human(id, "Denied reminder", BigDecimal.ZERO, "EUR"))
            dao.saveDebt(Debt(id, BigDecimal("100"), id, null, "2026-11-16"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.onHumanClick(id, "EUR", "Denied reminder") }
                waitFor("history missing") { try { onView(withTagValue(equalTo(id))).check(matches(isDisplayed())); true } catch (_: Exception) { false } }
                onView(withTagValue(equalTo(id))).perform(click())
                compose.onNodeWithTag("dueSettingsButton").performClick()
                compose.onNodeWithTag("dueReminderSwitch").assertDoesNotExist()
                compose.enterDebtDate(scenario, LocalDate.of(2099, 12, 31))
                compose.onNodeWithTag("dueReminderSwitch").performScrollTo().performClick()
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
                var button: AccessibilityNodeInfo? = null
                waitFor("Android notification permission dialog was not shown") {
                    val buttonId = if (allow) "permission_allow_button" else "permission_deny_button"
                    button = listOf("com.android.permissioncontroller", "com.google.android.permissioncontroller")
                        .flatMap { automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("$it:id/$buttonId").orEmpty() }.firstOrNull()
                    button != null
                }
                val bitmap = automation.takeScreenshot()
                java.io.File(context.getExternalFilesDir(null), "r22-system-permission-${if (allow) "allow" else "deny"}.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                assertTrue(button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithTag("dueReminderSwitch")
                        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                }
                if (allow) compose.onNodeWithTag("dueReminderSwitch").assertIsOn()
                else {
                    compose.onNodeWithTag("dueReminderSwitch").assertIsOff()
                    compose.onNodeWithTag("duePermissionStatus").assertExists()
                    compose.onNodeWithTag("dueTimeButton").assertDoesNotExist()
                }
                compose.onNodeWithTag("dueSaveButton").performClick()
                waitFor("due date not saved after denial") { dao.getAllDebtsById(id).single().dueDay == "2099-12-31" }
                val row = dao.getAllDebtsById(id).single()
                if (allow) assertNotNull(row.reminderToken) else assertNull(row.reminderToken)
                assertEquals(0, row.sum.compareTo(BigDecimal("100")))
                assertEquals(allow, DebtReminders.get(context).allowed())
                assertFalse(DebtReminders.get(context).deliverNow(id, "anything"))
            }
        } finally {
            dao.deleteDebtsByHumanId(id); dao.deleteHumanById(id)
            prefs.edit().putBoolean("pin_code_is_enabled", pin).putBoolean("ads_disabled", ads).commit()
        }
    }
}
