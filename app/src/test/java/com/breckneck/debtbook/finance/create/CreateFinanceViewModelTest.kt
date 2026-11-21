package com.breckneck.debtbook.finance.create

import java.math.BigDecimal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.usecase.Finance.SetFinance
import com.breckneck.deptbook.domain.usecase.Finance.UpdateFinance
import com.breckneck.deptbook.domain.usecase.Settings.GetFinanceCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CreateFinanceViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val setFinance = mock<SetFinance>()
    private val updateFinance = mock<UpdateFinance>()
    private val finance = Finance(sum = "12.34".toBigDecimal().stripTrailingZeros(), info = "", financeCategoryId = 1, date = Date())
    private lateinit var saved: androidx.lifecycle.SavedStateHandle
    private lateinit var vm: CreateFinanceViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val currency = mock<GetFinanceCurrency>()
        whenever(currency.execute()).thenReturn("€")
        saved = androidx.lifecycle.SavedStateHandle(mapOf("currency" to "EUR", "categoryId" to 1))
        val categories = mock<com.breckneck.deptbook.domain.usecase.FinanceCategory.GetFinanceCategoriesByState>()
        vm = CreateFinanceViewModel(saved, setFinance, currency, updateFinance, mock(), categories)
        vm.categories.value = listOf(com.breckneck.deptbook.domain.model.FinanceCategory(1, "Books",
            com.breckneck.deptbook.domain.util.FinanceCategoryState.EXPENSE, "#ffffff", 65))
        vm.changeAmount("12,34")
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun awaitState(state: CreateFinanceViewModel.SaveState, action: () -> Unit) {
        val reached = CountDownLatch(1)
        val observer = Observer<CreateFinanceViewModel.SaveState> { if (it == state) reached.countDown() }
        vm.saveState.observeForever(observer)
        try {
            action()
            assertTrue("Expected save state $state", reached.await(5, TimeUnit.SECONDS))
        } finally {
            vm.saveState.removeObserver(observer)
        }
    }

    @Test fun `creation waits for database completion and ignores repeated save taps`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            null
        }.whenever(setFinance).execute(any())
        try {
            vm.save()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertEquals(CreateFinanceViewModel.SaveState.SAVING, vm.saveState.value)
            vm.save()
            awaitState(CreateFinanceViewModel.SaveState.SAVED) { release.countDown() }
            vm.save()
            verify(setFinance, times(1)).execute(org.mockito.kotlin.check { assertEquals("12.34".toBigDecimal(), it.sum) })
        } finally {
            release.countDown()
        }
    }

    @Test fun `failed edit remains retryable and reports success only after retry succeeds`() {
        vm = CreateFinanceViewModel(androidx.lifecycle.SavedStateHandle(mapOf("financeEdit" to finance)),
            setFinance, mock(), updateFinance, mock(), mock())
        doThrow(IllegalStateException("database unavailable")).whenever(updateFinance).execute(any())
        awaitState(CreateFinanceViewModel.SaveState.ERROR) { vm.save() }
        doNothing().whenever(updateFinance).execute(any())
        awaitState(CreateFinanceViewModel.SaveState.SAVED) { vm.save() }
        verify(updateFinance, times(2)).execute(finance)
    }
}
