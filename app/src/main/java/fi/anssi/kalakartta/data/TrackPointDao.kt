package fi.anssi.kalakartta.data

import androidx.room.*

@Dao
interface TrackPointDao {
    @Insert
    fun insert(trackPoint: TrackPoint)

    @Query("SELECT * FROM TrackPoint")
    fun getAll(): List<TrackPoint>

    @Query("SELECT * FROM TrackPoint WHERE fishingSessionId = :sessionId ORDER BY timestamp ASC")
    fun getPointsForSession(sessionId: Long): List<TrackPoint>

    @Query("DELETE FROM TrackPoint WHERE fishingSessionId = :sessionId")
    fun deleteForSession(sessionId: Long)
}
