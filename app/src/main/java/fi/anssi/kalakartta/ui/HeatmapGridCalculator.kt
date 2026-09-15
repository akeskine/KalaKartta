package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.TrackPointHeatmapData
import kotlin.math.cos

/** Heatmap-ruudukon Androidista ja karttanäkymästä riippumaton laskenta. */
object HeatmapGridCalculator {

    const val LATITUDE_DEGREE_METERS = 111_320.0

    fun longitudeDegreeMeters(referenceLatitude: Double): Double =
        LATITUDE_DEGREE_METERS * cos(Math.toRadians(referenceLatitude))

    fun cellFor(
        latitude: Double,
        longitude: Double,
        gridSizeMeters: Double,
        referenceLatitude: Double
    ): Pair<Int, Int> {
        val longitudeMeters = longitudeDegreeMeters(referenceLatitude)
        val x = (longitude * longitudeMeters / gridSizeMeters).toInt()
        val y = (latitude * LATITUDE_DEGREE_METERS / gridSizeMeters).toInt()
        return x to y
    }

    fun pointCounts(
        points: List<TrackPointHeatmapData>,
        gridSizeMeters: Double,
        referenceLatitude: Double
    ): Map<Pair<Int, Int>, Int> = points.groupingBy {
        cellFor(it.latitude, it.longitude, gridSizeMeters, referenceLatitude)
    }.eachCount()

    fun sessionCounts(
        points: List<TrackPointHeatmapData>,
        gridSizeMeters: Double,
        referenceLatitude: Double
    ): Map<Pair<Int, Int>, Int> = points
        .groupBy { cellFor(it.latitude, it.longitude, gridSizeMeters, referenceLatitude) }
        .mapValues { (_, cellPoints) -> cellPoints.map { it.fishingSessionId }.toSet().size }

    fun pointAndSessionCounts(
        points: List<TrackPointHeatmapData>,
        gridSizeMeters: Double,
        referenceLatitude: Double
    ): Map<Pair<Int, Int>, Int> = points
        .groupBy { cellFor(it.latitude, it.longitude, gridSizeMeters, referenceLatitude) }
        .mapValues { (_, cellPoints) ->
            cellPoints.size + 3 * cellPoints.map { it.fishingSessionId }.toSet().size
        }

    fun latitudeForCell(y: Int, gridSizeMeters: Double): Double =
        y * gridSizeMeters / LATITUDE_DEGREE_METERS

    fun longitudeForCell(x: Int, gridSizeMeters: Double, referenceLatitude: Double): Double =
        x * gridSizeMeters / longitudeDegreeMeters(referenceLatitude)
}
