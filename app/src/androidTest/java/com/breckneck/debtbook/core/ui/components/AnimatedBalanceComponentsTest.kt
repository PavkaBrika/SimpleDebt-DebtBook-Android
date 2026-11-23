package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.math.BigDecimal
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnimatedBalanceComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rollsChangingSignsAndKeepsAnUnchangedSignStill() {
        val amount = mutableStateOf(BigDecimal.ZERO)
        fun label(value: BigDecimal) = when (value.signum()) {
            1 -> "+$value"
            -1 -> "−${value.abs()}"
            else -> "0"
        }
        compose.setContent {
            AnimatedAmount(amount.value, "${label(amount.value)}\u00a0₽")
        }

        fun layers(text: String) = compose.onAllNodes(
            hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        fun SemanticsNode.showsFirstCharacter() = config[SemanticsProperties.Text].single().spanStyles.none {
            it.start == 0 && it.end >= 1 && it.item.color == Color.Transparent
        }
        val restY = layers(label(BigDecimal.ZERO)).single().positionInRoot.y

        compose.mainClock.autoAdvance = false
        try {
            fun change(old: String, new: String, movesSign: Boolean, showsOutgoingSign: Boolean) {
                val value = BigDecimal(new)
                compose.runOnIdle { amount.value = value }
                compose.mainClock.advanceTimeBy(128)

                val targetLayers = layers(label(value))
                val moving = targetLayers.single { abs(it.positionInRoot.y - restY) > 1f }
                val stationary = targetLayers.single { it.id != moving.id }
                if (value.signum() != 0) {
                    assertEquals(movesSign, moving.showsFirstCharacter())
                    assertEquals(!movesSign, stationary.showsFirstCharacter())
                }
                val previous = BigDecimal(old)
                if (previous.signum() != 0) {
                    assertEquals(showsOutgoingSign, layers(label(previous)).single().showsFirstCharacter())
                }
                compose.mainClock.advanceTimeBy(500)
            }
            change("0", "75", movesSign = true, showsOutgoingSign = false)
            change("75", "875", movesSign = false, showsOutgoingSign = false)
            change("875", "-30", movesSign = true, showsOutgoingSign = true)
            change("-30", "0", movesSign = false, showsOutgoingSign = true)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun currencyFollowsTheWidthOfTheAmountOnTheSameLine() {
        val amount = mutableStateOf(BigDecimal("9"))
        val currency = compose.onNodeWithText("\u00a0₽", useUnmergedTree = true)
        compose.setContent { AnimatedAmount(amount.value, "${amount.value}\u00a0₽") }
        fun currencyPosition() = currency.fetchSemanticsNode().positionInRoot
        val narrow = currencyPosition()

        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { amount.value = BigDecimal("100") }
            compose.mainClock.advanceTimeBy(144)
            val growing = currencyPosition()
            compose.mainClock.advanceTimeBy(500)
            val wide = currencyPosition()

            compose.runOnIdle { amount.value = BigDecimal("9") }
            compose.mainClock.advanceTimeBy(144)
            val shrinking = currencyPosition()
            compose.mainClock.advanceTimeBy(500)

            assertTrue(growing.x > narrow.x && growing.x < wide.x)
            assertTrue(shrinking.x > narrow.x && shrinking.x < wide.x)
            for (position in listOf(growing, wide, shrinking, currencyPosition())) {
                assertEquals(narrow.y, position.y, 1f)
            }
            assertEquals(narrow.x, currencyPosition().x, 1f)
            compose.onNodeWithText("9\u00a0₽").assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

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
