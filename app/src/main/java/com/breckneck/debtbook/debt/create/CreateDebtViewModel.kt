package com.breckneck.debtbook.debt.create

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.DebtDueChange
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.usecase.Debt.*
import com.breckneck.deptbook.domain.usecase.Debt.GetCurrentDateUseCase
import com.breckneck.deptbook.domain.usecase.Human.*
import com.breckneck.deptbook.domain.usecase.Settings.GetDefaultCurrency
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.LedgerDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val initialMinute = savedState.get<Int>("minuteOnEdit") ?: DebtReminderPolicy.DEFAULT_MINUTE
    val minute: LiveData<Int> = savedState.getLiveData("draftMinute", initialMinute)
    val hasRepayments = savedState.get<Boolean>("hasRepayments") == true
    val canRemind = savedState.get<Boolean>("canRemind") ?: true

    val hasDueChange: Boolean
        get() = dueDay.value != initialDueDay || remind.value != initialRemind || (remind.value == true && minute.value != initialMinute)

    fun hasPastReminder(): Boolean {
        val day = dueDay.value ?: return false
        val time = minute.value ?: return false
        return hasDueChange && remind.value == true && DebtReminderPolicy.trigger(day, time) <= System.currentTimeMillis()
    }

    fun setDueDraft(day: String?, selectedMinute: Int?) {
        savedState["dueDay"] = day
        savedState["remind"] = selectedMinute != null
        if (selectedMinute != null) savedState["draftMinute"] = selectedMinute
    }

    fun saveDebt(humanId: Int, debtId: Int, name: String, sum: BigDecimal,
                 date: String, info: String) {
        if (isSaving.value == true || savedHumanId.value != null) return
        val selectedDay = dueDay.value
        val reminderEnabled = remind.value == true
        val selectedMinute = minute.value.takeIf { reminderEnabled }
        val due = if (hasDueChange) DebtDueChange(selectedDay, selectedMinute) else null
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

    val currency = savedState.getLiveData("draftCurrency", savedState.get<String>("currency") ?: getDefaultCurrency.execute())
    val date: LiveData<String> = savedState.getLiveData(
        "selectedDebtDate", savedState.get<String>("date") ?: getCurrentDateUseCase.execute()
    )

    fun setStoredDate(date: String) {
        savedState["selectedDebtDate"] = date
    }
}
