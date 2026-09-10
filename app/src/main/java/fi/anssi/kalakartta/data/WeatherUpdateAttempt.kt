package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "WeatherUpdateAttempt")
data class WeatherUpdateAttempt(
    @PrimaryKey val catchId: Long,
    val attemptedAt: Long,
    val succeeded: Boolean,
    val errorMessage: String? = null
)

object WeatherUpdateAttemptSelector {
    fun prioritizeCatchIds(
        catchIds: List<Long>,
        attemptsByCatchId: Map<Long, WeatherUpdateAttempt>
    ): List<Long> {
        return catchIds.sortedBy { attemptsByCatchId[it]?.succeeded == false }
    }

    fun countFailedCatchIds(
        catchIds: List<Long>,
        attemptsByCatchId: Map<Long, WeatherUpdateAttempt>
    ): Int {
        return catchIds.count { attemptsByCatchId[it]?.succeeded == false }
    }
}