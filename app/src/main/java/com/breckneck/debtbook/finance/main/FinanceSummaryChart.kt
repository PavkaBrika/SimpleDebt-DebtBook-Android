package com.breckneck.debtbook.finance.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBalancePalette
import com.breckneck.debtbook.core.ui.components.bubbleBalancePalette
import com.breckneck.debtbook.core.ui.theme.LocalDebtBookDarkTheme
import com.breckneck.debtbook.finance.badgeColor
import com.breckneck.debtbook.finance.localizedName
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import java.math.MathContext
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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

private const val ExpandedStrokeFraction = .15f
private val ChartPadding = 4.dp

private data class CategorySector(val row: CategoryTotal, val color: Color, val start: Float, val sweep: Float)

private fun FinanceTotals.categorySectors(): List<CategorySector> {
    if (total.signum() <= 0 || categories.any { it.amount.signum() < 0 }) return emptyList()
    var start = 0f
    return categories.filter { it.amount.signum() > 0 }.map { row ->
        val sweep = row.amount.divide(total, MathContext.DECIMAL64).toFloat() * 360f
        CategorySector(row, row.category.badgeColor, start, sweep).also { start += sweep }
    }
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
    val sectors = remember(totals) { totals.categorySectors() }
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = sectors.firstOrNull { it.row.category.id == selectedId }
    val showDetails = expanded && expansion >= .86f && sectors.isNotEmpty()
    val selectNext = if (showDetails && sectors.size > 1) {
        {
            val next = (sectors.indexOfFirst { it.row.category.id == selectedId } + 1) % sectors.size
            selectedId = sectors[next].row.category.id
        }
    } else null
    val toggleAction by rememberUpdatedState(onToggle)
    LaunchedEffect(expanded) {
        if (!expanded) selectedId = null
    }

    val configuration = LocalConfiguration.current
    val formatter = remember(configuration) { NumberFormat.getPercentInstance() }
    val textMeasurer = rememberTextMeasurer()
    val sectorStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val labels = remember(sectors, textMeasurer, sectorStyle, formatter) {
        sectors.map { sector ->
            if (sector.sweep < 24f) null else {
                val symbol = String(Character.toChars(sector.row.category.image))
                val percent = formatter.format(sector.row.percent / 100.0)
                textMeasurer.measure("$symbol $percent", sectorStyle, softWrap = false, maxLines = 1)
            }
        }
    }

    val toggle = if (onToggle == null) Modifier else Modifier
        .clip(CircleShape)
        .clickable(
            role = Role.Button,
            onClickLabel = if (expanded) stringResource(R.string.finance_collapse_shares) else null,
            onClick = onToggle,
        )
    val sectorGesture = if (!showDetails) Modifier else Modifier.pointerInput(sectors) {
        detectTapGestures { position ->
            val offset = position - Offset(size.width / 2f, size.height / 2f)
            val radius = offset.getDistance()
            val outer = minOf(size.width, size.height) / 2f - ChartPadding.toPx()
            if (radius < outer * (1f - 2f * ExpandedStrokeFraction)) {
                toggleAction?.invoke()
            } else if (radius <= outer + ChartPadding.toPx()) {
                val angle = (atan2(offset.y, offset.x) * 180f / PI.toFloat() + 450f) % 360f
                selectedId = (sectors.firstOrNull { angle < it.start + it.sweep } ?: sectors.last()).row.category.id
            }
        }
    }
    val browse = if (selectNext != null && selected == null) {
        val label = stringResource(R.string.finance_next_sector)
        Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(label) {
                selectNext()
                true
            })
        }
    } else Modifier
    Box(modifier.then(toggle).then(browse), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().then(sectorGesture).padding(ChartPadding)) {
            val stroke = size.minDimension * (.115f + (ExpandedStrokeFraction - .115f) * expansion)
            val bounds = Size(size.width - stroke, size.height - stroke)
            val origin = Offset(stroke / 2f, stroke / 2f)
            drawArc(palette.border, 0f, 360f, false, origin, bounds, style = Stroke(stroke))
            val labelAlpha = ((expansion - .65f) / .35f).coerceIn(0f, 1f)
            sectors.forEachIndexed { index, sector ->
                val gap = if (sectors.size == 1) 0f else minOf(2.5f, sector.sweep * .3f)
                val emphasizedStroke = if (showDetails && sector == selected && sectors.size > 1) stroke + 4.dp.toPx() else stroke
                drawArc(sector.color, sector.start - 90f + gap / 2f, sector.sweep - gap, false, origin, bounds, style = Stroke(emphasizedStroke))
                val label = labels[index]
                if (label != null && labelAlpha > 0f) {
                    drawSectorLabel(sector, label, bounds.minDimension / 2f, stroke, labelAlpha)
                }
            }
        }
        if (onToggle != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (showDetails && selected != null) {
                    SectorDetails(
                        selected = selected,
                        percent = formatter.format(selected.row.percent / 100.0),
                        palette = palette,
                        onNext = selectNext,
                    )
                }
                SharesLabel(palette, expansion)
            }
        }
    }
}

@Composable
private fun SectorDetails(
    selected: CategorySector,
    percent: String,
    palette: BubbleBalancePalette,
    onNext: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth(.64f).padding(bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                selected.row.category.localizedName(),
                color = palette.content,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(percent, color = palette.muted, style = MaterialTheme.typography.labelSmall)
        }
        if (onNext != null) {
            IconButton(onNext, Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(R.string.finance_next_sector),
                    Modifier.size(18.dp),
                    tint = palette.content,
                )
            }
        }
    }
}

@Composable
private fun SharesLabel(palette: BubbleBalancePalette, expansion: Float) {
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

private fun DrawScope.drawSectorLabel(sector: CategorySector, label: TextLayoutResult, radius: Float, stroke: Float, alpha: Float) {
    val radians = PI.toFloat() / 180f
    if (label.size.height > stroke || sector.sweep * radians * radius < label.size.width + 8.dp.toPx()) return

    val middle = sector.start + sector.sweep / 2f
    val bearing = (middle - 90f) * radians
    val pivot = center + Offset(cos(bearing) * radius, sin(bearing) * radius)
    val rotation = when {
        middle > 270f -> middle - 360f
        middle > 90f -> middle - 180f
        else -> middle
    }
    val ink = if (sector.color.luminance() > .18f) Color.Black else Color.White
    rotate(rotation, pivot) {
        drawText(label, color = ink, topLeft = pivot - Offset(label.size.width / 2f, label.size.height / 2f), alpha = alpha)
    }
}
