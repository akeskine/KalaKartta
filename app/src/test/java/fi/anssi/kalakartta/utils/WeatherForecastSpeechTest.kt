package fi.anssi.kalakartta.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherForecastSpeechTest {

    @Test
    fun temperatureAndWindAreRoundedToNearestIntegers() {
        val speech = formatForecastSpeech(
            hours = 1,
            row = ForecastRow(
                time = 0L,
                parameters = mapOf(
                    "Temperature" to 2.7,
                    "WindSpeedMS" to 3.4
                )
            )
        )

        assertEquals("Sää yhden tunnin päästä: +3 astetta, 3 metriä sekunnissa.", speech)
    }

    @Test
    fun negativeTemperaturesKeepTheirMinusSignAndUseNormalRounding() {
        val speech = formatForecastSpeech(
            hours = 3,
            row = ForecastRow(
                time = 0L,
                parameters = mapOf(
                    "Temperature" to -2.5,
                    "WindSpeedMS" to 3.5
                )
            )
        )

        assertEquals("Sää kolmen tunnin päästä: -3 astetta, 4 metriä sekunnissa.", speech)
    }

    @Test
    fun compactSummaryFormatsConditionsWindAndTime() {
        val row = ForecastRow(
            time = 0L,
            parameters = mapOf(
                "Temperature" to 10.4,
                "TotalCloudCover" to 80.0,
                "Precipitation1h" to 5.0,
                "WindSpeedMS" to 3.0,
                "WindGust" to 8.0,
                "WindDirection" to 180.0
            )
        )

        val summary = formatForecastSummary(row, java.util.Locale.US, referenceTime = row.time)

        val expectedTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            .format(java.util.Date(row.time))
        assertEquals(expectedTime, summary?.time)
        assertEquals("+10°, pilvistä, runsasta sadetta, 3(8) m/s", summary?.conditions)
        assertEquals("+10°", summary?.temperatureText)
        assertEquals(80.0, summary?.cloudCoverPercent ?: Double.NaN, 0.0)
        assertEquals("pilvistä", summary?.cloudCoverDescription)
        assertEquals(5.0, summary?.precipitationMmPerHour ?: Double.NaN, 0.0)
        assertEquals("runsasta sadetta", summary?.precipitationDescription)
        assertEquals("3(8) m/s", summary?.windText)
        assertEquals(180f, summary?.windDirectionDegrees)
    }

    @Test
    fun compactSummaryMarksForecastOnFollowingDay() {
        val referenceCalendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 11)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val forecastCalendar = (referenceCalendar.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val summary = formatForecastSummary(
            ForecastRow(forecastCalendar.timeInMillis, mapOf("Temperature" to 10.0)),
            java.util.Locale.US,
            referenceCalendar.timeInMillis
        )

        assertEquals("12:00 (+1)", summary?.time)
    }
}
