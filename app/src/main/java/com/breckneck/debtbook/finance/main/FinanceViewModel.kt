package com.breckneck.debtbook.finance.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.model.FinanceCategoryWithFinances
import com.breckneck.deptbook.domain.usecase.FinanceCategory.GetAllCategoriesWithFinances
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

internal data class FinanceSummary(
    val currency: String,
    val direction: FinanceCategoryState,
    val period: FinancePeriod,
    val categories: List<CategoryTotal> = emptyList(),
    val total: BigDecimal = BigDecimal.ZERO,
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    fun withEntries(data: List<FinanceCategoryWithFinances>): FinanceSummary {
        val days = period.days()
        val rows = data.filter { it.financeCategory.state == direction }.mapNotNull { group ->
            val entries = group.financeList.filter {
                it.currency == currency && (it.calendarDay ?: LedgerDate.fromDate(it.date)) in days
            }
            if (entries.isEmpty()) null else CategoryTotal(group.financeCategory, entries.sumOf { it.sum })
        }.sortedByDescending { it.amount }
        val total = rows.sumOf { it.amount }
        return copy(total = total, loading = false, failed = false, categories = rows.map {
            val percent = if (total.signum() == 0) 0 else it.amount.movePointRight(2)
                .divide(total, 0, RoundingMode.HALF_UP).toInt()
            it.copy(percent = percent)
        })
    }
}

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    getCurrency: GetFinanceCurrency,
    private val saveCurrency: SetFinanceCurrency,
    private val getEntries: GetAllCategoriesWithFinances,
) : ViewModel() {
    private val current = MutableStateFlow(FinanceSummary(
        getCurrency.execute(), saved["direction"] ?: FinanceCategoryState.EXPENSE,
        FinancePeriod(saved["interval"] ?: FinanceInterval.DAY, saved["day"] ?: LedgerDate.today()),
    ))
    internal val summary = current.asStateFlow()
    private var load: Job? = null

    fun setCurrency(currency: String) {
        saveCurrency.execute(currency)
        refresh(current.value.copy(currency = currency, loading = true))
    }

    internal fun setDirection(direction: FinanceCategoryState) {
        saved["direction"] = direction
        refresh(current.value.copy(direction = direction, loading = true))
    }

    internal fun setPeriod(period: FinancePeriod) {
        saved["interval"] = period.interval
        saved["day"] = period.day
        refresh(current.value.copy(period = period, loading = true))
    }

    fun refresh() = refresh(current.value)

    private fun refresh(selection: FinanceSummary) {
        load?.cancel()
        current.value = selection.copy(failed = false)
        load = viewModelScope.launch {
            try {
                current.value = withContext(Dispatchers.IO) { selection.withEntries(getEntries.execute()) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                current.value = selection.copy(loading = false, failed = true)
            }
        }
    }
}
