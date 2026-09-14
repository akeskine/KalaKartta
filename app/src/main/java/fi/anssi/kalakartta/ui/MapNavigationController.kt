package fi.anssi.kalakartta.ui

import android.content.Intent
import androidx.lifecycle.LifecycleCoroutineScope
import fi.anssi.kalakartta.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

/** Owns intent-driven map navigation and summary-range zooming. */
class MapNavigationController(
    private val map: MapView,
    private val database: AppDatabase,
    private val filterManager: FilterManager,
    private val scope: LifecycleCoroutineScope,
    private val closeSettings: () -> Unit,
    private val reloadMarkers: () -> Unit,
    private val updateFilterStatus: () -> Unit
) {
    fun handleIntent(intent: Intent) {
        if (!intent.getBooleanExtra("EXTRA_ZOOM_TO_SUMMARY", false)) return

        val start = intent.getLongExtra("EXTRA_START_TIME", -1L)
        val end = intent.getLongExtra("EXTRA_END_TIME", -1L)
        if (start != -1L && end != -1L) {
            closeSettings()
            filterManager.clearFilters()
            filterManager.saveFilters(FilterManager.Filters(startDate = start, endDate = end))
            reloadMarkers()
            updateFilterStatus()
            zoomToRangeOnMap(start, end)
        }
        intent.removeExtra("EXTRA_ZOOM_TO_SUMMARY")
    }

    private fun zoomToRangeOnMap(start: Long, end: Long) {
        scope.launch(Dispatchers.IO) {
            val points = database.trackPointDao().getPointsForHeatmapRange(start, end)
            val catches = database.fishCatchDao().getCatchesInRange(start, end)
            if (points.isEmpty() && catches.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                var minLat = Double.MAX_VALUE
                var maxLat = -Double.MAX_VALUE
                var minLon = Double.MAX_VALUE
                var maxLon = -Double.MAX_VALUE

                for (point in points) {
                    minLat = minOf(minLat, point.latitude)
                    maxLat = maxOf(maxLat, point.latitude)
                    minLon = minOf(minLon, point.longitude)
                    maxLon = maxOf(maxLon, point.longitude)
                }
                for (catchItem in catches) {
                    minLat = minOf(minLat, catchItem.latitude)
                    maxLat = maxOf(maxLat, catchItem.latitude)
                    minLon = minOf(minLon, catchItem.longitude)
                    maxLon = maxOf(maxLon, catchItem.longitude)
                }

                if (minLat == Double.MAX_VALUE) return@withContext

                val latDelta = maxLat - minLat
                val lonDelta = maxLon - minLon
                val margin = 0.1
                val finalMinLat = minLat - latDelta * margin
                val finalMaxLat = maxLat + latDelta * margin
                val finalMinLon = minLon - lonDelta * margin
                val finalMaxLon = maxLon + lonDelta * margin

                val centerLat = (finalMaxLat + finalMinLat) / 2.0
                val centerLon = (finalMaxLon + finalMinLon) / 2.0
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    centerLat,
                    finalMinLon,
                    centerLat,
                    finalMaxLon,
                    results
                )

                val finalBox = if (results[0] < 400.0) {
                    val metersPerDegreeLon = 111320.0 * kotlin.math.cos(Math.toRadians(centerLat))
                    val degreeDelta = (400.0 / metersPerDegreeLon) / 2.0
                    BoundingBox(
                        finalMaxLat,
                        centerLon + degreeDelta,
                        finalMinLat,
                        centerLon - degreeDelta
                    )
                } else {
                    BoundingBox(finalMaxLat, finalMaxLon, finalMinLat, finalMinLon)
                }

                map.zoomToBoundingBox(finalBox, true, 100)
            }
        }
    }
}
