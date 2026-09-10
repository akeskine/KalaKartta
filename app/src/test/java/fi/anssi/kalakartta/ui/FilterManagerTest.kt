package fi.anssi.kalakartta.ui

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
}