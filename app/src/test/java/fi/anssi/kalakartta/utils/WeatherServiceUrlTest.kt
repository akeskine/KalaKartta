package fi.anssi.kalakartta.utils

import fi.anssi.kalakartta.data.PressureSample
import fi.anssi.kalakartta.data.SeaLevelSample
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
    fun pressureForecastRequestsTwelveHoursOfSurfacePressure() {
        val url = buildPressureForecastUrl(
            "https://opendata.fmi.fi/wfs?storedquery_id=fmi::forecast::harmonie::surface::point::simple",
            60.17,
            24.94,
            "2026-09-21T03:00:00Z",
            "2026-09-21T15:00:00Z"
        )

        assertTrue(url.contains("latlon=60.17,24.94"))
        assertTrue(url.contains("starttime=2026-09-21T03:00:00Z"))
        assertTrue(url.contains("endtime=2026-09-21T15:00:00Z"))
        assertTrue(url.endsWith("&timestep=60&parameters=Pressure"))
    }

    @Test
    fun pressureForecastSamplesAreFiniteAndLimitedToTheRequestedRange() {
        val startTime = 10L
        val endTime = 20L
        val samples = pressureForecastSamples(
            mapOf(
                9L to mapOf("Pressure" to 999.0),
                10L to mapOf("Pressure" to 1000.0),
                15L to mapOf("Pressure" to Double.NaN),
                20L to mapOf("Pressure" to 1002.0),
                21L to mapOf("Pressure" to 1003.0),
                18L to mapOf("Temperature" to 10.0)
            ),
            startTime,
            endTime
        )

        assertEquals(
            listOf(PressureSample(10L, 1000.0), PressureSample(20L, 1002.0)),
            samples
        )
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
    fun seaLevelForecastRequestsThePointAndHourlyTimeRange() {
        val url = buildSeaLevelForecastUrl(
            "https://opendata.fmi.fi/wfs?storedquery_id=fmi::forecast::sealevel::point::multipointcoverage",
            60.17,
            24.94,
            "2026-09-21T03:00:00Z",
            "2026-09-21T15:00:00Z"
        )

        assertTrue(url.contains("storedquery_id=fmi::forecast::sealevel::point::multipointcoverage"))
        assertTrue(url.contains("latlon=60.17,24.94"))
        assertTrue(url.contains("starttime=2026-09-21T03:00:00Z"))
        assertTrue(url.contains("endtime=2026-09-21T15:00:00Z"))
        assertTrue(url.endsWith("&timestep=60"))
    }

    @Test
    fun seaLevelForecastSamplesAreLimitedToTheRequestedRange() {
        val samples = seaLevelForecastSamples(
            listOf(
                SeaLevelObservation(60.0, 24.0, SeaLevelSample(9L, 9L)),
                SeaLevelObservation(60.0, 24.0, SeaLevelSample(10L, 10L)),
                SeaLevelObservation(60.0, 24.0, SeaLevelSample(20L, 20L)),
                SeaLevelObservation(60.0, 24.0, SeaLevelSample(21L, 21L))
            ),
            startTime = 10L,
            endTime = 20L
        )

        assertEquals(
            listOf(
                SeaLevelSample(10L, 10L),
                SeaLevelSample(20L, 20L)
            ),
            samples
        )
    }

    @Test
    fun forecastWindDataIsMergedFromTheNearestHarmonieRow() {
        val merged = mergeForecastWindData(
            forecastRows = mapOf(
                100L to mapOf("WindSpeedMS" to 4.0),
                200L to mapOf("WindSpeedMS" to 5.0)
            ),
            harmonieWindRows = mapOf(
                101L to mapOf(
                    "WindSpeedMS" to 6.0,
                    "WindGust" to 8.0,
                    "WindDirection" to 180.0
                ),
                199L to mapOf(
                    "WindSpeedMS" to 7.0,
                    "WindGust" to 9.0,
                    "WindDirection" to 200.0
                )
            )
        )

        assertEquals(6.0, merged[100L]?.get("WindSpeedMS"))
        assertEquals(8.0, merged[100L]?.get("WindGust"))
        assertEquals(180.0, merged[100L]?.get("WindDirection"))
        assertEquals(7.0, merged[200L]?.get("WindSpeedMS"))
        assertEquals(9.0, merged[200L]?.get("WindGust"))
        assertEquals(200.0, merged[200L]?.get("WindDirection"))
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
