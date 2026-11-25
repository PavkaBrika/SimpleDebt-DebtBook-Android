package com.breckneck.debtbook.debt.create

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.DebtDueChange
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.repository.HumanRepository
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

internal data class SavedDebtOwner(val id: Int, val name: String, val currency: String)

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
    private val savedState: SavedStateHandle,
    private val people: HumanRepository,
) : ViewModel() {

    val isSaving = MutableLiveData(false)
    val saveError = MutableLiveData(false)
    internal val draftPhoto = savedState.getLiveData<String?>("draftPersonPhoto", null)
    internal val loadingPhoto = MutableLiveData(false)
    internal val photoError = MutableLiveData(false)
    internal val pickedContact = MutableLiveData<String?>()
    internal val savedOwner = MutableLiveData<SavedDebtOwner>()
    internal val needsPersonChoice = MutableLiveData(false)
    private val _availablePeople = MutableLiveData<List<HumanDomain>>(emptyList())
    internal val availablePeople: LiveData<List<HumanDomain>> = _availablePeople
    internal val selectedPersonId = savedState.getLiveData<Int?>("draftPersonId", null)

    init {
        if ((savedState.get<Int>("idHuman") ?: -1) <= 0) {
            viewModelScope.launch {
                reloadPeople()
            }
        }
    }

    private suspend fun reloadPeople() {
        try {
            _availablePeople.value = withContext(Dispatchers.IO) { people.getAllHumans() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("CreateDebt", "Could not read people", error)
        }
    }

    internal fun selectPerson(id: Int?) {
        savedState["draftPersonId"] = id
        needsPersonChoice.value = false
    }

    internal fun selectCurrency(value: String) {
        if (currency.value == value) return
        currency.value = value
        needsPersonChoice.value = false
    }

    internal fun setPhoto(readPhoto: suspend () -> String?) {
        if (isSaving.value == true || loadingPhoto.value == true) return
        loadingPhoto.value = true
        photoError.value = false
        viewModelScope.launch {
            try {
                savedState["draftPersonPhoto"] = readPhoto()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                photoError.value = true
            } finally {
                loadingPhoto.value = false
            }
        }
    }
    private val initialDueDay = savedState.get<String>("dueDayOnEdit")?.let(LedgerDate::canonical)
    private val initialRemind = savedState.get<Boolean>("remindOnEdit") == true
    val dueDay: LiveData<String?> = savedState.getLiveData("dueDay", initialDueDay)
    val remind: LiveData<Boolean> = savedState.getLiveData("remind", initialRemind)
    private val initialMinute = savedState.get<Int>("minuteOnEdit") ?: DebtReminderPolicy.DEFAULT_MINUTE
    val minute: LiveData<Int> = savedState.getLiveData("draftMinute", initialMinute)
    val hasRepayments = savedState.get<Boolean>("hasRepayments") == true
    val canRemind = savedState.get<Boolean>("canRemind") ?: true

    val hasDueChange: Boolean
        get() = canRemind && (dueDay.value != initialDueDay || remind.value != initialRemind ||
            (remind.value == true && minute.value != initialMinute))

    fun hasPastReminder(): Boolean {
        val day = dueDay.value ?: return false
        val time = minute.value ?: return false
        return hasDueChange && remind.value == true && DebtReminderPolicy.trigger(day, time) <= System.currentTimeMillis()
    }

    fun setDueDraft(day: String?, selectedMinute: Int?) {
        if (!canRemind) return
        savedState["dueDay"] = day
        savedState["remind"] = selectedMinute != null
        if (selectedMinute != null) savedState["draftMinute"] = selectedMinute
    }

    fun saveDebt(humanId: Int, debtId: Int, name: String, sum: BigDecimal,
                 date: String, info: String) {
        if (isSaving.value == true || loadingPhoto.value == true || savedOwner.value != null) return
        val draftCurrency = requireNotNull(currency.value)
        val photo = draftPhoto.value
        val selectedDay = dueDay.value
        val reminderEnabled = remind.value == true
        val selectedMinute = minute.value.takeIf { reminderEnabled }
        val due = if (hasDueChange) DebtDueChange(selectedDay, selectedMinute) else null
        isSaving.value = true
        saveError.value = false
        needsPersonChoice.value = false
        viewModelScope.launch {
            try {
                val owner = withContext(Dispatchers.IO) { transaction.run {
                    val owner = resolveOwner(humanId, name.trim(), draftCurrency, photo)
                    val id = owner.id
                    if (debtId > 0) {
                        val previous = requireNotNull(getDebts.execute(id).firstOrNull { it.id == debtId })
                        editDebt.execute(debtId, sum, id, info.ifBlank { null }, date, due)
                        addSum.execute(id, sum - previous.sum)
                    } else {
                        setDebt.execute(sum, id, info.ifBlank { null }, date, selectedDay, selectedMinute)
                        addSum.execute(id, sum)
                    }
                    owner
                } }
                savedOwner.value = owner
            } catch (_: AmbiguousDebtOwner) {
                needsPersonChoice.value = true
                reloadPeople()
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

    private fun resolveOwner(id: Int, name: String, currency: String, photo: String?): SavedDebtOwner {
        val existing = if (id > 0) {
            requireNotNull(people.getHuman(id))
        } else {
            val matches = people.getAllHumans().filter { it.currency == currency && it.name.trim().equals(name, ignoreCase = true) }
            if (matches.size > 1) throw AmbiguousDebtOwner()
            matches.singleOrNull()
        }
        if (existing != null) return SavedDebtOwner(existing.id, existing.name, existing.currency)
        require(name.isNotBlank())
        setHuman.execute(name, BigDecimal.ZERO, currency, photo)
        return SavedDebtOwner(getLastHumanId.exectute(), name, currency)
    }

    private class AmbiguousDebtOwner : IllegalStateException()

    val currency = savedState.getLiveData("draftCurrency", savedState.get<String>("currency") ?: getDefaultCurrency.execute())
    val date: LiveData<String> = savedState.getLiveData(
        "selectedDebtDate", savedState.get<String>("date") ?: getCurrentDateUseCase.execute()
    )

    fun setStoredDate(date: String) {
        savedState["selectedDebtDate"] = date
    }
}
