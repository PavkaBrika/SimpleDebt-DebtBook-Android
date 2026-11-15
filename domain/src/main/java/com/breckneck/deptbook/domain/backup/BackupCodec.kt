package com.breckneck.deptbook.domain.backup

import com.breckneck.deptbook.domain.model.*
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyPolicy
import com.google.gson.*
import java.math.BigDecimal
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Portable, explicit wire contract. Legacy flat JSON is read-only compatibility input. */
class BackupCodec(private val legacyFinanceCurrency: String = "USD") {
    data class Document(val data: AppDataLists, val createdAt: Long?, val photos: Map<Int, String>, val legacy: Boolean) {
        val recordCount: Int get() = count(data)
    }
    private val gson = GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(BigDecimal::class.java, JsonSerializer<BigDecimal> { value, _, _ -> JsonPrimitive(MoneyPolicy.stored(value)) })
        .registerTypeAdapter(Date::class.java, JsonSerializer<Date> { value, _, _ -> JsonPrimitive(value.time) })
        .create()

    fun encode(data: AppDataLists, photos: Map<Int, String> = emptyMap(), createdAt: Long = System.currentTimeMillis()): String {
        val root = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("formatVersion", VERSION)
            addProperty("createdAt", createdAt)
            addProperty("recordCount", count(data))
            add("data", gson.toJsonTree(data))
            add("photos", JsonObject().apply { photos.forEach { (id, content) -> addProperty(id.toString(), content) } })
        }
        return gson.toJson(root).also { decode(it) } // Exports must meet the same contract as imports.
    }

    fun decode(content: String): Document {
        require(content.length <= MAX_CHARACTERS) { "Backup too large" }
        val root = parseStrict(content).let { require(it.isJsonObject); it.asJsonObject }
        val legacy = !root.has("format") && !root.has("formatVersion")
        val payload: JsonObject
        val created: Long?
        if (legacy) { payload = root; created = null } else {
            require(root.string("format") == FORMAT && root.integer("formatVersion") == VERSION) { "Unsupported backup version" }
            created = root.long("createdAt").also { require(it > 0) }
            payload = root.get("data").let { require(it?.isJsonObject == true); it.asJsonObject }
        }
        val humans = payload.rows("humanList").map { row ->
            HumanDomain(row.id(), row.string("name"), row.money("sumDebt", legacy), row.string("currency"),
                if (legacy) BigDecimal.ZERO else row.money("openingAdjustment", false))
        }
        val debts = payload.rows("debtList").map { row ->
            val rawDate = row.string("date")
            DebtDomain(row.id(), row.money("sum", legacy), row.integer("idHuman"), row.optionalString("info"),
                if (legacy) LedgerDate.canonical(rawDate) ?: rawDate else rawDate,
                if (legacy) rawDate else row.optionalString("legacyDate"))
        }
        val categories = payload.rows("financeCategoryList").map { row ->
            val color = row.string("color")
            require(Regex("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?").matches(color)) { "Invalid category color" }
            val image = row.integer("image")
            require(Character.isValidCodePoint(image)) { "Invalid category image" }
            FinanceCategory(row.id(), row.string("name"),
                FinanceCategoryState.entries.firstOrNull { it.name == row.string("state") }
                    ?: throw IllegalArgumentException("Invalid category state"), color, image)
        }
        val finances = payload.rows("financeList").map { row ->
            Finance(row.id(), row.money("sum", legacy), row.date("date", legacy)!!, row.optionalString("info"), row.integer("financeCategoryId"), if (legacy) legacyFinanceCurrency else row.string("currency"), row.optionalDay("calendarDay"))
        }
        val goals = payload.rows("goalList").map { row ->
            Goal(row.id(), row.string("name"), row.money("sum", legacy), row.money("savedSum", legacy),
                row.string("currency"), if (legacy) null else row.optionalString("photoPath"),
                row.date("creationDate", legacy)!!, row.date("goalDate", legacy, optional = true),
                if (legacy) BigDecimal.ZERO else row.money("openingAdjustment", false), row.optionalDay("goalDay"))
        }
        val deposits = payload.rows("goalDepositList").map { row ->
            GoalDeposit(row.id(), row.money("sum", legacy), row.date("date", legacy)!!, row.integer("goalId"))
        }
        fun unique(ids: List<Int>): Set<Int> {
            require(ids.all { it > 0 } && ids.distinct().size == ids.size) { "Duplicate or invalid IDs" }
            return ids.toSet()
        }
        val humanIds = unique(humans.map { it.id }); unique(debts.map { it.id })
        val categoryIds = unique(categories.map { it.id }); unique(finances.map { it.id })
        val goalIds = unique(goals.map { it.id }); unique(deposits.map { it.id })
        require(debts.all { it.idHuman in humanIds } && finances.all { it.financeCategoryId in categoryIds } && deposits.all { it.goalId in goalIds }) { "Broken backup references" }
        val debtTotals = debts.groupBy { it.idHuman }.mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { total, row -> total + row.sum } }
        val depositTotals = deposits.groupBy { it.goalId }.mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { total, row -> total + row.sum } }
        humans.forEach {
            val total = debtTotals[it.id] ?: BigDecimal.ZERO
            if (legacy) it.openingAdjustment = (it.sumDebt - total).stripTrailingZeros()
            else require(it.sumDebt.compareTo(it.openingAdjustment + total) == 0) { "Inconsistent debt total" }
        }
        goals.forEach {
            val total = depositTotals[it.id] ?: BigDecimal.ZERO
            if (legacy) it.openingAdjustment = (it.savedSum - total).stripTrailingZeros()
            else require(it.savedSum.compareTo(it.openingAdjustment + total) == 0) { "Inconsistent savings total" }
        }
        val data = AppDataLists(humans, debts, finances, categories, goals, deposits)
        val photos = mutableMapOf<Int, String>()
        if (!legacy) {
            require(root.integer("recordCount") == count(data)) { "Invalid record count" }
            val photoObject = root.get("photos").let { require(it?.isJsonObject == true); it.asJsonObject }
            photoObject.entrySet().forEach { (key, value) ->
                val id = key.toIntOrNull()
                require(id != null && id in goalIds && key == id.toString() && value.isJsonPrimitive && value.asJsonPrimitive.isString)
                val encoded = value.asString
                require(encoded.isNotEmpty() && encoded.length <= MAX_PHOTO_CHARACTERS && Regex("[A-Za-z0-9+/]*={0,2}").matches(encoded))
                require(encoded.length % 4 == 0)
                photos[id] = encoded
            }
            require(goals.all { it.photoPath == null }) { "Local photo paths are not portable" }
        }
        return Document(data, created, photos, legacy)
    }

    private fun parseStrict(content: String): JsonElement {
        val reader = com.google.gson.stream.JsonReader(content.reader()).apply { isLenient = false }
        fun read(depth: Int): JsonElement {
            require(depth <= 30) { "Backup nesting too deep" }
            return when (reader.peek()) {
                com.google.gson.stream.JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        require(!has(name)) { "Duplicate JSON field" }
                        add(name, read(depth + 1))
                    }
                    reader.endObject()
                }
                com.google.gson.stream.JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                    reader.beginArray()
                    while (reader.hasNext()) { require(size() < MAX_RECORDS); add(read(depth + 1)) }
                    reader.endArray()
                }
                com.google.gson.stream.JsonToken.STRING -> JsonPrimitive(reader.nextString())
                com.google.gson.stream.JsonToken.NUMBER -> {
                    val number = reader.nextString()
                    require(number.length <= 220)
                    JsonPrimitive(BigDecimal(number))
                }
                com.google.gson.stream.JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
                else -> throw IllegalArgumentException("Invalid JSON")
            }
        }
        return reader.use { read(0).also { require(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT) } }
    }

    private fun JsonObject.optionalDay(name: String): String? = optionalString(name)?.also {
        require(LedgerDate.canonical(it) == it) { "Invalid calendar day" }
    }
    private fun JsonObject.rows(name: String): List<JsonObject> = get(name).let { value ->
        require(value?.isJsonArray == true) { "Missing table" }
        require(value.asJsonArray.size() <= MAX_RECORDS)
        value.asJsonArray.map { require(it.isJsonObject); it.asJsonObject }
    }
    private fun JsonObject.string(name: String): String = get(name).let {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Missing string" }
        it.asString.also { text -> require(text.length <= MAX_FIELD_CHARACTERS) }
    }
    private fun JsonObject.optionalString(name: String): String? =
        if (!has(name) || get(name).isJsonNull) null else string(name)
    private fun JsonObject.long(name: String): Long = get(name).let {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isNumber && Regex("-?[0-9]+").matches(it.asString)) { "Missing integer" }
        it.asString.toLong()
    }
    private fun JsonObject.integer(name: String): Int = long(name).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
    private fun JsonObject.id() = integer("id")
    private fun JsonObject.money(name: String, legacy: Boolean): BigDecimal = get(name).let {
        require(it != null && it.isJsonPrimitive &&
            (if (legacy) it.asJsonPrimitive.isNumber else it.asJsonPrimitive.isString)) { "Missing decimal amount" }
        require(it.asString.length <= 220)
        if (!legacy) require(Regex("-?[0-9]+(?:\\.[0-9]+)?").matches(it.asString))
        BigDecimal(it.asString).also(MoneyPolicy::stored).stripTrailingZeros()
    }
    private fun JsonObject.date(name: String, legacy: Boolean, optional: Boolean = false): Date? {
        val value = get(name)
        if (optional && (value == null || value.isJsonNull)) return null
        require(value != null && value.isJsonPrimitive) { "Missing date" }
        if (value.asJsonPrimitive.isNumber) return Date(long(name)).also {
            require(it.time in -62135596800000L..253402300799999L)
        }
        require(legacy && value.asJsonPrimitive.isString) { "Invalid date representation" }
        val text = value.asString
        require(text.length <= 100)
        val candidates = mutableSetOf<Long>()
        val patterns = listOf("MMM d, yyyy, h:mm:ss a", "MMM d, yyyy h:mm:ss a", "MMM d, yyyy HH:mm:ss", "dd.MM.yyyy, HH:mm:ss", "d MMM yyyy, HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (locale in listOf(Locale.US, Locale.forLanguageTag("ru"), Locale.GERMANY, Locale.FRANCE)) {
            for (pattern in patterns) {
                val parser = SimpleDateFormat(pattern, locale).apply {
                    isLenient = false
                    // Old Gson strings without an offset represent a local time with unknown zone.
                    // Use the legacy device zone; this limitation is surfaced before legacy import.
                    if (pattern.endsWith("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
                }
                val position = ParsePosition(0)
                parser.parse(text, position)?.takeIf { position.index == text.length }?.let { candidates += it.time }
            }
        }
        require(candidates.size == 1) { "Ambiguous or invalid legacy timestamp" }
        return Date(candidates.single())
    }
    companion object {
        const val VERSION = 2
        const val FORMAT = "SimpleDebtBackup"
        const val MAX_CHARACTERS = 32 * 1024 * 1024
        const val MAX_PHOTO_CHARACTERS = 8 * 1024 * 1024
        private const val MAX_FIELD_CHARACTERS = 64 * 1024
        private const val MAX_RECORDS = 100_000
        fun count(data: AppDataLists): Int = data.humanList.size + data.debtList.size + data.financeList.size + data.financeCategoryList.size + data.goalList.size + data.goalDepositList.size
    }
}
