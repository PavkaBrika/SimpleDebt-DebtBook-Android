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
    private lateinit var vm: CreateFinanceViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val currency = mock<GetFinanceCurrency>()
        whenever(currency.execute()).thenReturn("€")
        vm = CreateFinanceViewModel(setFinance, mock(), currency, updateFinance, mock(), mock(), mock())
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
            vm.setFinance(finance)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertEquals(CreateFinanceViewModel.SaveState.SAVING, vm.saveState.value)
            vm.setFinance(finance)
            awaitState(CreateFinanceViewModel.SaveState.SAVED) { release.countDown() }
            vm.setFinance(finance)
            verify(setFinance, times(1)).execute(finance)
        } finally {
            release.countDown()
        }
    }

    @Test fun `failed edit remains retryable and reports success only after retry succeeds`() {
        doThrow(IllegalStateException("database unavailable")).whenever(updateFinance).execute(any())
        awaitState(CreateFinanceViewModel.SaveState.ERROR) { vm.editFinance(finance) }
        doNothing().whenever(updateFinance).execute(any())
        awaitState(CreateFinanceViewModel.SaveState.SAVED) { vm.editFinance(finance) }
        verify(updateFinance, times(2)).execute(finance)
    }
}
