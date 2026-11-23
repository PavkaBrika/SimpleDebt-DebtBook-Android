package com.breckneck.debtbook.finance.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
import kotlin.math.abs
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
    val showPie = expansion >= .9f && sectors.isNotEmpty() && onToggle != null
    LaunchedEffect(expanded) {
        if (!expanded) selectedId = null
    }

    val title = stringResource(R.string.finance_category_shares)
    val labels = if (showPie) rememberSectorLabels(sectors) else emptyList()
    val toggle = if (onToggle != null && !showPie) Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onToggle) else Modifier
    val sectorGesture = if (!showPie) Modifier else Modifier.pointerInput(sectors) {
        detectTapGestures { position ->
            val offset = position - Offset(size.width / 2f, size.height / 2f)
            if (offset.getDistance() <= minOf(size.width, size.height) / 2f - ChartPadding.toPx()) {
                val angle = offset.chartAngle()
                val sector = sectors.firstOrNull { angle < it.start + it.sweep } ?: sectors.last()
                selectedId = sector.row.category.id
            }
        }
    }
    Box(modifier.then(toggle), contentAlignment = Alignment.Center) {
        val description = if (showPie) Modifier.semantics {
            contentDescription = title
            role = Role.Image
        } else Modifier
        Canvas(Modifier.matchParentSize().then(description).then(sectorGesture).padding(ChartPadding)) {
            val radius = size.minDimension / 2f
            drawCircle(palette.border, radius)
            val labelAlpha = ((expansion - .9f) * 10f).coerceIn(0f, 1f)
            sectors.forEachIndexed { index, sector ->
                val gap = if (sectors.size == 1) 0f else minOf(2f, sector.sweep * .25f)
                val start = sector.start - 90f + gap / 2f
                val sweep = sector.sweep - gap
                drawArc(sector.color, start, sweep, useCenter = true)
                if (showPie && selected == sector && sectors.size > 1) {
                    val inset = 3.dp.toPx()
                    val ink = if (sector.color.luminance() > .18f) Color.Black else Color.White
                    drawArc(
                        ink.copy(alpha = .65f), start, sweep, useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        style = Stroke(2.dp.toPx()),
                    )
                }
                if (showPie) labels[index]?.let { drawSectorLabel(sector, it, radius, labelAlpha) }
            }
            val hole = radius * .77f * (1f - expansion)
            if (hole > 0f) drawCircle(palette.container, hole)
        }
        if (showPie && onToggle != null) {
            val controls = IconButtonDefaults.iconButtonColors(containerColor = palette.border, contentColor = palette.content)
            IconButton(onToggle, Modifier.align(Alignment.TopEnd).size(36.dp), colors = controls) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.finance_collapse_shares), Modifier.size(20.dp))
            }
            if (sectors.size > 1) {
                IconButton(
                    onClick = {
                        val next = (sectors.indexOfFirst { it.row.category.id == selectedId } + 1) % sectors.size
                        selectedId = sectors[next].row.category.id
                    },
                    modifier = Modifier.align(Alignment.TopStart).size(36.dp),
                    colors = controls,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.finance_next_sector), Modifier.size(20.dp))
                }
            }
            if (selected != null) {
                val configuration = LocalConfiguration.current
                val share = remember(selected.row.percent, configuration) {
                    NumberFormat.getPercentInstance().format(selected.row.percent / 100.0)
                }
                Text(
                    "${selected.row.category.localizedName()} · $share",
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(palette.container, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = palette.content,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (onToggle != null) {
            SharesLabel(title, palette)
        }
    }
}

@Composable
private fun SharesLabel(title: String, palette: BubbleBalancePalette) {
    val labelStyle = MaterialTheme.typography.labelMedium
    Row(
        Modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f, fill = false),
            color = palette.content,
            style = labelStyle,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = labelStyle.fontSize),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(14.dp), tint = palette.content)
    }
}

private data class SectorLabel(val names: List<TextLayoutResult>, val share: TextLayoutResult)

