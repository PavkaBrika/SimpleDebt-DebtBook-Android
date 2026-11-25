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
import androidx.compose.ui.semantics.SemanticsProperties
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
        prefs.edit().putString("app_theme", if (dark) "DARK" else "LIGHT")
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
            for (key in listOf("app_theme", "ads_disabled", "default_currency", "pin_code_is_enabled",
                "debts_quantity_for_app_rate_dialog_show", "human_order_attribute", "human_order_by_increase")) {
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
    private fun remainingAmount(id: Int, amount: String, label: Int = R.string.debt_remaining_to_receive) {
        compose.record(id)
        val panel = hasAnyAncestor(hasTestTag("loanRemaining-$id"))
        compose.onNode(panel and hasText(context.getString(label)), useUnmergedTree = true).assertExists()
        compose.onNode(panel and hasText("$amount\u00a0₽"), useUnmergedTree = true).assertExists()
    }

    @Test fun currencyScreenFiltersDebtsAndPreservesTheNewDebtDraft() {
        balance("+18 300")
        compose.onNode(hasText("RUB") and hasClickAction()).performClick()
        awaitDestination(R.id.currencyPickerFragment)
        awaitCurrencyScreen()
        text(context.getString(R.string.rub)).assertIsSelected()
        text(context.getString(R.string.usd)).assertDoesNotExist()
        text(context.getString(R.string.eur)).performClick()
        awaitDestination(R.id.mainFragment)
        compose.awaitTag("peopleList").performScrollToNode(hasText("Claire"))
        text("Claire").assertIsDisplayed()
        text("Анна").assertDoesNotExist()

        compose.awaitTag("addHumanButton").performClick()
        awaitDestination(R.id.newDebtFragment)
        compose.awaitTag("debtSumEditText").performTextReplacement("123")
        compose.awaitTag("humanNameEditText").performTextReplacement("Draft")
        val currencyButton = compose.onNodeWithContentDescription(context.getString(R.string.select_currency))
        currencyButton.performScrollTo().performClick()
        awaitDestination(R.id.currencyPickerFragment)
        awaitCurrencyScreen()
        pressBack()
        awaitDestination(R.id.newDebtFragment)
        compose.awaitTag("humanNameEditText").assertTextContains("Draft")
        compose.awaitTag("debtSumEditText").assertTextContains("123")
        currencyButton.performScrollTo().performClick()
        awaitDestination(R.id.currencyPickerFragment)
        awaitCurrencyScreen()
        text(context.getString(R.string.eur)).performClick()
        awaitDestination(R.id.newDebtFragment)
        compose.awaitTag("humanNameEditText").assertTextContains("Draft")
        compose.awaitTag("debtSumEditText").assertTextContains("123")
        compose.onNode(hasText("EUR") and hasClickAction()).assertIsDisplayed()
        assertEquals("RUB", prefs.getString("default_currency", null))
    }

    private fun awaitCurrencyScreen() {
        val title = hasText(context.getString(R.string.select_currency))
        compose.waitUntil(8000) { compose.onAllNodes(title).fetchSemanticsNodes(false).isNotEmpty() }
    }

    @Test fun newDebtSuggestsAnExistingPersonAndKeepsTheirCurrency() {
        val peopleCount = dao.getAllHuman().size
        compose.awaitTag("addHumanButton").performClick()
        val name = compose.awaitTag("humanNameEditText")
        val contacts = compose.awaitTag("pickDebtContact").performScrollTo().assertIsDisplayed()
        assertEquals(
            name.fetchSemanticsNode().boundsInWindow.center.y,
            contacts.fetchSemanticsNode().boundsInWindow.center.y,
            2f,
        )
        name.performTextReplacement("clai")
        compose.awaitTag("debtPerson-$euro").performScrollTo().performClick()
        text("Claire").assertExists()
        text("EUR").assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.select_currency)).assertDoesNotExist()
        compose.awaitTag("directionNegative").performScrollTo().performClick().assertIsSelected()
        compose.awaitTag("debtSumEditText").performTextReplacement("10")
        capture("existing-person-form")
        compose.awaitTag("setFinanceButton").performClick()
        awaitDestination(R.id.debtDetailsFragment)
        assertEquals(peopleCount, dao.getAllHuman().size)
        assertEquals(2, dao.getAllDebtsById(euro).size)
        assertEquals(0, dao.getHumanSumDebt(euro).compareTo(800.toBigDecimal()))
    }

    @Test fun ambiguousPersonErrorReturnsToNameAndHighlightsTheChoices() {
        val duplicate = 921004
        dao.insertHuman(Human(duplicate, "Анна", BigDecimal.ZERO, "RUB"))
        try {
            compose.awaitTag("addHumanButton").performClick()
            compose.awaitTag("humanNameEditText").performTextReplacement("Анна")
            compose.awaitTag("debtSumEditText").performTextReplacement("20")
            compose.awaitTag("setFinanceButton").performClick()
            val message = context.getString(R.string.debt_choose_matching_person)
            compose.waitUntil(8000) { compose.onAllNodesWithText(message).fetchSemanticsNodes(false).isNotEmpty() }
            text(message).assertIsDisplayed()
            compose.awaitTag("debtPersonSuggestions").assertIsDisplayed()
            compose.onNodeWithTag("pickDebtPhoto").assertDoesNotExist()
            capture("person-choice-error")
            compose.awaitTag("debtPerson-$duplicate").performScrollTo().performClick()
            compose.onNodeWithTag("debtPersonSuggestions").assertDoesNotExist()
            text(message).assertDoesNotExist()
            compose.awaitTag("setFinanceButton").performClick()
            awaitDestination(R.id.debtDetailsFragment)
            assertEquals(1, dao.getAllDebtsById(duplicate).size)
        } finally {
            dao.deleteDebtsByHumanId(duplicate)
            dao.deleteHumanById(duplicate)
        }
    }

    @Test fun saveShowsAmountAndNameErrorsBesideTheirFields() {
        val peopleCount = dao.getAllHuman().size
        compose.awaitTag("addHumanButton").performClick()
        val amount = compose.awaitTag("debtSumEditText")
        val name = compose.awaitTag("humanNameEditText")
        val save = compose.awaitTag("setFinanceButton")
        val hint = compose.onNodeWithTag("debtAmountError")
        val missingAmount = context.getString(R.string.debt_amount_required)
        val missingName = context.getString(R.string.debt_name_required)
        val zero = context.getString(R.string.zerodebt)
        fun fieldError(message: String) = SemanticsMatcher.expectValue(SemanticsProperties.Error, message)

        compose.awaitTag("createDebtDue").performScrollTo()
        save.performClick()
        amount.assertIsDisplayed().assert(fieldError(missingAmount))
        name.assert(fieldError(missingName))
        hint.assertIsDisplayed().assertTextEquals(missingAmount)
        assertTrue(hint.fetchSemanticsNode().boundsInWindow.top >= amount.fetchSemanticsNode().boundsInWindow.bottom)
        assertTrue(hint.fetchSemanticsNode().boundsInWindow.bottom <= name.fetchSemanticsNode().boundsInWindow.top)

        amount.performTextReplacement("0")
        save.performClick()
        amount.assert(fieldError(zero))
        hint.assertTextEquals(zero)
        capture("inline-amount-zero")

        amount.performTextReplacement("25")
        save.performClick()
        hint.assertDoesNotExist()
        name.assertIsDisplayed().assert(fieldError(missingName))
        assertEquals(peopleCount, dao.getAllHuman().size)

        name.performTextReplacement("Анна")
        name.assert(!fieldError(missingName))
        save.performClick()
        awaitDestination(R.id.debtDetailsFragment)
        assertEquals(peopleCount, dao.getAllHuman().size)
        assertEquals(2, dao.getAllDebtsById(anna).size)
    }

    @Test fun personActionsAndHistoryFiltersStayAvailableAcrossNewDebt() {
        val borrowed = anna + 100
        val settled = anna + 101
        dao.saveDebt(Debt(borrowed, (-40).toBigDecimal(), anna, "Borrowed", today, recordedAsLoan = true))
        dao.saveDebt(Debt(settled, 50.toBigDecimal(), anna, "Settled", today, recordedAsLoan = true))
        dao.saveDebt(Debt(0, (-50).toBigDecimal(), anna, null, today, repaymentOfId = settled, operationId = "details-polish-payment"))
        person()
        fun control(tag: String): SemanticsNodeInteraction {
            compose.awaitTag("personHistory").performScrollToNode(hasTestTag(tag))
            return compose.onNodeWithTag(tag)
        }
        val add = compose.awaitTag("addDebtButton").assertIsDisplayed()
        val manage = compose.onNodeWithContentDescription(context.getString(R.string.debt_manage)).assertIsDisplayed()
        val share = compose.awaitTag("shareHumanButton").assertIsDisplayed()
        assertEquals(manage.fetchSemanticsNode().boundsInWindow.center.y, share.fetchSemanticsNode().boundsInWindow.center.y, 2f)
        assertTrue(share.fetchSemanticsNode().boundsInWindow.center.x < manage.fetchSemanticsNode().boundsInWindow.center.x)
        assertTrue(add.fetchSemanticsNode().boundsInWindow.top > manage.fetchSemanticsNode().boundsInWindow.bottom)
        manage.performClick()
        text(context.getString(R.string.person_rename)).assertIsDisplayed()
        text(context.getString(R.string.debt_choose_photo)).assertIsDisplayed()
        text(context.getString(R.string.person_delete_action)).assertIsDisplayed()
        pressBack()

        val tabs = control("debtHistoryTabs").fetchSemanticsNode().boundsInWindow
        val sort = compose.onNodeWithTag("sortButton").fetchSemanticsNode().boundsInWindow
        assertTrue(tabs.right <= sort.left)
        assertEquals(tabs.center.y, sort.center.y, 2f)
        capture("history-tabs")
        control("settledDebtsTab").performClick().assertIsSelected()
        compose.record(settled).assertTextContains("Settled")
        add.performClick()
        awaitDestination(R.id.newDebtFragment)
        pressBack()
        awaitDestination(R.id.debtDetailsFragment)
        control("settledDebtsTab").assertIsSelected()
        compose.record(settled).assertTextContains("Settled")
        compose.onNodeWithTag("personHistoryLoading").assertDoesNotExist()

        control("openDebtsTab").performClick().assertIsSelected()
        control("historyFilter-NEGATIVE").performClick()
        compose.record(borrowed).assertTextContains("Borrowed")
        compose.onNodeWithTag("record-$anna").assertDoesNotExist()
        control("sortButton").performClick()
        text(context.getString(R.string.debt_history_order)).assertIsDisplayed()
        val choice = text(context.getString(R.string.debt_order_amount_lowest)).performScrollTo()
        choice.performClick()
        text(context.getString(R.string.debt_history_order)).assertDoesNotExist()
        control("sortButton").performClick()
        text(context.getString(R.string.debt_order_amount_lowest)).performScrollTo().assertIsSelected()
        pressBack()

        add.performClick()
        val amount = compose.awaitTag("debtSumEditText")
        amount.performTextReplacement("25")
        amount.assertTextContains("25")
        compose.awaitTag("setFinanceButton").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        awaitDestination(R.id.debtDetailsFragment)
        val newId = dao.getAllDebtsById(anna).single { it.sum.compareTo(25.toBigDecimal()) == 0 }.id
        control("openDebtsTab").assertIsSelected()
        compose.record(newId).assertExists()
        assertEquals(0, dao.getHumanSumDebt(anna).compareTo(12385.toBigDecimal()))
    }

    @Test fun debtFormScrollDrawsBehindTheSaveButtonWhileTheKeyboardIsOpen() {
        compose.awaitTag("addHumanButton").performClick()
        val amount = compose.awaitTag("debtSumEditText").performClick()
        compose.waitUntil(10_000) {
            var imeVisible = false
            onActivity { imeVisible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            imeVisible
        }
        val save = compose.awaitTag("setFinanceButton").assertIsDisplayed()
        val form = compose.awaitTag("debtFormScroll")
        assertTrue(form.fetchSemanticsNode().boundsInWindow.bottom > save.fetchSemanticsNode().boundsInWindow.bottom)
        val originalSaveBounds = save.fetchSemanticsNode().boundsInWindow
        form.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 160f) }
        assertEquals(originalSaveBounds, save.fetchSemanticsNode().boundsInWindow)
        capture("form-ime-edge")
        amount.performScrollTo().assertIsDisplayed()
        pressBack()
    }

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
        compose.onNodeWithTag("debtBalance").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.clear_currency_search)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.close_currency_search)).performClick()
        balance("+12 400")
    }

    @Test fun orderSheetAppliesEachChoiceAndCanRememberIt() {
        balance("+18 300")
        val list = compose.awaitTag("peopleList")
        val orderButton = compose.onNodeWithTag("filterHumanButton")
        val sheetTitle = text(context.getString(R.string.people_order_title))
        val older = compose.onNodeWithTag("person-$anna")
        val newer = compose.onNodeWithTag("person-$other")
        val rememberChoice = text(context.getString(R.string.remember_choice))
        val originalOrder = prefs.all.filterKeys { it.startsWith("human_order_") }
        for ((label, annaFirst) in listOf(
            R.string.people_order_newest to false,
            R.string.people_order_oldest to true,
            R.string.people_order_lowest_balance to false,
            R.string.people_order_highest_balance to true,
        )) {
            list.performScrollToIndex(0)
            orderButton.performClick()
            sheetTitle.assertIsDisplayed()
            if (label == R.string.people_order_highest_balance) rememberChoice.performClick()
            text(context.getString(label)).performClick()
            compose.waitUntil(8000) {
                compose.onAllNodesWithText(context.getString(R.string.people_order_title)).fetchSemanticsNodes(false).isEmpty()
            }
            list.performScrollToIndex(3)
            assertEquals(annaFirst, older.fetchSemanticsNode().boundsInWindow.top < newer.fetchSemanticsNode().boundsInWindow.top)
            if (label != R.string.people_order_highest_balance) {
                assertEquals(originalOrder, prefs.all.filterKeys { it.startsWith("human_order_") })
            }
        }
        assertEquals(false, prefs.all["human_order_by_increase"])
        list.performScrollToIndex(0)
        orderButton.performClick()
        text(context.getString(R.string.people_order_highest_balance)).assertIsSelected()
        capture("people-order")
        pressBack()
        sheetTitle.assertDoesNotExist()
    }

    @Test fun searchReplacesTheHeaderAndRestoresTheBalanceOnBack() {
        balance("+18 300")
        val search = compose.onNodeWithContentDescription(context.getString(R.string.find_person))
        val searchCenter = search.fetchSemanticsNode().boundsInWindow.center
        val balanceCard = compose.onNodeWithTag("debtBalance")
        val originalHeight = balanceCard.fetchSemanticsNode().boundsInWindow.height
        search.performClick()
        val field = compose.onNode(hasContentDescription(context.getString(R.string.find_person)) and hasSetTextAction())
        field.assertIsFocused()
        val close = compose.onNodeWithContentDescription(context.getString(R.string.close_currency_search))
        val closeCenter = close.fetchSemanticsNode().boundsInWindow.center
        assertEquals(searchCenter.x, closeCenter.x, 1f)
        assertEquals(searchCenter.y, closeCenter.y, 1f)
        balanceCard.assertDoesNotExist()

        field.performTextReplacement("бОр")
        compose.waitUntil(8000) { compose.onAllNodesWithTag("person-$anna").fetchSemanticsNodes(false).isEmpty() }
        text("Борис").assertIsDisplayed()
        capture("people-search")

        fun imeVisible(): Boolean {
            var visible = false
            onActivity {
                visible = ViewCompat.getRootWindowInsets(it.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            return visible
        }
        compose.waitUntil(8000, ::imeVisible)
        pressBack()
        compose.waitUntil(8000) { !imeVisible() }
        field.assertTextContains("бОр")
        pressBack()
        search.assertIsDisplayed()
        assertEquals(originalHeight, balanceCard.fetchSemanticsNode().boundsInWindow.height, 1f)
        balance("+18 300")
    }

    @Test fun annaRepaymentAndUndoUpdateWholeCurrency() {
        balance("+18 300")
        capture("list")
        person()
        remainingAmount(anna, "12 400")
        text(context.getString(R.string.debt_today)).performClick()
        compose.onNodeWithTag("dueSaveButton").assertExists()
        pressBack()
        compose.onNodeWithTag("personHistory").performScrollToNode(hasTestTag("repay-$anna"))
        compose.onNodeWithTag("repay-$anna").performClick()
        compose.onNodeWithTag("repaymentAmountInput").performTextReplacement("2400")
        text(context.getString(R.string.debt_after_repayment, "10 000\u00a0₽")).assertExists()
        compose.onNodeWithTag("repaymentSaveButton").performClick()
        compose.waitUntil(8000) { dao.getAllDebtsById(anna).size == 2 }
        remainingAmount(anna, "10 000")
        capture("partial")
        pressBack()
        balance("+15 900")
        person()
        val payment = dao.getAllDebtsById(anna).single { it.repaymentOfId == anna }
        compose.record(payment.id).performClick()
        compose.onNode(hasText(context.getString(R.string.repayment_cancel)) and hasClickAction()).performClick()
        compose.waitUntil(8000) { dao.getAllDebtsById(anna).any { it.cancelled } }
        remainingAmount(anna, "12 400")
        compose.record(payment.id).assertTextContains(context.getString(R.string.repayment_cancelled))
        capture("undone")
        pressBack()
        balance("+18 300")
        compose.awaitTag("peopleList").performScrollToIndex(0)
        compose.onNode(hasText("RUB") and hasClickAction()).performClick()
        awaitDestination(R.id.currencyPickerFragment)
        awaitCurrencyScreen()
        text(context.getString(R.string.eur)).performClick()
        compose.awaitTag("peopleList").performScrollToNode(hasText("Claire"))
        compose.onNodeWithTag("person-$euro").assertTextContains("Claire")
        compose.onNodeWithTag("person-$anna").assertDoesNotExist()
        assertEquals(0, dao.getHumanSumDebt(euro).compareTo(810.toBigDecimal()))
        compose.awaitTag("addHumanButton").performClick()
        compose.awaitTag("setFinanceButton")
        text("RUB").assertIsDisplayed()
    }

    @Test fun balanceAndLoanColorsFollowTheirOwnDirections() {
        val dark = InstrumentationRegistry.getArguments().getString("theme") == "dark"
        val green = if (dark) 0xFF314E39.toInt() else 0xFFDBEDCD.toInt()
        val red = if (dark) 0xFF4B352F.toInt() else 0xFFF9E5D9.toInt()
        val neutral = if (dark) 0xFF2C3B30.toInt() else 0xFFE9EDE2.toInt()
        fun checkFill(tag: String, expected: Int) {
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
            node.performScrollTo().assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInWindow
            val screen = IntArray(2)
            val window = IntArray(2)
            onActivity {
                it.window.decorView.getLocationOnScreen(screen)
                it.window.decorView.getLocationInWindow(window)
            }
            val x = (bounds.left + bounds.width * .8f).toInt() + screen[0] - window[0]
            val y = bounds.center.y.toInt() + screen[1] - window[1]
            // UiAutomation can sample the visible screen on API 23 as well.
            val screenshot = inst.uiAutomation.takeScreenshot()
            try {
                assertEquals(tag, expected, screenshot.getPixel(x, y))
            } finally {
                screenshot.recycle()
            }
        }
        fun checkBalance(label: Int, amount: String, expectedColor: Int) {
            val card = compose.awaitTag("personBalanceCard")
            card.assertIsDisplayed()
            val panel = hasAnyAncestor(hasTestTag("personBalanceCard"))
            compose.onNode(panel and hasText(context.getString(label) + " · RUB"), useUnmergedTree = true).assertExists()
            compose.onNode(panel and hasText("$amount\u00a0₽"), useUnmergedTree = true).assertExists()
            checkFill("personBalanceCard", expectedColor)
        }

        person()
        checkBalance(R.string.show_positive, "12 400", green)
        pressBack()
        val borrowed = anna + 100
        dao.saveDebt(Debt(borrowed, (-12400).toBigDecimal(), anna, "Borrowed", today, recordedAsLoan = true))
        person()
        checkBalance(R.string.debt_person_balance, "0", neutral)
        pressBack()
        dao.saveDebt(Debt(borrowed + 1, (-2500).toBigDecimal(), anna, null, today, recordedAsLoan = true))
        person()
        checkBalance(R.string.show_negative, "2 500", red)
        capture("balance-debit")
        remainingAmount(anna, "12 400")
        checkFill("loanRemaining-$anna", green)
        capture("remaining-credit")
        remainingAmount(borrowed, "12 400", R.string.debt_remaining_to_repay)
        checkFill("loanRemaining-$borrowed", red)
        capture("remaining-debit")
    }

    @Test fun earlyOnlyCardKeepsItsCommentAndBalanceUntilDeletionIsConfirmed() {
        val note = "Earlier transfer"
        val opening = BigDecimal("7.89")
        val entry = dao.getAllDebtsById(euro).single().copy(recordedAsLoan = false, dueDay = null, info = note)
        dao.updateDebt(entry)
        dao.updateHuman(requireNotNull(dao.getHuman(euro)).copy(openingAdjustment = opening, sumDebt = opening + entry.sum))

        text("RUB").performClick()
        awaitDestination(R.id.currencyPickerFragment)
        awaitCurrencyScreen()
        text(context.getString(R.string.eur)).performClick()
        compose.awaitTag("peopleList").performScrollToNode(hasTestTag("person-$euro"))
        compose.onNodeWithTag("person-$euro").performClick()
        compose.awaitTag("earlyEntriesTab").assertIsSelected()
        val adjustment = context.getString(R.string.opening_adjustment, "+" + MoneyDisplay.formatExact(opening, "EUR"), "EUR")
        text(adjustment).assertExists()
        compose.record(entry.id).assertTextContains(note).assertTextContains(LedgerDate.display(today)).performClick()
        compose.awaitTag("editButton").performClick()
        compose.awaitTag("debtInfoEditText").performScrollTo().performTextReplacement("Receipt")
        compose.awaitTag("setFinanceButton").performClick()
        compose.record(entry.id).assertTextContains("Receipt").performClick()
        compose.onNodeWithTag("deleteButton").performClick()
        text(context.getString(R.string.early_entry_delete_question)).assertExists()
        compose.onNode(hasClickAction() and hasText(context.getString(R.string.cancel))).performClick()
        compose.awaitTag("debtActionsCancel").performClick()
        compose.record(entry.id).assertTextContains("Receipt").performClick()
        compose.onNodeWithTag("deleteButton").performClick()
        compose.onNode(hasClickAction() and hasText(context.getString(R.string.delete))).performClick()
        compose.awaitTag("emptyDebtsLayout").assertTextContains(context.getString(R.string.no_open_debts))
        compose.onNodeWithTag("earlyEntriesTab").assertDoesNotExist()
        assertEquals(0, dao.getHumanSumDebt(euro).compareTo(opening))
    }

    @Test fun exactLegacyBalanceSurvivesFullRepaymentAndRename() {
        val principal = BigDecimal("12.3456")
        val opening = BigDecimal("7.89")
        dao.updateDebt(dao.getAllDebtsById(anna).single().copy(sum = principal))
        dao.updateHuman(requireNotNull(dao.getHuman(anna)).copy(openingAdjustment = opening, sumDebt = opening + principal))
        person()
        balance(MoneyDisplay.formatExact(opening + principal, "RUB"))
        remainingAmount(anna, MoneyDisplay.formatExact(principal, "RUB"))
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
            compose.onNodeWithContentDescription(context.getString(R.string.debt_manage)).performClick()
            text(context.getString(action)).performClick()
        }
        manage(R.string.person_rename)
        compose.onNode(hasSetTextAction()).performTextReplacement("Anna QA")
        text(context.getString(R.string.save)).performClick()
        compose.waitUntil(8000) { dao.getHuman(anna)?.name == "Anna QA" }
        text("Anna QA").assertIsDisplayed()
        manage(R.string.person_delete_action)
        compose.awaitTag("personDeleteSheet").assertIsDisplayed()
        text(context.getString(R.string.person_delete_title, "Anna QA")).assertIsDisplayed()
        capture("person-delete")
        compose.onNode(hasClickAction() and hasText(context.getString(R.string.cancel))).performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithTag("personDeleteSheet").fetchSemanticsNodes(false).isEmpty() }
        assertNotNull(dao.getHuman(anna))
        manage(R.string.person_delete_action)
        compose.onNode(hasClickAction() and hasText(context.getString(R.string.delete))).performClick()
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
