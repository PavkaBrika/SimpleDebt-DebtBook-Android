package com.breckneck.debtbook.goal

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
import android.view.View
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.goal.main.mapper.formatGoalMoney
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
    private val goalNames = listOf(initialName, editedName, finalName)
    private lateinit var oldPrefs: Map<String, *>

    @Before fun launch() {
        oldPrefs = prefs.all
        val theme = if (InstrumentationRegistry.getArguments().getString("bubbleTheme") == "dark") R.string.dark_theme else R.string.light_theme
        prefs.edit().putString("app_theme", context.getString(theme)).putString("default_currency", "RUB")
            .putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true)
            .putInt("debts_quantity_for_app_rate_dialog_show", Int.MAX_VALUE).commit()
        cleanGoals()
        val launchIntent = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
        awaitDestination(R.id.mainFragment, timeoutMillis = 45_000)
        tab(R.string.goals, R.id.goalsFragment)
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
        awaitDestination(R.id.createGoalsFragment)
        click(R.string.goal_save)
        node(R.string.youmustentername).performScrollTo().assertIsDisplayed()
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performTextReplacement("20000")
        textField(R.string.already_saved_sum).performTextReplacement("1000")
        click(R.string.goal_save)
        awaitDestination(R.id.goalsFragment)
        assertBalance(initialName, "1000", 0)
        compose.waitUntil(10000) { !keyboardVisible() }
        capture("active")

        click(R.string.goal_top_up)
        textField(R.string.sum).performTextReplacement("2500")
        compose.onNodeWithTag("goal_adjustment_confirm").performClick()
        compose.waitUntil(8000) { dao.getAllGoals().any { it.name == initialName && it.savedSum.compareTo("3500".toBigDecimal()) == 0 } }
        openGoal(initialName)

        click(R.string.goal_withdraw)
        textField(R.string.sum).performTextReplacement("3501")
        compose.onNodeWithTag("goal_adjustment_confirm").performClick()
        node(R.string.goal_overdrawn).performScrollTo().assertIsDisplayed()
        assertBalance(initialName, "3500", 1)
        textField(R.string.sum).performTextReplacement("300")
        compose.onNodeWithTag("goal_adjustment_confirm").performClick()
        awaitHistory()
        assertBalance(initialName, "3200", 2)
        assertDisplayedAmount("3200")
        assertDisplayedAmount("16800")
        capture("details")

        ordinaryBack(R.id.goalsFragment)
        click(R.string.goal_edit_action)
        awaitDestination(R.id.createGoalsFragment)
        textField(R.string.name).performTextReplacement(editedName)
        click(R.string.goal_save)
        awaitDestination(R.id.goalsFragment)
        openGoal(editedName)
        awaitHistory()
        assertBalance(editedName, "3200", 2)
        scrollToGoalContent(R.string.goal_edit).performClick()
        awaitDestination(R.id.createGoalsFragment)
        textField(R.string.name).performTextReplacement(finalName)
        click(R.string.goal_save)
        awaitDestination(R.id.goalDetailsFragment)
        compose.onNodeWithText(finalName).assertIsDisplayed()
        awaitHistory()

        // Scroll back to the balance actions before completing the goal.
        scrollToGoalContent(R.string.goal_top_up).performClick()
        textField(R.string.sum).performTextReplacement("16800")
        compose.onNodeWithTag("goal_adjustment_confirm").performClick()
        awaitText(R.string.goal_reached)
        assertBalance(finalName, "20000", 3)
        compose.onAllNodesWithText(context.getString(R.string.goal_top_up)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.goal_withdraw)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.goal_edit)).assertCountEquals(0)
        ordinaryBack(R.id.goalsFragment)
        node(R.string.goal_reached).assertIsDisplayed()
        capture("complete")

        openGoal(finalName)
        scrollToGoalContent(R.string.goal_delete).performClick()
        click(R.string.cancel)
        assertBalance(finalName, "20000", 3)
        scrollToGoalContent(R.string.goal_delete).performClick()
        click(R.string.delete)
        awaitDestination(R.id.goalsFragment)
        awaitText(R.string.goal_empty_list)
        assertTrue(dao.getAllGoals().none { it.name == finalName })
    }

    @Test fun tabSelectionAndSystemInsetsFollowNavHost() {
        for ((title, id) in listOf(
            R.string.debts to R.id.mainFragment, R.string.finances to R.id.financeFragment,
            R.string.settings to R.id.settingsFragment, R.string.goals to R.id.goalsFragment,
        )) {
            tab(title, id)
            val oldEntry = onMain { it.navController.currentBackStackEntry }
            tab(title, id)
            assertTrue(oldEntry === onMain { it.navController.currentBackStackEntry })
        }
        click(R.string.goal_new)
        awaitDestination(R.id.createGoalsFragment)
        assertTrue(onMain { !it.findViewById<android.view.View>(R.id.bottomNavigationView).isShown } == true)
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        awaitDestination(R.id.goalsFragment)
        assertTrue(onMain { it.findViewById<android.view.View>(R.id.bottomNavigationView).isShown } == true)
    }

    @Test fun rotationPreservesFormDraftAndCurrency() {
        click(R.string.goal_new)
        awaitDestination(R.id.createGoalsFragment)
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performTextReplacement("20000")
        textField(R.string.already_saved_sum).performTextReplacement("1000")
        if (keyboardVisible()) ordinaryBack(R.id.createGoalsFragment)
        compose.waitUntil(10000) { !keyboardVisible() }
        val rub = context.getString(R.string.rub)
        val yen = context.getString(R.string.jpy)
        compose.onNodeWithText(context.getString(R.string.a11y_currency_picker, rub)).performScrollTo().performClick()
        compose.onNodeWithText(yen).performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText(context.getString(R.string.select_currency)).fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        try {
            rotate(UiAutomation.ROTATION_FREEZE_90, Configuration.ORIENTATION_LANDSCAPE)
            awaitDestination(R.id.createGoalsFragment)
            compose.onNodeWithText(initialName).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("20000").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("1000").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.a11y_currency_picker, yen)).performScrollTo().assertIsDisplayed()
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

    @Test fun edgeSwipePreviewsThePreviousFragmentAndCanBeCancelled() {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        assumeTrue(Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2)
        click(R.string.goal_new)
        awaitDestination(R.id.createGoalsFragment)
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performTextReplacement("20000")
        if (keyboardVisible()) ordinaryBack(R.id.createGoalsFragment)
        compose.waitUntil(10000) { !keyboardVisible() }
        node(R.string.goal_date_optional).performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
        ordinaryBack(R.id.createGoalsFragment)
        compose.waitUntil(10000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        if (keyboardVisible()) ordinaryBack(R.id.createGoalsFragment)
        compose.waitUntil(10000) { !keyboardVisible() }
        swipeBack(current = R.id.createGoalComposeView, previous = R.id.goalsComposeView, cancel = true, label = "new-goal")
        awaitDestination(R.id.createGoalsFragment)
        compose.onNodeWithText(initialName).assertIsDisplayed()
        compose.onNodeWithText("20000").assertIsDisplayed()
        assertTrue(dao.getAllGoals().none { it.name == initialName })
        swipeBack(current = R.id.createGoalComposeView, previous = R.id.goalsComposeView, label = "new-goal")
        awaitDestination(R.id.goalsFragment)

        click(R.string.goal_new)
        awaitDestination(R.id.createGoalsFragment)
        textField(R.string.name).performTextReplacement(initialName)
        textField(R.string.goal_target_amount).performTextReplacement("20000")
        click(R.string.goal_save)
        awaitDestination(R.id.goalsFragment)
        awaitGoal(initialName)

        click(R.string.goal_edit_action)
        awaitDestination(R.id.createGoalsFragment)
        swipeBack(current = R.id.createGoalComposeView, previous = R.id.goalsComposeView, label = "edit-from-list")
        awaitDestination(R.id.goalsFragment)
        openGoal(initialName)
        scrollToGoalContent(R.string.goal_edit).performClick()
        awaitDestination(R.id.createGoalsFragment)
        swipeBack(current = R.id.createGoalComposeView, previous = R.id.goalDetailsComposeView, label = "edit-from-details")
        awaitDestination(R.id.goalDetailsFragment)
        swipeBack(current = R.id.goalDetailsComposeView, previous = R.id.goalsComposeView, label = "details")
        awaitDestination(R.id.goalsFragment)
    }

    private fun rotate(rotation: Int, orientation: Int) {
        // Android owns configuration changes. Keep the test clock from forcing a layout
        // while the old window is being replaced.
        compose.mainClock.autoAdvance = false
        try {
            assertTrue(automation.setRotation(rotation))
            compose.waitUntil(10000) {
                onMain { activity ->
                    activity.resources.configuration.orientation == orientation &&
                        activity.findViewById<View>(R.id.createGoalComposeView)?.isLaidOut == true
                } == true
            }
            instrumentation.waitForIdleSync()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun swipeBack(current: Int, previous: Int, cancel: Boolean = false, label: String) {
        compose.waitForIdle()
        val (width, height) = checkNotNull(onMain { it.window.decorView.run { width to height } })
        val outgoing = checkNotNull(onMain { it.findViewById<View>(current) })
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
            // Inspect the actual animated views while the system still owns the pointer stream.
            val deadline = SystemClock.uptimeMillis() + 2500
            var preview = false
            while (!preview && SystemClock.uptimeMillis() < deadline) {
                preview = onMain { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment
                    val incoming = host.requireView().findViewById<View>(previous)
                    // FragmentContainerView keeps drawing the outgoing View after removing its child.
                    incoming != null && incoming.isShown && incoming.alpha > 0 && outgoing.translationX > 0
                } == true
                if (!preview) SystemClock.sleep(32)
            }
            capture("preview-$label${if (cancel) "-before-cancel" else ""}")
            assertTrue("Previous fragment did not appear during $label edge swipe", preview)
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
    private fun openGoal(name: String) {
        awaitGoal(name)
        compose.onNodeWithText(name).performClick()
        awaitDestination(R.id.goalDetailsFragment)
    }
    private fun awaitText(id: Int) = awaitGoal(context.getString(id))
    private fun awaitHistory() {
        scrollToGoalContent(R.string.history)
        awaitText(R.string.goal_withdrawal)
        scrollToGoalContent(R.string.goal_deposit).assertIsDisplayed()
        scrollToGoalContent(R.string.goal_withdrawal).assertIsDisplayed()
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
    private fun tab(title: Int, destination: Int) {
        compose.onNode(hasText(context.getString(title)) and isSelectable()).performClick()
        awaitDestination(destination)
        compose.onNode(hasText(context.getString(title)) and isSelected()).assertIsDisplayed()
    }
    private fun ordinaryBack(destination: Int) {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitDestination(destination)
    }
    private fun awaitDestination(id: Int, timeoutMillis: Long = 10000) {
        compose.waitUntil(timeoutMillis) {
            onMain { activity ->
                val entry = activity.navController.currentBackStackEntry
                val host = activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment
                val fragment = host.childFragmentManager.primaryNavigationFragment
                entry?.destination?.id == id && fragment != null && fragment.tag == entry.id &&
                    fragment.isResumed && fragment.view?.isShown == true
            } == true
        }
        compose.waitForIdle()
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
