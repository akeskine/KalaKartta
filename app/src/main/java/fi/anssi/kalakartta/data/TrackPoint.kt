package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = FishingSession::class,
            parentColumns = ["id"],
            childColumns = ["fishingSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("fishingSessionId")]
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fishingSessionId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val accuracy: Float
)
