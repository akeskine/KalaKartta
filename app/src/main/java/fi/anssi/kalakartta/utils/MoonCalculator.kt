package fi.anssi.kalakartta.utils

import kotlin.math.*

/**
 * Laskee kuun vaiheen ja korkeuden.
 */
class MoonCalculator {

    /**
     * Laskee kuun vaiheen väliltä 0.0 ... 1.0.
     * 0.0 = uusikuu, 0.25 = ensimmäinen neljännes, 0.5 = täysikuu, 0.75 = viimeinen neljännes.
     */
    fun getMoonPhase(timeInMillis: Long): Double {
        // Tunnettu uusi kuu: 2000-01-06 18:14 UTC
        val knownNewMoon = 947182440000L
        val synodicMonth = 29.530588853 * 24 * 60 * 60 * 1000.0 // millisekunteina
        
        val diff = timeInMillis - knownNewMoon
        var phase = (diff % synodicMonth) / synodicMonth
        if (phase < 0) phase += 1.0
        return phase
    }

    /**
     * Laskee kuun korkeuden (altitude) väliltä -90.0 ... +90.0 astetta.
     * @param lat Leveysaste
     * @param lon Pituusaste
     * @param timeInMillis Aika millisekunteina
     * @return Kuun korkeus asteina
     */
    fun getMoonAltitude(lat: Double, lon: Double, timeInMillis: Long): Double {
        // Päivät J2000.0 epookista (2000-01-01 12:00 UTC)
        val d = (timeInMillis - 946728000000L) / (24.0 * 60 * 60 * 1000.0)
        
        // Kuun radan elementit (karkeita approksimaatioita)
        val L = normalizeAngle(218.316 + 13.176396 * d) // Kuun keskipituus
        val M = normalizeAngle(134.963 + 13.064993 * d) // Kuun keskianomalia
        val F = normalizeAngle(93.272 + 13.229350 * d)  // Kuun pituusnoodista
        
        // Kuun pituus- ja leveysaste ekliptikalla
        val lonMoon = L + 6.289 * sin(Math.toRadians(M))
        val latMoon = 5.128 * sin(Math.toRadians(F))
        
        // Ekliptikan kaltevuus
        val ecl = 23.439 - 0.0000004 * d
        
        // Muunnos ekvatoriaalisiin koordinaatteihin (rektaskensio ja deklinaatio)
        val ra = Math.toDegrees(atan2(
            sin(Math.toRadians(lonMoon)) * cos(Math.toRadians(ecl)) - tan(Math.toRadians(latMoon)) * sin(Math.toRadians(ecl)),
            cos(Math.toRadians(lonMoon))
        ))
        val dec = Math.toDegrees(asin(
            sin(Math.toRadians(latMoon)) * cos(Math.toRadians(ecl)) + cos(Math.toRadians(latMoon)) * sin(Math.toRadians(ecl)) * sin(Math.toRadians(lonMoon))
        ))
        
        // Paikallinen tähtiaika (sidereal time) asteina
        val siderealTime = normalizeAngle(280.46061837 + 360.98564736629 * d + lon)
        
        // Tuntikulma (Hour Angle)
        val hourAngle = normalizeAngle(siderealTime - ra)
        
        // Muunnos horisonttijärjestelmään (korkeus)
        val alt = asin(
            sin(Math.toRadians(lat)) * sin(Math.toRadians(dec)) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(dec)) * cos(Math.toRadians(hourAngle))
        )
        
        return Math.toDegrees(alt)
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle % 360
        if (a < 0) a += 360
        return a
    }
}
