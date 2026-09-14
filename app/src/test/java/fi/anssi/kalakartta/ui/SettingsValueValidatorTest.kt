package fi.anssi.kalakartta.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsValueValidatorTest {

    @Test
    fun positiveIntUsesValueOnlyWhenItIsPositive() {
        assertEquals(12, SettingsValueValidator.positiveIntOrDefault(" 12 ", 50))
        assertEquals(50, SettingsValueValidator.positiveIntOrDefault("0", 50))
        assertEquals(50, SettingsValueValidator.positiveIntOrDefault("-1", 50))
        assertEquals(50, SettingsValueValidator.positiveIntOrDefault("", 50))
    }

    @Test
    fun nonNegativeIntRejectsNegativeValuesAndInvalidInput() {
        assertEquals(0, SettingsValueValidator.nonNegativeIntOrDefault("0", 30))
        assertEquals(30, SettingsValueValidator.nonNegativeIntOrDefault("-1", 30))
        assertEquals(30, SettingsValueValidator.nonNegativeIntOrDefault("abc", 30))
    }

    @Test
    fun floatingPointValuesAcceptFinnishDecimalSeparator() {
        assertEquals(1.25f, SettingsValueValidator.positiveFloatOrDefault("1,25", 10f), 0.0001f)
        assertEquals(10f, SettingsValueValidator.positiveFloatOrDefault("0", 10f), 0.0001f)
        assertEquals(10f, SettingsValueValidator.positiveFloatOrDefault("NaN", 10f), 0.0001f)
        assertEquals(10f, SettingsValueValidator.positiveFloatOrDefault("Infinity", 10f), 0.0001f)
    }

    @Test
    fun latitudeMustBeWithinTheSupportedRange() {
        assertEquals(64.7f, SettingsValueValidator.latitudeOrDefault("64,7", 10f), 0.0001f)
        assertEquals(0f, SettingsValueValidator.latitudeOrDefault("0", 10f), 0.0001f)
        assertEquals(180f, SettingsValueValidator.latitudeOrDefault("180", 10f), 0.0001f)
        assertEquals(10f, SettingsValueValidator.latitudeOrDefault("180.1", 10f), 0.0001f)
        assertEquals(10f, SettingsValueValidator.latitudeOrDefault("-1", 10f), 0.0001f)
    }
}
