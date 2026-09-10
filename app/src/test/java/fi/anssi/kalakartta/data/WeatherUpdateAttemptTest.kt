package fi.anssi.kalakartta.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherUpdateAttemptTest {
    @Test
    fun prioritizeCatchIdsPlacesLatestFailuresLast() {
        val attempts = mapOf(
            2L to WeatherUpdateAttempt(2L, 100L, succeeded = false, errorMessage = "timeout"),
            3L to WeatherUpdateAttempt(3L, 200L, succeeded = true),
            4L to WeatherUpdateAttempt(4L, 300L, succeeded = false, errorMessage = "no data")
        )

        val result = WeatherUpdateAttemptSelector.prioritizeCatchIds(
            catchIds = listOf(1L, 2L, 3L, 4L),
            attemptsByCatchId = attempts
        )

        assertEquals(listOf(1L, 3L, 2L, 4L), result)
    }

    @Test
    fun countFailedCatchIdsCountsOnlyLatestFailures() {
        val attempts = mapOf(
            1L to WeatherUpdateAttempt(1L, 100L, succeeded = false),
            2L to WeatherUpdateAttempt(2L, 200L, succeeded = true),
            3L to WeatherUpdateAttempt(3L, 300L, succeeded = false)
        )

        val result = WeatherUpdateAttemptSelector.countFailedCatchIds(
            catchIds = listOf(1L, 2L, 3L, 4L),
            attemptsByCatchId = attempts
        )

        assertEquals(2, result)
    }
}