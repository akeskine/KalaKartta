package fi.anssi.kalakartta.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatsFormatterTest {

    @Test
    fun formatsDurationAsHoursAndMinutes() {
        assertEquals("2 h 5 min", SessionStatsFormatter.formatDuration(2 * 60 * 60_000L + 5 * 60_000L))
        assertEquals("0 h 0 min", SessionStatsFormatter.formatDuration(0L))
    }

    @Test
    fun notificationOmitsHoursWhenSessionIsShorterThanAnHour() {
        assertEquals("45 min", SessionStatsFormatter.formatNotificationDuration(45 * 60_000L))
        assertEquals("1 h 5 min", SessionStatsFormatter.formatNotificationDuration(65 * 60_000L))
    }

    @Test
    fun formatsDistanceInMetersOrKilometers() {
        assertEquals("999 m", SessionStatsFormatter.formatDistance(999.9))
        assertEquals("1,235 km", SessionStatsFormatter.formatDistance(1234.56))
    }

    @Test
    fun formatsSessionSummaryWithFinnishDecimalSeparator() {
        assertEquals(
            "Session kesto: 1 h 2 min\nKuljettu matka: 1,235 km,",
            SessionStatsFormatter.formatSummary(62 * 60_000L, 1234.56)
        )
    }
}
