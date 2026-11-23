package com.breckneck.debtbook.finance

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.components.BubbleBalanceCard
import com.breckneck.debtbook.core.ui.components.bubbleBalancePalette
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.finance.main.FinancePeriodControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FinancePeriodControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cardPassesThroughIntermediateHeightsInBothDirections() {
        val awayFromCurrentPeriod = mutableStateOf(false)
        compose.setContent {
            DebtBookTheme {
                val palette = bubbleBalancePalette()
                BubbleBalanceCard(
                    title = "Income",
                    currency = "USD",
                    balance = "100",
                    onCurrencyClick = {},
                    modifier = Modifier.width(288.dp).testTag("finance-period-card"),
                    palette = palette,
                ) {
                    FinancePeriodControls(
                        label = "23 Nov 2026",
                        palette = palette,
                        onPrevious = {},
                        onNext = {},
                        onSelectPeriod = {},
                        onCurrentPeriod = if (awayFromCurrentPeriod.value) ({}) else null,
                    )
                }
            }
        }

        fun height() = compose.onNodeWithTag("finance-period-card").fetchSemanticsNode().boundsInRoot.height

        fun heightsAfter(show: Boolean): List<Float> {
            compose.runOnIdle { awayFromCurrentPeriod.value = show }
            return List(20) {
                compose.mainClock.advanceTimeByFrame()
                height()
            }
        }

        val collapsed = height()
        compose.mainClock.autoAdvance = false
        try {
            val entering = heightsAfter(true)
            val expanded = height()
            assertTrue(expanded > collapsed)
            assertTrue(entering.distinct().count { it > collapsed && it < expanded } >= 3)

            val exiting = heightsAfter(false)
            assertTrue(exiting.distinct().count { it > collapsed && it < expanded } >= 3)
            assertEquals(collapsed, height(), 1f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
