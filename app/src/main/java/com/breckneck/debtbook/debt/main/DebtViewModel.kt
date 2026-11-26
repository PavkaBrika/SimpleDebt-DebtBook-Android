package com.breckneck.debtbook.debt.main

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.debtbook.debt.DebtUpgradeGuidance
import com.breckneck.debtbook.debt.DebtUpgradeTopics
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.model.PersonDebtHistory
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.usecase.Debt.GetAllDebts
import com.breckneck.deptbook.domain.usecase.Human.GetAllHumansUseCase
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
    private val upgradeGuidance: DebtUpgradeGuidance,
) : ViewModel() {

    private val TAG = "MainFragmentViewModel"

    private val _screenState = MutableLiveData(ScreenState.LOADING)
    val screenState: LiveData<ScreenState>
        get() = _screenState
    private var refreshJob: Job? = null

    internal var selectedCurrency = saved.get<String>("listCurrency") ?: getDefaultCurrency.execute()
        private set
    private val _balance = MutableLiveData<DebtListBalance>()
    internal val balance: LiveData<DebtListBalance> get() = _balance
    private var people: List<HumanDomain>? = null
    private var histories = emptyMap<Int, PersonDebtHistory>()
    private var currenciesWithDebts = emptySet<String>()
    private var hasEarlyEntries = false
    private val _availableUpgradeTopics = MutableLiveData<DebtUpgradeTopics?>()
    internal val availableUpgradeTopics: LiveData<DebtUpgradeTopics?> = _availableUpgradeTopics
    internal val upgradeProgress = upgradeGuidance.progress
    val overviewCurrencies = MutableLiveData<List<String>>()
    val loadFailed = MutableLiveData(false)
    val deadlines = MutableLiveData<Map<Int, String?>>()
    val settled = MutableLiveData<Set<Int>>()
    fun selectCurrency(value: String) {
        saved["listCurrency"] = value.takeUnless { it == getDefaultCurrency.execute() }
        selectedCurrency = value
        publishBalance()
        publishHumans()
        if (_availableUpgradeTopics.value != null) publishUpgradeTopics()
    }

    internal fun selectCurrencyFromPicker(value: String) {
        if (value != selectedCurrency && upgradeProgress.value?.showCurrencyHint == true) {
            // A restored picker can send its result before the list has reloaded.
            if (people == null) saved["pendingCurrencyHintChoice"] = value
            else if (value in currenciesWithDebts) upgradeGuidance.completeCurrencyHint()
        }
        selectCurrency(value)
    }

    internal fun recordFirstDebtListVisit() {
        _availableUpgradeTopics.value?.let(upgradeGuidance::recordFirstVisit)
    }

    internal fun dismissUpgradeNotice() = upgradeGuidance.dismissNotice()

    internal fun dismissCurrencyHint() = upgradeGuidance.completeCurrencyHint()

    private val _resultedHumanList = MutableLiveData<List<HumanDomain>>()
    val resultedHumanList: LiveData<List<HumanDomain>>
        get() = _resultedHumanList

    private val _humanFilter = saved.getLiveData("listFilter", Filter.ALL)
    val humanFilter: LiveData<Filter>
        get() = _humanFilter
    private val _searchFilter = saved.getLiveData("searchFilter", Filter.ALL)
    internal val searchFilter: LiveData<Filter> get() = _searchFilter
    private val _humanOrder = MutableLiveData<Pair<HumanOrderAttribute, Boolean>>()
    val humanOrder: LiveData<Pair<HumanOrderAttribute, Boolean>>
        get() = _humanOrder

    private val _isSortDialogOpened = saved.getLiveData("listOrderOpen", false)
    val isSortDialogOpened: LiveData<Boolean>
        get() = _isSortDialogOpened
    private val _isSearching = saved.getLiveData("listSearching", false)
    val isSearching: LiveData<Boolean>
        get() = _isSearching
    val query get() = _searchQuery
    private val _searchQuery = saved.getStateFlow("listQuery", "")

    init {
        getHumanOrder()
        viewModelScope.launch {
            _searchQuery.debounce(500).collectLatest {
                if (_isSearching.value == true) publishHumans()
            }
        }
    }

    /** A refresh is a request, including when the previous result was empty. */
    fun setListStateLoading() {
        refreshJob?.cancel()
        if (upgradeProgress.value == null) _availableUpgradeTopics.value = null
        refreshJob = viewModelScope.launch {
            if (people == null) {
                loadFailed.value = false
                _screenState.value = ScreenState.LOADING
            }
            try {
                val (persons, loans, recordedCurrencies) = withContext(Dispatchers.IO) {
                    val (persons, rows) = transaction.run { getAllHumansUseCase.execute() to getAllDebts.execute() }
                    val rowsByPerson = rows.groupBy { it.idHuman }
                    val histories = persons.associate { it.id to PersonDebtHistory.from(rowsByPerson[it.id].orEmpty()) }
                    val currencies = persons.filter { it.id in rowsByPerson }.mapTo(mutableSetOf()) { it.currency }
                    Triple(persons, histories, currencies)
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
                currenciesWithDebts = recordedCurrencies
                hasEarlyEntries = loans.values.any { it.earlyEntries.isNotEmpty() }
                if (saved.remove<String>("pendingCurrencyHintChoice") in recordedCurrencies) {
                    upgradeGuidance.completeCurrencyHint()
                }
                deadlines.value = loans.mapValues { (_, history) -> history.openLoans.mapNotNull { it.original.dueDay }.minOrNull() }
                settled.value = loans.filterValues { it.openLoans.isEmpty() && it.settledLoans.isNotEmpty() }.keys
                publishBalance()
                publishHumans()
                publishUpgradeTopics()
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

    private fun publishBalance() {
        val inCurrency = people?.filter { it.currency == selectedCurrency } ?: return
        _balance.value = DebtListBalance(
            currency = selectedCurrency,
            receivable = inCurrency.filter { it.sumDebt.signum() > 0 }.sumOf { it.sumDebt },
            payable = inCurrency.filter { it.sumDebt.signum() < 0 }.sumOf { it.sumDebt },
        )
    }

    private fun publishHumans() {
        val allPeople = people ?: return
        val searching = _isSearching.value == true
        val filter = (if (searching) _searchFilter.value else _humanFilter.value) ?: Filter.ALL
        val query = if (searching) _searchQuery.value.trim() else ""
        val visible = orderHumans(allPeople.filter { person ->
            (searching || person.currency == selectedCurrency) &&
                person.name.contains(query, ignoreCase = true) &&
                histories.getValue(person.id).matches(filter, person.sumDebt)
        })
        _resultedHumanList.value = visible
        _screenState.value = if (visible.isEmpty()) ScreenState.EMPTY else ScreenState.SUCCESS
    }

    fun onSetHumanSort(filter: Filter, order: Pair<HumanOrderAttribute, Boolean>) {
        if (_isSearching.value == true) _searchFilter.value = filter else _humanFilter.value = filter
        _humanOrder.value = order
        saved["listOrderByAmount"] = order.first == HumanOrderAttribute.Sum
        saved["listAscending"] = order.second
        publishHumans()
    }

    private fun publishUpgradeTopics() {
        _availableUpgradeTopics.value = DebtUpgradeTopics(
            otherCurrencies = currenciesWithDebts.any { it != selectedCurrency },
            earlyEntries = hasEarlyEntries,
        )
    }

    fun saveHumanOrder(order: Pair<HumanOrderAttribute, Boolean>) {
        setHumanOrder.execute(order = order)
    }

    fun onHumanSortDialogOpen() {
        _isSortDialogOpened.value = true
    }

    fun onHumanSortDialogClose() {
        _isSortDialogOpened.value = false
    }

    fun setSearchQuery(query: String) {
        saved["listQuery"] = query
    }

    fun onStartSearch() {
        setIsSearching(true)
    }

    fun onStopSearch() {
        saved["listQuery"] = ""
        _searchFilter.value = Filter.ALL
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
