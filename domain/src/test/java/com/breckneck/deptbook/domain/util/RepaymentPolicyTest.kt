package com.breckneck.deptbook.domain.util

import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.model.*
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RepaymentPolicyTest {
    private val original = DebtDomain(1, BigDecimal("100").stripTrailingZeros(), 1, "Loan", "2026-11-16")
    private fun payment(id: Int, value: String) = DebtDomain(id, BigDecimal(value).stripTrailingZeros(), 1, null,
        "2026-11-16", repaymentOfId = 1, operationId = "request-00000000$id")
    private fun snapshot(rows: List<DebtDomain>) = AppDataLists(
        listOf(HumanDomain(1, "Person", rows.fold(BigDecimal("7.01")) { a, b -> a + b.effectiveSum }.stripTrailingZeros(), "EUR", BigDecimal("7.01"))),
        rows, emptyList(), emptyList(), emptyList(), emptyList())

    @Test fun `multiple repayments cancellation and closure preserve both directions exactly`() {
        for (direction in listOf(BigDecimal.ONE, BigDecimal.ONE.negate())) {
            val debt = original.copy(sum = original.sum * direction)
            val first = payment(2, "-20").copy(sum = BigDecimal("-20") * direction)
            val cancelled = payment(3, "-30").copy(sum = BigDecimal("-30") * direction, cancelled = true)
            val rows = listOf(debt, first, cancelled)
            RepaymentPolicy.validate(rows)
            assertEquals(0, BigDecimal("80").compareTo(RepaymentPolicy.remaining(debt, rows)))
            val closed = rows + payment(4, "-80").copy(sum = BigDecimal("-80") * direction)
            RepaymentPolicy.validate(closed)
            assertEquals(0, RepaymentPolicy.remaining(debt, closed).signum())
        }
    }
    @Test fun `unlinked legacy operations do not settle each other`() {
        val rows = listOf(original, original.copy(id = 2, sum = BigDecimal("-100")))
        RepaymentPolicy.validate(rows)
        assertEquals(0, BigDecimal("100").compareTo(RepaymentPolicy.remaining(original, rows)))
    }
    @Test fun `invalid links directions request IDs and overpayments are rejected`() {
        val first = payment(2, "-20")
        for (bad in listOf(first.copy(repaymentOfId = 99), first.copy(idHuman = 2),
            first.copy(sum = BigDecimal.ZERO), first.copy(sum = BigDecimal("20")),
            first.copy(sum = BigDecimal("-101")), first.copy(operationId = null),
            first.copy(repaymentOfId = 2), first.copy(date = "unknown"))) {
            assertThrows(IllegalArgumentException::class.java) { RepaymentPolicy.validate(listOf(original, bad)) }
        }
        assertThrows(IllegalArgumentException::class.java) { RepaymentPolicy.validate(listOf(original, first, first.copy(id = 3))) }
        assertThrows(IllegalArgumentException::class.java) { RepaymentPolicy.validate(listOf(original.copy(cancelled = true))) }
    }
    @Test fun `backup preserves repayment audit and validates before totals`() {
        val codec = BackupCodec()
        val data = snapshot(listOf(original, payment(2, "-20"), payment(3, "-30").copy(cancelled = true)))
        val text = codec.encode(data)
        assertEquals(data, codec.decode(text).data)
        for ((field, value) in listOf("repaymentOfId" to "99", "cancelled" to "false", "operationId" to "\"wrong\"", "sum" to "\"-101\"")) {
            val root = JsonParser.parseString(text).asJsonObject
            root.getAsJsonObject("data").getAsJsonArray("debtList")[2].asJsonObject.add(field, JsonParser.parseString(value))
            assertThrows(IllegalArgumentException::class.java) { codec.decode(root.toString()) }
        }
    }
    @Test fun `v2 import keeps every old operation independent and v3 cannot be downgraded`() {
        val codec = BackupCodec()
        val data = snapshot(listOf(original, original.copy(id = 2, sum = BigDecimal("-20").stripTrailingZeros())))
        val root = JsonParser.parseString(codec.encode(data)).asJsonObject
        root.addProperty("formatVersion", 2)
        root.getAsJsonObject("data").getAsJsonArray("debtList").forEach { listOf("cancelled", "dueDay", "reminderMinute", "reminderToken", "reminderDelivered").forEach(it.asJsonObject::remove) }
        assertEquals(data, codec.decode(root.toString()).data)
        val linked = JsonParser.parseString(codec.encode(snapshot(listOf(original, payment(2, "-20"))))).asJsonObject
        linked.addProperty("formatVersion", 2)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(linked.toString()) }
    }
}
