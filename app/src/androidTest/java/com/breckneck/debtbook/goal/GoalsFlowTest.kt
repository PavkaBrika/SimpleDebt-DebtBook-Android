package com.breckneck.debtbook.goal

import com.breckneck.debtbook.core.ui.awaitRoute
import com.breckneck.debtbook.core.ui.routeIsVisible
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.goal.main.mapper.formatGoalMoney
import entity.GoalDepositData
import entity.GoalData
import java.util.Date
import database.AppDataBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class GoalsFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val dao get() = AppDataBase(context).appDao()
    private val prefs get() = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val initialName = "Bubble QA · Trip"
    private val editedName = "Bubble QA · Renamed"
    private val finalName = "Bubble QA · Details"
    private val historyName = "Путешествие по городам Японии · Bubble QA"
    private val scrollGoals = (1..7).map { "Bubble QA · Plan $it" }
    private val goalNames = listOf(initialName, editedName, finalName, historyName) + scrollGoals
    private lateinit var oldPrefs: Map<String, *>

    @Before fun launch() {
        oldPrefs = prefs.all
        val theme = if (InstrumentationRegistry.getArguments().getString("bubbleTheme") == "dark") "DARK" else "LIGHT"
        prefs.edit().putString("app_theme", theme).putString("default_currency", "RUB")
            .putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true)
            .putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).commit()
        cleanGoals()
        launchGoalsFromLauncher()
    }

    private fun launchGoalsFromLauncher() {
        launchFromLauncher()
        tab(R.string.goals, "Goals")
    }

    private fun launchFromLauncher() {
        val previousActivity = onMain { it }
        val launchIntent = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
        compose.waitUntil(45_000) { onMain { it !== previousActivity } == true }
    }

    @After fun cleanup() {
        cleanGoals()
        val editor = prefs.edit()
        for (key in listOf("app_theme", "default_currency", "pin_code_is_enabled", "ads_disabled", "debts_quantity_for_app_rate_dialog_show")) {
            when (val value = oldPrefs[key]) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                null -> editor.remove(key)
            }
        }
        editor.commit()
    }

    @Test fun goalsFromLauncherThroughCompletionAndDeletion() {
        capture("empty")
        click(R.string.goal_new)
        compose.awaitRoute("GoalForm")
        click(R.string.goal_save)
        node(R.string.youmustentername).performScrollTo().assertIsDisplayed()
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performTextReplacement("20000")
        textField(R.string.already_saved_sum).performTextReplacement("1000")
        click(R.string.goal_save)
        compose.awaitRoute("Goals")
        assertBalance(initialName, "1000", 1)
        val goal = dao.getAllGoals().single { it.name == initialName }
        val openingDeposit = dao.getGoalDepositsByGoalId(goal.id).single()
        assertEquals(goal.creationDate, openingDeposit.date)
        assertEquals(0, goal.savedSum.compareTo(openingDeposit.sum))
        compose.waitUntil(10000) { !keyboardVisible() }
        capture("active")
        openGoal(initialName)
        for (label in listOf(R.string.goal_top_up, R.string.goal_withdraw)) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(context.getString(label), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(context.getString(label), 1, layouts.single().lineCount)
        }
        scrollToGoalContent(R.string.already_saved_sum).assertIsDisplayed()
        scrollToGoalContent("1000".toBigDecimal().formatGoalMoney("RUB", showPlus = true)).assertIsDisplayed()
        ordinaryBack("Goals")

        click(R.string.goal_top_up)
        node(R.string.goal_add_money).assertIsDisplayed()
        capture("deposit-sheet")
        textField(R.string.sum).performTextReplacement("99")
        compose.onNodeWithContentDescription(context.getString(R.string.cancel))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(8000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() && onMain { it.hasWindowFocus() } == true
        }
        assertBalance(initialName, "1000", 1)
        click(R.string.goal_top_up)
        textField(R.string.sum).performClick().performTextReplacement("2500")
        compose.onNodeWithTag("goal_adjustment_confirm").performScrollTo().assertIsDisplayed()
        capture("deposit-entry")
        compose.onNodeWithTag("goal_adjustment_confirm").performClick()
        compose.waitUntil(8000) { dao.getAllGoals().any { it.name == initialName && it.savedSum.compareTo("3500".toBigDecimal()) == 0 } }
        openGoal(initialName)

        click(R.string.goal_withdraw)
        node(R.string.goal_withdraw_money).assertIsDisplayed()
        capture("withdraw-sheet")
        textField(R.string.sum).performTextReplacement("3501")
        compose.onNodeWithTag("goal_adjustment_confirm").performClick()
        awaitText(R.string.goal_overdrawn)
        node(R.string.goal_overdrawn).performScrollTo().assertIsDisplayed()
        assertBalance(initialName, "3500", 2)
        textField(R.string.sum).performTextReplacement("300")
        compose.onNodeWithTag("goal_adjustment_confirm").performClick()
        awaitHistory()
        assertBalance(initialName, "3200", 3)
        assertDisplayedAmount("3200")
        assertDisplayedAmount("16800")
        capture("details")

        ordinaryBack("Goals")
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        click(R.string.goal_edit_action)
        compose.awaitRoute("GoalForm")
        textField(R.string.name).performTextReplacement(editedName)
        click(R.string.goal_save)
        compose.awaitRoute("Goals")
        openGoal(editedName)
        awaitHistory()
        assertBalance(editedName, "3200", 3)
        chooseGoalAction(R.string.goal_edit)
        compose.awaitRoute("GoalForm")
        textField(R.string.name).performTextReplacement(finalName)
        click(R.string.goal_save)
        compose.awaitRoute("GoalDetails")
        compose.onNodeWithText(finalName).assertIsDisplayed()
        awaitHistory()

        // Scroll back to the balance actions before completing the goal.
        scrollToGoalContent(R.string.goal_top_up).performClick()
        textField(R.string.sum).performTextReplacement("16800")
        compose.onNodeWithTag("goal_adjustment_confirm").performClick()
        awaitText(R.string.goal_reached)
        assertBalance(finalName, "20000", 4)
        compose.onAllNodesWithText(context.getString(R.string.goal_top_up)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.goal_withdraw)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.goal_edit)).assertCountEquals(0)
        compose.waitUntil(10000) { onMain { it.hasWindowFocus() } == true && !keyboardVisible() }
        ordinaryBack("Goals")
        val total = "20000".toBigDecimal().formatGoalMoney("RUB")
        compose.onNodeWithText(context.getString(R.string.goal_balance, total)).assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.goal_target_caption, total)).assertCountEquals(0)
        capture("complete")

        val deleteButton = compose.onNodeWithContentDescription(context.getString(R.string.goal_delete_named, finalName))
        deleteButton.assertIsDisplayed().performClick()
        node(R.string.goal_delete_title).assertIsDisplayed()
        click(R.string.cancel)
        openGoal(finalName)
        awaitHistory()
        deleteButton.assertIsDisplayed().performClick()
        node(R.string.goal_delete_title).assertIsDisplayed()
        capture("delete-sheet")
        click(R.string.cancel)
        assertBalance(finalName, "20000", 4)
        deleteButton.performClick()
        click(R.string.delete)
        compose.awaitRoute("Goals")
        awaitText(R.string.goal_empty_list)
        assertTrue(dao.getAllGoals().none { it.name == finalName })
    }

    @Test fun goalsCreatedWithZeroOrBlankSavingsHaveEmptyHistory() {
        for ((name, savings) in listOf(initialName to "0", editedName to "")) {
            click(R.string.goal_new)
            compose.awaitRoute("GoalForm")
            textField(R.string.name).performTextReplacement(name)
            textField(R.string.goal_target_amount).performTextReplacement("20000")
            if (savings.isNotEmpty()) textField(R.string.already_saved_sum).performTextReplacement(savings)
            click(R.string.goal_save)
            compose.awaitRoute("Goals")
            openGoal(name)
            scrollToGoalContent(R.string.goal_empty_history).assertIsDisplayed()
            assertBalance(name, "0", 0)
            scrollToGoalContent(R.string.goal_top_up).performClick()
            textField(R.string.sum).performTextReplacement("1000")
            compose.onNodeWithTag("goal_adjustment_confirm").performClick()
            scrollToGoalContent(R.string.goal_deposit).assertIsDisplayed()
            assertBalance(name, "1000", 1)
            ordinaryBack("Goals")
        }
    }

    @Test fun goalListScrollsUnderTheBarsAndClearsTheLastAction() {
        click(R.string.goal_new)
        compose.awaitRoute("GoalForm")
        dao.insertAllGoals(scrollGoals.mapIndexed { index, name ->
            GoalData(0, name, "20000".toBigDecimal(), (1000 * (index + 1)).toBigDecimal(), "RUB", null, Date(), null)
        })
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performTextReplacement("20000")
        click(R.string.goal_save)
        compose.awaitRoute("Goals")
        val list = compose.onNode(hasScrollToIndexAction())
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val screenHeight = checkNotNull(onMain { it.window.decorView.height })
        assertEquals(0f, viewport.top, 1f)
        assertEquals(screenHeight.toFloat(), viewport.bottom, 1f)
        compose.waitUntil(10000) { !keyboardVisible() }
        val createButton = node(R.string.goal_new).fetchSemanticsNode().boundsInRoot
        val sidePadding = with(compose.density) { 16.dp.toPx() }
        assertEquals(viewport.left + sidePadding, createButton.left, 1f)
        assertEquals(viewport.right - sidePadding, createButton.right, 1f)
        val goalsTab = compose.onNode(hasText(context.getString(R.string.goals)) and isSelectable())
        assertTrue("The create button should clear the tabs", createButton.bottom < goalsTab.fetchSemanticsNode().boundsInRoot.top)
        list.performScrollToIndex(3)
        val statusHeight = checkNotNull(onMain { activity ->
            ViewCompat.getRootWindowInsets(activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.statusBars()).top
        })
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, statusHeight + 60f) }
        compose.waitForIdle()
        val card = compose.onNodeWithText(scrollGoals[2]).fetchSemanticsNode().boundsInRoot
        assertTrue("A card should scroll behind the system clock", card.top < statusHeight && card.bottom > statusHeight)
        assertEquals(createButton, node(R.string.goal_new).fetchSemanticsNode().boundsInRoot)
        capture("list-under-bars")
        list.performScrollToIndex(scrollGoals.size + 1)
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, screenHeight.toFloat()) }
        val lastEdit = compose.onAllNodesWithText(context.getString(R.string.goal_edit_action)).fetchSemanticsNodes().last()
        assertTrue("Last edit action must clear the fixed button", lastEdit.boundsInRoot.bottom <= createButton.top)
        capture("list-end")
    }

    @Test fun goalHistoryScrollsBehindTheFloatingHeaderAndSystemBars() {
        val now = System.currentTimeMillis()
        dao.insertAllGoals(listOf(GoalData(0, historyName, 20000.toBigDecimal(), 1000.toBigDecimal(),
            "RUB", null, Date(now - 24 * 86_400_000L), null)))
        val goal = dao.getAllGoals().single { it.name == historyName }
        for (day in 1..24) {
            val movement = if (day % 4 == 0) -day else day
            dao.addGoalDeposit(GoalDepositData(0, movement.toBigDecimal(), Date(now - (24 - day) * 86_400_000L), goal.id))
        }
        launchGoalsFromLauncher()
        openGoal(historyName)
        val list = compose.onNode(hasScrollToIndexAction())
        val title = compose.onNodeWithText(historyName)
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        val (width, height, bars) = checkNotNull(onMain { activity ->
            val decor = activity.window.decorView
            Triple(decor.width, decor.height, ViewCompat.getRootWindowInsets(decor)!!.getInsets(WindowInsetsCompat.Type.systemBars()))
        })
        val viewport = list.fetchSemanticsNode().boundsInRoot
        assertEquals(0f, viewport.top, 1f)
        assertEquals(height.toFloat(), viewport.bottom, 1f)
        assertEquals(0f, viewport.left, 1f)
        assertEquals(width.toFloat(), viewport.right, 1f)
        assertTrue(titleBounds.top >= bars.top)
        val menu = compose.onNodeWithContentDescription(context.getString(R.string.goal_actions)).fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithContentDescription(context.getString(R.string.back)).fetchSemanticsNode().boundsInRoot
        assertEquals(back.height, titleBounds.height, 1f)
        assertEquals(menu.height, titleBounds.height, 1f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(historyName, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val fitsOneLine = layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.size.width
        assertEquals(if (fitsOneLine) 1 else 2, layout.lineCount)
        assertTrue(menu.left >= bars.left && menu.right <= width - bars.right)
        capture("detail-header")

        list.performTouchInput { swipeUp() }
        val amount = 18.toBigDecimal().formatGoalMoney("RUB", showPlus = true)
        list.performScrollToNode(hasText(amount))
        val amountNode = compose.onNodeWithText(amount)
        fun moveAmountTo(y: Float) {
            val distance = amountNode.fetchSemanticsNode().boundsInRoot.center.y - y
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
            compose.waitForIdle()
            val visible = amountNode.fetchSemanticsNode().boundsInRoot
            assertTrue("The movement must be visible at y=$y within $visible", y in visible.top..visible.bottom)
        }
        moveAmountTo(titleBounds.center.y)
        assertEquals(titleBounds, title.fetchSemanticsNode().boundsInRoot)
        capture("history-under-header")
        moveAmountTo(bars.top / 2f)
        openGoalActions()
        node(R.string.goal_edit).assertIsDisplayed()
        node(R.string.goal_delete).assertIsDisplayed()
        ordinaryBack("GoalDetails")
        val oldest = 1.toBigDecimal().formatGoalMoney("RUB", showPlus = true)
        list.performScrollToNode(hasText(oldest))
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, height.toFloat()) }
        val lastRow = compose.onNodeWithText(oldest).fetchSemanticsNode().boundsInRoot
        assertTrue(lastRow.top > titleBounds.bottom && lastRow.bottom <= height - bars.bottom)
        capture("history-end")
        ordinaryBack("Goals")
    }

    @Test fun tabSelectionAndSystemInsetsFollowNavHost() {
        for ((title, id) in listOf(
            R.string.debts to "Debts", R.string.finances to "Finances",
            R.string.settings to "Settings", R.string.goals to "Goals",
        )) {
            tab(title, id)
            tab(title, id)
            compose.awaitRoute(id)
        }
        val goalsTab = compose.onNode(hasText(context.getString(R.string.goals)) and isSelectable())
        click(R.string.goal_new)
        compose.awaitRoute("GoalForm")
        goalsTab.assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.awaitRoute("Goals")
        goalsTab.assertIsSelected().assertIsDisplayed()
    }

    @Test fun rapidTabChangesAndBackKeepTheIndicatorOnTheVisibleScreen() {
        fun checkTab(title: Int, destination: String) {
            compose.awaitRoute(destination)
            compose.onNode(hasTestTag("bubbleRootTitle") and hasText(context.getString(title))).assertIsDisplayed()
            val selected = compose.onNodeWithTag("bottomTab-${destination.lowercase()}").assertIsSelected()
            val indicator = compose.onNodeWithTag("bottomTabIndicator", useUnmergedTree = true)
            assertEquals(
                "The indicator must finish over the visible screen's tab",
                selected.fetchSemanticsNode().boundsInWindow.center.x,
                indicator.fetchSemanticsNode().boundsInWindow.center.x,
                3f,
            )
        }

        tab(R.string.finances, "Finances")
        tab(R.string.settings, "Settings")
        val bar = compose.onNodeWithTag("bubbleBottomBar-settings")
        val barOrigin = bar.fetchSemanticsNode().boundsInWindow.topLeft
        val taps = listOf("finances", "goals", "debts").map { id ->
            compose.onNodeWithTag("bottomTab-$id").fetchSemanticsNode().boundsInWindow.center - barOrigin
        }
        bar.performTouchInput { taps.forEach { click(it) } }
        checkTab(R.string.debts, "Debts")

        tab(R.string.finances, "Finances")
        checkTab(R.string.finances, "Finances")
        val previous = onMain { activity -> activity.also { it.recreate() } }
        compose.waitUntil(15_000) { onMain { it !== previous } == true }
        checkTab(R.string.finances, "Finances")
        ordinaryBack("Debts")
        checkTab(R.string.debts, "Debts")
    }

    @Test fun launcherRestoresLastTabAfterTaskIsCleared() {
        for ((title, destination) in listOf(
            R.string.finances to "Finances",
            R.string.goals to "Goals",
            R.string.debts to "Debts",
        )) {
            tab(title, destination)
            launchFromLauncher()
            compose.awaitRoute(destination)

            tab(R.string.settings, "Settings")
            launchFromLauncher()
            compose.awaitRoute(destination)
            compose.onNode(hasText(context.getString(title)) and isSelectable()).assertIsSelected()
        }
    }

    @Test fun goalFormsScrollUnderFixedControlsAndKeepInputAboveTheKeyboard() {
        click(R.string.goal_new)
        compose.awaitRoute("GoalForm")
        fun verifyForm(title: Int) {
            awaitText(R.string.goal_photo_choose)
            val form = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText(context.getString(R.string.goal_photo_choose))))
            val window = checkNotNull(onMain { it.window.decorView.height })
            val viewport = form.fetchSemanticsNode().boundsInRoot
            assertEquals(0f, viewport.top, 1f)
            assertEquals(window.toFloat(), viewport.bottom, 1f)
            val header = node(title).fetchSemanticsNode().boundsInRoot
            val back = compose.onNodeWithContentDescription(context.getString(R.string.back)).fetchSemanticsNode().boundsInRoot
            assertEquals(back.height, header.height, 1f)
            val save = node(R.string.goal_save).fetchSemanticsNode().boundsInRoot
            form.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, window.toFloat()) }
            compose.waitForIdle()
            assertTrue(node(R.string.goal_photo_choose).fetchSemanticsNode().boundsInRoot.bottom <= save.top)
            assertEquals(header, node(title).fetchSemanticsNode().boundsInRoot)
            assertEquals(save, node(R.string.goal_save).fetchSemanticsNode().boundsInRoot)
            capture(if (title == R.string.goal_new) "form-new" else "form-edit")
        }
        verifyForm(R.string.goal_new)
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performTextReplacement("20000")
        val amount = textField(R.string.already_saved_sum)
        amount.performClick().performTextReplacement("1000")
        compose.waitUntil(10000) { keyboardVisible() }
        compose.waitUntil(5000) {
            val imeTop = checkNotNull(onMain { activity ->
                val decor = activity.window.decorView
                decor.height - ViewCompat.getRootWindowInsets(decor)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
            })
            val save = node(R.string.goal_save).fetchSemanticsNode().boundsInRoot
            val field = amount.fetchSemanticsNode().boundsInRoot
            field.height > 0 && field.top >= node(R.string.goal_new).fetchSemanticsNode().boundsInRoot.bottom &&
                field.bottom <= save.top && save.bottom <= imeTop + 1
        }
        capture("form-keyboard")
        click(R.string.goal_save)
        compose.awaitRoute("Goals")
        awaitGoal(initialName)
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        click(R.string.goal_edit_action)
        compose.awaitRoute("GoalForm")
        verifyForm(R.string.goal_edit)
        ordinaryBack("Goals")
    }

    @Test fun keyboardKeepsEveryCharacterWhenMovingBetweenGoalFields() {
        click(R.string.goal_new)
        compose.awaitRoute("GoalForm")
        textField(R.string.name).performClick()
        compose.waitUntil(8000) { keyboardVisible() }
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("input text CoverReviewTrip2026"),
        ).use { it.readBytes() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        compose.onNodeWithText("CoverReviewTrip2026").performScrollTo().assertIsDisplayed()
    }

    @Test fun currencyScreenKeepsTheGoalDraft() {
        click(R.string.goal_new)
        compose.awaitRoute("GoalForm")
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performTextReplacement("20000")
        selectYen()
        awaitGoal("JPY")
        compose.onNodeWithText(initialName).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("20000").performScrollTo().assertIsDisplayed()
    }

    @Test fun rotationPreservesFormDraftAndCurrency() {
        click(R.string.goal_new)
        compose.awaitRoute("GoalForm")
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performTextReplacement("20000")
        textField(R.string.already_saved_sum).performScrollTo().performClick().performTextReplacement("1000")
        compose.waitUntil(10000) { keyboardVisible() }
        ordinaryBack("GoalForm")
        compose.waitUntil(10000) { !keyboardVisible() }
        selectYen()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText(context.getString(R.string.select_currency)).fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        try {
            rotate(UiAutomation.ROTATION_FREEZE_90, Configuration.ORIENTATION_LANDSCAPE)
            compose.awaitRoute("GoalForm")
            compose.onNodeWithText(initialName).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("20000").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("1000").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("JPY").performScrollTo().assertIsDisplayed()
            val form = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText(context.getString(R.string.goal_photo_choose))))
            val header = node(R.string.goal_new).fetchSemanticsNode().boundsInRoot
            val draftName = compose.onNodeWithText(initialName)
            draftName.performScrollTo()
            val distance = draftName.fetchSemanticsNode().boundsInRoot.center.y - header.center.y
            form.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
            compose.waitForIdle()
            val behindHeader = draftName.fetchSemanticsNode().boundsInRoot
            assertTrue("The draft must scroll beneath the header", header.center.y in behindHeader.top..behindHeader.bottom)
            assertEquals(header, node(R.string.goal_new).fetchSemanticsNode().boundsInRoot)
            capture("rotated-draft")
            assertTrue(dao.getAllGoals().none { it.name == initialName })
        } finally {
            try {
                rotate(UiAutomation.ROTATION_FREEZE_0, Configuration.ORIENTATION_PORTRAIT)
            } finally {
                automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            }
        }
    }

    @Test fun edgeSwipePreviewsThePreviousScreenAndCanBeCancelled() {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        assumeTrue(Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2)
        click(R.string.goal_new)
        compose.awaitRoute("GoalForm")
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performScrollTo().performClick().performTextReplacement("20000")
        compose.waitUntil(10000) { keyboardVisible() }
        node(R.string.goal_date_optional).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
        click(R.string.cancel)
        compose.waitUntil(10000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() &&
                onMain { it.hasWindowFocus() } == true && !keyboardVisible()
        }
        swipeBack(current = "GoalForm", previous = "Goals", cancel = true, label = "new-goal")
        compose.awaitRoute("GoalForm")
        compose.onNodeWithText(initialName).assertIsDisplayed()
        compose.onNodeWithText("20000").assertIsDisplayed()
        assertTrue(dao.getAllGoals().none { it.name == initialName })
        swipeBack(current = "GoalForm", previous = "Goals", label = "new-goal")
        compose.awaitRoute("Goals")

        click(R.string.goal_new)
        compose.awaitRoute("GoalForm")
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performTextReplacement("20000")
        click(R.string.goal_save)
        compose.awaitRoute("Goals")
        awaitGoal(initialName)

        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        click(R.string.goal_edit_action)
        compose.awaitRoute("GoalForm")
        swipeBack(current = "GoalForm", previous = "Goals", label = "edit-from-list")
        compose.awaitRoute("Goals")
        openGoal(initialName)
        chooseGoalAction(R.string.goal_edit)
        compose.awaitRoute("GoalForm")
        swipeBack(current = "GoalForm", previous = "GoalDetails", label = "edit-from-details")
        compose.awaitRoute("GoalDetails")
        swipeBack(current = "GoalDetails", previous = "Goals", cancel = true, label = "details")
        compose.awaitRoute("GoalDetails")
        swipeBack(current = "GoalDetails", previous = "Goals", label = "details")
        compose.awaitRoute("Goals")
    }

    private fun rotate(rotation: Int, orientation: Int) {
        // Android owns configuration changes. Keep the test clock from forcing a layout
        // while the old window is being replaced.
        compose.mainClock.autoAdvance = false
        try {
            assertTrue(automation.setRotation(rotation))
            compose.waitUntil(10000) {
                onMain { activity ->
                    activity.resources.configuration.orientation == orientation
                } == true && compose.routeIsVisible("GoalForm")
            }
            instrumentation.waitForIdleSync()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun swipeBack(current: String, previous: String, cancel: Boolean = false, label: String) {
        compose.waitForIdle()
        val (width, height) = checkNotNull(onMain { it.window.decorView.run { width to height } })
        val y = height * .42f
        val down = SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        touch(MotionEvent.ACTION_DOWN, 1f)
        try {
            for (step in 1..20) {
                SystemClock.sleep(18)
                touch(MotionEvent.ACTION_MOVE, width * .60f * step / 20)
            }
            compose.waitUntil(2500) { compose.routeIsVisible(previous) }
            capture("preview-$label${if (cancel) "-before-cancel" else ""}")
            assertTrue("Outgoing screen follows the gesture",
                compose.onNodeWithTag(current).fetchSemanticsNode().boundsInRoot.left > 0f)
            if (previous == "Goals") {
                compose.onNode(hasText(context.getString(R.string.goals)) and isSelected()).assertIsDisplayed()
            }
            if (cancel) {
                for (step in 19 downTo 0) {
                    SystemClock.sleep(18)
                    touch(MotionEvent.ACTION_MOVE, 1f + width * .60f * step / 20)
                }
                SystemClock.sleep(200)
            }
        } finally {
            touch(MotionEvent.ACTION_UP, if (cancel) 1f else width * .60f)
        }
    }

    private fun cleanGoals() = dao.getAllGoals().filter { it.name in goalNames }.forEach(dao::deleteGoal)
    private fun node(id: Int) = compose.onNodeWithText(context.getString(id))
    private fun click(id: Int) = node(id).performClick()
    private fun textField(id: Int): SemanticsNodeInteraction =
        compose.onNode(hasSetTextAction() and hasText(context.getString(id))).performScrollTo()

    private fun awaitGoal(name: String) = compose.waitUntil(8000) {
        compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
    }
    private fun selectYen() {
        compose.onNodeWithText("RUB").performScrollTo().performClick()
        compose.awaitRoute("Currencies")
        val search = hasContentDescription(context.getString(R.string.search_currency))
        compose.waitUntil(8000) { compose.onAllNodes(search).fetchSemanticsNodes(false).isNotEmpty() }
        compose.onNode(search).performClick()
        compose.waitUntil(8000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes(false).isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("JPY")
        val yen = context.getString(R.string.jpy)
        awaitGoal(yen)
        compose.onNodeWithText(yen).performClick()
        compose.awaitRoute("GoalForm")
    }
    private fun openGoal(name: String) {
        awaitGoal(name)
        compose.onNodeWithText(name).performClick()
        compose.awaitRoute("GoalDetails")
    }
    private fun openGoalActions() =
        compose.onNodeWithContentDescription(context.getString(R.string.goal_actions)).performClick()

    private fun chooseGoalAction(action: Int) {
        openGoalActions()
        node(action).assertIsDisplayed().performClick()
    }
    private fun awaitText(id: Int) = awaitGoal(context.getString(id))
    private fun awaitHistory() {
        // A narrow viewport may only compose the heading. Scroll to the actual
        // movement while waiting for the asynchronous history refresh.
        compose.waitUntil(8000) {
            try {
                scrollToGoalContent(R.string.goal_withdrawal).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        for (amount in listOf("2500", "1000")) {
            scrollToGoalContent(amount.toBigDecimal().formatGoalMoney("RUB", showPlus = true)).assertIsDisplayed()
        }
    }
    private fun scrollToGoalContent(id: Int): SemanticsNodeInteraction =
        scrollToGoalContent(context.getString(id))
    private fun scrollToGoalContent(text: String): SemanticsNodeInteraction {
        val matcher = hasText(text)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        return compose.onNode(matcher)
    }
    private fun assertDisplayedAmount(amount: String) {
        scrollToGoalContent(amount.toBigDecimal().formatGoalMoney("RUB")).assertIsDisplayed()
    }
    private fun assertBalance(name: String, expected: String, movements: Int) {
        val goal = dao.getAllGoals().single { it.name == name }
        assertEquals(0, expected.toBigDecimal().compareTo(goal.savedSum))
        assertEquals(movements, dao.getGoalDepositsByGoalId(goal.id).size)
    }
    private fun tab(title: Int, destination: String) {
        val tab = hasText(context.getString(title)) and isSelectable()
        compose.waitUntil(45_000) {
            compose.onAllNodes(tab).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNode(tab).performClick()
        compose.awaitRoute(destination)
        compose.onNode(hasText(context.getString(title)) and isSelected()).assertIsDisplayed()
    }
    private fun ordinaryBack(destination: String) {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.awaitRoute(destination)
    }
    private fun keyboardVisible(): Boolean = onMain {
        ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())
    } == true

    private fun <T> onMain(get: (MainActivity) -> T): T? {
        var result: T? = null
        instrumentation.runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()?.let(get)
        }
        return result
    }
    private fun capture(label: String) {
        val suffix = InstrumentationRegistry.getArguments().getString("bubbleTheme") ?: "light"
        val bitmap = automation.takeScreenshot() ?: automation.executeShellCommand("screencap -p").let {
            ParcelFileDescriptor.AutoCloseInputStream(it).use(BitmapFactory::decodeStream)
        } ?: return
        File(context.getExternalFilesDir(null), "bubble-$label-$suffix.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
