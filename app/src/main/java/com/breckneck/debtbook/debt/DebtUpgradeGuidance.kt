package com.breckneck.debtbook.debt

import android.content.Context
import androidx.core.content.edit
import com.breckneck.debtbook.app.wasUpdated
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DebtUpgradeTopics(
    val otherCurrencies: Boolean = false,
    val earlyEntries: Boolean = false,
) {
    val isEmpty: Boolean get() = !otherCurrencies && !earlyEntries
}

internal data class DebtUpgradeProgress(
    val topics: DebtUpgradeTopics,
    val noticeSeen: Boolean = false,
    val currencyHintSeen: Boolean = false,
    val earlyEntriesHintSeen: Boolean = false,
) {
    val showNotice: Boolean get() = !noticeSeen && !topics.isEmpty
    val showCurrencyHint: Boolean get() = topics.otherCurrencies && !currencyHintSeen
    val showEarlyEntriesHint: Boolean get() = topics.earlyEntries && !earlyEntriesHintSeen
}

/** Stores the result of the first successful visit, including a visit with no applicable topics. */
@Singleton
class DebtUpgradeGuidance @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("debt_upgrade_guidance", Context.MODE_PRIVATE)
    private val _progress = MutableStateFlow(readProgress())
    internal val progress = _progress.asStateFlow()

    private fun readProgress(): DebtUpgradeProgress? {
        if (!prefs.contains("other_currencies")) return null
        return DebtUpgradeProgress(
            topics = DebtUpgradeTopics(
                otherCurrencies = prefs.getBoolean("other_currencies", false),
                earlyEntries = prefs.getBoolean("early_entries", false),
            ),
            noticeSeen = prefs.getBoolean("notice_seen", false),
            currencyHintSeen = prefs.getBoolean("currency_hint_seen", false),
            earlyEntriesHintSeen = prefs.getBoolean("early_entries_hint_seen", false),
        )
    }

    internal fun recordFirstVisit(available: DebtUpgradeTopics) {
        if (_progress.value != null) return
        val topics = if (context.wasUpdated()) available else DebtUpgradeTopics()
        prefs.edit {
            putBoolean("other_currencies", topics.otherCurrencies)
            putBoolean("early_entries", topics.earlyEntries)
        }
        _progress.value = DebtUpgradeProgress(topics)
    }

    internal fun dismissNotice() {
        val current = _progress.value?.takeIf { it.showNotice } ?: return
        prefs.edit { putBoolean("notice_seen", true) }
        _progress.value = current.copy(noticeSeen = true)
    }

    internal fun completeCurrencyHint() {
        val current = _progress.value?.takeIf { it.showCurrencyHint } ?: return
        prefs.edit { putBoolean("currency_hint_seen", true) }
        _progress.value = current.copy(currencyHintSeen = true)
    }

    internal fun completeEarlyEntriesHint() {
        val current = _progress.value?.takeIf { it.showEarlyEntriesHint } ?: return
        prefs.edit { putBoolean("early_entries_hint_seen", true) }
        _progress.value = current.copy(earlyEntriesHintSeen = true)
    }
}
