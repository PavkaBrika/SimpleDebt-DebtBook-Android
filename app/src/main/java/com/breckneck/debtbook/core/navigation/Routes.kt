package com.breckneck.debtbook.core.navigation

import kotlinx.serialization.Serializable

internal enum class MainTab(val key: String, val persisted: Boolean = true) {
    DEBTS("debts"),
    FINANCES("finances"),
    GOALS("goals"),
    SETTINGS("settings", persisted = false),
}

@Serializable internal data object Debts
@Serializable internal data object Finances
@Serializable internal data object Goals
@Serializable internal data object Settings

@Serializable
internal data class DebtForm(
    val idHuman: Int = -1,
    val idDebt: Int = -1,
    val currency: String? = null,
    val date: String? = null,
    val name: String? = null,
    val info: String? = null,
    val sum: String? = null,
    val dueDayOnEdit: String? = null,
    val minuteOnEdit: Int = -1,
    val remindOnEdit: Boolean = false,
    val hasRepayments: Boolean = false,
    val canRemind: Boolean = true,
)

@Serializable
internal data class DebtPerson(
    val idHuman: Int,
    val currency: String,
    val name: String,
    val showSettledDebts: Boolean = false,
    val openDebtId: Int = -1,
)

@Serializable internal data class EditDebtPerson(val idHuman: Int)

@Serializable
internal data class FinanceForm(
    val categoryState: String,
    val currency: String,
    val day: String,
    val categoryId: Int = -1,
    val financeId: Int = -1,
)

@Serializable internal data class FinanceCategoryForm(val categoryState: String)

@Serializable
internal data class FinanceHistory(
    val categoryId: Int,
    val categoryName: String,
    val categoryState: String,
    val currency: String,
    val day: String,
)

@Serializable internal data class GoalForm(val goalId: Int = -1)
@Serializable internal data class GoalDetails(val goalId: Int)

@Serializable
internal data class Currencies(val selected: String, val available: List<String>? = null)

@Serializable internal data object DefaultCurrency
@Serializable internal data object Appearance
@Serializable internal data object AppSecurity
@Serializable internal data class PinManagement(val action: String)
@Serializable internal data object Backup
