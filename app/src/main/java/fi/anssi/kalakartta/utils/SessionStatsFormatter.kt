package fi.anssi.kalakartta.utils

import java.util.Locale

/** Kalastussessioiden kesto- ja matkatekstien puhdas muotoilu. */
object SessionStatsFormatter {

    fun formatDuration(durationMs: Long): String {
        val totalMinutes = durationMs / MINUTE_MILLIS
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "$hours h $minutes min"
    }

    fun formatNotificationDuration(durationMs: Long): String {
        val totalMinutes = durationMs / MINUTE_MILLIS
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "$hours h $minutes min" else "$minutes min"
    }

    fun formatDistance(meters: Double): String = if (meters >= 1000) {
        String.format(Locale.US, "%.3f km", meters / 1000.0).replace('.', ',')
    } else {
        "${meters.toInt()} m"
    }

    fun formatSummary(durationMs: Long, distanceMeters: Double): String {
        val totalMinutes = durationMs / MINUTE_MILLIS
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val distanceKm = distanceMeters / 1000.0
        return String.format(
            Locale.US,
            "Session kesto: %d h %d min\nKuljettu matka: %.3f km.",
            hours,
            minutes,
            distanceKm
        ).replace('.', ',')
    }

    private const val MINUTE_MILLIS = 60_000L
}
