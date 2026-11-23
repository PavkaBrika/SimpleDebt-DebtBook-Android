package com.breckneck.debtbook.finance.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breckneck.debtbook.core.ui.components.BubbleBalancePalette
import com.breckneck.debtbook.core.ui.components.bubbleBalancePalette
import com.breckneck.debtbook.core.ui.theme.LocalDebtBookDarkTheme
import com.breckneck.debtbook.finance.badgeColor
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import java.math.MathContext
import java.text.NumberFormat

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
internal fun FinanceSummaryChart(
    totals: FinanceTotals,
    palette: BubbleBalancePalette,
    modifier: Modifier = Modifier,
) {
    val categories = remember(totals) {
        if (totals.total.signum() <= 0 || totals.categories.any { it.amount.signum() < 0 }) emptyList()
        else totals.categories.filter { it.amount.signum() > 0 }
    }
    val segments = remember(categories, totals.total) {
        categories.map { row ->
            row.category.badgeColor to (row.amount.divide(totals.total, MathContext.DECIMAL64).toFloat() * 360f)
        }
    }
    val leading = categories.firstOrNull()
    // The list exposes every category name and percentage to accessibility services.
    BoxWithConstraints(modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, maxHeight)
        Box(Modifier.size(diameter).padding(4.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = size.minDimension * .115f
                val bounds = Size(size.width - stroke, size.height - stroke)
                val origin = Offset(stroke / 2f, stroke / 2f)
                drawArc(palette.border, 0f, 360f, false, origin, bounds, style = Stroke(stroke))
                var start = -90f
                for ((color, sweep) in segments) {
                    val gap = if (segments.size == 1) 0f else minOf(2.5f, sweep * .3f)
                    drawArc(color, start + gap / 2f, sweep - gap, false, origin, bounds, style = Stroke(stroke))
                    start += sweep
                }
            }
            if (leading != null) {
                val configuration = LocalConfiguration.current
                val percent = remember(leading.percent, configuration) {
                    NumberFormat.getPercentInstance().format(leading.percent / 100.0)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (diameter >= 70.dp && LocalDensity.current.fontScale < 1.6f) {
                        Text(String(Character.toChars(leading.category.image)), fontSize = 14.sp, lineHeight = 16.sp)
                    }
                    Text(
                        percent, color = palette.content, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                }
            }
        }
    }
}
