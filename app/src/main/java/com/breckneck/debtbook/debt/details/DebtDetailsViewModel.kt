package com.breckneck.debtbook.debt.details

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.math.BigDecimal
import com.breckneck.deptbook.domain.repository.DataTransaction
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.model.PersonDebtHistory
import com.breckneck.deptbook.domain.usecase.Debt.DeleteDebtUseCase
import com.breckneck.deptbook.domain.usecase.Debt.DeleteDebtsByHumanIdUseCase
import com.breckneck.deptbook.domain.usecase.Debt.GetAllDebtsByIdUseCase
import com.breckneck.deptbook.domain.usecase.Human.AddSumUseCase
import com.breckneck.deptbook.domain.usecase.Human.DeleteHumanUseCase
import com.breckneck.deptbook.domain.usecase.Human.GetLastHumanIdUseCase
import com.breckneck.deptbook.domain.usecase.Settings.GetDebtOrder
import com.breckneck.deptbook.domain.usecase.Settings.SetDebtOrder
import com.breckneck.deptbook.domain.util.DebtOrderAttribute
import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.sortDebts
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

enum class DebtSection { OPEN, SETTLED, EARLY }
enum class DebtDetailsState { LOADING, SUCCESS, EMPTY, ERROR }
enum class RepaymentFeedback { SETTLED, OVER_LIMIT }

enum class RepaymentError { INVALID_AMOUNT, EXCEEDS_REMAINING, SAVE_FAILED }

