package fi.anssi.kalakartta.data

import org.junit.Assert.*
import org.junit.Test

class PressureCatchTest {
    private val hourMillis = 60 * 60 * 1000L

    @Test
    fun testPressureConverter() {
        val converter = PressureConverter()
        val samples = listOf(
            PressureSample(1000L, 1013.25),
            PressureSample(2000L, 1012.0)
        )
        
        val json = converter.fromPressureSampleList(samples)
        val result = converter.toPressureSampleList(json)
        
        assertEquals(samples.size, result.size)
        assertEquals(samples[0].time, result[0].time)
        assertEquals(samples[0].pressure, result[0].pressure, 0.001)
        assertEquals(samples[1].time, result[1].time)
        assertEquals(samples[1].pressure, result[1].pressure, 0.001)
    }

    @Test
    fun testPressureConverterEmpty() {
        val converter = PressureConverter()
        val samples = emptyList<PressureSample>()
        
        val json = converter.fromPressureSampleList(samples)
        val result = converter.toPressureSampleList(json)
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun testPressureTrendCalculation() {
        val samples = listOf(
            PressureSample(0L, 1000.0), // 0 hours
            PressureSample(3600000L, 1001.0) // 1 hour
        )
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = 0L,
            pressureSamples = samples
        )
        
        val trend = fishCatch.calculatePressureTrend()
        assertNotNull(trend)
        assertEquals(1.0, trend!!, 0.001) // 1 hPa / 1 h = 1.0
    }

    @Test
    fun testPressureTrendCalculationFalling() {
        val samples = listOf(
            PressureSample(0L, 1000.0),
            PressureSample(7200000L, 998.0) // 2 hours
        )
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = 0L,
            pressureSamples = samples
        )
        
