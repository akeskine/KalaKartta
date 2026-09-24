package fi.anssi.kalakartta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeaLevelCatchTest {
    @Test
    fun calculatesSeaLevelTrendInCentimetersPerHour() {
        val samples = (0..11).map { hour ->
            val level = if (hour <= 5) 100L + hour else 105L + (hour - 5) * 3L
            SeaLevelSample(hour * 60 * 60 * 1000L, level)
        }
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = 6 * 60 * 60 * 1000L,
            seaLevelSamples = samples
        )

        assertEquals(23.0 / 11.0, fishCatch.calculateSeaLevelTrend()!!, 0.000001)
        assertEquals(2.0, fishCatch.calculateSeaLevelTurningTrend()!!, 0.000001)
    }

    @Test
    fun seaLevelTrendIsNullWhenSamplesShareATimestamp() {
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = 1L,
            seaLevelSamples = listOf(SeaLevelSample(1L, 20L), SeaLevelSample(1L, 21L))
        )

        assertNull(fishCatch.calculateSeaLevelTrend())
    }

    @Test
    fun seaLevelRoomConverterRoundTripsSamples() {
        val samples = listOf(SeaLevelSample(1_000L, -20L), SeaLevelSample(2_000L, 35L))
        val converter = SeaLevelConverter()

        assertEquals(samples, converter.toSeaLevelSampleList(converter.fromSeaLevelSampleList(samples)))
    }
}