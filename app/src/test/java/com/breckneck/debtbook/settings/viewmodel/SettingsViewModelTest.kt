package com.breckneck.debtbook.settings.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.breckneck.debtbook.settings.main.SettingsViewModel
import com.breckneck.deptbook.domain.usecase.Settings.GetAddSumInShareText
import com.breckneck.deptbook.domain.usecase.Settings.GetAppTheme
import com.breckneck.deptbook.domain.usecase.Settings.GetDefaultCurrency
import com.breckneck.deptbook.domain.usecase.Settings.GetIsFingerprintAuthEnabled
import com.breckneck.deptbook.domain.usecase.Settings.SetAddSumInShareText
import com.breckneck.deptbook.domain.usecase.Settings.SetAppTheme
import com.breckneck.deptbook.domain.usecase.Settings.SetDefaultCurrency
import com.breckneck.deptbook.domain.usecase.Settings.SetIsFingerprintAuthEnabled
import com.breckneck.deptbook.domain.usecase.Settings.GetPinLockDelay
import com.breckneck.deptbook.domain.usecase.Settings.SetPinLockDelay
import com.breckneck.deptbook.domain.util.PinLockDelay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class SettingsViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val setDefaultCurrency = Mockito.mock(SetDefaultCurrency::class.java)
    private val getDefaultCurrency = Mockito.mock(GetDefaultCurrency::class.java)
    private val setAddSumInShareText = Mockito.mock(SetAddSumInShareText::class.java)
    private val getAddSumInShareText = Mockito.mock(GetAddSumInShareText::class.java)
    private val getAppTheme = Mockito.mock(GetAppTheme::class.java)
    private val setAppTheme = Mockito.mock(SetAppTheme::class.java)
    private val setIsFingerprintAuthEnabled = Mockito.mock(SetIsFingerprintAuthEnabled::class.java)
    private val getIsFingerprintAuthEnabled = Mockito.mock(GetIsFingerprintAuthEnabled::class.java)

    private val getPinLockDelay = Mockito.mock(GetPinLockDelay::class.java)
    private val setPinLockDelay = Mockito.mock(SetPinLockDelay::class.java)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Mockito.`when`(getPinLockDelay.execute()).thenReturn(PinLockDelay.IMMEDIATE)
        Mockito.`when`(getDefaultCurrency.execute()).thenReturn("USD")
        Mockito.`when`(getAppTheme.execute()).thenReturn("light")
        Mockito.`when`(getAddSumInShareText.execute()).thenReturn(false)

        viewModel = SettingsViewModel(
            setDefaultCurrency = setDefaultCurrency,
            getDefaultCurrency = getDefaultCurrency,
            setAddSumInShareText = setAddSumInShareText,
            getAddSumInShareText = getAddSumInShareText,
            getAppTheme = getAppTheme,
            setAppTheme = setAppTheme,
            setIsFingerprintAuthEnabled = setIsFingerprintAuthEnabled,
            getIsFingerprintAuthEnabled = getIsFingerprintAuthEnabled,
            getPinLockDelay = getPinLockDelay,
            setPinLockDelay = setPinLockDelay
        )
    }

    @After
    fun tearDown() {
        Mockito.reset(
            setDefaultCurrency, getDefaultCurrency,
            setAddSumInShareText, getAddSumInShareText,
            getAppTheme, setAppTheme,
            setIsFingerprintAuthEnabled, getIsFingerprintAuthEnabled
        )
    }

    @Test
    fun `init should load defaultCurrency from use case`() {
        assertEquals("USD", viewModel.defaultCurrency.value)
    }

    @Test
    fun `init should load appTheme from use case`() {
        assertEquals("light", viewModel.appTheme.value)
    }

    @Test
    fun `init should load addSumInShareText from use case`() {
        assertFalse(viewModel.addSumInShareText.value!!)
    }

    @Test
    fun `setDefaultCurrency should update LiveData and call use case`() {
        viewModel.setDefaultCurrency("JPY")
        assertEquals("JPY", viewModel.defaultCurrency.value)
        Mockito.verify(setDefaultCurrency).execute(currency = "JPY")
    }

    @Test
    fun `setAppTheme should update LiveData and call use case`() {
        viewModel.setAppTheme("dark")
        assertEquals("dark", viewModel.appTheme.value)
        Mockito.verify(setAppTheme).execute(theme = "dark")
    }

    @Test
    fun `setSumInShareText true should update LiveData and call use case`() {
        viewModel.setSumInShareText(true)
        assertTrue(viewModel.addSumInShareText.value!!)
        Mockito.verify(setAddSumInShareText).execute(true)
    }

    @Test
    fun `setSumInShareText false should update LiveData and call use case`() {
        viewModel.setSumInShareText(false)
        assertFalse(viewModel.addSumInShareText.value!!)
        Mockito.verify(setAddSumInShareText).execute(false)
    }

    @Test
    fun `getIsFingerprintAuthEnabled should update LiveData`() {
        Mockito.`when`(getIsFingerprintAuthEnabled.execute()).thenReturn(true)
        viewModel.getIsFingerprintAuthEnabled()
        assertEquals(true, viewModel.isFingerprintAuthEnabled.value)
    }

    @Test
    fun `setIsFingerprintAuthEnabled should update LiveData and call use case`() {
        viewModel.setIsFingerprintAuthEnabled(true)
        assertEquals(true, viewModel.isFingerprintAuthEnabled.value)
        Mockito.verify(setIsFingerprintAuthEnabled).execute(true)
    }
    @Test fun `lock delay selection is saved and displayed`() {
        viewModel.setPinLockDelay(PinLockDelay.MINUTE_1)
        Mockito.verify(setPinLockDelay).execute(PinLockDelay.MINUTE_1)
        assertEquals(PinLockDelay.MINUTE_1, viewModel.pinLockDelay.value)
    }
}
