package entity

import java.math.BigDecimal

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import com.breckneck.deptbook.domain.model.DebtDomain

@Entity(indices = [Index(value = ["operationId"], unique = true), Index(value = ["repaymentOfId"])])
data class Debt(
    @PrimaryKey(autoGenerate = true)
    var id: Int,
    var sum: BigDecimal,
    var idHuman: Int,
    var info: String?,
    var date: String,
    var legacyDate: String? = null,
    val repaymentOfId: Int? = null,
    val operationId: String? = null,
    @ColumnInfo(defaultValue = "0") val cancelled: Boolean = false) {
    fun toDomain() = DebtDomain(id, sum, idHuman, info, date, legacyDate, repaymentOfId, operationId, cancelled)
}

fun DebtDomain.toEntity() = Debt(id, sum, idHuman, info, date, legacyDate, repaymentOfId, operationId, cancelled)
