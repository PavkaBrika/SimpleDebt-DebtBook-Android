package com.breckneck.debtbook.debt

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.settings.edgeSwipe
import com.breckneck.debtbook.settings.requirePredictiveGestures
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.MoneyDisplay
import com.breckneck.deptbook.domain.util.LedgerDate
import database.AppDataBase
import entity.Debt
import entity.Human
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.math.BigDecimal

/** Fixtures belong to a disposable device. All navigation starts at the launcher destination. */
class BubbleDebtJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val dao = AppDataBase(context).appDao()
    private val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val old = prefs.all.toMap()
    private val anna = 921001
    private val other = 921002
    private val euro = 921003
    private val today = LedgerDate.today()
    private lateinit var main: MainActivity
    private fun onActivity(action: (MainActivity) -> Unit) = inst.runOnMainSync { action(main) }

    @Before fun launch() {
        val dark = InstrumentationRegistry.getArguments().getString("theme") == "dark"
        prefs.edit().putString("app_theme", context.getString(if (dark) R.string.dark_theme else R.string.light_theme))
            .putBoolean("ads_disabled", true).putString("default_currency", "RUB")
            .putBoolean("pin_code_is_enabled", false).putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).commit()
        for ((id, name, amount) in listOf(Triple(anna, "Анна", 12400), Triple(other, "Борис", 5900), Triple(euro, "Claire", 810))) {
            dao.insertHuman(Human(id, name, BigDecimal.ZERO, if (id == euro) "EUR" else "RUB"))
            dao.saveDebt(Debt(id, amount.toBigDecimal(), id, name, today, recordedAsLoan = true, dueDay = today))
        }
        inst.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        SystemClock.sleep(650)
        context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        compose.awaitTag("addHumanButton")
        inst.runOnMainSync {
            main = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
        }
    }

    @After fun clean() {
        if (::main.isInitialized) onActivity { it.finish() }
        for (id in listOf(anna, other, euro)) { dao.deleteDebtsByHumanId(id); dao.deleteHumanById(id) }
        prefs.edit().apply {
            for (key in listOf("app_theme", "ads_disabled", "default_currency", "pin_code_is_enabled", "debts_quantity_for_app_rate_dialog_show")) {
                when (val value = old[key]) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    null -> remove(key)
                }
            }
        }.commit()
    }

    private fun text(value: String) = compose.onNodeWithText(value)
    private fun person() {
        val list = compose.awaitTag("peopleList").performScrollToNode(hasTestTag("person-$anna"))
        val row = compose.onNodeWithTag("person-$anna")
        val overlap = row.fetchSemanticsNode().boundsInWindow.bottom -
            compose.awaitTag("addHumanButton").fetchSemanticsNode().boundsInWindow.top
        if (overlap > 0f) list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, overlap) }
        row.assertTextContains(context.getString(R.string.debt_today)).performClick()
        compose.awaitTag("personHistory")
    }
    private fun balance(expected: String) {
        val label = "$expected\u00a0₽"
        compose.waitUntil(8000) { compose.onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
    private fun capture(name: String) {
        if (!name.startsWith("gesture-")) {
            compose.waitUntil(6000) { inst.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName }
            SystemClock.sleep(350)
        }
        val image = inst.uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "debt-$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun dueRemaining(amount: String) = context.getString(R.string.debt_remaining, amount, "RUB")

    @Test fun earliestDeadlineUsesIndividualOpenLoansAcrossFilters() {
        val yesterday = com.breckneck.debtbook.debt.reminders.testDay(-1)
        val before = com.breckneck.debtbook.debt.reminders.testDay(-2)
        val opposite = other + 10
        dao.saveDebt(Debt(opposite, (-5900).toBigDecimal(), other, "Opposite", before, dueDay = yesterday))
        dao.saveDebt(Debt(other + 11, 10.toBigDecimal(), other, "Settled", before, dueDay = before))
        dao.saveDebt(Debt(0, (-10).toBigDecimal(), other, null, today, repaymentOfId = other + 11,
            operationId = "settled-deadline-test"))
        // Return from a real destination to refresh the list.
        compose.awaitTag("addHumanButton").performClick()
        compose.awaitTag("setFinanceButton")
        pressBack()
        val list = compose.awaitTag("peopleList")
        for (label in listOf(R.string.show_negative, R.string.show_positive)) {
            val choice = hasText(context.getString(label)) and hasClickAction()
            list.performScrollToNode(choice)
            compose.onNode(choice).performClick()
            list.performScrollToNode(hasTestTag("person-$other"))
            compose.onNodeWithTag("person-$other").assertTextContains("0\u00a0₽")
                .assertTextContains(context.getString(R.string.debt_overdue, LedgerDate.display(yesterday)))
            list.performScrollToNode(hasText("+12 400\u00a0₽"))
        }
        list.performScrollToIndex(0)
        compose.onNodeWithContentDescription(context.getString(R.string.find_person)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Бор")
        compose.waitUntil(8000) { compose.onAllNodesWithTag("person-$anna").fetchSemanticsNodes().isEmpty() }
        balance("+12 400")
    }

    @Test fun annaRepaymentAndUndoUpdateWholeCurrency() {
        balance("+18 300")
        capture("list")
        person()
        compose.record(anna).assertTextContains(dueRemaining("12 400"))
        text(context.getString(R.string.debt_today)).performClick()
        compose.onNodeWithTag("dueSaveButton").assertExists()
        pressBack()
        compose.onNodeWithTag("personHistory").performScrollToNode(hasTestTag("repay-$anna"))
        compose.onNodeWithTag("repay-$anna").performClick()
        compose.onNodeWithTag("repaymentAmountInput").performTextReplacement("2400")
        text(context.getString(R.string.debt_after_repayment, "10 000\u00a0₽")).assertExists()
        compose.onNodeWithTag("repaymentSaveButton").performClick()
        compose.waitUntil(8000) { dao.getAllDebtsById(anna).size == 2 }
        compose.record(anna).assertTextContains(dueRemaining("10 000"))
        capture("partial")
        pressBack()
        balance("+15 900")
        person()
        val payment = dao.getAllDebtsById(anna).single { it.repaymentOfId == anna }
        compose.record(payment.id).performClick()
        compose.onNode(hasText(context.getString(R.string.repayment_cancel)) and hasClickAction()).performClick()
        compose.waitUntil(8000) { dao.getAllDebtsById(anna).any { it.cancelled } }
        compose.record(anna).assertTextContains(dueRemaining("12 400"))
        compose.record(payment.id).assertTextContains(context.getString(R.string.repayment_cancelled))
        capture("undone")
        pressBack()
        balance("+18 300")
        text("RUB").performClick()
        compose.onNodeWithText("EUR", substring = true).performClick()
        compose.awaitTag("peopleList").performScrollToNode(hasText("Claire"))
        compose.onNodeWithTag("person-$euro").assertTextContains("Claire")
        compose.onNodeWithTag("person-$anna").assertDoesNotExist()
        assertEquals(0, dao.getHumanSumDebt(euro).compareTo(810.toBigDecimal()))
        compose.awaitTag("addHumanButton").performClick()
        compose.awaitTag("setFinanceButton")
        text("RUB").assertIsDisplayed()
    }

    @Test fun exactLegacyBalanceSurvivesFullRepaymentAndRename() {
        val principal = BigDecimal("12.3456")
        val opening = BigDecimal("7.89")
        dao.updateDebt(dao.getAllDebtsById(anna).single().copy(sum = principal))
        dao.updateHuman(requireNotNull(dao.getHuman(anna)).copy(openingAdjustment = opening, sumDebt = opening + principal))
        person()
        balance(MoneyDisplay.formatExact(opening + principal, "RUB"))
        compose.record(anna).assertTextContains(dueRemaining(MoneyDisplay.formatExact(principal, "RUB")))
        compose.onNodeWithTag("personHistory").performScrollToNode(hasTestTag("repay-$anna"))
        compose.onNodeWithTag("repay-$anna").performClick()
        compose.awaitTag("repaymentFullButton").performClick()
        compose.onNodeWithTag("repaymentAmountInput").assertTextContains(DecimalInput.format(principal, currency = "RUB"))
        compose.onNodeWithTag("repaymentSaveButton").performClick()
        compose.waitUntil(8000) { dao.getAllDebtsById(anna).size == 2 }
        compose.awaitTag("personHistory").performScrollToIndex(0)
        balance(MoneyDisplay.formatExact(opening, "RUB"))
        assertEquals(0, dao.getAllDebtsById(anna).single { it.repaymentOfId == anna }.sum.compareTo(-principal))
        fun manage(action: Int) {
            val button = context.getString(R.string.debt_manage)
            compose.awaitTag("personHistory").performScrollToNode(hasText(button))
            text(button).performClick()
            text(context.getString(action)).performClick()
        }
        manage(R.string.person_rename)
        compose.onNode(hasSetTextAction()).performTextReplacement("Anna QA")
        text(context.getString(R.string.save)).performClick()
        compose.waitUntil(8000) { dao.getHuman(anna)?.name == "Anna QA" }
        text("Anna QA").assertIsDisplayed()
        manage(R.string.person_delete_action)
        val confirm = androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.okButton))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
        SystemClock.sleep(500)
        confirm.perform(androidx.test.espresso.action.ViewActions.click())
        compose.awaitTag("addHumanButton")
        assertNull(dao.getHuman(anna))
        assertTrue(dao.getAllDebtsById(anna).isEmpty())
    }

    @Test fun talkBackCanFocusDebtActions() {
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 24)
        val configured = android.provider.Settings.Secure.getString(context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        Assume.assumeTrue(configured.contains("talkback", ignoreCase = true))
        val reader = inst.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        compose.waitUntil(10000) {
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
                .any { it.id.contains("talkback", ignoreCase = true) }
        }
        fun accessibleButton(label: String): AccessibilityNodeInfo {
            var target: AccessibilityNodeInfo? = null
            compose.waitUntil(8000) {
                fun AccessibilityNodeInfo.findLabel(): AccessibilityNodeInfo? {
                    if (isVisibleToUser && (text?.contains(label) == true || contentDescription?.contains(label) == true)) return this
                    for (index in 0 until childCount) getChild(index)?.findLabel()?.let { return it }
                    return null
                }
                val match = reader.rootInActiveWindow?.findLabel()
                target = generateSequence(match) { it.parent }.firstOrNull { it.isClickable }
                target != null
            }
            return requireNotNull(target)
        }
        fun focus(label: String) {
            val button = accessibleButton(label)
            assertTrue(button.isAccessibilityFocused || button.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            compose.waitUntil(5000) {
                button.refresh() && button.isAccessibilityFocused
            }
        }

        compose.awaitTag("peopleList").performScrollToNode(hasTestTag("person-$anna"))
        assertTrue(accessibleButton("Анна").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        compose.awaitTag("personHistory").performScrollToNode(hasTestTag("repay-$anna"))
        focus(context.getString(R.string.debt_repay))
        assertTrue(accessibleButton(context.getString(R.string.newdebt)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        compose.awaitTag("setFinanceButton")
        focus(context.getString(R.string.debt_save))
    }

    @Test fun formKeyboardAndFourTabs() {
        compose.awaitTag("addHumanButton").performClick()
        compose.awaitTag("debtSumEditText").performClick().performTextReplacement("42")
        var keyboardTop = 0
        compose.waitUntil(8000) {
            onActivity {
                val decor = it.window.decorView
                val insets = ViewCompat.getRootWindowInsets(decor)!!
                if (insets.isVisible(WindowInsetsCompat.Type.ime())) keyboardTop = decor.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            }
            keyboardTop > 0
        }
        SystemClock.sleep(350)
        val save = compose.onNodeWithTag("setFinanceButton").assertIsDisplayed()
        assertTrue(save.fetchSemanticsNode().boundsInWindow.bottom <= keyboardTop + 1)
        capture("keyboard")
        pressBack()
        pressBack()
        compose.awaitTag("addHumanButton")
        for ((tab, action, destination) in listOf(
            Triple(R.string.finances, R.string.finance_add_expense, R.id.createFinanceFragment),
            Triple(R.string.goals, R.string.goal_new, R.id.createGoalsFragment),
            Triple(R.string.settings, R.string.synchronization, R.id.synchronizationFragment))) {
            compose.onNode(hasText(context.getString(tab)) and hasClickAction()).performClick()
            val matcher = hasText(context.getString(action)) and hasClickAction()
            compose.waitUntil(8000) { compose.onAllNodes(matcher).fetchSemanticsNodes(false).isNotEmpty() }
            val actionNode = compose.onNode(matcher)
            if (tab == R.string.settings) actionNode.performScrollTo()
            actionNode.performClick()
            awaitDestination(destination)
            pressBack()
            compose.waitUntil(8000) { compose.onAllNodes(matcher).fetchSemanticsNodes(false).isNotEmpty() }
        }
        compose.onNode(hasText(context.getString(R.string.debts)) and hasClickAction()).performClick()
        compose.awaitTag("addHumanButton").assertIsDisplayed()
    }

    private fun awaitDestination(id: Int) {
        compose.waitUntil(8000) {
            var current = false
            onActivity { current = it.navController.currentDestination?.id == id }
            current
        }
    }

    @Test fun predictiveFormBackToPersonListAndLauncher() {
        requirePredictiveGestures(context)
        person()
        compose.awaitTag("addDebtButton").performClick()
        compose.awaitTag("debtSumEditText").performTextReplacement("123")
        compose.onNodeWithTag("debtInfoEditText").performScrollTo().performTextReplacement("Draft")
        var keyboard = false
        onActivity { keyboard = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        if (keyboard) pressBack()
        fun swipe(cancel: Boolean, previous: Int) {
            SystemClock.sleep(500)
            inst.edgeSwipe(cancel) {
                onActivity { assertTrue("Back preview", it.findViewById<View>(previous)?.isShown == true) }
                capture("gesture-$previous-$cancel")
            }
            SystemClock.sleep(450)
        }
        swipe(true, R.id.debtDetailsSheets)
        compose.awaitTag("debtSumEditText").assertTextContains("123")
        compose.onNodeWithTag("debtInfoEditText").assertTextContains("Draft")
        assertEquals(1, dao.getAllDebtsById(anna).size)
        swipe(false, R.id.debtDetailsSheets)
        awaitDestination(R.id.debtDetailsFragment)
        compose.onNode(hasText("Анна") and hasClickAction().not()).assertExists()
        swipe(false, R.id.humansLayout)
        compose.awaitTag("addHumanButton").performClick()
        compose.awaitTag("debtSumEditText")
        swipe(false, R.id.humansLayout)
        awaitDestination(R.id.mainFragment)
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            inst.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        val launcher = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .trim().lineSequence().last().substringBefore('/')
        SystemClock.sleep(600)
        inst.edgeSwipe(false) { capture("gesture-home") }
        compose.waitUntil(10000) { inst.uiAutomation.rootInActiveWindow?.packageName?.toString() == launcher }
    }
}
