package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PressureSample
import fi.anssi.kalakartta.utils.WeatherStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherUpdateTargetTest {
    private val hourMillis = 60 * 60 * 1000L

    @Test
    fun pressureHistoryIsCompleteWhenSampleIsInPlusFiveToSixHourWindow() {
        val caughtAt = 10 * hourMillis
        val now = caughtAt + 7 * hourMillis
        val fishCatch = fishCatchWithTrend(
            caughtAt,
            PressureSample(caughtAt + 5 * hourMillis, 1001.0)
        )

        assertFalse(needsPressureHistoryUpdate(fishCatch, now))
    }

    @Test
    fun pressureHistoryNeedsUpdateWhenPlusFiveToSixHourWindowHasNoSample() {
        val caughtAt = 10 * hourMillis
        val now = caughtAt + 7 * hourMillis
        val fishCatch = fishCatchWithTrend(
            caughtAt,
            PressureSample(caughtAt + 4 * hourMillis, 1001.0),
            PressureSample(caughtAt + 7 * hourMillis, 1002.0)
        )

        assertTrue(needsPressureHistoryUpdate(fishCatch, now))
    }

    @Test
    fun recentCatchDoesNotNeedFuturePressureHistoryYet() {
        val caughtAt = 10 * hourMillis
        val now = caughtAt + 6 * hourMillis
        val fishCatch = fishCatchWithTrend(caughtAt)

        assertFalse(needsPressureHistoryUpdate(fishCatch, now))
    }

    @Test
    fun invalidPressureSampleDoesNotCompleteHistoryWindow() {
        val caughtAt = 10 * hourMillis
        val now = caughtAt + 7 * hourMillis
        val fishCatch = fishCatchWithTrend(
            caughtAt,
            PressureSample(caughtAt + 5 * hourMillis, Double.NaN)
        )

        assertTrue(needsPressureHistoryUpdate(fishCatch, now))
    }

    @Test
    fun weatherStationIsShownWhenWeatherIsEnabledAndStationWasFound() {
        val state = weatherStationUiState(
            weatherEnabled = true,
            station = WeatherStation("123", "Helsinki", 60.0, 24.0)
        )

        assertTrue(state.visible)
        assertEquals("Sääasema: Helsinki", state.text)
    }

    @Test
    fun weatherStationIsHiddenWhenNoStationWasFound() {
        val state = weatherStationUiState(weatherEnabled = true, station = null)

        assertFalse(state.visible)
        assertEquals("", state.text)
    }

    @Test
    fun weatherStationIsHiddenWhenWeatherIsDisabled() {
        val state = weatherStationUiState(
            weatherEnabled = false,
            station = WeatherStation("123", "Helsinki", 60.0, 24.0)
        )

        assertFalse(state.visible)
        assertEquals("", state.text)
    }

    @Test
    fun forceRefreshWithoutStationLeavesWeatherStationHidden() {
        val state = weatherStationUiState(weatherEnabled = true, station = null)

        assertFalse(state.visible)
        assertEquals("", state.text)
    }

    private fun fishCatchWithTrend(caughtAt: Long, vararg samples: PressureSample): FishCatch {
        return FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = caughtAt,
            pressureTrend = 0.1,
            pressureSamples = samples.toList()
        )
    }
}