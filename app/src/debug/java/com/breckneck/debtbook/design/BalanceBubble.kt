package com.breckneck.debtbook.design

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.LocalDebtBookDarkTheme
import com.breckneck.debtbook.core.ui.theme.spacing

/** A single-currency summary. Formatting, aggregation and currency selection belong to its caller. */
@Composable
internal fun BalanceBubble(
    title: String,
    currency: String,
    balance: String,
    receivableLabel: String,
    receivable: String,
    payableLabel: String,
    payable: String,
    onCurrencyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDebtBookDarkTheme.current
    val background = if (dark) Color(0xFF123D30) else Color(0xFF164837)
    val foreground = Color(0xFFFCFFEB)
    val muted = if (dark) Color(0xFFC4D6B9) else Color(0xFFC1D6BD)
    val line = if (dark) Color(0xFF456953) else Color(0xFF47735C)
    val spacing = MaterialTheme.spacing
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = background, contentColor = foreground) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(spacing.space20)) {
            val largeText = LocalDensity.current.fontScale >= 1.3f || maxWidth < 260.dp
            if (!largeText) {
                CoinStack(background, Modifier.align(Alignment.TopEnd).size(78.dp, 69.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                Row(
                    modifier = Modifier.padding(end = if (largeText) 0.dp else 82.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, Modifier.weight(1f, fill = false), color = muted, style = MaterialTheme.typography.labelLarge)
                    Box(
                        Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onCurrencyClick)
                            .height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            Modifier.border(1.dp, line, CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(currency, style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
                        }
                    }
                }
                Text(
                    balance,
                    modifier = Modifier.padding(end = if (largeText) 0.dp else 70.dp),
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum", letterSpacing = (-1.2).sp),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(spacing.space4))
                HorizontalDivider(color = line)
                if (largeText) {
                    SummaryAmount(receivableLabel, receivable, muted)
                    SummaryAmount(payableLabel, payable, muted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.space12)) {
                        SummaryAmount(receivableLabel, receivable, muted, Modifier.weight(1f))
                        SummaryAmount(payableLabel, payable, muted, Modifier.weight(1f))
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

@Composable
private fun CoinStack(background: Color, modifier: Modifier) {
    val ink = Color(0xFFB6D98D)
    Canvas(modifier) {
        scale(size.width / 94f, size.height / 80f, Offset.Zero) {
            rotate(-20f, Offset(47f, 40f)) {
                for ((x, y) in listOf(8f to 51f, 8f to 45f, 8f to 39f, 26f to 28f, 26f to 22f, 26f to 16f)) {
                    val oval = Size(58f, 16f)
                    drawOval(background, Offset(x, y + 4f), oval)
                    drawOval(ink, Offset(x, y + 4f), oval, style = Stroke(1.2f))
                    drawOval(background, Offset(x, y), oval)
                    drawOval(ink, Offset(x, y), oval, style = Stroke(1.2f))
                }
                drawOval(ink, Offset(34f, 20f), Size(42f, 8f), style = Stroke(1.1f))
            }
        }
    }
}

@Preview(name = "Баланс · день", widthDp = 390, locale = "ru")
@Preview(name = "Баланс · ночь", widthDp = 390, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Баланс · крупный шрифт", widthDp = 320, fontScale = 1.5f, locale = "ru")
@Composable
private fun BalanceBubblePreview() {
    var dollars by remember { mutableStateOf(false) }
    DebtBookTheme {
        BalanceBubble(
            title = "Общий баланс",
            currency = if (dollars) "USD" else "RUB",
            balance = if (dollars) "+80 $" else "+18 300 ₽",
            receivableLabel = "Мне должны",
            receivable = if (dollars) "80 $" else "22 500 ₽",
            payableLabel = "Я должен",
            payable = if (dollars) "0 $" else "4 200 ₽",
            onCurrencyClick = { dollars = !dollars },
            modifier = Modifier.padding(16.dp),
        )
    }
}
