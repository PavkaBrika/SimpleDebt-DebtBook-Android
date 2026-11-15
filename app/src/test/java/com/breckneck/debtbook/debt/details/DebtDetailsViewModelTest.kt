package com.breckneck.debtbook.debt.details

import java.math.BigDecimal

import com.breckneck.deptbook.domain.repository.DataTransaction
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class DebtDetailsViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val debts: GetAllDebtsByIdUseCase = mock()
    private val latestId: GetLastHumanIdUseCase = mock()
    private val sum: GetHumanSumDebtUseCase = mock()
    private val deleteHuman: DeleteHumanUseCase = mock()
    private val deleteHumanDebts: DeleteDebtsByHumanIdUseCase = mock()
    private val deleteDebt: DeleteDebtUseCase = mock()
    private val addSum: AddSumUseCase = mock()
    private val getOrder: GetDebtOrder = mock()
    private val setOrder: SetDebtOrder = mock()
    private val record = DebtDomain(9, "12.34".toBigDecimal().stripTrailingZeros(), 7, null, "1 Jan 2026")

    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        whenever(getOrder.execute()).thenReturn(DebtOrderAttribute.CreationDate to false)
        whenever(debts.execute(any())).thenReturn(listOf(record))
        whenever(sum.execute(any())).thenReturn(record.sum)
    }
    @After fun teardown() = Dispatchers.resetMain()

    private fun vm(state: SavedStateHandle = SavedStateHandle(mapOf("idHuman" to 7))) =
        DebtDetailsViewModel(debts, latestId, sum, deleteHuman, deleteHumanDebts, deleteDebt,
            addSum, getOrder, setOrder, object : DataTransaction {
                override fun <T> run(block: () -> T): T = block()
            }, state)

    @Test fun `restored ID loads history without querying latest person`() {
        val model = vm(SavedStateHandle(mapOf("idHuman" to 7, "newHuman" to true)))
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(listOf(record), model.resultedDebtList.value)
        assertEquals(0, ("12.34".toBigDecimal().stripTrailingZeros()).compareTo(model.overallSum.value!!!!))
        verify(debts).execute(7)
        verifyNoInteractions(latestId)
    }

    @Test fun `legacy new person resolution is saved for process recreation`() {
        whenever(latestId.exectute()).thenReturn(7)
        val saved = SavedStateHandle(mapOf("newHuman" to true))
        vm(saved).screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(7, saved.get<Int>("idHuman"))
        assertEquals(false, saved.get<Boolean>("newHuman"))
        whenever(latestId.exectute()).thenReturn(99)
        vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
            .screenState.await { it == DebtDetailsState.SUCCESS }
        verify(latestId, times(1)).exectute()
        verify(debts, times(2)).execute(7)
    }

    @Test fun `missing arguments show error instead of loading or empty history`() {
        val model = vm(SavedStateHandle())
        model.screenState.await { it == DebtDetailsState.ERROR }
        verifyNoInteractions(debts, sum)
    }

    @Test fun `read failure is visible and retry loads records`() {
        whenever(debts.execute(7)).thenThrow(IllegalStateException("database unavailable"))
            .thenReturn(listOf(record))
        val model = vm()
        model.screenState.await { it == DebtDetailsState.ERROR }
        model.retry()
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(listOf(record), model.resultedDebtList.value)
    }

    @Test fun `unknown legacy date remains visible and can switch to creation order`() {
        whenever(getOrder.execute()).thenReturn(DebtOrderAttribute.Date to false)
        whenever(debts.execute(7)).thenReturn(listOf(record.copy(date = "unknown date"), record.copy(id = 10)))
        val model = vm()
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals("unknown date", model.resultedDebtList.value!!.last().date)
        model.onSetDebtSort(Filter.ALL, DebtOrderAttribute.CreationDate to false)
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(2, model.resultedDebtList.value!!.size)
    }

    @Test fun `filter and sort survive reconstructed saved state`() {
        val saved = SavedStateHandle(mapOf("idHuman" to 7))
        val model = vm(saved)
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        model.onSetDebtSort(Filter.NEGATIVE, DebtOrderAttribute.Sum to true)
        assertEquals(DebtDetailsState.EMPTY, model.screenState.value)
        val recreated = vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        recreated.screenState.await { it == DebtDetailsState.EMPTY }
        assertEquals(Filter.NEGATIVE, recreated.debtFilter.value)
        assertEquals(DebtOrderAttribute.Sum to true, recreated.debtOrder.value)
    }

    @Test fun `human deletion navigates only after both writes complete and ignores duplicate tap`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            started.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
            null
        }.whenever(deleteHuman).execute(7)
        val model = vm()
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        model.deleteHuman()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        model.deleteHuman()
        assertEquals(false, model.humanDeleted.value)
        release.countDown()
        model.humanDeleted.await { it == true }
        verify(deleteHuman, times(1)).execute(7)
        verify(deleteHumanDebts, times(1)).execute(7)
    }

    private fun <T> LiveData<T>.await(predicate: (T?) -> Boolean) {
        val latch = CountDownLatch(1)
        val observer = Observer<T> { if (predicate(it)) latch.countDown() }
        observeForever(observer)
        try { assertTrue("LiveData did not reach expected state; actual=$value", latch.await(5, TimeUnit.SECONDS)) }
        finally { removeObserver(observer) }
    }
}
