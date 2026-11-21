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
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.settings.edgeSwipe
import com.breckneck.debtbook.settings.requirePredictiveGestures
import com.breckneck.deptbook.domain.util.LedgerDate
import database.AppDataBase
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.TimeZone

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
        val theme = if (InstrumentationRegistry.getArguments().getString("theme") == "dark") R.string.dark_theme else R.string.light_theme
        prefs.edit().putBoolean("ads_disabled", true).putBoolean("pin_code_is_enabled", false)
            .putString("app_theme", str(theme)).putString("finance_currency", "USD")
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
        act(R.string.finance_add_expense)
        input(R.string.sum, "255,75")
        input(R.string.comment, note)
        act(R.string.create_new_category)
        node(hasSetTextAction() and hasText(str(R.string.name))).performTextReplacement(books)
        act(R.string.emoji_tab_education)
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
        pencil()
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

    @Test fun outsidePeriodHistoryAndCrossCurrencyCategoryDeletion() {
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
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(str(R.string.finance_today)))
        act(R.string.finance_today)
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
        pencil()
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

        act(R.string.finance_add_expense)
        focus(str(R.string.finance_save_entry))
        input(R.string.sum, "6")
        act(R.string.food)
        act(R.string.finance_save_entry)
        total("6")
        act(R.string.food)
        node(hasContentDescription(str(R.string.finance_edit_entry), substring = true)).assertIsDisplayed()
        SystemClock.sleep(350)
        focus(str(R.string.finance_edit_entry))
    }

    @Test fun keyboardLeavesSaveActionVisibleOnTheForm() {
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
        capture("keyboard")
        back()
        awaitText("12,34")
    }

    @Test fun monthBoundaryUsesTheSelectedDayInTheForm() {
        // The compact date picker uses the device's numeric date order.
        val day = LedgerDate.today()
        act(LedgerDate.display(day))
        act(R.string.goal_choose_date)
        compose.waitUntil(8000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription(str(androidx.compose.material3.R.string.m3c_date_picker_switch_to_input_mode)).performClick()
        }
        val field = node(hasSetTextAction())
        val date = requireNotNull(LedgerDate.localDate("2026-01-31"))
        val digits = android.text.format.DateFormat.getDateFormatOrder(context).joinToString("") {
            when (it) { 'd' -> "31"; 'M' -> "01"; else -> "2026" }
        }
        field.performTextReplacement(digits)
        act(str(android.R.string.ok))
        val january = LedgerDate.fromDate(date)
        act(LedgerDate.display(january))
        act(R.string.month)
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
            pencil()
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
        pencil()
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

    private fun str(id: Int) = context.getString(id)
    private fun node(match: SemanticsMatcher): SemanticsNodeInteraction {
        compose.waitUntil(20000) { compose.onAllNodes(match).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNode(match)
    }
    private fun awaitText(text: String) { node(hasText(text)) }
    private fun act(id: Int) = act(str(id))
    private fun act(text: String) {
        node(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }
    private fun desc(id: Int) = compose.onNodeWithContentDescription(str(id)).performClick()
    private fun direction(id: Int) = act(id)
    private fun input(label: Int, value: String) {
        node(hasSetTextAction() and hasText(str(label))).performScrollTo().performTextReplacement(value)
    }
    private fun pencil() = node(hasContentDescription(str(R.string.finance_edit_entry), substring = true)).performClick()
    private fun currency(from: String, to: Int) {
        act(from)
        act(to)
        awaitText(str(to).substringAfterLast(' '))
    }
    private fun total(amount: String, currency: String = "RUB") {
        val lists = compose.onAllNodes(hasScrollToIndexAction())
        if (lists.fetchSemanticsNodes().size == 1) lists[0].performScrollToIndex(0)
        awaitText(amount.toBigDecimal().financeMoney(currency))
    }
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
