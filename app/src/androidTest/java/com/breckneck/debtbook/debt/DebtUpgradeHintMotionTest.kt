package com.breckneck.debtbook.debt

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DebtUpgradeHintMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun adjacentContentMovesDuringAppearanceAndDismissal() {
        val action = mutableStateOf<Int?>(null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val following = compose.onNodeWithText("Following content")
        val close = compose.onNodeWithContentDescription(context.getString(R.string.debt_upgrade_close_history_hint))
        compose.setContent {
            DebtBookTheme {
                val hint = rememberDebtUpgradeHintTransition(action.value)
                LazyColumn {
                    item(key = "header") { Text("Header") }
                    item(key = "hint") {
                        DebtUpgradeHint(
                            transition = hint,
                            title = R.string.debt_upgrade_history_hint,
                            description = R.string.debt_upgrade_history_description,
                            dismissLabel = R.string.debt_upgrade_close_history_hint,
                            onAction = { action.value = null },
                            onDismiss = { action.value = null },
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    item(key = "following") { Text("Following content") }
                }
            }
        }

        fun followingTop() = following.fetchSemanticsNode().boundsInRoot.top

        val hidden = followingTop()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { action.value = R.string.debt_upgrade_open_early_entries }
            compose.mainClock.advanceTimeBy(96)
            val appearing = followingTop()
            compose.mainClock.advanceTimeBy(350)
            val visible = followingTop()
            assertTrue("Appearance should move the next row between its final positions", appearing > hidden && appearing < visible)

            close.performClick()
            compose.mainClock.advanceTimeBy(80)
            val disappearing = followingTop()
            assertTrue("Dismissal should move the next row between its final positions", disappearing > hidden && disappearing < visible)
            close.assertIsNotEnabled()

            compose.mainClock.advanceTimeBy(350)
            assertEquals(hidden, followingTop(), 1f)
            close.assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
