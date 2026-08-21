package fi.anssi.kalakartta.data

import androidx.room.*

@Dao
interface TrackPointDao {
    @Insert
    fun insert(trackPoint: TrackPoint)

    @Insert
    fun insertAll(trackPoints: List<TrackPoint>)

    @Query("SELECT * FROM TrackPoint WHERE fishingSessionId = :sessionId ORDER BY timestamp ASC")
    fun getPointsForSession(sessionId: Long): List<TrackPoint>

    @Query("SELECT * FROM TrackPoint WHERE fishingSessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    fun getLastPointForSession(sessionId: Long): TrackPoint?

    @Query("SELECT COUNT(*) FROM TrackPoint WHERE fishingSessionId = :sessionId")
    fun getPointCountForSession(sessionId: Long): Int

    @Query("SELECT fishingSessionId, latitude, longitude, timestamp FROM TrackPoint")
    fun getAllForHeatmap(): List<TrackPointHeatmapData>

    @Query("""
        SELECT fishingSessionId, latitude, longitude, timestamp FROM TrackPoint 
        WHERE latitude BETWEEN :latSouth AND :latNorth 
          AND longitude BETWEEN :lonWest AND :lonEast
    """)
    fun getPointsForHeatmapArea(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): List<TrackPointHeatmapData>

    @Query("""
        SELECT fishingSessionId, latitude, longitude, timestamp FROM TrackPoint 
        WHERE timestamp >= :startDate AND timestamp <= :endDate
    """)
    fun getPointsForHeatmapRange(startDate: Long, endDate: Long): List<TrackPointHeatmapData>

    @Query("""
        SELECT fishingSessionId, latitude, longitude, timestamp FROM TrackPoint 
        WHERE timestamp >= :startDate AND timestamp <= :endDate
          AND latitude BETWEEN :latSouth AND :latNorth 
          AND longitude BETWEEN :lonWest AND :lonEast
    """)
    fun getPointsForHeatmapRangeAndArea(startDate: Long, endDate: Long, latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): List<TrackPointHeatmapData>

    @Query("""
        SELECT 
            CAST((longitude * :lonDegreeMeters / :gridSizeMeters) AS INTEGER) as x,
            CAST((latitude * :latDegreeMeters / :gridSizeMeters) AS INTEGER) as y,
            COUNT(DISTINCT fishingSessionId) as sessionCount
        FROM TrackPoint
        GROUP BY x, y
    """)
    fun getAggregatedHeatmap(latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): List<HeatmapGridCell>

    @Query("""
        SELECT 
            CAST((longitude * :lonDegreeMeters / :gridSizeMeters) AS INTEGER) as x,
            CAST((latitude * :latDegreeMeters / :gridSizeMeters) AS INTEGER) as y,
            COUNT(DISTINCT fishingSessionId) as sessionCount
        FROM TrackPoint 
        WHERE latitude BETWEEN :latSouth AND :latNorth 
          AND longitude BETWEEN :lonWest AND :lonEast
        GROUP BY x, y
    """)
    fun getAggregatedHeatmapArea(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double, latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): List<HeatmapGridCell>

    @Query("""
        SELECT 
            CAST((longitude * :lonDegreeMeters / :gridSizeMeters) AS INTEGER) as x,
            CAST((latitude * :latDegreeMeters / :gridSizeMeters) AS INTEGER) as y,
            COUNT(DISTINCT fishingSessionId) as sessionCount
        FROM TrackPoint 
        WHERE timestamp >= :startDate AND timestamp <= :endDate
        GROUP BY x, y
    """)
    fun getAggregatedHeatmapRange(startDate: Long, endDate: Long, latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): List<HeatmapGridCell>

    @Query("""
        SELECT 
            CAST((longitude * :lonDegreeMeters / :gridSizeMeters) AS INTEGER) as x,
            CAST((latitude * :latDegreeMeters / :gridSizeMeters) AS INTEGER) as y,
            COUNT(DISTINCT fishingSessionId) as sessionCount
        FROM TrackPoint 
        WHERE timestamp >= :startDate AND timestamp <= :endDate
          AND latitude BETWEEN :latSouth AND :latNorth 
          AND longitude BETWEEN :lonWest AND :lonEast
        GROUP BY x, y
    """)
    fun getAggregatedHeatmapRangeAndArea(startDate: Long, endDate: Long, latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double, latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): List<HeatmapGridCell>

    @Query("DELETE FROM TrackPoint WHERE fishingSessionId = :sessionId")
    fun deleteForSession(sessionId: Long)

    @Query("SELECT COUNT(*) FROM TrackPoint")
    fun getCount(): Int
}

data class TrackPointHeatmapData(
    val fishingSessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

data class HeatmapGridCell(
    val x: Int,
    val y: Int,
    val sessionCount: Int
)
