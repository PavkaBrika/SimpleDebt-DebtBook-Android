package com.breckneck.debtbook.debt

import androidx.compose.ui.test.*
import com.breckneck.debtbook.debt.record
import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
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
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

class DebtStatementJourneyTest {
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao = AppDataBase(context).appDao()
    private val name = "Statement " + System.nanoTime().toString(36)
    private fun tap(id: Int) {
        if (id in listOf(R.id.shareHumanButton, R.id.sortButton, R.id.settledDebtsTab)) {
            val tag = context.resources.getResourceEntryName(id)
            compose.awaitTag("personHistory").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performClick()
        } else onView(withId(id)).perform(click())
    }
    private fun preview() {
        tap(R.id.shareHumanButton)
        android.os.SystemClock.sleep(500)
        onView(withId(R.id.deleteButton)).inRoot(isDialog()).perform(click()) // Text in the share format sheet
        compose.waitUntil(8000) { runCatching {
            onView(withId(R.id.sendButton)).inRoot(isDialog()).check(matches(isCompletelyDisplayed()))
        }.isSuccess }
    }
    private fun displayedText(): String {
        var text = ""
        onView(withId(R.id.statementText)).check { view, _ -> text = (view as TextView).text.toString() }
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
        val sent = java.util.concurrent.CopyOnWriteArrayList<Intent>()
        val callback = IntentCallback { if (it.action == Intent.ACTION_CHOOSER) sent.add(it) }
        val monitor = IntentMonitorRegistry.getInstance()
        monitor.addIntentCallback(callback)
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
        val rateKey = "debts_quantity_for_app_rate_dialog_show"
        val previousRateThreshold = prefs.all[rateKey] as? Int
        prefs.edit().putInt(rateKey, Int.MAX_VALUE).commit()
        try {
            val lent = add(16000, 1, "Original loan")
            add(-3600, 4, "First payment", lent.id)
            add(-2400, 7, "Second payment", lent.id)
            dao.removeDebt(add(-800, 8, "Voided payment", lent.id))
            val borrowed = add(-500, 2, "Settled loan")
            add(500, 3, "Repaid in full", borrowed.id)
            // Enough settled entries to exercise scrolling even with compact loan summaries.
            repeat(10) { index ->
                val closed = add(50, 1, "Closed $index")
                add(-50, 2, "Closed payment $index", closed.id)
            }

            ActivityScenario.launch(MainActivity::class.java).use { screen ->
                compose.onNodeWithText("USD").performClick()
                compose.onNodeWithText("RUB", substring = true).performClick()
                compose.onNodeWithTag("peopleList").performScrollToNode(hasText(name))
                compose.onNodeWithText(name).performClick()
                tap(R.id.settledDebtsTab)
                preview()
                onView(withId(R.id.statementText)).check(matches(
                    isDescendantOfA(withId(com.google.android.material.R.id.design_bottom_sheet))
                ))
                fun line(id: Int, value: String) = screen.screenString(id).format(value)
                onView(withId(R.id.includeSettled)).check(matches(isNotChecked()))
                val open = displayedText()
                assertTrue(open.contains(line(R.string.statement_remaining, "10 000 RUB")))
                assertFalse(open.contains(line(R.string.statement_borrowed, "500 RUB")))
                assertFalse(open.contains("First payment"))
                tap(R.id.includeSettled)
                onView(withId(R.id.statementScroll)).perform(swipeUp()).check { view, error ->
                    if (error != null) throw error
                    assertTrue("Long statements must scroll inside the sheet", view.scrollY > 0)
                }
                onView(withId(R.id.sendButton)).check(matches(isCompletelyDisplayed()))
                onView(withId(R.id.cancelButton)).check(matches(isCompletelyDisplayed()))
                val all = displayedText()
                for (expected in listOf(
                    line(R.string.statement_you_owe, "10 000 RUB"), line(R.string.statement_lent, "16 000 RUB"),
                    line(R.string.statement_repaid, "6 000 RUB"), line(R.string.statement_remaining, "10 000 RUB"),
                    line(R.string.statement_cancelled, "800 RUB"), line(R.string.statement_borrowed, "500 RUB"),
                    line(R.string.statement_remaining, "0 RUB")
                )) assertTrue(expected, all.contains(expected))
                tap(R.id.includeSettled)
                assertEquals(open, displayedText())
                tap(R.id.cancelButton)
                compose.awaitTag("settledDebtsTab").assertIsSelected()
                compose.record(borrowed.id).assertTextContains("Settled loan")
                assertTrue(sent.isEmpty())

                preview()
                pressBack()
                compose.awaitTag("settledDebtsTab").assertIsSelected()
                assertTrue(sent.isEmpty())

                tap(R.id.sortButton)
                onView(withId(R.id.showNegativeRadioButton)).inRoot(isDialog()).perform(scrollTo(), click())
                onView(withId(R.id.confirmButton)).inRoot(isDialog()).perform(scrollTo(), click())
                preview()
                onView(withId(R.id.includeSettled)).check(matches(isNotChecked()))
                assertTrue(displayedText().contains(screen.screenString(R.string.statement_empty)))
                tap(R.id.includeSettled)
                val selected = displayedText()
                assertTrue(selected.contains(screen.screenString(R.string.statement_negative)))
                assertTrue(selected.contains(line(R.string.statement_you_owe, "10 000 RUB")))
                assertTrue(selected.contains(line(R.string.statement_borrowed, "500 RUB")))
                assertFalse(selected.contains(line(R.string.statement_lent, "16 000 RUB")))
                screen.recreate()
                onView(withId(R.id.includeSettled)).check(matches(isChecked()))
                val shown = displayedText()
                assertEquals(selected, shown)
                compose.waitUntil(8000) {
                    runCatching { onView(withId(R.id.sendButton)).check(matches(isCompletelyDisplayed())) }.isSuccess
                }
                val send = onView(withId(R.id.sendButton)).inRoot(isDialog())
                compose.waitUntil(8000) { runCatching { send.check(matches(androidx.test.espresso.matcher.ViewMatchers.isEnabled())) }.isSuccess }
                // Wait for the recreated sheet's entrance transition before a physical button tap.
                android.os.SystemClock.sleep(500)
                send.perform(click())
                compose.waitUntil(8000) { sent.size == 1 }
                val intent = sent.single().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND, intent.action)
                assertEquals("text/plain", intent.type)
                assertEquals(shown, intent.getStringExtra(Intent.EXTRA_TEXT))
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4")
                    .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
                compose.awaitTag("settledDebtsTab").assertIsSelected()
                assertEquals(1, sent.size)
                tap(R.id.shareHumanButton)
                android.os.SystemClock.sleep(500)
                onView(withId(R.id.editButton)).inRoot(isDialog()).perform(click())
                compose.waitUntil(10000) { sent.size == 2 }
                val excel = requireNotNull(sent.last().getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
                assertEquals("application/vnd.ms-excel", excel.type)
                val uri = requireNotNull(excel.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
                context.contentResolver.openInputStream(uri)!!.use {
                    assertArrayEquals(byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte()), ByteArray(4).also { header -> java.io.DataInputStream(it).readFully(header) })
                }
            }
        } finally {
            prefs.edit().apply {
                if (previousRateThreshold == null) remove(rateKey) else putInt(rateKey, previousRateThreshold)
            }.commit()
            monitor.removeIntentCallback(callback)
            dao.deleteDebtsByHumanId(person.id)
            dao.deleteHumanById(person.id)
        }
    }
}
