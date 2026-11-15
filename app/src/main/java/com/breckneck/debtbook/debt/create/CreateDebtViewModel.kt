package com.breckneck.debtbook.debt.create

import java.math.BigDecimal
import com.breckneck.deptbook.domain.util.compareTo

import android.util.Log
import com.breckneck.deptbook.domain.repository.DataTransaction
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.breckneck.deptbook.domain.usecase.Human.*
import com.breckneck.deptbook.domain.usecase.Debt.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.breckneck.deptbook.domain.usecase.Debt.GetCurrentDateUseCase
import com.breckneck.deptbook.domain.usecase.Debt.SetDateUseCase
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
    private val setDateUseCase: SetDateUseCase
) : ViewModel() {

    val isSaving = MutableLiveData(false)
    val saveError = MutableLiveData(false)
    val savedHumanId = MutableLiveData<Int?>()

    fun consumeSaveResult() { savedHumanId.value = null }

    fun saveDebt(humanId: Int, debtId: Int, name: String, sum: BigDecimal,
                 date: String, info: String) {
        if (isSaving.value == true || savedHumanId.value != null) return
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
                        editDebt.execute(debtId, sum, id, info.ifBlank { null }, date)
                        addSum.execute(id, sum - previous.sum)
                    } else {
                        setDebt.execute(sum, id, info.ifBlank { null }, date)
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
    private val _date = MutableLiveData<String>()
    val date: LiveData<String>
        get() = _date

    init {
        Log.e(TAG, "NewDebtFragmentViewModel created")
        getDefaultCurrency()
        getCurrentDate()
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

    fun setStoredDate(date: String) { _date.value = date }

    fun getCurrentDate() {
        _date.value = getCurrentDateUseCase.execute()
    }

    fun setCurrentDate(year: Int, month: Int, day: Int) {
        _date.value = setDateUseCase.execute(year = year, month = month, day = day)
    }
}