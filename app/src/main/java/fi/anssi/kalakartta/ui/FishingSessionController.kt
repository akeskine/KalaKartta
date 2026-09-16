package fi.anssi.kalakartta.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.graphics.Color
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishingSession
import fi.anssi.kalakartta.service.FishingSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Owns the live fishing-session service connection and recording presentation. */
class FishingSessionController(
    private val activity: AppCompatActivity,
    private val map: MapView,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val addOverlayBelowMarkers: (Overlay) -> Unit,
    private val hideArchivedSession: () -> Unit,
    private val onSessionEnded: (sessionId: Long, durationMs: Long, distanceM: Float) -> Unit,
    private val onLocationDisabled: () -> Unit
) {
    private var fishingService: FishingSessionService? = null
    private var isBound = false
    private var sessionPolyline: Polyline? = null
    private var recoveryDialogShowing = false
    private val recordingHandler = Handler(activity.mainLooper)
    private var recordingDotVisible = true
    private var recordingBlinkRunnable: Runnable? = null

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(className: android.content.ComponentName, service: IBinder) {
            val binder = service as FishingSessionService.LocalBinder
            fishingService = binder.getService()
            isBound = true
            updateRecordingStatusUi()
            checkUnfinishedSessions()
        }

        override fun onServiceDisconnected(arg0: android.content.ComponentName) {
            isBound = false
            fishingService = null
            updateRecordingStatusUi()
        }
    }

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SESSION_ENDED -> {
                    val sessionId = intent.getLongExtra("SESSION_ID", -1L)
                    val durationMs = intent.getLongExtra("duration_ms", 0L)
                    val distanceM = intent.getFloatExtra("distance_m", 0f)
                    updateRecordingStatusUi()
                    if (sessionId != -1L) {
                        onSessionEnded(sessionId, durationMs, distanceM)
                    }
                }
                ACTION_SESSION_ENDED_LOCATION_OFF -> {
                    updateRecordingStatusUi()
                    onLocationDisabled()
                }
                ACTION_SESSION_RECOVERY_REQUIRED -> checkUnfinishedSessions()
                ACTION_SESSION_STARTED -> updateRecordingStatusUi()
            }
        }
    }

    fun onStart() {
        val filter = IntentFilter().apply {
            addAction(ACTION_SESSION_STARTED)
            addAction(ACTION_SESSION_ENDED)
            addAction(ACTION_SESSION_ENDED_LOCATION_OFF)
            addAction(ACTION_SESSION_RECOVERY_REQUIRED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(sessionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(sessionReceiver, filter)
        }

        val intent = Intent(activity, FishingSessionService::class.java)
        activity.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        updateRecordingStatusUi()
    }

    fun onStop() {
        if (isBound) {
            activity.unbindService(connection)
            isBound = false
        }
        try {
            activity.unregisterReceiver(sessionReceiver)
        } catch (_: Exception) {
        }
        recordingBlinkRunnable?.let { recordingHandler.removeCallbacks(it) }
    }

    fun getFishingService(): FishingSessionService? = fishingService

    fun updateRecordingStatus() {
        updateRecordingStatusUi()
    }

    fun updateSessionLine() {
        val showLiveRoute = settingsStore.showLiveSessionRoute
        val sessionId = fishingService?.getCurrentSessionId() ?: -1L
        if (sessionId != -1L && showLiveRoute) {
            scope.launch(Dispatchers.IO) {
                val points = database.trackPointDao().getPointsForSession(sessionId)
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
                        sessionPolyline?.setPoints(points.map { GeoPoint(it.latitude, it.longitude) })
                        map.invalidate()
                    } else {
                        removeSessionLine()
                    }
                }
            }
        } else {
            removeSessionLine()
        }
    }

    fun startFishingSession(locationCheckInterval: Int, minInterval: Int, maxInterval: Int, minDistance: Int) {
        hideArchivedSession()
        val intent = Intent(activity, FishingSessionService::class.java).apply {
            putExtra("LOCATION_CHECK_INTERVAL", locationCheckInterval)
            putExtra("MIN_INTERVAL", minInterval)
            putExtra("MAX_INTERVAL", maxInterval)
            putExtra("MIN_DISTANCE", minDistance)
        }
        startService(intent)
        activity.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        updateRecordingStatusUi(overrideRecording = true)
    }

    fun stopFishingSession() {
        fishingService?.stopSession()
        updateRecordingStatusUi(overrideRecording = false)
        updateSessionLine()
    }

    fun checkUnfinishedSessions() {
        if (fishingService?.isRecording() == true || recoveryDialogShowing) return

        scope.launch(Dispatchers.IO) {
            val activeState = database.activeFishingSessionDao().get()
            if (activeState != null) {
                withContext(Dispatchers.Main) {
                    if (fishingService?.isRecording() != true) {
                        val resumeIntent = Intent(activity, FishingSessionService::class.java).apply {
                            putExtra("CONTINUE_SESSION_ID", activeState.sessionId)
                        }
                        startService(resumeIntent)
                    }
                }
                return@launch
            }

            val sessions = database.fishingSessionDao().getUnfinishedSessions()
            if (sessions.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                if (fishingService?.isRecording() == true || recoveryDialogShowing) return@withContext
                recoveryDialogShowing = true
                if (sessions.size == 1) {
                    showSingleRecoveryDialog(sessions.single())
                } else {
                    showMultipleRecoveryDialog(sessions)
                }
            }
        }
    }

    private fun showSingleRecoveryDialog(session: FishingSession) {
        scope.launch(Dispatchers.IO) {
            val lastPoint = database.trackPointDao().getLastPointForSession(session.id)
            val lastTime = lastPoint?.timestamp ?: session.startedAt
            withContext(Dispatchers.Main) {
                val dialog = AlertDialog.Builder(activity)
                    .setTitle("Keskeneräinen sessio löytyi")
                    .setMessage("Haluatko jatkaa aiempaa sessiota vai päättää sen viimeiseen reittipisteeseen?")
                    .setPositiveButton("Jatka sessiota") { _, _ -> continueFishingSession(session) }
                    .setNegativeButton("Päätä sessio viimeiseen pisteeseen") { _, _ ->
                        finishUnfinishedSession(session, lastTime)
                    }
                    .setCancelable(false)
                    .create()
                dialog.setOnDismissListener { recoveryDialogShowing = false }
                dialog.show()
            }
        }
    }

    private fun showMultipleRecoveryDialog(sessions: List<FishingSession>) {
        val timeFormat = SimpleDateFormat("d.M. HH:mm", Locale.getDefault())
        val labels = sessions.map { "${timeFormat.format(Date(it.startedAt))} (ID ${it.id})" }.toTypedArray()
        var selectedIndex = 0
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Useita keskeneräisiä sessioita")
            .setMessage("Valitse jatkettava sessio. Muut päätetään viimeiseen tallennettuun pisteeseen.")
            .setSingleChoiceItems(labels, 0) { _, which -> selectedIndex = which }
            .setPositiveButton("Jatka valittua") { _, _ ->
                val selected = sessions[selectedIndex]
                finishSessions(sessions.filterIndexed { index, _ -> index != selectedIndex }) {
                    continueFishingSession(selected)
                }
            }
            .setNegativeButton("Päätä kaikki") { _, _ -> finishSessions(sessions) }
            .setCancelable(false)
            .create()
        dialog.setOnDismissListener { recoveryDialogShowing = false }
        dialog.show()
    }

    private fun continueFishingSession(session: FishingSession) {
        val intent = Intent(activity, FishingSessionService::class.java).apply {
            putExtra("LOCATION_CHECK_INTERVAL", settingsStore.locationCheckInterval)
            putExtra("MIN_INTERVAL", settingsStore.minTrackPointInterval)
            putExtra("MAX_INTERVAL", settingsStore.maxTrackPointInterval)
            putExtra("MIN_DISTANCE", settingsStore.minTrackPointDistance)
            putExtra("CONTINUE_SESSION_ID", session.id)
        }
        startService(intent)
        activity.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        updateRecordingStatusUi(overrideRecording = true)
    }

    private fun finishUnfinishedSession(session: FishingSession, endTime: Long) {
        scope.launch(Dispatchers.IO) {
            val points = database.trackPointDao().getPointsForSession(session.id)
            val actualStart = points.firstOrNull()?.timestamp ?: session.startedAt
            val actualEnd = points.lastOrNull()?.timestamp ?: endTime
            database.runInTransaction {
                database.fishingSessionDao().update(session.copy(startedAt = actualStart, endedAt = actualEnd))
                database.activeFishingSessionDao().get()?.let { state ->
                    if (state.sessionId == session.id) database.activeFishingSessionDao().delete()
                }
            }
            withContext(Dispatchers.Main) {
                updateSessionLine()
                android.widget.Toast.makeText(activity, "Sessio päätetty", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finishSessions(sessions: List<FishingSession>, onFinished: (() -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            val updates = sessions.map { session ->
                val points = database.trackPointDao().getPointsForSession(session.id)
                val actualStart = points.firstOrNull()?.timestamp ?: session.startedAt
                val actualEnd = points.lastOrNull()?.timestamp ?: session.startedAt
                session.copy(startedAt = actualStart, endedAt = actualEnd)
            }
            database.runInTransaction {
                updates.forEach { database.fishingSessionDao().update(it) }
                database.activeFishingSessionDao().get()?.let { state ->
                    if (sessions.any { it.id == state.sessionId }) database.activeFishingSessionDao().delete()
                }
            }
            withContext(Dispatchers.Main) {
                updateSessionLine()
                onFinished?.invoke()
            }
        }
    }

    private fun updateRecordingStatusUi(overrideRecording: Boolean? = null) {
        val recordingLayout = activity.findViewById<android.view.View>(R.id.recordingStatusLayout) ?: return
        val isRecording = overrideRecording ?: (fishingService?.isRecording() ?: false)
        val dot = activity.findViewById<android.view.View>(R.id.recordingDot)
        val statusText = activity.findViewById<TextView>(R.id.recordingStatusText)

        if (isRecording) {
            recordingLayout.visibility = android.view.View.VISIBLE
            statusText?.text = "REC"
            recordingBlinkRunnable?.let { recordingHandler.removeCallbacks(it) }
            recordingDotVisible = true
            dot?.visibility = android.view.View.VISIBLE
            recordingBlinkRunnable = object : Runnable {
                override fun run() {
                    recordingDotVisible = !recordingDotVisible
                    dot?.visibility = if (recordingDotVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                    recordingHandler.postDelayed(this, 1000)
                    updateSessionLine()
                }
            }
            recordingHandler.postDelayed(recordingBlinkRunnable!!, 1000)
        } else {
            recordingLayout.visibility = android.view.View.GONE
            recordingBlinkRunnable?.let { recordingHandler.removeCallbacks(it) }
            recordingBlinkRunnable = null
            dot?.visibility = android.view.View.GONE
            updateSessionLine()
        }
    }

    private fun removeSessionLine() {
        sessionPolyline?.let { map.overlays.remove(it) }
        sessionPolyline = null
        map.invalidate()
    }

    private fun startService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            activity.startService(intent)
        }
    }

    private companion object {
        const val ACTION_SESSION_STARTED = "fi.anssi.kalakartta.SESSION_STARTED"
        const val ACTION_SESSION_ENDED = "fi.anssi.kalakartta.SESSION_ENDED"
        const val ACTION_SESSION_ENDED_LOCATION_OFF = "fi.anssi.kalakartta.SESSION_ENDED_LOCATION_OFF"
        const val ACTION_SESSION_RECOVERY_REQUIRED = FishingSessionService.ACTION_SESSION_RECOVERY_REQUIRED
    }
}
