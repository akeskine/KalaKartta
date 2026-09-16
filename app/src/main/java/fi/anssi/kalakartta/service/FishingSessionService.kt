package fi.anssi.kalakartta.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fi.anssi.kalakartta.MainActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.ActiveFishingSession
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishingSession
import fi.anssi.kalakartta.data.TrackPoint
import fi.anssi.kalakartta.service.TalkingClockService
import fi.anssi.kalakartta.ui.SettingsStore
import fi.anssi.kalakartta.utils.SessionStatsFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FishingSessionService : Service() {

    private val binder = LocalBinder()
    private lateinit var locationManager: LocationManager
    private lateinit var db: AppDatabase
    private var recording = false
    private var currentSessionId: Long = -1L
    private var startedAt: Long = 0L
    private var totalDistance: Double = 0.0
    private var lastLocation: Location? = null
    private var locationCheckIntervalSeconds: Int = 10
    private var minTrackPointIntervalSeconds: Int = 30
    private var maxTrackPointIntervalSeconds: Int = 300
    private var minTrackPointDistanceMeters: Int = 20
    private var lastSavedTimestamp: Long = 0L
    private var lastSavedLocation: Location? = null
    private var currentStationaryIntervalSeconds: Int = 30
    private var locationProviderReceiver: BroadcastReceiver? = null
    private var locationCheckCount = 0

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val sessionOperationMutex = Mutex()
    private val pendingPointJobs = mutableSetOf<Job>()
    private var foregroundStarted = false

    companion object {
        var KALASTUSSESSIOT_DEBUG = false
        const val CHANNEL_ID = "FishingSessionChannel"
        const val NOTIFICATION_ID = 101
        const val ACTION_STOP = "STOP_SESSION"
        const val ACTION_SESSION_RECOVERY_REQUIRED = "fi.anssi.kalakartta.SESSION_RECOVERY_REQUIRED"
    }

    inner class LocalBinder : Binder() {
        fun getService(): FishingSessionService = this@FishingSessionService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        db = AppDatabase.getInstance(this)
        createNotificationChannel()
        
        registerLocationProviderReceiver()
    }

    private fun registerLocationProviderReceiver() {
        locationProviderReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    checkGpsStatus()
                }
            }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        // PROVIDERS_CHANGED_ACTION on järjestelmän lähetys, mutta joissain laitteissa se saattaa vaatia EXPORTED
        // tai se voi toimia NOT_EXPORTED kanssa. Kokeillaan tässä NOT_EXPORTED ensin, mutta varmistetaan toimivuus.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationProviderReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(locationProviderReceiver, filter)
        }
    }

    private fun checkGpsStatus() {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (recording && !isGpsEnabled) {
            stopSessionDueToLocationOff()
        }
    }

    private fun stopSessionDueToLocationOff() {
        if (!recording) return
        
        recording = false
        val sessionId = currentSessionId
        currentSessionId = -1L

        serviceScope.launch {
            sessionOperationMutex.withLock {
                synchronized(pendingPointJobs) { pendingPointJobs.toList() }.joinAll()
                if (sessionId != -1L) {
                    val session = db.fishingSessionDao().getById(sessionId)
                    if (session != null) {
                        db.runInTransaction {
                            db.fishingSessionDao().update(session.copy(endedAt = System.currentTimeMillis()))
                            db.activeFishingSessionDao().delete()
                        }
                    }
                }
                db.activeFishingSessionDao().delete()

                withContext(Dispatchers.Main) {
                    try {
                        locationManager.removeUpdates(locationListener)
                    } catch (e: Exception) {}

                    stopForeground(STOP_FOREGROUND_REMOVE)
                    foregroundStarted = false
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(NOTIFICATION_ID)

                    val intent = Intent("fi.anssi.kalakartta.SESSION_ENDED_LOCATION_OFF")
                    intent.putExtra("SESSION_ID", sessionId)
                    intent.setPackage(packageName)
                    sendBroadcast(intent)

                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession()
            return START_NOT_STICKY
        }

        startForegroundImmediately()
        serviceScope.launch {
            sessionOperationMutex.withLock {
                handleStartCommand(intent)
            }
        }

        return START_STICKY
    }

    private data class SessionParams(
        val locationCheckInterval: Int,
        val minInterval: Int,
        val maxInterval: Int,
        val minDistance: Int
    ) {
        companion object {
            fun fromState(state: ActiveFishingSession) = SessionParams(
                state.locationCheckIntervalSeconds,
                state.minTrackPointIntervalSeconds,
                state.maxTrackPointIntervalSeconds,
                state.minTrackPointDistanceMeters
            )
        }
    }

    private fun settingsParams(): SessionParams {
        val settings = SettingsStore(getSharedPreferences("settings", Context.MODE_PRIVATE))
        return SessionParams(
            settings.locationCheckInterval,
            settings.minTrackPointInterval,
            settings.maxTrackPointInterval,
            settings.minTrackPointDistance
        )
    }

    private fun paramsFromIntent(intent: Intent?): SessionParams {
        val defaults = settingsParams()
        return SessionParams(
            intent?.getIntExtra("LOCATION_CHECK_INTERVAL", defaults.locationCheckInterval)
                ?: defaults.locationCheckInterval,
            intent?.getIntExtra("MIN_INTERVAL", defaults.minInterval) ?: defaults.minInterval,
            intent?.getIntExtra("MAX_INTERVAL", defaults.maxInterval) ?: defaults.maxInterval,
            intent?.getIntExtra("MIN_DISTANCE", defaults.minDistance) ?: defaults.minDistance
        )
    }

    private fun paramsWereProvided(intent: Intent?): Boolean = intent != null && listOf(
        "LOCATION_CHECK_INTERVAL",
        "MIN_INTERVAL",
        "MAX_INTERVAL",
        "MIN_DISTANCE",
        "CONTINUE_SESSION_ID"
    ).any(intent::hasExtra)

    private fun applyParameters(params: SessionParams) {
        locationCheckIntervalSeconds = params.locationCheckInterval
        minTrackPointIntervalSeconds = params.minInterval
        maxTrackPointIntervalSeconds = params.maxInterval
        minTrackPointDistanceMeters = params.minDistance
        currentStationaryIntervalSeconds = params.minInterval
    }

    private suspend fun handleStartCommand(intent: Intent?) {
        if (recording) {
            val state = db.activeFishingSessionDao().get() ?: return
            if (paramsWereProvided(intent)) {
                val params = paramsFromIntent(intent)
                applyParameters(params)
                db.activeFishingSessionDao().update(state.copy(
                    locationCheckIntervalSeconds = params.locationCheckInterval,
                    minTrackPointIntervalSeconds = params.minInterval,
                    maxTrackPointIntervalSeconds = params.maxInterval,
                    minTrackPointDistanceMeters = params.minDistance
                ))
                withContext(Dispatchers.Main) { requestLocationUpdates() }
            }
            sendSessionStartedBroadcast()
            return
        }

        val storedState = db.activeFishingSessionDao().get()
        if (storedState != null) {
            val session = db.fishingSessionDao().getById(storedState.sessionId)
            if (session != null && session.endedAt == null) {
                resumeSession(storedState)
                return
            }
            db.activeFishingSessionDao().delete()
        }

        val continueId = intent?.getLongExtra("CONTINUE_SESSION_ID", -1L) ?: -1L
        val unfinished = db.fishingSessionDao().getUnfinishedSessions()
        when {
            continueId != -1L -> {
                val session = unfinished.firstOrNull { it.id == continueId }
                if (session != null) {
                    adoptAndResumeSession(session, paramsFromIntent(intent))
                } else {
                    stopAfterRecoveryRequired()
                }
            }
            unfinished.size == 1 && intent == null -> {
                // Compatibility path for sessions created by releases before
                // ActiveFishingSession existed.
                adoptAndResumeSession(unfinished.single(), settingsParams())
            }
            unfinished.size == 1 -> stopAfterRecoveryRequired()
            unfinished.size > 1 -> stopAfterRecoveryRequired()
            intent != null -> startNewSession(paramsFromIntent(intent))
            else -> stopServiceWithoutSession()
        }
    }

    private suspend fun startNewSession(params: SessionParams) {
        if (recording) return

        recording = true
        applyParameters(params)
        startedAt = System.currentTimeMillis()
        totalDistance = 0.0
        lastLocation = null
        lastSavedLocation = null
        lastSavedTimestamp = 0L
        currentStationaryIntervalSeconds = params.minInterval

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val settingsStore = SettingsStore(prefs)
        val defaultFisherman = settingsStore.defaultFisherman
        val session = FishingSession(startedAt = startedAt, fisherman = defaultFisherman.uppercase())
        var newSessionId = -1L
        db.runInTransaction {
            newSessionId = db.fishingSessionDao().insert(session)
            db.activeFishingSessionDao().insert(ActiveFishingSession(
                sessionId = newSessionId,
                locationCheckIntervalSeconds = params.locationCheckInterval,
                minTrackPointIntervalSeconds = params.minInterval,
                maxTrackPointIntervalSeconds = params.maxInterval,
                minTrackPointDistanceMeters = params.minDistance
            ))
        }
        currentSessionId = newSessionId

        withContext(Dispatchers.Main) {
            requestLocationUpdates()
            startTalkingClockIfNeeded(settingsStore)
            sendSessionStartedBroadcast()
        }
    }

    private suspend fun adoptAndResumeSession(session: FishingSession, params: SessionParams) {
        val otherSessions = db.fishingSessionDao().getUnfinishedSessions()
            .filter { it.id != session.id }
        db.runInTransaction {
            otherSessions.forEach { other ->
                val points = db.trackPointDao().getPointsForSession(other.id)
                val actualStart = points.firstOrNull()?.timestamp ?: other.startedAt
                val actualEnd = points.lastOrNull()?.timestamp ?: other.startedAt
                db.fishingSessionDao().update(other.copy(startedAt = actualStart, endedAt = actualEnd))
            }
            db.activeFishingSessionDao().insert(ActiveFishingSession(
                sessionId = session.id,
                locationCheckIntervalSeconds = params.locationCheckInterval,
                minTrackPointIntervalSeconds = params.minInterval,
                maxTrackPointIntervalSeconds = params.maxInterval,
                minTrackPointDistanceMeters = params.minDistance
            ))
        }
        resumeSession(db.activeFishingSessionDao().get()!!)
    }

    private suspend fun resumeSession(state: ActiveFishingSession) {
        val session = db.fishingSessionDao().getById(state.sessionId)
        if (session == null || session.endedAt != null) {
            db.activeFishingSessionDao().delete()
            stopServiceWithoutSession()
            return
        }

        if (recording) return
        recording = true
        currentSessionId = state.sessionId
        applyParameters(SessionParams.fromState(state))

        startedAt = session.startedAt
            
            // Lasketaan tähänastinen matka tallennetuista pisteistä
            val points = db.trackPointDao().getPointsForSession(state.sessionId)
            totalDistance = 0.0
            var prevLoc: Location? = null
            points.forEach { pt ->
                val loc = Location("stored").apply {
                    latitude = pt.latitude
                    longitude = pt.longitude
                }
                prevLoc?.let { totalDistance += it.distanceTo(loc).toDouble() }
                prevLoc = loc
            }
            lastLocation = prevLoc
            lastSavedLocation = prevLoc
            lastSavedTimestamp = points.lastOrNull()?.timestamp ?: 0L
            currentStationaryIntervalSeconds = minTrackPointIntervalSeconds

        val settingsStore = SettingsStore(getSharedPreferences("settings", Context.MODE_PRIVATE))
        withContext(Dispatchers.Main) {
            requestLocationUpdates()
            startTalkingClockIfNeeded(settingsStore)
            sendSessionStartedBroadcast()
        }
    }

    private fun startForegroundImmediately() {
        if (foregroundStarted) return
        startForeground(NOTIFICATION_ID, createNotification(initializing = true))
        foregroundStarted = true
    }

    private fun sendSessionStartedBroadcast() {
        val intent = Intent("fi.anssi.kalakartta.SESSION_STARTED")
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun startTalkingClockIfNeeded(settingsStore: SettingsStore) {
        if (!settingsStore.talkingClockOnlyFishing) return

        settingsStore.talkingClockEnabled = true
        val clockIntent = Intent(this, TalkingClockService::class.java).apply {
            putExtra("interval", settingsStore.talkingClockInterval)
            action = "SESSION_STARTED"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(clockIntent)
        } else {
            startService(clockIntent)
        }
    }

    private suspend fun stopAfterRecoveryRequired() {
        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            val intent = Intent(ACTION_SESSION_RECOVERY_REQUIRED).setPackage(packageName)
            sendBroadcast(intent)
            stopSelf()
        }
    }

    private suspend fun stopServiceWithoutSession() {
        withContext(Dispatchers.Main) {
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
            }
            stopSelf()
        }
    }

    fun stopSession() {
        var stoppedSessionId = -1L
        serviceScope.launch {
            var durationMs = 0L
            sessionOperationMutex.withLock {
                if (!recording) {
                    db.activeFishingSessionDao().delete()
                    withContext(Dispatchers.Main) {
                        if (foregroundStarted) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            foregroundStarted = false
                        }
                        stopSelf()
                    }
                    return@withLock
                }

                recording = false
                val sessionId = currentSessionId
                stoppedSessionId = sessionId
                currentSessionId = -1L
                synchronized(pendingPointJobs) { pendingPointJobs.toList() }.joinAll()
                if (sessionId != -1L) {
                    val session = db.fishingSessionDao().getById(sessionId)
                    if (session != null) {
                        val points = db.trackPointDao().getPointsForSession(sessionId)
                        val actualStart = points.firstOrNull()?.timestamp ?: session.startedAt
                        val actualEnd = points.lastOrNull()?.timestamp ?: System.currentTimeMillis()

                        durationMs = actualEnd - actualStart
                        db.runInTransaction {
                            db.fishingSessionDao().update(session.copy(startedAt = actualStart, endedAt = actualEnd))
                            db.activeFishingSessionDao().delete()
                        }
                    }
                }
                db.activeFishingSessionDao().delete()
            }
            
            withContext(Dispatchers.Main) {
                locationManager.removeUpdates(locationListener)
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
                
                // Pysäytetään kello jos asetus päällä
                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                val settingsStore = SettingsStore(prefs)
                if (settingsStore.talkingClockOnlyFishing) {
                    settingsStore.talkingClockEnabled = false
                    val clockIntent = Intent(this@FishingSessionService, TalkingClockService::class.java).apply {
                        action = "SESSION_ENDED"
                        putExtra("duration_ms", durationMs)
                        putExtra("distance_m", totalDistance)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(clockIntent)
                    } else {
                        startService(clockIntent)
                    }
                    
                    // Annetaan palvelun puhua loppuun ennen pysäytystä. 
                    // TalkingClockService hoitaa itse itsensä loppuun ja scheduleNextiä ei kutsuta SESSION_ENDEDissä.
                    // TalkingClockService.isEnding ja cancelScheduledTalk() varmistavat, ettei kello jää päälle 
                    // eikä uusia kellonaikoja sanota lopetusviestin aikana.
                }
                
                // Ilmoitetaan MainActivitylle että sessio loppui, jotta se voi avata dialogin
                val intent = Intent("fi.anssi.kalakartta.SESSION_ENDED")
                intent.putExtra("SESSION_ID", stoppedSessionId)
                intent.putExtra("duration_ms", durationMs)
                intent.putExtra("distance_m", totalDistance)
                intent.setPackage(packageName)
                sendBroadcast(intent)
                
                stopSelf()
            }
        }
    }

    private fun requestLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                locationCheckIntervalSeconds * 1000L,
                0f,
                locationListener
            )
        } catch (e: SecurityException) {
            // Pitäisi olla luvat kunnossa tässä vaiheessa
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = System.currentTimeMillis()
            locationCheckCount++
            
            // Päivitetään matka
            lastLocation?.let {
                totalDistance += it.distanceTo(location)
            }
            lastLocation = location

            // Tallennetaan reittipiste jos ehdot täyttyvät
            val timeSinceLastSave = now - lastSavedTimestamp
            val distanceSinceLastSave = lastSavedLocation?.distanceTo(location) ?: Float.MAX_VALUE
            
            val isStationary = distanceSinceLastSave < minTrackPointDistanceMeters
            val shouldSave: Boolean
            
            if (!isStationary) {
                // Liikkeellä
                shouldSave = timeSinceLastSave >= minTrackPointIntervalSeconds * 1000L
            } else {
                // Paikallaan tai lähes paikallaan
                shouldSave = timeSinceLastSave >= currentStationaryIntervalSeconds * 1000L
            }

            if (KALASTUSSESSIOT_DEBUG) {
                val distanceStr = lastSavedLocation?.let { String.format("%.1f m", distanceSinceLastSave) } ?: "-"
                val timeStr = if (lastSavedTimestamp > 0) "${(now - lastSavedTimestamp) / 1000} s" else "-"
                val speedStr = String.format("%.2f km/h", location.speed * 3.6).replace(".", ",")
                val accuracyStr = String.format("%.0f m", location.accuracy)
                
                android.util.Log.d("FishingSessionService", "Sijainnin tarkastus nro: $locationCheckCount")
                android.util.Log.d("FishingSessionService", "Etäisyys edellisestä pisteestä: $distanceStr")
                android.util.Log.d("FishingSessionService", "Nopeus: $speedStr")
                android.util.Log.d("FishingSessionService", "Tarkkuus: $accuracyStr")
                android.util.Log.d("FishingSessionService", "Aika edellisen pisteen tallennuksesta: $timeStr")
            }

            if (shouldSave) {
                saveTrackPoint(location, now)
                lastSavedTimestamp = now
                lastSavedLocation = location
                
                if (!isStationary) {
                    // Onnistunut minimietäisyyden saavuttanut tallennus nollaa porrastuksen
                    currentStationaryIntervalSeconds = minTrackPointIntervalSeconds
                } else {
                    // Onnistunut paikallaanolon tallennus kaksinkertaistaa välin (maxTrackPointIntervalSeconds asti)
                    currentStationaryIntervalSeconds = Math.min(currentStationaryIntervalSeconds * 2, maxTrackPointIntervalSeconds)
                }
            }
            
            // Päivitetään ilmoitus
            updateNotification()
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                checkGpsStatus()
            }
        }
    }

    private fun saveTrackPoint(location: Location, timestamp: Long = System.currentTimeMillis()) {
        val sessionId = currentSessionId
        if (!recording || sessionId == -1L) return

        val point = TrackPoint(
            fishingSessionId = sessionId,
            timestamp = timestamp,
            latitude = location.latitude,
            longitude = location.longitude,
            speed = location.speed,
            accuracy = location.accuracy
        )
        val job = serviceScope.launch {
            db.trackPointDao().insert(point)
        }
        synchronized(pendingPointJobs) {
            pendingPointJobs += job
        }
        job.invokeOnCompletion {
            synchronized(pendingPointJobs) {
                pendingPointJobs -= job
            }
        }
    }

    private fun createNotification(initializing: Boolean = false): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val duration = System.currentTimeMillis() - startedAt
        val durationStr = if (initializing || startedAt == 0L) {
            "Käynnistetään..."
        } else {
            SessionStatsFormatter.formatNotificationDuration(duration)
        }
        val distanceStr = String.format("%.4f km", totalDistance / 1000.0).replace(".", ",")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KalaKartta - session tallennus käynnissä")
            .setContentText("Kesto: $durationStr, matka: $distanceStr")
            .setSmallIcon(R.drawable.ahven) // Käytetään ahven-ikonia, koska rec-symbolia ei välttämättä ole
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Kalastussessio"
            val descriptionText = "Kalastussession reittipisteiden tallennus"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun isRecording() = recording
    fun getStartedAt() = startedAt
    fun getTotalDistance() = totalDistance
    fun getMinIntervalSeconds() = minTrackPointIntervalSeconds
    fun getMaxIntervalSeconds() = maxTrackPointIntervalSeconds
    fun getLocationCheckIntervalSeconds() = locationCheckIntervalSeconds
    fun getMinDistanceMeters() = minTrackPointDistanceMeters
    fun getCurrentSessionId() = currentSessionId
    fun getLocationCheckCount() = locationCheckCount
    fun getLastSavedLocation() = lastSavedLocation
    fun getLastSavedTimestamp() = lastSavedTimestamp
    fun getLastLocation() = lastLocation
    fun getDistanceSinceLastSave(): Float? {
        val last = lastLocation ?: return null
        val saved = lastSavedLocation ?: return null
        return last.distanceTo(saved)
    }
    fun getCurrentStationaryIntervalSeconds() = currentStationaryIntervalSeconds
    fun getShouldSaveStatus(): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLastSave = now - lastSavedTimestamp
        val last = lastLocation ?: return (timeSinceLastSave >= maxTrackPointIntervalSeconds * 1000L)
        val distanceSinceLastSave = lastSavedLocation?.distanceTo(last) ?: Float.MAX_VALUE
        
        val isStationary = distanceSinceLastSave < minTrackPointDistanceMeters
        return if (!isStationary) {
            timeSinceLastSave >= minTrackPointIntervalSeconds * 1000L
        } else {
            timeSinceLastSave >= currentStationaryIntervalSeconds * 1000L
        }
    }

    override fun onDestroy() {
        foregroundStarted = false
        locationProviderReceiver?.let {
            unregisterReceiver(it)
        }
        serviceJob.cancel()
        super.onDestroy()
    }
}
