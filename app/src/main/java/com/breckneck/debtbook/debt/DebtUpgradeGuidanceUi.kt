package com.breckneck.debtbook.debt

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun DebtUpgradeNotice(topics: DebtUpgradeTopics, onDismiss: () -> Unit) {
    val colors = bubbleColors()
    Dialog(onDismissRequest = onDismiss) {
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
                    IconButton(onDismiss, Modifier.size(48.dp)) {
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
                BubbleButton(onDismiss, Modifier.fillMaxWidth()) {
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
internal fun DebtUpgradeHint(
    @StringRes title: Int,
    @StringRes description: Int,
    @StringRes action: Int,
    @StringRes dismissLabel: Int,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = bubbleColors()
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.mint, contentColor = colors.onMint) {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)) {
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(stringResource(action), color = colors.onMint)
                }
            }
            IconButton(onDismiss, Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Close, stringResource(dismissLabel))
            }
        }
    }
}
