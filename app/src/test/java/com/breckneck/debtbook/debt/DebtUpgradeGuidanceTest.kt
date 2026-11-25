package com.breckneck.debtbook.debt

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DebtUpgradeGuidanceTest {
    @Test fun `an upgrade records exactly the available topics on the first visit`() {
        for (otherCurrencies in listOf(false, true)) {
            for (earlyEntries in listOf(false, true)) {
                val context = context(updated = true)
                val guidance = DebtUpgradeGuidance(context)
                val topics = DebtUpgradeTopics(otherCurrencies, earlyEntries)
                assertNull(guidance.progress.value)

                guidance.recordFirstVisit(topics)
                guidance.recordFirstVisit(DebtUpgradeTopics(!otherCurrencies, !earlyEntries))

                val progress = requireNotNull(DebtUpgradeGuidance(context).progress.value)
                assertEquals(topics, progress.topics)
                assertEquals(otherCurrencies || earlyEntries, progress.showNotice)
                assertEquals(otherCurrencies, progress.showCurrencyHint)
                assertEquals(earlyEntries, progress.showEarlyEntriesHint)
            }
        }
    }

    @Test fun `a fresh install skips guidance even with eligible records`() {
        val context = context(updated = false)
        DebtUpgradeGuidance(context).recordFirstVisit(DebtUpgradeTopics(true, true))
        assertEquals(DebtUpgradeProgress(DebtUpgradeTopics()), DebtUpgradeGuidance(context).progress.value)
    }

    @Test fun `the notice and hints are acknowledged independently across restarts`() {
        val context = context(updated = true)
        DebtUpgradeGuidance(context).apply {
            recordFirstVisit(DebtUpgradeTopics(true, true))
            dismissNotice()
        }
        DebtUpgradeGuidance(context).apply {
            val restored = requireNotNull(progress.value)
            assertFalse(restored.showNotice)
            assertTrue(restored.showCurrencyHint)
            assertTrue(restored.showEarlyEntriesHint)
            completeEarlyEntriesHint()
        }
        DebtUpgradeGuidance(context).apply {
            val restored = requireNotNull(progress.value)
            assertFalse(restored.showNotice)
            assertTrue(restored.showCurrencyHint)
            assertFalse(restored.showEarlyEntriesHint)
            completeCurrencyHint()
        }
        val restored = requireNotNull(DebtUpgradeGuidance(context).progress.value)
        assertFalse(restored.showNotice)
        assertFalse(restored.showCurrencyHint)
        assertFalse(restored.showEarlyEntriesHint)
    }

    @Suppress("DEPRECATION")
    private fun context(updated: Boolean): Context {
        val values = mutableMapOf<String, Boolean>()
        val editor = mock<SharedPreferences.Editor>()
        whenever(editor.putBoolean(any(), any())).doAnswer {
            values[it.getArgument(0)] = it.getArgument(1)
            editor
        }
        val preferences = mock<SharedPreferences>()
        whenever(preferences.edit()).thenReturn(editor)
        whenever(preferences.contains(any())).doAnswer { it.getArgument<String>(0) in values }
        whenever(preferences.getBoolean(any(), any())).doAnswer {
            values.getOrDefault(it.getArgument(0), it.getArgument(1))
        }
        val packageManager = mock<PackageManager>()
        val packageName = "com.breckneck.debtbook"
        whenever(packageManager.getPackageInfo(packageName, 0)).thenReturn(PackageInfo().apply {
            firstInstallTime = 1L
            lastUpdateTime = if (updated) 2L else firstInstallTime
        })
        return mock<Context>().also {
            whenever(it.packageName).thenReturn(packageName)
            whenever(it.packageManager).thenReturn(packageManager)
            whenever(it.getSharedPreferences(any(), any())).thenReturn(preferences)
        }
    }
}
