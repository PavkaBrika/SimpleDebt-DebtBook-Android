package com.breckneck.debtbook.goal

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.goal.main.mapper.toUi
import com.breckneck.debtbook.goal.main.screen.GoalBalanceCard
import com.breckneck.debtbook.goal.main.screen.GoalItem
import com.breckneck.deptbook.domain.model.Goal
import java.text.NumberFormat
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GoalProgressTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullBarPrecedesCollapseAndCheckAppearsInTheFinishedRow() {
        val goal = Goal(
            name = "Trip", sum = 1000.toBigDecimal(), savedSum = 800.toBigDecimal(),
            currency = "EUR", photoPath = null, creationDate = Date(), goalDate = null,
        )
        val card = mutableStateOf(goal.toUi())
        val visit = mutableIntStateOf(0)
        compose.setContent {
            key(visit.intValue) {
                GoalItem(card.value, {}, {}, {}, {}, Modifier.testTag("goal"))
            }
        }
        val bar = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true)
        val reached = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.goal_reached)
        val check = compose.onAllNodesWithContentDescription(reached, useUnmergedTree = true)
        fun height() = compose.onNodeWithTag("goal").fetchSemanticsNode().boundsInRoot.height
        val expanded = height()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { card.value = goal.copy(savedSum = 1050.toBigDecimal()).toUi() }
            var fullFramesBeforeCollapse = 0
            val collapsingHeights = mutableListOf<Float>()
            var heightAtCheck: Float? = null
            repeat(40) {
                compose.mainClock.advanceTimeBy(32)
                val height = height()
                val fill = bar.fetchSemanticsNodes().firstOrNull()?.config?.get(SemanticsProperties.ProgressBarRangeInfo)?.current
                if (fill == 1f && height == expanded) fullFramesBeforeCollapse++
                if (height < expanded) {
                    assertTrue("100% should be visible before the card folds", fullFramesBeforeCollapse >= 2)
                    collapsingHeights.add(height)
                }
                if (heightAtCheck == null && check.fetchSemanticsNodes().isNotEmpty()) heightAtCheck = height
            }
            val compact = height()
            assertTrue("The card should pass through several intermediate heights",
                collapsingHeights.distinct().count { it > compact && it < expanded } >= 3)
            assertEquals(compact, checkNotNull(heightAtCheck), 1f)
            assertTrue(bar.fetchSemanticsNodes().isEmpty())

            // Returning to the list must show a saved completed goal immediately.
            compose.runOnIdle { visit.intValue++ }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(compact, height(), 0f)
            assertTrue(check.fetchSemanticsNodes().isNotEmpty())
            assertTrue(bar.fetchSemanticsNodes().isEmpty())
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun percentageFollowsTheFillWhenTheBalanceRisesAndFalls() {
        val goal = Goal(
            name = "Trip", sum = 10000000.toBigDecimal(), savedSum = 2500000.toBigDecimal(),
            currency = "EUR", photoPath = null, creationDate = Date(), goalDate = null,
        )
        val card = mutableStateOf(goal.toUi())
        compose.setContent { GoalBalanceCard(card.value) }
        val bar = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        val formatter = NumberFormat.getPercentInstance()
        fun fill() = bar.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        fun expectPercent(percent: Int) {
            compose.onNodeWithText(formatter.format(percent / 100.0)).assertIsDisplayed()
        }
        fun setBalance(value: String) = compose.runOnIdle {
            card.value = goal.copy(savedSum = value.toBigDecimal()).toUi()
        }
        fun sampleFrames(previous: Float, increasing: Boolean): Float {
            var last = previous
            repeat(3) {
                compose.mainClock.advanceTimeBy(64)
                val current = fill()
                assertTrue("Unexpected fill change from $last to $current", if (increasing) current > last else current < last)
                expectPercent((current * 100).toInt())
                last = current
            }
            return last
        }

        expectPercent(25)
        compose.mainClock.autoAdvance = false
        try {
            setBalance("7500000")
            val beforeWithdrawal = sampleFrames(.25f, increasing = true)
            assertTrue(beforeWithdrawal < .75f)

            // Withdraw while the deposit is still animating.
            setBalance("1000000")
            compose.mainClock.advanceTimeByFrame()
            val reversal = fill()
            assertTrue(reversal < .75f)
            expectPercent((reversal * 100).toInt())
            sampleFrames(reversal, increasing = false)
            compose.mainClock.advanceTimeBy(500)
            expectPercent(10)

            // Float rounds this fraction to 0.5, but the balance is one cent short of half.
            setBalance("4999999.99")
            compose.mainClock.advanceTimeBy(500)
            assertEquals(.5f, fill(), 0f)
            expectPercent(49)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
