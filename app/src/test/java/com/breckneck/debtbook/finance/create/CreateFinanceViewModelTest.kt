package com.breckneck.debtbook.finance.create

import androidx.lifecycle.SavedStateHandle
import com.breckneck.debtbook.core.navigation.FinanceForm
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.usecase.FinanceCategory.GetFinanceCategoriesByState

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.usecase.Finance.SetFinance
import com.breckneck.deptbook.domain.usecase.Finance.UpdateFinance
import com.breckneck.deptbook.domain.usecase.Finance.GetFinanceByCategoryId
import com.breckneck.deptbook.domain.util.LedgerDate
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CreateFinanceViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val setFinance = mock<SetFinance>()
    private val updateFinance = mock<UpdateFinance>()
    private val finance = Finance(id = 8, currency = "EUR", sum = "12.34".toBigDecimal().stripTrailingZeros(), info = "", financeCategoryId = 1, date = Date())
    private val newEntry = FinanceForm("EXPENSE", "EUR", LedgerDate.today(), categoryId = 1)
    private lateinit var saved: SavedStateHandle
    private lateinit var vm: CreateFinanceViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        saved = SavedStateHandle()
        val categories = mock<GetFinanceCategoriesByState>()
        vm = CreateFinanceViewModel(saved, setFinance, mock(), updateFinance, mock(), categories, newEntry)
        vm.categories.value = listOf(FinanceCategory(1, "Books",
            FinanceCategoryState.EXPENSE, "#ffffff", 65))
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

    @Test fun `restored draft saves the selected ID amount day comment and currency`() {
        vm.changeAmount("255,75")
        vm.changeComment("Bookshop")
        vm.day.value = "2024-02-29"
        vm.categoryId.value = 17
        val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) })
        vm = CreateFinanceViewModel(snapshot, setFinance, mock(), updateFinance, mock(), mock(), newEntry)
        vm.categories.value = listOf(FinanceCategory(17, "Books", FinanceCategoryState.EXPENSE, "#FFFFFF", 65))
        awaitState(CreateFinanceViewModel.SaveState.SAVED) { vm.save() }
        verify(setFinance).execute(org.mockito.kotlin.check {
            assertEquals("255.75".toBigDecimal(), it.sum)
            assertEquals("2024-02-29", it.calendarDay)
            assertEquals("Bookshop", it.info)
            assertEquals("EUR", it.currency)
            assertEquals(17, it.financeCategoryId)
        })
    }

    @Test fun `failed edit remains retryable and reports success only after retry succeeds`() {
        val loaded = CountDownLatch(1)
        val getFinances = mock<GetFinanceByCategoryId> {
            onBlocking { execute(finance.financeCategoryId) } doReturn listOf(finance)
        }
        val edit = newEntry.copy(day = LedgerDate.fromDate(finance.date), financeId = finance.id)
        vm = CreateFinanceViewModel(saved, setFinance, getFinances, updateFinance, mock(), mock(), edit)
        val observer = Observer<CreateFinanceViewModel.OriginalState> {
            if (it == CreateFinanceViewModel.OriginalState.READY) loaded.countDown()
        }
        vm.originalState.observeForever(observer)
        try {
            assertTrue(loaded.await(5, TimeUnit.SECONDS))
        } finally {
            vm.originalState.removeObserver(observer)
        }
        saved.remove<String>("amount")
        doThrow(IllegalStateException("database unavailable")).whenever(updateFinance).execute(any())
        awaitState(CreateFinanceViewModel.SaveState.ERROR) { vm.save() }
        doNothing().whenever(updateFinance).execute(any())
        awaitState(CreateFinanceViewModel.SaveState.SAVED) { vm.save() }
        verify(updateFinance, times(2)).execute(finance)
    }

    @Test fun `missing edit never creates a new entry`() {
        val getFinances = mock<GetFinanceByCategoryId> {
            onBlocking { execute(newEntry.categoryId) } doReturn emptyList()
        }
        val edit = newEntry.copy(financeId = finance.id)
        vm = CreateFinanceViewModel(saved, setFinance, getFinances, updateFinance, mock(), mock(), edit)
        val missing = CountDownLatch(1)
        val observer = Observer<CreateFinanceViewModel.OriginalState> {
            if (it == CreateFinanceViewModel.OriginalState.MISSING) missing.countDown()
        }
        vm.originalState.observeForever(observer)
        try {
            assertTrue(missing.await(5, TimeUnit.SECONDS))
            vm.save()
            verifyNoInteractions(setFinance, updateFinance)
        } finally {
            vm.originalState.removeObserver(observer)
        }
    }
}
