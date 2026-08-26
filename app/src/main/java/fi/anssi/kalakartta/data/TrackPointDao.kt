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

    @Query("SELECT fishingSessionId, latitude, longitude, timestamp, speed FROM TrackPoint")
    fun getAllForHeatmap(): List<TrackPointHeatmapData>

    @Query("""
        SELECT fishingSessionId, latitude, longitude, timestamp, speed FROM TrackPoint 
        WHERE latitude BETWEEN :latSouth AND :latNorth 
          AND longitude BETWEEN :lonWest AND :lonEast
    """)
    fun getPointsForHeatmapArea(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): List<TrackPointHeatmapData>

    @Query("""
        SELECT fishingSessionId, latitude, longitude, timestamp, speed FROM TrackPoint 
        WHERE timestamp >= :startDate AND timestamp <= :endDate
    """)
    fun getPointsForHeatmapRange(startDate: Long, endDate: Long): List<TrackPointHeatmapData>

    @Query("""
        SELECT fishingSessionId, latitude, longitude, timestamp, speed FROM TrackPoint 
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
            COUNT(*) as sessionCount
        FROM TrackPoint
        GROUP BY x, y
    """)
    fun getAggregatedHeatmapPoints(latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): List<HeatmapGridCell>

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
            COUNT(*) as sessionCount
        FROM TrackPoint 
        WHERE latitude BETWEEN :latSouth AND :latNorth 
          AND longitude BETWEEN :lonWest AND :lonEast
        GROUP BY x, y
    """)
    fun getAggregatedHeatmapAreaPoints(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double, latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): List<HeatmapGridCell>

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
            COUNT(*) as sessionCount
        FROM TrackPoint 
        WHERE timestamp >= :startDate AND timestamp <= :endDate
        GROUP BY x, y
    """)
    fun getAggregatedHeatmapRangePoints(startDate: Long, endDate: Long, latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): List<HeatmapGridCell>

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

    @Query("""
        SELECT 
            CAST((longitude * :lonDegreeMeters / :gridSizeMeters) AS INTEGER) as x,
            CAST((latitude * :latDegreeMeters / :gridSizeMeters) AS INTEGER) as y,
            COUNT(*) as sessionCount
        FROM TrackPoint 
        WHERE timestamp >= :startDate AND timestamp <= :endDate
          AND latitude BETWEEN :latSouth AND :latNorth 
          AND longitude BETWEEN :lonWest AND :lonEast
        GROUP BY x, y
    """)
    fun getAggregatedHeatmapRangeAndAreaPoints(startDate: Long, endDate: Long, latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double, latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): List<HeatmapGridCell>

    @Query("DELETE FROM TrackPoint WHERE fishingSessionId = :sessionId AND (timestamp < :startTime OR timestamp > :endTime)")
    fun deletePointsOutsideRange(sessionId: Long, startTime: Long, endTime: Long)

    @Query("DELETE FROM TrackPoint WHERE fishingSessionId = :sessionId")
    fun deleteForSession(sessionId: Long)

    @Query("SELECT COUNT(*) FROM TrackPoint")
    fun getCount(): Int

    @Query("SELECT COUNT(*) FROM TrackPoint WHERE latitude BETWEEN :latSouth AND :latNorth AND longitude BETWEEN :lonWest AND :lonEast")
    fun getCountArea(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): Int

    @Query("SELECT COUNT(*) FROM TrackPoint WHERE timestamp >= :startDate AND timestamp <= :endDate")
    fun getCountRange(startDate: Long, endDate: Long): Int

    @Query("SELECT COUNT(*) FROM TrackPoint WHERE timestamp >= :startDate AND timestamp <= :endDate AND latitude BETWEEN :latSouth AND :latNorth AND longitude BETWEEN :lonWest AND :lonEast")
    fun getCountRangeAndArea(startDate: Long, endDate: Long, latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): Int

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT 
                CAST((longitude * :lonDegreeMeters / :gridSizeMeters) AS INTEGER) as x,
                CAST((latitude * :latDegreeMeters / :gridSizeMeters) AS INTEGER) as y
            FROM TrackPoint
            GROUP BY x, y
        )
    """)
    fun getHeatmapCellCount(latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): Int

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT 
                CAST((longitude * :lonDegreeMeters / :gridSizeMeters) AS INTEGER) as x,
                CAST((latitude * :latDegreeMeters / :gridSizeMeters) AS INTEGER) as y
            FROM TrackPoint
            WHERE latitude BETWEEN :latSouth AND :latNorth 
              AND longitude BETWEEN :lonWest AND :lonEast
            GROUP BY x, y
        )
    """)
    fun getHeatmapCellCountArea(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double, latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): Int

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT 
                CAST((longitude * :lonDegreeMeters / :gridSizeMeters) AS INTEGER) as x,
                CAST((latitude * :latDegreeMeters / :gridSizeMeters) AS INTEGER) as y
            FROM TrackPoint
            WHERE timestamp >= :startDate AND timestamp <= :endDate
            GROUP BY x, y
        )
    """)
    fun getHeatmapCellCountRange(startDate: Long, endDate: Long, latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): Int

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT 
                CAST((longitude * :lonDegreeMeters / :gridSizeMeters) AS INTEGER) as x,
                CAST((latitude * :latDegreeMeters / :gridSizeMeters) AS INTEGER) as y
            FROM TrackPoint
            WHERE timestamp >= :startDate AND timestamp <= :endDate
              AND latitude BETWEEN :latSouth AND :latNorth 
              AND longitude BETWEEN :lonWest AND :lonEast
            GROUP BY x, y
        )
    """)
    fun getHeatmapCellCountRangeAndArea(startDate: Long, endDate: Long, latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double, latDegreeMeters: Double, lonDegreeMeters: Double, gridSizeMeters: Double): Int
}

data class TrackPointHeatmapData(
    val fishingSessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val speed: Float
)

data class HeatmapGridCell(
    val x: Int,
    val y: Int,
    val sessionCount: Int
)
