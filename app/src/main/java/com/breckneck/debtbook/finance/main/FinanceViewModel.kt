package com.breckneck.debtbook.finance.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.model.FinanceCategoryWithFinances
import com.breckneck.deptbook.domain.usecase.FinanceCategory.GetAllCategoriesWithFinances
import com.breckneck.deptbook.domain.usecase.Settings.GetDefaultCurrency
import com.breckneck.deptbook.domain.usecase.Settings.GetFinanceCurrency
import com.breckneck.deptbook.domain.usecase.Settings.SetFinanceCurrency
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.FinanceInterval
import com.breckneck.deptbook.domain.util.LedgerDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import javax.inject.Inject

internal data class FinancePeriod(val interval: FinanceInterval, val day: String) {
    private fun calendar() = Calendar.getInstance().apply { time = requireNotNull(LedgerDate.localDate(day)) }

    private val field get() = when (interval) {
        FinanceInterval.DAY -> Calendar.DATE
        FinanceInterval.WEEK -> Calendar.WEEK_OF_YEAR
        FinanceInterval.MONTH -> Calendar.MONTH
        FinanceInterval.YEAR -> Calendar.YEAR
    }

    fun step(count: Int): String = calendar().apply {
        if (interval == FinanceInterval.MONTH || interval == FinanceInterval.YEAR) set(Calendar.DAY_OF_MONTH, 1)
        add(field, count)
    }.let { LedgerDate.fromDate(it.time) }

    fun days(): ClosedRange<String> {
        val calendar = calendar()
        when (interval) {
            FinanceInterval.DAY -> Unit
            FinanceInterval.WEEK -> calendar.add(Calendar.DATE, -(calendar.get(Calendar.DAY_OF_WEEK) + 5).mod(7))
            FinanceInterval.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            FinanceInterval.YEAR -> calendar.set(Calendar.DAY_OF_YEAR, 1)
        }
        val start = LedgerDate.fromDate(calendar.time)
        calendar.add(field, 1)
        calendar.add(Calendar.DATE, -1)
        return start..LedgerDate.fromDate(calendar.time)
    }
}

internal data class CategoryTotal(val category: FinanceCategory, val amount: BigDecimal, val percent: Int = 0)

internal data class FinanceTotals(
    val categories: List<CategoryTotal> = emptyList(),
    val total: BigDecimal = BigDecimal.ZERO,
)

internal data class FinanceSummary(
    val currency: String,
    val direction: FinanceCategoryState,
    val period: FinancePeriod,
    private val totals: Map<FinanceCategoryState, FinanceTotals> = emptyMap(),
    val failed: Boolean = false,
) {
    fun totalsFor(direction: FinanceCategoryState): FinanceTotals = totals[direction] ?: FinanceTotals()
    val hasTotals: Boolean get() = totals.isNotEmpty()
    val loading: Boolean get() = !hasTotals && !failed
}

internal fun summarizeFinances(
    data: List<FinanceCategoryWithFinances>,
    currency: String,
    period: FinancePeriod,
): Map<FinanceCategoryState, FinanceTotals> {
    val days = period.days()
    val byDirection = data.mapNotNull { group ->
        val entries = group.financeList.filter {
            it.currency == currency && (it.calendarDay ?: LedgerDate.fromDate(it.date)) in days
        }
        entries.takeIf { it.isNotEmpty() }?.let { CategoryTotal(group.financeCategory, it.sumOf { entry -> entry.sum }) }
    }.groupBy { it.category.state }
    return FinanceCategoryState.entries.associateWith { direction ->
        val rows = byDirection[direction].orEmpty().sortedByDescending { it.amount }
        val total = rows.sumOf { it.amount }
        FinanceTotals(rows.map { row ->
            val percent = if (total.signum() == 0) 0 else row.amount.movePointRight(2)
                .divide(total, 0, RoundingMode.HALF_UP).toInt()
            row.copy(percent = percent)
        }, total)
    }
}

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val getCurrency: GetFinanceCurrency,
    private val getDefaultCurrency: GetDefaultCurrency,
    private val saveCurrency: SetFinanceCurrency,
    private val getEntries: GetAllCategoriesWithFinances,
) : ViewModel() {
    private val current = MutableStateFlow(FinanceSummary(
        getCurrency.execute(getDefaultCurrency::execute), saved["direction"] ?: FinanceCategoryState.EXPENSE,
        FinancePeriod(saved["interval"] ?: FinanceInterval.DAY, saved["day"] ?: LedgerDate.today()),
    ))
    internal val summary = current.asStateFlow()
    private var requestedPeriod = current.value.period
    private var requestedCurrency = current.value.currency
    private var entries: List<FinanceCategoryWithFinances>? = null
    private var load: Job? = null
    private var calculation: Job? = null

    fun setCurrency(currency: String) {
        saveCurrency.execute(currency)
        requestedCurrency = currency
        updateSummaries()
    }

    internal fun setDirection(direction: FinanceCategoryState) {
        saved["direction"] = direction
        current.value = current.value.copy(direction = direction)
    }

    internal fun setPeriod(period: FinancePeriod) {
        saved["interval"] = period.interval
        saved["day"] = period.day
        requestedPeriod = period
        updateSummaries()
    }

    internal fun stepPeriod(count: Int) {
        setPeriod(requestedPeriod.copy(day = requestedPeriod.step(count)))
    }

    fun refresh() {
        val currency = getCurrency.execute(getDefaultCurrency::execute)
        if (currency != requestedCurrency) {
            requestedCurrency = currency
            updateSummaries()
        }
        load?.cancel()
        load = viewModelScope.launch {
            try {
                entries = withContext(Dispatchers.IO) { getEntries.execute() }
                current.value = current.value.copy(failed = false)
                updateSummaries()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                current.value = current.value.copy(failed = true)
            }
        }
    }

    private fun updateSummaries() {
        calculation?.cancel()
        val period = requestedPeriod
        val currency = requestedCurrency
        val data = entries
        if (data == null) {
            current.value = current.value.copy(period = period, currency = currency)
            return
        }
        calculation = viewModelScope.launch {
            val totals = withContext(Dispatchers.Default) { summarizeFinances(data, currency, period) }
            current.value = current.value.copy(period = period, currency = currency, totals = totals)
        }
    }
}
