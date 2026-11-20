package com.breckneck.debtbook.debt.details

import java.math.BigDecimal
import com.breckneck.deptbook.domain.util.compareTo

import com.breckneck.deptbook.domain.repository.DataTransaction
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.usecase.Debt.DeleteDebtUseCase
import com.breckneck.deptbook.domain.usecase.Debt.DeleteDebtsByHumanIdUseCase
import com.breckneck.deptbook.domain.usecase.Debt.GetAllDebtsByIdUseCase
import com.breckneck.deptbook.domain.usecase.Human.AddSumUseCase
import com.breckneck.deptbook.domain.usecase.Human.DeleteHumanUseCase
import com.breckneck.deptbook.domain.usecase.Human.GetHumanSumDebtUseCase
import com.breckneck.deptbook.domain.usecase.Human.GetLastHumanIdUseCase
import com.breckneck.deptbook.domain.usecase.Settings.GetDebtOrder
import com.breckneck.deptbook.domain.usecase.Settings.SetDebtOrder
import com.breckneck.deptbook.domain.util.DebtOrderAttribute
import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.RepaymentPolicy
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.usecase.Debt.RecordRepaymentUseCase
import com.breckneck.deptbook.domain.usecase.Debt.RepaymentExceedsRemaining
import com.breckneck.deptbook.domain.util.LedgerDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class DebtDetailsState { LOADING, SUCCESS, EMPTY, ERROR }
enum class RepaymentError { INVALID_AMOUNT, EXCEEDS_REMAINING, SAVE_FAILED }

