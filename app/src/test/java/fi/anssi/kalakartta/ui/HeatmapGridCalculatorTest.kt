package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.TrackPointHeatmapData
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos

class HeatmapGridCalculatorTest {

    @Test
    fun longitudeScaleUsesReferenceLatitude() {
        val expected = 111_320.0 * cos(Math.toRadians(64.7))

        assertEquals(expected, HeatmapGridCalculator.longitudeDegreeMeters(64.7), 0.0001)
    }

    @Test
    fun cellCoordinatesUseTheSameStableGridForPositiveAndNegativeValues() {
        assertEquals(
            2 to 2,
            HeatmapGridCalculator.cellFor(0.002, 0.002, 100.0, 0.0)
        )
        assertEquals(
            0 to 0,
            HeatmapGridCalculator.cellFor(-0.0005, -0.0005, 100.0, 0.0)
        )
    }

    @Test
    fun pointCountsAggregatePointsByCell() {
        val points = listOf(
            point(sessionId = 1, latitude = 0.001, longitude = 0.001),
            point(sessionId = 2, latitude = 0.0015, longitude = 0.0015),
            point(sessionId = 2, latitude = 0.003, longitude = 0.003)
        )

        val counts = HeatmapGridCalculator.pointCounts(points, 200.0, 0.0)

        assertEquals(2, counts[0 to 0])
        assertEquals(1, counts[1 to 1])
    }

    @Test
    fun sessionCountsCountEachSessionOnlyOncePerCell() {
        val points = listOf(
            point(sessionId = 1, latitude = 0.001, longitude = 0.001),
            point(sessionId = 1, latitude = 0.0015, longitude = 0.0015),
            point(sessionId = 2, latitude = 0.0017, longitude = 0.0017)
        )

        val counts = HeatmapGridCalculator.sessionCounts(points, 200.0, 0.0)

        assertEquals(2, counts[0 to 0])
    }

    @Test
    fun cellCoordinatesCanBeConvertedBackToGridOrigin() {
        val latitude = HeatmapGridCalculator.latitudeForCell(3, 100.0)
        val longitude = HeatmapGridCalculator.longitudeForCell(4, 100.0, 64.7)

        assertEquals(3 * 100.0 / 111_320.0, latitude, 0.0000001)
        assertEquals(4 * 100.0 / HeatmapGridCalculator.longitudeDegreeMeters(64.7), longitude, 0.0000001)
    }

    private fun point(sessionId: Long, latitude: Double, longitude: Double) =
        TrackPointHeatmapData(sessionId, latitude, longitude, 0L, 0f)
}
