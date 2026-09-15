package fi.anssi.kalakartta.ui

import android.content.Intent
import androidx.lifecycle.LifecycleCoroutineScope
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
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

        val start = intent.getLongExtraOrNull("EXTRA_START_TIME")
        val end = intent.getLongExtraOrNull("EXTRA_END_TIME")
        val fisherman = intent.getStringExtra("EXTRA_SUMMARY_FISHERMAN")

        closeSettings()
        filterManager.clearFilters()
        filterManager.saveFilters(
            FilterManager.Filters(
                startDate = start,
                endDate = end,
                fisherman = fisherman
            )
        )
        reloadMarkers()
        updateFilterStatus()
        zoomToSummaryOnMap(start, end, fisherman)
        intent.removeExtra("EXTRA_ZOOM_TO_SUMMARY")
    }

    private fun zoomToSummaryOnMap(start: Long?, end: Long?, fisherman: String?) {
        scope.launch(Dispatchers.IO) {
            val catches = database.fishCatchDao().getAll().filter { catchItem ->
                val caughtAt = catchItem.caughtAt ?: return@filter false
                val inRange = (start == null || caughtAt >= start) &&
                    (end == null || caughtAt <= end)
                inRange &&
                    catchItem.species != "UNKNOWN" &&
                    (catchItem.eventType == null || catchItem.eventType == FishCatch.CAUGHT_FISH) &&
                    (fisherman == null || catchItem.fisherman.equals(fisherman, ignoreCase = true))
            }

            // Jos yhteenvedossa ei ole kalapisteitä, reitti on edelleen hyödyllinen
            // kohdistuksen kohde esimerkiksi pelkän kalastussession yhteenvetoon.
            val fallbackTrackPoints = if (catches.isEmpty()) {
                if (start == null && end == null) {
                    database.trackPointDao().getAllForHeatmap()
                } else {
                    database.trackPointDao().getPointsForHeatmapRange(
                        start ?: Long.MIN_VALUE,
                        end ?: Long.MAX_VALUE
                    )
                }
            } else {
                emptyList()
            }

            val coordinates = if (catches.isNotEmpty()) {
                catches.map { SummaryMapViewportCalculator.Point(it.latitude, it.longitude) }
            } else {
                fallbackTrackPoints.map {
                    SummaryMapViewportCalculator.Point(it.latitude, it.longitude)
                }
            }
            val bounds = SummaryMapViewportCalculator.calculate(coordinates) ?: return@launch

            withContext(Dispatchers.Main) {
                map.zoomToBoundingBox(
                    BoundingBox(bounds.north, bounds.east, bounds.south, bounds.west),
                    true,
                    100
                )
            }
        }
    }

    private fun Intent.getLongExtraOrNull(name: String): Long? =
        if (hasExtra(name)) getLongExtra(name, 0L) else null
}
