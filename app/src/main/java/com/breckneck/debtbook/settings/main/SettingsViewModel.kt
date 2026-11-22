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
import com.breckneck.deptbook.domain.usecase.Settings.GetIsFingerprintAuthEnabled
import com.breckneck.deptbook.domain.usecase.Settings.SetAddSumInShareText
import com.breckneck.deptbook.domain.usecase.Settings.SetAppTheme
import com.breckneck.deptbook.domain.usecase.Settings.SetDefaultCurrency
import com.breckneck.deptbook.domain.usecase.Settings.SetIsFingerprintAuthEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
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
        refreshDefaultCurrency()
        getAppTheme()
        getSumInShareText()
    }

    fun refreshDefaultCurrency() {
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
