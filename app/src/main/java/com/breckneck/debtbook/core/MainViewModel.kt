package com.breckneck.debtbook.core

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.debtbook.settings.rating.AppRatingState
import com.breckneck.deptbook.domain.usecase.Ad.SaveClicksUseCase
import com.breckneck.deptbook.domain.usecase.Ad.GetClicksUseCase
import com.breckneck.deptbook.domain.usecase.Debt.GetDebtQuantity
import com.breckneck.deptbook.domain.usecase.Settings.GetAppTheme
import com.breckneck.deptbook.domain.usecase.Settings.GetDebtQuantityForAppRateDialogShow
import com.breckneck.deptbook.domain.usecase.Settings.GetPINCodeEnabled
import com.breckneck.deptbook.domain.usecase.Settings.GetPinLockDelay
import com.breckneck.deptbook.domain.usecase.Settings.SetDebtQuantityForAppRateDialogShow
import com.breckneck.deptbook.domain.usecase.Settings.SetIsFingerprintAuthEnabled
import com.breckneck.deptbook.domain.usecase.Settings.SetPINCodeEnabled
import com.breckneck.deptbook.domain.usecase.Settings.GetAdsDisabled
import com.breckneck.deptbook.domain.usecase.Settings.SetAdsDisabled
import com.breckneck.deptbook.domain.util.DEBT_QUANTITY_FOR_LAST_SHOW_APP_RATE_DIALOG
import com.breckneck.deptbook.domain.util.DEBT_QUANTITY_FOR_NEXT_SHOW_APP_RATE_DIALOG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getDebtQuantity: GetDebtQuantity,
    private val getDebtQuantityForAppRateDialogShow: GetDebtQuantityForAppRateDialogShow,
    private val setDebtQuantityForAppRateDialogShow: SetDebtQuantityForAppRateDialogShow,
    private val getClicksUseCase: GetClicksUseCase,
    private val saveClicks: SaveClicksUseCase,
    private val getAppTheme: GetAppTheme,
    private val setPINCodeEnabled: SetPINCodeEnabled,
    private val getPINCodeEnabled: GetPINCodeEnabled,
    private val setIsFingerprintAuthEnabled: SetIsFingerprintAuthEnabled,
    private val getAdsDisabled: GetAdsDisabled,
    private val setAdsDisabled: SetAdsDisabled,
    private val getPinLockDelay: GetPinLockDelay
    ) : ViewModel() {

    private val TAG = "MainActivityViewModel"

    internal var appRating by mutableStateOf<AppRatingState?>(null)
        private set
    private val _debtQuantity = MutableLiveData<Int>(0)
    val debtQuantity: LiveData<Int>
        get() = _debtQuantity
    private var nextRatingPrompt = getDebtQuantityForAppRateDialogShow.execute()
    private val _adClicksCounter = MutableLiveData<Int>()
    val adClicksCounter: LiveData<Int>
        get() = _adClicksCounter
    private val _appTheme = MutableLiveData<String>()
    val appTheme: LiveData<String>
        get() = _appTheme
    private val _isNeedUpdateDebtData = MutableLiveData<Boolean>(false)
    val isNeedDebtDataUpdate: LiveData<Boolean>
        get() = _isNeedUpdateDebtData
    private val _isPINCodeEnabled = MutableLiveData<Boolean>(false)
    val isPINCodeEnabled: LiveData<Boolean>
        get() = _isPINCodeEnabled
    private val _adsDisabled = MutableLiveData<Boolean>(false)
    val adsDisabled: LiveData<Boolean>
        get() = _adsDisabled

    init {
        Log.e(TAG, "Main Activity View Model Started")
        getDebtQuantity()
        getAdClicksCounter()
        getAppTheme()
        loadAdsDisabled()
    }

    override fun onCleared() {
        super.onCleared()
        saveClicks.execute(adClicksCounter.value!!)
        Log.e(TAG, "Main Activity View Model cleared")
    }

    private fun getDebtQuantity() {
        viewModelScope.launch {
            try {
                val quantity = withContext(Dispatchers.IO) { getDebtQuantity.execute() }
                _debtQuantity.value = quantity
            } catch (e: Exception) {
                Log.e(TAG, e.message.toString())
            }
        }
    }

    internal fun showAppRating(fromSettings: Boolean) {
        if (appRating != null) return
        val automaticPromptDue = (debtQuantity.value ?: 0) >= nextRatingPrompt &&
            nextRatingPrompt <= DEBT_QUANTITY_FOR_LAST_SHOW_APP_RATE_DIALOG
        if (!fromSettings && !automaticPromptDue) return
        appRating = AppRatingState(fromSettings)
    }

    internal fun selectAppRating(rating: Int) {
        appRating = appRating?.copy(rating = rating)
    }

    internal fun showAppReview() {
        appRating = appRating?.copy(writingReview = true)
    }

    internal fun updateAppReview(text: String) {
        appRating = appRating?.copy(reviewText = text)
    }

    internal fun closeAppRating() {
        val rating = appRating ?: return
        if (!rating.fromSettings) {
            nextRatingPrompt += DEBT_QUANTITY_FOR_NEXT_SHOW_APP_RATE_DIALOG
            setDebtQuantityForAppRateDialogShow.execute(nextRatingPrompt)
        }
        appRating = null
    }

    fun onAddDebt() {
        _debtQuantity.value = _debtQuantity.value!! + 1
    }

    private fun getAdClicksCounter() {
        _adClicksCounter.value = getClicksUseCase.execute()
    }

    fun onActionClick() {
        _adClicksCounter.value = _adClicksCounter.value!! + 1
    }

    fun onAdShow() {
        _adClicksCounter.value = 0
    }

    fun getAppTheme() {
        _appTheme.value = getAppTheme.execute()
    }

    fun setIsNeedUpdateDebtData(isNeedUpdate: Boolean) {
        _isNeedUpdateDebtData.value = isNeedUpdate
    }

    fun setIsPINCodeEnabled(enabled: Boolean) {
        _isPINCodeEnabled.value = enabled
        setPINCodeEnabled.execute(_isPINCodeEnabled.value!!)
    }

    fun pinLockDelayMillis(): Long = getPinLockDelay.execute().millis

    fun getIsPINCodeEnabled() {
        _isPINCodeEnabled.value = getPINCodeEnabled.execute()
    }

    fun setIsFingerprintEnabled(isEnabled: Boolean) {
        setIsFingerprintAuthEnabled.execute(isEnabled = isEnabled)
    }

    private fun loadAdsDisabled() {
        _adsDisabled.value = getAdsDisabled.execute()
    }

    fun toggleAdsDisabled() {
        val newValue = !(_adsDisabled.value ?: false)
        _adsDisabled.value = newValue
        setAdsDisabled.execute(isDisabled = newValue)
    }
}
