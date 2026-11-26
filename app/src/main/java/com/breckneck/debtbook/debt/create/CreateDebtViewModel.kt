package com.breckneck.debtbook.debt.create

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.breckneck.debtbook.core.navigation.DebtForm
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.debtbook.app.DebtUsageAnalytics
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SavedDebtOwner(val id: Int, val name: String, val currency: String)

@HiltViewModel(assistedFactory = CreateDebtViewModel.Factory::class)
class CreateDebtViewModel @AssistedInject internal constructor(
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
    private val debtUsageAnalytics: DebtUsageAnalytics,
    @Assisted private val route: DebtForm,
) : ViewModel() {
    @AssistedFactory
    internal interface Factory {
        fun create(route: DebtForm): CreateDebtViewModel
    }


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
        if (route.idHuman <= 0) {
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
    private val initialDueDay = route.dueDayOnEdit?.let(LedgerDate::canonical)
    private val initialRemind = route.remindOnEdit
    val dueDay: LiveData<String?> = savedState.getLiveData("dueDay", initialDueDay)
    val remind: LiveData<Boolean> = savedState.getLiveData("remind", initialRemind)
    private val initialMinute = route.minuteOnEdit.takeIf { it >= 0 } ?: DebtReminderPolicy.DEFAULT_MINUTE
    val minute: LiveData<Int> = savedState.getLiveData("draftMinute", initialMinute)
    val hasRepayments = route.hasRepayments
    val canRemind = route.canRemind

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
                val owner = withContext(Dispatchers.IO) {
                    val (owner, reminderChanged) = transaction.run {
                        val owner = resolveOwner(humanId, name.trim(), draftCurrency, photo)
                        val id = owner.id
                        val reminderChanged = if (debtId > 0) {
                            val previous = requireNotNull(getDebts.execute(id).firstOrNull { it.id == debtId })
                            editDebt.execute(debtId, sum, id, info.ifBlank { null }, date, due)
                            addSum.execute(id, sum - previous.sum)
                            due?.schedulesReminderComparedTo(previous) == true
                        } else {
                            setDebt.execute(sum, id, info.ifBlank { null }, date, selectedDay, selectedMinute)
                            addSum.execute(id, sum)
                            false
                        }
                        owner to reminderChanged
                    }
                    when {
                        debtId <= 0 -> debtUsageAnalytics.debtCreated()
                        reminderChanged -> debtUsageAnalytics.existingDebtActionSaved()
                    }
                    owner
                }
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

    val currency = savedState.getLiveData("draftCurrency", route.currency ?: getDefaultCurrency.execute())
    val date: LiveData<String> = savedState.getLiveData(
        "selectedDebtDate", route.date ?: getCurrentDateUseCase.execute()
    )

    fun setStoredDate(date: String) {
        savedState["selectedDebtDate"] = date
    }
}
