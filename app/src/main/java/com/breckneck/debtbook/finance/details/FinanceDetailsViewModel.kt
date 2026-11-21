package com.breckneck.debtbook.finance.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.usecase.Finance.DeleteFinance
import com.breckneck.deptbook.domain.usecase.Finance.GetFinanceByCategoryId
import com.breckneck.deptbook.domain.usecase.FinanceCategory.GetFinanceCategoriesByState
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.LedgerDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class FinanceHistory(
    val entries: List<Finance> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val deleteFailed: Boolean = false,
    val deleting: Boolean = false,
    val categoryDeleted: Boolean = false,
)

@HiltViewModel
class FinanceDetailsViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val getFinances: GetFinanceByCategoryId,
    private val getCategories: GetFinanceCategoriesByState,
    private val deleteFinance: DeleteFinance,
) : ViewModel() {
    val categoryId = requireNotNull(saved.get<Int>("categoryId"))
    val name = requireNotNull(saved.get<String>("categoryName"))
    val currency = requireNotNull(saved.get<String>("currency"))
    val direction = FinanceCategoryState.valueOf(requireNotNull(saved.get<String>("categoryState")))
    val day = saved.get<String>("day") ?: LedgerDate.today()
    private val current = MutableStateFlow(FinanceHistory())
    internal val history = current.asStateFlow()
    private var load: Job? = null

    fun refresh() {
        load?.cancel()
        load = viewModelScope.launch {
            try {
                val (entries, missing) = withContext(Dispatchers.IO) {
                    val list = getFinances.execute(categoryId).filter { it.currency == currency }
                    list to (list.isEmpty() && getCategories.execute(direction).none { it.id == categoryId })
                }
                current.value = FinanceHistory(entries = entries, loading = false, categoryDeleted = missing)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                current.value = current.value.copy(loading = false, loadFailed = true, deleting = false)
            }
        }
    }

    fun delete(entry: Finance) {
        if (current.value.deleting) return
        load?.cancel()
        current.value = current.value.copy(deleting = true, deleteFailed = false)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { deleteFinance.execute(entry) }
                current.value = current.value.copy(entries = current.value.entries.filterNot { it.id == entry.id }, deleting = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                current.value = current.value.copy(deleting = false, deleteFailed = true)
            }
        }
    }
}
