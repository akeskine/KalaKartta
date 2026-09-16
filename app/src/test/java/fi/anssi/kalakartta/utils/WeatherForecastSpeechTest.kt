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
}
