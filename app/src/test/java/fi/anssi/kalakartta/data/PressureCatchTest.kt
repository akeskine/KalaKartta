package fi.anssi.kalakartta.data

import org.junit.Assert.*
import org.junit.Test

class PressureCatchTest {

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
}
