package sharedprefs

import android.content.Context
import com.breckneck.deptbook.data.storage.SettingsStorage
import com.breckneck.deptbook.domain.util.DEBT_QUANTITY_FOR_NEXT_SHOW_APP_RATE_DIALOG
import entity.UserData
import util.DeviceCurrency
import util.ORDER_DEBT_BY_DATE
import util.ORDER_HUMAN_BY_DATE

private const val SHARED_PREFS_SETTINGS = "shared_prefs_settings"
private const val DEFAULT_CURRENCY = "default_currency"
private const val APP_RATE = "app_rate"
private const val APP_THEME = "app_theme"
private const val DEBTS_QUANTITY_FOR_APP_RATE_DIALOG_SHOW = "debts_quantity_for_app_rate_dialog_show"
private const val DEBT_ORDER_ATTRIBUTE = "debt_order_attribute"
private const val DEBT_ORDER_BY_INCREASE = "debt_order_by_increase"
private const val HUMAN_ORDER_ATTRIBUTE = "human_order_attribute"
private const val HUMAN_ORDER_BY_INCREASE = "human_order_by_increase"
private const val IS_AUTHORIZED = "is_authorized"
private const val USER_NAME = "user_name"
private const val USER_EMAIL = "user_email"
private const val LAST_SYNC_DATE = "last_sync_date"
private const val FINANCE_CURRENCY = "finance_currency"
private const val PIN_CODE_IS_ENABLED = "pin_code_is_enabled"
private const val PIN_CODE = "pin_code"
private const val PIN_LOCK_DELAY = "pin_lock_delay_millis"
private const val FINGERPRINT_AUTH_IS_ENABLED = "fingerprint_auth_is_enabled"
private const val ADS_DISABLED = "ads_disabled"


class SharedPrefsSettingsStorageImpl(val context: Context): SettingsStorage {

    val sharedPreferences = context.getSharedPreferences(SHARED_PREFS_SETTINGS, Context.MODE_PRIVATE)

    override fun setUserData(userData: UserData) {
        sharedPreferences.edit().putString(USER_NAME, userData.name).apply()
        sharedPreferences.edit().putString(USER_EMAIL, userData.email).apply()
    }

    override fun getUserData(): UserData {
        return UserData(name = sharedPreferences.getString(USER_NAME, "")!!, email = sharedPreferences.getString(
            USER_EMAIL, "")!!)
    }

    override fun setDefaultCurrency(currency: String) {
        sharedPreferences.edit().putString(DEFAULT_CURRENCY, currency).apply()
    }

    override fun getDefaultCurrency(): String {
        return sharedPreferences.getString(DEFAULT_CURRENCY, null) ?: DeviceCurrency.get(context)
    }

    override fun setAppIsRated(isRated: Boolean) {
        sharedPreferences.edit().putBoolean(APP_RATE, isRated).apply()
    }

    override fun getAppIsRated(): Boolean {
        return sharedPreferences.getBoolean(APP_RATE, false)
    }

    override fun setAppTheme(theme: String) {
        sharedPreferences.edit().putString(APP_THEME, theme).apply()
    }

    override fun getAppTheme(): String {
        return sharedPreferences.getString(APP_THEME, "")!!
    }

    override fun setDebtQuantityForAppRateDialogShow(quantity: Int) {
        sharedPreferences.edit().putInt(DEBTS_QUANTITY_FOR_APP_RATE_DIALOG_SHOW, quantity).apply()
    }

    override fun getDebtQuantityForAppRateDialogShow(): Int {
        return sharedPreferences.getInt(DEBTS_QUANTITY_FOR_APP_RATE_DIALOG_SHOW, DEBT_QUANTITY_FOR_NEXT_SHOW_APP_RATE_DIALOG)
    }

    override fun setDebtOrder(order: Pair<Int, Boolean>) {
        sharedPreferences.edit()
            .putInt(DEBT_ORDER_ATTRIBUTE, order.first)
            .putBoolean(DEBT_ORDER_BY_INCREASE, order.second)
            .apply()
    }

    override fun getDebtOrder(): Pair<Int, Boolean> {
        return Pair(
            sharedPreferences.getInt(DEBT_ORDER_ATTRIBUTE, ORDER_DEBT_BY_DATE),
            sharedPreferences.getBoolean(DEBT_ORDER_BY_INCREASE, true)
        )
    }

    override fun setHumanOrder(order: Pair<Int, Boolean>) {
        sharedPreferences.edit()
            .putInt(HUMAN_ORDER_ATTRIBUTE, order.first)
            .putBoolean(HUMAN_ORDER_BY_INCREASE, order.second)
            .apply()
    }

    override fun getHumanOrder(): Pair<Int, Boolean> {
        return Pair(
            sharedPreferences.getInt(HUMAN_ORDER_ATTRIBUTE, ORDER_HUMAN_BY_DATE),
            sharedPreferences.getBoolean(HUMAN_ORDER_BY_INCREASE, true)
        )
    }

    override fun setIsAuthorized(isAuthorized: Boolean) {
        sharedPreferences.edit().putBoolean(IS_AUTHORIZED, isAuthorized).apply()
    }

    override fun getIsAuthorized(): Boolean {
        return sharedPreferences.getBoolean(IS_AUTHORIZED, false)
    }

    override fun setLastSyncDate(lastSyncDateInMillis: Long) {
        sharedPreferences.edit().putLong(LAST_SYNC_DATE, lastSyncDateInMillis).apply()
    }

    override fun getLastSyncDate(): Long {
        return sharedPreferences.getLong(LAST_SYNC_DATE, 0)
    }

    override fun setFinanceCurrency(currency: String) {
        sharedPreferences.edit().putString(FINANCE_CURRENCY, currency).apply()
    }

    override fun getFinanceCurrency(): String? {
        return sharedPreferences.getString(FINANCE_CURRENCY, null)
    }

    override fun setPINCodeEnabled(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(PIN_CODE_IS_ENABLED, isEnabled).apply()
    }

    override fun getPINCodeEnabled(): Boolean {
        return sharedPreferences.getBoolean(PIN_CODE_IS_ENABLED, false)
    }

    override fun getPinLockDelayMillis(): Long = sharedPreferences.getLong(PIN_LOCK_DELAY, 0L)

    override fun setPinLockDelayMillis(delayMillis: Long) {
        sharedPreferences.edit().putLong(PIN_LOCK_DELAY, delayMillis).apply()
    }

    override fun setPINCode(PINCode: String) {
        sharedPreferences.edit().putString(PIN_CODE, PINCode).apply()
    }

    override fun getPINCode(): String {
        return sharedPreferences.getString(PIN_CODE,  "")!!
    }

    override fun setIsFingerprintAuthEnabled(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(FINGERPRINT_AUTH_IS_ENABLED, isEnabled).apply()
    }

    override fun getIsFingerprintAuthEnabled(): Boolean {
        return sharedPreferences.getBoolean(FINGERPRINT_AUTH_IS_ENABLED, false)
    }

    override fun setAdsDisabled(isDisabled: Boolean) {
        sharedPreferences.edit().putBoolean(ADS_DISABLED, isDisabled).apply()
    }

    override fun getAdsDisabled(): Boolean {
        return sharedPreferences.getBoolean(ADS_DISABLED, false)
    }
}
