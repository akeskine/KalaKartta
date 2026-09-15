package fi.anssi.kalakartta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryMapViewportCalculatorTest {

    @Test
    fun keepsAllNearbyPointsAndAddsMargin() {
        val bounds = SummaryMapViewportCalculator.calculate(
            listOf(
                SummaryMapViewportCalculator.Point(60.0, 24.0),
                SummaryMapViewportCalculator.Point(60.1, 24.2)
            )
        )

        assertNotNull(bounds)
        assertTrue(bounds!!.north > 60.1)
        assertTrue(bounds.south < 60.0)
        assertTrue(bounds.east > 24.2)
        assertTrue(bounds.west < 24.0)
    }

    @Test
    fun limitsWideDataToTheDensestLatitudeBand() {
        val points = buildList {
            repeat(8) { add(SummaryMapViewportCalculator.Point(60.0 + it * 0.05, 24.0)) }
            add(SummaryMapViewportCalculator.Point(10.0, 10.0))
            add(SummaryMapViewportCalculator.Point(20.0, 20.0))
        }

        val bounds = SummaryMapViewportCalculator.calculate(points)

        assertNotNull(bounds)
        assertEquals(60.0, bounds!!.south, 1.0)
        assertEquals(60.35, bounds.north, 1.0)
        assertTrue(bounds.north - bounds.south < 10.0)
    }

    @Test
    fun limitsGlobalPointsToTheDensestNearbyLongitudeBand() {
        val points = buildList {
            repeat(8) { add(SummaryMapViewportCalculator.Point(60.0, 24.0 + it * 0.5)) }
            add(SummaryMapViewportCalculator.Point(60.0, -120.0))
            add(SummaryMapViewportCalculator.Point(60.0, 140.0))
        }

        val bounds = SummaryMapViewportCalculator.calculate(points)

        assertNotNull(bounds)
        assertTrue(bounds!!.west > 0.0)
        assertTrue(bounds.east < 40.0)
    }
}
