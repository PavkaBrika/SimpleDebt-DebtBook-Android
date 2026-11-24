package entity

import java.math.BigDecimal

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Human(
    @PrimaryKey(autoGenerate = true)
    var id: Int,
    var name: String,
    var sumDebt: BigDecimal,
    var currency: String,
    var openingAdjustment: BigDecimal = sumDebt,
    val photo: String? = null,
) {
}