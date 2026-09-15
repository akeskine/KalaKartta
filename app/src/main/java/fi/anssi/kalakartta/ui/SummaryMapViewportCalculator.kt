package fi.anssi.kalakartta.ui

/** Calculates a useful map viewport for the fish points shown in a summary. */
object SummaryMapViewportCalculator {
    private const val KILOMETERS_PER_LATITUDE_DEGREE = 111.32
    private const val MAX_CONTENT_HEIGHT_KM = 900.0
    private const val MAX_CONTENT_WIDTH_KM = 1500.0
    private const val MAX_VIEWPORT_HEIGHT_KM = 1000.0
    private const val MARGIN_RATIO = 0.08
    private const val MIN_MARGIN_KM = 5.0

    data class Point(val latitude: Double, val longitude: Double)

    data class Bounds(
        val north: Double,
        val east: Double,
        val south: Double,
        val west: Double
    )

    fun calculate(points: List<Point>): Bounds? {
        if (points.isEmpty()) return null

        val minLatitude = points.minOf { it.latitude }
        val maxLatitude = points.maxOf { it.latitude }
        val latitudeSpanKm = (maxLatitude - minLatitude) * KILOMETERS_PER_LATITUDE_DEGREE
        val selectedPoints = if (latitudeSpanKm > MAX_CONTENT_HEIGHT_KM) {
            selectDensestLatitudeBand(points)
        } else {
            points
        }

        val longitudeLimitedPoints = selectDensestLongitudeBand(selectedPoints)
        val selectedMinLatitude = longitudeLimitedPoints.minOf { it.latitude }
        val selectedMaxLatitude = longitudeLimitedPoints.maxOf { it.latitude }
        val selectedMinLongitude = longitudeLimitedPoints.minOf { it.longitude }
        val selectedMaxLongitude = longitudeLimitedPoints.maxOf { it.longitude }
        val selectedLatitudeSpanKm =
            (selectedMaxLatitude - selectedMinLatitude) * KILOMETERS_PER_LATITUDE_DEGREE

        val requestedMarginKm = maxOf(MIN_MARGIN_KM, selectedLatitudeSpanKm * MARGIN_RATIO)
        val availableMarginKm =
            maxOf(0.0, (MAX_VIEWPORT_HEIGHT_KM - selectedLatitudeSpanKm) / 2.0)
        val latitudeMarginKm = minOf(requestedMarginKm, availableMarginKm)
        val latitudeMarginDegrees = latitudeMarginKm / KILOMETERS_PER_LATITUDE_DEGREE

        val longitudeSpan = selectedMaxLongitude - selectedMinLongitude
        val longitudeMargin = maxOf(
            MIN_MARGIN_KM / KILOMETERS_PER_LATITUDE_DEGREE,
            longitudeSpan * MARGIN_RATIO
        )

        return Bounds(
            north = minOf(90.0, selectedMaxLatitude + latitudeMarginDegrees),
            east = minOf(180.0, selectedMaxLongitude + longitudeMargin),
            south = maxOf(-90.0, selectedMinLatitude - latitudeMarginDegrees),
            west = maxOf(-180.0, selectedMinLongitude - longitudeMargin)
        )
    }

    private fun selectDensestLongitudeBand(points: List<Point>): List<Point> {
        if (points.size < 2) return points

        val minLongitude = points.minOf { it.longitude }
        val maxLongitude = points.maxOf { it.longitude }
        val centerLatitude = points.map { it.latitude }.average()
        val kilometersPerLongitudeDegree =
            KILOMETERS_PER_LATITUDE_DEGREE * maxOf(0.1, kotlin.math.cos(Math.toRadians(centerLatitude)))
        val longitudeSpanKm = (maxLongitude - minLongitude) * kilometersPerLongitudeDegree
        if (longitudeSpanKm <= MAX_CONTENT_WIDTH_KM) return points

        val sorted = points.sortedBy { it.longitude }
        val maxBandLongitudeSpan = MAX_CONTENT_WIDTH_KM / kilometersPerLongitudeDegree
        var bestStart = 0
        var bestEnd = 0
        var bestCount = 0
        var end = 0

        for (start in sorted.indices) {
            if (end < start) end = start
            while (
                end + 1 < sorted.size &&
                sorted[end + 1].longitude - sorted[start].longitude <= maxBandLongitudeSpan
            ) {
                end++
            }

            val count = end - start + 1
            if (count > bestCount) {
                bestStart = start
                bestEnd = end
                bestCount = count
            }
        }

        return sorted.subList(bestStart, bestEnd + 1)
    }

    private fun selectDensestLatitudeBand(points: List<Point>): List<Point> {
        val sorted = points.sortedBy { it.latitude }
        val maxBandLatitudeSpan = MAX_CONTENT_HEIGHT_KM / KILOMETERS_PER_LATITUDE_DEGREE
        var bestStart = 0
        var bestEnd = 0
        var bestCount = 0
        var end = 0

        for (start in sorted.indices) {
            if (end < start) end = start
            while (
                end + 1 < sorted.size &&
                sorted[end + 1].latitude - sorted[start].latitude <= maxBandLatitudeSpan
            ) {
                end++
            }

            val count = end - start + 1
            if (count > bestCount) {
                bestStart = start
                bestEnd = end
                bestCount = count
            }
        }

        return sorted.subList(bestStart, bestEnd + 1)
    }
}
