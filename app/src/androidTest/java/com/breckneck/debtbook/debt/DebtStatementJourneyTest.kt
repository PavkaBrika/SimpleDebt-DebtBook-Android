package com.breckneck.debtbook.debt

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import com.breckneck.debtbook.debt.record
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
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
    private fun tapTab(tag: String) {
        compose.awaitTag("personHistory").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    private fun preview() {
        compose.awaitTag("shareHumanButton").performClick()
        compose.awaitTag("shareDebtText").performClick()
        compose.awaitTag("sendDebtStatement").assertIsDisplayed()
    }
    private fun displayedText(): String =
        compose.awaitTag("statementText").fetchSemanticsNode().config[SemanticsProperties.Text].single().text

    private fun dismissChooser(chooser: Intent) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val chooserPackage = requireNotNull(chooser.resolveActivity(context.packageManager)).packageName
        val target = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        val sharePackages = context.packageManager.queryIntentActivities(target, 0).map { it.activityInfo.packageName } + chooserPackage
        // On API 23 the chooser can immediately open the only app that handles the file type.
        compose.waitUntil(8000) { automation.rootInActiveWindow?.packageName?.toString() in sharePackages }
        automation.executeShellCommand("input keyevent 4")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        compose.awaitTag("settledDebtsTab").assertIsSelected()
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
        val legacyBalanceKey = "sum_in_share_text"
        val previousRateThreshold = prefs.all[rateKey] as? Int
        val previousBalanceSetting = prefs.all[legacyBalanceKey] as? Boolean
        val currencyKey = "default_currency"
        val previousCurrency = prefs.all[currencyKey] as? String
        prefs.edit().putInt(rateKey, Int.MAX_VALUE).putBoolean(legacyBalanceKey, true).putString(currencyKey, "RUB").commit()
        try {
            val earlyNote = "Earlier receipt"
            val early = add(250, 1, earlyNote)
            val lent = add(16000, 1, "Original loan")
            add(-3600, 4, "First payment", lent.id)
            add(-2400, 7, "Second payment", lent.id)
            dao.removeDebt(add(-800, 8, "Voided payment", lent.id))
            dao.removeDebt(add(-300, 6, "Another voided payment", lent.id))
            val borrowed = add(-500, 2, "Settled loan")
            add(500, 3, "Repaid in full", borrowed.id)
            // Enough settled entries to exercise scrolling even with compact loan summaries.
            repeat(10) { index ->
                val closed = add(50, 1, "Closed $index")
                add(-50, 2, "Closed payment $index", closed.id)
            }

            ActivityScenario.launch(MainActivity::class.java).use { screen ->
                compose.openDebts()
                compose.awaitTag("peopleList").performScrollToNode(hasText(name))
                compose.onNodeWithText(name).performClick()
                compose.awaitTag("openDebtsTab").assertIsSelected()
                tapTab("earlyEntriesTab")
                compose.record(early.id).assertTextContains(earlyNote).assertTextContains("+250\u00a0₽")
                    .assertTextContains(screen.screenString(R.string.early_entry)).performClick()
                compose.awaitTag("editButton").assertExists()
                compose.onNodeWithTag("repaymentButton").assertDoesNotExist()
                compose.onNodeWithTag("debtActionsCancel").performClick()
                tapTab("settledDebtsTab")
                preview()
                compose.awaitTag("debtStatementSheet").assertExists()
                fun line(id: Int, value: String) = screen.screenString(id).format(value)
                val includeSettled = compose.onNodeWithTag("includeSettled").assertIsOff()
                val includeEarly = compose.onNodeWithTag("includeEarlyEntries").assertIsOff()
                val open = displayedText()
                assertFalse(open.contains(earlyNote))
                assertTrue(open.contains(line(R.string.statement_you_owe, "10 250 RUB")))
                includeEarly.performClick()
                val withEarly = displayedText()
                assertTrue(withEarly.contains(line(R.string.statement_entry, "+250 RUB")))
                assertEquals(1, withEarly.lines().count { it == earlyNote })
                assertFalse(withEarly.contains(line(R.string.statement_borrowed, "500 RUB")))
                assertTrue(open.contains(line(R.string.statement_remaining, "10 000 RUB")))
                assertFalse(open.contains(line(R.string.statement_borrowed, "500 RUB")))
                assertFalse(open.contains("Original loan"))
                assertFalse(open.contains("First payment"))
                val payments = listOf(
                    line(R.string.statement_payment, "3 600 RUB"), line(R.string.statement_cancelled, "300 RUB"),
                    line(R.string.statement_payment, "2 400 RUB"), line(R.string.statement_cancelled, "800 RUB"),
                )
                assertTrue(payments.all { it in open })
                assertTrue(payments.zipWithNext().all { (first, second) -> open.indexOf(first) < open.indexOf(second) })
                val scroll = compose.onNodeWithTag("statementScroll")
                includeSettled.performClick()
                scroll.performTouchInput { swipeUp() }
                assertTrue("Long statements must scroll inside the sheet",
                    scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0)
                compose.onNodeWithTag("sendDebtStatement").assertIsDisplayed()
                compose.onNodeWithTag("closeDebtStatement").assertIsDisplayed()
                val all = displayedText()
                for (expected in listOf(
                    line(R.string.statement_you_owe, "10 250 RUB"), line(R.string.statement_lent, "16 000 RUB"),
                    line(R.string.statement_repaid, "6 000 RUB"), line(R.string.statement_remaining, "10 000 RUB"),
                    line(R.string.statement_cancelled, "800 RUB"), line(R.string.statement_borrowed, "500 RUB"),
                    line(R.string.statement_payment, "500 RUB"), line(R.string.statement_remaining, "0 RUB")
                )) assertTrue(expected, all.contains(expected))
                assertTrue(all.contains(earlyNote))
                screen.recreate()
                compose.awaitTag("includeEarlyEntries").assertIsOn()
                includeSettled.assertIsOn()
                assertEquals(all, displayedText())
                includeEarly.performClick()
                compose.waitUntil(5000) { !displayedText().contains(earlyNote) }
                val onlySettledAndOpen = displayedText()
                assertFalse(onlySettledAndOpen.contains(earlyNote))
                assertTrue(onlySettledAndOpen.contains(line(R.string.statement_borrowed, "500 RUB")))
                includeSettled.performClick()
                assertEquals(open, displayedText())
                compose.awaitTag("closeDebtStatement").performClick()
                compose.awaitTag("settledDebtsTab").assertIsSelected()
                compose.record(borrowed.id).assertTextContains("Settled loan")
                assertTrue(sent.isEmpty())

                preview()
                pressBack()
                compose.awaitTag("settledDebtsTab").assertIsSelected()
                assertTrue(sent.isEmpty())

                val negative = hasTestTag("historyFilter-NEGATIVE")
                compose.awaitTag("personHistory").performScrollToNode(negative)
                compose.onNode(negative).performClick()
                preview()
                includeSettled.assertIsOff()
                includeEarly.assertDoesNotExist()
                assertTrue(displayedText().contains(screen.screenString(R.string.statement_empty)))
                val compactHeight = scroll.fetchSemanticsNode().size.height
                compose.mainClock.autoAdvance = false
                try {
                    includeSettled.performSemanticsAction(SemanticsActions.OnClick)
                    compose.mainClock.advanceTimeBy(96)
                    val growing = scroll.fetchSemanticsNode().size.height
                    compose.mainClock.advanceTimeBy(320)
                    val expanded = scroll.fetchSemanticsNode().size.height
                    assertTrue("Statement height $compactHeight → $growing → $expanded", compactHeight < growing && growing < expanded)
                } finally {
                    compose.mainClock.autoAdvance = true
                }
                val selected = displayedText()
                assertTrue(selected.contains(screen.screenString(R.string.statement_negative)))
                assertTrue(selected.contains(line(R.string.statement_you_owe, "10 250 RUB")))
                assertTrue(selected.contains(line(R.string.statement_borrowed, "500 RUB")))
                assertFalse(selected.contains(line(R.string.statement_lent, "16 000 RUB")))
                screen.recreate()
                compose.awaitTag("includeSettled").assertIsOn()
                val shown = displayedText()
                assertEquals(selected, shown)
                compose.awaitTag("sendDebtStatement").assertIsDisplayed().assertIsEnabled().performClick()
                compose.waitUntil(8000) { sent.size == 1 }
                val intent = sent.single().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND, intent.action)
                assertEquals("text/plain", intent.type)
                assertEquals(shown, intent.getStringExtra(Intent.EXTRA_TEXT))
                dismissChooser(sent.single())
                assertEquals(1, sent.size)
                compose.awaitTag("shareHumanButton").performClick()
                compose.awaitTag("shareDebtExcel").performClick()
                compose.waitUntil(10000) { sent.size == 2 }
                val excel = requireNotNull(sent.last().getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
                assertEquals("application/vnd.ms-excel", excel.type)
                val uri = requireNotNull(excel.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
                context.contentResolver.openInputStream(uri)!!.use {
                    assertArrayEquals(byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte()), ByteArray(4).also { header -> java.io.DataInputStream(it).readFully(header) })
                }
                dismissChooser(sent.last())
            }
        } finally {
            prefs.edit().apply {
                if (previousRateThreshold == null) remove(rateKey) else putInt(rateKey, previousRateThreshold)
                if (previousBalanceSetting == null) remove(legacyBalanceKey) else putBoolean(legacyBalanceKey, previousBalanceSetting)
                if (previousCurrency == null) remove(currencyKey) else putString(currencyKey, previousCurrency)
            }.commit()
            monitor.removeIntentCallback(callback)
            dao.deleteDebtsByHumanId(person.id)
            dao.deleteHumanById(person.id)
        }
    }
}