@HiltViewModel
class DebtDetailsViewModel @Inject constructor(
    private val getAllDebtsByIdUseCase: GetAllDebtsByIdUseCase,
    private val getLastHumanIdUseCase: GetLastHumanIdUseCase,
    private val getHumanSumDebtUseCase: GetHumanSumDebtUseCase,
    private val deleteHumanUseCase: DeleteHumanUseCase,
    private val deleteDebtsByHumanIdUseCase: DeleteDebtsByHumanIdUseCase,
    private val deleteDebtUseCase: DeleteDebtUseCase,
    private val addSumUseCase: AddSumUseCase,
    private val getDebtOrder: GetDebtOrder,
    private val setDebtOrder: SetDebtOrder,
    private val transaction: DataTransaction,
    private val savedStateHandle: SavedStateHandle,
    private val recordRepayment: RecordRepaymentUseCase
) : ViewModel() {

    private val _screenState = MutableLiveData(DebtDetailsState.LOADING)
    val screenState: LiveData<DebtDetailsState>
        get() = _screenState
    private var loadJob: Job? = null

    private val _initialDebtList = MutableLiveData<List<DebtDomain>>()
    val history: LiveData<List<DebtDomain>> get() = _initialDebtList
    private val _resultedDebtList = MutableLiveData<List<DebtDomain>>()
    val resultedDebtList: LiveData<List<DebtDomain>>
        get() = _resultedDebtList

    private var _humanId = savedStateHandle.get<Int>("idHuman")
    val humanId: Int?
        get() = _humanId
    private val _humanName = savedStateHandle.getLiveData<String>("name")
    val humanName: LiveData<String>
        get() = _humanName
    private val _humanCurrency = savedStateHandle.get<String>("currency")
    val humanCurrency: String?
        get() = _humanCurrency

    private val _overallSum = MutableLiveData<BigDecimal>()
    val overallSum: LiveData<BigDecimal>
        get() = _overallSum

    private val _debtDeleted = MutableLiveData<Unit>()
    val debtDeleted: LiveData<Unit>
        get() = _debtDeleted

    private val _isSortDialogShown by lazy { MutableLiveData<Boolean>() }
    val isSortDialogShown: LiveData<Boolean>
        get() = _isSortDialogShown
    private val _isHumanDeleteDialogShown by lazy { MutableLiveData<Boolean>() }
    val isHumanDeleteDialogShown: LiveData<Boolean>
        get() = _isHumanDeleteDialogShown
    private val _isShareDialogShown by lazy { MutableLiveData<Boolean>() }
    val isShareDialogShown: LiveData<Boolean>
        get() = _isShareDialogShown
    val debtActionId = savedStateHandle.getLiveData<Int?>("debtActionId", null)

    fun selectDebtAction(id: Int?) {
        debtActionId.value = id
    }

    private val _debtOrder = MutableLiveData<Pair<DebtOrderAttribute, Boolean>>()
    val debtOrder: LiveData<Pair<DebtOrderAttribute, Boolean>> = _debtOrder
    private val _debtFilter = MutableLiveData(
        savedStateHandle.get<String>("historyFilter")?.let { value ->
            Filter.entries.firstOrNull { it.name == value }
        } ?: Filter.ALL
    )
    val debtFilter: LiveData<Filter> = _debtFilter
    private val _isDeleting = MutableLiveData(false)
    val isDeleting: LiveData<Boolean> = _isDeleting
    private val _humanDeleted = MutableLiveData(false)
    val humanDeleted: LiveData<Boolean> = _humanDeleted
    private val _operationFailed = MutableLiveData(false)
    val operationFailed: LiveData<Boolean> = _operationFailed

    val repaymentDebtId = savedStateHandle.getLiveData<Int?>("repaymentDebtId", null)
    val repaymentAmount = savedStateHandle.getLiveData("repaymentAmount", "")
    val repaymentError = MutableLiveData<RepaymentError?>(null)
    val repaymentRemaining = MutableLiveData<BigDecimal>()

    fun originalDebt(id: Int): DebtDomain? = _initialDebtList.value?.firstOrNull { it.id == id && it.repaymentOfId == null }
    fun hasRepayments(id: Int): Boolean = _initialDebtList.value.orEmpty().any { it.repaymentOfId == id }
    fun remaining(debt: DebtDomain): BigDecimal = RepaymentPolicy.remaining(debt, _initialDebtList.value.orEmpty())

    fun openRepayment(debt: DebtDomain) {
        if (_isDeleting.value == true || debt.repaymentOfId != null || remaining(debt).signum() <= 0) return
        savedStateHandle["repaymentRequestId"] = UUID.randomUUID().toString()
        savedStateHandle["repaymentDay"] = LedgerDate.today()
        repaymentAmount.value = ""
        repaymentError.value = null
        repaymentRemaining.value = remaining(debt)
        repaymentDebtId.value = debt.id
    }

    fun changeRepaymentAmount(text: String) {
        repaymentAmount.value = text
        repaymentError.value = null
    }

    fun closeRepayment() {
        if (_isDeleting.value == true) return
        repaymentDebtId.value = null
        savedStateHandle.remove<String>("repaymentRequestId")
        savedStateHandle.remove<String>("repaymentDay")
    }

    fun saveRepayment() {
        if (_isDeleting.value == true || _screenState.value == DebtDetailsState.LOADING) return
        val debt = repaymentDebtId.value?.let(::originalDebt) ?: return
        val rest = remaining(debt)
        repaymentRemaining.value = rest
        val amount = DecimalInput.parse(repaymentAmount.value.orEmpty(), humanCurrency, previous = rest)
        if (amount == null || amount.signum() <= 0) {
            repaymentError.value = RepaymentError.INVALID_AMOUNT
            return
        }
        if (amount > rest) {
            repaymentError.value = RepaymentError.EXCEEDS_REMAINING
            return
        }
        val request = savedStateHandle.get<String>("repaymentRequestId") ?: return
        val day = savedStateHandle.get<String>("repaymentDay") ?: return
        _isDeleting.value = true
        repaymentError.value = null
        viewModelScope.launch {
            var saved = false
            try {
                withContext(Dispatchers.IO) { recordRepayment.execute(debt.id, debt.idHuman, amount, day, request) }
                saved = true
                _debtDeleted.value = Unit
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exceeded: RepaymentExceedsRemaining) {
                repaymentRemaining.value = exceeded.remaining
                repaymentError.value = RepaymentError.EXCEEDS_REMAINING
            } catch (_: Exception) {
                repaymentError.value = RepaymentError.SAVE_FAILED
            } finally {
                _isDeleting.value = false
            }
            if (saved) closeRepayment()
            retry()
        }
    }

    init {
        retry()
    }

    /** One load owns the spinner and publishes the history and its total together. */
    fun retry() {
        if ((_screenState.value == DebtDetailsState.LOADING && loadJob?.isActive == true) || _isDeleting.value == true) return
        _screenState.value = DebtDetailsState.LOADING
        loadJob = viewModelScope.launch {
            try {
                if (_debtOrder.value == null) {
                    val preference = getDebtOrder.execute()
                    val attribute = when (savedStateHandle.get<String>("historyOrder")) {
                        "creation" -> DebtOrderAttribute.CreationDate
                        "date" -> DebtOrderAttribute.Date
                        "sum" -> DebtOrderAttribute.Sum
                        else -> preference.first
                    }
                    _debtOrder.value = attribute to
                        (savedStateHandle.get<Boolean>("historyAscending") ?: preference.second)
                }
                val id = humanId?.takeIf { it > 0 } ?: if (savedStateHandle.get<Boolean>("newHuman") == true) {
                    withContext(Dispatchers.IO) { getLastHumanIdUseCase.exectute() }
                } else null
                require(id != null && id > 0) { "Missing history ID" }
                _humanId = id
                savedStateHandle["idHuman"] = id
                savedStateHandle["newHuman"] = false
                val (debts, sum) = withContext(Dispatchers.IO) {
                    transaction.run { getAllDebtsByIdUseCase.execute(id) to getHumanSumDebtUseCase.execute(id) }
                }
                _initialDebtList.value = debts
                _overallSum.value = sum
                val request = savedStateHandle.get<String>("repaymentRequestId")
                if (request != null && debts.any { it.operationId == request }) closeRepayment()
                repaymentDebtId.value?.let { selected ->
                    val original = debts.firstOrNull { it.id == selected && it.repaymentOfId == null }
                    if (original == null) closeRepayment() else repaymentRemaining.value = remaining(original)
                }
                applySort()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _screenState.value = DebtDetailsState.ERROR
            }
        }
    }

    private fun applySort() {
        try {
            val filtered = (_initialDebtList.value ?: emptyList()).filter { debt ->
                val direction = debt.repaymentOfId?.let { id -> originalDebt(id)?.sum } ?: debt.sum
                when (_debtFilter.value ?: Filter.ALL) {
                    Filter.ALL -> true
                    Filter.NEGATIVE -> direction <= 0
                    Filter.POSITIVE -> direction >= 0
                }
            }
            val result = com.breckneck.deptbook.domain.util.sortDebts(filtered, _debtOrder.value!!)
            _resultedDebtList.value = result
            _screenState.value = if (result.isEmpty()) DebtDetailsState.EMPTY else DebtDetailsState.SUCCESS
        } catch (_: Exception) {
            // Legacy locale-dependent dates can fail to sort. Keep the records unchanged;
            // the screen offers retry and lets the user select creation order instead.
            _screenState.value = DebtDetailsState.ERROR
        }
    }

    fun saveDebtOrder(order: Pair<DebtOrderAttribute, Boolean>) {
        setDebtOrder.execute(order)
    }

    fun deleteHuman() {
        val id = humanId ?: return
        if (_isDeleting.value == true) return
        _isDeleting.value = true
        _operationFailed.value = false
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { transaction.run {
                    deleteHumanUseCase.execute(id)
                    deleteDebtsByHumanIdUseCase.execute(id)
                } }
                _humanDeleted.value = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _operationFailed.value = true
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun deleteDebt(debtDomain: DebtDomain) {
        val id = humanId ?: return
        if (_isDeleting.value == true) return
        _isDeleting.value = true
        _operationFailed.value = false
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { transaction.run {
                    // Use the persisted amount, and make repeated deletion a no-op.
                    val current = getAllDebtsByIdUseCase.execute(id).firstOrNull { it.id == debtDomain.id }
                    if (current != null) {
                        deleteDebtUseCase.execute(current)
                        addSumUseCase.execute(humanId = id, sum = -current.sum)
                    }
                } }
                _debtDeleted.value = Unit
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _operationFailed.value = true
            } finally {
                _isDeleting.value = false
            }
            retry()
        }
    }

    fun dismissOperationError() {
        _operationFailed.value = false
    }

    fun onHumanDeleteDialogOpen() {
        _isHumanDeleteDialogShown.value = true
    }

    fun onHumanDeleteDialogClose() {
        _isHumanDeleteDialogShown.value = false
    }

    fun onShareDialogOpen() {
        _isShareDialogShown.value = true
    }

    fun onShareDialogClose() {
        _isShareDialogShown.value = false
    }

    fun onSortDialogOpen() {
        _isSortDialogShown.value = true
    }

    fun onOrderDialogClose() {
        _isSortDialogShown.value = false
    }

    fun onSetDebtSort(filter: Filter, order: Pair<DebtOrderAttribute, Boolean>) {
        _debtFilter.value = filter
        _debtOrder.value = order
        savedStateHandle["historyFilter"] = filter.name
        savedStateHandle["historyOrder"] = when (order.first) {
            DebtOrderAttribute.CreationDate -> "creation"
            DebtOrderAttribute.Date -> "date"
            DebtOrderAttribute.Sum -> "sum"
        }
        savedStateHandle["historyAscending"] = order.second
        if (_initialDebtList.value != null) applySort() else retry()
    }
}
