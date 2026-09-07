package fi.anssi.kalakartta.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MoonCalculatorTest {

    private val calculator = MoonCalculator()

    @Test
    fun testMoonPhaseNewMoon() {
        // Uusi kuu: 16.8.2023 12:38 UTC
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2023, Calendar.AUGUST, 16, 12, 38)
        val phase = calculator.getMoonPhase(cal.timeInMillis)
        // Pitäisi olla lähellä 0.0 tai 1.0
        assertTrue("Vaiheen pitäisi olla lähellä nollaa: $phase", phase < 0.05 || phase > 0.95)
    }

    @Test
    fun testMoonPhaseFullMoon() {
        // Täysikuu: 31.8.2023 01:35 UTC
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2023, Calendar.AUGUST, 31, 1, 35)
        val phase = calculator.getMoonPhase(cal.timeInMillis)
        // Pitäisi olla lähellä 0.5
        assertEquals(0.5, phase, 0.05)
    }

    @Test
    fun testMoonPhaseFirstQuarter() {
        // Ensimmäinen neljännes n. 24.8.2023
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2023, Calendar.AUGUST, 24, 9, 57)
        val phase = calculator.getMoonPhase(cal.timeInMillis)
        assertEquals(0.25, phase, 0.05)
    }

    @Test
    fun testMoonAltitude() {
        // Testataan kuun korkeutta tietyllä hetkellä ja paikalla
        // Helsinki: 60.17, 24.94
        // 7.9.2026 18:00 UTC (Tämä on tulevaisuudessa, mutta algoritmi toimii)
        val lat = 60.17
        val lon = 24.94
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2026, Calendar.SEPTEMBER, 7, 18, 0)
        
        val altitude = calculator.getMoonAltitude(lat, lon, cal.timeInMillis)
        
        // Varmistetaan että saadaan jokin järkevä arvo väliltä -90 ja 90
        assertTrue("Korkeuden pitäisi olla välillä -90 ja 90: $altitude", altitude in -90.0..90.0)
    }
}
