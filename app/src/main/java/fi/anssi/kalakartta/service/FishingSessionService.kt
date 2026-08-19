package fi.anssi.kalakartta.service

import android.app.*
import android.content.Context
import android.content.Intent
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
    private var intervalSeconds: Int = 0
    private var lastSavedTimestamp: Long = 0L

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val CHANNEL_ID = "FishingSessionChannel"
        const val NOTIFICATION_ID = 101
        const val ACTION_STOP = "STOP_SESSION"
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession()
            return START_NOT_STICKY
        }
        
        val interval = intent?.getIntExtra("INTERVAL", 30) ?: 30
        if (recording) {
            intervalSeconds = interval
            // Päivitetään ilmoitus jos tarpeen tai lähetetään uusi broadcast
            val updateIntent = Intent("fi.anssi.kalakartta.SESSION_STARTED")
            sendBroadcast(updateIntent)
        } else {
            startSession(interval)
        }
        
        return START_STICKY
    }

    private fun startSession(interval: Int) {
        if (recording) return
        
        recording = true
        intervalSeconds = interval
        startedAt = System.currentTimeMillis()
        totalDistance = 0.0
        lastLocation = null
        lastSavedTimestamp = 0L

        serviceScope.launch {
            val session = FishingSession(startedAt = startedAt)
            currentSessionId = db.fishingSessionDao().insert(session)
            
            launch(Dispatchers.Main) {
                startForeground(NOTIFICATION_ID, createNotification())
                requestLocationUpdates()
                
                // Ilmoitetaan MainActivitylle että sessio on alkanut (ja interval on asetettu)
                val intent = Intent("fi.anssi.kalakartta.SESSION_STARTED")
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
            if (sessionId != -1L) {
                val session = db.fishingSessionDao().getById(sessionId)
                if (session != null) {
                    db.fishingSessionDao().update(session.copy(endedAt = System.currentTimeMillis()))
                }
            }
            
            launch(Dispatchers.Main) {
                locationManager.removeUpdates(locationListener)
                stopForeground(STOP_FOREGROUND_REMOVE)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
                
                // Ilmoitetaan MainActivitylle että sessio loppui, jotta se voi avata dialogin
                val intent = Intent("fi.anssi.kalakartta.SESSION_ENDED")
                intent.putExtra("SESSION_ID", sessionId)
                sendBroadcast(intent)
                
                stopSelf()
            }
        }
    }

    private fun requestLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
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
            
            // Päivitetään matka
            lastLocation?.let {
                totalDistance += it.distanceTo(location)
            }
            lastLocation = location

            // Tallennetaan reittipiste jos väli on kulunut
            if (now - lastSavedTimestamp >= intervalSeconds * 1000L) {
                saveTrackPoint(location)
                lastSavedTimestamp = now
            }
            
            // Päivitetään ilmoitus
            updateNotification()
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
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
        val distanceStr = String.format("%.1f km", totalDistance / 1000.0).replace(".", ",")

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
    fun getIntervalSeconds() = intervalSeconds

    fun getCurrentTrackPoints(): List<TrackPoint> {
        return db.trackPointDao().getPointsForSession(currentSessionId)
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
