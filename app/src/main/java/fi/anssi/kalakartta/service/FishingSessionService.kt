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
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishingSession
import fi.anssi.kalakartta.data.TrackPoint
import fi.anssi.kalakartta.service.TalkingClockService
import fi.anssi.kalakartta.ui.SettingsDefaults
import fi.anssi.kalakartta.ui.SettingsKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

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

    companion object {
        var KALASTUSSESSIOT_DEBUG = false
        const val CHANNEL_ID = "FishingSessionChannel"
        const val NOTIFICATION_ID = 101
        const val ACTION_STOP = "STOP_SESSION"
        var isRunning = false
    }

    inner class LocalBinder : Binder() {
        fun getService(): FishingSessionService = this@FishingSessionService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        isRunning = true
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
            if (sessionId != -1L) {
                val session = db.fishingSessionDao().getById(sessionId)
                if (session != null) {
                    db.fishingSessionDao().update(session.copy(endedAt = System.currentTimeMillis()))
                }
            }
            
            launch(Dispatchers.Main) {
                try {
                    locationManager.removeUpdates(locationListener)
                } catch (e: Exception) {}
                
                stopForeground(STOP_FOREGROUND_REMOVE)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession()
            return START_NOT_STICKY
        }
        
        val locInt = intent?.getIntExtra("LOCATION_CHECK_INTERVAL", 10) ?: 10
        val minInt = intent?.getIntExtra("MIN_INTERVAL", 30) ?: 30
        val maxInt = intent?.getIntExtra("MAX_INTERVAL", 300) ?: 300
        val minDist = intent?.getIntExtra("MIN_DISTANCE", 20) ?: 20
        val continueId = intent?.getLongExtra("CONTINUE_SESSION_ID", -1L) ?: -1L

        if (recording) {
            locationCheckIntervalSeconds = locInt
            minTrackPointIntervalSeconds = minInt
            maxTrackPointIntervalSeconds = maxInt
            minTrackPointDistanceMeters = minDist
            
            // Päivitetään päivitysväli jos se muuttui lennosta
            requestLocationUpdates()

            // Päivitetään ilmoitus jos tarpeen tai lähetetään uusi broadcast
            val updateIntent = Intent("fi.anssi.kalakartta.SESSION_STARTED")
            updateIntent.setPackage(packageName)
            sendBroadcast(updateIntent)
        } else if (continueId != -1L) {
            continueSession(continueId, locInt, minInt, maxInt, minDist)
        } else {
            startSession(locInt, minInt, maxInt, minDist)
        }
        
        return START_STICKY
    }

    private fun startSession(locInt: Int, minInt: Int, maxInt: Int, minDist: Int) {
        if (recording) return
        
        recording = true
        locationCheckIntervalSeconds = locInt
        minTrackPointIntervalSeconds = minInt
        maxTrackPointIntervalSeconds = maxInt
        minTrackPointDistanceMeters = minDist
        
        startedAt = System.currentTimeMillis()
        totalDistance = 0.0
        lastLocation = null
        lastSavedLocation = null
        lastSavedTimestamp = 0L
        currentStationaryIntervalSeconds = minInt

        serviceScope.launch {
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val defaultFisherman = prefs.getString(SettingsKeys.DEFAULT_FISHERMAN, SettingsDefaults.DEFAULT_FISHERMAN) ?: SettingsDefaults.DEFAULT_FISHERMAN
            val session = FishingSession(startedAt = startedAt, fisherman = defaultFisherman.uppercase())
            currentSessionId = db.fishingSessionDao().insert(session)
            
            launch(Dispatchers.Main) {
                startForeground(NOTIFICATION_ID, createNotification())
                requestLocationUpdates()
                
                // Käynnistetään kello jos asetus päällä
                if (prefs.getBoolean(SettingsKeys.TALKING_CLOCK_ONLY_FISHING, SettingsDefaults.TALKING_CLOCK_ONLY_FISHING)) {
                    prefs.edit().putBoolean(SettingsKeys.TALKING_CLOCK_ENABLED, true).apply()
                    val interval = prefs.getInt(SettingsKeys.TALKING_CLOCK_INTERVAL, SettingsDefaults.TALKING_CLOCK_INTERVAL)
                    val clockIntent = Intent(this@FishingSessionService, TalkingClockService::class.java).apply {
                        putExtra("interval", interval)
                        action = "SESSION_STARTED"
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(clockIntent)
                    } else {
                        startService(clockIntent)
                    }
                }

                // Ilmoitetaan MainActivitylle että sessio on alkanut (ja interval on asetettu)
                val intent = Intent("fi.anssi.kalakartta.SESSION_STARTED")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        }
    }

    private fun continueSession(sessionId: Long, locInt: Int, minInt: Int, maxInt: Int, minDist: Int) {
        if (recording) return
        recording = true
        currentSessionId = sessionId
        locationCheckIntervalSeconds = locInt
        minTrackPointIntervalSeconds = minInt
        maxTrackPointIntervalSeconds = maxInt
        minTrackPointDistanceMeters = minDist

        serviceScope.launch {
            val session = db.fishingSessionDao().getById(sessionId)
            startedAt = session?.startedAt ?: System.currentTimeMillis()
            
            // Lasketaan tähänastinen matka tallennetuista pisteistä
            val points = db.trackPointDao().getPointsForSession(sessionId)
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
            currentStationaryIntervalSeconds = minInt

            launch(Dispatchers.Main) {
                startForeground(NOTIFICATION_ID, createNotification())
                requestLocationUpdates()

                // Käynnistetään kello jos asetus päällä
                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (prefs.getBoolean(SettingsKeys.TALKING_CLOCK_ONLY_FISHING, SettingsDefaults.TALKING_CLOCK_ONLY_FISHING)) {
                    prefs.edit().putBoolean(SettingsKeys.TALKING_CLOCK_ENABLED, true).apply()
                    val interval = prefs.getInt(SettingsKeys.TALKING_CLOCK_INTERVAL, SettingsDefaults.TALKING_CLOCK_INTERVAL)
                    val clockIntent = Intent(this@FishingSessionService, TalkingClockService::class.java).apply {
                        putExtra("interval", interval)
                        action = "SESSION_STARTED"
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(clockIntent)
                    } else {
                        startService(clockIntent)
                    }
                }

                val intent = Intent("fi.anssi.kalakartta.SESSION_STARTED")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        }
    }

    fun stopSession() {
        if (!recording) {
            stopSelf()
            return
        }

        recording = false
        val sessionId = currentSessionId
        currentSessionId = -1L

        serviceScope.launch {
            var durationMs = 0L
            if (sessionId != -1L) {
                val session = db.fishingSessionDao().getById(sessionId)
                if (session != null) {
                    val points = db.trackPointDao().getPointsForSession(sessionId)
                    val actualStart = points.firstOrNull()?.timestamp ?: session.startedAt
                    val actualEnd = points.lastOrNull()?.timestamp ?: System.currentTimeMillis()
                    
                    durationMs = actualEnd - actualStart
                    db.fishingSessionDao().update(session.copy(startedAt = actualStart, endedAt = actualEnd))
                }
            }
            
            launch(Dispatchers.Main) {
                locationManager.removeUpdates(locationListener)
                stopForeground(STOP_FOREGROUND_REMOVE)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
                
                // Pysäytetään kello jos asetus päällä
                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (prefs.getBoolean(SettingsKeys.TALKING_CLOCK_ONLY_FISHING, SettingsDefaults.TALKING_CLOCK_ONLY_FISHING)) {
                    prefs.edit().putBoolean(SettingsKeys.TALKING_CLOCK_ENABLED, false).apply()
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
                intent.putExtra("SESSION_ID", sessionId)
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
                saveTrackPoint(location)
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

    private fun saveTrackPoint(location: Location) {
        serviceScope.launch {
            val point = TrackPoint(
                fishingSessionId = currentSessionId,
                timestamp = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                speed = location.speed,
                accuracy = location.accuracy
            )
            db.trackPointDao().insert(point)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val duration = System.currentTimeMillis() - startedAt
        val hours = TimeUnit.MILLISECONDS.toHours(duration)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60
        val durationStr = if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
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
        isRunning = false
        locationProviderReceiver?.let {
            unregisterReceiver(it)
        }
        serviceJob.cancel()
        super.onDestroy()
    }
}
