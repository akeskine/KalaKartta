package fi.anssi.kalakartta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.FolderOverlay
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import fi.anssi.kalakartta.io.ImportExportManager
import fi.anssi.kalakartta.data.*
import androidx.room.Room
import fi.anssi.kalakartta.ui.SettingsManager
import fi.anssi.kalakartta.ui.CatchManager
import fi.anssi.kalakartta.ui.MarkerManager
import fi.anssi.kalakartta.ui.FilterManager
import fi.anssi.kalakartta.ui.FishingHeatmapOverlay
import fi.anssi.kalakartta.ui.SettingsKeys
import fi.anssi.kalakartta.ui.SettingsDefaults
import fi.anssi.kalakartta.ui.SettingsStore
import fi.anssi.kalakartta.ui.WindDirectionView
import fi.anssi.kalakartta.utils.WeatherService
import fi.anssi.kalakartta.utils.SessionStatsFormatter
import fi.anssi.kalakartta.utils.MMLTileSource
import fi.anssi.kalakartta.utils.TraficomTileSource
import fi.anssi.kalakartta.utils.VeneilykarttaTileSource
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.utils.enlargeButtons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.*
import fi.anssi.kalakartta.service.FishingSessionService
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import fi.anssi.kalakartta.ui.SessionReplayResult

class MainActivity : AppCompatActivity() {

