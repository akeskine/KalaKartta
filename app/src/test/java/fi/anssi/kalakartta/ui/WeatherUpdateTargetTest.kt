package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PressureSample
import fi.anssi.kalakartta.data.SeaLevelSample
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
    fun missingSeaLevelMakesCatchAnUpdateTargetEvenWhenWeatherWasMarkedComplete() {
        val catch = fishCatchWithTrend(10 * hourMillis).copy(
            weatherDataCompleteTime = 1L,
            seaLevel = null,
            seaLevelSamples = emptyList(),
            pressureSamples = listOf(PressureSample(15 * hourMillis, 1001.0))
        )

        assertTrue(hasMissingSeaLevelData(catch))
        assertTrue(isMissingWeatherUpdateTarget(catch, catch.caughtAt!! + 12 * hourMillis))
    }

    @Test
    fun inlandCatchMarkedSeaLevelCompleteIsNotRepeatedlyUpdatedForSeaLevel() {
        val catch = fishCatchWithTrend(10 * hourMillis).copy(
            weatherDataCompleteTime = 1L,
            seaLevelDataCompleteTime = 20 * hourMillis,
            pressureSamples = listOf(PressureSample(15 * hourMillis, 1001.0))
        )

        assertFalse(hasMissingSeaLevelData(catch))
        assertFalse(needsSeaLevelHistoryUpdate(catch, 30 * hourMillis))
        assertFalse(isMissingWeatherUpdateTarget(catch, 30 * hourMillis))
    }

    @Test
    fun seaLevelHistoryNeedsUpdateWhenPlusFiveToSixHourWindowIsMissing() {
        val caughtAt = 10 * hourMillis
        val catch = fishCatchWithTrend(caughtAt).copy(
            seaLevel = 42L,
            seaLevelSamples = listOf(SeaLevelSample(caughtAt + 4 * hourMillis, 41L))
        )

        assertTrue(needsSeaLevelHistoryUpdate(catch, caughtAt + 7 * hourMillis))
    }

    @Test
    fun seaLevelSampleInCompletionWindowCompletesHistory() {
        val caughtAt = 10 * hourMillis
        val catch = fishCatchWithTrend(caughtAt).copy(
            seaLevel = 42L,
            seaLevelSamples = listOf(SeaLevelSample(caughtAt + 5 * hourMillis, 45L))
        )

        assertFalse(needsSeaLevelHistoryUpdate(catch, caughtAt + 7 * hourMillis))
    }


    @Test
    fun weatherUpdateStartButtonIsHiddenWhenThereAreNoTargets() {
        assertFalse(shouldShowWeatherUpdateStartButton(0))
    }

    @Test
    fun weatherUpdateStartButtonIsShownWhenTargetsExist() {
        assertTrue(shouldShowWeatherUpdateStartButton(1))
    }

    @Test
    fun automaticWeatherUpdateIsDisabledByDefaultSetting() {
        assertFalse(shouldRunAutomaticWeatherUpdate(false, 25L * hourMillis, 0L, 24))
    }

    @Test
    fun automaticWeatherUpdateRunsOnlyAfterConfiguredInterval() {
        val lastUpdate = 100L
        assertFalse(shouldRunAutomaticWeatherUpdate(true, lastUpdate + 24 * hourMillis, lastUpdate, 24))
        assertTrue(shouldRunAutomaticWeatherUpdate(true, lastUpdate + 24 * hourMillis + 1, lastUpdate, 24))
    }

    @Test
    fun automaticWeatherUpdateNotificationIsHiddenWhenNothingWasAttempted() {
        assertFalse(
            shouldShowMissingWeatherUpdateNotification(
                MissingWeatherUpdateResult(successful = 0, failed = 0, noChanges = 0, attempted = 0)
            )
        )
        assertTrue(
            shouldShowMissingWeatherUpdateNotification(
                MissingWeatherUpdateResult(successful = 1, failed = 0, noChanges = 0, attempted = 1)
            )
        )
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
