package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.TrackPoint
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
    private var filterEnabled = false
    
    init {
        refreshSettings()
        refreshData()
    }
    
    fun refreshSettings(): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val oldGridSize = gridSizeMeters
        gridSizeMeters = prefs.getFloat("heatmap_grid_size", 100.0f).toDouble().coerceAtLeast(1.0)
        minPoints = prefs.getInt("heatmap_min_points", 1).coerceAtLeast(1)
        maxPoints = prefs.getInt("heatmap_max_points", 5).coerceAtLeast(minPoints + 1)
        filterEnabled = prefs.getBoolean("heatmap_filter_enabled", false)
        
        val colorStr = prefs.getString("heatmap_color", "Punainen")
        baseColor = when (colorStr) {
            "Violetti" -> Color.rgb(128, 0, 128)
            "Vihreä" -> Color.GREEN
            else -> Color.RED
        }
        return oldGridSize != gridSizeMeters
    }

    fun refreshData() {
        refreshSettings()
        dataJob?.cancel()
        dataJob = scope.launch {
            var points = withContext(Dispatchers.IO) {
                db.trackPointDao().getAll()
            }
            
            if (filterEnabled) {
                points = withContext(Dispatchers.Default) {
                    FilterManager(context).applyTrackPointFilter(points)
                }
            }
            
            val newData = withContext(Dispatchers.Default) {
                processPoints(points)
            }
            
            heatmapData = newData
            mapView.invalidate()
        }
    }

    private fun processPoints(points: List<TrackPoint>): Map<Pair<Int, Int>, Int> {
        val gridSessions = mutableMapOf<Pair<Int, Int>, MutableSet<Long>>()
        
        // Approksimaatio: 1 aste latitudia on n. 111320 metriä
        val latDegreeMeters = 111320.0
        // Käytetään kiinteää latitudia (60 astetta) longitudin muunnokseen, jotta ruudutus on vakio
        val lonDegreeMeters = latDegreeMeters * cos(Math.toRadians(60.0))
        
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

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        
        val projection = osmv.projection
        val boundingBox = projection.boundingBox
        
        val latDegreeMeters = 111320.0
        val lonDegreeMeters = latDegreeMeters * cos(Math.toRadians(60.0))
        
        val minX = (boundingBox.lonWest * lonDegreeMeters / gridSizeMeters).roundToInt() - 1
        val maxX = (boundingBox.lonEast * lonDegreeMeters / gridSizeMeters).roundToInt() + 1
        val minY = (boundingBox.latSouth * latDegreeMeters / gridSizeMeters).roundToInt() - 1
        val maxY = (boundingBox.latNorth * latDegreeMeters / gridSizeMeters).roundToInt() + 1
        
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                val count = heatmapData[Pair(x, y)] ?: continue
                if (count < minPoints) continue
                
                // Lasketaan alfa
                val ratio = (count.toFloat() - minPoints) / (maxPoints - minPoints)
                val clampedRatio = ratio.coerceIn(0f, 1f)
                val alpha = (40 + (200 * clampedRatio)).toInt() // 40-240
                
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
}