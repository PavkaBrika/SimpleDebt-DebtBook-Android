package com.breckneck.debtbook.finance.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBalancePalette
import com.breckneck.debtbook.core.ui.components.BubbleButton

@Composable
internal fun FinancePeriodControls(
    period: FinancePeriod,
    palette: BubbleBalancePalette,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectPeriod: () -> Unit,
    onCurrentPeriod: (() -> Unit)?,
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val arrowColors = IconButtonDefaults.iconButtonColors(
                containerColor = palette.border,
                contentColor = palette.content,
            )
            IconButton(onPrevious, colors = arrowColors) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.finance_previous))
            }
            OutlinedButton(
                onClick = onSelectPeriod,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                border = BorderStroke(1.dp, palette.muted.copy(alpha = .7f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.border.copy(alpha = .22f),
                    contentColor = palette.content,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.DateRange, null, Modifier.size(16.dp))
                    PeriodLabel(period)
                }
            }
            IconButton(onNext, colors = arrowColors) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.finance_next))
            }
        }
        AnimatedVisibility(
            visible = onCurrentPeriod != null,
            enter = expandVertically(tween(240), expandFrom = Alignment.Top) + fadeIn(tween(210)),
            exit = shrinkVertically(tween(240), shrinkTowards = Alignment.Top) + fadeOut(tween(180)),
        ) {
            BubbleButton(
                onClick = { onCurrentPeriod?.invoke() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.border, contentColor = palette.content),
            ) {
                Text(stringResource(R.string.finance_current_period), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PeriodLabel(period: FinancePeriod) {
    val label = period.label()
    val slideDistance = with(LocalDensity.current) { 12.dp.roundToPx() }
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) slideDistance else -slideDistance
    AnimatedContent(
        targetState = period,
        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(label) },
        contentAlignment = Alignment.Center,
        contentKey = { it.label() },
        transitionSpec = {
            val from = initialState.days()
            val to = targetState.days()
            val earlier = to.start < from.start || (to.start == from.start && to.endInclusive < from.endInclusive)
            val offset = if (earlier) -forward else forward
            (slideInHorizontally(tween(180)) { offset } + fadeIn(tween(180))) togetherWith
                (slideOutHorizontally(tween(180)) { -offset } + fadeOut(tween(180)))
        },
        label = "Period",
    ) { shown ->
        Text(shown.label(), textAlign = TextAlign.Center)
    }
}
