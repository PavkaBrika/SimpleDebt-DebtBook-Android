package com.breckneck.debtbook.goal

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.goal.main.mapper.formatGoalMoney
import database.AppDataBase
import entity.GoalData
import entity.GoalDepositData
import java.io.File
import java.text.NumberFormat
import java.util.Date
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CompletedGoalJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dao get() = AppDataBase(context).appDao()
    private val prefs get() = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private lateinit var savedPreferences: Map<String, *>
    private val goalIds = mutableListOf<Int>()
    private var cover: File? = null

    @Before fun prepare() {
        savedPreferences = prefs.all
        val dark = InstrumentationRegistry.getArguments().getString("bubbleTheme") == "dark"
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true)
            .putString("app_theme", context.getString(if (dark) R.string.dark_theme else R.string.light_theme))
            .putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).commit()
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { it.finish() }
        }
        goalIds.forEach { id -> dao.getGoal(id)?.let(dao::deleteGoal) }
        cover?.delete()
        val editor = prefs.edit()
        for (key in listOf("pin_code_is_enabled", "ads_disabled", "app_theme", "debts_quantity_for_app_rate_dialog_show")) {
            when (val old = savedPreferences[key]) {
                is Boolean -> editor.putBoolean(key, old)
                is Int -> editor.putInt(key, old)
                is String -> editor.putString(key, old)
                null -> editor.remove(key)
            }
        }
        editor.commit()
    }

    @Test fun reachesTargetAndOpensTheSameConfirmationFromListAndHistory() {
        val name = "Наушники"
        val file = File.createTempFile("cover", ".png", context.cacheDir).also { cover = it }
        val bitmap = Bitmap.createBitmap(90, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val id = seed(name, "18000", "12000", "RUB", file.path)
        dao.addGoalDeposit(GoalDepositData(0, 5000.toBigDecimal(), Date(), id))
        dao.addGoalDeposit(GoalDepositData(0, (-500).toBigDecimal(), Date(), id))
        openGoals(name)

        val photoNode = compose.onNodeWithContentDescription(str(R.string.goal_s_photo), useUnmergedTree = true)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            compose.waitUntil(8000) {
                val pixels = photoNode.captureToImage().toPixelMap()
                val center = pixels[pixels.width / 2, pixels.height / 2]
                center.blue > .9f && center.red < .1f
            }
        }
        val photo = photoNode.fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(photo.width > photo.height && photo.bottom <= title.top)
        capture("cover")
        // The cover can place the edit action behind the floating New goal button.
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        text(R.string.goal_edit_action).performClick()
        awaitDestination(R.id.createGoalsFragment)
        waitText(name)
        backTo(R.id.goalsFragment)

        compose.onNodeWithText(name).performClick()
        awaitDestination(R.id.goalDetailsFragment)
        compose.onNodeWithContentDescription(str(R.string.goal_actions)).performClick()
        text(R.string.goal_delete).assertIsDisplayed()
        text(R.string.goal_edit).performClick()
        awaitDestination(R.id.createGoalsFragment)
        backTo(R.id.goalDetailsFragment)
        backTo(R.id.goalsFragment)
        topUp("1500")
        val amount = NumberFormat.getNumberInstance().format(18_000)
        val caption = str(R.string.goal_balance, "$amount\u00a0₽")
        waitText(caption)
        capture("list")
        text(R.string.goal_edit_action).assertDoesNotExist()
        text(R.string.goal_top_up).assertDoesNotExist()
        compose.onNodeWithContentDescription(str(R.string.goal_s_photo)).assertDoesNotExist()
        val history = dao.getGoalDepositsByGoalId(id)
        assertEquals(3, history.size)

        trash(name).performClick()
        confirmSheet(name)
        capture("headphones-confirmation")
        text(R.string.cancel).performClick()
        awaitDestination(R.id.goalsFragment)
        compose.onNodeWithText(caption).assertIsDisplayed()
        assertEquals(history, dao.getGoalDepositsByGoalId(id))

        compose.onNodeWithText(name).performClick()
        awaitDestination(R.id.goalDetailsFragment)
        compose.onNodeWithContentDescription(str(R.string.goal_actions)).assertDoesNotExist()
        val list = compose.onNode(hasScrollToIndexAction())
        capture("details")
        list.performScrollToNode(hasText(str(R.string.goal_withdrawal)))
        list.performScrollToNode(hasText(str(R.string.goal_deposit)))
        trash(name).performClick()
        confirmSheet(name)
        text(R.string.cancel).performClick()
        awaitDestination(R.id.goalDetailsFragment)
        assertEquals(history, dao.getGoalDepositsByGoalId(id))
        trash(name).performClick()
        confirmSheet(name)
        text(R.string.delete).performClick()
        awaitDestination(R.id.goalsFragment)
        compose.waitUntil(8000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
        assertNull(dao.getGoal(id))
        assertTrue(dao.getGoalDepositsByGoalId(id).isEmpty())
    }

    @Test fun showsActualForeignCurrencySavingsAndDeletesOnlyTheSelectedRow() {
        val name = "Concert"
        val id = seed(name, "100", "90", "EUR")
        val other = seed("Other goal", "100", "130", "USD")
        openGoals(name)
        compose.onNodeWithContentDescription(str(R.string.goal_s_photo)).assertDoesNotExist()
        topUp("35.25")
        val number = NumberFormat.getNumberInstance().format("125.25".toBigDecimal())
        waitText(str(R.string.goal_balance, "$number\u00a0€"))
        assertTrue(dao.getGoal(id)!!.savedSum.compareTo("125.25".toBigDecimal()) == 0)
        trash(name).performClick()
        confirmSheet(name)
        text(R.string.delete).performClick()
        compose.waitUntil(8000) { dao.getGoal(id) == null && compose.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
        assertTrue(dao.getGoalDepositsByGoalId(id).isEmpty())
        assertNotNull(dao.getGoal(other))
        trash("Other goal").assertIsDisplayed()
    }

    @Test fun completionInDetailsReplacesTheMenuAndRefreshesTheList() {
        val name = "New speakers"
        val id = seed(name, "500", "420", "USD")
        openGoals(name)
        compose.onNodeWithText(name).performClick()
        awaitDestination(R.id.goalDetailsFragment)
        compose.onNodeWithContentDescription(str(R.string.goal_actions)).assertIsDisplayed()
        topUp("80")
        compose.waitUntil(8000) {
            compose.onAllNodesWithContentDescription(str(R.string.goal_delete_named, name)).fetchSemanticsNodes().isNotEmpty()
        }
        trash(name).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.goal_actions)).assertDoesNotExist()
        backTo(R.id.goalsFragment)
        trash(name).assertIsDisplayed()
        assertEquals(1, dao.getGoalDepositsByGoalId(id).size)
    }

    @Test fun deletingFromTheListSlidesTheSheetDownAndMovesTheNextRowUp() {
        val first = "First completed goal"
        val second = "Second completed goal"
        val firstId = seed(first, "100", "100", "EUR")
        seed(second, "100", "100", "EUR")
        openGoals(first)
        val nextRow = compose.onNodeWithText(second)
        val initialRowTop = nextRow.fetchSemanticsNode().positionInWindow.y
        trash(first).performClick()
        val title = compose.onAllNodesWithText(str(R.string.goal_delete_title))
        val initialSheetTop = title.fetchSemanticsNodes().single().positionInWindow.y

        val rowPositions = mutableListOf<Float>()
        checkSheetExit(title, initialSheetTop, text(R.string.delete), { dao.getGoal(firstId) == null }) {
            rowPositions.add(nextRow.fetchSemanticsNode().positionInWindow.y)
        }
        val finalRowTop = nextRow.fetchSemanticsNode().positionInWindow.y
        assertTrue("The remaining row should pass through intermediate positions",
            rowPositions.any { it < initialRowTop - 1 && it > finalRowTop + 1 })
    }

    @Test fun savingADepositSlidesTheSheetDownAndMovesTheEarlierMovement() {
        val name = "Trip fund"
        val id = seed(name, "500", "10", "USD")
        val oldAmount = 20.toBigDecimal()
        dao.addGoalDeposit(GoalDepositData(0, oldAmount, Date(), id))
        openGoals(name)
        compose.onNodeWithText(name).performClick()
        awaitDestination(R.id.goalDetailsFragment)
        val oldLabel = oldAmount.formatGoalMoney("USD", showPlus = true)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(oldLabel))
        val movement = compose.onAllNodesWithText(oldLabel)
        val initialTop = movement.fetchSemanticsNodes().single().positionInWindow.y
        text(R.string.goal_top_up).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("30")
        val title = compose.onAllNodesWithText(str(R.string.goal_add_money))
        val initialSheetTop = title.fetchSemanticsNodes().single().positionInWindow.y

        val movementPositions = mutableListOf<Float>()
        checkSheetExit(title, initialSheetTop, compose.onNodeWithTag("goal_adjustment_confirm"),
            { dao.getGoalDepositsByGoalId(id).size == 2 }) {
            movement.fetchSemanticsNodes().singleOrNull()?.let { movementPositions.add(it.positionInWindow.y) }
        }
        val furthestTop = movementPositions.max()
        assertTrue("The earlier movement should move down gradually",
            movementPositions.any { it > initialTop + 1 && it < furthestTop - 1 })
    }

    private fun checkSheetExit(
        title: SemanticsNodeInteractionCollection,
        initialTop: Float,
        confirm: SemanticsNodeInteraction,
        committed: () -> Boolean,
        sampleRows: () -> Unit,
    ) {
        val sheetPositions = mutableListOf<Float>()
        compose.mainClock.autoAdvance = false
        try {
            confirm.performClick()
            compose.waitUntil(8000, committed)
            repeat(48) {
                compose.mainClock.advanceTimeBy(32)
                title.fetchSemanticsNodes().singleOrNull()?.let { sheetPositions.add(it.positionInWindow.y) }
                sampleRows()
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        title.assertCountEquals(0)
        assertTrue("The sheet should travel down before it disappears", sheetPositions.any { it > initialTop + 1 })
    }

    private fun capture(label: String) {
        val theme = InstrumentationRegistry.getArguments().getString("capture") ?: return
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(context.getExternalFilesDir(null), "completed-goal-$label-$theme.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun seed(name: String, target: String, saved: String, code: String, photo: String? = null): Int {
        require(dao.getAllGoals().none { it.name == name }) { "Use a dedicated test device without a goal named $name" }
        dao.insertGoal(GoalData(0, name, target.toBigDecimal(), saved.toBigDecimal(), code, photo, Date(), null))
        return dao.getAllGoals().single { it.name == name }.id.also { goalIds.add(it) }
    }

    private fun openGoals(name: String) {
        val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
        awaitDestination(R.id.mainFragment)
        compose.onNode(hasText(str(R.string.goals)) and isSelectable()).performClick()
        awaitDestination(R.id.goalsFragment)
        waitText(name)
    }

    private fun topUp(amount: String) {
        text(R.string.goal_top_up).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(amount)
        compose.onNodeWithTag("goal_adjustment_confirm").performClick()
    }

    private fun trash(name: String) = compose.onNodeWithContentDescription(str(R.string.goal_delete_named, name))
        .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)

    private fun confirmSheet(name: String) {
        text(R.string.goal_delete_title).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.goal_delete_message, name)).assertIsDisplayed()
        text(R.string.delete).assertIsDisplayed()
        text(R.string.cancel).assertIsDisplayed()
        capture("confirmation")
    }

    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun text(id: Int) = compose.onNodeWithText(str(id))
    private fun waitText(value: String) = compose.waitUntil(8000) {
        compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
    }
    private fun backTo(id: Int) {
        compose.waitForIdle()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitDestination(id)
    }
    private fun awaitDestination(id: Int) = compose.waitUntil(30000) {
        var ready = false
        instrumentation.runOnMainSync {
            ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().any { activity ->
                    val entry = activity.navController.currentBackStackEntry
                    val host = activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as? NavHostFragment
                    entry?.destination?.id == id && entry.lifecycle.currentState == Lifecycle.State.RESUMED &&
                        host?.childFragmentManager?.primaryNavigationFragment?.isResumed == true && activity.hasWindowFocus()
                }
        }
        ready
    }
}
