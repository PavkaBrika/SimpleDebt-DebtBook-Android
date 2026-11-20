package com.breckneck.debtbook.debt.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.spacing
import com.breckneck.debtbook.debt.reminders.DebtDateSheet
import com.breckneck.deptbook.domain.util.LedgerDate
import com.google.android.material.card.MaterialCardView

private enum class DateField { DEBT, DUE }

@Composable
internal fun CreateDebtDates(vm: CreateDebtViewModel, neighboringCard: MaterialCardView, newDebt: Boolean) {
    val date by vm.date.observeAsState()
    val draftDueDay by vm.dueDay.observeAsState()
    val reminder by vm.remind.observeAsState(false)
    val saving by vm.isSaving.observeAsState(false)
    var openField by rememberSaveable { mutableStateOf<DateField?>(null) }
    val spacing = MaterialTheme.spacing
    val dateLabel = stringResource(R.string.debt_entry_date)
    val dueLabel = stringResource(R.string.due_settings)

    Column(Modifier.fillMaxWidth().padding(start = spacing.space8, end = spacing.space8, top = spacing.space8),
        verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
        DebtDateCard(dateLabel, date?.let(LedgerDate::display).orEmpty(), neighboringCard, !saving, "createDebtDate") {
            openField = DateField.DEBT
        }
        if (newDebt) {
            DebtDateCard(dueLabel, draftDueDay?.let(LedgerDate::display) ?: stringResource(R.string.due_optional),
                neighboringCard, !saving, "createDebtDue") {
                openField = DateField.DUE
            }
        }
    }
    when (openField) {
        DateField.DEBT -> {
            val today = LedgerDate.today()
            val due = if (newDebt) draftDueDay else vm.existingDueDay
            DebtDateSheet(
                title = dateLabel,
                initialDay = date,
                maxDay = due?.let { minOf(today, it) } ?: today,
                confirmText = stringResource(R.string.due_apply),
                onDismiss = { openField = null }
            ) { day, _ -> vm.setStoredDate(day) }
        }
        DateField.DUE -> DebtDateSheet(
            title = dueLabel,
            initialDay = draftDueDay,
            minDay = date?.let(LedgerDate::canonical),
            initialReminder = reminder,
            confirmText = stringResource(R.string.due_apply),
            onDismiss = { openField = null },
            onSave = vm::setDueDraft
        )
        null -> Unit
    }
}

@Composable
private fun DebtDateCard(
    title: String,
    value: String,
    neighboringCard: MaterialCardView,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val density = LocalDensity.current
    // Match the existing form cards while the rest of the form still uses Material Views.
    val background = Color(neighboringCard.cardBackgroundColor.defaultColor)
    val border = neighboringCard.strokeWidth.takeIf { it > 0 }?.let {
        BorderStroke(with(density) { it.toDp() }, Color(neighboringCard.strokeColorStateList?.defaultColor ?: neighboringCard.strokeColor))
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(with(density) { neighboringCard.radius.toDp() }),
        colors = CardDefaults.cardColors(containerColor = background, disabledContainerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = with(density) { neighboringCard.cardElevation.toDp() }),
        border = border
    ) {
        Row(
            Modifier.fillMaxWidth().padding(spacing.space16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12)
        ) {
            Icon(Icons.Outlined.DateRange, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
