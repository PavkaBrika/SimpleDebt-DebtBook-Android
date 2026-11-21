package com.breckneck.debtbook.debt

import android.content.Context
import android.os.Process
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.debt.details.DebtDetailsViewModel
import com.breckneck.debtbook.debt.main.DebtViewModel
import com.breckneck.debtbook.debt.reminders.DebtReminders
import com.breckneck.debtbook.debt.reminders.enterDebtDate
import com.breckneck.debtbook.debt.reminders.openDueCalendarInForm
import com.breckneck.debtbook.debt.reminders.screenString
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyDisplay
import com.breckneck.deptbook.domain.util.ScreenState
import database.AppDataBase
import entity.Debt
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** On a dedicated test AVD, run settle and reopen separately to check a real process restart. */
class SettledDebtJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao get() = AppDataBase(context).appDao()
    private val args = InstrumentationRegistry.getArguments()
    private val phase = args.getString("r23Phase", "both")
    private val name = "Анна R2.3 " + (args.getString("r23Run") ?: System.nanoTime().toString(36))
    private lateinit var screen: ActivityScenario<MainActivity>
    private val person get() = dao.getAllHuman().single { it.name == name }
    private fun records() = dao.getAllDebtsById(person.id)
    private fun issued() = records().single { it.repaymentOfId == null && it.sum.signum() > 0 }
    private fun borrowed() = records().single { it.repaymentOfId == null && it.sum.signum() < 0 }
    private fun repayments(): List<Debt> {
        val originalId = issued().id
        return records().filter { it.repaymentOfId == originalId }.sortedBy { it.id }
    }
    private fun fragment(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment)
            .childFragmentManager.primaryNavigationFragment!!
    private fun tap(id: Int) = onView(withId(id)).perform(click())
    private fun await(condition: () -> Boolean) = compose.waitUntil(20_000, condition)
    private fun amount(value: Int) = MoneyDisplay.format(value.toBigDecimal(), "RUB")

    @Test fun oppositeLoansPartialSettlementAndUndo() {
        require(phase in listOf("both", "settle", "reopen"))
        require(phase == "both" || args.containsKey("r23Run")) { "Use the same r23Run for both processes" }
        val marker = context.getSharedPreferences("r23_test_process", Context.MODE_PRIVATE)
        if (phase == "reopen") {
            assertTrue("Run settle first", marker.contains(name))
            assertNotEquals("Expected a new app process", marker.getInt(name, -1), Process.myPid())
        } else {
            assertFalse("Select a fresh r23Run", dao.getAllHuman().any { it.name == name })
        }
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
        val old = prefs.all
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true)
            .putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).putString("default_currency", "RUB").commit()
        var keepForRestart = false
        try {
            if (phase != "reopen") {
                assertTrue("Grant notifications on the test AVD", DebtReminders.get(context).allowed())
                ActivityScenario.launch(MainActivity::class.java).use { screen = it; settle() }
                if (phase == "settle") {
                    marker.edit().putInt(name, Process.myPid()).commit()
                    keepForRestart = true
                }
            }
            if (phase != "settle") {
                ActivityScenario.launch(MainActivity::class.java).use { screen = it; reopenAndFinish() }
            }
        } finally {
            if (!keepForRestart) {
                dao.getAllHuman().singleOrNull { it.name == name }?.let {
                    dao.deleteDebtsByHumanId(it.id)
                    dao.deleteHumanById(it.id)
                }
                marker.edit().remove(name).commit()
            }
            prefs.edit().apply {
                for (key in listOf("pin_code_is_enabled", "ads_disabled", "debts_quantity_for_app_rate_dialog_show", "default_currency")) {
                    when (val value = old[key]) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is String -> putString(key, value)
                        null -> remove(key)
                    }
                }
            }.commit()
        }
    }

    private fun settle() {
        tap(R.id.addHumanButton)
        onView(withId(R.id.humanNameEditText)).perform(replaceText(name), closeSoftKeyboard())
        onView(withId(R.id.debtSumEditText)).perform(replaceText("1000"), closeSoftKeyboard())
        compose.openDueCalendarInForm()
        compose.enterDebtDate(screen, LocalDate.now().plusDays(10))
        compose.onNodeWithTag("dueReminderSwitch").performClick()
        compose.onNodeWithTag("dueSaveButton").performClick()
        tap(R.id.setFinanceButton)
        await { dao.getAllHuman().any { it.name == name } && records().size == 1 }
        awaitRows(issued().id)
        val token = checkNotNull(issued().reminderToken)
        tap(R.id.addDebtButton)
        onView(withId(R.id.customSwitch)).perform(
            GeneralClickAction(Tap.SINGLE, { view ->
                val point = GeneralLocation.CENTER.calculateCoordinates(view)
                point[0] += view.width * 0.25f
                point
            }, Press.FINGER)
        )
        onView(withId(R.id.debtSumEditText)).perform(replaceText("1000"), closeSoftKeyboard())
        tap(R.id.setFinanceButton)
        await { records().size == 2 }
        val lent = issued().id
        val owed = borrowed().id
        awaitRows(lent, owed)
        onView(withId(R.id.overallSumTextView)).check(matches(withText("0 RUB")))
        tap(R.id.shareHumanButton)
        tap(R.id.deleteButton) // Text
        val statement = onView(withId(R.id.statementText))
        for ((label, total) in listOf(R.string.statement_lent to 1000, R.string.statement_borrowed to 1000,
            R.string.statement_even to 0)) {
            statement.check(matches(withText(containsString(screen.screenString(label).format("${amount(total)} RUB")))))
        }
        tap(R.id.cancelButton)
        tap(R.id.settledDebtsTab)
        awaitRows()
        reopenFromPeople(positive = true, negative = true, balance = 0)
        pay(lent, 400)
        val first = repayments().single().id
        awaitRows(lent, owed, first)
        assertRemaining(lent, 600)
        checkRow(borrowed(), R.string.debt_borrowed)
        assertEquals(token, issued().reminderToken)
        tap(R.id.settledDebtsTab)
        awaitRows()
        reopenFromPeople(positive = true, negative = true, balance = -400)
        pay(lent, 600)
        awaitRows(owed)
        assertNull(issued().reminderMinute)
        assertNull(issued().reminderToken)
        checkClosedHistory()
        reopenFromPeople(positive = false, negative = true, balance = -1000)
    }

    private fun reopenAndFinish() {
        filtersAndOpen(positive = false, negative = true, balance = -1000)
        val lent = issued().id
        val owed = borrowed().id
        awaitRows(owed)
        checkClosedHistory()
        val (first, last) = repayments()
        row(last.id).perform(click())
        compose.onNodeWithTag("deleteButton").performClick()
        compose.onNode(hasText(screen.screenString(R.string.repayment_cancel)) and hasClickAction()).performClick()
        await { records().single { it.id == last.id }.cancelled }
        awaitRows()
        tap(R.id.openDebtsTab)
        awaitRows(lent, owed, first.id, last.id)
        assertRemaining(lent, 600)
        checkRow(last, R.string.repayment_cancelled)
        assertNull(issued().reminderMinute)
        assertNull(issued().reminderToken)
        reopenFromPeople(positive = true, negative = true, balance = -400)

        pay(lent, 600)
        awaitRows(owed)
        pay(owed, 1000)
        awaitRows()
        reopenFromPeople(positive = false, negative = false, balance = 0)
        awaitRows()
        tap(R.id.settledDebtsTab)
        awaitRows(*records().map { it.id }.toIntArray())
        checkRow(borrowed(), R.string.debt_borrowed)
        checkRow(records().single { it.repaymentOfId == owed }, R.string.repayment_paid)
    }

    private fun checkClosedHistory() {
        tap(R.id.settledDebtsTab)
        val (first, last) = repayments()
        awaitRows(issued().id, first.id, last.id)
        checkRow(issued(), R.string.debt_lent)
        checkRow(first, R.string.repayment_received)
        checkRow(last, R.string.repayment_received)
        assertEquals(0, (-400).toBigDecimal().compareTo(first.sum))
        assertEquals(0, (-600).toBigDecimal().compareTo(last.sum))
    }

    private fun pay(id: Int, value: Int) {
        val oldCount = records().size
        row(id).perform(click())
        compose.onNodeWithTag("repaymentButton").performClick()
        val input = compose.onNodeWithTag("repaymentAmountInput")
        input.performTextReplacement(value.toString())
        input.assertTextContains(value.toString())
        compose.onNodeWithTag("repaymentSaveButton").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        await { records().size == oldCount + 1 }
    }

    private fun reopenFromPeople(positive: Boolean, negative: Boolean, balance: Int) {
        tap(R.id.backButton)
        filtersAndOpen(positive, negative, balance)
    }

    private fun filtersAndOpen(positive: Boolean, negative: Boolean, balance: Int) {
        for ((filter, expected) in listOf(R.id.showPositiveRadioButton to positive,
            R.id.showNegativeRadioButton to negative, R.id.showAllRadioButton to true)) {
            tap(R.id.filterHumanButton)
            tap(filter)
            tap(R.id.confirmButton)
            await {
                var ready = false
                screen.onActivity {
                    val vm = ViewModelProvider(fragment(it))[DebtViewModel::class.java]
                    val people = vm.resultedHumanList.value
                    val index = people?.indexOfFirst { p -> p.name == name } ?: -1
                    ready = people != null && vm.screenState.value != ScreenState.LOADING && (index >= 0) == expected
                    if (ready && expected) it.findViewById<RecyclerView>(R.id.humanRecyclerView).scrollToPosition(index)
                }
                ready
            }
            if (expected) {
                onView(withText(name)).check(matches(isDisplayed()))
                onView(allOf(withId(R.id.humanCardView), hasDescendant(withText(name))))
                    .check(matches(hasDescendant(allOf(withId(R.id.debt), withText(amount(balance))))))
            } else {
                // Hidden RecyclerViews may retain children while the empty-state fade finishes.
                await {
                    try {
                        onView(allOf(withText(name), isDisplayed())).check(doesNotExist())
                        true
                    } catch (_: AssertionError) {
                        false
                    }
                }
            }
        }
        onView(withText(name)).perform(click())
    }

    private fun awaitRows(vararg ids: Int) {
        await {
            var ready = false
            screen.onActivity { activity ->
                if (activity.navController.currentDestination?.id == R.id.debtDetailsFragment) {
                    val vm = ViewModelProvider(fragment(activity))[DebtDetailsViewModel::class.java]
                    val rows = vm.visibleDebtRows.value
                    val list = activity.findViewById<RecyclerView>(R.id.debtsRecyclerView)
                    ready = vm.isDeleting.value != true && rows?.map { it.id }?.toSet() == ids.toSet() &&
                        list?.adapter?.itemCount == ids.size
                }
            }
            ready
        }
        if (ids.isEmpty()) onView(withId(R.id.emptyDebtsLayout)).check(matches(isDisplayed()))
    }

    private fun row(id: Int): ViewInteraction {
        screen.onActivity {
            val vm = ViewModelProvider(fragment(it))[DebtDetailsViewModel::class.java]
            val index = vm.visibleDebtRows.value.orEmpty().indexOfFirst { record -> record.id == id }
            assertTrue("Missing record $id", index >= 0)
            it.findViewById<RecyclerView>(R.id.debtsRecyclerView).scrollToPosition(index)
        }
        return onView(withTagValue(equalTo(id))).perform(scrollTo())
    }

    private fun assertRemaining(id: Int, expected: Int) {
        row(id).check(matches(hasDescendant(withText(containsString(screen.screenString(R.string.debt_remaining)
            .format(amount(expected), "RUB"))))))
    }

    private fun checkRow(debt: Debt, title: Int) {
        val card = row(debt.id)
        val formatted = MoneyDisplay.format(debt.sum, "RUB")
        card.check(matches(hasDescendant(withText(if (debt.sum.signum() > 0) "+$formatted" else formatted))))
            .check(matches(hasDescendant(withText("RUB"))))
            .check(matches(hasDescendant(withText(title))))
            .perform(click())
        val line = "${MoneyDisplay.format(debt.sum.abs(), "RUB")} RUB · ${LedgerDate.display(debt.date)}"
        compose.onNodeWithText(line).assertIsDisplayed()
        compose.onNodeWithTag("debtActionsCancel").performClick()
    }
}
