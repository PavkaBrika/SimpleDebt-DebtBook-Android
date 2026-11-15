package entity

import java.math.BigDecimal

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity
data class GoalData(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    var name: String,
    var sum: BigDecimal,
    var savedSum: BigDecimal,
    var currency: String,
    var photoPath: String?,
    var creationDate: Date,
    var goalDate: Date?,
    var openingAdjustment: BigDecimal = savedSum
)
