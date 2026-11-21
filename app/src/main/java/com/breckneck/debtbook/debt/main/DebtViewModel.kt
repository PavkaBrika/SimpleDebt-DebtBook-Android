package com.breckneck.debtbook.debt.main

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.model.PersonDebtHistory
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.usecase.Debt.GetAllDebts
import com.breckneck.deptbook.domain.usecase.Human.*
import com.breckneck.deptbook.domain.usecase.Settings.GetFirstMainCurrency
import com.breckneck.deptbook.domain.usecase.Settings.GetHumanOrder
import com.breckneck.deptbook.domain.usecase.Settings.GetSecondMainCurrency
import com.breckneck.deptbook.domain.usecase.Settings.SetHumanOrder
import com.breckneck.deptbook.domain.util.HumanOrderAttribute
import com.breckneck.deptbook.domain.util.ScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
@OptIn(FlowPreview::class)
class DebtViewModel @Inject constructor(
    private val getAllHumansUseCase: GetAllHumansUseCase,
    private val getAllDebts: GetAllDebts,
    private val transaction: DataTransaction,
    private val getAllDebtsSumUseCase: GetAllDebtsSumUseCase,
    private val getFirstMainCurrency: GetFirstMainCurrency,
    private val getSecondMainCurrency: GetSecondMainCurrency,
    private val getHumanOrder: GetHumanOrder,
    private val setHumanOrder: SetHumanOrder,
    private val updateHuman: UpdateHuman
) : ViewModel() {

    private val TAG = "MainFragmentViewModel"

    private val _screenState = MutableLiveData(ScreenState.LOADING)
    val screenState: LiveData<ScreenState>
        get() = _screenState
    private var refreshJob: Job? = null

    private val _mainSums = MutableLiveData<Pair<String, String>>()
    val mainSums: LiveData<Pair<String, String>>
        get() = _mainSums

    private var peopleByFilter: Map<Filter, List<HumanDomain>> = emptyMap()
    private val _resultedHumanList = MutableLiveData<List<HumanDomain>>()
    val resultedHumanList: LiveData<List<HumanDomain>>
        get() = _resultedHumanList

    private val _humanFilter = MutableLiveData<Filter>(Filter.ALL)
    val humanFilter: LiveData<Filter>
        get() = _humanFilter
    private val _humanOrder = MutableLiveData<Pair<HumanOrderAttribute, Boolean>>()
    val humanOrder: LiveData<Pair<HumanOrderAttribute, Boolean>>
        get() = _humanOrder

    private val _isSortDialogOpened = MutableLiveData<Boolean>()
    val isSortDialogOpened: LiveData<Boolean>
        get() = _isSortDialogOpened
    private val _isChangeDebtNameDialogOpened = MutableLiveData<Boolean>()
    val isChangeDebtNameDialogOpened: LiveData<Boolean>
        get() = _isChangeDebtNameDialogOpened

    private val _changedHuman = MutableLiveData<HumanDomain>()
    val changedHuman: LiveData<HumanDomain>
        get() = _changedHuman
    private val _changedHumanPosition = MutableLiveData<Int>()
    val changedHumanPosition: LiveData<Int>
        get() = _changedHumanPosition

    private val _isSearching = MutableLiveData(false)
    val isSearching: LiveData<Boolean>
        get() = _isSearching
    private val _searchQuery = MutableStateFlow("")

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
            _screenState.value = ScreenState.LOADING
            try {
                val (lists, sums) = withContext(Dispatchers.IO) {
                    val (people, rows, totals) = transaction.run {
                        Triple(
                            getAllHumansUseCase.execute(),
                            getAllDebts.execute(),
                            getAllDebtsSumUseCase.execute(getFirstMainCurrency.execute(), getSecondMainCurrency.execute())
                        )
                    }
                    val rowsByPerson = rows.groupBy { it.idHuman }
                    val histories = people.associate { person ->
                        person.id to PersonDebtHistory.from(rowsByPerson[person.id].orEmpty())
                    }
                    val lists = Filter.entries.associateWith { filter ->
                        people.filter { person -> histories.getValue(person.id).matches(filter, person.sumDebt) }
                    }
                    lists to totals
                }
                peopleByFilter = lists
                _mainSums.value = sums
                publishHumans()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not refresh debts", e)
                publishHumans()
            }
        }
    }

    fun getMainSums() {
        viewModelScope.launch {
            try {
                val sums = withContext(Dispatchers.IO) {
                    getAllDebtsSumUseCase.execute(
                        getFirstMainCurrency.execute(),
                        getSecondMainCurrency.execute()
                    )
                }
                _mainSums.value = sums
            } catch (e: Exception) {
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }

    fun getHumanOrder() {
        _humanOrder.value = getHumanOrder.execute()
    }

    private fun orderHumans(list: List<HumanDomain>): List<HumanDomain> {
        return com.breckneck.deptbook.domain.util.sortHumans(
            debtList = list,
            order = humanOrder.value!!
        )
    }

    private fun publishHumans() {
        val sorted = orderHumans(peopleByFilter[humanFilter.value].orEmpty())
        val query = if (_isSearching.value == true) _searchQuery.value.trim() else ""
        val visible = sorted.filter { it.name.contains(query, ignoreCase = true) }
        _resultedHumanList.value = visible
        _screenState.value = if (visible.isEmpty()) ScreenState.EMPTY else ScreenState.SUCCESS
    }

    fun onSetHumanSort(filter: Filter, order: Pair<HumanOrderAttribute, Boolean>) {
        _humanFilter.value = filter
        _humanOrder.value = order
        publishHumans()
    }

    fun saveHumanOrder(order: Pair<HumanOrderAttribute, Boolean>) {
        setHumanOrder.execute(order = order)
    }

    fun updateHuman(human: HumanDomain) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { updateHuman.execute(humanDomain = human) }
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

    fun onChangeDebtNameDialogOpen(humanDomain: HumanDomain, position: Int) {
        _isChangeDebtNameDialogOpened.value = true
        _changedHuman.value = humanDomain
        _changedHumanPosition.value = position
    }

    fun onChangeDebtNameDialogClose() {
        _isChangeDebtNameDialogOpened.value = false
    }

    fun setSearchQuery(query: String) {
        viewModelScope.launch {
            _searchQuery.emit(query)
        }
    }

    fun onStartSearch() {
        setIsSearching(true)
    }

    fun onStopSearch() {
        _searchQuery.value = ""
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
