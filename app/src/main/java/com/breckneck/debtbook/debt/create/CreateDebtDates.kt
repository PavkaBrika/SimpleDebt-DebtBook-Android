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
internal fun CreateDebtDates(vm: CreateDebtViewModel, neighboringCard: MaterialCardView) {
    val date by vm.date.observeAsState()
    val draftDueDay by vm.dueDay.observeAsState()
    val reminder by vm.remind.observeAsState(false)
    val saving by vm.isSaving.observeAsState(false)
    var openField by rememberSaveable { mutableStateOf<DateField?>(null) }
    val spacing = MaterialTheme.spacing
    val dateLabel = stringResource(R.string.debt_entry_date)
    val dueLabel = stringResource(R.string.due_settings)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = spacing.space8, vertical = spacing.space8),
        verticalArrangement = Arrangement.spacedBy(spacing.space8)
    ) {
        DebtDateCard(
            dateLabel, date?.let(LedgerDate::display).orEmpty(), neighboringCard,
            enabled = !saving && !vm.hasRepayments, tag = "createDebtDate", editable = !vm.hasRepayments
        ) { openField = DateField.DEBT }
        DebtDateCard(
            dueLabel, draftDueDay?.let(LedgerDate::display) ?: stringResource(R.string.due_optional),
            neighboringCard, !saving, "createDebtDue"
        ) { openField = DateField.DUE }
    }
    when (openField) {
        DateField.DEBT -> {
            val today = LedgerDate.today()
            DebtDateSheet(
                title = dateLabel,
                initialDay = date,
                maxDay = draftDueDay?.let { minOf(today, it) } ?: today,
                confirmText = stringResource(R.string.due_apply),
                onDismiss = { openField = null }
            ) { day, _ -> vm.setStoredDate(day) }
        }
        DateField.DUE -> DebtDateSheet(
            title = dueLabel,
            initialDay = draftDueDay,
            minDay = date?.let(LedgerDate::canonical),
            initialReminder = reminder.takeIf { vm.canRemind },
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
    editable: Boolean = true,
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
        colors = CardDefaults.cardColors(
            containerColor = background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = background,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (editable) 0.38f else 1f)
        ),
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
            if (editable) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
