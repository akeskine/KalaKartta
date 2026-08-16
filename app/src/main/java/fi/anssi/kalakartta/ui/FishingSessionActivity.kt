package fi.anssi.kalakartta.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CalendarView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishingSession
import fi.anssi.kalakartta.data.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FishingSessionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var calendarView: CalendarView
    private lateinit var sessionsContainer: LinearLayout
    private lateinit var sessionsLabel: TextView
    private var allSessions: List<FishingSession> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fishing_sessions)

        db = AppDatabase.getInstance(this)
        calendarView = findViewById(R.id.calendarView)
        sessionsContainer = findViewById(R.id.sessionsContainer)
        sessionsLabel = findViewById(R.id.sessionsLabel)

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            showSessionsForDate(year, month, dayOfMonth)
        }

        loadSessions()
    }

    private fun loadSessions() {
        lifecycleScope.launch(Dispatchers.IO) {
            allSessions = db.fishingSessionDao().getAll().filter { it.endedAt != null }
            withContext(Dispatchers.Main) {
                // Oletuksena näytetään tämän päivän sessiot
                val cal = Calendar.getInstance()
                showSessionsForDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            }
        }
    }

    private fun showSessionsForDate(year: Int, month: Int, dayOfMonth: Int) {
        sessionsContainer.removeAllViews()
        
        val targetCal = Calendar.getInstance()
        targetCal.set(year, month, dayOfMonth, 0, 0, 0)
        targetCal.set(Calendar.MILLISECOND, 0)
        val startOfDay = targetCal.timeInMillis
        
        targetCal.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = targetCal.timeInMillis

        val sessionsForDate = allSessions.filter { it.startedAt in startOfDay until endOfDay }

        if (sessionsForDate.isEmpty()) {
            sessionsLabel.visibility = View.GONE
            val noSessionsText = TextView(this).apply {
                text = "Ei tallennettuja sessioita tälle päivälle."
                setPadding(0, 20, 0, 20)
            }
            sessionsContainer.addView(noSessionsText)
        } else {
            sessionsLabel.visibility = View.VISIBLE
            sessionsForDate.forEach { session ->
                addSessionItem(session)
            }
        }
    }

    private fun addSessionItem(session: FishingSession) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startTime = timeFormat.format(Date(session.startedAt))
        val endTime = session.endedAt?.let { timeFormat.format(Date(it)) } ?: "?"

        val sessionView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
            isClickable = true
            isFocusable = true
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        val titleText = TextView(this).apply {
            text = "Sessio: $startTime - $endTime"
            textSize = 18f
            setTextColor(Color.BLACK)
        }
        sessionView.addView(titleText)

        val detailsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(20, 10, 0, 10)
        }

        sessionView.setOnClickListener {
            if (detailsContainer.visibility == View.VISIBLE) {
                detailsContainer.visibility = View.GONE
            } else {
                if (detailsContainer.childCount == 0) {
                    loadSessionDetails(session, detailsContainer)
                }
                detailsContainer.visibility = View.VISIBLE
            }
        }

        sessionView.addView(detailsContainer)
        sessionsContainer.addView(sessionView)
    }

    private fun loadSessionDetails(session: FishingSession, container: LinearLayout) {
        lifecycleScope.launch(Dispatchers.IO) {
            val points = db.trackPointDao().getPointsForSession(session.id)
            
            val duration = (session.endedAt ?: session.startedAt) - session.startedAt
            val hours = TimeUnit.MILLISECONDS.toHours(duration)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
            val durationStr = if (hours > 0) "${hours} h ${minutes} min ${seconds} s" else "${minutes} min ${seconds} s"
            
            var totalDistance = 0.0
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val results = FloatArray(1)
                android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
                totalDistance += results[0]
            }
            val distanceStr = String.format("%.2f km", totalDistance / 1000.0).replace(".", ",")

            withContext(Dispatchers.Main) {
                val infoText = TextView(this@FishingSessionActivity).apply {
                    text = "Kesto: $durationStr\nMatka: $distanceStr\nReittipisteitä: ${points.size} kpl"
                    setPadding(0, 0, 0, 10)
                }
                container.addView(infoText)

                val mapButton = Button(this@FishingSessionActivity).apply {
                    text = "Näytä kartalla"
                    setOnClickListener {
                        val intent = Intent()
                        intent.putExtra("EXTRA_SESSION_ID", session.id)
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                }
                container.addView(mapButton)
            }
        }
    }
}
