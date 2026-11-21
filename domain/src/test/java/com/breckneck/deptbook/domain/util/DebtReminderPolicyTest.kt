package com.breckneck.deptbook.domain.util

import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.model.*
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.TimeZone

class DebtReminderPolicyTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val row = DebtDomain(1, BigDecimal("100").stripTrailingZeros(), 1, null, "2026-11-16",
        dueDay = "2026-11-17", reminderMinute = 9 * 60, reminderToken = "token-1")
    private fun at(day: String, minute: Int) = DebtReminderPolicy.trigger(day, minute, utc)

    @Test fun `due date is optional and must be a real ISO day on an original nonzero debt`() {
        DebtReminderPolicy.validate(row.copy(dueDay = null, reminderMinute = null, reminderToken = null))
        for (invalid in listOf(row.copy(dueDay = "2026-02-29"), row.copy(dueDay = "17/11/2026"),
            row.copy(repaymentOfId = 2), row.copy(sum = BigDecimal.ZERO), row.copy(reminderMinute = -1),
            row.copy(reminderMinute = 1440), row.copy(reminderToken = null), row.copy(dueDay = null),
            row.copy(reminderToken = "invalid token"))) {
            assertThrows(IllegalArgumentException::class.java) { DebtReminderPolicy.validate(invalid) }
        }
    }

    @Test fun `overdue depends on the local day and remaining amount in both directions`() {
        for (sign in listOf(1, -1)) {
            val original = row.copy(sum = row.sum * sign.toBigDecimal())
            val partial = DebtDomain(2, BigDecimal(-20 * sign), 1, null, "2026-11-16", repaymentOfId = 1, operationId = "repayment-2")
            val full = partial.copy(id = 3, sum = BigDecimal(-80 * sign), operationId = "repayment-3")
            assertFalse(DebtReminderPolicy.overdue(original, BigDecimal("80"), "2026-11-17"))
            assertTrue(DebtReminderPolicy.overdue(original, RepaymentPolicy.remaining(original, listOf(original, partial)), "2026-11-18"))
            assertFalse(DebtReminderPolicy.overdue(original, RepaymentPolicy.remaining(original, listOf(original, partial, full)), "2026-11-18"))
            assertTrue(DebtReminderPolicy.overdue(original, RepaymentPolicy.remaining(original, listOf(original, partial, full.copy(cancelled = true))), "2026-11-18"))
        }
    }

    @Test fun `timezone changes instant but never calendar date and DST resolves one instant`() {
        val bangkok = TimeZone.getTimeZone("Asia/Bangkok")
        assertEquals(7 * 60 * 60 * 1000L, at("2026-11-17", 540) - DebtReminderPolicy.trigger("2026-11-17", 540, bangkok))
        val la = TimeZone.getTimeZone("America/Los_Angeles")
        assertEquals(at("2026-03-08", 10 * 60 + 30), DebtReminderPolicy.trigger("2026-03-08", 2 * 60 + 30, la))
        val repeated = DebtReminderPolicy.trigger("2026-11-01", 90, la)
        assertEquals(at("2026-11-01", 9 * 60 + 30), repeated)
        assertEquals("2026-11-17", row.dueDay)
    }

    @Test fun `replanning allows late delivery today but never repeats delivered or older reminders`() {
        val before = at("2026-11-17", 8 * 60)
        assertEquals(at("2026-11-17", 540), DebtReminderPolicy.next(row, listOf(row), before, utc))
        val after = at("2026-11-17", 10 * 60)
        assertEquals(after + 1000, DebtReminderPolicy.next(row, listOf(row), after, utc))
        assertNull(DebtReminderPolicy.next(row.copy(reminderDelivered = true), listOf(row), before, utc))
        assertNull(DebtReminderPolicy.next(row, listOf(row), at("2026-11-18", 0), utc))
        assertNull(DebtReminderPolicy.next(row.copy(reminderMinute = null, reminderToken = null), listOf(row), before, utc))
    }

    @Test fun `backup v4 preserves reminders and rejects downgrade or corrupt schedule`() {
        val codec = BackupCodec()
        val data = AppDataLists(listOf(HumanDomain(1, "Test", BigDecimal("100").stripTrailingZeros(), "EUR", BigDecimal.ZERO)), listOf(row), emptyList(), emptyList(), emptyList(), emptyList())
        for (delivered in listOf(false, true)) {
            val source = data.copy(debtList = listOf(row.copy(reminderDelivered = delivered)))
            assertEquals(source, codec.decode(codec.encode(source)).data)
        }
        val root = JsonParser.parseString(codec.encode(data)).asJsonObject
        root.addProperty("formatVersion", 3)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(root.toString()) }
        root.getAsJsonObject("data").getAsJsonArray("debtList").forEach { item ->
            listOf("dueDay", "reminderMinute", "reminderToken", "reminderDelivered", "recordedAsLoan").forEach(item.asJsonObject::remove)
        }
        assertNull(codec.decode(root.toString()).data.debtList.single().dueDay)
        assertNull(codec.decode(root.toString()).data.debtList.single().reminderMinute)
        root.addProperty("formatVersion", 4)
        val debt = root.getAsJsonObject("data").getAsJsonArray("debtList").single().asJsonObject
        debt.addProperty("reminderDelivered", false)
        debt.addProperty("reminderMinute", 1440)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(root.toString()) }
    }
}
