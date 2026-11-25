package com.breckneck.debtbook.finance.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.debtbook.core.navigation.FinanceForm
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.usecase.Finance.GetFinanceByCategoryId
import com.breckneck.deptbook.domain.usecase.Finance.SetFinance
import com.breckneck.deptbook.domain.usecase.Finance.UpdateFinance
import com.breckneck.deptbook.domain.usecase.FinanceCategory.DeleteFinanceCategory
import com.breckneck.deptbook.domain.usecase.FinanceCategory.GetFinanceCategoriesByState
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.LedgerDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

@HiltViewModel(assistedFactory = CreateFinanceViewModel.Factory::class)
class CreateFinanceViewModel @AssistedInject internal constructor(
    private val saved: SavedStateHandle,
    private val setFinance: SetFinance,
    private val getFinances: GetFinanceByCategoryId,
    private val updateFinance: UpdateFinance,
    private val deleteCategory: DeleteFinanceCategory,
    private val getCategories: GetFinanceCategoriesByState,
    @Assisted private val route: FinanceForm,
) : ViewModel() {
    @AssistedFactory
    internal interface Factory {
        fun create(route: FinanceForm): CreateFinanceViewModel
    }

    private var original: Finance? = null
    val editing = route.financeId > 0
    val currency get() = original?.currency ?: route.currency
    val amount get() = saved.get<String>("amount") ?: original?.let { DecimalInput.format(it.sum, currency = currency) }.orEmpty()
    val comment get() = saved.get<String>("comment") ?: original?.info.orEmpty()
    val day = saved.getLiveData("draftDay", route.day)
    val direction = saved.getLiveData("draftDirection", FinanceCategoryState.valueOf(route.categoryState))
    val categoryId = saved.getLiveData("selectedCategory", route.categoryId.coerceAtLeast(0))

    enum class OriginalState { LOADING, READY, MISSING, FAILED }

    private val originalStatus = MutableLiveData(if (editing) OriginalState.LOADING else OriginalState.READY)
    val originalState: LiveData<OriginalState> = originalStatus
    private var originalLoad: Job? = null
    val categories = MutableLiveData<List<FinanceCategory>>(emptyList())
    val loadFailed = MutableLiveData(false)
    val deleting = MutableLiveData(false)
    val deleteFailed = MutableLiveData(false)
    private var load: Job? = null

    enum class SaveState { IDLE, SAVING, SAVED, ERROR }
    private val status = MutableLiveData(SaveState.IDLE)
    val saveState: LiveData<SaveState> = status

    init {
        if (editing) loadOriginal()
    }

    fun loadOriginal() {
        originalLoad?.cancel()
        originalStatus.value = OriginalState.LOADING
        originalLoad = viewModelScope.launch {
            try {
                original = withContext(Dispatchers.IO) {
                    getFinances.execute(route.categoryId).firstOrNull { it.id == route.financeId }
                }
                originalStatus.value = if (original == null) OriginalState.MISSING else OriginalState.READY
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                originalStatus.value = OriginalState.FAILED
            }
        }
    }

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
        if (originalStatus.value != OriginalState.READY) return
        val original = original
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
