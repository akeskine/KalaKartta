package fi.anssi.kalakartta.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherSettingsDialogTest {
    @Test
    fun formatsCurrentSeaLevelAndRemovesStationIdentifier() {
        assertEquals(
            "Meriveden korkeus Helsinki: +15 cm (MW)",
            formatSeaLevelTitle("123:Helsinki", 15L)
        )
    }

    @Test
    fun formatsNegativeSeaLevelWithoutAddingPositiveSign() {
        assertEquals(
            "Meriveden korkeus Turku: -4 cm (MW)",
            formatSeaLevelTitle("456:Turku", -4L)
        )
    }

    @Test
    fun showsPlaceholdersWhenCurrentLevelAndStationAreUnavailable() {
        assertEquals(
            "Meriveden korkeus: — cm (MW)",
            formatSeaLevelTitle("", null)
        )
    }
}