package com.breckneck.debtbook.finance.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBalancePalette
import com.breckneck.debtbook.core.ui.components.BubbleButton

@Composable
internal fun FinancePeriodControls(
    label: String,
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
                    Text(label, textAlign = TextAlign.Center)
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
