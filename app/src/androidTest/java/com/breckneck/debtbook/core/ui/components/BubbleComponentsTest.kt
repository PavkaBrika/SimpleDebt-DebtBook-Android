package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.finance.details.screen.FinanceHistoryRow
import com.breckneck.debtbook.finance.financeMoney
import androidx.compose.foundation.layout.Column
import com.breckneck.deptbook.domain.model.Finance
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BubbleComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactBalanceCardKeepsItsFooterAndCurrencyAction() {
        val currency = mutableStateOf("RUB")
        compose.setContent {
            DebtBookTheme {
                BubbleBalanceCard(
                    title = "Balance",
                    currency = currency.value,
                    balance = "100",
                    onCurrencyClick = { currency.value = "EUR" },
                    modifier = Modifier.width(240.dp),
                ) { compact -> Text(if (compact) "Compact breakdown" else "Wide breakdown") }
            }
        }

        compose.onNodeWithText("Compact breakdown").assertIsDisplayed()
        compose.onNodeWithText("100").assertIsDisplayed()
        compose.onNodeWithText("RUB").assertHasClickAction().performClick()
        compose.onNodeWithText("EUR").assertIsDisplayed()
    }

    @Test fun amountFieldUsesTheSelectedCurrencyForFractionalInput() {
        val currency = mutableStateOf("JPY")
        var lastEdit = ""
        compose.setContent {
            DebtBookTheme {
                BubbleAmountField(
                    state = rememberTextFieldState(),
                    currency = currency.value,
                    onValueChange = { lastEdit = it },
                ) {
                    TextButton(onClick = { currency.value = "USD" }) { Text(currency.value) }
                }
            }
        }

        compose.onNode(hasSetTextAction()).performTextReplacement("12.50")
        compose.runOnIdle { assertEquals("", lastEdit) }
        compose.onNodeWithText("JPY").performClick()
        compose.onNodeWithText("USD").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("12.50")
        compose.runOnIdle { assertEquals("12.50", lastEdit) }
    }

    @Test fun financeHistoryRowRetainsItsAmountNoteAndClickTarget() {
        val first = Finance(1, "120".toBigDecimal(), Date(), "Lunch", 1)
        val second = first.copy(id = 2, sum = "36".toBigDecimal(), info = "Coffee")
        var selected: Finance? = null
        compose.setContent {
            DebtBookTheme {
                Column {
                    for (entry in listOf(first, second)) {
                        FinanceHistoryRow(entry, "Food", { selected = entry }, {})
                    }
                }
            }
        }

        compose.onNodeWithText(second.sum.financeMoney("USD")).assertIsDisplayed()
        compose.onNodeWithText("Coffee").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(second, selected) }
    }
}
