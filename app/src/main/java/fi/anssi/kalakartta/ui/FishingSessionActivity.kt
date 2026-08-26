package fi.anssi.kalakartta.ui

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.View
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishingSession
import fi.anssi.kalakartta.data.TrackPoint
import fi.anssi.kalakartta.utils.formatFishermanName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FishingSessionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var calendarGrid: GridLayout
    private lateinit var monthYearText: TextView
    private lateinit var sessionsContainer: LinearLayout
    private lateinit var sessionsLabel: TextView
    private var allSessions: List<FishingSession> = emptyList()
    private var currentCalendar = Calendar.getInstance()
    private var selectedCalendar = Calendar.getInstance()
    private var openSessionId: Long = -1L
    
    companion object {
        const val EDIT_SESSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        setContentView(R.layout.activity_fishing_sessions)

        if (savedInstanceState != null) {
            currentCalendar.timeInMillis = savedInstanceState.getLong("currentCalendar", System.currentTimeMillis())
            selectedCalendar.timeInMillis = savedInstanceState.getLong("selectedCalendar", System.currentTimeMillis())
            openSessionId = savedInstanceState.getLong("openSessionId", -1L)
        }

        db = AppDatabase.getInstance(this)
        calendarGrid = findViewById(R.id.calendarGrid)
        monthYearText = findViewById(R.id.monthYearText)
        sessionsContainer = findViewById(R.id.sessionsContainer)
        sessionsLabel = findViewById(R.id.sessionsLabel)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.prevMonthButton).setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        findViewById<Button>(R.id.nextMonthButton).setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }

        loadSessions()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_SESSION_REQUEST && resultCode == RESULT_OK) {
            loadSessions()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("currentCalendar", currentCalendar.timeInMillis)
        outState.putLong("selectedCalendar", selectedCalendar.timeInMillis)
        outState.putLong("openSessionId", openSessionId)
    }

    private fun loadSessions() {
        lifecycleScope.launch(Dispatchers.IO) {
            allSessions = db.fishingSessionDao().getFinishedSessions()
            withContext(Dispatchers.Main) {
                updateCalendar()
                showSessionsForDate(
                    selectedCalendar.get(Calendar.YEAR),
                    selectedCalendar.get(Calendar.MONTH),
                    selectedCalendar.get(Calendar.DAY_OF_MONTH)
                )
            }
        }
    }

    private fun updateCalendar() {
        calendarGrid.removeAllViews()
        
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("fi", "FI"))
        monthYearText.text = sdf.format(currentCalendar.time).replaceFirstChar { it.uppercase() }

        val cal = currentCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        
        // Suomalainen viikko alkaa maanantaista (Calendar.MONDAY = 2)
        // cal.get(Calendar.DAY_OF_WEEK) palauttaa: SUN=1, MON=2, ..., SAT=7
        var firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
        if (firstDayOfWeek < 0) firstDayOfWeek += 7

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Lisätään viikonpäivien nimet
        val daysOfWeek = listOf("ma", "ti", "ke", "to", "pe", "la", "su")
        daysOfWeek.forEach { dayName ->
            val tv = TextView(this).apply {
                text = dayName
                gravity = android.view.Gravity.CENTER
                setPadding(0, 10, 0, 10)
                textSize = 12f
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            calendarGrid.addView(tv, params)
        }

        // Tyhjät välit ennen ensimmäistä päivää
        for (i in 0 until firstDayOfWeek) {
            val emptyView = View(this)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = 1
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            calendarGrid.addView(emptyView, params)
        }

        val inflater = LayoutInflater.from(this)
        for (day in 1..daysInMonth) {
            val dayView = inflater.inflate(R.layout.item_calendar_day, calendarGrid, false)
            val dayText = dayView.findViewById<TextView>(R.id.dayText)
            val indicator = dayView.findViewById<View>(R.id.sessionIndicator)
            
            dayText.text = day.toString()

            val dayCal = cal.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, day)
            
            // Tarkista onko sessioita
            val hasSessions = allSessions.any {
                val sCal = Calendar.getInstance()
                sCal.timeInMillis = it.startedAt
                sCal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                sCal.get(Calendar.MONTH) == dayCal.get(Calendar.MONTH) &&
                sCal.get(Calendar.DAY_OF_MONTH) == dayCal.get(Calendar.DAY_OF_MONTH)
            }
            
            if (hasSessions) {
                indicator.visibility = View.VISIBLE
            }

            // Korosta valittu päivä
            if (dayCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR) &&
                dayCal.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH) &&
                dayCal.get(Calendar.DAY_OF_MONTH) == selectedCalendar.get(Calendar.DAY_OF_MONTH)) {
                val outValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.colorControlHighlight, outValue, true)
                dayView.setBackgroundColor(outValue.data)
            }

            dayView.setOnClickListener {
                selectedCalendar = dayCal
                updateCalendar()
                showSessionsForDate(
                    dayCal.get(Calendar.YEAR),
                    dayCal.get(Calendar.MONTH),
                    dayCal.get(Calendar.DAY_OF_MONTH)
                )
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            calendarGrid.addView(dayView, params)
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(titleText)
            
            val editIcon = ImageView(this@FishingSessionActivity).apply {
                setImageResource(R.drawable.ic_edit)
                setPadding(20, 20, 20, 20)
                val safeMargin = resources.getDimensionPixelSize(R.dimen.landscape_safe_margin)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = safeMargin
                }
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    showSessionMenu(it, session)
                }
            }
            addView(editIcon)
        }
        sessionView.addView(headerRow)

        val detailsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(20, 10, 0, 10)
        }

        sessionView.setOnClickListener {
            if (detailsContainer.visibility == View.VISIBLE) {
                detailsContainer.visibility = View.GONE
                openSessionId = -1L
            } else {
                openSessionId = session.id
                if (detailsContainer.childCount == 0) {
                    loadSessionDetails(session, detailsContainer)
                }
                detailsContainer.visibility = View.VISIBLE
            }
        }

        sessionView.addView(detailsContainer)
        sessionsContainer.addView(sessionView)

        if (session.id == openSessionId) {
            loadSessionDetails(session, detailsContainer)
            detailsContainer.visibility = View.VISIBLE
        }
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
            val distanceStr = String.format("%.3f km", totalDistance / 1000.0).replace(".", ",")

            withContext(Dispatchers.Main) {
                val infoText = TextView(this@FishingSessionActivity).apply {
                    var textContent = "Kesto: $durationStr\nMatka: $distanceStr\nReittipisteitä: ${points.size} kpl"
                    if (session.fisherman.isNotBlank()) {
                        textContent += "\nKalastaja: ${formatFishermanName(session.fisherman)}"
                    }
                    text = textContent
                    setPadding(0, 0, 0, 10)
                }
                container.addView(infoText)

                val replayRow = LinearLayout(this@FishingSessionActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 10, 0, 10)
                }

                val replayLink = TextView(this@FishingSessionActivity).apply {
                    text = "Toista"
                    setTextColor(androidx.core.content.ContextCompat.getColor(this@FishingSessionActivity, R.color.link_color))
                    paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                    setPadding(0, 10, 0, 10)
                    isClickable = true
                    setOnClickListener {
                        val intent = Intent()
                        intent.putExtra("EXTRA_SESSION_ID", session.id)
                        intent.putExtra("EXTRA_REPLAY_REQUEST", true)
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                }
                replayRow.addView(replayLink)

                container.addView(replayRow)
            }
        }
    }

    private fun showSessionMenu(view: View, session: FishingSession) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Muokkaa")
        popup.menu.add("Poista")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Muokkaa" -> {
                    val intent = Intent(this, EditFishingSessionActivity::class.java)
                    intent.putExtra("SESSION_ID", session.id)
                    startActivityForResult(intent, EDIT_SESSION_REQUEST)
                    true
                }
                "Poista" -> {
                    confirmDeleteSession(session)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmDeleteSession(session: FishingSession) {
        val dateFmt = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        val startCal = Calendar.getInstance().apply { timeInMillis = session.startedAt }
        val endCal = session.endedAt?.let { Calendar.getInstance().apply { timeInMillis = it } }

        val sessionDetails = if (endCal == null || isSameDay(startCal, endCal)) {
            val dateStr = dateFmt.format(Date(session.startedAt))
            val startTimeStr = timeFmt.format(Date(session.startedAt))
            val endTimeStr = session.endedAt?.let { timeFmt.format(Date(it)) } ?: "?"
            "Kalastussessio $dateStr: $startTimeStr - $endTimeStr"
        } else {
            val startDateStr = dateFmt.format(Date(session.startedAt))
            val startTimeStr = timeFmt.format(Date(session.startedAt))
            val endDateStr = dateFmt.format(Date(session.endedAt!!))
            val endTimeStr = timeFmt.format(Date(session.endedAt))
            "Kalastussessio: $startDateStr $startTimeStr - $endDateStr $endTimeStr"
        }

        val message = "$sessionDetails\n\nHaluatko varmasti poistaa tämän kalastussession ja sen kaikki reittipisteet?"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Poistetaanko kalastussessio?")
            .setMessage(message)
            .setPositiveButton("Poista") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.fishingSessionDao().deleteById(session.id)
                    db.trackPointDao().deleteForSession(session.id)
                    loadSessions()
                }
            }
            .setNegativeButton("Peruuta", null)
            .show()
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
