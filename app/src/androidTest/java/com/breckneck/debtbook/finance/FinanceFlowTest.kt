package com.breckneck.debtbook.finance

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.ui.hideKeyboardBeforeBack
import com.breckneck.debtbook.finance.main.FinancePeriod
import com.breckneck.debtbook.finance.main.weekPickerLabel
import com.breckneck.debtbook.settings.edgeSwipe
import com.breckneck.debtbook.settings.requirePredictiveGestures
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.FinanceInterval
import database.AppDataBase
import entity.FinanceCategoryData
import entity.FinanceData
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Date
import java.util.TimeZone
import util.FinanceCategoryStateData

class FinanceFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dao get() = AppDataBase(context).appDao()
    private val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val books = "Книги"
    private val note = "Finance QA"
    private val prefsBefore = prefs.all
    private val existingCategories = dao.getAllFinanceCategories().map { it.id }
    private val originalEntries = dao.getAllFinances().map { it.id }

    @Before fun openFinance() {
        val theme = if (InstrumentationRegistry.getArguments().getString("theme") == "dark") "DARK" else "LIGHT"
        prefs.edit().putBoolean("ads_disabled", true).putBoolean("pin_code_is_enabled", false)
            .putString("app_theme", theme).putString("finance_currency", "USD")
            .putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).commit()
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
        act(R.string.finances)
        currency("USD", R.string.rub)
        awaitText("0".toBigDecimal().financeMoney("RUB"))
    }

    @After fun cleanup() {
        dao.getAllFinanceCategories().filter { it.id !in existingCategories }.forEach(dao::deleteFinanceCategory)
        kotlinx.coroutines.runBlocking {
            dao.getAllFinances().filter { it.id !in originalEntries }.forEach { dao.deleteFinance(it) }
        }
        val editor = prefs.edit()
        for (key in listOf("ads_disabled", "pin_code_is_enabled", "app_theme", "finance_currency", "debts_quantity_for_app_rate_dialog_show")) {
            when (val old = prefsBefore[key]) {
                is String -> editor.putString(key, old)
                is Boolean -> editor.putBoolean(key, old)
                is Int -> editor.putInt(key, old)
                null -> editor.remove(key)
            }
        }
        editor.commit()
    }

    @Test fun expenseRoundTripAndExactPeriodBalances() {
        direction(R.string.expenses)
        act(R.string.finance_add_expense)
        input(R.string.sum, "255,75")
        input(R.string.comment, note)
        act(R.string.create_new_category)
        node(hasSetTextAction() and hasText(str(R.string.name))).performTextReplacement(books)
        val iconSetLabel = str(R.string.finance_icon_set)
        val foodLabel = str(R.string.emoji_tab_food)
        val iconSetField = hasText(iconSetLabel) and hasClickAction()
        val sheetHeader = hasText(iconSetLabel) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        act(iconSetLabel)
        node(sheetHeader).assertIsDisplayed()
        node(hasText(foodLabel) and isSelected()).assertIsDisplayed()
        instrumentation.uiAutomation.waitForIdle(260, 4000)
        capture("icon-set")
        desc(R.string.cancel)
        compose.waitUntil(8000) { compose.onAllNodes(sheetHeader).fetchSemanticsNodes().isEmpty() }
        node(iconSetField and hasText(foodLabel))
        act(iconSetLabel)
        act(R.string.emoji_tab_education)
        node(iconSetField and hasText(str(R.string.emoji_tab_education)))
        act("📚")
        compose.onNodeWithContentDescription(context.getString(R.string.finance_color, 10)).performScrollTo().performClick()
        act(R.string.finance_save_category)
        node(hasText(books) and isSelected())
        awaitText("255,75")
        awaitText(note)
        act(R.string.finance_save_entry)
        total("255.75")
        capture("summary")
        val id = dao.getAllFinances().single { it.info == note }.id
        act(books)
        val entryRow = node(hasText(note) and hasClickAction()).assertIsDisplayed()
        instrumentation.uiAutomation.waitForIdle(260, 4000)
        capture("history-chevron")
        entryRow.performTouchInput {
            click(Offset(width - 12.dp.toPx(), center.y))
        }
        node(hasText(str(R.string.finance_edit_entry)) and hasClickAction()).assertIsDisplayed()
        capture("entry-actions")
        act(R.string.finance_edit_entry)
        input(R.string.sum, "300")
        act(R.string.finance_save_entry)
        awaitText(note)
        assertEquals(id, dao.getAllFinances().single { it.info == note }.id)
        back()
        total("300")
        act(books)
        act(note)
        act(R.string.delete)
        act(R.string.delete)
        awaitText(str(R.string.there_are_no_expenses_yet))
        back()
        total("0")
    }

    @Test fun overviewAndPagesFollowTheSelectedPeriod() {
        val today = LedgerDate.today()
        val previous = FinancePeriod(FinanceInterval.DAY, today).shifted(-8).day
        fun entry(name: String, direction: FinanceCategoryStateData, day: String, amount: Int, color: String = "#C5E1A5") {
            val category = dao.insertFinanceCategory(FinanceCategoryData(0, name, direction, color, 0x1F4DA)).toInt()
            dao.insertFinance(FinanceData(0, amount.toBigDecimal(), Date(), null, category, "RUB", day))
        }
        entry("Expense today", FinanceCategoryStateData.EXPENSE, today, 36)
        entry("Other expense", FinanceCategoryStateData.EXPENSE, today, 9, "#FFCCBC")
        entry("Income today", FinanceCategoryStateData.INCOME, today, 60)
        entry("Other income", FinanceCategoryStateData.INCOME, today, 30, "#B3E5FC")
        repeat(16) { entry("Earlier expense $it", FinanceCategoryStateData.EXPENSE, previous, 16 - it) }
        act(R.string.debts)
        act(R.string.finances)
        val incomeTab = node(hasText(str(R.string.revenues)) and isSelected()).assertIsDisplayed()
        val expenseTab = node(hasText(str(R.string.expenses)) and hasClickAction())
        val overviewTitle = node(hasText(str(R.string.finance_period_total))).assertIsDisplayed()
        assertTrue(incomeTab.fetchSemanticsNode().boundsInRoot.left < expenseTab.fetchSemanticsNode().boundsInRoot.left)
        assertTrue(overviewTitle.fetchSemanticsNode().boundsInRoot.bottom <= incomeTab.fetchSemanticsNode().boundsInRoot.top)
        total("90")
        awaitText("+" + 45.toBigDecimal().financeMoney("RUB"))
        val incomeCategory = hasText("Income today") and hasText(context.getString(R.string.finance_share_of_total, 67))
        val categoryLeft = node(incomeCategory).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left
        instrumentation.uiAutomation.waitForIdle(260, 4000)
        capture("income-page")
        val shares = node(hasText(str(R.string.finance_category_shares)) and hasClickAction())
        val collapsedWidth = shares.fetchSemanticsNode().boundsInRoot.width
        shares.performClick()
        val addIncome = node(hasText(str(R.string.finance_add_income)) and hasClickAction())
        val ring = shares.fetchSemanticsNode().boundsInRoot
        assertTrue(ring.width > collapsedWidth * 2.5f)
        assertTrue(ring.bottom <= addIncome.fetchSemanticsNode().boundsInRoot.top + 1f)
        instrumentation.uiAutomation.waitForIdle(260, 4000)
        capture("shares-ring")
        shares.performTouchInput { click(Offset(width * .92f, center.y)) }
        node(hasText(str(R.string.finance_category_shares)) and hasText("Income today") and hasClickAction()).assertIsDisplayed()
        instrumentation.uiAutomation.waitForIdle(260, 4000)
        capture("shares-expanded")
        summaryList().performScrollToIndex(2)
        val expandedCategory = node(incomeCategory).assertIsDisplayed()
        assertEquals(categoryLeft, expandedCategory.fetchSemanticsNode().boundsInRoot.left, 1f)
        assertTrue(expandedCategory.fetchSemanticsNode().boundsInRoot.center.y < addIncome.fetchSemanticsNode().boundsInRoot.top)
        instrumentation.uiAutomation.waitForIdle(260, 4000)
        capture("shares-breakdown")
        expandedCategory.performClick()
        awaitDestination(R.id.financeDetailsFragment)
        awaitText("Income today")
        back()
        awaitDestination(R.id.financeFragment)
        summaryList().performScrollToIndex(0)
        assertTrue(shares.fetchSemanticsNode().boundsInRoot.width > collapsedWidth)
        shares.performClick()
        assertEquals(collapsedWidth, shares.fetchSemanticsNode().boundsInRoot.width)

        summaryList().performTouchInput { swipeLeft() }
        node(hasText(str(R.string.expenses)) and isSelected()).assertIsDisplayed()
        total("45")
        node(hasText("Expense today") and hasText(context.getString(R.string.finance_share_of_total, 80))).assertIsDisplayed()
        capture("expense-page")
        summaryList().performTouchInput { swipeRight() }
        incomeTab.assertIsDisplayed()
        act(R.string.expenses)
        total("45")

        act(LedgerDate.display(today))
        for (option in listOf(R.string.day, R.string.week, R.string.month, R.string.year_full, R.string.finance_date_range)) {
            node(hasText(str(option)) and hasClickAction()).assertIsDisplayed()
        }
        node(hasText(str(R.string.day)) and isSelected()).assertIsDisplayed()
        capture("inline-interval-day")
        act(R.string.week)
        node(hasText(str(R.string.week)) and isSelected()).assertIsDisplayed()
        capture("inline-interval-week")
        val week = FinancePeriod(FinanceInterval.WEEK, today)
        val previousWeek = week.shifted(-1)
        act(R.string.finance_choose_week)
        val month = FinancePeriod(FinanceInterval.MONTH, today).days()
        if (previousWeek.days().endInclusive < month.start) desc(R.string.finance_previous_month)
        val weekOption = node(hasText(previousWeek.days().weekPickerLabel(context)) and hasClickAction()).performScrollTo()
        instrumentation.uiAutomation.waitForIdle(260, 4000)
        capture("week-picker")
        weekOption.performClick()
        node(hasText(periodLabel(previousWeek)) and hasClickAction()).assertIsDisplayed()
        back()
        node(hasText(LedgerDate.display(today)) and hasClickAction()).assertIsDisplayed()
        repeat(8) { desc(R.string.finance_previous) }
        total("136")
        awaitText((-136).toBigDecimal().financeMoney("RUB"))
        val list = summaryList()
        val pinnedBounds = overviewTitle.fetchSemanticsNode().boundsInRoot
        list.performScrollToIndex(5)
        val row = node(hasText("Earlier expense 3"))
        val distance = row.fetchSemanticsNode().boundsInRoot.center.y - pinnedBounds.center.y
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
        val scrolledBounds = row.fetchSemanticsNode().boundsInRoot
        assertTrue(pinnedBounds.center.y in scrolledBounds.top..scrolledBounds.bottom)
        assertEquals(pinnedBounds, overviewTitle.fetchSemanticsNode().boundsInRoot)
        list.performScrollToNode(hasText("Earlier expense 15"))
        list.performScrollToIndex(0)
        val date = node(hasText(LedgerDate.display(previous)) and hasClickAction())
        val currentPeriod = hasText(str(R.string.finance_current_period)) and hasClickAction()
        val currentPeriodAction = node(currentPeriod and hasAnyAncestor(verticalList)).assertIsDisplayed()
        val returnBounds = currentPeriodAction.fetchSemanticsNode().boundsInRoot
        val previousArrow = node(hasContentDescription(str(R.string.finance_previous))).fetchSemanticsNode().boundsInRoot
        val nextArrow = node(hasContentDescription(str(R.string.finance_next))).fetchSemanticsNode().boundsInRoot
        assertTrue(returnBounds.top >= date.fetchSemanticsNode().boundsInRoot.bottom)
        assertTrue(returnBounds.left <= previousArrow.left)
        assertTrue(returnBounds.right >= nextArrow.right)
        capture("current-period")
        currentPeriodAction.performTouchInput { click(Offset(8.dp.toPx(), center.y)) }
        awaitText(LedgerDate.display(today))
        total("45")
        awaitText("+" + 45.toBigDecimal().financeMoney("RUB"))
        compose.onNode(currentPeriod).assertDoesNotExist()

        desc(R.string.finance_previous)
        node(currentPeriod).performTouchInput { click(Offset(width - 8.dp.toPx(), center.y)) }
        node(hasText(LedgerDate.display(today)) and hasClickAction()).assertIsDisplayed()
        compose.onNode(currentPeriod).assertDoesNotExist()
    }

    @Test fun addButtonsBelongToThePageAndOpenItsOperation() {
        val expense = hasText(str(R.string.finance_add_expense)) and hasClickAction()
        val income = hasText(str(R.string.finance_add_income)) and hasClickAction()
        val incomeTab = hasText(str(R.string.revenues)) and hasClickAction()
        val expenseTab = hasText(str(R.string.expenses)) and hasClickAction()
        node(income and hasTestTag("addFinanceButton")).performClick()
        awaitText(str(R.string.finance_new_income))
        val incomeBounds = node(incomeTab).assertIsSelected().fetchSemanticsNode().boundsInRoot
        val expenseBounds = node(expenseTab).assertIsNotSelected().fetchSemanticsNode().boundsInRoot
        assertTrue(incomeBounds.left < expenseBounds.left)
        capture("income-form-accent")
        node(expenseTab).performClick()
        awaitText(str(R.string.finance_new_expense))
        node(expenseTab).assertIsSelected()
        capture("expense-form-accent")
        compose.hideKeyboardBeforeBack(activity { it.window })
        back()
        awaitDestination(R.id.financeFragment)
        summaryList().performTouchInput { swipeLeft() }
        node(expense and hasTestTag("addFinanceButton")).performClick()
        awaitText(str(R.string.finance_new_expense))
        node(expenseTab).assertIsSelected()
    }

    @Test fun customDateRangeIncludesBothEndsAndStepsByItsLength() {
        direction(R.string.expenses)
        val first = "2026-01-31"
        val last = "2026-02-02"
        val period = FinancePeriod(FinanceInterval.DATE_RANGE, first, last)
        val category = dao.insertFinanceCategory(FinanceCategoryData(0, books,
            FinanceCategoryStateData.EXPENSE, "#C5E1A5", 0x1F4DA)).toInt()
        for ((day, amount) in listOf("2026-01-30" to 7, first to 12, last to 13, "2026-02-03" to 19)) {
            dao.insertFinance(FinanceData(0, amount.toBigDecimal(), Date(), null, category, "RUB", day))
        }
        act(R.string.debts)
        act(R.string.finances)
        act(LedgerDate.display(LedgerDate.today()))
        act(R.string.finance_date_range)
        node(hasText(str(R.string.finance_date_range)) and isSelected()).assertIsDisplayed()
        act(R.string.finance_range_start)
        chooseCalendarDay("2026-02-04")
        act(R.string.finance_range_end)
        chooseCalendarDay(last)
        node(hasText(str(R.string.finance_range_invalid))).assertIsDisplayed()
        node(hasText(str(R.string.finance_apply_period))).assertIsNotEnabled()

        act(R.string.finance_range_start)
        chooseCalendarDay(first)
        capture("range-draft")
        act(R.string.finance_apply_period)
        node(hasText(periodLabel(period)) and hasClickAction()).assertIsDisplayed()
        total("25")
        awaitText((-25).toBigDecimal().financeMoney("RUB"))
        desc(R.string.finance_next)
        total("19")
        awaitText((-19).toBigDecimal().financeMoney("RUB"))
        node(hasText(periodLabel(period.shifted(1))) and hasClickAction()).assertIsDisplayed()
        act(R.string.finance_current_period)
        node(hasText(periodLabel(period.atToday(LedgerDate.today()))) and hasClickAction()).assertIsDisplayed()
    }

    @Test fun cancellingCurrencySelectionKeepsTheFinanceFilters() {
        val yesterday = FinancePeriod(FinanceInterval.DAY, LedgerDate.today()).shifted(-1).day
        desc(R.string.finance_previous)
        direction(R.string.revenues)
        act("RUB")
        awaitDestination(R.id.currencyPickerFragment)
        desc(R.string.back)
        awaitDestination(R.id.financeFragment)
        node(hasText(str(R.string.revenues)) and isSelected()).assertIsDisplayed()
        node(hasText(LedgerDate.display(yesterday)) and hasClickAction()).assertIsDisplayed()
        node(hasText("RUB") and hasClickAction()).assertIsDisplayed()

        val defaultCurrency = prefs.getString("default_currency", null)
        currency("RUB", R.string.eur)
        node(hasText(str(R.string.revenues)) and isSelected()).assertIsDisplayed()
        node(hasText(LedgerDate.display(yesterday)) and hasClickAction()).assertIsDisplayed()
        assertEquals(defaultCurrency, prefs.getString("default_currency", null))
    }

    @Test fun outsidePeriodHistoryAndCrossCurrencyCategoryDeletion() {
        direction(R.string.expenses)
        // Use a private category so confirming deletion can never remove a built-in category.
        val categoryId = dao.insertFinanceCategory(entity.FinanceCategoryData(0, books,
            util.FinanceCategoryStateData.EXPENSE, "#C5E1A5", 0x1F4DA)).toInt()
        act(R.string.finance_add_expense)
        act(books)
        input(R.string.sum, "3")
        act(R.string.finance_save_entry)
        total("3")
        desc(R.string.finance_previous)
        total("0")
        act(R.string.finance_add_expense)
        act(books)
        input(R.string.sum, "2")
        input(R.string.comment, "Yesterday")
        act(R.string.finance_save_entry)
        total("2")
        act(R.string.finance_current_period)
        total("3")
        act(books)
        awaitText("Yesterday")
        total("5")
        act(R.string.finance_add_entry)
        node(hasText(books) and isSelected())
        awaitText("RUB")
        input(R.string.sum, "1,25")
        input(R.string.comment, "From history")
        act(R.string.finance_save_entry)
        awaitText("From history")
        total("6.25")
        back()
        total("4.25")
        currency("RUB", R.string.eur)
        direction(R.string.revenues)
        act(R.string.finance_add_income)
        act(R.string.salary)
        input(R.string.sum, "17,50")
        act(R.string.finance_save_entry)
        total("17.5", "EUR")
        direction(R.string.expenses)
        act(R.string.finance_add_expense)
        act(books)
        input(R.string.sum, "4")
        act(R.string.finance_save_entry)
        total("4", "EUR")
        act(R.string.finance_add_expense)
        act(books)
        act(R.string.delete_category)
        awaitText(context.getString(R.string.finance_delete_category_all, books))
        act(R.string.cancel)
        assertEquals(listOf("EUR", "RUB", "RUB", "RUB"), dao.getAllFinances().filter { it.financeCategoryId == categoryId }.map { it.currency }.sorted())
        act(R.string.delete_category)
        act(R.string.delete)
        compose.waitUntil(8000) { dao.getAllFinanceCategories().none { it.id == categoryId } }
        back()
        total("0", "EUR")
        assertTrue(dao.getAllFinances().none { it.financeCategoryId == categoryId })
        direction(R.string.revenues)
        total("17.5", "EUR")
        act(R.string.salary)
        editEntry(str(R.string.salary))
        awaitText(str(R.string.salary))
        input(R.string.sum, "18,75")
        act(R.string.finance_save_entry)
        total("18.75", "EUR")
        back()
        total("18.75", "EUR")
    }

    @Test fun talkBackCanFocusSaveAndEdit() {
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 24)
        val configured = android.provider.Settings.Secure.getString(context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        Assume.assumeTrue(configured.contains("talkback", ignoreCase = true))
        val reader = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        compose.waitUntil(10000) {
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
                .any { it.id.contains("talkback", ignoreCase = true) }
        }
        fun focus(label: String) {
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
            val button = requireNotNull(target)
            assertTrue(button.isAccessibilityFocused || button.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            compose.waitUntil(5000) {
                button.refresh() && button.isAccessibilityFocused
            }
        }

        direction(R.string.expenses)
        act(R.string.finance_add_expense)
        focus(str(R.string.finance_save_entry))
        input(R.string.sum, "6")
        act(R.string.food)
        act(R.string.finance_save_entry)
        total("6")
        act(R.string.food)
        node(hasText(str(R.string.food)) and hasClickAction()).assertIsDisplayed()
        SystemClock.sleep(350)
        focus(str(R.string.food))
        act(R.string.food)
        focus(str(R.string.finance_edit_entry))
    }

    @Test fun incomeFormScrollsBehindSaveAndShowsTheLastControl() {
        repeat(24) { index ->
            dao.insertFinanceCategory(FinanceCategoryData(
                0, "Income category ${index + 1}", FinanceCategoryStateData.INCOME, "#DBEDCD", 0x1F4DA,
            ))
        }
        act(R.string.finance_add_income)
        val save = node(hasText(str(R.string.finance_save_entry)) and hasClickAction()).assertIsDisplayed()
        val footer = save.fetchSemanticsNode().boundsInWindow
        val form = node(hasScrollAction() and hasAnyDescendant(hasText(str(R.string.finance_entry_date))))
        val viewport = form.fetchSemanticsNode().boundsInWindow
        assertTrue(viewport.bottom > footer.bottom)

        val category = node(hasText("Income category 12")).performScrollTo()
        val distance = category.fetchSemanticsNode().boundsInWindow.center.y - footer.center.y
        form.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
        val row = category.fetchSemanticsNode().boundsInWindow
        assertTrue("A category should pass behind the save button", footer.center.y in row.top..row.bottom)
        assertEquals(footer, save.fetchSemanticsNode().boundsInWindow)
        capture("form-under-save")

        form.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, viewport.height * 3) }
        val last = node(hasText(str(R.string.create_new_category)) and hasClickAction()).assertIsDisplayed()
        assertTrue(last.fetchSemanticsNode().boundsInWindow.bottom <= footer.top)
        capture("form-end")
    }

    @Test fun keyboardLeavesSaveActionVisibleOnTheForm() {
        direction(R.string.expenses)
        act(R.string.finance_add_expense)
        val amount = node(hasText(str(R.string.sum)) and hasSetTextAction()).performScrollTo()
        amount.performClick().performTextReplacement("12,34")
        compose.waitUntil(8000) {
            activity { androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        }
        val save = node(hasText(str(R.string.finance_save_entry)) and hasClickAction()).assertIsDisplayed()
        val keyboardTop = activity {
            val decor = it.window.decorView
            decor.height - androidx.core.view.ViewCompat.getRootWindowInsets(decor)!!
                .getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
        }
        assertTrue(save.fetchSemanticsNode().boundsInWindow.bottom <= keyboardTop + 1)
        val comment = node(hasText(str(R.string.comment)) and hasSetTextAction()).performScrollTo()
        comment.performClick().performTextReplacement(note)
        comment.assertIsFocused().assertIsDisplayed()
        assertTrue(comment.fetchSemanticsNode().boundsInWindow.bottom <= save.fetchSemanticsNode().boundsInWindow.top)
        capture("keyboard")
        back()
        awaitText("12,34")
        awaitText(note)
    }

    @Test fun monthBoundaryUsesTheSelectedDayInTheForm() {
        direction(R.string.expenses)
        val day = LedgerDate.today()
        act(LedgerDate.display(day))
        act(R.string.finance_period_day)
        val january = "2026-01-31"
        chooseCalendarDay(january)
        act(R.string.finance_apply_period)
        act(LedgerDate.display(january))
        act(R.string.month)
        node(hasText(str(R.string.month)) and isSelected()).assertIsDisplayed()
        node(hasText(str(R.string.finance_period_month_day)) and hasClickAction()).assertIsDisplayed()
        act(R.string.finance_apply_period)
        desc(R.string.finance_next)
        total("0")
        act(R.string.finance_add_expense)
        awaitText(LedgerDate.display("2026-02-01"))
        input(R.string.sum, "8")
        act(R.string.food)
        act(R.string.finance_save_entry)
        total("8")
        desc(R.string.finance_previous)
        total("0")
    }

    @Test fun fractionalYenCannotBeSaved() {
        direction(R.string.expenses)
        currency("RUB", R.string.jpy)
        act(R.string.finance_add_expense)
        act(R.string.food)
        input(R.string.sum, "9,25")
        act(R.string.finance_save_entry)
        awaitText(str(R.string.repayment_invalid))
        input(R.string.sum, "9")
        act(R.string.finance_save_entry)
        total("9", "JPY")
    }

    @Test fun calendarDateSurvivesDeviceZoneChange() {
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 35)
        direction(R.string.expenses)
        val zone = TimeZone.getDefault().id
        fun changeZone(id: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("cmd alarm set-timezone $id")
            ).use { it.readBytes() }
            compose.waitUntil(5000) { TimeZone.getDefault().id == id }
        }
        try {
            changeZone("Pacific/Kiritimati")
            act(R.string.finance_add_expense)
            input(R.string.sum, "5")
            input(R.string.comment, note)
            act(R.string.food)
            act(R.string.finance_save_entry)
            total("5")
            val day = requireNotNull(dao.getAllFinances().single { it.info == note }.calendarDay)
            changeZone("America/Adak")
            act(R.string.food)
            awaitText(LedgerDate.display(day))
            editEntry(note)
            awaitText(LedgerDate.display(day))
            act(R.string.finance_save_entry)
            awaitText(note)
            assertEquals(day, dao.getAllFinances().single { it.info == note }.calendarDay)
        } finally {
            changeZone(zone)
        }
    }

    @Test fun predictiveBackRestoresDraftAndReturnsToHistoryAndSummary() {
        requirePredictiveGestures(context)
        direction(R.string.expenses)
        act(R.string.finance_add_expense)
        input(R.string.sum, "255,75")
        input(R.string.comment, note)
        act(R.string.food)
        act(R.string.create_new_category)
        node(hasSetTextAction() and hasText(str(R.string.name))).performTextReplacement("Draft category")
        gesture(R.id.financeCategoryView, R.id.financeFormView, "255,75", cancel = true)
        awaitText("Draft category")
        gesture(R.id.financeCategoryView, R.id.financeFormView, note, cancel = false)
        node(hasText(str(R.string.food)) and isSelected())
        awaitText("255,75")
        act(R.string.finance_save_entry)
        total("255.75")
        act(R.string.food)
        editEntry(note)
        input(R.string.sum, "301")
        gesture(R.id.financeFormView, R.id.financeHistoryView, note, cancel = true)
        awaitText("301")
        gesture(R.id.financeFormView, R.id.financeHistoryView, note, cancel = false)
        awaitText(note)
        for (cancel in listOf(true, false)) {
            gesture(R.id.financeHistoryView, R.id.financeSummaryView, str(R.string.finance_add_expense), cancel)
            if (cancel) awaitText(note)
        }
        total("255.75")
    }

    private fun periodLabel(period: FinancePeriod): String {
        val days = period.days()
        return "${LedgerDate.display(days.start)} – ${LedgerDate.display(days.endInclusive)}"
    }

    private fun chooseCalendarDay(day: String) {
        val field = hasSetTextAction()
        val switchToInput = hasContentDescription(str(androidx.compose.material3.R.string.m3c_date_picker_switch_to_input_mode))
        node(field or switchToInput)
        if (compose.onAllNodes(field).fetchSemanticsNodes().isEmpty()) {
            node(switchToInput).performClick()
        }
        val (year, month, date) = day.split('-')
        // The compact date picker uses the device's numeric date order.
        val digits = android.text.format.DateFormat.getDateFormatOrder(context).joinToString("") {
            when (it) { 'd' -> date; 'M' -> month; else -> year }
        }
        node(field).performTextReplacement(digits)
        act(str(android.R.string.ok))
    }

    private fun str(id: Int) = context.getString(id)
    private fun node(match: SemanticsMatcher): SemanticsNodeInteraction {
        compose.waitUntil(20000) { compose.onAllNodes(match).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        return compose.onNode(match)
    }
    private fun awaitText(text: String) { node(hasText(text)) }
    private fun act(id: Int) = act(str(id))
    private fun act(text: String) {
        node(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }
    private fun desc(id: Int) = node(hasContentDescription(str(id))).performClick()
    private fun direction(id: Int) = act(id)
    private fun input(label: Int, value: String) {
        node(hasSetTextAction() and hasText(str(label))).performScrollTo().performTextReplacement(value)
    }
    private fun editEntry(label: String) {
        act(label)
        act(R.string.finance_edit_entry)
    }
    private fun currency(from: String, to: Int) {
        act(from)
        awaitDestination(R.id.currencyPickerFragment)
        desc(R.string.search_currency)
        node(hasSetTextAction()).performTextReplacement(str(to).substringAfterLast(' '))
        act(to)
        awaitDestination(R.id.financeFragment)
        awaitText(str(to).substringAfterLast(' '))
    }
    private fun awaitDestination(id: Int) {
        compose.waitUntil(8000) { activity { it.navController.currentDestination?.id == id } }
    }
    private fun summaryList() = node(verticalList)
    private fun total(amount: String, currency: String = "RUB") {
        val lists = compose.onAllNodes(verticalList)
        if (lists.fetchSemanticsNodes().size == 1) lists[0].performScrollToIndex(0)
        awaitText(amount.toBigDecimal().financeMoney(currency))
    }
    private val verticalList = hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun <T> activity(block: (MainActivity) -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync {
            val current = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            result = block(current)
        }
        return requireNotNull(result)
    }

    private fun gesture(out: Int, into: Int, previewText: String, cancel: Boolean) {
        compose.waitForIdle()
        SystemClock.sleep(450)
        if (activity { androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }) {
            back()
            SystemClock.sleep(400)
        }
        val outgoing = activity { it.findViewById<View>(out) }
        instrumentation.edgeSwipe(cancel) {
            capture("preview-$out-$cancel")
            assertTrue("Preview must show $previewText while the pointer is down", activity {
                val view = it.findViewById<ComposeView>(into) ?: return@activity false
                val root = view.getChildAt(0) as RootForTest
                view.isShown && view.alpha > 0 && outgoing.translationX > 0 &&
                    root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true).any(hasText(previewText)::matches)
            })

        }
        SystemClock.sleep(450)
    }

    private fun capture(label: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "finance-$label.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
