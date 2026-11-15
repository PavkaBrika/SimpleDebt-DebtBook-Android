package entity

import java.math.BigDecimal

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    foreignKeys = [ForeignKey(
        entity = GoalData::class,
        parentColumns = ["id"],
        childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class GoalDepositData(
    @PrimaryKey(autoGenerate = true)
    var id: Int,
    var sum: BigDecimal,
    var date: Date,
    var goalId: Int
)