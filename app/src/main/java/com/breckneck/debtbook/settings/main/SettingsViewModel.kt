package com.breckneck.debtbook.settings.main

import com.breckneck.deptbook.domain.usecase.Settings.GetPinLockDelay
import com.breckneck.deptbook.domain.usecase.Settings.SetPinLockDelay
import com.breckneck.deptbook.domain.util.PinLockDelay
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.breckneck.deptbook.domain.usecase.Settings.GetAddSumInShareText
import com.breckneck.deptbook.domain.usecase.Settings.GetAppTheme
import com.breckneck.deptbook.domain.usecase.Settings.GetDefaultCurrency
import com.breckneck.deptbook.domain.usecase.Settings.GetFirstMainCurrency
import com.breckneck.deptbook.domain.usecase.Settings.GetIsFingerprintAuthEnabled
import com.breckneck.deptbook.domain.usecase.Settings.GetSecondMainCurrency
import com.breckneck.deptbook.domain.usecase.Settings.SetAddSumInShareText
import com.breckneck.deptbook.domain.usecase.Settings.SetAppTheme
import com.breckneck.deptbook.domain.usecase.Settings.SetDefaultCurrency
import com.breckneck.deptbook.domain.usecase.Settings.SetFirstMainCurrency
import com.breckneck.deptbook.domain.usecase.Settings.SetIsFingerprintAuthEnabled
import com.breckneck.deptbook.domain.usecase.Settings.SetSecondMainCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val setFirstMainCurrency: SetFirstMainCurrency,
    private val getFirstMainCurrency: GetFirstMainCurrency,
    private val setSecondMainCurrency: SetSecondMainCurrency,
    private val getSecondMainCurrency: GetSecondMainCurrency,
    private val setDefaultCurrency: SetDefaultCurrency,
    private val getDefaultCurrency: GetDefaultCurrency,
    private val setAddSumInShareText: SetAddSumInShareText,
    private val getAddSumInShareText: GetAddSumInShareText,
    private val getAppTheme: GetAppTheme,
    private val setAppTheme: SetAppTheme,
    private val setIsFingerprintAuthEnabled: SetIsFingerprintAuthEnabled,
    private val getIsFingerprintAuthEnabled: GetIsFingerprintAuthEnabled,
    private val getPinLockDelay: GetPinLockDelay,
    private val setPinLockDelay: SetPinLockDelay
) : ViewModel() {

    private val _pinLockDelay = MutableLiveData(getPinLockDelay.execute())
    val pinLockDelay: LiveData<PinLockDelay> = _pinLockDelay

    fun setPinLockDelay(delay: PinLockDelay) {
        setPinLockDelay.execute(delay)
        _pinLockDelay.value = delay
    }


    private val _firstMainCurrency = MutableLiveData<String>()
    val firstMainCurrency: LiveData<String>
        get() = _firstMainCurrency
    private val _secondMainCurrency = MutableLiveData<String>()
    val secondMainCurrency: LiveData<String>
        get() = _secondMainCurrency
    private val _defaultCurrency = MutableLiveData<String>()
    val defaultCurrency: LiveData<String>
        get() = _defaultCurrency
    private val _addSumInShareText = MutableLiveData<Boolean>()
    val addSumInShareText: LiveData<Boolean>
        get() = _addSumInShareText
    private val _appTheme = MutableLiveData<String>()
    val appTheme: LiveData<String>
        get() = _appTheme
    private val _isFingerprintAuthEnabled = MutableLiveData<Boolean>(false)
    val isFingerprintAuthEnabled: LiveData<Boolean>
        get() = _isFingerprintAuthEnabled

    init {
        getFirstMainCurrency()
        getSecondMainCurrency()
        getDefaultCurrency()
        getAppTheme()
        getSumInShareText()
    }

    private fun getFirstMainCurrency() {
        _firstMainCurrency.value = getFirstMainCurrency.execute()
    }

    fun setFirstMainCurrency(currency: String) {
        setFirstMainCurrency.execute(currency = currency)
        _firstMainCurrency.value = currency
    }

    private fun getSecondMainCurrency() {
        _secondMainCurrency.value = getSecondMainCurrency.execute()
    }

    fun setSecondMainCurrency(currency: String) {
        setSecondMainCurrency.execute(currency = currency)
        _secondMainCurrency.value = currency
    }

    private fun getDefaultCurrency() {
        _defaultCurrency.value = getDefaultCurrency.execute()
    }

    fun setDefaultCurrency(currency: String) {
        setDefaultCurrency.execute(currency = currency)
        _defaultCurrency.value = currency
    }

    private fun getAppTheme() {
        _appTheme.value = getAppTheme.execute()
    }

    fun setAppTheme(theme: String) {
        setAppTheme.execute(theme = theme)
        _appTheme.value = theme
    }

    private fun getSumInShareText() {
        _addSumInShareText.value = getAddSumInShareText.execute()
    }

    fun setSumInShareText(value: Boolean) {
        setAddSumInShareText.execute(value)
        _addSumInShareText.value = value
    }

    fun getIsFingerprintAuthEnabled() {
        _isFingerprintAuthEnabled.value = getIsFingerprintAuthEnabled.execute()
    }

    fun setIsFingerprintAuthEnabled(isEnabled: Boolean) {
        _isFingerprintAuthEnabled.value = isEnabled
        setIsFingerprintAuthEnabled.execute(_isFingerprintAuthEnabled.value!!)
    }
}