package com.breckneck.deptbook.domain.backup

import com.breckneck.deptbook.domain.model.*
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BackupCodecTest {
    private val codec = BackupCodec()
    private fun fixture() = AppDataLists(
        listOf(HumanDomain(1, "Person", "999999999999999999.99".toBigDecimal(), "EUR", "0.01".toBigDecimal())),
        listOf(DebtDomain(2, "999999999999999999.98".toBigDecimal(), 1, "Note", "2026-11-15", "15 Nov 2026")),
        listOf(Finance(3, "0.01".toBigDecimal(), Date(1794700800123L), "Info", 4, "EUR", "2026-11-15")),
        listOf(FinanceCategory(4, "Food", FinanceCategoryState.EXPENSE, "#123456", 123)),
        listOf(Goal(5, "Goal", "1000".toBigDecimal().stripTrailingZeros(), "20.3".toBigDecimal(), "JPY", null, Date(1794700800123L), Date(1794787200000L), "20.1".toBigDecimal(), "2026-11-16")),
        listOf(GoalDeposit(6, "0.2".toBigDecimal(), Date(1794700800123L), 5)),
    )
    private fun encoded() = codec.encode(fixture(), mapOf(5 to "aW1hZ2U="), 1794700800123L)

    @Test fun `versioned round trip is lossless for every table and metadata`() {
        val text = encoded()
        assertTrue(text.contains("\"sum\":\"999999999999999999.98\""))
        val document = codec.decode(text)
        assertEquals(fixture(), document.data)
        assertEquals(1794700800123L, document.createdAt)
        assertEquals(6, document.recordCount)
        assertEquals("aW1hZ2U=", document.photos[5])
        assertFalse(document.legacy)
    }
    @Test fun `versioned timestamp and day survive locale and timezone changes`() {
        val text = encoded()
        val oldLocale = Locale.getDefault(); val oldZone = TimeZone.getDefault()
        try {
            for (language in listOf("en-US", "ru-RU", "de-DE", "fr-FR")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                for (zone in listOf("Pacific/Kiritimati", "America/Los_Angeles")) {
                    TimeZone.setDefault(TimeZone.getTimeZone(zone))
                    assertEquals(fixture(), codec.decode(text).data)
                }
            }
        } finally { Locale.setDefault(oldLocale); TimeZone.setDefault(oldZone) }
    }
    @Test fun `legacy flat JSON keeps totals precision relations and raw calendar text`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val old = JsonParser.parseString(Gson().toJson(fixture())).asJsonObject
            // Actual v1 used JSON numbers, without openingAdjustment or legacyDate.
            for ((table, fields) in mapOf("humanList" to listOf("sumDebt"), "debtList" to listOf("sum"), "financeList" to listOf("sum"), "goalList" to listOf("sum", "savedSum"), "goalDepositList" to listOf("sum"))) {
                old.getAsJsonArray(table).forEach { row ->
                    fields.forEach { field -> row.asJsonObject.addProperty(field, row.asJsonObject.get(field).asBigDecimal) }
                    row.asJsonObject.remove("openingAdjustment")
                }
            }
            old.getAsJsonArray("financeList").forEach { it.asJsonObject.remove("currency"); it.asJsonObject.remove("calendarDay") }
            old.getAsJsonArray("goalList").forEach { it.asJsonObject.remove("goalDay") }
            old.getAsJsonArray("debtList")[0].asJsonObject.addProperty("date", "01/02/2024")
            old.getAsJsonArray("goalList")[0].asJsonObject.addProperty("photoPath", "/another/device/photo.jpg")
            val document = BackupCodec("GBP").decode(old.toString())
            assertTrue(document.legacy)
            assertEquals("GBP", document.data.financeList.single().currency)
            assertNull(document.data.financeList.single().calendarDay)
            assertNull(document.createdAt)
            assertEquals("01/02/2024", document.data.debtList.single().date)
            assertEquals("01/02/2024", document.data.debtList.single().legacyDate)
            assertEquals("0.01".toBigDecimal(), document.data.humanList.single().openingAdjustment)
            assertNull(document.data.goalList.single().photoPath)
            assertEquals(fixture().financeList.single().date.time / 1000 * 1000, document.data.financeList.single().date.time)
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun `unsupported corrupt and incomplete envelopes are rejected`() {
        val valid = encoded()
        val bad = listOf(
            valid.replace("\"formatVersion\":2", "\"formatVersion\":3"),
            valid.replace("\"recordCount\":6", "\"recordCount\":7"),
            valid.replace("\"calendarDay\":\"2026-11-15\"", "\"calendarDay\":\"2026-02-30\""),
            valid.replace("\"idHuman\":1", "\"idHuman\":99"),
            valid.replace("\"financeCategoryId\":4", "\"financeCategoryId\":99"),
            valid.replace("\"goalId\":5", "\"goalId\":99"),
            valid.replace("\"openingAdjustment\":\"0.01\"", "\"openingAdjustment\":\"0.02\""),
            valid.replace("\"state\":\"EXPENSE\"", "\"state\":\"unknown\""),
            valid.replace("\"name\":\"Person\"", "\"name\":null"),
            valid.replace("\"sum\":\"0.01\"", "\"sum\":0.01"),
            valid.replace("\"sum\":\"0.01\"", "\"sum\":\"NaN\""),
            valid.replace("\"sum\":\"0.01\"", "\"sum\":\"1e999\""),
            valid.replace("\"id\":1", "\"id\":1.5"),
            valid.replace("\"date\":1794700800123", "\"date\":null"),
            valid.replace("\"photos\":{\"5\":", "\"photos\":{\"99\":"),
            valid.replace("aW1hZ2U=", "not a photo"),
            valid.replace("\"color\":\"#123456\"", "\"color\":\"invalid\""),
            valid.dropLast(1), valid + "garbage", "{}", "[]", "null",
            valid.replace("\"recordCount\":6", "\"recordCount\":6,\"recordCount\":6"),
        )
        bad.forEachIndexed { index, text -> assertThrows(Exception::class.java, { codec.decode(text) }, "case $index: " + text.take(120)) }
    }
    @Test fun `duplicate row IDs are rejected before restore`() {
        val root = JsonParser.parseString(encoded()).asJsonObject
        val rows = root.getAsJsonObject("data").getAsJsonArray("debtList")
        rows.add(rows[0].deepCopy())
        root.addProperty("recordCount", 7)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(root.toString()) }
    }
    @Test fun `all tables are mandatory even when empty`() {
        for (table in listOf("humanList", "debtList", "financeList", "financeCategoryList", "goalList", "goalDepositList")) {
            val root = JsonParser.parseString(encoded()).asJsonObject
            root.getAsJsonObject("data").remove(table)
            assertThrows(IllegalArgumentException::class.java) { codec.decode(root.toString()) }
        }
    }
}
