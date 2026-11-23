package com.breckneck.debtbook.finance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBalanceCard
import com.breckneck.debtbook.core.ui.components.bubbleBalancePalette
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.finance.main.CategoryTotal
import com.breckneck.debtbook.finance.main.FinanceSummaryChart
import com.breckneck.debtbook.finance.main.FinanceTotals
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FinanceChartExpansionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ringMovesAndGrowsWhileTheCardExpandsThenCollapses() {
        val open = mutableStateOf(false)
        val amount = 60.toBigDecimal()
        val category = FinanceCategory(name = "Salary", state = FinanceCategoryState.INCOME, color = "#C5E1A5", image = 0x1F4B5)
        val totals = FinanceTotals(listOf(CategoryTotal(category, amount, 100)), amount)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val label = hasText(context.getString(R.string.finance_category_shares))
        val shares = compose.onNode(label and hasClickAction())
        val card = compose.onNodeWithTag("balance-card")

        compose.setContent {
            DebtBookTheme {
                val palette = bubbleBalancePalette()
                val progress by animateFloatAsState(if (open.value) 1f else 0f, tween(460), label = "Shares")
                BubbleBalanceCard(
                    title = "Income",
                    currency = "RUB",
                    balance = "60 ₽",
                    onCurrencyClick = {},
                    modifier = Modifier.width(288.dp).testTag("balance-card"),
                    palette = palette,
                    artwork = {
                        FinanceSummaryChart(totals, palette, open.value, progress, { open.value = !open.value }, it)
                    },
                    artworkExpansion = progress,
                )
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(label and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().isLineEllipsized(0))
        val closedRing = shares.fetchSemanticsNode().boundsInRoot
        val closedHeight = card.fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.autoAdvance = false
        try {
            shares.performClick()
            compose.mainClock.advanceTimeBy(144)
            val openingRing = shares.fetchSemanticsNode().boundsInRoot
            val openingHeight = card.fetchSemanticsNode().boundsInRoot.height
            compose.mainClock.advanceTimeBy(500)
            val openRing = shares.fetchSemanticsNode().boundsInRoot
            val openHeight = card.fetchSemanticsNode().boundsInRoot.height

            assertTrue(openingRing.top > closedRing.top && openingRing.top < openRing.top)
            assertTrue(openingRing.width > closedRing.width && openingRing.width < openRing.width)
            assertTrue(openRing.left < openingRing.left && openingRing.left < closedRing.left)
            assertTrue(openingHeight > closedHeight && openingHeight < openHeight)

            shares.performClick()
            compose.mainClock.advanceTimeBy(144)
            val closingRing = shares.fetchSemanticsNode().boundsInRoot
            val closingHeight = card.fetchSemanticsNode().boundsInRoot.height
            assertTrue(closingRing.top > closedRing.top && closingRing.top < openRing.top)
            assertTrue(closingRing.width > closedRing.width && closingRing.width < openRing.width)
            assertTrue(closingHeight > closedHeight && closingHeight < openHeight)
            compose.mainClock.advanceTimeBy(500)
            assertEquals(closedRing, shares.fetchSemanticsNode().boundsInRoot)
            assertEquals(closedHeight, card.fetchSemanticsNode().boundsInRoot.height)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
