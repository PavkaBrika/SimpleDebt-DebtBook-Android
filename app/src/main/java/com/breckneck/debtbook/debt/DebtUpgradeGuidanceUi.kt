package com.breckneck.debtbook.debt

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import kotlinx.coroutines.delay

private const val NoticeDismissDelayMillis = 1_500L

@Composable
internal fun DebtUpgradeNotice(topics: DebtUpgradeTopics, onDismiss: () -> Unit) {
    val colors = bubbleColors()
    var canDismiss by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(NoticeDismissDelayMillis)
        canDismiss = true
    }
    Dialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.paper, contentColor = colors.ink) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.action)
                    IconButton(onDismiss, Modifier.size(48.dp), enabled = canDismiss) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.debt_upgrade_close_notice))
                    }
                }
                Text(stringResource(R.string.debt_upgrade_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.debt_upgrade_intro), color = colors.muted)
                if (topics.otherCurrencies) {
                    DebtUpgradeTopic(R.string.debt_upgrade_currency_topic, R.string.debt_upgrade_currency_notice)
                }
                if (topics.earlyEntries) {
                    DebtUpgradeTopic(R.string.debt_upgrade_history_topic, R.string.debt_upgrade_history_notice)
                }
                BubbleButton(onDismiss, Modifier.fillMaxWidth(), enabled = canDismiss) {
                    Text(stringResource(R.string.debt_upgrade_got_it))
                }
            }
        }
    }
}

@Composable
private fun DebtUpgradeTopic(@StringRes title: Int, @StringRes description: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = bubbleColors().muted)
    }
}

@Composable
internal fun rememberDebtUpgradeHintTransition(@StringRes action: Int?): Transition<Int?> {
    val state = remember { MutableTransitionState<Int?>(null) }
    state.targetState = action
    return rememberTransition(state, label = "Debt upgrade hint")
}

@Composable
internal fun DebtUpgradeHint(
    transition: Transition<Int?>,
    @StringRes title: Int,
    @StringRes description: Int,
    @StringRes dismissLabel: Int,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = bubbleColors()
    transition.AnimatedContent(
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(tween(220)) togetherWith fadeOut(tween(180))).using(SizeTransform { _, _ -> tween(220) })
        },
        contentKey = { it != null },
    ) { action ->
        if (action != null) {
            val enabled = transition.targetState != null
            val titleStyle = MaterialTheme.typography.titleMedium
            val titleLineHeight = with(LocalDensity.current) { titleStyle.lineHeight.toDp() }
            Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.mint, contentColor = colors.onMint) {
                Row(Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)) {
                    Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.heightIn(min = titleLineHeight), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp))
                            }
                            Text(stringResource(title), style = titleStyle, fontWeight = FontWeight.Bold)
                        }
                        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
                        TextButton(
                            onClick = onAction,
                            enabled = enabled,
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.onMint),
                        ) {
                            Text(stringResource(action), Modifier.weight(1f, fill = false))
                            if (action == R.string.debt_upgrade_open_currencies || action == R.string.debt_upgrade_open_early_entries) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
                            }
                        }
                    }
                    IconButton(onDismiss, Modifier.size(48.dp), enabled = enabled) {
                        Icon(Icons.Outlined.Close, stringResource(dismissLabel), tint = colors.onMint)
                    }
                }
            }
        } else {
            Spacer(Modifier.fillMaxWidth())
        }
    }
}
