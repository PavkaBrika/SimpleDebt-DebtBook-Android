package com.breckneck.debtbook.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtUsageAnalyticsTest {
    private val preferences = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("test_debt_usage_analytics", Context.MODE_PRIVATE)
    private val events = mutableListOf<String>()
    private var day = "2026-11-26"

    @After fun clearState() {
        preferences.edit().clear().commit()
    }

    private fun tracker(enabled: Boolean = true, report: (String) -> Unit = { events.add(it) }) =
        DebtUsageAnalytics(preferences, enabled, report) { day }

    @Test fun newInstallCountsOnlyAfterFirstCreationAndOnLaterDay() {
        val original = tracker()
        original.initialize(wasUpdated = false)
        original.existingDebtActionSaved()
        assertEquals(emptyList<String>(), events)
        original.debtCreated()
        original.debtCreated()
        original.existingDebtActionSaved()
        assertEquals(listOf("first_debt_saved"), events)

        val upgraded = tracker()
        upgraded.initialize(wasUpdated = true)
        day = "2026-11-25"
        upgraded.debtCreated()
        assertEquals(listOf("first_debt_saved"), events)
        day = "2026-11-27"
        upgraded.existingDebtActionSaved()
        tracker().debtCreated()
        assertEquals(listOf("first_debt_saved", "debt_action_on_later_day"), events)
    }

    @Test fun existingInstallStaysExcluded() {
        tracker().initialize(wasUpdated = true)
        val next = tracker()
        next.initialize(wasUpdated = false)
        next.debtCreated()
        day = "2026-11-27"
        next.existingDebtActionSaved()
        assertEquals(emptyList<String>(), events)
    }

    @Test fun disabledAnalyticsDoesNotInitializeOrSend() {
        val disabled = tracker(enabled = false)
        disabled.initialize(wasUpdated = false)
        disabled.debtCreated()
        assertFalse(preferences.contains("eligible"))
        assertEquals(emptyList<String>(), events)
    }

    @Test fun reportingFailureDoesNotRetryTheSameMilestone() {
        val failing = tracker { throw IllegalStateException("SDK unavailable") }
        failing.initialize(wasUpdated = false)
        failing.debtCreated()
        tracker().debtCreated()
        assertEquals(emptyList<String>(), events)
        day = "2026-11-27"
        tracker().existingDebtActionSaved()
        assertEquals(listOf("debt_action_on_later_day"), events)
    }
}
