package fi.anssi.kalakartta.ui

/** Heatmapin ja reittien laskentarajojen Androidista riippumaton valmistelu. */
object HeatmapLimitCalculator {

    const val ROUTE_REFERENCE_LATITUDE = 60.0
    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    data class RouteFadeSettings(val enabled: Boolean, val startDays: Int)

    fun gridSizeMeters(candidate: Double?, configured: Double): Double =
        (candidate ?: configured).coerceAtLeast(1.0)

    fun routeSessionStartLimit(
        nowMillis: Long,
        fadeEnabled: Boolean,
        fadeStartDays: Int
    ): Long {
        if (!fadeEnabled) return Long.MIN_VALUE

        val normalizedDays = fadeStartDays.coerceAtLeast(0).toLong()
        return nowMillis - (normalizedDays + 1L) * DAY_MILLIS
    }

    fun effectiveRouteFadeSettings(
        currentEnabled: Boolean,
        currentStartDays: Int,
        providedEnabled: Boolean?,
        providedStartDays: Int?
    ): RouteFadeSettings = RouteFadeSettings(
        enabled = providedEnabled ?: currentEnabled,
        startDays = providedStartDays ?: currentStartDays
    )

    fun removeTransitionsForRoutes(removeTransitionsMode: Int, removeTransitions: Boolean): Boolean =
        removeTransitionsMode == 1 && removeTransitions

    fun latitudeDegreeMeters(): Double = HeatmapGridCalculator.LATITUDE_DEGREE_METERS

    fun longitudeDegreeMeters(): Double =
        HeatmapGridCalculator.longitudeDegreeMeters(ROUTE_REFERENCE_LATITUDE)
}
