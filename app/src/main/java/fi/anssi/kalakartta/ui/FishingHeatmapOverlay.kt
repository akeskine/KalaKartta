package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import kotlin.math.cos
import kotlin.math.roundToInt

class FishingHeatmapOverlay(private val context: Context, private val db: AppDatabase, private val mapView: MapView) : Overlay() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var dataJob: Job? = null
    
    // Ruudun koko metreinä
    private var gridSizeMeters = 100.0
    
    // Ruudutus: Map<Pair<RuutuX, RuutuY>, VierailuKerrat>
    private var heatmapData = mapOf<Pair<Int, Int>, Int>()
    
    private val paint = Paint().apply {
        style = Paint.Style.FILL
    }
    
    private val rect = RectF()
    
    private var minPoints = 1
    private var maxPoints = 5
    private var baseColor = Color.RED
    private var heatmapEnabled = false
    private var routesEnabled = false
    private var heatmapFilterEnabled = false
    private var routesFilterEnabled = false
    private var calculationMethod = ""
    private var removeTransitions = false
    private var maxSpeed = 10.0f

    private var routeData = listOf<List<TrackPointHeatmapData>>()

    init {
        refreshSettings()
        refreshData()
    }

    fun refreshSettings(): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val oldGridSize = gridSizeMeters
        val oldHeatmapEnabled = heatmapEnabled
        val oldRoutesEnabled = routesEnabled
        val oldHeatmapFilterEnabled = heatmapFilterEnabled
        val oldRoutesFilterEnabled = routesFilterEnabled

        gridSizeMeters = prefs.getFloat("heatmap_grid_size", 300.0f).toDouble().coerceAtLeast(1.0)
        minPoints = prefs.getInt("heatmap_min_points", 1).coerceAtLeast(1)
        maxPoints = prefs.getInt("heatmap_max_points", 5).coerceAtLeast(minPoints + 1)
        heatmapEnabled = prefs.getBoolean("heatmap_enabled", false)
        routesEnabled = prefs.getBoolean("fishing_routes_enabled", false)
        heatmapFilterEnabled = prefs.getBoolean("heatmap_filter_enabled", false)
        routesFilterEnabled = prefs.getBoolean("routes_filter_enabled", false)
        calculationMethod = prefs.getString("heatmap_calculation_method", context.getString(R.string.heatmap_method_sessions)) ?: context.getString(R.string.heatmap_method_sessions)
        removeTransitions = prefs.getBoolean("heatmap_remove_transitions", false)
        maxSpeed = prefs.getFloat("heatmap_max_speed", 10.0f)

        val colorStr = prefs.getString("heatmap_color", "Punainen")
        baseColor = when (colorStr) {
            "Violetti" -> Color.rgb(128, 0, 128)
            "Vihreä" -> Color.GREEN
            else -> Color.RED
        }
        return oldGridSize != gridSizeMeters || oldHeatmapEnabled != heatmapEnabled ||
                oldRoutesEnabled != routesEnabled || oldHeatmapFilterEnabled != heatmapFilterEnabled ||
                oldRoutesFilterEnabled != routesFilterEnabled
    }

    private fun getPoints(f: fi.anssi.kalakartta.ui.FilterManager.Filters, hasAreaFilter: Boolean): List<TrackPointHeatmapData> {
        return when {
            (f.startDate != null || f.endDate != null) && hasAreaFilter -> {
                db.trackPointDao().getPointsForHeatmapRangeAndArea(
                    f.startDate ?: 0L, f.endDate ?: Long.MAX_VALUE,
                    f.latSouth!!, f.latNorth!!, f.lonWest!!, f.lonEast!!
                )
            }
            f.startDate != null || f.endDate != null -> {
                db.trackPointDao().getPointsForHeatmapRange(f.startDate ?: 0L, f.endDate ?: Long.MAX_VALUE)
            }
            hasAreaFilter -> {
                db.trackPointDao().getPointsForHeatmapArea(
                    f.latSouth!!, f.latNorth!!, f.lonWest!!, f.lonEast!!
                )
            }
            else -> db.trackPointDao().getAllForHeatmap()
        }
    }

    fun refreshData() {
        refreshSettings()
        dataJob?.cancel()
        dataJob = scope.launch {
            val newData = withContext(Dispatchers.IO) {
                val fm = FilterManager(context)
                val f = fm.getFilters()
                
                // Approksimaatio: 1 aste latitudia on n. 111320 metriä
                val latDegreeMeters = 111320.0
                // Käytetään kiinteää latitudia (60 astetta) longitudin muunnokseen, jotta ruudutus on vakio
                val lonDegreeMeters = latDegreeMeters * Math.cos(Math.toRadians(60.0))

                val hasAnnualDateFilter = f.annualStartDay != null && f.annualStartMonth != null && 
                                        f.annualEndDay != null && f.annualEndMonth != null
                val hasTimeFilter = f.startTimeMinutes != null && f.endTimeMinutes != null
                val hasAnnualTimeFilter = f.annualStartTimeMinutes != null && f.annualEndTimeMinutes != null
                val hasAreaFilter = f.latNorth != null && f.latSouth != null && f.lonEast != null && f.lonWest != null
                
                val resultData: Map<Pair<Int, Int>, Int> = if (heatmapEnabled) {
                    if (heatmapFilterEnabled && (hasAnnualDateFilter || hasTimeFilter || hasAnnualTimeFilter)) {
                        val rawPoints = getPoints(f, hasAreaFilter)

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
                                getPoints(f, hasAreaFilter)
                            } else {
                                db.trackPointDao().getAllForHeatmap()
                            }
                            
                            val filteredPoints = rawPoints.filter { it.speed <= maxSpeed }
                            processPoints(filteredPoints)
                        } else {
                            val isPointCalculation = calculationMethod == context.getString(R.string.heatmap_method_points)
                            val aggregated = when {
                                !heatmapFilterEnabled -> {
                                    if (isPointCalculation) {
                                        db.trackPointDao().getAggregatedHeatmapPoints(
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
                val newRouteData: List<List<TrackPointHeatmapData>> = if (routesEnabled) {
                    val rawPoints = if (routesFilterEnabled) {
                        getPoints(f, hasAreaFilter)
                    } else {
                        db.trackPointDao().getAllForHeatmap()
                    }

                    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Helsinki"))
                    val filteredPoints = rawPoints.filter { p ->
                        if (removeTransitions && p.speed > maxSpeed) return@filter false
                        
                        if (routesFilterEnabled) {
                            calendar.timeInMillis = p.timestamp

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
                        }
                        true
                    }

                    filteredPoints.groupBy { it.fishingSessionId }
                        .values.map { it.sortedBy { p -> p.timestamp } }
                } else {
                    listOf()
                }

                Pair(resultData, newRouteData)
            }
            
            heatmapData = newData.first
            routeData = newData.second
            mapView.invalidate()
        }
    }

    private fun processPoints(points: List<TrackPointHeatmapData>): Map<Pair<Int, Int>, Int> {
        val isPointCalculation = calculationMethod == context.getString(R.string.heatmap_method_points)
        
        if (isPointCalculation) {
            val gridPoints = mutableMapOf<Pair<Int, Int>, Int>()
            
            // Approksimaatio: 1 aste latitudia on n. 111320 metriä
            val latDegreeMeters = 111320.0
            // Käytetään kiinteää latitudia (60 astetta) longitudin muunnokseen, jotta ruudutus on vakio
            val lonDegreeMeters = latDegreeMeters * Math.cos(Math.toRadians(60.0))
            
            for (p in points) {
                val x = (p.longitude * lonDegreeMeters / gridSizeMeters).roundToInt()
                val y = (p.latitude * latDegreeMeters / gridSizeMeters).roundToInt()
                
                val key = Pair(x, y)
                gridPoints[key] = (gridPoints[key] ?: 0) + 1
            }
            return gridPoints
        } else {
            val gridSessions = mutableMapOf<Pair<Int, Int>, MutableSet<Long>>()
            
            // Approksimaatio: 1 aste latitudia on n. 111320 metriä
            val latDegreeMeters = 111320.0
            // Käytetään kiinteää latitudia (60 astetta) longitudin muunnokseen, jotta ruudutus on vakio
            val lonDegreeMeters = latDegreeMeters * Math.cos(Math.toRadians(60.0))
            
            for (p in points) {
                val x = (p.longitude * lonDegreeMeters / gridSizeMeters).roundToInt()
                val y = (p.latitude * latDegreeMeters / gridSizeMeters).roundToInt()
                
                val key = Pair(x, y)
                if (!gridSessions.containsKey(key)) {
                    gridSessions[key] = mutableSetOf()
                }
                gridSessions[key]?.add(p.fishingSessionId)
            }
            
            return gridSessions.mapValues { it.value.size }
        }
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        
        val projection = osmv.projection
        
        if (heatmapEnabled) {
            val boundingBox = projection.boundingBox
            val latDegreeMeters = 111320.0
            val lonDegreeMeters = latDegreeMeters * cos(Math.toRadians(60.0))
            
            val minX = (boundingBox.lonWest * lonDegreeMeters / gridSizeMeters).roundToInt() - 1
            val maxX = (boundingBox.lonEast * lonDegreeMeters / gridSizeMeters).roundToInt() + 1
            val minY = (boundingBox.latSouth * latDegreeMeters / gridSizeMeters).roundToInt() - 1
            val maxY = (boundingBox.latNorth * latDegreeMeters / gridSizeMeters).roundToInt() + 1
            
            paint.style = Paint.Style.FILL
            
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    val count = heatmapData[Pair(x, y)] ?: continue
                    if (count < minPoints) continue
                    
                    // Lasketaan alfa (max peittävyys 60% = 153/255)
                    val ratio = (count.toFloat() - minPoints) / (maxPoints - minPoints).coerceAtLeast(1)
                    val clampedRatio = ratio.coerceIn(0f, 1f)
                    val alpha = (40 + (113 * clampedRatio)).toInt() // 40-153 (15%-60%)
                    
                    paint.color = Color.argb(
                        alpha,
                        Color.red(baseColor),
                        Color.green(baseColor),
                        Color.blue(baseColor)
                    )
                    
                    // Ruudun koordinaatit takaisin GeoPointeiksi piirtoa varten
                    val lat = y * gridSizeMeters / latDegreeMeters
                    val lon = x * gridSizeMeters / lonDegreeMeters
                    
                    val nextLat = (y + 1) * gridSizeMeters / latDegreeMeters
                    val nextLon = (x + 1) * gridSizeMeters / lonDegreeMeters
                    
                    val p1 = projection.toPixels(GeoPoint(lat, lon), null)
                    val p2 = projection.toPixels(GeoPoint(nextLat, nextLon), null)
                    
                    rect.set(
                        p1.x.toFloat(),
                        p2.y.toFloat(),
                        p2.x.toFloat(),
                        p1.y.toFloat()
                    )
                    
                    c.drawRect(rect, paint)
                }
            }
        }

        if (routesEnabled) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = baseColor
            paint.alpha = 200
            paint.isAntiAlias = true
            
            val p1 = android.graphics.Point()
            
            for (route in routeData) {
                if (route.size < 2) continue
                
                var first = true
                var prevX = 0f
                var prevY = 0f
                
                for (pt in route) {
                    projection.toPixels(GeoPoint(pt.latitude, pt.longitude), p1)
                    if (!first) {
                        c.drawLine(prevX, prevY, p1.x.toFloat(), p1.y.toFloat(), paint)
                    }
                    first = false
                    prevX = p1.x.toFloat()
                    prevY = p1.y.toFloat()
                }
            }
        }
    }
}