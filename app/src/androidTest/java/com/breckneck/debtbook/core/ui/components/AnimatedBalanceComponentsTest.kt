package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnimatedBalanceComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rollsUpForDepositsAndDownForWithdrawalsWhileProgressMovesBetweenBalances() {
        val amount = mutableStateOf(BigDecimal("99.50"))
        compose.setContent {
            Column {
                AnimatedAmount(amount.value, "${amount.value} €")
                AnimatedLinearProgressIndicator(amount.value.toFloat() / 200f)
            }
        }
        val bar = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        fun progress() = bar.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        val startTop = compose.onNodeWithText("99.50 €").getUnclippedBoundsInRoot().top
        assertEquals(.4975f, progress(), .0001f)

        compose.mainClock.autoAdvance = false
        try {
            fun change(old: String, new: String, increasing: Boolean) {
                compose.runOnIdle { amount.value = BigDecimal(new) }
                compose.mainClock.advanceTimeBy(128)
                val outgoing = compose.onNodeWithText("$old €", useUnmergedTree = true)
                val top = outgoing.getUnclippedBoundsInRoot().top
                assertTrue("Outgoing digits should move with the balance", if (increasing) top < startTop else top > startTop)
                val intermediate = progress()
                assertTrue("Progress should pass through an intermediate value", intermediate > .4975f && intermediate < .75f)
                compose.onNodeWithText("$new €").assertIsDisplayed()
                compose.mainClock.advanceTimeBy(500)
                outgoing.assertDoesNotExist()
            }
            change("99.50", "150", increasing = true)
            assertEquals(.75f, progress(), .0001f)
            change("150", "99.50", increasing = false)
            assertEquals(.4975f, progress(), .0001f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
