package com.breckneck.debtbook.debt.create

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.spacing
import com.breckneck.debtbook.debt.reminders.DebtDueDraft
import com.breckneck.debtbook.debt.reminders.DebtDueSheet
import com.breckneck.debtbook.debt.reminders.reminderTimeLabel
import com.breckneck.deptbook.domain.util.LedgerDate

@Composable
internal fun CreateDebtDueField(vm: CreateDebtViewModel) {
    val day by vm.dueDay.observeAsState()
    val minute by vm.reminderMinute.observeAsState()
    val saving by vm.isSaving.observeAsState(false)
    var opened by rememberSaveable { mutableStateOf(false) }
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    Card(
        onClick = { opened = true },
        enabled = !saving,
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space8)
            .padding(top = spacing.space8).testTag("createDebtDue")
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.due_settings)) },
            supportingContent = {
                val date = day
                val time = minute
                Text(if (date == null) stringResource(R.string.due_optional)
                    else if (time == null) LedgerDate.display(date)
                    else "${LedgerDate.display(date)} · ${reminderTimeLabel(context, time)}")
            },
            leadingContent = { Icon(painterResource(R.drawable.baseline_access_time_24), contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
    if (opened) {
        DebtDueSheet(DebtDueDraft(day, minute), confirmText = stringResource(R.string.due_apply),
            onDismiss = { opened = false }) { draft ->
            vm.setDueDraft(draft.day, draft.reminderMinute)
        }
    }
}
