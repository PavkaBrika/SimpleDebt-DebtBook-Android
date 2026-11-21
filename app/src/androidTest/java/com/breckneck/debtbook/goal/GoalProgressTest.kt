package com.breckneck.debtbook.goal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.breckneck.debtbook.goal.main.mapper.toUi
import com.breckneck.debtbook.goal.main.screen.GoalBalanceCard
import com.breckneck.deptbook.domain.model.Goal
import java.text.NumberFormat
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GoalProgressTest {
    @get:Rule val compose = createComposeRule()

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
