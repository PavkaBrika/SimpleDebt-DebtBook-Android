package com.breckneck.debtbook.debt.main

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.model.PersonDebtHistory
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.usecase.Debt.GetAllDebts
import com.breckneck.deptbook.domain.usecase.Human.*
import com.breckneck.deptbook.domain.usecase.Settings.GetDefaultCurrency
import com.breckneck.deptbook.domain.usecase.Settings.GetHumanOrder
import com.breckneck.deptbook.domain.usecase.Settings.SetHumanOrder
import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.HumanOrderAttribute
import com.breckneck.deptbook.domain.util.ScreenState
import com.breckneck.deptbook.domain.util.sortHumans
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DebtListBalance(
    val currency: String,
    val receivable: BigDecimal,
    val payable: BigDecimal,
) {
    val total: BigDecimal get() = receivable + payable
}

@HiltViewModel
@OptIn(FlowPreview::class)
class DebtViewModel @Inject constructor(
    private val getAllHumansUseCase: GetAllHumansUseCase,
    private val getAllDebts: GetAllDebts,
    private val transaction: DataTransaction,
    private val getDefaultCurrency: GetDefaultCurrency,
    private val saved: SavedStateHandle,
    private val getHumanOrder: GetHumanOrder,
    private val setHumanOrder: SetHumanOrder,
    private val updateHuman: UpdateHuman
) : ViewModel() {

    private val TAG = "MainFragmentViewModel"

    private val _screenState = MutableLiveData(ScreenState.LOADING)
    val screenState: LiveData<ScreenState>
        get() = _screenState
    private var refreshJob: Job? = null

    private var selectedCurrency = saved.get<String>("listCurrency") ?: getDefaultCurrency.execute()
    private val _balance = MutableLiveData<DebtListBalance>()
    internal val balance: LiveData<DebtListBalance> get() = _balance
    private var people: List<HumanDomain>? = null
    private var histories = emptyMap<Int, PersonDebtHistory>()
    val overviewCurrencies = MutableLiveData<List<String>>()
    val loadFailed = MutableLiveData(false)
    val deadlines = MutableLiveData<Map<Int, String?>>()
    val settled = MutableLiveData<Set<Int>>()
    fun selectCurrency(value: String) {
        saved["listCurrency"] = value.takeUnless { it == getDefaultCurrency.execute() }
        selectedCurrency = value
        publishHumans()
    }

    private val _resultedHumanList = MutableLiveData<List<HumanDomain>>()
    val resultedHumanList: LiveData<List<HumanDomain>>
        get() = _resultedHumanList

    private val _humanFilter = saved.getLiveData("listFilter", Filter.ALL)
    val humanFilter: LiveData<Filter>
        get() = _humanFilter
    private val _humanOrder = MutableLiveData<Pair<HumanOrderAttribute, Boolean>>()
    val humanOrder: LiveData<Pair<HumanOrderAttribute, Boolean>>
        get() = _humanOrder

    private val _isSortDialogOpened = saved.getLiveData("listOrderOpen", false)
    val isSortDialogOpened: LiveData<Boolean>
        get() = _isSortDialogOpened
    private val _isChangeDebtNameDialogOpened = MutableLiveData<Boolean>()
    val isChangeDebtNameDialogOpened: LiveData<Boolean>
        get() = _isChangeDebtNameDialogOpened

    private val _changedHuman = MutableLiveData<HumanDomain>()
    val changedHuman: LiveData<HumanDomain>
        get() = _changedHuman
    private val _isSearching = saved.getLiveData("listSearching", false)
    val isSearching: LiveData<Boolean>
        get() = _isSearching
    val query get() = _searchQuery
    private val _searchQuery = saved.getStateFlow("listQuery", "")

    init {
        getHumanOrder()
        setListStateLoading()
        viewModelScope.launch {
            _searchQuery.debounce(500).collectLatest {
                if (_isSearching.value == true) publishHumans()
            }
        }
    }

    /** A refresh is a request, including when the previous result was empty. */
    fun setListStateLoading() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (people == null) {
                loadFailed.value = false
                _screenState.value = ScreenState.LOADING
            }
            try {
                val (persons, loans) = withContext(Dispatchers.IO) {
                    val (persons, rows) = transaction.run { getAllHumansUseCase.execute() to getAllDebts.execute() }
                    val rowsByPerson = rows.groupBy { it.idHuman }
                    persons to persons.associate { it.id to PersonDebtHistory.from(rowsByPerson[it.id].orEmpty()) }
                }
                val defaultCurrency = getDefaultCurrency.execute()
                val currencies = (listOf(defaultCurrency) + persons.map { it.currency }).distinct()
                overviewCurrencies.value = currencies
                val selected = saved.get<String>("listCurrency")?.takeIf { it in currencies && it != defaultCurrency }
                saved["listCurrency"] = selected
                selectedCurrency = selected ?: defaultCurrency
                loadFailed.value = false
                people = persons
                histories = loans
                deadlines.value = loans.mapValues { (_, history) -> history.openLoans.mapNotNull { it.original.dueDay }.minOrNull() }
                settled.value = loans.filterValues { it.openLoans.isEmpty() && it.settledLoans.isNotEmpty() }.keys
                publishHumans()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadFailed.value = true
                Log.e(TAG, "Could not refresh debts", e)
            }
        }
    }

    fun getHumanOrder() {
        val order = getHumanOrder.execute()
        val byAmount = saved.get<Boolean>("listOrderByAmount")
        _humanOrder.value = if (byAmount == null) order else
            (if (byAmount) HumanOrderAttribute.Sum else HumanOrderAttribute.Date) to (saved.get<Boolean>("listAscending") ?: order.second)
    }

    private fun orderHumans(list: List<HumanDomain>): List<HumanDomain> {
        return sortHumans(
            debtList = list,
            order = humanOrder.value!!
        )
    }

    private fun publishHumans() {
        val inCurrency = people?.filter { it.currency == selectedCurrency } ?: return
        _balance.value = DebtListBalance(
            currency = selectedCurrency,
            receivable = inCurrency.filter { it.sumDebt.signum() > 0 }.sumOf { it.sumDebt },
            payable = inCurrency.filter { it.sumDebt.signum() < 0 }.sumOf { it.sumDebt },
        )
        val filter = humanFilter.value ?: Filter.ALL
        val sorted = orderHumans(inCurrency.filter { histories.getValue(it.id).matches(filter, it.sumDebt) })
        val query = if (_isSearching.value == true) _searchQuery.value.trim() else ""
        val visible = sorted.filter { it.name.contains(query, ignoreCase = true) }
        _resultedHumanList.value = visible
        _screenState.value = if (visible.isEmpty()) ScreenState.EMPTY else ScreenState.SUCCESS
    }

    fun onSetHumanSort(filter: Filter, order: Pair<HumanOrderAttribute, Boolean>) {
        _humanFilter.value = filter
        _humanOrder.value = order
        saved["listOrderByAmount"] = order.first == HumanOrderAttribute.Sum
        saved["listAscending"] = order.second
        publishHumans()
    }

    fun saveHumanOrder(order: Pair<HumanOrderAttribute, Boolean>) {
        setHumanOrder.execute(order = order)
    }

    fun updateHuman(human: HumanDomain) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { transaction.run {
                    val current = getAllHumansUseCase.execute().first { it.id == human.id }
                    updateHuman.execute(current.copy(name = human.name))
                } }
                setListStateLoading()
                Log.e(TAG, "human updated")
            } catch (e: Exception) {
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }

    fun onHumanSortDialogOpen() {
        _isSortDialogOpened.value = true
    }

    fun onHumanSortDialogClose() {
        _isSortDialogOpened.value = false
    }

    fun onChangeDebtNameDialogOpen(humanDomain: HumanDomain) {
        _isChangeDebtNameDialogOpened.value = true
        _changedHuman.value = humanDomain
    }

    fun onChangeDebtNameDialogClose() {
        _isChangeDebtNameDialogOpened.value = false
    }

    fun setSearchQuery(query: String) {
        saved["listQuery"] = query
    }

    fun onStartSearch() {
        setIsSearching(true)
    }

    fun onStopSearch() {
        saved["listQuery"] = ""
        setIsSearching(false)
    }

    fun setIsSearching(isSearching: Boolean) {
        _isSearching.value = isSearching
        publishHumans()
    }

    override fun onCleared() {
        Log.e(TAG, "MainFragment VM cleared")
        super.onCleared()
    }
}