@Composable
private fun rememberSectorLabels(sectors: List<CategorySector>): List<SectorLabel?> {
    val names = sectors.map { it.row.category.localizedName() }
    val configuration = LocalConfiguration.current
    val textMeasurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.labelMedium.copy(textAlign = TextAlign.Center)
    val compactNameStyle = nameStyle.copy(fontSize = 10.sp, lineHeight = 12.sp)
    val shareStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val nameWidth = with(LocalDensity.current) { 92.dp.roundToPx() }
    val compactNameWidth = with(LocalDensity.current) { 76.dp.roundToPx() }
    return remember(sectors, names, configuration, textMeasurer, nameStyle, shareStyle, nameWidth, compactNameWidth) {
        val formatter = NumberFormat.getPercentInstance()
        sectors.mapIndexed { index, sector ->
            if (sector.sweep < 10f) null else SectorLabel(
                names = listOf(
                    textMeasurer.measure(
                        names[index], nameStyle, overflow = TextOverflow.Ellipsis,
                        maxLines = 2, constraints = Constraints(maxWidth = nameWidth),
                    ),
                    textMeasurer.measure(
                        names[index], compactNameStyle, overflow = TextOverflow.Ellipsis,
                        maxLines = 2, constraints = Constraints(maxWidth = compactNameWidth),
                    ),
                ),
                share = textMeasurer.measure(formatter.format(sector.row.percent / 100.0), shareStyle, maxLines = 1),
            )
        }
    }
}

private val LabelDistances = listOf(.54f, .46f, .62f, .7f, .36f)

private fun DrawScope.drawSectorLabel(sector: CategorySector, label: SectorLabel, radius: Float, alpha: Float) {
    val share = label.share
    val gap = 2.dp.toPx()
    val direction = sector.midDirection()
    val ink = if (sector.color.luminance() > .18f) Color.Black else Color.White

    for (name in label.names) {
        val completeHeight = name.size.height + gap + share.size.height
        val nameWidth = (0 until name.lineCount).maxOfOrNull { name.getLineRight(it) - name.getLineLeft(it) } ?: 0f
        val completeWidth = maxOf(nameWidth, share.size.width.toFloat())
        val namePosition = labelPosition(sector, direction, completeWidth, completeHeight, radius) ?: continue
        val top = namePosition.y - completeHeight / 2f
        drawText(name, color = ink, topLeft = Offset(namePosition.x - name.size.width / 2f, top), alpha = alpha)
        drawText(share, color = ink, topLeft = Offset(namePosition.x - share.size.width / 2f, top + name.size.height + gap), alpha = alpha)
        return
    }
    val sharePosition = labelPosition(sector, direction, share.size.width.toFloat(), share.size.height.toFloat(), radius) ?: return
    drawText(share, color = ink, topLeft = sharePosition - Offset(share.size.width / 2f, share.size.height / 2f), alpha = alpha)
}

private fun DrawScope.labelPosition(sector: CategorySector, direction: Offset, width: Float, height: Float, radius: Float): Offset? {
    val margin = 4.dp.toPx()
    val halfWidth = width / 2f + margin
    val halfHeight = height / 2f + margin
    val distances = if (sector.sweep >= 359.9f) listOf(0f) else LabelDistances
    for (distance in distances) {
        val position = direction * (radius * distance)
        if (sector.sweep < 359.9f && abs(position.x) < halfWidth && abs(position.y) < halfHeight) continue
        val corners = listOf(
            position + Offset(-halfWidth, -halfHeight), position + Offset(halfWidth, -halfHeight),
            position + Offset(-halfWidth, halfHeight), position + Offset(halfWidth, halfHeight),
        )
        if (corners.all { point ->
                val angle = point.chartAngle()
                point.getDistance() <= radius && (sector.sweep >= 359.9f || angle > sector.start + 1f && angle < sector.start + sector.sweep - 1f)
            }) return center + position
    }
    return null
}

private fun Offset.chartAngle(): Float = (atan2(y, x) * 180f / PI.toFloat() + 450f) % 360f

private fun CategorySector.midDirection(): Offset {
    val radians = (start + sweep / 2f - 90f) * PI.toFloat() / 180f
    return Offset(cos(radians), sin(radians))
}
