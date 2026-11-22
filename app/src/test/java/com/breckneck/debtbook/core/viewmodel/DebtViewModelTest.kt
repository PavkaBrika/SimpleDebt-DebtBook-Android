package com.breckneck.debtbook.core.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.util.Filter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.breckneck.debtbook.debt.main.DebtViewModel
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.usecase.Debt.GetAllDebts
import com.breckneck.deptbook.domain.usecase.Human.GetAllHumansUseCase
import com.breckneck.deptbook.domain.usecase.Human.UpdateHuman
import com.breckneck.deptbook.domain.usecase.Settings.GetFirstMainCurrency
import com.breckneck.deptbook.domain.usecase.Settings.GetHumanOrder
import com.breckneck.deptbook.domain.usecase.Settings.SetHumanOrder
import com.breckneck.deptbook.domain.util.HumanOrderAttribute
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
import org.mockito.Mockito
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DebtViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val getAllDebts = Mockito.mock(GetAllDebts::class.java)
    private val getAllHumansUseCase = Mockito.mock(GetAllHumansUseCase::class.java)
    private val getFirstMainCurrency = Mockito.mock(GetFirstMainCurrency::class.java)
    private val getSecondMainCurrency = Mockito.mock(com.breckneck.deptbook.domain.usecase.Settings.GetSecondMainCurrency::class.java)
    private val updateHuman = Mockito.mock(UpdateHuman::class.java)
    private val setHumanOrder = Mockito.mock(SetHumanOrder::class.java)
    private val getHumanOrder = Mockito.mock(GetHumanOrder::class.java)

    private lateinit var viewModel: DebtViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(getHumanOrder.execute()).thenReturn(Pair(HumanOrderAttribute.Sum, true))
        whenever(getSecondMainCurrency.execute()).thenReturn("EUR")
        whenever(getFirstMainCurrency.execute()).thenReturn("$")
        whenever(getAllHumansUseCase.execute()).thenReturn(emptyList())
        whenever(getAllDebts.execute()).thenReturn(emptyList())
        viewModel = DebtViewModel(
            saved = androidx.lifecycle.SavedStateHandle(),
            getAllDebts = getAllDebts,
            transaction = object : DataTransaction {
                override fun <T> run(block: () -> T): T = block()
            },
            getAllHumansUseCase = getAllHumansUseCase,
            getFirstMainCurrency = getFirstMainCurrency,
            getSecondMainCurrency = getSecondMainCurrency,
            getHumanOrder = getHumanOrder,
            setHumanOrder = setHumanOrder,
            updateHuman = updateHuman
        )
    }

    @After
    fun tearDown() {
        Mockito.reset(
            getAllHumansUseCase,
            getFirstMainCurrency,
            updateHuman,
            setHumanOrder,
            getHumanOrder,
        )
        Dispatchers.resetMain()
    }

    @Test
    fun `people filters use the balance and open loans of each person with the same name`() {
        val legacy = HumanDomain(1, "Anna", 1000.toBigDecimal(), "RUB", openingAdjustment = 0.toBigDecimal())
        val opposite = legacy.copy(id = 2, sumDebt = 0.toBigDecimal())
        val adjustmentOnly = legacy.copy(id = 3, openingAdjustment = legacy.sumDebt)
        val mixed = legacy.copy(id = 4, sumDebt = (-190).toBigDecimal(), openingAdjustment = 10.toBigDecimal())
        val entry = DebtDomain(1, legacy.sumDebt, legacy.id, null, "2025-11-01")
        val lent = entry.copy(id = 2, idHuman = opposite.id, recordedAsLoan = true)
        val borrowed = lent.copy(id = 3, sum = -lent.sum)
        whenever(getAllHumansUseCase.execute()).thenReturn(listOf(legacy, opposite, adjustmentOnly, mixed))
        whenever(getAllDebts.execute()).thenReturn(listOf(entry, lent, borrowed,
            entry.copy(id = 4, idHuman = mixed.id, sum = (-200).toBigDecimal())))
        val loaded = CountDownLatch(1)
        val observer = Observer<List<HumanDomain>> { if (it.size == 4) loaded.countDown() }
        viewModel.resultedHumanList.observeForever(observer)
        try {
            viewModel.selectCurrency("RUB")
            viewModel.setListStateLoading()
            assertTrue(loaded.await(5, TimeUnit.SECONDS))
            viewModel.onSetHumanSort(Filter.POSITIVE, HumanOrderAttribute.Sum to true)
            assertEquals(setOf(legacy, adjustmentOnly, opposite), viewModel.resultedHumanList.value!!.toSet())
            viewModel.onSetHumanSort(Filter.NEGATIVE, HumanOrderAttribute.Sum to true)
            assertEquals(setOf(mixed, opposite), viewModel.resultedHumanList.value!!.toSet())
            viewModel.onSetHumanSort(Filter.ALL, HumanOrderAttribute.Sum to true)
            assertEquals(setOf(legacy, opposite, adjustmentOnly, mixed), viewModel.resultedHumanList.value!!.toSet())
        } finally {
            viewModel.resultedHumanList.removeObserver(observer)
        }
    }

    @Test
    fun `getHumanOrder should update humanOrder LiveData from use case`() {
        val expectedOrder = Pair(HumanOrderAttribute.Sum, true)
        Mockito.`when`(getHumanOrder.execute()).thenReturn(expectedOrder)

        viewModel.getHumanOrder()

        assertEquals(expectedOrder, viewModel.humanOrder.value)
    }

    @Test
    fun `getHumanOrder should update humanOrder with Date attribute`() {
        val expectedOrder = Pair(HumanOrderAttribute.Date, false)
        Mockito.`when`(getHumanOrder.execute()).thenReturn(expectedOrder)

        viewModel.getHumanOrder()

        assertEquals(expectedOrder, viewModel.humanOrder.value)
    }

    @Test
    fun `saveHumanOrder should delegate to setHumanOrder use case`() {
        val order = Pair(HumanOrderAttribute.Sum, false)
        viewModel.saveHumanOrder(order = order)
        Mockito.verify(setHumanOrder).execute(order = order)
    }

    @Test
    fun `setIsSearching should update isSearching LiveData`() {
        viewModel.setIsSearching(true)
        assertEquals(true, viewModel.isSearching.value)

        viewModel.setIsSearching(false)
        assertEquals(false, viewModel.isSearching.value)
    }

    @Test
    fun `onHumanSortDialogOpen should set isSortDialogOpened to true`() {
        viewModel.onHumanSortDialogOpen()
        assertEquals(true, viewModel.isSortDialogOpened.value)
    }

    @Test
    fun `onHumanSortDialogClose should set isSortDialogOpened to false`() {
        viewModel.onHumanSortDialogOpen()
        viewModel.onHumanSortDialogClose()
        assertEquals(false, viewModel.isSortDialogOpened.value)
    }
}
