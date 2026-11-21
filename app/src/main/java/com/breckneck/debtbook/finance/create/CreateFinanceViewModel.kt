package com.breckneck.debtbook.finance.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.usecase.Finance.SetFinance
import com.breckneck.deptbook.domain.usecase.Finance.UpdateFinance
import com.breckneck.deptbook.domain.usecase.FinanceCategory.DeleteFinanceCategory
import com.breckneck.deptbook.domain.usecase.FinanceCategory.GetFinanceCategoriesByState
import com.breckneck.deptbook.domain.usecase.Settings.GetFinanceCurrency
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.LedgerDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class CreateFinanceViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val setFinance: SetFinance,
    getCurrency: GetFinanceCurrency,
    private val updateFinance: UpdateFinance,
    private val deleteCategory: DeleteFinanceCategory,
    private val getCategories: GetFinanceCategoriesByState,
) : ViewModel() {
    private val original = saved.get<Finance>("financeEdit")
    val editing = original != null
    val currency = original?.currency ?: saved.get<String>("currency") ?: getCurrency.execute()
    val amount get() = saved.get<String>("amount") ?: original?.let { DecimalInput.format(it.sum, currency = currency) }.orEmpty()
    val comment get() = saved.get<String>("comment") ?: original?.info.orEmpty()
    val day = saved.getLiveData("draftDay", original?.let { it.calendarDay ?: LedgerDate.fromDate(it.date) }
        ?: saved.get<String>("day") ?: saved.get<Long>("dayInMillis")?.let { LedgerDate.fromDate(Date(it)) } ?: LedgerDate.today())
    val direction = saved.getLiveData("draftDirection", FinanceCategoryState.valueOf(saved["categoryState"] ?: "EXPENSE"))
    val categoryId = saved.getLiveData("selectedCategory", original?.financeCategoryId ?: saved.get<Int>("categoryId") ?: 0)
    val categories = MutableLiveData<List<FinanceCategory>>(emptyList())
    val loadFailed = MutableLiveData(false)
    val deleting = MutableLiveData(false)
    val deleteFailed = MutableLiveData(false)
    private var load: Job? = null

    enum class SaveState { IDLE, SAVING, SAVED, ERROR }
    private val status = MutableLiveData(SaveState.IDLE)
    val saveState: LiveData<SaveState> = status

    fun changeAmount(value: String) { saved["amount"] = value }
    fun changeComment(value: String) { saved["comment"] = value }

    fun changeDirection(value: FinanceCategoryState) {
        if (editing || direction.value == value) return
        direction.value = value
        categoryId.value = 0
        categories.value = emptyList()
        reload()
    }

    fun reload() {
        load?.cancel()
        val selected = requireNotNull(direction.value)
        loadFailed.value = false
        load = viewModelScope.launch {
            try {
                categories.value = withContext(Dispatchers.IO) { getCategories.execute(selected) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadFailed.value = true
            }
        }
    }

    fun deleteSelected() {
        val category = categories.value?.find { it.id == categoryId.value } ?: return
        if (deleting.value == true || editing) return
        deleting.value = true
        deleteFailed.value = false
        viewModelScope.launch {
            try {
                // Room cascades to this category's transactions in the same statement.
                withContext(Dispatchers.IO) { deleteCategory.execute(category) }
                categoryId.value = 0
                reload()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                deleteFailed.value = true
            } finally {
                deleting.value = false
            }
        }
    }

    fun parsedAmount() = DecimalInput.parse(amount, currency, original?.sum)?.takeIf { it.signum() > 0 }

    fun save() {
        if (status.value == SaveState.SAVING || status.value == SaveState.SAVED || deleting.value == true) return
        val sum = parsedAmount() ?: return
        val category = original?.financeCategoryId ?: categories.value?.find { it.id == categoryId.value }?.id ?: return
        val day = requireNotNull(day.value)
        val sameDay = original != null && day == (original.calendarDay ?: LedgerDate.fromDate(original.date))
        val finance = Finance(
            id = original?.id ?: 0, sum = sum, info = comment, financeCategoryId = category, currency = currency,
            date = if (sameDay) requireNotNull(original).date else requireNotNull(LedgerDate.localDate(day)),
            calendarDay = if (sameDay) original?.calendarDay else day,
        )
        status.value = SaveState.SAVING
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (editing) updateFinance.execute(finance) else setFinance.execute(finance)
                }
                status.value = SaveState.SAVED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status.value = SaveState.ERROR
            }
        }
    }
}
