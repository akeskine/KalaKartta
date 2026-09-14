package fi.anssi.kalakartta.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.ui.MarkerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Owns archived-session playback, its map overlay and the replay player UI. */
class ReplayMapController(
    private val activity: AppCompatActivity,
    private val map: MapView,
    private val database: AppDatabase,
    private val markerManager: MarkerManager,
    private val scope: CoroutineScope,
    private val addOverlayBelowMarkers: (Overlay) -> Unit,
    private val onReplayVisibilityChanged: () -> Unit
) {
    private val replayController = SessionReplayController()
    private var replayJob: Job? = null
    private var loadJob: Job? = null
    private var requestGeneration = 0L
    private var archivedSessionPolyline: Polyline? = null
    private var visibleArchivedSessionId: Long = -1L
    private var isOnlySessionCatchesMode = false

    fun replaySessionOnMap(
        sessionId: Long,
        onlySessionCatches: Boolean = false,
        startAtEnd: Boolean = false
    ) {
        val request = cancelPendingWork()
        replayController.clear()
        isOnlySessionCatchesMode = onlySessionCatches
        visibleArchivedSessionId = -1L

        loadJob = scope.launch(Dispatchers.IO) {
            val session = database.fishingSessionDao().getById(sessionId)
            val points = database.trackPointDao().getPointsForSession(sessionId)
            if (points.isEmpty() || session == null) return@launch
            val endTime = session.endedAt ?: points.last().timestamp
            val initialTime = if (startAtEnd) endTime else session.startedAt

            withContext(Dispatchers.Main) {
                if (!isCurrentRequest(request)) return@withContext

                replayController.load(
                    points = points,
                    startTime = session.startedAt,
                    endTime = endTime,
                    currentTime = initialTime
                )
                initReplayUi()
                updateSessionInfoText(replayController.startTime, replayController.endTime)
                replaceArchivedPolyline()
                visibleArchivedSessionId = sessionId
                updateReplayFrame()
                zoomToPoints(points)

                markerManager.setMaxTimestamp(replayController.startTime)
                if (isOnlySessionCatchesMode) {
                    markerManager.setTimeRange(replayController.startTime, replayController.startTime, true)
                }
                updateReplayUi()
            }
        }
    }

    fun restoreState(savedInstanceState: Bundle) {
        val sessionId = savedInstanceState.getLong("visibleArchivedSessionId", -1L)
        if (sessionId == -1L) return

        replayController.restorePlaybackState(
            currentTime = savedInstanceState.getLong("currentReplayTime", 0L),
            speed = savedInstanceState.getInt("replaySpeed", SessionReplayController.DEFAULT_SPEED),
            isPlaying = savedInstanceState.getBoolean("isReplayPlaying", false)
        )
        restoreReplaySession(sessionId, savedInstanceState.getBoolean("replayMinimized", false))
    }

    fun saveState(outState: Bundle) {
        outState.putLong("visibleArchivedSessionId", visibleArchivedSessionId)
        outState.putLong("currentReplayTime", replayController.currentTime)
        outState.putInt("replaySpeed", replayController.speed)
        outState.putBoolean("isReplayPlaying", replayController.isPlaying)
        val playerContainer = activity.findViewById<View>(R.id.replayPlayerContainer)
        outState.putBoolean("replayMinimized", playerContainer.visibility == View.GONE)
    }

    fun showArchivedSessionOnMap(sessionId: Long) {
        val request = cancelPendingWork()
        replayController.clear()
        isOnlySessionCatchesMode = false
        visibleArchivedSessionId = -1L
        activity.findViewById<View>(R.id.replayPlayerLayout).visibility = View.GONE
        activity.findViewById<View>(R.id.replayPlayerContainer).visibility = View.VISIBLE
        activity.findViewById<View>(R.id.replayRestoreButton).visibility = View.GONE
        activity.findViewById<View>(R.id.addCatchButton).visibility = View.VISIBLE
        onReplayVisibilityChanged()
        markerManager.resetTimeRange()

        loadJob = scope.launch(Dispatchers.IO) {
            val session = database.fishingSessionDao().getById(sessionId)
            val points = database.trackPointDao().getPointsForSession(sessionId)
            if (points.isEmpty() || session == null) return@launch

            withContext(Dispatchers.Main) {
                if (!isCurrentRequest(request)) return@withContext

                replaceArchivedPolyline()
                val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
                archivedSessionPolyline?.setPoints(geoPoints)
                visibleArchivedSessionId = sessionId
                updateSessionInfoText(session.startedAt, session.endedAt ?: points.last().timestamp)
                map.controller.animateTo(geoPoints[0], 15.0, 500L)
                markerManager.resetTimeRange()
                map.invalidate()
            }
        }
    }

    fun hideArchivedSession() {
        cancelPendingWork()
        replayController.clear()
        isOnlySessionCatchesMode = false

        activity.findViewById<View>(R.id.replayPlayerLayout).visibility = View.GONE
        activity.findViewById<View>(R.id.sessionInfoText).visibility = View.GONE
        activity.findViewById<View>(R.id.addCatchButton).visibility = View.VISIBLE
        onReplayVisibilityChanged()

        archivedSessionPolyline?.let {
            map.overlays.remove(it)
            archivedSessionPolyline = null
            map.invalidate()
        }
        visibleArchivedSessionId = -1L
        markerManager.resetTimeRange()
    }

    fun getVisibleArchivedSessionId(): Long = visibleArchivedSessionId

    private fun restoreReplaySession(sessionId: Long, minimized: Boolean) {
        val request = cancelPendingWork()
        loadJob = scope.launch(Dispatchers.IO) {
            val session = database.fishingSessionDao().getById(sessionId)
            val points = database.trackPointDao().getPointsForSession(sessionId)
            if (points.isEmpty() || session == null) return@launch

            withContext(Dispatchers.Main) {
                if (!isCurrentRequest(request)) return@withContext

                replayController.load(
                    points = points,
                    startTime = session.startedAt,
                    endTime = session.endedAt ?: points.last().timestamp,
                    currentTime = replayController.currentTime,
                    speed = replayController.speed,
                    isPlaying = replayController.isPlaying
                )
                initReplayUi()
                updateSessionInfoText(replayController.startTime, replayController.endTime)

                val speedOptions = listOf("10x", "30x", "60x", "120x", "360x", "720x", "1440x", "2880x")
                val speedIndex = speedOptions.indexOf("${replayController.speed}x")
                if (speedIndex != -1) {
                    activity.findViewById<Spinner>(R.id.replaySpeedSpinner).setSelection(speedIndex)
                }
                if (minimized) {
                    activity.findViewById<View>(R.id.replayPlayerContainer).visibility = View.GONE
                    activity.findViewById<View>(R.id.replayRestoreButton).visibility = View.VISIBLE
                }

                replaceArchivedPolyline()
                visibleArchivedSessionId = sessionId
                updateReplayFrame()
                updateReplayUi()
                if (replayController.isPlaying) startReplayLoop() else updateReplayPlayPauseIcon()
            }
        }
    }

    private fun initReplayUi() {
        val playerLayout = activity.findViewById<View>(R.id.replayPlayerLayout)
        val playerContainer = activity.findViewById<View>(R.id.replayPlayerContainer)
        val restoreButton = activity.findViewById<View>(R.id.replayRestoreButton)
        val playPauseButton = activity.findViewById<ImageButton>(R.id.replayPlayPauseButton)
        val seekBar = activity.findViewById<SeekBar>(R.id.replaySeekBar)
        val speedSpinner = activity.findViewById<Spinner>(R.id.replaySpeedSpinner)
        val minimizeButton = activity.findViewById<View>(R.id.replayMinimizeButton)

        playerLayout.visibility = View.VISIBLE
        playerContainer.visibility = View.VISIBLE
        restoreButton.visibility = View.GONE
        playerContainer.setOnClickListener { }
        activity.findViewById<View>(R.id.addCatchButton).visibility = View.GONE
        onReplayVisibilityChanged()

        playPauseButton.setOnClickListener {
            replayController.setPlaying(!replayController.isPlaying)
            updateReplayPlayPauseIcon()
            if (replayController.isPlaying) startReplayLoop()
        }

        seekBar.max = (replayController.endTime - replayController.startTime).toInt()
        seekBar.progress = (replayController.currentTime - replayController.startTime).toInt()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    replayController.seek(replayController.startTime + progress)
                    updateReplayFrame()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val speedOptions = listOf("10x", "30x", "60x", "120x", "360x", "720x", "1440x", "2880x")
        val adapter = ArrayAdapter(activity, R.layout.spinner_item_narrow, speedOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        speedSpinner.adapter = adapter
        speedSpinner.setSelection(4)
        speedSpinner.post {
            (speedSpinner.selectedView as? TextView)?.setTextColor(Color.WHITE)
        }
        speedSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                replayController.setSpeed(speedOptions[position].removeSuffix("x").toIntOrNull() ?: SessionReplayController.DEFAULT_SPEED)
                (view as? TextView)?.setTextColor(Color.WHITE)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        minimizeButton.setOnClickListener {
            playerContainer.visibility = View.GONE
            restoreButton.visibility = View.VISIBLE
        }
        restoreButton.setOnClickListener {
            playerContainer.visibility = View.VISIBLE
            restoreButton.visibility = View.GONE
        }
        updateReplayPlayPauseIcon()
    }

    private fun updateReplayPlayPauseIcon() {
        val button = activity.findViewById<ImageButton>(R.id.replayPlayPauseButton)
        button.setImageResource(if (replayController.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun startReplayLoop() {
        replayJob?.cancel()
        replayJob = scope.launch(Dispatchers.Main) {
            while (isActive && replayController.isPlaying) {
                replayController.advance()
                updateReplayFrame()
                updateReplayUi()
                if (!replayController.isPlaying) {
                    updateReplayPlayPauseIcon()
                    break
                }
                delay(SessionReplayController.DEFAULT_STEP_MILLIS)
            }
        }
    }

    private fun cancelPendingWork(): Long {
        replayJob?.cancel()
        replayJob = null
        loadJob?.cancel()
        loadJob = null
        requestGeneration += 1
        return requestGeneration
    }

    private fun isCurrentRequest(request: Long): Boolean = request == requestGeneration

    private fun updateReplayFrame(frame: SessionReplayController.Frame = replayController.frame()) {
        archivedSessionPolyline?.setPoints(frame.visiblePoints.map { GeoPoint(it.latitude, it.longitude) })
        if (isOnlySessionCatchesMode) {
            markerManager.setTimeRange(replayController.startTime, frame.currentTime, true)
        } else {
            markerManager.setMaxTimestamp(frame.currentTime)
        }
        map.invalidate()
    }

    private fun updateReplayUi() {
        val seekBar = activity.findViewById<SeekBar>(R.id.replaySeekBar)
        val timeText = activity.findViewById<TextView>(R.id.replayTimeText)
        seekBar.progress = (replayController.currentTime - replayController.startTime).toInt()
        val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        timeText.text = "${format.format(Date(replayController.currentTime))} / ${format.format(Date(replayController.endTime))}"
    }

    private fun updateSessionInfoText(start: Long, end: Long) {
        val infoText = activity.findViewById<TextView>(R.id.sessionInfoText)
        val startCalendar = Calendar.getInstance().apply { timeInMillis = start }
        val endCalendar = Calendar.getInstance().apply { timeInMillis = end }
        val sameDay = startCalendar.get(Calendar.YEAR) == endCalendar.get(Calendar.YEAR) &&
                startCalendar.get(Calendar.DAY_OF_YEAR) == endCalendar.get(Calendar.DAY_OF_YEAR)
        val date = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
        val time = SimpleDateFormat("H:mm", Locale.getDefault())
        val dateTime = SimpleDateFormat("d.M.yyyy H:mm", Locale.getDefault())
        infoText.text = if (sameDay) {
            "Sessio ${date.format(Date(start))} ${time.format(Date(start))} - ${time.format(Date(end))}"
        } else {
            "Sessio ${dateTime.format(Date(start))} - ${dateTime.format(Date(end))}"
        }
        infoText.visibility = View.VISIBLE
    }

    private fun replaceArchivedPolyline() {
        archivedSessionPolyline?.let { map.overlays.remove(it) }
        archivedSessionPolyline = Polyline(map).apply {
            outlinePaint.color = Color.BLUE
            outlinePaint.strokeWidth = 8f
            setOnClickListener { _, _, _ -> true }
        }
        addOverlayBelowMarkers(archivedSessionPolyline!!)
    }

    private fun zoomToPoints(points: List<fi.anssi.kalakartta.data.TrackPoint>) {
        if (points.isEmpty()) return
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val box = BoundingBox(maxLat, maxLon, minLat, minLon)
        val centerLat = (maxLat + minLat) / 2.0
        val centerLon = (maxLon + minLon) / 2.0
        val results = FloatArray(1)
        android.location.Location.distanceBetween(centerLat, minLon, centerLat, maxLon, results)
        val finalBox = if (results[0] < 400.0) {
            val metersPerDegreeLon = 111320.0 * Math.cos(Math.toRadians(centerLat))
            val degreeDelta = (400.0 / metersPerDegreeLon) / 2.0
            BoundingBox(maxLat, centerLon + degreeDelta, minLat, centerLon - degreeDelta)
        } else {
            box
        }
        map.zoomToBoundingBox(finalBox, true, 100)
    }
}
