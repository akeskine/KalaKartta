package fi.anssi.kalakartta.utils

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/**
 * Laskee auringon nousu- ja laskuajat.
 * Käyttää NOAA:n algoritmia (yksinkertaistettu versio).
 */
class SunService {

    fun getSunriseSunset(latitude: Double, longitude: Double, date: Calendar): Pair<Calendar, Calendar>? {
        val timeZone = date.timeZone
        
        // Käytetään vuoden päivää (1-366) Julian-päivän sijasta, 
        // koska käytetty algoritmi (Almanac for Computers) odottaa sitä.
        val n = date.get(Calendar.DAY_OF_YEAR).toDouble()
        
        val sunriseUtc = calculateSunTime(n, latitude, longitude, true) ?: return null
        val sunsetUtc = calculateSunTime(n, latitude, longitude, false) ?: return null

        val sunrise = calendarFromUtcMinutes(date, sunriseUtc, timeZone)
        val sunset = calendarFromUtcMinutes(date, sunsetUtc, timeZone)

        return Pair(sunrise, sunset)
    }

    private fun calculateSunTime(n: Double, lat: Double, lon: Double, isSunrise: Boolean): Double? {
        val zenith = 90.833 // Auringon keskipiste horisontissa + refraktio

        // 1. Lasketaan ajan murto-osa
        val longitudeHour = lon / 15.0
        val t = if (isSunrise) {
            n + ((6.0 - longitudeHour) / 24.0)
        } else {
            n + ((18.0 - longitudeHour) / 24.0)
        }

        // 2. Auringon keskianomalia
        val m = (0.9856 * t) - 3.289

        // 3. Auringon todellinen pituus
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        l = l % 360
        if (l < 0) l += 360

        // 4. Auringon rektaskensio
        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
        ra = ra % 360
        if (ra < 0) ra += 360

        // Säädetään oikeaan neljännekseen
        val lQuadrant = floor(l / 90.0) * 90
        val raQuadrant = floor(ra / 90.0) * 90
        ra += (lQuadrant - raQuadrant)

        // Muutetaan tunneiksi
        ra /= 15.0

        // 5. Auringon deklinaatio
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))

        // 6. Auringon paikallinen tuntikulma
        val cosH = (cos(Math.toRadians(zenith)) - (sinDec * sin(Math.toRadians(lat)))) / (cosDec * cos(Math.toRadians(lat)))

        if (cosH > 1) return null // Aurinko ei nouse
        if (cosH < -1) return null // Aurinko ei laske

        // 7. Lopullinen aika UTC-tunteina
        val h = if (isSunrise) {
            360 - Math.toDegrees(acos(cosH))
        } else {
            Math.toDegrees(acos(cosH))
        }
        val hT = h / 15.0

        val time = hT + ra - (0.06571 * t) - 6.622
        var utcTime = time - longitudeHour
        utcTime = utcTime % 24
        if (utcTime < 0) utcTime += 24

        return utcTime * 60.0 // Palautetaan minuutteina UTC-ajassa
    }

    private fun calendarFromUtcMinutes(baseDate: Calendar, utcMinutes: Double, tz: TimeZone): Calendar {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = baseDate.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MINUTE, utcMinutes.toInt())
        
        // Muunnos paikalliseen aikaan
        val result = Calendar.getInstance(tz)
        result.timeInMillis = cal.timeInMillis
        return result
    }
}
