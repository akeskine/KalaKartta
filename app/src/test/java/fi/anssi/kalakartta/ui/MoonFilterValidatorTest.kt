package fi.anssi.kalakartta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonFilterValidatorTest {

    @Test
    fun emptyPairsAreValid() {
        val result = MoonFilterValidator.validate(null, "", null, "")

        assertTrue(result.isValid)
    }

    @Test
    fun moonPhaseAcceptsValuesInsideItsRange() {
        val result = MoonFilterValidator.validate("0.25", "0.75", null, null)

        assertTrue(result.isValid)
    }

    @Test
    fun oneMissingBoundRequiresTheOtherBound() {
        val result = MoonFilterValidator.validate("0.25", "", null, null)

        assertFalse(result.isValid)
        assertEquals(MoonFilterValidator.Error.BOTH_REQUIRED, result.moonPhase.error)
    }

    @Test
    fun phaseAndAltitudeRangesAreValidated() {
        val phaseResult = MoonFilterValidator.validate("-0.1", "0.5", null, null)
        val altitudeResult = MoonFilterValidator.validate(null, null, "-91", "45")

        assertEquals(MoonFilterValidator.Error.RANGE, phaseResult.moonPhase.error)
        assertEquals(MoonFilterValidator.Error.RANGE, altitudeResult.moonAltitude.error)
    }

    @Test
    fun altitudeBoundsMustBeAscending() {
        val result = MoonFilterValidator.validate(null, null, "20", "-20")

        assertFalse(result.isValid)
        assertEquals(MoonFilterValidator.Error.ORDER, result.moonAltitude.error)
    }

    @Test
    fun nonFiniteValuesAreRejected() {
        val result = MoonFilterValidator.validate("NaN", "0.5", "-10", "Infinity")

        assertFalse(result.isValid)
        assertEquals(MoonFilterValidator.Error.RANGE, result.moonPhase.error)
        assertEquals(MoonFilterValidator.Error.RANGE, result.moonAltitude.error)
    }
}
