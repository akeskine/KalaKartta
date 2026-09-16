package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent runtime state for the one session that is currently being recorded.
 * The fixed primary key makes the state a singleton and prevents two services from
 * claiming different sessions as active at the same time.
 */
@Entity(tableName = "ActiveFishingSession")
data class ActiveFishingSession(
    @PrimaryKey val singletonId: Int = 1,
    val sessionId: Long,
    val locationCheckIntervalSeconds: Int,
    val minTrackPointIntervalSeconds: Int,
    val maxTrackPointIntervalSeconds: Int,
    val minTrackPointDistanceMeters: Int
)
