package fi.anssi.kalakartta.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class EditFishingSessionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var sessionId: Long = -1L
    private var session: FishingSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
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
        } catch (e: Exception) {
            // Ignored
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_fishing_session)

        db = AppDatabase.getInstance(this)
        sessionId = intent.getLongExtra("SESSION_ID", -1L)

        if (sessionId == -1L) {
            finish()
            return
        }

        findViewById<TextView>(R.id.saveLink).setOnClickListener { saveAndFinish() }
        findViewById<TextView>(R.id.backLink).setOnClickListener { finish() }

        loadSessionData()
    }

    private fun loadSessionData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val s = db.fishingSessionDao().getById(sessionId)
            val points = db.trackPointDao().getPointsForSession(sessionId)
            
            withContext(Dispatchers.Main) {
                if (s == null) {
                    finish()
                    return@withContext
                }
                session = s
                
                // Otsikko
                val dateFmt = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                
                val startCal = Calendar.getInstance().apply { timeInMillis = s.startedAt }
                val endCal = s.endedAt?.let { Calendar.getInstance().apply { timeInMillis = it } }
                
                val title = if (endCal == null || isSameDay(startCal, endCal)) {
                    val dateStr = dateFmt.format(Date(s.startedAt))
                    val startTimeStr = timeFmt.format(Date(s.startedAt))
                    val endTimeStr = s.endedAt?.let { timeFmt.format(Date(it)) } ?: "?"
                    "Kalastussessio $dateStr: $startTimeStr - $endTimeStr"
                } else {
                    val startDateStr = dateFmt.format(Date(s.startedAt))
                    val startTimeStr = timeFmt.format(Date(s.startedAt))
                    val endDateStr = dateFmt.format(Date(s.endedAt!!))
                    val endTimeStr = timeFmt.format(Date(s.endedAt))
                    "Kalastussessio: $startDateStr $startTimeStr - $endDateStr $endTimeStr"
                }
                findViewById<TextView>(R.id.sessionTitleText).text = title
                
                // Statsit
                val duration = (s.endedAt ?: s.startedAt) - s.startedAt
                val hours = TimeUnit.MILLISECONDS.toHours(duration)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
                val durationStr = if (hours > 0) "${hours} h ${minutes} min ${seconds} s" else "${minutes} min ${seconds} s"
                findViewById<TextView>(R.id.durationText).text = "Kesto: $durationStr"
                
                var totalDistance = 0.0
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
                    totalDistance += results[0]
                }
                val distanceStr = String.format("%.3f km", totalDistance / 1000.0).replace(".", ",")
                findViewById<TextView>(R.id.distanceText).text = "Matka: $distanceStr"
                findViewById<TextView>(R.id.trackPointsText).text = "Reittipisteitä: ${points.size} kpl"
                
                // Edit kentät
                findViewById<EditText>(R.id.fishermanInput).setText(s.fisherman)
                findViewById<EditText>(R.id.notesInput).setText(s.notes)
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun saveAndFinish() {
        val newFisherman = findViewById<EditText>(R.id.fishermanInput).text.toString()
        val newNotes = findViewById<EditText>(R.id.notesInput).text.toString()
        
        val s = session ?: return
        
        lifecycleScope.launch(Dispatchers.IO) {
            db.fishingSessionDao().update(s.copy(fisherman = newFisherman, notes = newNotes))
            withContext(Dispatchers.Main) {
                Toast.makeText(this@EditFishingSessionActivity, R.string.save_success, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }
}
