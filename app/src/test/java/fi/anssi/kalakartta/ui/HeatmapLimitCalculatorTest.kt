package fi.anssi.kalakartta.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatmapLimitCalculatorTest {

    @Test
    fun gridSizeHasAOneMeterLowerBound() {
        assertEquals(1.0, HeatmapLimitCalculator.gridSizeMeters(null, 0.0), 0.0)
        assertEquals(1.0, HeatmapLimitCalculator.gridSizeMeters(-10.0, 300.0), 0.0)
        assertEquals(50.0, HeatmapLimitCalculator.gridSizeMeters(50.0, 300.0), 0.0)
    }

    @Test
    fun routeLimitIsDisabledWhenFadeIsDisabled() {
        assertEquals(
            Long.MIN_VALUE,
            HeatmapLimitCalculator.routeSessionStartLimit(1_000_000L, false, 365)
        )
    }

    @Test
    fun routeLimitIncludesTheExistingOneDaySafetyMargin() {
        val now = 1_000_000_000L

        assertEquals(
            now - 366L * HeatmapLimitCalculator.DAY_MILLIS,
            HeatmapLimitCalculator.routeSessionStartLimit(now, true, 365)
        )
    }

    @Test
    fun negativeFadeDaysAreNormalizedToZero() {
        val now = 1_000_000_000L

        assertEquals(
            now - HeatmapLimitCalculator.DAY_MILLIS,
            HeatmapLimitCalculator.routeSessionStartLimit(now, true, -10)
        )
    }

    @Test
    fun routeTransitionFilteringRequiresModeOneAndEnabledSetting() {
        assertEquals(true, HeatmapLimitCalculator.removeTransitionsForRoutes(1, true))
        assertEquals(false, HeatmapLimitCalculator.removeTransitionsForRoutes(1, false))
        assertEquals(false, HeatmapLimitCalculator.removeTransitionsForRoutes(0, true))
    }

    @Test
    fun limitQueriesUseTheDocumentedStableReferenceLatitude() {
        assertEquals(60.0, HeatmapLimitCalculator.ROUTE_REFERENCE_LATITUDE, 0.0)
        assertEquals(
            HeatmapGridCalculator.longitudeDegreeMeters(60.0),
            HeatmapLimitCalculator.longitudeDegreeMeters(),
            0.0001
        )
    }
}