        val trend = fishCatch.calculatePressureTrend()
        assertNotNull(trend)
        assertEquals(-1.0, trend!!, 0.001) // (998 - 1000) / 2 = -1.0
    }

    @Test
    fun testPressureTrendInsufficientSamples() {
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = 0L,
            pressureSamples = listOf(PressureSample(0L, 1000.0))
        )
        assertNull(fishCatch.calculatePressureTrend())
    }

    @Test
    fun testPressureTrendReturnsNullWhenSamplesHaveSameTimestamp() {
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = 0L,
            pressureSamples = listOf(
                PressureSample(1000L, 1000.0),
                PressureSample(1000L, 1001.0)
            )
        )

        assertNull(fishCatch.calculatePressureTrend())
    }

    @Test
    fun testPressureTurningTrendIsPositiveWhenPressureRisesFasterAfterCatch() {
        val caughtAt = 12 * hourMillis
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = caughtAt,
            pressureSamples = listOf(
                PressureSample(caughtAt - 11 * hourMillis, 1000.0),
                PressureSample(caughtAt - 9 * hourMillis, 1000.2),
                PressureSample(caughtAt - 7 * hourMillis, 1000.4),
                PressureSample(caughtAt - 5 * hourMillis, 1000.6),
                PressureSample(caughtAt - 3 * hourMillis, 1000.8),
                PressureSample(caughtAt - hourMillis, 1001.0),
                PressureSample(caughtAt + hourMillis, 1001.4),
                PressureSample(caughtAt + 3 * hourMillis, 1001.8),
                PressureSample(caughtAt + 5 * hourMillis, 1002.2),
                PressureSample(caughtAt + 7 * hourMillis, 1002.6),
                PressureSample(caughtAt + 9 * hourMillis, 1003.0),
                PressureSample(caughtAt + 11 * hourMillis, 1003.4)
            )
        )

        assertEquals(0.1, fishCatch.calculatePressureTurningTrend()!!, 0.001)
    }

    @Test
    fun testPressureTurningTrendUsesFirstAndLastSampleOfEachHalf() {
        val caughtAt = 12 * hourMillis
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = caughtAt,
            pressureSamples = listOf(
                PressureSample(caughtAt - 11 * hourMillis, 1000.0),
                PressureSample(caughtAt - 9 * hourMillis, 1008.0),
                PressureSample(caughtAt - 7 * hourMillis, 1007.0),
                PressureSample(caughtAt - 5 * hourMillis, 1002.0),
                PressureSample(caughtAt - 3 * hourMillis, 1004.0),
                PressureSample(caughtAt - hourMillis, 1006.0),
                PressureSample(caughtAt + hourMillis, 1008.0),
                PressureSample(caughtAt + 3 * hourMillis, 1012.0),
                PressureSample(caughtAt + 5 * hourMillis, 1016.0),
                PressureSample(caughtAt + 7 * hourMillis, 1012.0),
                PressureSample(caughtAt + 9 * hourMillis, 1014.0),
                PressureSample(caughtAt + 11 * hourMillis, 1018.0)
            )
        )

        val turningTrend = fishCatch.calculatePressureTurningTrend()!!

        assertEquals(0.4, turningTrend, 0.001)
    }

    @Test
    fun testPressureTurningTrendIsNegativeWhenPressureRisesLessAfterCatch() {
        val caughtAt = 12 * hourMillis
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = caughtAt,
            pressureSamples = listOf(
                PressureSample(caughtAt - 11 * hourMillis, 1000.0),
                PressureSample(caughtAt - 9 * hourMillis, 1000.2),
                PressureSample(caughtAt - 7 * hourMillis, 1000.4),
                PressureSample(caughtAt - 5 * hourMillis, 1000.6),
                PressureSample(caughtAt - 3 * hourMillis, 1000.8),
                PressureSample(caughtAt - hourMillis, 1001.0),
                PressureSample(caughtAt + hourMillis, 1000.6),
                PressureSample(caughtAt + 3 * hourMillis, 1000.2),
                PressureSample(caughtAt + 5 * hourMillis, 999.8),
                PressureSample(caughtAt + 7 * hourMillis, 999.4),
                PressureSample(caughtAt + 9 * hourMillis, 999.0),
                PressureSample(caughtAt + 11 * hourMillis, 998.6)
            )
        )

        assertEquals(-0.3, fishCatch.calculatePressureTurningTrend()!!, 0.001)
    }

    @Test
    fun testPressureTurningTrendIsZeroWhenHalfTrendsAreEqual() {
        val caughtAt = 12 * hourMillis
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = caughtAt,
            pressureSamples = listOf(
                PressureSample(caughtAt - 11 * hourMillis, 1000.0),
                PressureSample(caughtAt - 9 * hourMillis, 1000.2),
                PressureSample(caughtAt - 7 * hourMillis, 1000.4),
                PressureSample(caughtAt - 5 * hourMillis, 1000.6),
                PressureSample(caughtAt - 3 * hourMillis, 1000.8),
                PressureSample(caughtAt - hourMillis, 1001.0),
                PressureSample(caughtAt + hourMillis, 1001.2),
                PressureSample(caughtAt + 3 * hourMillis, 1001.4),
                PressureSample(caughtAt + 5 * hourMillis, 1001.6),
                PressureSample(caughtAt + 7 * hourMillis, 1001.8),
                PressureSample(caughtAt + 9 * hourMillis, 1002.0),
                PressureSample(caughtAt + 11 * hourMillis, 1002.2)
            )
        )

        assertEquals(0.0, fishCatch.calculatePressureTurningTrend()!!, 0.001)
    }

    @Test
    fun testPressureTurningTrendIsNullWhenEitherHalfHasTooFewSamples() {
        val caughtAt = 12 * hourMillis
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = caughtAt,
            pressureSamples = listOf(
                PressureSample(caughtAt - hourMillis, 1000.0),
                PressureSample(caughtAt + hourMillis, 1001.0)
            )
        )

        assertNull(fishCatch.calculatePressureTurningTrend())
    }
}
