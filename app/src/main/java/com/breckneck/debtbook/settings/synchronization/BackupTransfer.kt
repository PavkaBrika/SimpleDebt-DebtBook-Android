package com.breckneck.debtbook.settings.synchronization


import com.breckneck.deptbook.domain.model.AppDataLists
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps the upload snapshot at the moment of upload, never at screen creation. */
internal class BackupTransfer(
    private val readCurrentData: () -> AppDataLists,
    private val replaceCurrentData: (AppDataLists) -> Unit,
) {
    private val gson = Gson()

    suspend fun upload(writeRemote: suspend (String) -> Unit) {
        val content = withContext(Dispatchers.IO) { gson.toJson(readCurrentData()) }
        writeRemote(content)
    }

    suspend fun restore(readRemote: suspend () -> String) {
        val content = readRemote()
        withContext(Dispatchers.IO) {
            val data = decode(content)
            // The repository performs the entire replacement in its existing Room transaction.
            replaceCurrentData(data)
        }
    }

    @Suppress("SENSELESS_COMPARISON") // Gson may populate null into Kotlin non-null fields.
    private fun decode(content: String): AppDataLists {
        val root = JsonParser.parseString(content)
        require(root.isJsonObject) { "Invalid backup" }
        val requiredNumbers = mapOf(
            "humanList" to listOf("id", "sumDebt"),
            "debtList" to listOf("id", "sum", "idHuman"),
            "financeList" to listOf("id", "sum", "financeCategoryId"),
            "financeCategoryList" to listOf("id", "image"),
            "goalList" to listOf("id", "sum", "savedSum"),
            "goalDepositList" to listOf("id", "sum", "goalId"),
        )
        requiredNumbers.forEach { (name, numericFields) ->
            val list = root.asJsonObject.get(name)
            require(list != null && list.isJsonArray) { "Incomplete backup" }
            require(list.asJsonArray.all { it.isJsonObject }) { "Invalid backup record" }
            list.asJsonArray.forEach { record ->
                numericFields.forEach { field ->
                    val value = record.asJsonObject.get(field)
                    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                        "Missing or invalid numeric backup field"
                    }
                }
            }
        }
        val data = gson.fromJson(root, AppDataLists::class.java)
        // Gson can bypass Kotlin non-null constructors. Validate before any database mutation.
        require(data.humanList.all { it.name != null && it.currency != null && it.sumDebt != null })
        require(data.debtList.all { it.date != null && it.sum != null })
        require(data.financeList.all { it.date != null && it.sum != null })
        require(data.financeCategoryList.all { it.name != null && it.state != null && it.color != null })
        require(data.goalList.all {
            it.name != null && it.currency != null && it.creationDate != null &&
                it.sum != null && it.savedSum != null
        })
        require(data.goalDepositList.all { it.date != null && it.sum != null })
        return data
    }
}
