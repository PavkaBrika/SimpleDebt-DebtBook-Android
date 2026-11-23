package com.breckneck.debtbook.finance.main

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.breckneck.debtbook.core.ui.components.BubbleBalancePalette
import com.breckneck.debtbook.core.ui.components.bubbleBalancePalette
import com.breckneck.debtbook.core.ui.components.drawBubbleCoinStack
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
internal fun FinanceSummaryArtwork(
    direction: FinanceCategoryState,
    palette: BubbleBalancePalette,
    modifier: Modifier = Modifier,
) {
    val outgoing = direction == FinanceCategoryState.EXPENSE
    val ink = if (outgoing) Color(0xFFFFBB9F) else Color(0xFFB6D98D)
    Canvas(modifier) {
        val ratio = minOf(size.width / 94f, size.height / 106f)
        translate((size.width - 94f * ratio) / 2f, (size.height - 106f * ratio) / 2f) {
            scale(ratio, ratio, Offset.Zero) {
                translate(top = 30f) {
                    drawBubbleCoinStack(palette.container, ink)
                }
                val near = Offset(55f, 36f)
                val far = Offset(85f, 9f)
                val tip = if (outgoing) far else near
                val tail = if (outgoing) near else far
                val side = if (outgoing) 13f else -13f
                drawLine(ink, tail, tip, strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(ink, tip, tip + Offset(-side, 0f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(ink, tip, tip + Offset(0f, side), strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }
    }
}
