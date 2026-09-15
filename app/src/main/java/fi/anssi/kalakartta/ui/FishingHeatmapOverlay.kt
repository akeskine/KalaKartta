package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.TrackPoint
import fi.anssi.kalakartta.data.TrackPointHeatmapData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.util.BoundingBox

class FishingHeatmapOverlay(private val context: Context, private val db: AppDatabase, private val mapView: MapView) : Overlay() {

    private companion object {
        const val HEATMAP_BLUR_RADIUS_FRACTION = 0.85f
        const val MIN_HEATMAP_BLUR_RADIUS = 2f
        const val MAX_HEATMAP_BLUR_RADIUS = 96f
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private val settingsStore = SettingsStore(context.getSharedPreferences("settings", Context.MODE_PRIVATE))
    private var dataJob: Job? = null
    
    // Ruudun koko metreinä
    private var gridSizeMeters = 100.0
    
    // Ruudutus: Map<Pair<RuutuX, RuutuY>, VierailuKerrat>
    private var heatmapData = mapOf<Pair<Int, Int>, Int>()
    
    private val paint = Paint().apply {
        style = Paint.Style.FILL
    }
    
    private var minPoints = SettingsDefaults.HEATMAP_MIN_POINTS
    private var maxPoints = SettingsDefaults.HEATMAP_MAX_POINTS
    private var autoConfigure = SettingsDefaults.HEATMAP_AUTO_CONFIGURE
    private var baseColor = Color.RED
    private var heatmapEnabled = SettingsDefaults.HEATMAP_ENABLED
    private var routesEnabled = SettingsDefaults.FISHING_ROUTES_ENABLED
    private var heatmapFilterEnabled = SettingsDefaults.HEATMAP_FILTER_ENABLED
    private var routesFilterEnabled = SettingsDefaults.ROUTES_FILTER_ENABLED
    private var routesFadeEnabled = true
    private var routesFadeStartLimitDays = SettingsDefaults.ROUTES_FADE_START_DAYS
    private var routesFadeFullLimitDays = SettingsDefaults.ROUTES_FADE_FULL_DAYS
    private var calculationMethod = ""
    private var removeTransitions = SettingsDefaults.HEATMAP_REMOVE_TRANSITIONS
    private var removeTransitionsMode = SettingsDefaults.HEATMAP_REMOVE_TRANSITIONS_MODE
    private var maxSpeed = SettingsDefaults.HEATMAP_MAX_SPEED
    private var minZoomLevel = SettingsDefaults.HEATMAP_MIN_ZOOM.toDouble()
    private var maxTrackPoints = SettingsDefaults.MAX_TRACK_POINTS
    private var referenceLatitude = SettingsDefaults.HEATMAP_REFERENCE_LATITUDE.toDouble()
    private var fadeCellEdges = SettingsDefaults.HEATMAP_FADE_CELL_EDGES

    private data class HeatmapCell(val bounds: RectF, val color: Int)

    private val visibleHeatmapCells = mutableListOf<HeatmapCell>()
    // Kept as Any so older Android versions do not resolve API 31 classes while loading the overlay.
    private var heatmapBlurRenderNode: Any? = null
    private var heatmapBlurEffect: Any? = null
    private var heatmapBlurRadius = 0f

    private data class RouteWithBounds(
        val points: List<TrackPointHeatmapData>,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    private var routeData = listOf<RouteWithBounds>()

    init {
        refreshSettings()
        refreshData()
    }

    fun refreshSettings(): Boolean {
        val oldGridSize = gridSizeMeters
        val oldHeatmapEnabled = heatmapEnabled
        val oldRoutesEnabled = routesEnabled
        val oldHeatmapFilterEnabled = heatmapFilterEnabled
        val oldRoutesFilterEnabled = routesFilterEnabled
        val oldAutoConfigure = autoConfigure
        val oldRoutesFadeEnabled = routesFadeEnabled
        val oldRoutesFadeStartLimitDays = routesFadeStartLimitDays
        val oldRoutesFadeFullLimitDays = routesFadeFullLimitDays
        val oldFadeCellEdges = fadeCellEdges

        gridSizeMeters = settingsStore.heatmapGridSize.toDouble().coerceAtLeast(1.0)
        autoConfigure = settingsStore.heatmapAutoConfigure
        minPoints = if (autoConfigure) {
            SettingsDefaults.HEATMAP_MIN_POINTS
        } else {
            settingsStore.heatmapMinPoints.coerceAtLeast(1)
        }
        maxPoints = settingsStore.heatmapMaxPoints
            .coerceAtLeast(minPoints + 1)
        heatmapEnabled = settingsStore.heatmapEnabled
        routesEnabled = settingsStore.fishingRoutesEnabled
        heatmapFilterEnabled = settingsStore.heatmapFilterEnabled
        routesFilterEnabled = settingsStore.routesFilterEnabled
        routesFadeEnabled = settingsStore.routesFadeEnabled
        routesFadeStartLimitDays = settingsStore.routesFadeStartDays
        routesFadeFullLimitDays = settingsStore.routesFadeFullDays
        calculationMethod = settingsStore.getHeatmapCalculationMethod(context.getString(R.string.heatmap_method_points))
        removeTransitions = settingsStore.heatmapRemoveTransitions
        removeTransitionsMode = settingsStore.heatmapRemoveTransitionsMode
        maxSpeed = settingsStore.heatmapMaxSpeed
        minZoomLevel = settingsStore.heatmapMinZoom.toDouble()
        maxTrackPoints = settingsStore.maxTrackPoints
        referenceLatitude = settingsStore.heatmapReferenceLatitude.toDouble()
        fadeCellEdges = settingsStore.heatmapFadeCellEdges

        val colorStr = settingsStore.getHeatmapColor("Punainen")
        baseColor = when (colorStr) {
            "Violetti" -> Color.rgb(128, 0, 128)
            "Vihreä" -> Color.GREEN
            else -> Color.RED
        }
        return oldGridSize != gridSizeMeters || oldHeatmapEnabled != heatmapEnabled ||
                oldRoutesEnabled != routesEnabled || oldHeatmapFilterEnabled != heatmapFilterEnabled ||
                oldRoutesFilterEnabled != routesFilterEnabled || oldAutoConfigure != autoConfigure ||
                oldRoutesFadeEnabled != routesFadeEnabled ||
                oldRoutesFadeStartLimitDays != routesFadeStartLimitDays ||
                oldRoutesFadeFullLimitDays != routesFadeFullLimitDays ||
                oldFadeCellEdges != fadeCellEdges
    }

    private fun getPoints(f: fi.anssi.kalakartta.ui.FilterManager.Filters, hasAreaFilter: Boolean, latSouth: Double? = null, latNorth: Double? = null, lonWest: Double? = null, lonEast: Double? = null): List<TrackPointHeatmapData> {
        val useBBox = latSouth != null && latNorth != null && lonWest != null && lonEast != null
        val lS = if (useBBox) latSouth!! else f.latSouth ?: 0.0
        val lN = if (useBBox) latNorth!! else f.latNorth ?: 0.0
        val lW = if (useBBox) lonWest!! else f.lonWest ?: 0.0
        val lE = if (useBBox) lonEast!! else f.lonEast ?: 0.0
        val areaActive = hasAreaFilter || useBBox

        val totalCount = db.trackPointDao().getCountFiltered(
            checkRange = f.startDate != null || f.endDate != null,
            startDate = f.startDate ?: 0L,
            endDate = f.endDate ?: Long.MAX_VALUE,
            checkArea = areaActive,
            latSouth = lS,
            latNorth = lN,
            lonWest = lW,
            lonEast = lE,
            removeTransitions = false,
            maxSpeed = 0f
        )
        val step = if (totalCount > maxTrackPoints) (totalCount / maxTrackPoints) + 1 else 1

        return when {
            (f.startDate != null || f.endDate != null) && areaActive -> {
                if (step > 1) db.trackPointDao().getPointsForHeatmapRangeAndAreaSampled(f.startDate ?: 0L, f.endDate ?: Long.MAX_VALUE, lS, lN, lW, lE, step)
                else db.trackPointDao().getPointsForHeatmapRangeAndArea(f.startDate ?: 0L, f.endDate ?: Long.MAX_VALUE, lS, lN, lW, lE)
            }
            f.startDate != null || f.endDate != null -> {
                if (step > 1) db.trackPointDao().getPointsForHeatmapRangeSampled(f.startDate ?: 0L, f.endDate ?: Long.MAX_VALUE, step)
                else db.trackPointDao().getPointsForHeatmapRange(f.startDate ?: 0L, f.endDate ?: Long.MAX_VALUE)
            }
            areaActive -> {
                if (step > 1) db.trackPointDao().getPointsForHeatmapAreaSampled(lS, lN, lW, lE, step)
                else db.trackPointDao().getPointsForHeatmapArea(lS, lN, lW, lE)
            }
            else -> {
                if (step > 1) db.trackPointDao().getAllForHeatmapSampled(step)
                else db.trackPointDao().getAllForHeatmap()
            }
        }
    }

    private fun getRoutePoints(
        f: fi.anssi.kalakartta.ui.FilterManager.Filters,
        hasAreaFilter: Boolean,
        minSessionStart: Long,
        latSouth: Double? = null,
        latNorth: Double? = null,
        lonWest: Double? = null,
        lonEast: Double? = null
    ): List<TrackPointHeatmapData> {
        val useBBox = latSouth != null && latNorth != null && lonWest != null && lonEast != null
        val lS = if (useBBox) latSouth!! else f.latSouth ?: 0.0
        val lN = if (useBBox) latNorth!! else f.latNorth ?: 0.0
        val lW = if (useBBox) lonWest!! else f.lonWest ?: 0.0
        val lE = if (useBBox) lonEast!! else f.lonEast ?: 0.0
        val areaActive = hasAreaFilter || useBBox
        val hasRange = f.startDate != null || f.endDate != null

        val totalCount = db.trackPointDao().getCountFilteredForRoutes(
            minSessionStart = minSessionStart,
            checkRange = hasRange,
            startDate = f.startDate ?: 0L,
            endDate = f.endDate ?: Long.MAX_VALUE,
            checkArea = areaActive,
            latSouth = lS,
            latNorth = lN,
            lonWest = lW,
            lonEast = lE,
            removeTransitions = false,
            maxSpeed = 0f
        )
        val step = if (totalCount > maxTrackPoints) (totalCount / maxTrackPoints) + 1 else 1

        return when {
            hasRange && areaActive -> {
                if (step > 1) db.trackPointDao().getPointsForRoutesRangeAndAreaSampled(
                    minSessionStart, f.startDate ?: 0L, f.endDate ?: Long.MAX_VALUE,
                    lS, lN, lW, lE, step
                ) else db.trackPointDao().getPointsForRoutesRangeAndArea(
                    minSessionStart, f.startDate ?: 0L, f.endDate ?: Long.MAX_VALUE,
                    lS, lN, lW, lE
                )
            }
            hasRange -> {
                if (step > 1) db.trackPointDao().getPointsForRoutesRangeSampled(
                    minSessionStart, f.startDate ?: 0L, f.endDate ?: Long.MAX_VALUE, step
                ) else db.trackPointDao().getPointsForRoutesRange(
                    minSessionStart, f.startDate ?: 0L, f.endDate ?: Long.MAX_VALUE
                )
            }
            areaActive -> {
                if (step > 1) db.trackPointDao().getPointsForRoutesAreaSampled(
                    minSessionStart, lS, lN, lW, lE, step
                ) else db.trackPointDao().getPointsForRoutesArea(
                    minSessionStart, lS, lN, lW, lE
                )
            }
            else -> {
                if (step > 1) db.trackPointDao().getAllForRoutesSampled(minSessionStart, step)
                else db.trackPointDao().getAllForRoutes(minSessionStart)
            }
        }
    }

    fun refreshData(bbox: BoundingBox? = null) {
        refreshSettings()
        dataJob?.cancel()
        dataJob = scope.launch {
            val newData = withContext(Dispatchers.IO) {
                val fm = FilterManager(context)
                val f = fm.getFilters()
                
                // Approksimaatio: 1 aste latitudia on n. 111320 metriä
                val latDegreeMeters = HeatmapGridCalculator.LATITUDE_DEGREE_METERS
                // Käytetään vakiota (esim. Suomen keskipiste 64.7), jotta ruudukko on stabiili ja ennustettava.
                // Dynaaminen latitudi draw-metodissa rikkoo ruudukon haun, jos se ei vastaa indeksointia.
                val lonDegreeMeters = HeatmapGridCalculator.longitudeDegreeMeters(referenceLatitude)

                val hasAnnualDateFilter = f.annualStartDay != null && f.annualStartMonth != null && 
                                        f.annualEndDay != null && f.annualEndMonth != null
                val hasTimeFilter = f.startTimeMinutes != null && f.endTimeMinutes != null
                val hasAnnualTimeFilter = f.annualStartTimeMinutes != null && f.annualEndTimeMinutes != null
                val hasAreaFilter = f.latNorth != null && f.latSouth != null && f.lonEast != null && f.lonWest != null
                val zoom = mapView.zoomLevelDouble
                val marginFactor = if (zoom >= 12.0) 0.25 else 0.0

                val latS = bbox?.let { it.latSouth - (it.latNorth - it.latSouth) * marginFactor }
                val latN = bbox?.let { it.latNorth + (it.latNorth - it.latSouth) * marginFactor }
                val lonW = bbox?.let { it.lonWest - (it.lonEast - it.lonWest) * marginFactor }
                val lonE = bbox?.let { it.lonEast + (it.lonEast - it.lonWest) * marginFactor }

                val routeSessionStartLimit = if (routesFadeEnabled) {
                    val dayMillis = 1000L * 60 * 60 * 24
                    val fadeLimitDays = routesFadeStartLimitDays.coerceAtLeast(0).toLong()
                    System.currentTimeMillis() - (fadeLimitDays + 1L) * dayMillis
                } else {
                    Long.MIN_VALUE
                }
                
                val resultData: Map<Pair<Int, Int>, Int> = if (heatmapEnabled) {
                    if (heatmapFilterEnabled && (hasAnnualDateFilter || hasTimeFilter || hasAnnualTimeFilter)) {
                        val rawPoints = getPoints(f, hasAreaFilter, latS, latN, lonW, lonE)

                        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Helsinki"))
                        val filteredPoints = rawPoints.filter { p ->
                            calendar.timeInMillis = p.timestamp
                            
                            // Koordinaattisuodatus (jos ei tehty jo SQL-tasolla tai varmuuden vuoksi)
                            if (hasAreaFilter) {
                                if (p.latitude < f.latSouth!! || p.latitude > f.latNorth!! ||
                                    p.longitude < f.lonWest!! || p.longitude > f.lonEast!!) return@filter false
                            }
                            
                            if (hasAnnualDateFilter) {
                                val month = calendar.get(java.util.Calendar.MONTH)
                                val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                val currentVal = month * 100 + day
                                val startVal = f.annualStartMonth!! * 100 + f.annualStartDay!!
                                val endVal = f.annualEndMonth!! * 100 + f.annualEndDay!!
                                if (startVal <= endVal) {
                                    if (currentVal < startVal || currentVal > endVal) return@filter false
                                } else {
                                    if (currentVal < startVal && currentVal > endVal) return@filter false
                                }
                            }

                            if (hasAnnualTimeFilter) {
                                val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                                val minute = calendar.get(java.util.Calendar.MINUTE)
                                val currentMinutes = hour * 60 + minute
                                if (f.annualStartTimeMinutes!! <= f.annualEndTimeMinutes!!) {
                                    if (currentMinutes < f.annualStartTimeMinutes || currentMinutes > f.annualEndTimeMinutes) return@filter false
                                } else {
                                    if (currentMinutes < f.annualStartTimeMinutes && currentMinutes > f.annualEndTimeMinutes) return@filter false
                                }
                            }

                            if (hasTimeFilter) {
                                val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                                val minute = calendar.get(java.util.Calendar.MINUTE)
                                val currentMinutes = hour * 60 + minute
                                if (f.startTimeMinutes!! <= f.endTimeMinutes!!) {
                                    if (currentMinutes < f.startTimeMinutes || currentMinutes > f.endTimeMinutes) return@filter false
                                } else {
                                    if (currentMinutes < f.startTimeMinutes && currentMinutes > f.endTimeMinutes) return@filter false
                                }
                            }

                            if (removeTransitions && p.speed > maxSpeed) return@filter false
                            
                            true
                        }
                        processPoints(filteredPoints)
                    } else {
                        // Käytetään SQL-tason aggregointia
                        // Jos removeTransitions on päällä, emme voi käyttää nykyisiä aggregointikyselyitä,
                        // koska ne eivät sisällä nopeussuodatusta. 
                        // Tässä tapauksessa haemme kaikki pisteet ja suodatamme Kotlinissa.
                        
                        if (removeTransitions) {
                            val rawPoints = if (heatmapFilterEnabled) {
                                getPoints(f, hasAreaFilter, latS, latN, lonW, lonE)
                            } else {
                                getPoints(f, false, latS, latN, lonW, lonE)
                            }
                            
                            val filteredPoints = rawPoints.filter { it.speed <= maxSpeed }
                            processPoints(filteredPoints)
                        } else {
                            val isPointCalculation = calculationMethod == context.getString(R.string.heatmap_method_points)
                            val isPointAndSessionCalculation = calculationMethod == context.getString(R.string.heatmap_method_points_and_sessions)
                            val aggregated = when {
                                !heatmapFilterEnabled -> {
                                    if (isPointCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapPoints(
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    } else if (isPointAndSessionCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapPointsAndSessions(
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    } else {
                                        db.trackPointDao().getAggregatedHeatmap(
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    }
                                }
                                (f.startDate != null || f.endDate != null) && hasAreaFilter -> {
                                    if (isPointCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapRangeAndAreaPoints(
                                            f.startDate ?: 0L,
                                            f.endDate ?: Long.MAX_VALUE,
                                            f.latSouth!!, f.latNorth!!, f.lonWest!!, f.lonEast!!,
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    } else if (isPointAndSessionCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapRangeAndAreaPointsAndSessions(
                                            f.startDate ?: 0L,
                                            f.endDate ?: Long.MAX_VALUE,
                                            f.latSouth!!, f.latNorth!!, f.lonWest!!, f.lonEast!!,
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    } else {
                                        db.trackPointDao().getAggregatedHeatmapRangeAndArea(
                                            f.startDate ?: 0L,
                                            f.endDate ?: Long.MAX_VALUE,
                                            f.latSouth!!, f.latNorth!!, f.lonWest!!, f.lonEast!!,
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    }
                                }
                                f.startDate != null || f.endDate != null -> {
                                    if (isPointCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapRangePoints(
                                            f.startDate ?: 0L,
                                            f.endDate ?: Long.MAX_VALUE,
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    } else if (isPointAndSessionCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapRangePointsAndSessions(
                                            f.startDate ?: 0L,
                                            f.endDate ?: Long.MAX_VALUE,
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    } else {
                                        db.trackPointDao().getAggregatedHeatmapRange(
                                            f.startDate ?: 0L,
                                            f.endDate ?: Long.MAX_VALUE,
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    }
                                }
                                hasAreaFilter -> {
                                    if (isPointCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapAreaPoints(
                                            f.latSouth!!, f.latNorth!!, f.lonWest!!, f.lonEast!!,
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    } else if (isPointAndSessionCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapAreaPointsAndSessions(
                                            f.latSouth!!, f.latNorth!!, f.lonWest!!, f.lonEast!!,
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    } else {
                                        db.trackPointDao().getAggregatedHeatmapArea(
                                            f.latSouth!!, f.latNorth!!, f.lonWest!!, f.lonEast!!,
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    }
                                }
                                else -> {
                                    if (isPointCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapPoints(
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    } else if (isPointAndSessionCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapPointsAndSessions(
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    } else {
                                        db.trackPointDao().getAggregatedHeatmap(
                                            latDegreeMeters,
                                            lonDegreeMeters,
                                            gridSizeMeters
                                        )
                                    }
                                }
                            }
                            aggregated.associate { Pair(it.x, it.y) to it.sessionCount }
                        }
                    }
                } else {
                    mapOf()
                }

                // Reittien haku
                val newRouteData = mutableListOf<RouteWithBounds>()
                if (routesEnabled) {
                    val routeFilters = FilterManager.filtersForRoutes(f, routesFilterEnabled)
                    val rawPoints = getRoutePoints(
                        routeFilters,
                        routesFilterEnabled && hasAreaFilter,
                        routeSessionStartLimit,
                        latS,
                        latN,
                        lonW,
                        lonE
                    )

                    if (rawPoints.isNotEmpty()) {
                        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Helsinki"))
                        
                        var currentRoutePoints = mutableListOf<TrackPointHeatmapData>()
                        var currentSessionId = rawPoints[0].fishingSessionId
                        
                        var rMinLat = Double.MAX_VALUE
                        var rMaxLat = -Double.MAX_VALUE
                        var rMinLon = Double.MAX_VALUE
                        var rMaxLon = -Double.MAX_VALUE

                        fun finalizeRoute() {
                            if (currentRoutePoints.size >= 2) {
                                newRouteData.add(RouteWithBounds(currentRoutePoints, rMinLat, rMaxLat, rMinLon, rMaxLon))
                            }
                        }

                        for (p in rawPoints) {
                            var accepted = true
                            if (removeTransitions && removeTransitionsMode == 1 && p.speed > maxSpeed) accepted = false
                            
                            if (accepted && routesFilterEnabled) {
                                calendar.timeInMillis = p.timestamp

                                if (hasAreaFilter) {
                                    if (p.latitude < f.latSouth!! || p.latitude > f.latNorth!! ||
                                        p.longitude < f.lonWest!! || p.longitude > f.lonEast!!) accepted = false
                                }

                                if (accepted && hasAnnualDateFilter) {
                                    val month = calendar.get(java.util.Calendar.MONTH)
                                    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                    val currentVal = month * 100 + day
                                    val startVal = f.annualStartMonth!! * 100 + f.annualStartDay!!
                                    val endVal = f.annualEndMonth!! * 100 + f.annualEndDay!!
                                    if (startVal <= endVal) {
                                        if (currentVal < startVal || currentVal > endVal) accepted = false
                                    } else {
                                        if (currentVal < startVal && currentVal > endVal) accepted = false
                                    }
                                }

                                if (accepted && hasAnnualTimeFilter) {
                                    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                                    val minute = calendar.get(java.util.Calendar.MINUTE)
                                    val currentMinutes = hour * 60 + minute
                                    if (f.annualStartTimeMinutes!! <= f.annualEndTimeMinutes!!) {
                                        if (currentMinutes < f.annualStartTimeMinutes || currentMinutes > f.annualEndTimeMinutes) accepted = false
                                    } else {
                                        if (currentMinutes < f.annualStartTimeMinutes && currentMinutes > f.annualEndTimeMinutes) accepted = false
                                    }
                                }

                                if (accepted && hasTimeFilter) {
                                    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                                    val minute = calendar.get(java.util.Calendar.MINUTE)
                                    val currentMinutes = hour * 60 + minute
                                    if (f.startTimeMinutes!! <= f.endTimeMinutes!!) {
                                        if (currentMinutes < f.startTimeMinutes || currentMinutes > f.endTimeMinutes) accepted = false
                                    } else {
                                        if (currentMinutes < f.startTimeMinutes && currentMinutes > f.endTimeMinutes) accepted = false
                                    }
                                }
                            }
                            
                            if (accepted) {
                                if (p.fishingSessionId != currentSessionId) {
                                    finalizeRoute()
                                    currentRoutePoints = mutableListOf()
                                    currentSessionId = p.fishingSessionId
                                    rMinLat = Double.MAX_VALUE; rMaxLat = -Double.MAX_VALUE; rMinLon = Double.MAX_VALUE; rMaxLon = -Double.MAX_VALUE
                                }
                                currentRoutePoints.add(p)
                                if (p.latitude < rMinLat) rMinLat = p.latitude
                                if (p.latitude > rMaxLat) rMaxLat = p.latitude
                                if (p.longitude < rMinLon) rMinLon = p.longitude
                                if (p.longitude > rMaxLon) rMaxLon = p.longitude
                            }
                        }
                        finalizeRoute()
                    }
                }
                
                Pair(resultData, newRouteData)
            }
            
            heatmapData = newData.first
            routeData = newData.second
            
            if (autoConfigure && heatmapData.isNotEmpty()) {
                val values = heatmapData.values.sorted()
                // Mahdollisimman tarkkaan 5 prosenttia heatmap-ruuduista saa tummimman värisävyn
                // Eli etsitään 95. persentiili
                val index = (values.size * 0.95).toInt().coerceIn(0, values.size - 1)
                val calculatedMax = values[index]
                
                maxPoints = calculatedMax.coerceAtLeast(5)
                minPoints = 1
                
                // Tallennetaan lasketut arvot, jotta SettingsManager voi näyttää ne
                settingsStore.heatmapMinPoints = minPoints
                settingsStore.heatmapMaxPoints = maxPoints
            }
            
            mapView.invalidate()
        }
    }

    private fun processPoints(points: List<TrackPointHeatmapData>): Map<Pair<Int, Int>, Int> {
        val isPointCalculation = calculationMethod == context.getString(R.string.heatmap_method_points)
        val isPointAndSessionCalculation = calculationMethod == context.getString(R.string.heatmap_method_points_and_sessions)

        return if (isPointCalculation) {
            HeatmapGridCalculator.pointCounts(points, gridSizeMeters, referenceLatitude)
        } else if (isPointAndSessionCalculation) {
            HeatmapGridCalculator.pointAndSessionCounts(points, gridSizeMeters, referenceLatitude)
        } else {
            HeatmapGridCalculator.sessionCounts(points, gridSizeMeters, referenceLatitude)
        }
    }

    private fun drawHeatmapCells(c: Canvas) {
        paint.style = Paint.Style.FILL
        visibleHeatmapCells.forEach { cell ->
            paint.color = cell.color
            c.drawRect(cell.bounds, paint)
        }
    }

    /**
     * Blurs the complete heatmap as one render node. DECAL keeps the blurred edge transparent,
     * so only the heatmap fades into the map and the map itself is never part of the effect.
     */
    private fun drawBlurredHeatmap(c: Canvas, osmv: MapView, blurRadius: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !c.isHardwareAccelerated) return false

        var renderNode: RenderNode? = null
        return try {
            val node = (heatmapBlurRenderNode as? RenderNode) ?: RenderNode("fishingHeatmap").also {
                heatmapBlurRenderNode = it
            }
            renderNode = node
            node.setPosition(0, 0, osmv.width, osmv.height)

            val recordingCanvas = node.beginRecording()
            drawHeatmapCells(recordingCanvas)
            node.endRecording()

            val blurEffect = if (heatmapBlurEffect !is RenderEffect || heatmapBlurRadius != blurRadius) {
                RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.DECAL
                ).also {
                    heatmapBlurEffect = it
                    heatmapBlurRadius = blurRadius
                }
            } else {
                heatmapBlurEffect as RenderEffect
            }
            node.setRenderEffect(blurEffect)
            c.drawRenderNode(node)
            true
        } catch (_: RuntimeException) {
            // Keep the original rectangle renderer for unsupported/problematic canvases.
            renderNode?.setRenderEffect(null)
            false
        }
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        
        val projection = osmv.projection
        
        if (heatmapEnabled && osmv.zoomLevelDouble >= minZoomLevel) {
            val boundingBox = projection.boundingBox
            val minCell = HeatmapGridCalculator.cellFor(
                boundingBox.latSouth,
                boundingBox.lonWest,
                gridSizeMeters,
                referenceLatitude
            )
            val maxCell = HeatmapGridCalculator.cellFor(
                boundingBox.latNorth,
                boundingBox.lonEast,
                gridSizeMeters,
                referenceLatitude
            )
            val minX = minCell.first - 1
            val maxX = maxCell.first + 1
            val minY = minCell.second - 1
            val maxY = maxCell.second + 1
            
            visibleHeatmapCells.clear()
            var blurRadius = 0f

            for ((key, count) in heatmapData) {
                val x = key.first
                val y = key.second
                
                if (x in minX..maxX && y in minY..maxY) {
                    if (count < minPoints) continue
                    
                    // Lasketaan alfa (max peittävyys 60% = 153/255)
                    val ratio = (count.toFloat() - minPoints) / (maxPoints - minPoints).coerceAtLeast(1)
                    val clampedRatio = ratio.coerceIn(0f, 1f)
                    val alpha = (40 + (113 * clampedRatio)).toInt() // 40-153 (15%-60%)
                    
                    val color = Color.argb(
                        alpha,
                        Color.red(baseColor),
                        Color.green(baseColor),
                        Color.blue(baseColor)
                    )
                    
                    // Ruudun koordinaatit takaisin GeoPointeiksi piirtoa varten
                    val lat = HeatmapGridCalculator.latitudeForCell(y, gridSizeMeters)
                    val lon = HeatmapGridCalculator.longitudeForCell(x, gridSizeMeters, referenceLatitude)
                    
                    val nextLat = HeatmapGridCalculator.latitudeForCell(y + 1, gridSizeMeters)
                    val nextLon = HeatmapGridCalculator.longitudeForCell(x + 1, gridSizeMeters, referenceLatitude)
                    
                    val p1 = projection.toPixels(GeoPoint(lat, lon), null)
                    val p2 = projection.toPixels(GeoPoint(nextLat, nextLon), null)
                    
                    val cellBounds = RectF(
                        p1.x.toFloat(),
                        p2.y.toFloat(),
                        p2.x.toFloat(),
                        p1.y.toFloat()
                    )

                    if (blurRadius == 0f) {
                        val cellSize = minOf(
                            kotlin.math.abs(cellBounds.width()),
                            kotlin.math.abs(cellBounds.height())
                        )
                        blurRadius = (cellSize * HEATMAP_BLUR_RADIUS_FRACTION)
                            .coerceIn(MIN_HEATMAP_BLUR_RADIUS, MAX_HEATMAP_BLUR_RADIUS)
                    }
                    visibleHeatmapCells += HeatmapCell(cellBounds, color)
                }
            }

            if (fadeCellEdges && blurRadius > 0f && drawBlurredHeatmap(c, osmv, blurRadius)) {
                visibleHeatmapCells.clear()
            } else {
                // Compatibility/performance fallback: the original rectangular renderer.
                drawHeatmapCells(c)
                visibleHeatmapCells.clear()
            }
        }

        if (routesEnabled) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = baseColor
            paint.alpha = 200
            paint.isAntiAlias = true
            
            val p1 = android.graphics.Point()
            val bbox = projection.boundingBox
            // Lisätään marginaali koordinaatteihin, jotta viivat piirtyvät siististi näytön reunoilla
            val margin = 0.01 
            val minLat = bbox.latSouth - margin
            val maxLat = bbox.latNorth + margin
            val minLon = bbox.lonWest - margin
            val maxLon = bbox.lonEast + margin

            val now = System.currentTimeMillis()
            for (route in routeData) {
                // 1. Reittikohtainen Bounding Box -tarkistus
                if (route.maxLat < minLat || route.minLat > maxLat || 
                    route.maxLon < minLon || route.minLon > maxLon) continue

                if (route.points.size < 2) continue
                
                if (routesFadeEnabled) {
                    val timestamp = route.points.firstOrNull()?.timestamp ?: 0L
                    val ageMs = now - timestamp
                    val ageDays = ageMs / (1000L * 60 * 60 * 24)
                    
                    if (ageDays > routesFadeStartLimitDays) continue
                    
                    val alpha = if (ageDays < routesFadeFullLimitDays) {
                        200
                    } else {
                        val range = (routesFadeStartLimitDays - routesFadeFullLimitDays).toDouble().coerceAtLeast(1.0)
                        val pos = (ageDays - routesFadeFullLimitDays).toDouble()
                        val ratio = 1.0 - (pos / range)
                        (ratio * 200).toInt().coerceIn(0, 200)
                    }
                    paint.alpha = alpha
                } else {
                    paint.alpha = 200
                }
                
                var first = true
                var prevX = 0f
                var prevY = 0f
                var prevInside = false
                
                for (pt in route.points) {
                    val isInside = pt.latitude in minLat..maxLat && pt.longitude in minLon..maxLon
                    
                    // Piirretään jos joko nykyinen tai edellinen piste on näkyvällä alueella
                    if (isInside || prevInside) {
                        projection.toPixels(GeoPoint(pt.latitude, pt.longitude), p1)
                        val curX = p1.x.toFloat()
                        val curY = p1.y.toFloat()
                        
                        if (!first) {
                            // 2. Dynaaminen harvennus piirtovaiheessa (Visual Downsampling)
                            // Piirretään vain jos piste on tarpeeksi kaukana edellisestä (esim. > 2 pikseliä)
                            // TAI jos se on reitin viimeinen piste (varmistetaan reitin jatkuvuus)
                            val dx = curX - prevX
                            val dy = curY - prevY
                            if (dx*dx + dy*dy > 4f || pt == route.points.last()) {
                                c.drawLine(prevX, prevY, curX, curY, paint)
                                prevX = curX
                                prevY = curY
                                first = false
                            }
                        } else {
                            prevX = curX
                            prevY = curY
                            first = false
                        }
                    } else {
                        // Jos hypätään näkymän ulkopuolelle, merkataan seuraava piste "ensimmäiseksi"
                        // jotta ei vedetä viivaa näkymän halki silloin kun se ei ole tarpeen
                        first = true
                    }
                    prevInside = isInside
                }
            }
        }
    }
}
