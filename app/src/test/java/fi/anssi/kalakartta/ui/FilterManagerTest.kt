package fi.anssi.kalakartta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterManagerTest {

    @Test
    fun moonPhaseRangeIncludesEndpoints() {
        assertTrue(FilterManager.isValueInRange(0.25, 0.25, 0.5, wraps = true))
        assertTrue(FilterManager.isValueInRange(0.5, 0.25, 0.5, wraps = true))
        assertFalse(FilterManager.isValueInRange(0.1, 0.25, 0.5, wraps = true))
    }

    @Test
    fun moonPhaseRangeCanWrapAroundZero() {
        assertTrue(FilterManager.isValueInRange(0.75, 0.75, 0.25, wraps = true))
        assertTrue(FilterManager.isValueInRange(0.1, 0.75, 0.25, wraps = true))
        assertFalse(FilterManager.isValueInRange(0.5, 0.75, 0.25, wraps = true))
    }

    @Test
    fun moonAltitudeRangeDoesNotWrapAround() {
        assertTrue(FilterManager.isValueInRange(-30.0, -45.0, 45.0, wraps = false))
        assertTrue(FilterManager.isValueInRange(45.0, -45.0, 45.0, wraps = false))
        assertFalse(FilterManager.isValueInRange(60.0, -45.0, 45.0, wraps = false))
        assertFalse(FilterManager.isValueInRange(-60.0, -45.0, 45.0, wraps = false))
    }

    @Test
    fun rangeSupportsOneSidedBounds() {
        assertTrue(FilterManager.isValueInRange(0.8, 0.5, null, wraps = true))
        assertTrue(FilterManager.isValueInRange(-10.0, null, 20.0, wraps = false))
        assertFalse(FilterManager.isValueInRange(0.2, 0.5, null, wraps = true))
    }

    @Test
    fun pressureTrendUsesSymmetricThresholds() {
        val threshold = 0.10

        assertTrue(
            FilterManager.matchesPressureTrend(
                -0.11,
                FilterManager.PRESSURE_TREND_FALLING,
                threshold
            )
        )
        assertTrue(
            FilterManager.matchesPressureTrend(
                0.10,
                FilterManager.PRESSURE_TREND_FLAT,
                threshold
            )
        )
        assertTrue(
            FilterManager.matchesPressureTrend(
                0.11,
                FilterManager.PRESSURE_TREND_RISING,
                threshold
            )
        )
        assertFalse(
            FilterManager.matchesPressureTrend(
                -0.10,
                FilterManager.PRESSURE_TREND_FALLING,
                threshold
            )
        )
        assertFalse(
            FilterManager.matchesPressureTrend(
                null,
                FilterManager.PRESSURE_TREND_FLAT,
                threshold
            )
        )
    }

    @Test
    fun pressureTurningTrendUsesItsThreeWayClassification() {
        val threshold = 0.20

        assertTrue(
            FilterManager.matchesPressureTrend(
                -0.21,
                FilterManager.PRESSURE_TURNING_TREND_FALLING,
                threshold
            )
        )
        assertTrue(
            FilterManager.matchesPressureTrend(
                0.0,
                FilterManager.PRESSURE_TURNING_TREND_FLAT,
                threshold
            )
        )
        assertTrue(
            FilterManager.matchesPressureTrend(
                0.21,
                FilterManager.PRESSURE_TURNING_TREND_RISING,
                threshold
            )
        )
        assertFalse(
            FilterManager.matchesPressureTrend(
                0.19,
                FilterManager.PRESSURE_TURNING_TREND_RISING,
                threshold
            )
        )
    }

    @Test
    fun seaLevelTrendUsesItsThreeWayClassificationAndDefaultThreshold() {
        val threshold = FilterManager.DEFAULT_SEA_LEVEL_TREND_THRESHOLD.toDouble()

        assertEquals(1.0f, FilterManager.DEFAULT_SEA_LEVEL_TREND_THRESHOLD, 0.0f)
        assertTrue(
            FilterManager.matchesSeaLevelTrend(
                -threshold - 0.01,
                FilterManager.SEA_LEVEL_TREND_FALLING,
                threshold
            )
        )
        assertTrue(
            FilterManager.matchesSeaLevelTrend(
                threshold,
                FilterManager.SEA_LEVEL_TREND_FLAT,
                threshold
            )
        )
        assertTrue(
            FilterManager.matchesSeaLevelTrend(
                threshold + 0.01,
                FilterManager.SEA_LEVEL_TREND_RISING,
                threshold
            )
        )
        assertFalse(
            FilterManager.matchesSeaLevelTrend(
                -threshold,
                FilterManager.SEA_LEVEL_TREND_FALLING,
                threshold
            )
        )
        assertFalse(
            FilterManager.matchesSeaLevelTrend(
                null,
                FilterManager.SEA_LEVEL_TREND_FLAT,
                threshold
            )
        )
        assertTrue(FilterManager.matchesSeaLevelTrend(null, null, threshold))
    }

    @Test
    fun seaLevelTurningTrendUsesSeparateThresholdAndThreeWayClassification() {
        val threshold = FilterManager.DEFAULT_SEA_LEVEL_TURNING_TREND_THRESHOLD.toDouble()

        assertEquals(3.0f, FilterManager.DEFAULT_SEA_LEVEL_TURNING_TREND_THRESHOLD, 0.0f)
        assertTrue(
            FilterManager.matchesSeaLevelTrend(
                -threshold - 0.01,
                FilterManager.SEA_LEVEL_TURNING_TREND_FALLING,
                threshold
            )
        )
        assertTrue(
            FilterManager.matchesSeaLevelTrend(
                0.0,
                FilterManager.SEA_LEVEL_TURNING_TREND_FLAT,
                threshold
            )
        )
        assertTrue(
            FilterManager.matchesSeaLevelTrend(
                threshold + 0.01,
                FilterManager.SEA_LEVEL_TURNING_TREND_RISING,
                threshold
            )
        )
        assertFalse(
            FilterManager.matchesSeaLevelTrend(
                threshold,
                FilterManager.SEA_LEVEL_TURNING_TREND_RISING,
                threshold
            )
        )
        assertFalse(
            FilterManager.matchesSeaLevelTrend(
                null,
                FilterManager.SEA_LEVEL_TURNING_TREND_FALLING,
                threshold
            )
        )
    }

    @Test
    fun routeFiltersAreClearedWhenRouteFilteringIsDisabled() {
        val filters = FilterManager.Filters(
            startDate = 1L,
            endDate = 2L,
            latSouth = 60.0,
            latNorth = 61.0
        )

        assertEquals(FilterManager.Filters(), FilterManager.filtersForRoutes(filters, false))
        assertEquals(filters, FilterManager.filtersForRoutes(filters, true))
    }

    @Test
    fun heatmapFiltersAreClearedWhenHeatmapFilteringIsDisabled() {
        val filters = FilterManager.Filters(
            startDate = 1L,
            endDate = 2L,
            startTimeMinutes = 60,
            endTimeMinutes = 120,
            latSouth = 60.0,
            latNorth = 61.0
        )

        assertEquals(FilterManager.Filters(), FilterManager.filtersForHeatmap(filters, false))
        assertEquals(filters, FilterManager.filtersForHeatmap(filters, true))
    }
}
