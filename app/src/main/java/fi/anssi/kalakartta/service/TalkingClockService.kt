package fi.anssi.kalakartta.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.anssi.kalakartta.MainActivity
import fi.anssi.kalakartta.R
import java.util.*

class TalkingClockService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var intervalMinutes = 10
    private var isTtsInitialized = false
    
    private val talkRunnable = object : Runnable {
        override fun run() {
            speakCurrentTime()
            scheduleNext()
        }
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
        val newInterval = intent?.getIntExtra("interval", 10) ?: 10
        if (newInterval != intervalMinutes) {
            intervalMinutes = newInterval
            if (isTtsInitialized) {
                handler.removeCallbacks(talkRunnable)
                scheduleNext()
            }
        } else if (intent?.action == "START_IMMEDIATELY") {
            handler.removeCallbacks(talkRunnable)
            scheduleNext()
        }
        
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("fi", "FI"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TalkingClockService", "Finnish language not supported")
            } else {
                isTtsInitialized = true
                scheduleNext()
            }
        } else {
            Log.e("TalkingClockService", "TTS Initialization failed")
        }
    }

    private fun scheduleNext() {
        val now = Calendar.getInstance()
        val minutesSinceHour = now.get(Calendar.MINUTE)
        
        // Lasketaan seuraava kerta tasatunneista lähtien
        // Esim. jos nyt on 20:47 ja väli 10 min, seuraava on 20:50.
        // Laskukaava: interval - (min % interval)
        val minutesToNext = intervalMinutes - (minutesSinceHour % intervalMinutes)
        
        val nextTime = Calendar.getInstance()
        nextTime.add(Calendar.MINUTE, minutesToNext)
        nextTime.set(Calendar.SECOND, 0)
        nextTime.set(Calendar.MILLISECOND, 0)
        
        val delayMs = nextTime.timeInMillis - System.currentTimeMillis()
        
        // Varmistetaan että viive on vähintään sekunti, jos nyt sattui olemaan juuri tasaminuutti
        val finalDelay = if (delayMs < 1000) intervalMinutes * 60 * 1000L else delayMs
        
        handler.removeCallbacks(talkRunnable)
        handler.postDelayed(talkRunnable, finalDelay)
        
        Log.d("TalkingClockService", "Scheduled next talk in $minutesToNext minutes (at ${nextTime.get(Calendar.HOUR_OF_DAY)}:${nextTime.get(Calendar.MINUTE)})")
    }

    private fun speakCurrentTime() {
        if (!isTtsInitialized) return
        
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val salutation = prefs.getString("talking_clock_salutation", "") ?: ""
        
        var text = formatTimeFinnish(hour, minute)
        if (salutation.isNotEmpty()) {
            text = "$salutation $text"
        }
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TalkingClock")
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
        handler.removeCallbacks(talkRunnable)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
