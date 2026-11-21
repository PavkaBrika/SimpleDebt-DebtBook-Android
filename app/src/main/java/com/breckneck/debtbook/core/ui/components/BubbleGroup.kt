package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun BubbleGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = bubbleColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.paper,
        contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column { content() }
    }
}

/** Keeps a single outlined group while LazyColumn composes each row independently. */
@Composable
internal fun BubbleGroupedRow(index: Int, count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = bubbleColors()
    val top = if (index == 0) 23.dp else 0.dp
    val bottom = if (index == count - 1) 23.dp else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).background(colors.border)
            .padding(start = 1.dp, end = 1.dp, top = if (index == 0) 1.dp else 0.dp, bottom = 1.dp),
        shape = shape, color = colors.paper, contentColor = colors.ink, content = content,
    )
}

@Composable
internal fun BubbleValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp)) {
        val valueWidth = maxWidth * .54f
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f))
            Text(value, Modifier.widthIn(max = valueWidth), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        }
    }
}
