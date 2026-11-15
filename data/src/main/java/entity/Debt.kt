package entity

import java.math.BigDecimal

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Debt(
    @PrimaryKey(autoGenerate = true)
    var id: Int,
    var sum: BigDecimal,
    var idHuman: Int,
    var info: String?,
    var date: String,
    var legacyDate: String? = null) {
}
