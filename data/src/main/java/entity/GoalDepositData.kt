package entity

import java.math.BigDecimal

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date
import com.breckneck.deptbook.domain.model.GoalDeposit

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
    var goalId: Int,
    @ColumnInfo(defaultValue = "0") val isInitial: Boolean = false
) {
    fun toDomain() = GoalDeposit(id, sum, date, goalId, isInitial)
}

fun GoalDeposit.toEntity() = GoalDepositData(id, sum, date, goalId, isInitial)
