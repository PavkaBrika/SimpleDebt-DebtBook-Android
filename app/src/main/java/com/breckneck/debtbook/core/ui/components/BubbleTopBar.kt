package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun BubbleTopBar(
    title: String,
    onBack: () -> Unit,
    hostInsets: PaddingValues,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    action: @Composable (Dp) -> Unit = { Spacer(Modifier.size(it)) },
) {
    val colors = bubbleColors()
    val direction = LocalLayoutDirection.current
    val lineHeight = 22.sp
    val bubbleHeight = with(LocalDensity.current) { maxOf(48.dp, lineHeight.toDp() * 2 + 2.dp) }
    Row(
        modifier = modifier.fillMaxWidth().background(
            Brush.verticalGradient(
                0f to colors.background.copy(alpha = .92f),
                .45f to colors.background.copy(alpha = .64f),
                1f to colors.background.copy(alpha = 0f),
            ),
        ).padding(
            start = hostInsets.calculateStartPadding(direction) + 16.dp,
            end = hostInsets.calculateEndPadding(direction) + 16.dp,
            top = hostInsets.calculateTopPadding() + 6.dp,
            bottom = 6.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BubbleIconButton(onClick = onBack, enabled = backEnabled, diameter = bubbleHeight) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.height(bubbleHeight)
                    .background(colors.paper.copy(alpha = .88f), CircleShape)
                    .border(1.dp, colors.border, CircleShape)
                    .semantics(mergeDescendants = true) { heading() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    color = colors.ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 18.sp,
                    lineHeight = lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        action(bubbleHeight)
    }
}