    private val settingsStore by lazy {
        SettingsStore(getSharedPreferences("settings", MODE_PRIVATE))
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Jos lupa annettiin, kokeillaan avata asetukset uudelleen
            // Mutta tarkistetaan vielä Alarm-lupa jos tarpeen
            settingsManager.openTalkingClockSettingsIfPermissionsOk()
        } else {
            Toast.makeText(this, R.string.talking_clock_permission_notifications, Toast.LENGTH_LONG).show()
        }
    }

    private val fishingSessionActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleActivityResult(3001, result.resultCode, result.data)
    }

    private var pendingActivityResultRequestCode: Int? = null
    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val requestCode = pendingActivityResultRequestCode ?: return@registerForActivityResult
        pendingActivityResultRequestCode = null
        handleActivityResult(requestCode, result.resultCode, result.data)
    }

    fun getNotificationPermissionLauncher() = requestNotificationPermissionLauncher

    fun launchFishingSessionActivity(intent: Intent) {
        fishingSessionActivityLauncher.launch(intent)
    }

    fun launchActivityForResult(intent: Intent, requestCode: Int) {
        pendingActivityResultRequestCode = requestCode
        activityResultLauncher.launch(intent)
    }

    private lateinit var db: AppDatabase
    private lateinit var importExportManager: ImportExportManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var catchManager: CatchManager
    private lateinit var markerManager: MarkerManager
    private lateinit var filterManager: FilterManager
    private lateinit var weatherService: WeatherService
    private var weatherCheckDone = false
    private var lastFoundStation: fi.anssi.kalakartta.utils.WeatherStation? = null
    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private var scaleBarOverlay: ScaleBarOverlay? = null
    private val heatmapUpdateHandler = Handler(Looper.getMainLooper())
    private val heatmapUpdateRunnable = Runnable { if (::db.isInitialized && heatmapOverlay != null) { heatmapOverlay?.refreshData(map.boundingBox) } }
    
    private fun addOverlayBelowMarkers(overlay: Overlay) {
        var index = -1
        for (i in 0 until map.overlays.size) {
            if (map.overlays[i] is FolderOverlay) {
                index = i
                break
            }
        }
        if (index != -1) {
            map.overlays.add(index, overlay)
        } else {
            map.overlays.add(overlay)
        }
    }

    // Kalastussessio
    private var fishingService: FishingSessionService? = null
    private var isBound = false
    private var sessionPolyline: Polyline? = null
    private var archivedSessionPolyline: Polyline? = null
    private var heatmapOverlay: FishingHeatmapOverlay? = null
    private var replayJob: Job? = null
    private var visibleArchivedSessionId: Long = -1L
    private var isReplayPlaying = true
    private var replaySpeed = 60
    private var currentReplayTime = 0L
    private var replayStartTime = 0L
    private var replayEndTime = 0L
    private var replayPoints = listOf<TrackPoint>()
    private var isOnlySessionCatchesMode = false
    private val recordingHandler = Handler(Looper.getMainLooper())
    private var recordingDotVisible = true
    private var recordingIntervalSeconds = 0
    private var isBlinking = false
    private val recordingBlinkRunnable = object : Runnable {
        override fun run() {
            val dot = findViewById<android.view.View>(R.id.recordingDot)
            
            if (dot != null) {
                recordingDotVisible = !recordingDotVisible
                dot.visibility = if (recordingDotVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
            }
            
            recordingHandler.postDelayed(this, 1000)
            updateSessionLine()
        }
    }

    fun updateSessionLine() {
        val showLiveRoute = settingsStore.showLiveSessionRoute
        
        val sessionId = fishingService?.getCurrentSessionId() ?: -1L
        if (sessionId != -1L && showLiveRoute) {
            lifecycleScope.launch(Dispatchers.IO) {
                val points = db.trackPointDao().getPointsForSession(sessionId)
                withContext(Dispatchers.Main) {
                    if (points.isNotEmpty()) {
                        if (sessionPolyline == null) {
                            sessionPolyline = Polyline(map).apply {
                                outlinePaint.color = Color.rgb(144, 238, 144)
                                outlinePaint.strokeWidth = 8f
                                setOnClickListener { _, _, _ -> true }
                            }
                            addOverlayBelowMarkers(sessionPolyline!!)
                        }
                        val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
                        sessionPolyline?.setPoints(geoPoints)
                        map.invalidate()
                    } else if (sessionPolyline != null) {
                        map.overlays.remove(sessionPolyline)
                        sessionPolyline = null
                        map.invalidate()
                    }
                }
            }
        } else if (sessionPolyline != null) {
            map.overlays.remove(sessionPolyline)
            sessionPolyline = null
            map.invalidate()
        }
    }

    private fun zoomToRangeOnMap(start: Long, end: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val points = db.trackPointDao().getPointsForHeatmapRange(start, end)
            val catches = db.fishCatchDao().getCatchesInRange(start, end)
            
            if (points.isEmpty() && catches.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                var minLat = Double.MAX_VALUE
                var maxLat = -Double.MAX_VALUE
                var minLon = Double.MAX_VALUE
                var maxLon = -Double.MAX_VALUE

                for (p in points) {
                    if (p.latitude < minLat) minLat = p.latitude
                    if (p.latitude > maxLat) maxLat = p.latitude
                    if (p.longitude < minLon) minLon = p.longitude
                    if (p.longitude > maxLon) maxLon = p.longitude
                }
                for (c in catches) {
                    if (c.latitude < minLat) minLat = c.latitude
                    if (c.latitude > maxLat) maxLat = c.latitude
                    if (c.longitude < minLon) minLon = c.longitude
                    if (c.longitude > maxLon) maxLon = c.longitude
                }

                if (minLat == Double.MAX_VALUE) return@withContext

                val box = BoundingBox(maxLat, maxLon, minLat, minLon)
                
                // Lisätään 10% marginaali
                val latDelta = maxLat - minLat
                val lonDelta = maxLon - minLon
                val margin = 0.1
                
                val finalMinLat = minLat - latDelta * margin
                val finalMaxLat = maxLat + latDelta * margin
                val finalMinLon = minLon - lonDelta * margin
                val finalMaxLon = maxLon + lonDelta * margin

                // Varmistetaan vähintään 400 metrin leveys
                val centerLat = (finalMaxLat + finalMinLat) / 2.0
                val centerLon = (finalMaxLon + finalMinLon) / 2.0
                val results = FloatArray(1)
                android.location.Location.distanceBetween(centerLat, finalMinLon, centerLat, finalMaxLon, results)
                val currentWidth = results[0]
                
                val finalBox = if (currentWidth < 400.0) {
                    val latRad = Math.toRadians(centerLat)
                    val metersPerDegreeLon = 111320.0 * Math.cos(latRad)
                    val degreeDelta = (400.0 / metersPerDegreeLon) / 2.0
                    BoundingBox(finalMaxLat, centerLon + degreeDelta, finalMinLat, centerLon - degreeDelta)
                } else {
                    BoundingBox(finalMaxLat, finalMaxLon, finalMinLat, finalMinLon)
                }
                
                map.zoomToBoundingBox(finalBox, true, 100)
            }
        }
    }

    private fun replaySessionOnMap(sessionId: Long, onlySessionCatches: Boolean = false) {
        replayJob?.cancel()
        isOnlySessionCatchesMode = onlySessionCatches
        
        lifecycleScope.launch(Dispatchers.IO) {
            val session = db.fishingSessionDao().getById(sessionId)
            val points = db.trackPointDao().getPointsForSession(sessionId)
            if (points.isEmpty() || session == null) return@launch

            withContext(Dispatchers.Main) {
                replayPoints = points
                replayStartTime = session.startedAt
                replayEndTime = session.endedAt ?: points.last().timestamp
                currentReplayTime = replayStartTime
                isReplayPlaying = false
                
                initReplayUI()
                updateSessionInfoText(replayStartTime, replayEndTime)
                
                if (archivedSessionPolyline != null) {
                    map.overlays.remove(archivedSessionPolyline)
                }
                archivedSessionPolyline = Polyline(map).apply {
                    outlinePaint.color = Color.BLUE
                    outlinePaint.strokeWidth = 8f
                    setOnClickListener { _, _, _ -> true }
                }
                addOverlayBelowMarkers(archivedSessionPolyline!!)
                visibleArchivedSessionId = sessionId
                
                updateReplayFrame()
                
                if (points.isNotEmpty()) {
                    var minLat = Double.MAX_VALUE
                    var maxLat = -Double.MAX_VALUE
                    var minLon = Double.MAX_VALUE
                    var maxLon = -Double.MAX_VALUE

                    for (p in points) {
                        if (p.latitude < minLat) minLat = p.latitude
                        if (p.latitude > maxLat) maxLat = p.latitude
                        if (p.longitude < minLon) minLon = p.longitude
                        if (p.longitude > maxLon) maxLon = p.longitude
                    }

                    val box = BoundingBox(maxLat, maxLon, minLat, minLon)
                    
                    // Varmistetaan vähintään 400 metrin leveys
                    val centerLat = (maxLat + minLat) / 2.0
                    val centerLon = (maxLon + minLon) / 2.0
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(centerLat, minLon, centerLat, maxLon, results)
                    val currentWidth = results[0]
                    
                    val finalBox = if (currentWidth < 400.0) {
                        // Lasketaan tarvittava pituuskaste-ero (longitude delta) 400 metrille
                        // 1 aste pituuskastetta metreinä on noin 111320 * cos(lat)
                        val latRad = Math.toRadians(centerLat)
                        val metersPerDegreeLon = 111320.0 * Math.cos(latRad)
                        val degreeDelta = (400.0 / metersPerDegreeLon) / 2.0
                        BoundingBox(maxLat, centerLon + degreeDelta, minLat, centerLon - degreeDelta)
                    } else {
                        box
                    }
                    
                    map.zoomToBoundingBox(finalBox, true, 100)
                }
                
                markerManager.setMaxTimestamp(replayStartTime)
                if (isOnlySessionCatchesMode) {
                    markerManager.setTimeRange(replayStartTime, replayStartTime, true)
                }
                
                updateReplayUI()
            }
        }
    }

    private fun restoreReplaySession(sessionId: Long, minimized: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val session = db.fishingSessionDao().getById(sessionId)
            val points = db.trackPointDao().getPointsForSession(sessionId)
            if (points.isEmpty() || session == null) return@launch

            withContext(Dispatchers.Main) {
                replayPoints = points
                replayStartTime = session.startedAt
                replayEndTime = session.endedAt ?: points.last().timestamp
                // currentReplayTime, replaySpeed ja isReplayPlaying on jo palautettu
                
                initReplayUI()
                updateSessionInfoText(replayStartTime, replayEndTime)
                
                val speedOptions = listOf("10x", "30x", "60x", "120x", "360x", "720x", "1440x", "2880x")
                val speedIndex = speedOptions.indexOf("${replaySpeed}x")
                if (speedIndex != -1) {
                    findViewById<android.widget.Spinner>(R.id.replaySpeedSpinner).setSelection(speedIndex)
                }

                if (minimized) {
                    findViewById<android.view.View>(R.id.replayPlayerContainer).visibility = android.view.View.GONE
                    findViewById<android.view.View>(R.id.replayRestoreButton).visibility = android.view.View.VISIBLE
                }

                if (archivedSessionPolyline != null) {
                    map.overlays.remove(archivedSessionPolyline)
                }
                archivedSessionPolyline = Polyline(map).apply {
                    outlinePaint.color = Color.BLUE
                    outlinePaint.strokeWidth = 8f
                    setOnClickListener { _, _, _ -> true }
                }
                addOverlayBelowMarkers(archivedSessionPolyline!!)
                visibleArchivedSessionId = sessionId
                
                // Päivitetään frame nykyisen ajan mukaan
                updateReplayFrame()
                updateReplayUI()
                
                if (isReplayPlaying) {
                    startReplayLoop()
                } else {
                    updateReplayPlayPauseIcon()
                }
            }
        }
    }

    private fun initReplayUI() {
        val playerLayout = findViewById<android.view.View>(R.id.replayPlayerLayout)
        val playerContainer = findViewById<android.view.View>(R.id.replayPlayerContainer)
        val restoreButton = findViewById<android.view.View>(R.id.replayRestoreButton)
        val playPauseButton = findViewById<android.widget.ImageButton>(R.id.replayPlayPauseButton)
        val seekBar = findViewById<android.widget.SeekBar>(R.id.replaySeekBar)
        val speedSpinner = findViewById<android.widget.Spinner>(R.id.replaySpeedSpinner)
        val minimizeButton = findViewById<android.view.View>(R.id.replayMinimizeButton)

        playerLayout.visibility = android.view.View.VISIBLE
        playerContainer.visibility = android.view.View.VISIBLE
        restoreButton.visibility = android.view.View.GONE
        
        playerContainer.setOnClickListener { 
            // Estetään klikkausten meneminen läpi kartalle
        }
        
        // Piilotetaan muut napit
        findViewById<android.view.View>(R.id.addCatchButton).visibility = android.view.View.GONE
        updateMyLocationButtonVisibility()

        playPauseButton.setOnClickListener {
            isReplayPlaying = !isReplayPlaying
            updateReplayPlayPauseIcon()
            if (isReplayPlaying) startReplayLoop()
        }

        seekBar.max = (replayEndTime - replayStartTime).toInt()
        seekBar.progress = 0
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentReplayTime = replayStartTime + progress
                    updateReplayFrame()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        val speedOptions = listOf("10x", "30x", "60x", "120x", "360x", "720x", "1440x", "2880x")
        val adapter = android.widget.ArrayAdapter(this, R.layout.spinner_item_narrow, speedOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        speedSpinner.adapter = adapter
        speedSpinner.setSelection(4) // 360x
        
        // Asetetaan valkoiset värit spinnerin tekstille
        speedSpinner.post {
            (speedSpinner.selectedView as? android.widget.TextView)?.setTextColor(android.graphics.Color.WHITE)
        }
        
        speedSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                replaySpeed = speedOptions[position].replace("x", "").toIntOrNull() ?: 60
                (view as? android.widget.TextView)?.setTextColor(android.graphics.Color.WHITE)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        minimizeButton.setOnClickListener {
            playerContainer.visibility = android.view.View.GONE
            restoreButton.visibility = android.view.View.VISIBLE
        }

        restoreButton.setOnClickListener {
            playerContainer.visibility = android.view.View.VISIBLE
            restoreButton.visibility = android.view.View.GONE
        }
        
        updateReplayPlayPauseIcon()
    }

    private fun updateReplayPlayPauseIcon() {
        val playPauseButton = findViewById<android.widget.ImageButton>(R.id.replayPlayPauseButton)
        playPauseButton.setImageResource(if (isReplayPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun startReplayLoop() {
        replayJob?.cancel()
        replayJob = lifecycleScope.launch(Dispatchers.Main) {
            val stepMs = 100L
            while (isActive && isReplayPlaying && currentReplayTime < replayEndTime) {
                val simStepMs = stepMs * replaySpeed
                currentReplayTime += simStepMs
                if (currentReplayTime > replayEndTime) currentReplayTime = replayEndTime
                
                updateReplayFrame()
                updateReplayUI()
                
                if (currentReplayTime >= replayEndTime) {
                    isReplayPlaying = false
                    updateReplayPlayPauseIcon()
                    break
                }
                delay(stepMs)
            }
        }
    }

    private fun updateReplayFrame() {
        val currentTime = currentReplayTime
        val visiblePoints = replayPoints.filter { it.timestamp <= currentTime }
        val geoPoints = visiblePoints.map { GeoPoint(it.latitude, it.longitude) }
        
        archivedSessionPolyline?.setPoints(geoPoints)
        if (isOnlySessionCatchesMode) {
            markerManager.setTimeRange(replayStartTime, currentTime, true)
        } else {
            markerManager.setMaxTimestamp(currentTime)
        }
        map.invalidate()
    }

    private fun updateReplayUI() {
        val seekBar = findViewById<android.widget.SeekBar>(R.id.replaySeekBar)
        val timeText = findViewById<android.widget.TextView>(R.id.replayTimeText)
        
        seekBar.progress = (currentReplayTime - replayStartTime).toInt()
        
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val currentStr = sdf.format(java.util.Date(currentReplayTime))
        val endStr = sdf.format(java.util.Date(replayEndTime))
        timeText.text = "$currentStr / $endStr"
    }

    private fun updateSessionInfoText(start: Long, end: Long) {
        val infoText = findViewById<android.widget.TextView>(R.id.sessionInfoText)
        
        val calStart = java.util.Calendar.getInstance()
        calStart.timeInMillis = start
        val calEnd = java.util.Calendar.getInstance()
        calEnd.timeInMillis = end
        
        val sameDay = calStart.get(java.util.Calendar.YEAR) == calEnd.get(java.util.Calendar.YEAR) &&
                calStart.get(java.util.Calendar.DAY_OF_YEAR) == calEnd.get(java.util.Calendar.DAY_OF_YEAR)
        
        val dfDate = java.text.SimpleDateFormat("d.M.yyyy", java.util.Locale.getDefault())
        val dfTime = java.text.SimpleDateFormat("H:mm", java.util.Locale.getDefault())
        val dfDateTime = java.text.SimpleDateFormat("d.M.yyyy H:mm", java.util.Locale.getDefault())
        
        val text = if (sameDay) {
            "Sessio ${dfDate.format(java.util.Date(start))} ${dfTime.format(java.util.Date(start))} - ${dfTime.format(java.util.Date(end))}"
        } else {
            "Sessio ${dfDateTime.format(java.util.Date(start))} - ${dfDateTime.format(java.util.Date(end))}"
        }
        
        infoText.text = text
        infoText.visibility = android.view.View.VISIBLE
    }

    private fun showArchivedSessionOnMap(sessionId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val session = db.fishingSessionDao().getById(sessionId)
            val points = db.trackPointDao().getPointsForSession(sessionId)
            if (points.isNotEmpty() && session != null) {
                withContext(Dispatchers.Main) {
                    if (archivedSessionPolyline != null) {
                        map.overlays.remove(archivedSessionPolyline)
                    }
                    archivedSessionPolyline = Polyline(map).apply {
                        outlinePaint.color = Color.BLUE
                        outlinePaint.strokeWidth = 8f
                        setOnClickListener { _, _, _ -> true }
                    }
                    val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
                    archivedSessionPolyline?.setPoints(geoPoints)
                    addOverlayBelowMarkers(archivedSessionPolyline!!)
                    visibleArchivedSessionId = sessionId
                    
                    updateSessionInfoText(session.startedAt, session.endedAt ?: points.last().timestamp)
                    
                    // Zoomataan session alkuun
                    map.controller.animateTo(geoPoints[0], 15.0, 500L)
                    markerManager.resetTimeRange()
                    map.invalidate()
                }
            }
        }
    }

    private fun replaySessionOnMap(sessionId: Long, speed: Int) {
        replayJob?.cancel()
        replayJob = lifecycleScope.launch(Dispatchers.IO) {
            val session = db.fishingSessionDao().getById(sessionId)
            val points = db.trackPointDao().getPointsForSession(sessionId)
            if (points.isEmpty() || session == null) return@launch

            withContext(Dispatchers.Main) {
                if (archivedSessionPolyline != null) {
                    map.overlays.remove(archivedSessionPolyline)
                }
                archivedSessionPolyline = Polyline(map).apply {
                    outlinePaint.color = Color.BLUE
                    outlinePaint.strokeWidth = 8f
                    setOnClickListener { _, _, _ -> true }
                }
                addOverlayBelowMarkers(archivedSessionPolyline!!)
                visibleArchivedSessionId = sessionId
                
                // Zoomataan session alkuun
                map.controller.animateTo(GeoPoint(points[0].latitude, points[0].longitude), 15.0, 500L)
                markerManager.setMaxTimestamp(session.startedAt)
                map.invalidate()
            }

            val startTime = session.startedAt
            val endTime = session.endedAt ?: points.last().timestamp
            val duration = endTime - startTime
            
            // Toistoväli esim 100ms välein
            val stepMs = 100L
            val simStepMs = stepMs * speed
            
            var currentSimTime = startTime
            
            while (currentSimTime <= endTime && isActive) {
                val currentTime = currentSimTime
                val visiblePoints = points.filter { it.timestamp <= currentTime }
                val geoPoints = visiblePoints.map { GeoPoint(it.latitude, it.longitude) }
                
                withContext(Dispatchers.Main) {
                    archivedSessionPolyline?.setPoints(geoPoints)
                    markerManager.setMaxTimestamp(currentTime)
                    map.invalidate()
                }
                
                delay(stepMs)
                currentSimTime += simStepMs
            }
            
            // Varmistetaan lopuksi kaikki pisteet näkyviin
            if (isActive) {
                withContext(Dispatchers.Main) {
                    val allGeoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
                    archivedSessionPolyline?.setPoints(allGeoPoints)
                    markerManager.resetTimeRange()
                    map.invalidate()
                }
            }
        }
    }

    fun hideArchivedSession() {
        replayJob?.cancel()
        isOnlySessionCatchesMode = false
        
        findViewById<android.view.View>(R.id.replayPlayerLayout).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.sessionInfoText).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.addCatchButton).visibility = android.view.View.VISIBLE
        updateMyLocationButtonVisibility()

        if (archivedSessionPolyline != null) {
            map.overlays.remove(archivedSessionPolyline)
            archivedSessionPolyline = null
            visibleArchivedSessionId = -1L
            markerManager.resetTimeRange()
            map.invalidate()
        }
    }

    fun getVisibleArchivedSessionId(): Long = visibleArchivedSessionId

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "fi.anssi.kalakartta.SESSION_ENDED" -> {
                    val sessionId = intent.getLongExtra("SESSION_ID", -1L)
                    val durationMs = intent.getLongExtra("duration_ms", 0L)
                    val distanceM = intent.getFloatExtra("distance_m", 0f)
                    updateRecordingStatusUI()
                    if (sessionId != -1L) {
                        showSessionNotesDialog(sessionId, durationMs, distanceM)
                    }
                }
                "fi.anssi.kalakartta.SESSION_ENDED_LOCATION_OFF" -> {
                    updateRecordingStatusUI()
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Kalastussessio lopetettu")
                        .setMessage("Kalastussessio on lopetettu, koska sijaintipalvelu on pois päältä.")
                        .setPositiveButton("OK", null)
                        .show()
                }
                "fi.anssi.kalakartta.SESSION_STARTED" -> {
                    // Päivitetään paikallinen väli siltä varalta että se on muuttunut palvelussa
                    recordingIntervalSeconds = settingsStore.minTrackPointInterval
                    updateRecordingStatusUI()
                }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: android.content.ComponentName, service: IBinder) {
            val binder = service as FishingSessionService.LocalBinder
            fishingService = binder.getService()
            isBound = true
            updateRecordingStatusUI()
        }

        override fun onServiceDisconnected(arg0: android.content.ComponentName) {
            isBound = false
            fishingService = null
            updateRecordingStatusUI()
        }
    }

    // Mittaustyökalu
    private var measurementMarkers = mutableListOf<Marker>()
    private var measurementPolyline: Polyline? = null
    private var measurementCursorLine: Polyline? = null
    private var measurementPoints = mutableListOf<GeoPoint>()
    private val measurementHandler = Handler(Looper.getMainLooper())
    private var measurementLongClickRunnable: Runnable? = null

    private var isFirstResume = true
    private var screenReceiver: BroadcastReceiver? = null

    private val locationProviderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                updateMyLocationButtonVisibility()
            }
        }
    }

    private var isUserScrolling = false
    private var isSelectionMode = false

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("visibleArchivedSessionId", visibleArchivedSessionId)
        outState.putLong("currentReplayTime", currentReplayTime)
        outState.putInt("replaySpeed", replaySpeed)
        outState.putBoolean("isReplayPlaying", isReplayPlaying)
        
        val playerContainer = findViewById<android.view.View>(R.id.replayPlayerContainer)
        if (playerContainer != null) {
            outState.putBoolean("replayMinimized", playerContainer.visibility == android.view.View.GONE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val crashFile = java.io.File(filesDir, "startup-crash.txt")

        if (crashFile.exists()) {
            val errorText = try {
                crashFile.readText()
            } catch (e: Exception) {
                "Virheen lukeminen epäonnistui"
            }
            AlertDialog.Builder(this)
                .setTitle("Edellinen käynnistys kaatui")
                .setMessage(errorText)
                .setPositiveButton("OK") { _, _ ->
                    crashFile.delete()
                }
                .setCancelable(false)
                .show()
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }

            super.onCreate(savedInstanceState)

            android.util.Log.d("KalaKartta", "before config init")
            Configuration.getInstance().userAgentValue = packageName
            setContentView(R.layout.activity_main)

            android.util.Log.d("KalaKartta", "before map init")
            map = findViewById(R.id.map)

            // Alustetaan tietokanta ja managerit ennen UI-päivityksiä (kuten updateMapTileSource)
            // jotta ne eivät kaadu lateinit-virheisiin (esim. heatmap)
            android.util.Log.d("KalaKartta", "before db init")
            try {
                db = AppDatabase.getInstance(this)
            } catch (e: Exception) {
                android.util.Log.e("KalaKartta", "Database initialization failed", e)
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)

                AlertDialog.Builder(this)
                    .setTitle("Tietokantavirhe")
                    .setMessage("Tietokannan avaaminen epäonnistui. Tämä johtuu yleensä sovelluspäivityksen yhteydessä tapahtuneesta migraatiovirheestä.\n\nVirhe: ${e.localizedMessage}\n\nJos virhe toistuu, voit yrittää poistaa sovelluksen ja asentaa sen uudelleen (huom: tiedot katoavat).")
                    .setPositiveButton("OK", null)
                    .show()

                // Luodaan tyhjä in-memory tietokanta, jotta sovellus ei kaadu heti kaikkialla
                db = androidx.room.Room.inMemoryDatabaseBuilder(
                    applicationContext,
                    AppDatabase::class.java
                ).build()
            }
            android.util.Log.d("KalaKartta", "after db init")

            filterManager = FilterManager(this)
            weatherService = WeatherService(this)

            importExportManager = ImportExportManager(this, db) { forceRefreshSpecies ->
                reloadMarkersFromDb(forceRefreshSpecies)
            }

            settingsManager = SettingsManager(this, db, importExportManager, onWeatherSettingsChanged = { isEnabled ->
                if (isEnabled) {
                    checkWeather(force = true)
                } else {
                    updateWeatherUI()
                }
            }, onMapSettingsChanged = {
                updateMapTileSource()
                updateFishingHeatmap()
                updateScaleBar()
            }, onSettingsActivityResult = { requestCode, resultCode, data ->
                handleActivityResult(requestCode, resultCode, data)
            }) { forceRefreshSpecies ->
                reloadMarkersFromDb(forceRefreshSpecies)
            }

            onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (findViewById<android.view.View>(R.id.replayPlayerLayout).visibility == android.view.View.VISIBLE) {
                        val sessionId = visibleArchivedSessionId
                        hideArchivedSession()
                        
                        // Avataan asetukset ja sessioiden listaus
                        settingsManager.openSettings()
                        
                        // Avataan FishingSessionActivity suoraan oikealla ID:llä
                        val intent = Intent(this@MainActivity, fi.anssi.kalakartta.ui.FishingSessionActivity::class.java)
                        intent.putExtra("EXTRA_OPEN_SESSION_ID", sessionId)
                        fishingSessionActivityLauncher.launch(intent)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            })

            // Tehtävä 1: Sovelluksen käynnistyessä aseta aina heat map-ruutujen ja reittien näyttäminen pois päältä.
            if (savedInstanceState == null) {
                settingsStore.heatmapEnabled = SettingsDefaults.HEATMAP_ENABLED
                settingsStore.fishingRoutesEnabled = SettingsDefaults.FISHING_ROUTES_ENABLED
            }

            // Migraatio vanhasta pikanäppäin-asetuksesta
            if (!settingsStore.hasHeatmapShortcutModeSetting()) {
                val oldVal = settingsStore.showHeatmapShortcut
                val newVal = if (oldVal) 3 else 0
                settingsStore.heatmapShortcutMode = newVal
            }

            markerManager = MarkerManager(this, map, db) { marker ->
                val fish = marker.relatedObject as? FishCatch
                val place = marker.relatedObject as? PlaceOfInterest

                // Poistetaan välittömästi MarkerManagerin listoista ja kartalta,
                // jotta onScroll/rebuildMarkers ei tuo sitä takaisin tietokantapoiston aikana.
                markerManager.removeMarker(marker)

                lifecycleScope.launch {
                    val deletedId = fish?.id ?: place?.id
                    android.util.Log.d("MainActivity", "Deleting from DB: ID=$deletedId")
                    withContext(Dispatchers.IO) {
                        if (fish != null) {
                            db.fishCatchDao().deleteById(fish.id)
                        }
                        if (place != null) {
                            db.placeOfInterestDao().deleteById(place.id)
                        }
                    }
                    // Lisätään väliaikainen ilmoitus käyttäjän pyynnöstä
                    android.util.Log.d("MainActivity", "Deleted from DB")
                    android.widget.Toast.makeText(this@MainActivity, "Piste poistettu", android.widget.Toast.LENGTH_SHORT).show()

                    // Kun poisto on valmistunut tietokannassa, ladataan listat uudelleen.
                    // MarkerManager pitää huolen että poistettu ID ei näy väliaikanakaan.
                    reloadMarkersFromDb()
                }
            }

            catchManager = CatchManager(this, map, db, weatherService,
                onCatchAdded = { fish ->
                    markerManager.addOrUpdateMarkerIncremental(fish, map.zoomLevelDouble, filterManager)
                },
                onPlaceAdded = { place ->
                    markerManager.addOrUpdatePlaceIncremental(place, map.zoomLevelDouble)
                }
            )

            updateMapTileSource()
            map.setMultiTouchControls(true)
            map.zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )

            isSelectionMode = intent.getBooleanExtra("EXTRA_SELECTION_MODE", false)

            if (isSelectionMode) {
                val statePrefs = getSharedPreferences("map_state", MODE_PRIVATE)
                val lat = statePrefs.getFloat("lat", 60.1695f).toDouble()
                val lon = statePrefs.getFloat("lon", 24.9354f).toDouble()
                val zoom = statePrefs.getFloat("zoom", 15.0f).toDouble()
                map.controller.setZoom(zoom)
                map.controller.setCenter(org.osmdroid.util.GeoPoint(lat, lon))
            } else {
                map.controller.setZoom(15.0)
                // Asetetaan alkusijainti Helsingin keskustaan, jos omaa sijaintia ei vielä ole
                val helsinkiCenter = org.osmdroid.util.GeoPoint(60.1695, 24.9354)
                map.controller.setCenter(helsinkiCenter)
            }

            locationOverlay = object : MyLocationNewOverlay(GpsMyLocationProvider(this), map) {
                override fun draw(canvas: android.graphics.Canvas, map: MapView, shadow: Boolean) {
                    try {
                        super.draw(canvas, map, shadow)
                    } catch (e: Exception) {
                        // Hiljennetään mahdolliset piirto-virheet (esim. Bitmap NPE)
                        android.util.Log.e("MainActivity", "Error drawing locationOverlay: ${e.message}")
                    }
                }
            }
            // Poistettu: locationOverlay.enableMyLocation() - siirretty lupien tarkistuksen jälkeen
            map.overlays.add(locationOverlay)

            requestLocationPermission()

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationOverlay.enableMyLocation()
            }

            map.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    isUserScrolling = true
                } else if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                    // Pieni viive, jotta scroll-tapahtuma ehtii tulla ennen kuin nollataan
                    map.postDelayed({ isUserScrolling = false }, 500)
                }
                false
            }

            findViewById<MaterialButton>(R.id.addCatchButton).setOnClickListener {
                catchManager.showSpeciesDialog()
            }

            findViewById<MaterialButton>(R.id.heatmapShortcutButton).setOnClickListener {
                val shortcutMode = settingsStore.heatmapShortcutMode
                val heatmapEnabled = settingsStore.heatmapEnabled
                val routesEnabled = settingsStore.fishingRoutesEnabled

                val (newHeatmap, newRoutes) = when (shortcutMode) {
                    1 -> Pair(!heatmapEnabled, routesEnabled) // Kalastetut alueet: kytkee heat mapin päälle/pois
                    2 -> Pair(heatmapEnabled, !routesEnabled) // Reitit: kytkee reitit päälle/pois
                    3 -> when { // Kalastetut alueet ja reitit: sykli
                        !heatmapEnabled && !routesEnabled -> Pair(true, false)
                        heatmapEnabled && !routesEnabled -> Pair(true, true)
                        heatmapEnabled && routesEnabled -> Pair(false, true)
                        else -> Pair(false, false)
                    }
                    else -> Pair(heatmapEnabled, routesEnabled)
                }

                // Tarkista rajat ennen päälle kytkemistä
                val checkingHeatmap = newHeatmap && !heatmapEnabled
                val checkingRoutes = newRoutes && !routesEnabled
                
                if (checkingHeatmap || checkingRoutes) {
                    settingsManager.checkLimits(checkingHeatmap, checkingRoutes) { success ->
                        if (success) {
                            settingsStore.heatmapEnabled = newHeatmap
                            settingsStore.fishingRoutesEnabled = newRoutes
                            updateFishingHeatmap()
                        }
                    }
                } else {
                    settingsStore.heatmapEnabled = newHeatmap
                    settingsStore.fishingRoutesEnabled = newRoutes
                    updateFishingHeatmap()
                }
            }

            findViewById<MaterialButton>(R.id.myLocationButton).setOnClickListener {
                // Aktivoi seuranta (keskittää sijaintiin)
                locationOverlay.enableFollowLocation()

                val myLocation = locationOverlay.myLocation
                if (myLocation != null) {
                    map.controller.animateTo(myLocation, map.zoomLevelDouble, 250L)
                } else {
                    // Jos overlaylla ei ole vielä sijaintia, kokeillaan järjestelmän LocationManageria
                    val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                    val lastKnown = try {
                        locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                            ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    } catch (e: SecurityException) {
                        null
                    }

                    if (lastKnown != null) {
                        val geoPoint = org.osmdroid.util.GeoPoint(lastKnown.latitude, lastKnown.longitude)
                        map.controller.animateTo(geoPoint, map.zoomLevelDouble, 250L)
                    } else {
                        android.widget.Toast.makeText(this, "Sijaintia ei ole vielä saatavilla", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Palautetaan toisto-tila
            if (savedInstanceState != null) {
                val sessionId = savedInstanceState.getLong("visibleArchivedSessionId", -1L)
                if (sessionId != -1L) {
                    currentReplayTime = savedInstanceState.getLong("currentReplayTime", 0L)
                    replaySpeed = savedInstanceState.getInt("replaySpeed", 60)
                    isReplayPlaying = savedInstanceState.getBoolean("isReplayPlaying", false)
                    val minimized = savedInstanceState.getBoolean("replayMinimized", false)
                    
                    // Ladataan sessio uudelleen ja asetetaan tila
                    restoreReplaySession(sessionId, minimized)
                }
            }

            findViewById<MaterialButton?>(R.id.settingsButton)?.setOnClickListener {
                if (filterManager.hasActiveFilters()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val catches = db.fishCatchDao().getAll()
                        val filteredCatches = filterManager.applyFilter(catches)
                        val places = db.placeOfInterestDao().getAll()
                        val filteredPlaces = filterManager.applyPlaceFilter(places)
                        withContext(Dispatchers.Main) {
                            settingsManager.openSettings(true, filteredCatches, filteredPlaces)
                        }
                    }
                } else {
                    settingsManager.openSettings()
                }
            }

            val measurementButton = findViewById<MaterialButton>(R.id.measurementButton)
            measurementButton.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        measurementLongClickRunnable = Runnable {
                            // Pitkä painallus (1s) -> tyhjennä
                            clearMeasurement()
                            android.widget.Toast.makeText(this, "Mittaustyökalu nollattu", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        measurementHandler.postDelayed(measurementLongClickRunnable!!, 1000)
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        measurementLongClickRunnable?.let { measurementHandler.removeCallbacks(it) }
                        // Jos ei ollut pitkä painallus, lasketaan klikkaukseksi
                        if (event.eventTime - event.downTime < 1000) {
                            handleMeasurementClick()
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        measurementLongClickRunnable?.let { measurementHandler.removeCallbacks(it) }
                        true
                    }
                    else -> false
                }
            }

            findViewById<MaterialButton>(R.id.undoMeasurementButton).setOnClickListener {
                undoLastMeasurementPoint()
            }

            findViewById<MaterialButton>(R.id.quickMapSourceButton).setOnClickListener {
                val currentApiKey = settingsStore.mmlApiKey
                val currentSource = settingsStore.mapSource

                val internalIds = arrayOf("OSM", "MML_MAASTO", "MML_ILMA", "TRAFICOM_SEA", "TRAFICOM_BOATING")

                // Suodatetaan karttapohjat, jotka on valittu pikavalintaan
                val enabledSources = internalIds.filter { id ->
                    val default = if (id.startsWith("MML_")) currentApiKey.isNotEmpty() else true
                    settingsStore.isQuickMapSourceEnabled(id, default)
                }

                if (enabledSources.isNotEmpty()) {
                    val currentIndex = enabledSources.indexOf(currentSource)
                    val nextIndex = (currentIndex + 1) % enabledSources.size
                    val nextSource = enabledSources[nextIndex]

                    settingsStore.mapSource = nextSource
                    updateMapTileSource()
                }
            }

            android.util.Log.d("KalaKartta", "after db init")
            
            // Esitäyttö taustasäikeessä
            lifecycleScope.launch(Dispatchers.IO) {
                db.initializeDefaults()
            }

            map.addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    // Jos käyttäjä skrollaa itse, poistetaan automaattinen seuranta
                    if (isUserScrolling) {
                        updateHeatmapDelayed()
                        locationOverlay.disableFollowLocation()
                    }

                    // Kun ollaan zoomed in, päivitetään näkyvät markerit (clipping)
                    if (map.zoomLevelDouble >= 13.0) {
                        markerManager.setMarkersVisible(true, map.zoomLevelDouble, forceRebuild = true)
                    }

                    if (measurementPoints.isNotEmpty()) {
                        val center = map.mapCenter as GeoPoint
                        if (measurementCursorLine == null) {
                            measurementCursorLine = Polyline(map).apply {
                                outlinePaint.color = Color.RED
                                outlinePaint.strokeWidth = 3f
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                                setOnClickListener { _, _, _ -> true }
                            }
                            addOverlayBelowMarkers(measurementCursorLine!!)
                        }
                        measurementCursorLine?.setPoints(listOf(measurementPoints.last(), center))

                        val textView = findViewById<TextView>(R.id.measurementText)
                        val lastPoint = measurementPoints.last()
                        val distanceToCenter = lastPoint.distanceToAsDouble(center)

                        var totalDistance = 0.0
                        for (i in 0 until measurementPoints.size - 1) {
                            totalDistance += measurementPoints[i].distanceToAsDouble(measurementPoints[i + 1])
                        }
                        totalDistance += distanceToCenter

                        val distStr = SessionStatsFormatter.formatDistance(distanceToCenter)
                        val totalStr = SessionStatsFormatter.formatDistance(totalDistance)

                        if (measurementPoints.size == 1) {
                            textView.text = "${getString(R.string.distance)} $distStr."
                        } else {
                            textView.text = "${getString(R.string.distance)} $distStr, ${getString(R.string.route)} $totalStr"
                        }
                        map.invalidate()
                    }
                    return false
                }
                override fun onZoom(event: ZoomEvent?): Boolean {
                    updateHeatmapDelayed()
                    updateMarkersVisibility()
                    return true
                }
            })

            android.util.Log.d("KalaKartta", "before loadCatches")
            loadCatches()
            android.util.Log.d("KalaKartta", "after loadCatches")

            updateMarkersVisibility()
            updateFilterStatusUI()
            updateDefaultFishermanUI()

            isSelectionMode = intent.getBooleanExtra("EXTRA_SELECTION_MODE", false)
            if (isSelectionMode) {
                findViewById<android.view.View>(R.id.measurementButton).visibility = android.view.View.GONE
                // Nollataan aluerajaus valintatilaan mentäessä, jotta nähdään kaikki pisteet
                val currentFilters = filterManager.getFilters()
                if (currentFilters.latNorth != null) {
                    filterManager.saveFilters(currentFilters.copy(
                        latNorth = null, latSouth = null, lonEast = null, lonWest = null
                    ))
                    loadCatches() // Päivitetään näkyvät pisteet
                }

                findViewById<android.view.View>(R.id.selectionModeLayout).visibility = android.view.View.VISIBLE
                findViewById<android.view.View>(R.id.addCatchButton).visibility = android.view.View.GONE
                findViewById<android.view.View>(R.id.settingsButton).visibility = android.view.View.GONE
                findViewById<android.view.View>(R.id.mapCrosshair).visibility = android.view.View.GONE

                findViewById<android.widget.Button>(R.id.cancelSelectionButton).setOnClickListener {
                    setResult(RESULT_CANCELED)
                    finish()
                }

                findViewById<android.widget.Button>(R.id.confirmSelectionButton).setOnClickListener {
                    val bounds = map.boundingBox
                    val resultIntent = Intent().apply {
                        putExtra("EXTRA_LAT_NORTH", bounds.latNorth)
                        putExtra("EXTRA_LAT_SOUTH", bounds.latSouth)
                        putExtra("EXTRA_LON_EAST", bounds.lonEast)
                        putExtra("EXTRA_LON_WEST", bounds.lonWest)
                    }

                    // Kaapataan kuvakaappaus thumbnailia varten
                    try {
                        val bitmap = android.graphics.Bitmap.createBitmap(map.width, map.height, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)

                        // Piilotetaan mittaustyökalu kuvakaappauksen ajaksi
                        val mLayout = findViewById<LinearLayout>(R.id.measurementLayout)
                        val mButton = findViewById<MaterialButton>(R.id.measurementButton)
                        val oldLayoutVis = mLayout.visibility
                        val oldButtonVis = mButton.visibility
                        mLayout.visibility = android.view.View.GONE
                        mButton.visibility = android.view.View.GONE

                        map.draw(canvas)

                        mLayout.visibility = oldLayoutVis
                        mButton.visibility = oldButtonVis

                        // Pienennetään thumbnailia
                        val thumbnail = android.graphics.Bitmap.createScaledBitmap(bitmap, 160, 120, true)
                        val thumbFile = java.io.File(cacheDir, "area_thumb.jpg")
                        val out = java.io.FileOutputStream(thumbFile)
                        thumbnail.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                        out.flush()
                        out.close()
                        resultIntent.putExtra("EXTRA_THUMB_PATH", thumbFile.absolutePath)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Thumbnail capture failed", e)
                    }

                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }

            // Automaattinen kohdistus sovelluksen avauksessa
            val autoCenter = settingsStore.autoCenterOnStart

            // Tarkistetaan oletuskalastaja vain jos sovellus on asennettu tai päivitetty
            val lastVersionName = settingsStore.lastVersionName
            val currentVersionName = try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                pInfo.versionName ?: ""
            } catch (e: Exception) {
                ""
            }

            val currentFisherman = settingsStore.defaultFisherman
            if (currentVersionName != lastVersionName) {
                if (currentFisherman.isEmpty()) {
                    checkDefaultFisherman()
                }
                settingsStore.lastVersionName = currentVersionName
            }

            if (autoCenter && !isSelectionMode) {
                locationOverlay.runOnFirstFix {
                    lifecycleScope.launch(Dispatchers.Main) {
                        val myLocation = locationOverlay.myLocation
                        if (myLocation != null) {
                            map.controller.animateTo(myLocation, map.zoomLevelDouble, 500L)
                        }
                    }
                }
            }

            if (crashFile.exists()) {
                crashFile.delete()
            }
            
            checkUnfinishedSessions()
            settingsManager.checkShowUserManual()
        } catch (t: Throwable) {
            crashFile.writeText(t.stackTraceToString())
            throw t
        }
    }

    private fun updateMapTileSource() {
        val mapSource = settingsStore.mapSource
        val apiKey = settingsStore.mmlApiKey

        when (mapSource) {
            "TRAFICOM_SEA" -> {
                map.setTileSource(TraficomTileSource())
                updateUIColors(true)
            }
            "TRAFICOM_BOATING" -> {
                map.setTileSource(VeneilykarttaTileSource())
                updateUIColors(true)
            }
            "MML_MAASTO" -> {
                map.setTileSource(MMLTileSource("MML Maastokartta", "maastokartta", apiKey))
                updateUIColors(true)
            }
            "MML_ILMA" -> {
                map.setTileSource(MMLTileSource("MML Ilmakuva", "ortokuva", apiKey))
                updateUIColors(true)
            }
            else -> {
                map.setTileSource(TileSourceFactory.MAPNIK)
                updateUIColors(false)
            }
        }
        updateScaleBar()
        updateDefaultFishermanUI()
    }

    private fun updateScaleBar() {
        val showScale = settingsStore.showScaleBar
        val showMeasurement = settingsStore.showMeasurementTool
        val showQuickMap = settingsStore.showQuickMapSource
        val mapSource = settingsStore.mapSource
        val useBlack = mapSource == "MML_MAASTO" || mapSource == "MML_ILMA" || mapSource == "TRAFICOM_SEA" || mapSource == "TRAFICOM_BOATING"

        val measurementButton = findViewById<MaterialButton>(R.id.measurementButton)
        if (showMeasurement) {
            measurementButton.visibility = android.view.View.VISIBLE
            findViewById<MaterialButton>(R.id.undoMeasurementButton).visibility = if (measurementPoints.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        } else {
            measurementButton.visibility = android.view.View.GONE
            findViewById<MaterialButton>(R.id.undoMeasurementButton).visibility = android.view.View.GONE
            if (measurementPoints.isNotEmpty()) {
                clearMeasurement()
            }
        }

        findViewById<MaterialButton>(R.id.quickMapSourceButton).visibility = if (showQuickMap) android.view.View.VISIBLE else android.view.View.GONE

        // Poistetaan vanha jos on
        scaleBarOverlay?.let { map.overlays.remove(it) }

        if (showScale) {
            val density = resources.displayMetrics.density
            val buttonMargin = resources.getDimensionPixelSize(R.dimen.button_margin_bottom)
            val targetWidth = (48 * density).toInt()

            val color = if (useBlack) {
                ContextCompat.getColor(this, android.R.color.black)
            } else {
                ContextCompat.getColor(this, android.R.color.white)
            }

            // Käytetään omaa ScaleBarOverlay-aliluokkaa, jolla pakotetaan pituus ja väri
            val scaleBar = object : ScaleBarOverlay(map) {
                override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
                    if (shadow) return

                    // Pakotetaan pituus heijastuksella juuri ennen piirtoa,
                    // jos osmdroid yrittää laskea sen uudelleen
                    try {
                        val fields = listOf("mLineWidth", "lineWidth", "mMinWidth", "minWidth", "mMaxWidth", "maxWidth")
                        for (name in fields) {
                            try {
                                val field = ScaleBarOverlay::class.java.getDeclaredField(name)
                                field.isAccessible = true
                                field.set(this, targetWidth)
                            } catch (e: NoSuchFieldException) {}
                        }
                    } catch (e: Exception) {}

                    super.draw(canvas, mapView, shadow)
                }
            }.apply {
                setAlignBottom(true)

                // yOffset mitataan pohjasta ylöspäin (koska setAlignBottom(true)).
                val yOffset = buttonMargin - (22 * density).toInt()
                val xOffset = 60
                setScaleBarOffset(xOffset, yOffset)

                setTextSize(density * 12)

                // Asetetaan värit
                barPaint.color = color
                textPaint.color = color
            }
            map.overlays.add(scaleBar)
            scaleBarOverlay = scaleBar
        } else {
            scaleBarOverlay = null
        }

        // Nappien paikka ei enää muutu mittakaavan mukaan
        val myLocationButton = findViewById<MaterialButton>(R.id.myLocationButton)
        val addCatchButton = findViewById<MaterialButton>(R.id.addCatchButton)

        val baseMargin = resources.getDimensionPixelSize(R.dimen.button_margin_bottom)

        val myLocParams = myLocationButton.layoutParams as FrameLayout.LayoutParams
        myLocParams.bottomMargin = baseMargin
        myLocationButton.layoutParams = myLocParams

        val addCatchParams = addCatchButton.layoutParams as FrameLayout.LayoutParams
        addCatchParams.bottomMargin = baseMargin
        addCatchButton.layoutParams = addCatchParams

        myLocationButton.requestLayout()
        addCatchButton.requestLayout()
        updateDefaultFishermanUI()
        val shortcutMode = settingsStore.heatmapShortcutMode
        findViewById<MaterialButton>(R.id.heatmapShortcutButton).visibility = 
            if (shortcutMode > 0) android.view.View.VISIBLE else android.view.View.GONE

        updateFishingHeatmap()
        
        map.invalidate()
    }

    private fun updateUIColors(useBlack: Boolean) {
        val color = if (useBlack) {
            ContextCompat.getColor(this, android.R.color.black)
        } else {
            ContextCompat.getColor(this, android.R.color.white)
        }

        findViewById<TextView>(R.id.mapCrosshair).setTextColor(color)

        val buttons = listOf(
            findViewById<MaterialButton>(R.id.myLocationButton),
            findViewById<MaterialButton>(R.id.addCatchButton),
            findViewById<MaterialButton>(R.id.settingsButton),
            findViewById<MaterialButton>(R.id.quickMapSourceButton),
            findViewById<MaterialButton>(R.id.heatmapShortcutButton),
            findViewById<MaterialButton>(R.id.measurementButton),
            findViewById<MaterialButton>(R.id.undoMeasurementButton)
        )

        buttons.forEach { button ->
            if (button.id == R.id.measurementButton) {
                val bitmap = createMeasurementPinBitmap(color)
                button.icon = BitmapDrawable(resources, bitmap)
            }
            button.iconTint = android.content.res.ColorStateList.valueOf(color)
            button.strokeColor = android.content.res.ColorStateList.valueOf(color)
        }
    }

    private fun loadCatches() {
        lifecycleScope.launch(Dispatchers.IO) {
            val catches = db.fishCatchDao().getAll()
            val filteredCatches = filterManager.applyFilter(catches)
            val places = db.placeOfInterestDao().getAll()
            val filteredPlaces = filterManager.applyPlaceFilter(places)
            withContext(Dispatchers.Main) {
                markerManager.setAllCatches(filteredCatches)
                markerManager.setAllPlaces(filteredPlaces)
            }
        }
    }

    private fun updateFilterStatusUI() {
        val layout = findViewById<android.view.View>(R.id.filterStatusLayout)
        val text = findViewById<android.widget.TextView>(R.id.filterStatusText)
        val windView = findViewById<WindDirectionView>(R.id.filterWindView)

        val filters = filterManager.getFilters()
        if (filterManager.hasActiveFilters()) {
            layout.visibility = android.view.View.VISIBLE
            if (filters.windMin != null && filters.windMax != null) {
                windView.visibility = android.view.View.VISIBLE
                windView.setRange(filters.windMin, filters.windMax)
            } else {
                windView.visibility = android.view.View.GONE
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val description = filterManager.getFilterDescription()
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed && filterManager.hasActiveFilters()) {
                        text.text = description
                    }
                }
            }
        } else {
            layout.visibility = android.view.View.GONE
        }
    }

    private fun reloadMarkersFromDb(forceRefreshSpecies: Boolean = false) {
        if (forceRefreshSpecies) {
            markerManager.rebuildMarkers(map.zoomLevelDouble, forceRefreshSpecies = true)
        }
        loadCatches()
        updateFishingHeatmap()
    }

    private fun checkDefaultFisherman() {
        val currentFisherman = settingsStore.defaultFisherman

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val input = android.widget.EditText(this).apply {
            setText(currentFisherman)
            hint = "Esim. Matti"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            }
        }
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Oletuskalastaja")
            .setMessage("Syötä oletuskalastajan nimi (valinnainen):")
            .setView(layout)
            .setPositiveButton("Tallenna") { _, _ ->
                val newFisherman = input.text.toString().trim()
                settingsStore.defaultFisherman = newFisherman
                updateDefaultFishermanUI()
            }
            .setNegativeButton("Ohita", null)
            .show()
    }

    private fun updateDefaultFishermanUI() {
        val rawFisherman = settingsStore.defaultFisherman
        val showOnMap = settingsStore.showFishermanOnMap
        val textView = findViewById<TextView>(R.id.defaultFishermanText) ?: return

        if (showOnMap && rawFisherman.isNotEmpty()) {
            fun formatName(name: String): String {
                return name.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { part ->
                    part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
            val fisherman = formatName(rawFisherman)
            textView.visibility = android.view.View.VISIBLE
            textView.text = fisherman

            val mapSource = settingsStore.mapSource
            val useBlack = mapSource == "MML_MAASTO" || mapSource == "MML_ILMA"
            val color = if (useBlack) {
                ContextCompat.getColor(this, android.R.color.black)
            } else {
                ContextCompat.getColor(this, android.R.color.white)
            }
            textView.setTextColor(color)

            // Säädetään marginaali vastaamaan mittakaavajanaa
            val density = resources.displayMetrics.density
            val buttonMargin = resources.getDimensionPixelSize(R.dimen.button_margin_bottom)
            val yOffset = buttonMargin - (22 * density).toInt()

            val params = textView.layoutParams as FrameLayout.LayoutParams
            params.bottomMargin = yOffset
            textView.layoutParams = params
        } else {
            textView.visibility = android.view.View.GONE
        }
    }

    private fun checkWeather(force: Boolean = false) {
        if (force) {
            weatherCheckDone = false
        }

        val isEnabled = settingsStore.weatherEnabled
        if (!isEnabled) return

        // Haetaan kaikki sääasemat muistiin taustalla, jos niitä ei vielä ole
        weatherService.fetchAllStations()

        if (weatherCheckDone) return

        val myLocation = if (::locationOverlay.isInitialized) locationOverlay.myLocation else null
        if (myLocation == null) {
            // Poistettu automaattinen virheilmoitus puuttuvasta sijainnista
            return
        }

        weatherCheckDone = true

        weatherService.fetchNearestStation(myLocation.latitude, myLocation.longitude, System.currentTimeMillis()) { station, error ->
            lifecycleScope.launch(Dispatchers.Main) {
                if (error != null) {
                    // Epäonnistumisesta ei välttämättä tarvitse ilmoittaa käyttäjälle automaattisessa haussa
                } else if (station != null) {
                    lastFoundStation = station
                    updateWeatherUI()
                }
            }
        }
    }

    private fun updateWeatherUI() {
        val weatherStationText = findViewById<TextView>(R.id.weatherStationText)
        weatherStationText.visibility = android.view.View.GONE
    }

    private fun updateFishingHeatmap() {
        if (!::db.isInitialized) return
        val heatmapEnabled = settingsStore.heatmapEnabled
        val routesEnabled = settingsStore.fishingRoutesEnabled

        if (heatmapEnabled || routesEnabled) {
            if (heatmapOverlay == null) {
                heatmapOverlay = FishingHeatmapOverlay(this, db, map)
                map.overlays.add(0, heatmapOverlay) // Lisätään pohjalle
            } else {
                heatmapOverlay?.refreshData()
            }
        } else {
            heatmapOverlay?.let {
                map.overlays.remove(it)
                heatmapOverlay = null
            }
        }
        
        val shortcutButton = findViewById<MaterialButton>(R.id.heatmapShortcutButton)
        if (heatmapEnabled || routesEnabled) {
            val colorStr = settingsStore.getHeatmapColor("Punainen")
            val baseColor = when (colorStr) {
                "Violetti" -> Color.rgb(128, 0, 128)
                "Vihreä" -> Color.GREEN
                else -> Color.RED
            }
            // Alfa 80 (n. 31%) kuten aiemmin, mutta valitulla värillä
            val alpha = if (heatmapEnabled && routesEnabled) 160 else 80
            val shortcutColor = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            shortcutButton.backgroundTintList = ColorStateList.valueOf(shortcutColor)
            
            // Muutetaan myös reunus painikkeen väriseksi jos heatmap tai reitit on päällä, 
            // jotta se erottuu pikanäppäimenä mutta osoittaa tilan
            shortcutButton.strokeColor = ColorStateList.valueOf(baseColor)
        } else {
            shortcutButton.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            
            // Palautetaan normaali reunusväri karttapohjan mukaan
            val currentMapSource = settingsStore.mapSource
            val useBlack = currentMapSource == "MML_MAASTO" || currentMapSource == "MML_ILMA" || currentMapSource == "TRAFICOM_SEA" || currentMapSource == "TRAFICOM_BOATING"
            val color = if (useBlack) {
                ContextCompat.getColor(this, android.R.color.black)
            } else {
                ContextCompat.getColor(this, android.R.color.white)
            }
            shortcutButton.strokeColor = ColorStateList.valueOf(color)
        }
        
        map.invalidate()
    }

    private fun updateHeatmapDelayed() { heatmapUpdateHandler.removeCallbacks(heatmapUpdateRunnable); heatmapUpdateHandler.postDelayed(heatmapUpdateRunnable, 500) }

    private fun updateMarkersVisibility() {

        // Näytetään pisteet laajemmalla zoom-alueella (alk. tasolta 1.0)
        // Optimointi on tehty MarkerManagerin kuvakevälimuistilla ja klusteroinnilla
        markerManager.setMarkersVisible(map.zoomLevelDouble >= 1.0, map.zoomLevelDouble)
    }

    private fun createMeasurementPinBitmap(color: Int): Bitmap {
        val size = (32 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Piirretään pallo (nuppineulan pää)
        // Nuppi halkaisijaltaan puolet nykyisestä -> säde puoleen.
        // Aiemmin säde oli size/4f, nyt size/8f.
        paint.color = color
        canvas.drawCircle(size / 2f, size / 4f, size / 8f, paint)

        // Piirretään neula
        // Pidennä nuppineulan vartta 30 %.
        // Aiemmin pituus oli (size * 0.9f) - (size / 3f + size / 4f) = 0.9 - 0.583 = 0.317 size
        // Uusi pituus: 0.317 * 1.3 = 0.412 size.
        // Uusi loppupiste: 0.25 (alku) + 0.125 (nupin säde) + 0.412 = 0.787 size?
        // Itse asiassa helpompi:
        val startY = size / 4f + size / 8f
        val originalLength = size * 0.9f - (size / 3f + size / 4f)
        val newLength = originalLength * 1.3f
        val endY = startY + newLength

        paint.strokeWidth = size / 10f
        canvas.drawLine(size / 2f, startY, size / 2f, endY, paint)

        return bitmap
    }

    override fun onStart() {
        super.onStart()
        
        // Luetaan tallennusväli asetuksista
        recordingIntervalSeconds = settingsStore.minTrackPointInterval

        // Yhdistetään FishingSessionServiceen
        Intent(this, FishingSessionService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        val filter = IntentFilter().apply {
            addAction("fi.anssi.kalakartta.SESSION_STARTED")
            addAction("fi.anssi.kalakartta.SESSION_ENDED")
            addAction("fi.anssi.kalakartta.SESSION_ENDED_LOCATION_OFF")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(sessionReceiver, filter)
        }
        updateRecordingStatusUI()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        try {
            unregisterReceiver(sessionReceiver)
        } catch (e: Exception) {}
        recordingHandler.removeCallbacks(recordingBlinkRunnable)
    }

    fun startFishingSession(locationCheckInterval: Int, minInterval: Int, maxInterval: Int, minDistance: Int) {
        // Poistetaan vanha arkistoitu reitti jos sellainen on näkyvissä
        hideArchivedSession()

        recordingIntervalSeconds = minInterval
        val intent = Intent(this, FishingSessionService::class.java).apply {
            putExtra("LOCATION_CHECK_INTERVAL", locationCheckInterval)
            putExtra("MIN_INTERVAL", minInterval)
            putExtra("MAX_INTERVAL", maxInterval)
            putExtra("MIN_DISTANCE", minDistance)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // Yhdistetään uudelleen jos ei oltu yhdistettynä
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        updateRecordingStatusUI(true)
    }

    fun stopFishingSession() {
        fishingService?.stopSession()
        updateRecordingStatusUI(false)
        updateSessionLine()
    }

    private fun checkUnfinishedSessions() {
        if (FishingSessionService.isRunning) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            val unfinishedSession = db.fishingSessionDao().getActiveSession()
            if (unfinishedSession != null) {
                val lastPoint = db.trackPointDao().getLastPointForSession(unfinishedSession.id)
                val lastTime = lastPoint?.timestamp ?: unfinishedSession.startedAt
                
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Keskeneräinen sessio löytyi")
                        .setMessage("Haluatko jatkaa aiempaa sessiota vai päättää sen viimeiseen reittipisteeseen?")
                        .setPositiveButton("Jatka sessiota") { _, _ ->
                            continueFishingSession(unfinishedSession)
                        }
                        .setNegativeButton("Päätä sessio viimeiseen pisteeseen") { _, _ ->
                            finishUnfinishedSession(unfinishedSession, lastTime)
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    private fun continueFishingSession(session: FishingSession) {
        val locInt = settingsStore.locationCheckInterval
        val minInt = settingsStore.minTrackPointInterval
        val maxInt = settingsStore.maxTrackPointInterval
        val minDist = settingsStore.minTrackPointDistance

        val intent = Intent(this, FishingSessionService::class.java).apply {
            putExtra("LOCATION_CHECK_INTERVAL", locInt)
            putExtra("MIN_INTERVAL", minInt)
            putExtra("MAX_INTERVAL", maxInt)
            putExtra("MIN_DISTANCE", minDist)
            putExtra("CONTINUE_SESSION_ID", session.id)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        updateRecordingStatusUI(true)
    }

    private fun finishUnfinishedSession(session: FishingSession, endTime: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val points = db.trackPointDao().getPointsForSession(session.id)
            val actualStart = points.firstOrNull()?.timestamp ?: session.startedAt
            val actualEnd = points.lastOrNull()?.timestamp ?: endTime
            
            db.fishingSessionDao().update(session.copy(startedAt = actualStart, endedAt = actualEnd))
            withContext(Dispatchers.Main) {
                updateSessionLine()
                android.widget.Toast.makeText(this@MainActivity, "Sessio päätetty", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getFishingService() = fishingService

    private fun updateRecordingStatusUI(overrideRecording: Boolean? = null) {
        val recordingLayout = findViewById<android.view.View>(R.id.recordingStatusLayout) ?: return
        val isRecording = overrideRecording ?: (fishingService?.isRecording() ?: false)
        val dot = findViewById<android.view.View>(R.id.recordingDot)
        val statusText = findViewById<android.widget.TextView>(R.id.recordingStatusText)
        
        if (isRecording) {
            recordingLayout.visibility = android.view.View.VISIBLE
            statusText?.text = "REC"
            
            isBlinking = true
            recordingHandler.removeCallbacks(recordingBlinkRunnable)
            recordingDotVisible = true
            dot?.visibility = android.view.View.VISIBLE
            recordingHandler.postDelayed(recordingBlinkRunnable, 1000)
            updateSessionLine()
        } else {
            recordingLayout.visibility = android.view.View.GONE
            isBlinking = false
            recordingHandler.removeCallbacks(recordingBlinkRunnable)
            dot?.visibility = android.view.View.GONE
            updateSessionLine()
        }
    }

    private fun showSessionNotesDialog(sessionId: Long, durationMs: Long, distanceM: Float) {
        if (isFinishing || isDestroyed) return
        
        lifecycleScope.launch {
            val pointCount = withContext(Dispatchers.IO) {
                db.trackPointDao().getPointCountForSession(sessionId)
            }

            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Kalastussessio lopetettu")
            
            val layout = android.widget.LinearLayout(this@MainActivity)
            layout.orientation = android.widget.LinearLayout.VERTICAL
            layout.setPadding(48, 24, 48, 24)

            // Session kesto ja matka
            val statsLabel = android.widget.TextView(this@MainActivity)
            statsLabel.text = SessionStatsFormatter.formatSummary(durationMs, distanceM.toDouble())
            statsLabel.textSize = 16f
            statsLabel.setPadding(0, 0, 0, 24)
            layout.addView(statsLabel)

            if (pointCount == 0) {
                val noPointsLabel = android.widget.TextView(this@MainActivity)
                noPointsLabel.text = "Kalastussessiossa ei ole reittipisteitä. Kalastussessiota ei tallenneta."
                noPointsLabel.textSize = 16f
                noPointsLabel.setTextColor(Color.RED)
                layout.addView(noPointsLabel)
                
                builder.setView(layout)
                builder.setPositiveButton("Sulje", null)
                
                // Poistetaan sessio koska reittipisteitä ei ole
                withContext(Dispatchers.IO) {
                    db.fishingSessionDao().deleteById(sessionId)
                }
            } else {
                val label = android.widget.TextView(this@MainActivity)
                label.text = "Kalastussession huomiot:"
                label.textSize = 16f
                layout.addView(label)
                
                val input = android.widget.EditText(this@MainActivity)
                input.hint = "Lisää muistiinpanoja sessiosta..."
                input.setLines(3)
                input.gravity = android.view.Gravity.TOP
                
                layout.addView(input)
                builder.setView(layout)

                builder.setPositiveButton("Tallenna") { _, _ ->
                    val notes = input.text.toString()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val session = db.fishingSessionDao().getById(sessionId)
                        if (session != null) {
                            db.fishingSessionDao().update(session.copy(notes = notes))
                        }
                    }
                }
                builder.setNegativeButton("Sulje", null)
            }
            builder.show()
        }
    }

    private fun clearMeasurement() {
        measurementPoints.clear()
        measurementMarkers.forEach { map.overlays.remove(it) }
        measurementMarkers.clear()
        measurementPolyline?.let { map.overlays.remove(it) }
        measurementPolyline = null
        measurementCursorLine?.let { map.overlays.remove(it) }
        measurementCursorLine = null
        findViewById<MaterialButton>(R.id.undoMeasurementButton).visibility = android.view.View.GONE
        findViewById<LinearLayout>(R.id.measurementLayout).visibility = android.view.View.GONE
        map.invalidate()
    }

    private fun handleMeasurementClick() {
        val center = map.mapCenter as GeoPoint
        measurementPoints.add(center)

        val marker = Marker(map).apply {
            position = center
            // Luodaan punainen nuppineula koodilla
            val bitmap = createMeasurementPinBitmap(Color.RED)
            icon = BitmapDrawable(resources, bitmap)

            // Lasketaan ankkuri uudelleen pidentyneen varren mukaan
            val size = (32 * resources.displayMetrics.density).toInt()
            val startY = size / 4f + size / 8f
            val originalLength = size * 0.9f - (size / 3f + size / 4f)
            val newLength = originalLength * 1.3f
            val endY = startY + newLength
            setAnchor(Marker.ANCHOR_CENTER, endY / size.toFloat())

            // Estetään infoikkunan aukeaminen
            setOnMarkerClickListener { _, _ -> true }
        }
        map.overlays.add(marker)
        measurementMarkers.add(marker)

        if (measurementPoints.size > 1) {
            if (measurementPolyline == null) {
                measurementPolyline = Polyline(map).apply {
                    outlinePaint.color = Color.RED
                    outlinePaint.strokeWidth = 5f
                    setOnClickListener { _, _, _ -> true }
                }
                addOverlayBelowMarkers(measurementPolyline!!)
            }
            measurementPolyline?.setPoints(measurementPoints)
        }

        findViewById<MaterialButton>(R.id.undoMeasurementButton).visibility = android.view.View.VISIBLE
        updateMeasurementUI()
        map.invalidate()
    }

    private fun undoLastMeasurementPoint() {
        if (measurementPoints.isNotEmpty()) {
            val lastPoint = measurementPoints.removeAt(measurementPoints.size - 1)
            val lastMarker = measurementMarkers.removeAt(measurementMarkers.size - 1)
            map.overlays.remove(lastMarker)

            if (measurementPoints.isEmpty()) {
                measurementPolyline?.let { map.overlays.remove(it) }
                measurementPolyline = null
                measurementCursorLine?.let { map.overlays.remove(it) }
                measurementCursorLine = null
                findViewById<MaterialButton>(R.id.undoMeasurementButton).visibility = android.view.View.GONE
            } else {
                measurementPolyline?.setPoints(measurementPoints)
                // Päivitetään cursor-viiva
                val center = map.mapCenter as GeoPoint
                measurementCursorLine?.setPoints(listOf(measurementPoints.last(), center))
            }
            updateMeasurementUI()
            map.invalidate()
        }
    }

    private fun updateMeasurementUI() {
        val layout = findViewById<LinearLayout>(R.id.measurementLayout)
        val textView = findViewById<TextView>(R.id.measurementText)

        if (measurementPoints.isEmpty()) {
            layout.visibility = android.view.View.GONE
            return
        }

        layout.visibility = android.view.View.VISIBLE

        if (measurementPoints.size == 1) {
            textView.text = getString(R.string.measurement_start_hint)
        } else {
            val lastPoint = measurementPoints.last()
            val secondLastPoint = measurementPoints[measurementPoints.size - 2]
            val distanceToLast = secondLastPoint.distanceToAsDouble(lastPoint)

            var totalDistance = 0.0
            for (i in 0 until measurementPoints.size - 1) {
                totalDistance += measurementPoints[i].distanceToAsDouble(measurementPoints[i + 1])
            }

            val distStr = SessionStatsFormatter.formatDistance(distanceToLast)
            val totalStr = SessionStatsFormatter.formatDistance(totalDistance)

            if (measurementPoints.size == 2) {
                textView.text = "${getString(R.string.distance)} $distStr."
            } else {
                textView.text = "${getString(R.string.distance)} $distStr, ${getString(R.string.route)} $totalStr"
            }

            android.widget.Toast.makeText(this, getString(R.string.measurement_next_hint), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra("EXTRA_ZOOM_TO_SUMMARY", false)) {
            val start = intent.getLongExtra("EXTRA_START_TIME", -1L)
            val end = intent.getLongExtra("EXTRA_END_TIME", -1L)
            if (start != -1L && end != -1L) {
                // Suljetaan dialogit
                settingsManager.closeSettings()
                
                // Asetetaan suodattimet vastaamaan aikaväliä, jotta pisteet näkyvät kartalla
                filterManager.clearFilters()
                filterManager.saveFilters(FilterManager.Filters(startDate = start, endDate = end))
                reloadMarkersFromDb()
                updateFilterStatusUI()
                
                zoomToRangeOnMap(start, end)
            }
            intent.removeExtra("EXTRA_ZOOM_TO_SUMMARY")
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        
        intent?.let { handleIntent(it) }
        
        updateRecordingStatusUI()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationOverlay.enableMyLocation()
        }

        updateMyLocationButtonVisibility()
        updateScaleBar()
        
        // Rekisteröidään sijaintipalveluiden seuranta
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationProviderReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(locationProviderReceiver, filter)
        }

        // Rekisteröidään näytön avauksen seuranta
        val screenFilter = IntentFilter(Intent.ACTION_SCREEN_ON)
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    if (settingsStore.autoCenterOnStart && !isSelectionMode) {
                        val myLocation = locationOverlay.myLocation
                        if (myLocation != null) {
                            map.controller.animateTo(myLocation, map.zoomLevelDouble, 500L)
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screenFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, screenFilter)
        }

        // Yritetään näyttää sääasema jos se on vielä näyttämättä
        if (!weatherCheckDone) {
            checkWeather()
        }

        // Päivitetään kartta ja suodattimet
        reloadMarkersFromDb()
        updateFilterStatusUI()

        isFirstResume = false
    }

    private fun saveMapState() {
        val prefs = getSharedPreferences("map_state", MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("lat", map.mapCenter.latitude.toFloat())
            putFloat("lon", map.mapCenter.longitude.toFloat())
            putFloat("zoom", map.zoomLevelDouble.toFloat())
            apply()
        }
    }

    override fun onPause() {
        saveMapState()
        try {
            unregisterReceiver(locationProviderReceiver)
        } catch (_: IllegalArgumentException) {
        }
        try {
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (_: IllegalArgumentException) {
        }
        locationOverlay.disableMyLocation()
        map.onPause()
        super.onPause()
    }

    private fun updateMyLocationButtonVisibility() {
        if (findViewById<android.view.View>(R.id.replayPlayerLayout).visibility == android.view.View.VISIBLE) {
            findViewById<MaterialButton>(R.id.myLocationButton).visibility = android.view.View.GONE
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = try {
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            false
        }
        val isNetworkEnabled = try {
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }

        findViewById<MaterialButton>(R.id.myLocationButton).visibility =
            if (hasPermission && (isGpsEnabled || isNetworkEnabled)) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationOverlay.enableMyLocation()
            }
            updateMyLocationButtonVisibility()
        }
    }

    private fun handleActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val replayRequest = SessionReplayResult.parse(resultCode, data)
        if (resultCode == RESULT_OK) {
            val catchId = data?.getLongExtra("EXTRA_CATCH_ID", -1L) ?: -1L
            val sessionId = data?.getLongExtra("EXTRA_SESSION_ID", -1L) ?: -1L

            if (sessionId != -1L) {
                // Suljetaan mahdolliset dialogit ennen kartalle siirtymistä
                settingsManager.closeSettings()
                
                if (replayRequest != null) {
                    replaySessionOnMap(replayRequest.sessionId, replayRequest.onlySessionCatches)
                } else {
                    showArchivedSessionOnMap(sessionId)
                }
            } else if (requestCode == 3001) {
                // Sessioiden listauksesta palattiin ilman valintaa, ei tehdä mitään erikoista
            } else if (requestCode == 1001 && catchId != -1L) {
                // Muokattu kala: päivitetään vain se (inkrementaalinen päivitys)
                lifecycleScope.launch(Dispatchers.IO) {
                    val fish = db.fishCatchDao().getById(catchId)
                    withContext(Dispatchers.Main) {
                        if (fish != null) {
                            markerManager.addOrUpdateMarkerIncremental(fish, map.zoomLevelDouble)
                        } else {
                            reloadMarkersFromDb()
                        }
                    }
                }
            } else if (requestCode == 2001 || requestCode == 2002 || requestCode == 2003) {
                // Suodattimet, yhteenveto tai kalapäiväkirja päivitetty
                reloadMarkersFromDb()
                if (data?.getBooleanExtra("BACK_TO_SETTINGS", false) == true) {
                    settingsManager.openSettings()
                }
            } else if (requestCode == 1002) {
                // Kalalajit muokattu: pakotetaan MarkerManagerin päivitys
                markerManager.rebuildMarkers(map.zoomLevelDouble, forceRefreshSpecies = true)
            } else {
                // Muut tapaukset (import, asetukset tms.): täysi reload
                reloadMarkersFromDb()
            }
            updateFilterStatusUI()
        }
    }
}
