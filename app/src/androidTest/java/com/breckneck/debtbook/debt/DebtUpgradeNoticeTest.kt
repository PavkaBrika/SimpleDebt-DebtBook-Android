package com.breckneck.debtbook.debt

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebtUpgradeNoticeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dismissalButtonsIgnoreTapsDuringTheInitialDelay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val confirm = compose.onNodeWithText(context.getString(R.string.debt_upgrade_got_it))
        val close = compose.onNodeWithContentDescription(context.getString(R.string.debt_upgrade_close_notice))
        var dismissals = 0

        compose.mainClock.autoAdvance = false
        try {
            compose.setContent {
                DebtBookTheme {
                    DebtUpgradeNotice(DebtUpgradeTopics(otherCurrencies = true, earlyEntries = true)) {
                        dismissals++
                    }
                }
            }

            compose.mainClock.advanceTimeBy(1_000)
            confirm.assertIsNotEnabled().performTouchInput { click() }
            close.assertIsNotEnabled().performTouchInput { click() }
            compose.runOnIdle { assertEquals(0, dismissals) }

            compose.mainClock.advanceTimeBy(600)
            close.assertIsEnabled()
            confirm.assertIsEnabled().performTouchInput { click() }
            compose.runOnIdle { assertEquals(1, dismissals) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
