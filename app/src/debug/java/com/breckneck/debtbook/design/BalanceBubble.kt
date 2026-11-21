package com.breckneck.debtbook.design

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.components.BubbleBalanceCard
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Preview(name = "Баланс · день", widthDp = 390, locale = "ru")
@Preview(name = "Баланс · ночь", widthDp = 390, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Баланс · крупный шрифт", widthDp = 320, fontScale = 1.5f, locale = "ru")
@Composable
private fun BalanceBubblePreview() {
    var dollars by remember { mutableStateOf(false) }
    DebtBookTheme {
        BubbleBalanceCard(
            title = "Общий баланс",
            currency = if (dollars) "USD" else "RUB",
            balance = if (dollars) "+80 $" else "+18 300 ₽",
            showCoins = true,
            onCurrencyClick = { dollars = !dollars },
            modifier = Modifier.padding(16.dp),
        ) { compact ->
            val colors = bubbleColors()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = colors.heroBorder)
                val receivable = if (dollars) "80 $" else "22 500 ₽"
                val payable = if (dollars) "0 $" else "4 200 ₽"
                if (compact) {
                    SummaryAmount("Мне должны", receivable, colors.heroMuted)
                    SummaryAmount("Я должен", payable, colors.heroMuted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryAmount("Мне должны", receivable, colors.heroMuted, Modifier.weight(1f))
                        SummaryAmount("Я должен", payable, colors.heroMuted, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryAmount(label: String, amount: String, muted: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = muted, style = MaterialTheme.typography.labelMedium)
        Text(amount, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
    }
}
