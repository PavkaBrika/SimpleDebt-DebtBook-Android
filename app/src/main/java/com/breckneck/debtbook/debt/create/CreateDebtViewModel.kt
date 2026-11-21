package com.breckneck.debtbook.debt.create

import java.math.BigDecimal

import android.util.Log
import com.breckneck.deptbook.domain.repository.DataTransaction
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.breckneck.deptbook.domain.usecase.Human.*
import com.breckneck.deptbook.domain.usecase.Debt.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.breckneck.deptbook.domain.usecase.Debt.GetCurrentDateUseCase
import com.breckneck.deptbook.domain.model.DebtDueChange
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.usecase.Settings.GetDefaultCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CreateDebtViewModel @Inject constructor(
    private val getDefaultCurrency: GetDefaultCurrency,
    private val getCurrentDateUseCase: GetCurrentDateUseCase,
    private val setHuman: SetHumanUseCase,
    private val getLastHumanId: GetLastHumanIdUseCase,
    private val setDebt: SetDebtUseCase,
    private val editDebt: EditDebtUseCase,
    private val getDebts: GetAllDebtsByIdUseCase,
    private val addSum: AddSumUseCase,
    private val transaction: DataTransaction,
    private val savedState: SavedStateHandle
) : ViewModel() {

    val isSaving = MutableLiveData(false)
    val saveError = MutableLiveData(false)
    val savedHumanId = MutableLiveData<Int?>()
    private val initialDueDay = savedState.get<String>("dueDayOnEdit")?.let(LedgerDate::canonical)
    private val initialRemind = savedState.get<Boolean>("remindOnEdit") == true
    val dueDay: LiveData<String?> = savedState.getLiveData("dueDay", initialDueDay)
    val remind: LiveData<Boolean> = savedState.getLiveData("remind", initialRemind)
    val hasRepayments = savedState.get<Boolean>("hasRepayments") == true
    val canRemind = savedState.get<Boolean>("canRemind") ?: true

    val hasDueChange: Boolean
        get() = dueDay.value != initialDueDay || remind.value != initialRemind

    fun setDueDraft(day: String, remind: Boolean) {
        savedState["dueDay"] = day
        savedState["remind"] = remind
    }

    fun consumeSaveResult() { savedHumanId.value = null }

    fun saveDebt(humanId: Int, debtId: Int, name: String, sum: BigDecimal,
                 date: String, info: String) {
        if (isSaving.value == true || savedHumanId.value != null) return
        val selectedDay = dueDay.value
        val reminderEnabled = remind.value == true
        val selectedMinute = DebtReminderPolicy.DEFAULT_MINUTE.takeIf { reminderEnabled }
        val due = selectedDay?.takeIf { hasDueChange }?.let { DebtDueChange(it, reminderEnabled) }
        isSaving.value = true
        saveError.value = false
        viewModelScope.launch {
            try {
                val savedId = withContext(Dispatchers.IO) { transaction.run {
                    val id = if (humanId > 0) humanId else {
                        setHuman.execute(name, BigDecimal.ZERO, currency.value!!)
                        getLastHumanId.exectute()
                    }
                    if (debtId > 0) {
                        val previous = requireNotNull(getDebts.execute(id).firstOrNull { it.id == debtId })
                        editDebt.execute(debtId, sum, id, info.ifBlank { null }, date, due)
                        addSum.execute(id, sum - previous.sum)
                    } else {
                        setDebt.execute(sum, id, info.ifBlank { null }, date, selectedDay, selectedMinute)
                        addSum.execute(id, sum)
                    }
                    id
                } }
                savedHumanId.value = savedId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError.value = true
                Log.e("CreateDebt", "Saving debt failed")
            } finally {
                isSaving.value = false
            }
        }
    }

    private val TAG = "NewDebtFragmentVM"

    private val _currency = MutableLiveData<String>()
    val currency: LiveData<String>
        get() = _currency
    private val _selectedCurrencyPosition = MutableLiveData<Int>()
    val selectedCurrencyPosition: LiveData<Int>
        get() = _selectedCurrencyPosition
    private val _isCurrencyDialogOpened = MutableLiveData<Boolean>()
    val isCurrencyDialogOpened: LiveData<Boolean>
        get() = _isCurrencyDialogOpened
    val date: LiveData<String> = savedState.getLiveData(
        "selectedDebtDate", savedState.get<String>("date") ?: getCurrentDateUseCase.execute()
    )

    init {
        Log.e(TAG, "NewDebtFragmentViewModel created")
        getDefaultCurrency()
    }

    override fun onCleared() {
        super.onCleared()
        Log.e(TAG, "NewDebtFragmentViewModel cleared")
    }

    fun setCurrency(currency: String) {
        _currency.value = currency
    }

    fun onCurrencyDialogOpen(selectedCurrencyPosition: Int) {
        _isCurrencyDialogOpened.value = true
        _selectedCurrencyPosition.value = selectedCurrencyPosition
    }

    fun onCurrencyDialogClose() {
        _isCurrencyDialogOpened.value = false
    }

    private fun getDefaultCurrency() {
        _currency.value = getDefaultCurrency.execute()
    }

    fun setStoredDate(date: String) {
        savedState["selectedDebtDate"] = date
    }
}
