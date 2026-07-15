package fi.anssi.kalakartta.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SummaryActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private var startDateTime: Calendar? = null
    private var endDateTime: Calendar? = null
    private var startTimeSet = false
    private var endTimeSet = false

    private lateinit var startDateButton: TextView
    private lateinit var startTimeButton: TextView
    private lateinit var endDateButton: TextView
    private lateinit var endTimeButton: TextView
    private lateinit var summaryResultText: TextView

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)

        db = AppDatabase.getInstance(this)

        startDateButton = findViewById(R.id.startDateButton)
        startTimeButton = findViewById(R.id.startTimeButton)
        endDateButton = findViewById(R.id.endDateButton)
        endTimeButton = findViewById(R.id.endTimeButton)
        summaryResultText = findViewById(R.id.summaryResultText)

        findViewById<TextView>(R.id.todaySummaryButton).setOnClickListener {
            generateTodaySummary()
        }

        startDateButton.setOnClickListener {
            showDatePicker(true)
        }

        startTimeButton.setOnClickListener {
            showTimePicker(true)
        }

        endDateButton.setOnClickListener {
            showDatePicker(false)
        }

        endTimeButton.setOnClickListener {
            showTimePicker(false)
        }

        findViewById<TextView>(R.id.generateRangeSummaryButton).setOnClickListener {
            generateRangeSummary()
        }

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun showDatePicker(isStart: Boolean) {
        val cal = if (isStart) startDateTime ?: Calendar.getInstance() else endDateTime ?: Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val target = if (isStart) {
                if (startDateTime == null) startDateTime = Calendar.getInstance()
                startDateTime!!
            } else {
                if (endDateTime == null) endDateTime = Calendar.getInstance()
                endDateTime!!
            }
            target.set(Calendar.YEAR, y)
            target.set(Calendar.MONTH, m)
            target.set(Calendar.DAY_OF_MONTH, d)
            updateDateButtons()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(isStart: Boolean) {
        val cal = if (isStart) startDateTime ?: Calendar.getInstance() else endDateTime ?: Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            val target = if (isStart) {
                if (startDateTime == null) startDateTime = Calendar.getInstance()
                startTimeSet = true
                startDateTime!!
            } else {
                if (endDateTime == null) endDateTime = Calendar.getInstance()
                endTimeSet = true
                endDateTime!!
            }
            target.set(Calendar.HOUR_OF_DAY, h)
            target.set(Calendar.MINUTE, m)
            target.set(Calendar.SECOND, 0)
            target.set(Calendar.MILLISECOND, 0)
            updateDateButtons()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun updateDateButtons() {
        startDateButton.text = startDateTime?.let { dateFormat.format(it.time) } ?: "Alkupvm"
        startTimeButton.text = if (startTimeSet) startDateTime?.let { timeFormat.format(it.time) } ?: "Klo" else "Klo"
        endDateButton.text = endDateTime?.let { dateFormat.format(it.time) } ?: "Loppupvm"
        endTimeButton.text = if (endTimeSet) endDateTime?.let { timeFormat.format(it.time) } ?: "Klo" else "Klo"
    }

    private fun generateTodaySummary() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        val title = "Kalansaaliit ${dateFormat.format(Date())}"
        fetchAndDisplaySummary(start, end, title)
    }

    private fun generateRangeSummary() {
        val start = startDateTime?.timeInMillis ?: 0L
        val end = endDateTime?.timeInMillis ?: Long.MAX_VALUE

        val sb = StringBuilder("Kalansaaliit aikavälillä ")
        if (startDateTime != null) {
            sb.append(dateFormat.format(startDateTime!!.time))
            if (startTimeSet) sb.append(" klo ").append(timeFormat.format(startDateTime!!.time))
        } else {
            sb.append("kaikki")
        }
        
        sb.append(" - ")
        
        if (endDateTime != null) {
            sb.append(dateFormat.format(endDateTime!!.time))
            if (endTimeSet) sb.append(" klo ").append(timeFormat.format(endDateTime!!.time))
        } else {
            sb.append("kaikki")
        }

        fetchAndDisplaySummary(start, end, sb.toString())
    }

    private fun fetchAndDisplaySummary(start: Long, end: Long, title: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val allCatches = db.fishCatchDao().getAll()
            val filteredCatches = allCatches.filter { 
                val caughtAt = it.caughtAt ?: 0L
                caughtAt in start..end && 
                it.species != "UNKNOWN" && 
                (it.eventType == null || it.eventType == FishCatch.CAUGHT_FISH)
            }
            val speciesList = db.fishSpeciesDao().getAll()
            val speciesMap = speciesList.associateBy { it.id }

            val result = formatSummary(title, filteredCatches, speciesMap)
            
            withContext(Dispatchers.Main) {
                summaryResultText.text = result
            }
        }
    }

    private fun formatSummary(title: String, catches: List<FishCatch>, speciesMap: Map<String, FishSpecies>): String {
        if (catches.isEmpty()) {
            return "$title\n\nEi saaliita tältä ajalta."
        }

        val sb = StringBuilder(title).append("\n\n")

        // Ryhmittele lajeittain
        val grouped = catches.groupBy { it.species }
        
        // Järjestä lajit: eniten kaloja ensin
        val sortedSpecies = grouped.entries.sortedByDescending { it.value.size }

        for (entry in sortedSpecies) {
            val speciesId = entry.key
            val speciesCatches = entry.value
            val speciesName = speciesMap[speciesId]?.name ?: speciesId
            
            sb.append(speciesName).append(" ").append(speciesCatches.size).append(" kpl")
            
            // Suodata kalat joilla on paino tai pituus
            val withData = speciesCatches.filter { it.weight != null || it.length != null }
            
            if (withData.isNotEmpty()) {
                // Järjestä: paino desc, sitten pituus desc
                val sortedCatches = withData.sortedWith(compareByDescending<FishCatch> { it.weight }.thenByDescending { it.length })
                
                sb.append(" (")
                sb.append(sortedCatches.joinToString(", ") { c ->
                    val weightStr = c.weight?.let { "${it}g" }
                    val lengthStr = c.length?.let { "${it}cm" }
                    if (weightStr != null && lengthStr != null) "$weightStr/$lengthStr"
                    else weightStr ?: lengthStr ?: ""
                })
                sb.append(")")
            }
            sb.append("\n")
        }

        return sb.toString()
    }
}
