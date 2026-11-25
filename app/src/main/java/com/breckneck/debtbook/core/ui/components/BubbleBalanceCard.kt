package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.core.ui.theme.spacing
import kotlin.math.roundToInt

internal data class BubbleBalancePalette(
    val container: Color,
    val content: Color,
    val muted: Color,
    val border: Color,
)

@Composable
internal fun bubbleBalancePalette(): BubbleBalancePalette {
    val colors = bubbleColors()
    return BubbleBalancePalette(colors.hero, colors.onHero, colors.heroMuted, colors.heroBorder)
}

/** A single-currency summary. Formatting, aggregation and currency selection belong to its caller. */
@Composable
internal fun BubbleBalanceCard(
    title: String,
    currency: String,
    balance: String,
    onCurrencyClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showCoins: Boolean = false,
    balanceValue: java.math.BigDecimal? = null,
    palette: BubbleBalancePalette = bubbleBalancePalette(),
    artwork: (@Composable (Modifier) -> Unit)? = null,
    artworkExpansion: Float = 0f,
    footer: (@Composable (compact: Boolean) -> Unit)? = null,
) {
    val spacing = MaterialTheme.spacing
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = palette.container, contentColor = palette.content) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(spacing.space20)) {
            val largeText = LocalDensity.current.fontScale >= 1.3f || maxWidth < 260.dp
            val drawCoins = showCoins && !largeText && artwork == null
            val artworkWidth = (maxWidth * .35f).coerceAtMost(if (largeText) 82.dp else 108.dp)
            val titleInset = when {
                artwork != null -> artworkWidth + spacing.space8
                drawCoins -> 82.dp
                else -> 0.dp
            }
            val balanceInset = if (drawCoins) 70.dp else titleInset
            val heading: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                    Row(
                        modifier = Modifier.padding(end = titleInset),
                        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, Modifier.weight(1f, fill = false), color = palette.muted, style = MaterialTheme.typography.labelLarge)
                        Box(
                            Modifier.clip(CircleShape)
                                .clickable(enabled = onCurrencyClick != null, role = Role.Button) { onCurrencyClick?.invoke() }
                                .height(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                Modifier.border(1.dp, palette.border, CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(currency, style = MaterialTheme.typography.labelMedium)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp))
                            }
                        }
                    }
                    val balanceStyle = MaterialTheme.typography.displaySmall.copy(
                        fontSize = if (largeText) 36.sp else 42.sp, lineHeight = if (largeText) 40.sp else 47.sp,
                        fontFeatureSettings = "tnum", letterSpacing = (-1.2).sp, fontWeight = FontWeight.SemiBold,
                    )
                    val balanceModifier = Modifier.padding(end = balanceInset)
                    if (balanceValue == null) Text(balance, balanceModifier, style = balanceStyle)
                    else AnimatedAmount(balanceValue, balance, balanceModifier, balanceStyle)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                if (artwork != null) {
                    BalanceCardArtwork(artworkWidth, artworkExpansion, heading, artwork)
                } else {
                    Box(Modifier.fillMaxWidth()) {
                        if (drawCoins) {
                            CoinStack(palette.container, Modifier.align(Alignment.TopEnd).size(78.dp, 69.dp))
                        }
                        heading()
                    }
                }
                if (footer != null) {
                    HorizontalDivider(color = palette.border)
                    footer(largeText)
                }
            }
        }
    }
}

@Composable
private fun BalanceCardArtwork(
    collapsedWidth: Dp,
    expansion: Float,
    heading: @Composable () -> Unit,
    artwork: @Composable (Modifier) -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Box { heading() }
            Box { artwork(Modifier.fillMaxSize()) }
        },
    ) { measurables, constraints ->
        val headingPlaceable = measurables[0].measure(constraints.copy(minHeight = 0))
        val width = headingPlaceable.width
        val headingHeight = headingPlaceable.height
        val collapsed = minOf(collapsedWidth.roundToPx(), headingHeight)
        val expanded = minOf(width, 248.dp.roundToPx())
        val gap = 12.dp.roundToPx()
        fun interpolate(from: Int, to: Int) = (from + (to - from) * expansion).roundToInt()

        val diameter = interpolate(collapsed, expanded)
        val artworkPlaceable = measurables[1].measure(Constraints.fixed(diameter, diameter))
        val x = interpolate(width - collapsed, (width - expanded) / 2)
        val y = interpolate((headingHeight - collapsed) / 2, headingHeight + gap)
        val height = headingHeight + interpolate(0, gap + expanded)
        layout(width, height) {
            headingPlaceable.placeRelative(0, 0)
            artworkPlaceable.placeRelative(x, y)
        }
    }
}

@Composable
private fun CoinStack(background: Color, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 94f, size.height / 80f, Offset.Zero) {
            drawBubbleCoinStack(background, Color(0xFFB6D98D))
        }
    }
}

internal fun DrawScope.drawBubbleCoinStack(background: Color, ink: Color) {
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
