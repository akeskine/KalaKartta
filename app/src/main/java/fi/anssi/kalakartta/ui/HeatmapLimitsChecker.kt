package fi.anssi.kalakartta.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos

/** Tarkistaa lämpökartan ja reittien laskennan piste- ja solmurajat. */
class HeatmapLimitsChecker(
    private val context: Context,
    private val db: AppDatabase,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    fun check(
        checkHeatmap: Boolean,
        checkRoutes: Boolean,
        newGridSize: Double? = null,
        providedFilters: FilterManager.Filters? = null,
        providedRemoveTransitions: Boolean? = null,
        providedMaxSpeed: Float? = null,
        onResult: (success: Boolean) -> Unit
    ) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val settingsStore = SettingsStore(prefs)
        val maxPoints = settingsStore.maxTrackPoints
        val maxCells = settingsStore.maxHeatmapCells

        val filters = providedFilters ?: FilterManager(context).getFilters()

        val heatmapFilterEnabled = if (providedFilters != null) {
            true
        } else {
            settingsStore.heatmapFilterEnabled
        }
        val routesFilterEnabled = if (providedFilters != null) {
            true
        } else {
            settingsStore.routesFilterEnabled
        }

        val removeTransitionsMode = settingsStore.heatmapRemoveTransitionsMode
        val baseRemoveTransitions = providedRemoveTransitions ?: settingsStore.heatmapRemoveTransitions
        val maxSpeed = providedMaxSpeed ?: settingsStore.heatmapMaxSpeed
        val routesFadeEnabled = settingsStore.routesFadeEnabled
        val routesFadeStartDays = settingsStore.routesFadeStartDays.coerceAtLeast(0).toLong()
        val routeSessionStartLimit = if (routesFadeEnabled) {
            val dayMillis = 1000L * 60 * 60 * 24
            System.currentTimeMillis() - (routesFadeStartDays + 1L) * dayMillis
        } else {
            Long.MIN_VALUE
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var error: String? = null

            if (checkRoutes) {
                val hasAreaFilter = filters.latNorth != null && filters.latSouth != null && filters.lonEast != null && filters.lonWest != null
                val hasRangeFilter = filters.startDate != null || filters.endDate != null

                val removeTransitionsRoutes = if (removeTransitionsMode == 1) baseRemoveTransitions else false

                val count = if (routesFilterEnabled) {
                    db.trackPointDao().getCountFilteredForRoutes(
                        routeSessionStartLimit,
                        hasRangeFilter, filters.startDate ?: 0L, filters.endDate ?: Long.MAX_VALUE,
                        hasAreaFilter, filters.latSouth ?: 0.0, filters.latNorth ?: 0.0, filters.lonWest ?: 0.0, filters.lonEast ?: 0.0,
                        removeTransitionsRoutes, maxSpeed
                    )
                } else {
                    db.trackPointDao().getCountFilteredForRoutes(
                        routeSessionStartLimit,
                        false, 0L, Long.MAX_VALUE,
                        false, 0.0, 0.0, 0.0, 0.0,
                        removeTransitionsRoutes, maxSpeed
                    )
                }

                if (count > maxPoints) {
                    error = context.getString(R.string.too_many_track_points, count, maxPoints)
                }
            }

            if (error == null && checkHeatmap) {
                val gridSize = newGridSize ?: settingsStore.heatmapGridSize.toDouble().coerceAtLeast(1.0)
                val latDegreeMeters = 111320.0
                val lonDegreeMeters = latDegreeMeters * cos(Math.toRadians(60.0))

                val hasAreaFilter = filters.latNorth != null && filters.latSouth != null && filters.lonEast != null && filters.lonWest != null
                val hasRangeFilter = filters.startDate != null || filters.endDate != null

                val count = if (heatmapFilterEnabled) {
                    db.trackPointDao().getHeatmapCellCountFiltered(
                        hasRangeFilter, filters.startDate ?: 0L, filters.endDate ?: Long.MAX_VALUE,
                        hasAreaFilter, filters.latSouth ?: 0.0, filters.latNorth ?: 0.0, filters.lonWest ?: 0.0, filters.lonEast ?: 0.0,
                        baseRemoveTransitions, maxSpeed,
                        latDegreeMeters, lonDegreeMeters, gridSize
                    )
                } else {
                    db.trackPointDao().getHeatmapCellCountFiltered(
                        false, 0L, Long.MAX_VALUE,
                        false, 0.0, 0.0, 0.0, 0.0,
                        baseRemoveTransitions, maxSpeed,
                        latDegreeMeters, lonDegreeMeters, gridSize
                    )
                }

                if (count > maxCells) {
                    error = context.getString(R.string.too_many_heatmap_cells, count, maxCells)
                }
            }

            withContext(Dispatchers.Main) {
                if (error != null) {
                    AlertDialog.Builder(context)
                        .setTitle(context.getString(R.string.warning))
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show()
                    onResult(false)
                } else {
                    onResult(true)
                }
            }
        }
    }
}