@HiltViewModel
class DebtDetailsViewModel @Inject constructor(
    private val getAllDebtsByIdUseCase: GetAllDebtsByIdUseCase,
    private val getLastHumanIdUseCase: GetLastHumanIdUseCase,
    private val people: com.breckneck.deptbook.domain.repository.HumanRepository,
    private val deleteHumanUseCase: DeleteHumanUseCase,
    private val deleteDebtsByHumanIdUseCase: DeleteDebtsByHumanIdUseCase,
    private val deleteDebtUseCase: DeleteDebtUseCase,
    private val addSumUseCase: AddSumUseCase,
    private val getDebtOrder: GetDebtOrder,
    private val setDebtOrder: SetDebtOrder,
    private val transaction: DataTransaction,
    private val savedStateHandle: SavedStateHandle,
    private val editDebt: com.breckneck.deptbook.domain.usecase.Debt.EditDebtUseCase,
    private val recordRepayment: RecordRepaymentUseCase
) : ViewModel() {

    private val _screenState = MutableLiveData(DebtDetailsState.LOADING)
    val screenState: LiveData<DebtDetailsState>
        get() = _screenState
    private var loadJob: Job? = null

    private val _initialDebtList = MutableLiveData<List<DebtDomain>>()
    val history: LiveData<List<DebtDomain>> get() = _initialDebtList
    private val _loans = MutableLiveData<PersonDebtHistory>()
    val loans: LiveData<PersonDebtHistory> get() = _loans
    private val _section = MutableLiveData<DebtSection>()
    val section: LiveData<DebtSection> get() = _section
    private val _visibleDebtRows = MutableLiveData<List<DebtDomain>>()
    val visibleDebtRows: LiveData<List<DebtDomain>> get() = _visibleDebtRows

    // Sharing and export continue to use the person's complete, sorted history.
    private val _resultedDebtList = MutableLiveData<List<DebtDomain>>()
    val resultedDebtList: LiveData<List<DebtDomain>>
        get() = _resultedDebtList

    private var _humanId = savedStateHandle.get<Int>("idHuman")
    val humanId: Int?
        get() = _humanId
    private val _humanName = savedStateHandle.getLiveData<String>("name")
    val humanName: LiveData<String>
        get() = _humanName
    private val _humanPhoto = MutableLiveData<String?>()
    internal val humanPhoto: LiveData<String?> get() = _humanPhoto
    private var photoRevision = 0
    private var _humanCurrency = savedStateHandle.get<String>("currency")
    val humanCurrency: String?
        get() = _humanCurrency

    private val _overallSum = MutableLiveData<BigDecimal>()
    val overallSum: LiveData<BigDecimal>
        get() = _overallSum

    private val _openingAdjustment = MutableLiveData<BigDecimal>()
    val openingAdjustment: LiveData<BigDecimal> get() = _openingAdjustment

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
    private val feedback = MutableSharedFlow<RepaymentFeedback>(extraBufferCapacity = 1)
    val repaymentFeedback = feedback.asSharedFlow()
    val repaymentRemaining = MutableLiveData<BigDecimal>()

    fun originalDebt(id: Int): DebtDomain? = _loans.value?.loan(id)?.original
    fun hasRepayments(id: Int): Boolean = !_loans.value?.loan(id)?.repayments.isNullOrEmpty()
    fun remaining(debt: DebtDomain): BigDecimal = _loans.value?.loan(debt.id)?.remaining ?: BigDecimal.ZERO

    fun selectDebtSection(section: DebtSection) {
        if (section == _section.value) return
        savedStateHandle["debtSection"] = section
        _section.value = section
        if (_screenState.value == DebtDetailsState.SUCCESS || _screenState.value == DebtDetailsState.EMPTY) updateVisibleRows()
    }

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
            feedback.tryEmit(RepaymentFeedback.OVER_LIMIT)
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
                val settled = withContext(Dispatchers.IO) { recordRepayment.execute(debt.id, debt.idHuman, amount, day, request) }
                if (settled) feedback.tryEmit(RepaymentFeedback.SETTLED)
                saved = true
                _debtDeleted.value = Unit
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exceeded: RepaymentExceedsRemaining) {
                feedback.tryEmit(RepaymentFeedback.OVER_LIMIT)
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
        val photoRevisionAtLoad = photoRevision
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
                val (debts, person, personLoans) = withContext(Dispatchers.IO) {
                    val (rows, balance) = transaction.run {
                        getAllDebtsByIdUseCase.execute(id) to requireNotNull(people.getHuman(id))
                    }
                    Triple(rows, balance, PersonDebtHistory.from(rows))
                }
                _loans.value = personLoans
                val requested = savedStateHandle.get<DebtSection>("debtSection")
                    ?: DebtSection.SETTLED.takeIf { savedStateHandle.get<Boolean>("showSettledDebts") == true }
                _section.value = when {
                    requested == DebtSection.EARLY && personLoans.earlyEntries.isEmpty() -> DebtSection.OPEN
                    requested != null -> requested
                    personLoans.openLoans.isEmpty() && personLoans.earlyEntries.isNotEmpty() -> DebtSection.EARLY
                    else -> DebtSection.OPEN
                }
                _initialDebtList.value = debts
                _overallSum.value = person.sumDebt
                _openingAdjustment.value = person.openingAdjustment
                _humanName.value = person.name
                if (photoRevisionAtLoad == photoRevision) _humanPhoto.value = person.photo
                _humanCurrency = person.currency
                val request = savedStateHandle.get<String>("repaymentRequestId")
                if (request != null && debts.any { it.operationId == request }) closeRepayment()
                repaymentDebtId.value?.let { selected ->
                    val original = originalDebt(selected)
                    if (original == null) closeRepayment() else repaymentRemaining.value = remaining(original)
                }
                savedStateHandle.remove<Int>("openDebtId")?.let { requested ->
                    personLoans.loan(requested)?.let { loan ->
                        selectDebtSection(if (loan.remaining.signum() == 0) DebtSection.SETTLED else DebtSection.OPEN)
                        selectDebtAction(requested)
                    }
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
            val history = _loans.value ?: return
            val filter = _debtFilter.value ?: Filter.ALL
            val filtered = _initialDebtList.value.orEmpty().filter { history.includesEntry(it, filter) }
            _resultedDebtList.value = sortDebts(filtered, _debtOrder.value!!)
            updateVisibleRows()
        } catch (_: Exception) {
            // Legacy locale-dependent dates can fail to sort. Keep the records unchanged;
            // the screen offers retry and lets the user select creation order instead.
            _screenState.value = DebtDetailsState.ERROR
        }
    }

    private fun updateVisibleRows() {
        val history = _loans.value ?: return
        val sorted = _resultedDebtList.value.orEmpty()
        val rows = when (_section.value) {
            DebtSection.EARLY -> sorted.filter { it.repaymentOfId == null && history.loan(it.id) == null }
            else -> {
                val loans = if (_section.value == DebtSection.SETTLED) history.settledLoans else history.openLoans
                val sectionById = loans.associateBy { it.original.id }
                buildList {
                    for (row in sorted) {
                        val loan = sectionById[row.id] ?: continue
                        add(loan.original)
                        addAll(loan.repayments)
                    }
                }
            }
        }
        _visibleDebtRows.value = rows
        _screenState.value = if (rows.isEmpty()) DebtDetailsState.EMPTY else DebtDetailsState.SUCCESS
    }

    fun saveDebtOrder(order: Pair<DebtOrderAttribute, Boolean>) {
        setDebtOrder.execute(order)
    }

    suspend fun saveDue(id: Int, day: String?, minute: Int?) {
        val owner = requireNotNull(humanId)
        withContext(Dispatchers.IO) { transaction.run {
            val current = getAllDebtsByIdUseCase.execute(owner).first { it.id == id }
            editDebt.execute(id, current.sum, owner, current.info, current.date,
                com.breckneck.deptbook.domain.model.DebtDueChange(day, minute))
        } }
        retry()
    }

    fun rename(name: String) {
        val id = humanId ?: return
        if (name.isBlank() || _isDeleting.value == true) return
        _isDeleting.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { transaction.run {
                    people.updateHuman(requireNotNull(people.getHuman(id)).copy(name = name.trim()))
                } }
                _humanName.value = name.trim()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _operationFailed.value = true
            } finally {
                _isDeleting.value = false
            }
        }
    }

    internal fun updatePhoto(readPhoto: suspend () -> String?) {
        val id = humanId ?: return
        if (_isDeleting.value == true) return
        _operationFailed.value = false
        _isDeleting.value = true
        viewModelScope.launch {
            try {
                val photo = readPhoto()
                withContext(Dispatchers.IO) { transaction.run {
                    people.updateHuman(requireNotNull(people.getHuman(id)).copy(photo = photo))
                } }
                photoRevision++
                _humanPhoto.value = photo
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _operationFailed.value = true
            } finally {
                _isDeleting.value = false
            }
        }
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
