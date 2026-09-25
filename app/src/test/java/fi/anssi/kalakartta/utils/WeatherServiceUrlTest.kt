package fi.anssi.kalakartta.utils

import fi.anssi.kalakartta.data.PressureSample
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherServiceUrlTest {
    private val observationsUrl =
        "https://opendata.fmi.fi/wfs?request=getFeature&storedquery_id=fmi::observations::weather::simple&fmisid="

    @Test
    fun pressureHistoryUsesTimestepOnFirstRequest() {
        val url = buildPressureSamplesUrl(
            observationsUrl,
            "101520",
            "2001-08-24T09:30:11Z",
            "2001-08-24T21:30:11Z",
            includeTimestep = true
        )

        assertTrue(url.contains("fmisid=101520"))
        assertTrue(url.contains("starttime=2001-08-24T09:30:11Z"))
        assertTrue(url.contains("endtime=2001-08-24T21:30:11Z"))
        assertTrue(url.endsWith("&timestep=60"))
    }

    @Test
    fun pressureHistoryFallbackOmitsTimestep() {
        val url = buildPressureSamplesUrl(
            observationsUrl,
            "101520",
            "2001-08-24T09:30:11Z",
            "2001-08-24T21:30:11Z",
            includeTimestep = false
        )

        assertTrue(url.contains("fmisid=101520"))
        assertFalse(url.contains("timestep="))
    }

    @Test
    fun seaLevelHistoryRequestsAllMareographsAtHourlyIntervals() {
        val url = buildSeaLevelSamplesUrl(
            "https://opendata.fmi.fi/wfs?service=WFS&storedquery_id=fmi::observations::mareograph::instant::multipointcoverage",
            "2026-09-21T03:00:00Z",
            "2026-09-21T15:00:00Z"
        )

        assertTrue(url.contains("storedquery_id=fmi::observations::mareograph::instant::multipointcoverage"))
        assertTrue(url.contains("starttime=2026-09-21T03:00:00Z"))
        assertTrue(url.contains("endtime=2026-09-21T15:00:00Z"))
        assertTrue(url.endsWith("&timestep=60"))
    }

    @Test
    fun seaLevelCatchTimeRequestUsesTenMinuteIntervals() {
        val url = buildSeaLevelSamplesUrl(
            "https://opendata.fmi.fi/wfs?service=WFS&storedquery_id=fmi::observations::mareograph::instant::multipointcoverage",
            "2026-09-21T14:50:00Z",
            "2026-09-21T15:10:00Z",
            timestepMinutes = 10
        )

        assertTrue(url.endsWith("&timestep=10"))
    }

    @Test
    fun sparsePressureHistoryGetsAnExtrapolatedSampleInCompletionWindow() {
        val hourMillis = 60 * 60 * 1000L
        val caughtAt = 10 * hourMillis
        val startTime = caughtAt - 6 * hourMillis
        val endTime = caughtAt + 6 * hourMillis
        val samples = listOf(
            PressureSample(caughtAt - 6 * hourMillis, 1000.0),
            PressureSample(caughtAt - 3 * hourMillis, 1001.0),
            PressureSample(caughtAt, 1002.0),
            PressureSample(caughtAt + 3 * hourMillis, 1003.0)
        )

        val completed = extrapolatePressureSampleIntoCompletionWindow(samples, startTime, endTime)

        assertEquals(5, completed.size)
        assertEquals(caughtAt + 6 * hourMillis, completed.last().time)
        assertEquals(1004.0, completed.last().pressure, 0.000001)
    }

    @Test
    fun recentPressureHistoryIsNotExtrapolatedIntoTheFuture() {
        val hourMillis = 60 * 60 * 1000L
        val caughtAt = 10 * hourMillis
        val startTime = caughtAt - 6 * hourMillis
        val samples = listOf(
            PressureSample(caughtAt - 3 * hourMillis, 1001.0),
            PressureSample(caughtAt, 1002.0)
        )

        val result = extrapolatePressureSampleIntoCompletionWindow(
            samples,
            startTime,
            caughtAt + 4 * hourMillis
        )

        assertEquals(samples, result)
    }
}