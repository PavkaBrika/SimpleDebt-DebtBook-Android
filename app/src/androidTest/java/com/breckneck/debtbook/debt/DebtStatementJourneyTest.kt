package com.breckneck.debtbook.debt

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.intent.IntentCallback
import androidx.test.runner.intent.IntentMonitorRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.debt.reminders.screenString
import database.AppDataBase
import entity.Debt
import entity.Human
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

class DebtStatementJourneyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao = AppDataBase(context).appDao()
    private val name = "Statement " + System.nanoTime().toString(36)
    private fun tap(id: Int) = onView(withId(id)).perform(click())
    private fun preview() {
        tap(R.id.shareHumanButton)
        tap(R.id.deleteButton) // Text in the share format sheet
    }
    private fun displayedText(): String {
        var text = ""
        onView(withId(android.R.id.message)).check { view, _ -> text = (view as TextView).text.toString() }
        return text
    }

    @Test fun previewFromArchiveCancelFilterRecreateAndSend() {
        dao.insertHuman(Human(0, name, BigDecimal.ZERO, "RUB"))
        val person = dao.getAllHuman().single { it.name == name }
        fun add(sum: Int, day: Int, note: String, parent: Int? = null): Debt {
            dao.saveDebt(Debt(0, sum.toBigDecimal(), person.id, note, "2026-10-%02d".format(java.util.Locale.ROOT, day),
                repaymentOfId = parent, operationId = parent?.let { UUID.randomUUID().toString() }))
            return dao.getAllDebtsById(person.id).single { it.info == note }
        }
        val sent = mutableListOf<Intent>()
        val callback = IntentCallback { if (it.action == Intent.ACTION_CHOOSER) sent.add(it) }
        val monitor = IntentMonitorRegistry.getInstance()
        monitor.addIntentCallback(callback)
        try {
            val lent = add(16000, 1, "Original loan")
            add(-3600, 4, "First payment", lent.id)
            add(-2400, 7, "Second payment", lent.id)
            dao.removeDebt(add(-800, 8, "Voided payment", lent.id))
            val borrowed = add(-500, 2, "Settled loan")
            add(500, 3, "Repaid in full", borrowed.id)

            ActivityScenario.launch(MainActivity::class.java).use { screen ->
                onView(allOf(withId(R.id.humanCardView), hasDescendant(withText(name)))).perform(scrollTo(), click())
                tap(R.id.settledDebtsTab)
                preview()
                val all = displayedText()
                fun line(id: Int, value: String) = screen.screenString(id).format(value)
                for (expected in listOf(
                    line(R.string.statement_you_owe, "10 000 RUB"), line(R.string.statement_lent, "16 000 RUB"),
                    line(R.string.statement_from_you, "3 600 RUB"), line(R.string.statement_from_you, "2 400 RUB"),
                    line(R.string.statement_repaid, "6 000 RUB"), line(R.string.statement_remaining, "10 000 RUB"),
                    line(R.string.statement_cancelled, "800 RUB"), line(R.string.statement_borrowed, "500 RUB"),
                    line(R.string.statement_from_me, "500 RUB"), line(R.string.statement_remaining, "0 RUB")
                )) assertTrue(expected, all.contains(expected))
                assertTrue(all.indexOf("First payment") < all.indexOf("Second payment"))
                tap(android.R.id.button2)
                onView(withId(R.id.settledDebtsTab)).check(matches(isChecked()))
                onView(withText("Settled loan")).check(matches(isDisplayed()))
                assertTrue(sent.isEmpty())

                tap(R.id.sortButton)
                onView(withId(R.id.showNegativeRadioButton)).perform(scrollTo(), click())
                onView(withId(R.id.confirmButton)).perform(scrollTo(), click())
                preview()
                val selected = displayedText()
                assertTrue(selected.contains(screen.screenString(R.string.statement_negative)))
                assertTrue(selected.contains(line(R.string.statement_you_owe, "10 000 RUB")))
                assertTrue(selected.contains("Repaid in full"))
                assertFalse(selected.contains("Original loan"))
                screen.recreate()
                assertEquals(selected, displayedText())
                pressBack()
                assertTrue(sent.isEmpty())
                onView(withId(R.id.settledDebtsTab)).check(matches(isChecked()))

                preview()
                val shown = displayedText()
                tap(android.R.id.button1)
                val intent = sent.single().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND, intent.action)
                assertEquals("text/plain", intent.type)
                assertEquals(shown, intent.getStringExtra(Intent.EXTRA_TEXT))
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4")
                    .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
                onView(withId(R.id.settledDebtsTab)).check(matches(isChecked()))
            }
        } finally {
            monitor.removeIntentCallback(callback)
            dao.deleteDebtsByHumanId(person.id)
            dao.deleteHumanById(person.id)
        }
    }
}
