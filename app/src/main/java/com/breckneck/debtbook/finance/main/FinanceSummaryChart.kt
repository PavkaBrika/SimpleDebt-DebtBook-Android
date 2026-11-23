package com.breckneck.debtbook.finance.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBalancePalette
import com.breckneck.debtbook.core.ui.components.bubbleBalancePalette
import com.breckneck.debtbook.core.ui.theme.LocalDebtBookDarkTheme
import com.breckneck.debtbook.finance.badgeColor
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import java.math.MathContext

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
    expanded: Boolean,
    expansion: Float,
    onToggle: (() -> Unit)?,
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
    val toggle = if (onToggle == null) Modifier else Modifier
        .clip(CircleShape)
        .clickable(
            role = Role.Button,
            onClickLabel = if (expanded) stringResource(R.string.finance_collapse_shares) else null,
            onClick = onToggle,
        )
    Box(modifier.then(toggle), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().padding(4.dp)) {
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
        if (onToggle != null) {
            val angle = if (LocalLayoutDirection.current == LayoutDirection.Ltr) -90f else 90f
            val labelStyle = MaterialTheme.typography.labelMedium
            Row(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.finance_category_shares),
                    Modifier.weight(1f, fill = false),
                    color = palette.content,
                    style = labelStyle,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = labelStyle.fontSize),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    Modifier.size(14.dp).graphicsLayer { rotationZ = angle * expansion },
                    tint = palette.content,
                )
            }
        }
    }
}
