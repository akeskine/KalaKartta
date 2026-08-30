package fi.anssi.kalakartta.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.anssi.kalakartta.MainActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.utils.SunService
import java.util.*

class TalkingClockService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var intervalMinutes = 10
    private var isTtsInitialized = false
    private var pendingAction: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val sunService = SunService()
    private var cachedSunTimes: Pair<Calendar, Calendar>? = null
    private var lastCalculationDate: String = ""
    private var lastCalculationLocation: Location? = null
    private var locationManager: LocationManager? = null
    private var lastKnownLocation: Location? = null
    
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var focusRequest: AudioFocusRequest? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastKnownLocation = location
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        tts = TextToSpeech(this, this)
        
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                60000L, // 1 minuutti
                100f,   // 100 metriä
                locationListener
            )
            locationManager?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                60000L,
                100f,
                locationListener
            )
            lastKnownLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            Log.e("TalkingClockService", "Sijaintilupia ei ole annettu", e)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Puhuva kello päällä")
            .setSmallIcon(R.drawable.ahven)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Puhuva kello"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "TalkingClockChannel"
        const val NOTIFICATION_ID = 102
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newInterval = intent?.getIntExtra("interval", -1) ?: -1
        val intervalChanged = newInterval != -1 && newInterval != intervalMinutes
        if (intervalChanged) {
            intervalMinutes = newInterval
        }
        
        if (isTtsInitialized) {
            handleAction(intent?.action)
        } else {
            pendingAction = intent?.action
        }
        
        return START_STICKY
    }

    private fun handleAction(action: String?) {
        when (action) {
            "START_IMMEDIATELY" -> {
                // Ei puhuta heti, vaan ajoitetaan seuraava tasaväli
                scheduleNext()
            }
            "SESSION_STARTED" -> {
                acquireWakeLock()
                speakSessionStarted()
                scheduleNext()
            }
            "SESSION_ENDED" -> {
                acquireWakeLock()
                speakSessionEnded()
            }
            "TALK" -> {
                acquireWakeLock()
                speakCurrentTime()
                scheduleNext()
            }
            else -> {
                // Oletuksena ajoitetaan vain seuraava, jos intervalli muuttui mutta ei erityistä actionia
                scheduleNext()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { 
                    abandonAudioFocus()
                    releaseWakeLock()
                    if (utteranceId == "SessionEnded") {
                        stopSelf()
                    }
                }
                override fun onError(utteranceId: String?) { 
                    abandonAudioFocus()
                    releaseWakeLock()
                    if (utteranceId == "SessionEnded") {
                        stopSelf()
                    }
                }
            })

            val result = tts?.setLanguage(Locale("fi", "FI"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TalkingClockService", "Finnish language not supported")
            } else {
                isTtsInitialized = true
                if (pendingAction != null) {
                    handleAction(pendingAction)
                    pendingAction = null
                } else {
                    // Ajoitetaan seuraava tasaväli ilman välitöntä puhetta
                    scheduleNext()
                }
            }
        } else {
            Log.e("TalkingClockService", "TTS Initialization failed")
        }
    }

    private fun scheduleNext() {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        
        // Lasketaan seuraava tasaväli keskiyöstä alkaen, jotta vältetään siirtymät
        val minutesSinceMidnight = (calendar.get(Calendar.HOUR_OF_DAY) * 60) + calendar.get(Calendar.MINUTE)
        val minutesToNext = intervalMinutes - (minutesSinceMidnight % intervalMinutes)
        
        val nextTime = Calendar.getInstance()
        nextTime.timeInMillis = now
        nextTime.add(Calendar.MINUTE, minutesToNext)
        nextTime.set(Calendar.SECOND, 0)
        nextTime.set(Calendar.MILLISECOND, 0)
        
        // Varmistetaan, että seuraava aika on vähintään 2 sekunnin päässä (lisätty marginaalia)
        if (nextTime.timeInMillis <= now + 2000) {
            nextTime.add(Calendar.MINUTE, intervalMinutes)
        }
        
        val triggerAtMillis = nextTime.timeInMillis
        
        val intent = Intent(this, TalkingClockService::class.java).apply {
            action = "TALK"
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Käytetään setAlarmClockia jos mahdollista, muuten setExactAndAllowWhileIdle.
                // Molemmat ovat tarkkoja, mutta setAlarmClock on kaikkein "tärkein".
                val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }

                if (canScheduleExact) {
                    val showIntent = Intent(this, MainActivity::class.java)
                    val showPendingIntent = PendingIntent.getActivity(
                        this, 0, showIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e("TalkingClockService", "Hälytyksen asetus epäonnistui: ${e.message}", e)
            // Viimeinen oljenkorsi
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
        
        Log.d("TalkingClockService", "Seuraava puhe ajoitettu: ${nextTime.time} (väli $intervalMinutes min)")
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KalaKartta:TalkingClockWakeLock")
        }
        wakeLock?.acquire(10 * 1000L) // 10s timeout turvallisuuden vuoksi
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun speakCurrentTime() {
        if (!isTtsInitialized) return
        
        val now = Calendar.getInstance()
        // Pieni pyöristys ylöspäin jos ollaan aivan sekunnin rajalla (esim. 15:44:59.950)
        now.add(Calendar.MILLISECOND, 500)
        
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val salutation = prefs.getString("talking_clock_salutation", "") ?: ""
        
        var text = formatTimeFinnish(hour, minute)
        if (salutation.isNotEmpty()) {
            text = "$salutation $text"
        }

        val sunText = getSunTimeSpeech(lastKnownLocation, prefs)
        if (sunText.isNotEmpty()) {
            text = "$text $sunText"
        }

        if (prefs.getBoolean("talking_clock_battery", false)) {
            val batteryLevel = getBatteryLevel()
            if (batteryLevel != -1) {
                text = "$text Akun varaus on $batteryLevel prosenttia."
            }
        }
        
        speakText(text)
    }

    private fun speakSessionStarted() {
        if (!isTtsInitialized) return
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val salutation = prefs.getString("talking_clock_salutation", "") ?: ""
        var text = getString(R.string.talking_clock_session_started, intervalMinutes)
        if (salutation.isNotEmpty()) {
            text = "$salutation, $text"
        }
        speakText(text)
    }

    private fun speakSessionEnded() {
        if (!isTtsInitialized) return
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val salutation = prefs.getString("talking_clock_salutation", "") ?: ""
        var text = getString(R.string.talking_clock_session_ended)
        if (salutation.isNotEmpty()) {
            text = "$salutation! $text"
        }
        speakText(text, true, "SessionEnded") // Käytetään flushia ja lopetetaan
    }

    private fun speakText(text: String, flush: Boolean = true, utteranceId: String = "TalkingClock") {
        if (requestAudioFocus()) {
            val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = tts?.speak(text, queueMode, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                releaseWakeLock()
                abandonAudioFocus()
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun getSunTimeSpeech(location: Location?, prefs: android.content.SharedPreferences): String {
        if (location == null) return ""
        
        val now = Calendar.getInstance()
        val tellSunrise = prefs.getBoolean("talking_clock_sunrise", false)
        val tellSunset = prefs.getBoolean("talking_clock_sunset", false)
        
        if (!tellSunrise && !tellSunset) return ""

        val sunriseLimitMs = prefs.getInt("talking_clock_sunrise_limit", 2) * 3600000L
        val sunsetLimitMs = prefs.getInt("talking_clock_sunset_limit", 2) * 3600000L
        
        val validEvents = mutableListOf<Pair<Long, String>>()
        
        // Tarkistetaan tämän päivän ja huomisen ajat
        for (i in 0..1) {
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val sunTimes = getOrUpdateSunTimes(location, date) ?: continue
            val (sunrise, sunset) = sunTimes
            
            val diffSunrise = sunrise.timeInMillis - now.timeInMillis
            val diffSunset = sunset.timeInMillis - now.timeInMillis
            
            if (tellSunrise && diffSunrise > 0 && diffSunrise <= sunriseLimitMs) {
                validEvents.add(diffSunrise to formatRemainingTime("nousuun", diffSunrise))
            }
            
            if (tellSunset && diffSunset > 0 && diffSunset <= sunsetLimitMs) {
                validEvents.add(diffSunset to formatRemainingTime("laskuun", diffSunset))
            }
        }

        return validEvents.minByOrNull { it.first }?.second ?: ""
    }

    private fun getOrUpdateSunTimes(currentLocation: Location, date: Calendar): Pair<Calendar, Calendar>? {
        val dateKey = "${date.get(Calendar.YEAR)}-${date.get(Calendar.DAY_OF_YEAR)}"
        
        // Välimuistitus on nyt päiväkohtainen. Jos pyydetty päivä on eri kuin viimeksi laskettu, lasketaan uudestaan.
        // Huom: Tämä tukee nyt vain yhtä päivää kerrallaan välimuistissa, mutta getSunTimeSpeech kutsuu tätä
        // peräkkäin tänään ja huomenna.
        val needsUpdate = cachedSunTimes == null || 
                          dateKey != lastCalculationDate || 
                          (lastCalculationLocation?.distanceTo(currentLocation) ?: Float.MAX_VALUE) > 50000

        if (needsUpdate) {
            cachedSunTimes = sunService.getSunriseSunset(
                currentLocation.latitude, 
                currentLocation.longitude, 
                date
            )
            lastCalculationDate = dateKey
            lastCalculationLocation = currentLocation
        }
        
        return cachedSunTimes
    }

    private fun formatRemainingTime(event: String, diffMs: Long): String {
        val totalMinutes = diffMs / 60000
        val hours = (totalMinutes / 60).toInt()
        val minutes = (totalMinutes % 60).toInt()

        val hoursStr = when (hours) {
            0 -> ""
            1 -> "yksi tunti"
            else -> {
                val hStr = when (hours) {
                    2 -> "kaksi"; 3 -> "kolme"; 4 -> "neljä"; 5 -> "viisi"; 6 -> "kuusi"
                    7 -> "seitsemän"; 8 -> "kahdeksan"; 9 -> "yhdeksän"; 10 -> "kymmenen"
                    11 -> "yksitoista"; 12 -> "kaksitoista"
                    13 -> "kolmetoista"; 14 -> "neljätoista"; 15 -> "viisitoista"
                    16 -> "kuusitoista"; 17 -> "seitsemäntoista"; 18 -> "kahdeksantoista"
                    19 -> "yhdeksäntoista"; 20 -> "kaksikymmentä"; 21 -> "kaksikymmentäyksi"
                    22 -> "kaksikymmentäkaksi"; 23 -> "kaksikymmentäkolme"; 24 -> "kaksikymmentäneljä"
                    else -> hours.toString()
                }
                "$hStr tuntia"
            }
        }

        val minutesStr = when (minutes) {
            0 -> if (hours == 0) "nolla minuuttia" else ""
            1 -> "yksi minuutti"
            else -> {
                val mStr = when (minutes) {
                    2 -> "kaksi"; 3 -> "kolme"; 4 -> "neljä"; 5 -> "viisi"; 6 -> "kuusi"
                    7 -> "seitsemän"; 8 -> "kahdeksan"; 9 -> "yhdeksän"; 10 -> "kymmenen"
                    11 -> "yksitoista"; 12 -> "kaksitoista"; 13 -> "kolmetoista"; 14 -> "neljätoista"
                    15 -> "viisitoista"; 16 -> "kuusitoista"; 17 -> "seitsemäntoista"; 18 -> "kahdeksantoista"
                    19 -> "yhdeksäntoista"; 20 -> "kaksikymmentä"; 30 -> "kolmekymmentä"; 40 -> "neljäkymmentä"
                    50 -> "viisikymmentä"
                    else -> {
                        val tens = minutes / 10
                        val ones = minutes % 10
                        val tensStr = when (tens) {
                            2 -> "kaksikymmentä"; 3 -> "kolmekymmentä"; 4 -> "neljäkymmentä"
                            5 -> "viisikymmentä"; else -> ""
                        }
                        val onesStr = when (ones) {
                            1 -> "yksi"; 2 -> "kaksi"; 3 -> "kolme"; 4 -> "neljä"; 5 -> "viisi"
                            6 -> "kuusi"; 7 -> "seitsemän"; 8 -> "kahdeksan"; 9 -> "yhdeksän"
                            else -> ""
                        }
                        tensStr + onesStr
                    }
                }
                "$mStr minuuttia"
            }
        }

        val timePart = if (hoursStr.isNotEmpty() && minutesStr.isNotEmpty()) {
            "$hoursStr $minutesStr"
        } else {
            hoursStr + minutesStr
        }

        return "Auringon $event on $timePart."
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            applicationContext.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level != -1 && scale != -1) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }
    }

    private fun formatTimeFinnish(hour: Int, minute: Int): String {
        // "Kello on kaksikymmentäyksi viisikymmentä." (klo 21:50)
        // "Kello on yhdeksän." (klo 9:00)
        // "Kello on yksi kymmenen." (klo 1:10)
        
        val hourStr = when (hour) {
            0 -> "nolla"
            1 -> "yksi"
            2 -> "kaksi"
            3 -> "kolme"
            4 -> "neljä"
            5 -> "viisi"
            6 -> "kuusi"
            7 -> "seitsemän"
            8 -> "kahdeksan"
            9 -> "yhdeksän"
            10 -> "kymmenen"
            11 -> "yksitoista"
            12 -> "kaksitoista"
            13 -> "kolmetoista"
            14 -> "neljätoista"
            15 -> "viisitoista"
            16 -> "kuusitoista"
            17 -> "seitsemäntoista"
            18 -> "kahdeksantoista"
            19 -> "yhdeksäntoista"
            20 -> "kaksikymmentä"
            21 -> "kaksikymmentäyksi"
            22 -> "kaksikymmentäkaksi"
            23 -> "kaksikymmentäkolme"
            else -> hour.toString()
        }
        
        if (minute == 0) {
            return "Kello on $hourStr."
        }
        
        val minStr = when (minute) {
            1 -> "nolla yksi"
            2 -> "nolla kaksi"
            3 -> "nolla kolme"
            4 -> "nolla neljä"
            5 -> "nolla viisi"
            6 -> "nolla kuusi"
            7 -> "nolla seitsemän"
            8 -> "nolla kahdeksan"
            9 -> "nolla yhdeksän"
            10 -> "kymmenen"
            11 -> "yksitoista"
            12 -> "kaksitoista"
            13 -> "kolmetoista"
            14 -> "neljätoista"
            15 -> "viisitoista"
            16 -> "kuusitoista"
            17 -> "seitsemäntoista"
            18 -> "kahdeksantoista"
            19 -> "yhdeksäntoista"
            20 -> "kaksikymmentä"
            30 -> "kolmekymmentä"
            40 -> "neljäkymmentä"
            50 -> "viisikymmentä"
            else -> {
                if (minute < 20) {
                    // Tämä haara ei enää pitäisi olla saavutettavissa 1-19 välillä,
                    // mutta pidetään varmuuden vuoksi oikein muotoiltuna jos minute < 10.
                    if (minute < 10) "nolla " + when(minute) {
                        1 -> "yksi"; 2 -> "kaksi"; 3 -> "kolme"; 4 -> "neljä"; 5 -> "viisi"
                        6 -> "kuusi"; 7 -> "seitsemän"; 8 -> "kahdeksan"; 9 -> "yhdeksän"
                        else -> ""
                    } else minute.toString()
                } else {
                    val tens = minute / 10
                    val ones = minute % 10
                    val tensStr = when (tens) {
                        2 -> "kaksikymmentä"
                        3 -> "kolmekymmentä"
                        4 -> "neljäkymmentä"
                        5 -> "viisikymmentä"
                        else -> ""
                    }
                    val onesStr = when (ones) {
                        0 -> ""
                        1 -> "yksi"
                        2 -> "kaksi"
                        3 -> "kolme"
                        4 -> "neljä"
                        5 -> "viisi"
                        6 -> "kuusi"
                        7 -> "seitsemän"
                        8 -> "kahdeksan"
                        9 -> "yhdeksän"
                        else -> ""
                    }
                    tensStr + onesStr
                }
            }
        }
        
        return "Kello on $hourStr $minStr."
    }

    override fun onDestroy() {
        val intent = Intent(this, TalkingClockService::class.java).apply {
            action = "TALK"
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent, 
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }

        locationManager?.removeUpdates(locationListener)
        tts?.stop()
        tts?.shutdown()
        abandonAudioFocus()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
