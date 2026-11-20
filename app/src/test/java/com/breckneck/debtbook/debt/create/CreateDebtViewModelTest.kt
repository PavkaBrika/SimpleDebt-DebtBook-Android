package com.breckneck.debtbook.debt.create

import java.math.BigDecimal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.usecase.Debt.*
import com.breckneck.deptbook.domain.usecase.Human.*
import com.breckneck.deptbook.domain.usecase.Settings.GetDefaultCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CreateDebtViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val defaults: GetDefaultCurrency = mock()
    private val date: GetCurrentDateUseCase = mock()
    private val setHuman: SetHumanUseCase = mock()
    private val latest: GetLastHumanIdUseCase = mock()
    private val setDebt: SetDebtUseCase = mock()
    private val editDebt: EditDebtUseCase = mock()
    private val getDebts: GetAllDebtsByIdUseCase = mock()
    private val addSum: AddSumUseCase = mock()
    private val transaction = object : DataTransaction { override fun <T> run(block: () -> T): T = block() }
    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        whenever(defaults.execute()).thenReturn("EUR")
        whenever(date.execute()).thenReturn("14 Nov 2026")
        whenever(latest.exectute()).thenReturn(7)
    }
    @After fun teardown() = Dispatchers.resetMain()
    private fun vm() = CreateDebtViewModel(defaults, date, setHuman, latest, setDebt, editDebt, getDebts, addSum, transaction, SavedStateHandle())

    @Test fun `duplicate save is ignored and navigation waits for persistence`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); null }
            .whenever(setDebt).execute(any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull())
        val model = vm()
        model.saveDebt(7, -1, "Person", "12.34".toBigDecimal().stripTrailingZeros(), "14 Nov 2026", "")
        assertTrue(started.await(5, TimeUnit.SECONDS))
        model.saveDebt(7, -1, "Person", "12.34".toBigDecimal().stripTrailingZeros(), "14 Nov 2026", "")
        assertNull(model.savedHumanId.value)
        release.countDown()
        model.savedHumanId.await { it == 7 }
        verify(setDebt, times(1)).execute("12.34".toBigDecimal().stripTrailingZeros(), 7, null, "14 Nov 2026", null, null)
        verify(addSum, times(1)).execute(7, "12.34".toBigDecimal().stripTrailingZeros())
    }

    @Test fun `failed write permits retry without reporting success`() {
        doThrow(IllegalStateException("write failed")).doNothing()
            .whenever(setDebt).execute(any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull())
        val model = vm()
        model.saveDebt(7, -1, "Person", "12.34".toBigDecimal().stripTrailingZeros(), "14 Nov 2026", "")
        model.isSaving.await { it == false }
        assertTrue(model.saveError.value!!)
        assertNull(model.savedHumanId.value)
        verifyNoInteractions(addSum)
        model.saveDebt(7, -1, "Person", "12.34".toBigDecimal().stripTrailingZeros(), "14 Nov 2026", "")
        model.savedHumanId.await { it == 7 }
        verify(addSum, times(1)).execute(7, "12.34".toBigDecimal().stripTrailingZeros())
    }

    @Test fun `editing uses persisted sum and respects direction`() {
        whenever(getDebts.execute(7)).thenReturn(listOf(DebtDomain(9, "12.34".toBigDecimal().stripTrailingZeros(), 7, null, "old date")))
        val model = vm()
        model.saveDebt(7, 9, "Person", -"20.55".toBigDecimal().stripTrailingZeros(), "14 Nov 2026", "")
        model.savedHumanId.await { it == 7 }
        verify(editDebt).execute(9, -"20.55".toBigDecimal().stripTrailingZeros(), 7, null, "14 Nov 2026")
        verify(addSum).execute(7, -"20.55".toBigDecimal().stripTrailingZeros() - "12.34".toBigDecimal().stripTrailingZeros())
        verifyNoInteractions(setDebt, setHuman)
    }

    private fun <T> LiveData<T>.await(predicate: (T?) -> Boolean) {
        val latch = CountDownLatch(1)
        val observer = Observer<T> { if (predicate(it)) latch.countDown() }
        observeForever(observer)
        try { assertTrue("Unexpected state: $value", latch.await(5, TimeUnit.SECONDS)) }
        finally { removeObserver(observer) }
    }
}
