package com.breckneck.debtbook.debt.details

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import com.breckneck.debtbook.debt.DebtUpgradeGuidance
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.usecase.Debt.DeleteDebtUseCase
import com.breckneck.deptbook.domain.usecase.Debt.DeleteDebtsByHumanIdUseCase
import com.breckneck.deptbook.domain.usecase.Debt.GetAllDebtsByIdUseCase
import com.breckneck.deptbook.domain.usecase.Debt.RecordRepaymentUseCase
import com.breckneck.deptbook.domain.usecase.Human.AddSumUseCase
import com.breckneck.deptbook.domain.usecase.Human.DeleteHumanUseCase
import com.breckneck.deptbook.domain.usecase.Human.GetLastHumanIdUseCase
import com.breckneck.deptbook.domain.usecase.Settings.GetDebtOrder
import com.breckneck.deptbook.domain.usecase.Settings.SetDebtOrder
import com.breckneck.deptbook.domain.util.DebtOrderAttribute
import com.breckneck.deptbook.domain.util.Filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class DebtDetailsViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val debts: GetAllDebtsByIdUseCase = mock()
    private val latestId: GetLastHumanIdUseCase = mock()
    private val people: com.breckneck.deptbook.domain.repository.HumanRepository = mock()
    private val deleteHuman: DeleteHumanUseCase = mock()
    private val deleteHumanDebts: DeleteDebtsByHumanIdUseCase = mock()
    private val deleteDebt: DeleteDebtUseCase = mock()
    private val addSum: AddSumUseCase = mock()
    private val getOrder: GetDebtOrder = mock()
    private val setOrder: SetDebtOrder = mock()
    private val recordRepayment: RecordRepaymentUseCase = mock()
    private val upgradeGuidance: DebtUpgradeGuidance = mock()
    private val record = DebtDomain(9, "12.34".toBigDecimal().stripTrailingZeros(), 7, null, "1 Jan 2026", recordedAsLoan = true)

    private val person = com.breckneck.deptbook.domain.model.HumanDomain(7, "Person", record.sum, "EUR", BigDecimal.ZERO)

    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        whenever(getOrder.execute()).thenReturn(DebtOrderAttribute.CreationDate to false)
        whenever(debts.execute(any())).thenReturn(listOf(record))
        whenever(people.getHuman(7)).thenReturn(person)
        whenever(upgradeGuidance.progress).thenReturn(MutableStateFlow(null))
    }
    @After fun teardown() = Dispatchers.resetMain()

    private fun vm(state: SavedStateHandle = SavedStateHandle(mapOf("idHuman" to 7))) =
        DebtDetailsViewModel(debts, latestId, people, deleteHuman, deleteHumanDebts, deleteDebt,
            addSum, getOrder, setOrder, object : DataTransaction {
                override fun <T> run(block: () -> T): T = block()
            }, state, mock(), recordRepayment, upgradeGuidance)

    @Test fun `repayment validates positive amount and refuses more than selected record`() {
        val model = vm(SavedStateHandle(mapOf("idHuman" to 7, "currency" to "EUR")))
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        model.openRepayment(record)
        for (text in listOf("0", "-1", "abc", "0.001")) {
            model.changeRepaymentAmount(text)
            model.saveRepayment()
            assertEquals(RepaymentError.INVALID_AMOUNT, model.repaymentError.value)
        }
        model.changeRepaymentAmount("13")
        model.saveRepayment()
        assertEquals(RepaymentError.EXCEEDS_REMAINING, model.repaymentError.value)
        verifyNoInteractions(recordRepayment)
    }

    @Test fun `failed repayment retains request and amount across saved state reconstruction`() {
        val saved = SavedStateHandle(mapOf("idHuman" to 7, "currency" to "EUR"))
        doThrow(IllegalStateException("disk write failure")).whenever(recordRepayment).execute(any(), any(), any(), any(), any())
        val model = vm(saved)
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        model.openRepayment(record)
        model.changeRepaymentAmount("2,34")
        val request = saved.get<String>("repaymentRequestId")!!
        model.saveRepayment()
        model.repaymentError.await { it == RepaymentError.SAVE_FAILED }
        model.isDeleting.await { it == false }
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val recreated = vm(restored)
        recreated.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(request, restored.get<String>("repaymentRequestId"))
        assertEquals("2,34", recreated.repaymentAmount.value)
        recreated.saveRepayment()
        recreated.repaymentError.await { it == RepaymentError.SAVE_FAILED }
        verify(recordRepayment, times(2)).execute(eq(9), eq(7), eq("2.34".toBigDecimal()), any(), eq(request))
    }

    @Test fun `committed request recovered from saved state closes form without another payment`() {
        val saved = SavedStateHandle(mapOf("idHuman" to 7, "currency" to "EUR", "repaymentDebtId" to 9,
            "repaymentRequestId" to "request-000000001", "repaymentAmount" to "2.34", "repaymentDay" to "2026-11-16"))
        val payment = record.copy(id = 10, sum = "-2.34".toBigDecimal(), repaymentOfId = 9, recordedAsLoan = false, operationId = "request-000000001")
        whenever(debts.execute(7)).thenReturn(listOf(record, payment))
        val model = vm(saved)
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        assertNull(model.repaymentDebtId.value)
        model.saveRepayment()
        verifyNoInteractions(recordRepayment)
        model.onSetDebtSort(Filter.POSITIVE, DebtOrderAttribute.CreationDate to true)
        assertEquals(setOf(record, payment), model.resultedDebtList.value?.toSet())
    }

    @Test fun `repeated repayment tap submits one request while write is pending`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); false }
            .whenever(recordRepayment).execute(any(), any(), any(), any(), any())
        val model = vm(SavedStateHandle(mapOf("idHuman" to 7, "currency" to "EUR")))
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        model.openRepayment(record)
        model.changeRepaymentAmount("2.34")
        model.saveRepayment()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        model.saveRepayment()
        release.countDown()
        model.repaymentDebtId.await { it == null }
        verify(recordRepayment, times(1)).execute(any(), any(), any(), any(), any())
    }

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
        verifyNoInteractions(debts, people)
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

    @Test fun `refresh keeps cached rows until the new balance and records arrive`() {
        val model = vm()
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        model.onResume()
        verify(debts).execute(7)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val added = record.copy(id = 10, sum = "5".toBigDecimal())
        whenever(people.getHuman(7)).thenReturn(person.copy(sumDebt = record.sum + added.sum))
        doAnswer {
            started.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
            listOf(record, added)
        }.whenever(debts).execute(7)
        model.onResume()
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertEquals(DebtDetailsState.SUCCESS, model.screenState.value)
            assertEquals(listOf(record), model.visibleDebtRows.value)
            assertEquals(record.sum, model.overallSum.value)
        } finally {
            release.countDown()
        }
        model.visibleDebtRows.await { it == listOf(record, added) }
        assertEquals(record.sum + added.sum, model.overallSum.value)
    }

    @Test fun `new debt opens the matching history after a successful reload`() {
        val model = vm()
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        model.selectDebtSection(DebtSection.SETTLED)
        model.onSetDebtSort(Filter.NEGATIVE, DebtOrderAttribute.CreationDate to false)
        model.showAddedDebt(positive = true)
        assertEquals(DebtSection.SETTLED, model.section.value)
        assertEquals(Filter.NEGATIVE, model.debtFilter.value)
        model.retry()
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(DebtSection.OPEN, model.section.value)
        assertEquals(Filter.ALL, model.debtFilter.value)
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

    @Test fun `archive selection survives recreation and groups repayments without narrowing exports`() {
        val other = record.copy(id = 10, sum = record.sum.negate())
        val first = record.copy(id = 11, sum = "-2.34".toBigDecimal(), repaymentOfId = 9, recordedAsLoan = false, date = "2026-11-10")
        val last = record.copy(id = 12, sum = "-10".toBigDecimal(), repaymentOfId = 9, recordedAsLoan = false, date = "2026-11-20")
        val history = listOf(record, other, last, first)
        whenever(debts.execute(7)).thenReturn(history)
        val saved = SavedStateHandle(mapOf("idHuman" to 7))
        val model = vm(saved)
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(listOf(other), model.visibleDebtRows.value)
        model.selectDebtSection(DebtSection.SETTLED)
        assertEquals(listOf(record, first, last), model.visibleDebtRows.value)
        assertEquals(history.toSet(), model.resultedDebtList.value!!.toSet())

        val recreated = vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        recreated.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(DebtSection.SETTLED, recreated.section.value)
        assertEquals(listOf(record, first, last), recreated.visibleDebtRows.value)

        val undone = last.copy(cancelled = true)
        whenever(debts.execute(7)).thenReturn(listOf(record, other, first, undone))
        recreated.retry()
        recreated.screenState.await { it == DebtDetailsState.EMPTY }
        assertTrue(recreated.visibleDebtRows.value!!.isEmpty())
        recreated.selectDebtSection(DebtSection.OPEN)
        assertEquals(listOf(record, first, undone, other), recreated.visibleDebtRows.value)
        assertEquals(0, recreated.remaining(record).compareTo("10".toBigDecimal()))
    }

    @Test fun `early history opens by default and keeps its balance when switching sections`() {
        val old = record.copy(recordedAsLoan = false, info = "Original note")
        whenever(debts.execute(7)).thenReturn(listOf(old))
        whenever(people.getHuman(7)).thenReturn(person.copy(sumDebt = "15.34".toBigDecimal(), openingAdjustment = 3.toBigDecimal()))
        val model = vm()
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(DebtSection.EARLY, model.section.value)
        assertEquals(listOf(old), model.visibleDebtRows.value)
        assertEquals(0, model.openingAdjustment.value!!.compareTo("3".toBigDecimal()))
        for (section in listOf(DebtSection.OPEN, DebtSection.SETTLED)) {
            model.selectDebtSection(section)
            assertEquals(DebtDetailsState.EMPTY, model.screenState.value)
            assertEquals("15.34".toBigDecimal(), model.overallSum.value)
            assertEquals(listOf(old), model.resultedDebtList.value)
        }
    }

    @Test fun `mixed history keeps an early entry separate from the loan and its payment`() {
        val early = record.copy(id = 8, sum = "-1".toBigDecimal(), recordedAsLoan = false)
        val payment = record.copy(id = 10, sum = "-2.34".toBigDecimal(), repaymentOfId = record.id, recordedAsLoan = false)
        val rows = listOf(early, record, payment)
        whenever(debts.execute(7)).thenReturn(rows)
        whenever(people.getHuman(7)).thenReturn(person.copy(sumDebt = 9.toBigDecimal()))
        val state = SavedStateHandle(mapOf("idHuman" to 7))
        val model = vm(state)
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(DebtSection.OPEN, model.section.value)
        assertEquals(listOf(record, payment), model.visibleDebtRows.value)
        assertEquals(0, model.remaining(record).compareTo("10".toBigDecimal()))
        model.selectDebtSection(DebtSection.EARLY)
        assertEquals(listOf(early), model.visibleDebtRows.value)
        val recreated = vm(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))
        recreated.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(listOf(early), recreated.visibleDebtRows.value)
        assertEquals(rows, recreated.resultedDebtList.value)
    }

    @Test fun `automatic early history preserves the hint when there are also settled loans`() {
        val early = record.copy(id = 8, info = "Earlier entry", recordedAsLoan = false)
        val paid = record.copy(id = 10, sum = -record.sum, repaymentOfId = record.id, recordedAsLoan = false)
        whenever(debts.execute(7)).thenReturn(listOf(early, record, paid))
        val model = vm()
        model.screenState.await { it == DebtDetailsState.SUCCESS }
        assertEquals(DebtSection.EARLY, model.section.value)
        assertEquals(listOf(early), model.visibleDebtRows.value)
        assertEquals(record, model.loans.value!!.settledLoans.single().original)

        model.onDebtSectionClick(DebtSection.EARLY)
        model.onDebtSectionClick(DebtSection.SETTLED)
        verify(upgradeGuidance, never()).completeEarlyEntriesHint()
        model.onDebtSectionClick(DebtSection.EARLY)
        verify(upgradeGuidance).completeEarlyEntriesHint()
        assertEquals(listOf(early), model.visibleDebtRows.value)
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
