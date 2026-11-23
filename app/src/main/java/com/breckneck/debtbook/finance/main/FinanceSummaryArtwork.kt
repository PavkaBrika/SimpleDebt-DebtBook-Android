package com.breckneck.debtbook.finance.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.components.BubbleBalancePalette
import com.breckneck.debtbook.core.ui.components.bubbleBalancePalette
import com.breckneck.debtbook.core.ui.theme.LocalDebtBookDarkTheme
import com.breckneck.deptbook.domain.util.FinanceCategoryState

@Composable
internal fun financeBalancePalette(direction: FinanceCategoryState): BubbleBalancePalette =
    if (direction == FinanceCategoryState.INCOME) {
        bubbleBalancePalette()
    } else {
        BubbleBalancePalette(
            container = if (LocalDebtBookDarkTheme.current) Color(0xFF4E2A2B) else Color(0xFF643432),
            content = Color(0xFFFFF2E8),
            muted = Color(0xFFF1C4B7),
            border = Color(0xFF985E58),
        )
    }

@Composable
internal fun FinanceSummaryArtwork(direction: FinanceCategoryState, compact: Boolean, palette: BubbleBalancePalette) {
    val outgoing = direction == FinanceCategoryState.EXPENSE
    val mark = if (outgoing) Color(0xFFFFBB9F) else Color(0xFFBFE49C)
    Canvas(Modifier.size(if (compact) 42.dp else 56.dp)) {
        scale(size.width / 56f, size.height / 56f, Offset.Zero) {
            drawCircle(mark.copy(alpha = .12f), radius = 27f, center = Offset(28f, 28f))
            for (y in listOf(36f, 31f, 26f)) {
                val rim = Offset(7f, y)
                val coin = Size(28f, 11f)
                drawOval(palette.container, rim, coin)
                drawOval(mark, rim, coin, style = Stroke(1.5f))
            }
            drawOval(mark.copy(alpha = .65f), Offset(12f, 29f), Size(18f, 5f), style = Stroke(1f))
            val near = Offset(33f, 23f)
            val far = Offset(49f, 7f)
            val tip = if (outgoing) far else near
            val tail = if (outgoing) near else far
            val side = if (outgoing) 8f else -8f
            drawLine(mark, tail, tip, strokeWidth = 2.8f, cap = StrokeCap.Round)
            drawLine(mark, tip, tip + Offset(-side, 0f), strokeWidth = 2.8f, cap = StrokeCap.Round)
            drawLine(mark, tip, tip + Offset(0f, side), strokeWidth = 2.8f, cap = StrokeCap.Round)
        }
    }
}
