package fi.anssi.kalakartta.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import fi.anssi.kalakartta.MainActivity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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

    private lateinit var startDateButton: View
    private lateinit var startTimeButton: View
    private lateinit var endDateButton: View
    private lateinit var endTimeButton: View
    private lateinit var summaryResultText: TextView
    private lateinit var copyToClipboardButton: ImageButton
    private lateinit var showOnMapButton: ImageButton
    private lateinit var fishermanSpinner: Spinner
    private lateinit var filterManager: FilterManager
    private var fishermanList: List<String> = emptyList()

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

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
        setContentView(R.layout.activity_summary)

        db = AppDatabase.getInstance(this)

        startDateButton = findViewById(R.id.startDateButton)
        startTimeButton = findViewById(R.id.startTimeButton)
        endDateButton = findViewById(R.id.endDateButton)
        endTimeButton = findViewById(R.id.endTimeButton)
        summaryResultText = findViewById(R.id.summaryResultText)
        copyToClipboardButton = findViewById(R.id.copyToClipboardButton)
        showOnMapButton = findViewById(R.id.showOnMapButton)
        fishermanSpinner = findViewById<Spinner>(R.id.fishermanSpinner)

        filterManager = FilterManager(this)

        copyToClipboardButton.setOnClickListener {
            val summary = summaryResultText.text.toString()
            if (summary.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Kalakartta yhteenveto", summary)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Yhteenveto kopioitu leikepöydälle", Toast.LENGTH_SHORT).show()
            }
        }

        showOnMapButton.setOnClickListener {
            applyFiltersAndShowMap()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val fishermenWithCounts = db.fishCatchDao().getFishermenWithCounts()
            
            // Format name: "KALLE KALASTAJA" -> "Kalle Kalastaja"
            fun formatName(name: String): String {
                return name.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { part ->
                    part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }

            val sortedFishermen = fishermenWithCounts
                .sortedWith(compareByDescending<fi.anssi.kalakartta.data.FishCatchDao.FishermanCount> { it.count }
                    .thenBy { it.fisherman.lowercase() })
                .map { formatName(it.fisherman) }

            fishermanList = listOf(getString(R.string.empty_selection)) + sortedFishermen
            withContext(Dispatchers.Main) {
                val adapter = android.widget.ArrayAdapter(this@SummaryActivity, R.layout.spinner_item, fishermanList)
                adapter.setDropDownViewResource(R.layout.spinner_item)
                fishermanSpinner.adapter = adapter
            }
        }

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

        findViewById<View>(R.id.backButton).setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra("BACK_TO_SETTINGS", true))
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
        (startDateButton as? TextView)?.text = startDateTime?.let { dateFormat.format(it.time) } ?: "Alkupvm"
        (startTimeButton as? TextView)?.text = if (startTimeSet) startDateTime?.let { timeFormat.format(it.time) } ?: "Klo" else "Klo"
        (endDateButton as? TextView)?.text = endDateTime?.let { dateFormat.format(it.time) } ?: "Loppupvm"
        (endTimeButton as? TextView)?.text = if (endTimeSet) endDateTime?.let { timeFormat.format(it.time) } ?: "Klo" else "Klo"
    }

    private fun generateTodaySummary() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        startDateTime = cal.clone() as Calendar
        startTimeSet = false
        val start = cal.timeInMillis
        
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        endDateTime = cal.clone() as Calendar
        endTimeSet = false
        val end = cal.timeInMillis

        updateDateButtons()
        val title = if (startTimeSet || endTimeSet) {
            "Kalansaaliit ${dateFormat.format(Date())}"
        } else {
            "Kalansaaliit ${dateFormat.format(Date())}" // Periaatteessa sama, mutta selkeyden vuoksi
        }
        fetchAndDisplaySummary(start, end, title)
    }

    private fun generateRangeSummary() {
        val start: Long
        val end: Long

        if (startDateTime != null) {
            val cal = startDateTime!!.clone() as Calendar
            if (!startTimeSet) {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            start = cal.timeInMillis
        } else {
            start = 0L
        }

        if (endDateTime != null) {
            val cal = endDateTime!!.clone() as Calendar
            if (!endTimeSet) {
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
            }
            end = cal.timeInMillis
        } else {
            end = Long.MAX_VALUE
        }

        val sb = StringBuilder("Kalansaaliit aikavälillä ")
        if (startDateTime != null) {
            val startDateStr = dateFormat.format(startDateTime!!.time)
            val endDateStr = endDateTime?.let { dateFormat.format(it.time) }
            
            if (startDateStr == endDateStr) {
                sb.append(startDateStr)
                if (startTimeSet && endTimeSet) {
                    val startTimeStr = timeFormat.format(startDateTime!!.time)
                    val endTimeStr = timeFormat.format(endDateTime!!.time)
                    if (startTimeStr == endTimeStr) {
                        sb.append(" klo ").append(startTimeStr)
                    } else {
                        sb.append(" klo ").append(startTimeStr).append("-").append(endTimeStr)
                    }
                } else if (startTimeSet) {
                    sb.append(" alkaen klo ").append(timeFormat.format(startDateTime!!.time))
                } else if (endTimeSet) {
                    sb.append(" loppuen klo ").append(timeFormat.format(endDateTime!!.time))
                }
            } else {
                sb.append(startDateStr)
                if (startTimeSet) sb.append(" klo ").append(timeFormat.format(startDateTime!!.time))
                
                sb.append(" - ")
                
                if (endDateTime != null) {
                    sb.append(dateFormat.format(endDateTime!!.time))
                    if (endTimeSet) sb.append(" klo ").append(timeFormat.format(endDateTime!!.time))
                } else {
                    sb.append("kaikki")
                }
            }
        } else {
            sb.append("kaikki")
            sb.append(" - ")
            if (endDateTime != null) {
                sb.append(dateFormat.format(endDateTime!!.time))
                if (endTimeSet) sb.append(" klo ").append(timeFormat.format(endDateTime!!.time))
            } else {
                sb.append("kaikki")
            }
        }
        sb.append(":")

        fetchAndDisplaySummary(start, end, sb.toString())
    }

    private fun fetchAndDisplaySummary(start: Long, end: Long, title: String) {
        val selectedFisherman = if (fishermanSpinner.selectedItemPosition > 0) {
            fishermanList[fishermanSpinner.selectedItemPosition]
        } else null

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val allCatches = db.fishCatchDao().getAll()
                val filteredCatches = allCatches.filter { 
                    val caughtAt = it.caughtAt ?: 0L
                    val matchesFisherman = selectedFisherman == null || it.fisherman.equals(selectedFisherman, ignoreCase = true)
                    
                    caughtAt in start..end && 
                    it.species != "UNKNOWN" && 
                    (it.eventType == null || it.eventType == FishCatch.CAUGHT_FISH) &&
                    matchesFisherman
                }
                val speciesList = db.fishSpeciesDao().getAll()
                val speciesMap = speciesList.associateBy { it.id }

                val fishermanTitlePart = if (selectedFisherman != null) {
                    " ($selectedFisherman)"
                } else ""
                val result = formatSummary(title + fishermanTitlePart, filteredCatches, speciesMap)
                
                withContext(Dispatchers.Main) {
                    summaryResultText.text = result
                    copyToClipboardButton.visibility = View.VISIBLE
                    showOnMapButton.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SummaryActivity, "Virhe yhteenvetoa luotaessa", Toast.LENGTH_LONG).show()
                }
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
        
        // Järjestä lajit käyttäjän määrittelemän järjestyksen mukaan
        val sortedSpecies = grouped.entries.sortedBy { entry ->
            speciesMap[entry.key]?.sortOrder ?: Int.MAX_VALUE
        }

        for ((index, entry) in sortedSpecies.withIndex()) {
            val speciesId = entry.key
            val speciesCatches = entry.value
            val speciesNameRaw = speciesMap[speciesId]?.name ?: speciesId
            val speciesName = speciesNameRaw.lowercase().replaceFirstChar { it.uppercase() }
            
            if (speciesId == "OTHER") {
                // Ryhmittele "Muu kalalaji" vielä tarkemman lajin mukaan
                val subGrouped = speciesCatches.groupBy { it.otherSpecies ?: "Tuntematon" }
                val sortedSubGroups = subGrouped.entries.sortedByDescending { it.value.size }
                
                for ((subIndex, subEntry) in sortedSubGroups.withIndex()) {
                    val otherSpeciesNameRaw = subEntry.key
                    val otherSpeciesName = if (otherSpeciesNameRaw != "Tuntematon") {
                        otherSpeciesNameRaw.lowercase().replaceFirstChar { it.uppercase() }
                    } else {
                        otherSpeciesNameRaw
                    }
                    val subCatches = subEntry.value
                    
                    sb.append(speciesName).append(" (").append(otherSpeciesName).append(") ").append(subCatches.size).append(" kpl")
                    appendCatchData(sb, subCatches)
                    
                    if (subIndex < sortedSubGroups.size - 1 || index < sortedSpecies.size - 1) {
                        sb.append("\n\n")
                    }
                }
            } else {
                sb.append(speciesName).append(" ").append(speciesCatches.size).append(" kpl")
                appendCatchData(sb, speciesCatches)
                
                if (index < sortedSpecies.size - 1) {
                    sb.append("\n\n")
                }
            }
        }

        return sb.toString()
    }

    private fun applyFiltersAndShowMap() {
        val start: Long? = if (startDateTime != null) {
            val cal = startDateTime!!.clone() as Calendar
            if (!startTimeSet) {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } else null

        val end: Long? = if (endDateTime != null) {
            val cal = endDateTime!!.clone() as Calendar
            if (!endTimeSet) {
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
            }
            cal.timeInMillis
        } else null

        val startMinutes: Int? = if (startTimeSet && startDateTime != null) {
            startDateTime!!.get(Calendar.HOUR_OF_DAY) * 60 + startDateTime!!.get(Calendar.MINUTE)
        } else null

        val endMinutes: Int? = if (endTimeSet && endDateTime != null) {
            endDateTime!!.get(Calendar.HOUR_OF_DAY) * 60 + endDateTime!!.get(Calendar.MINUTE)
        } else null

        val selectedFisherman = if (fishermanSpinner.selectedItemPosition > 0) {
            fishermanList[fishermanSpinner.selectedItemPosition]
        } else null

        val filters = filterManager.getFilters().copy(
            startDate = start,
            endDate = end,
            startTimeMinutes = startMinutes,
            endTimeMinutes = endMinutes,
            fisherman = selectedFisherman
        )
        filterManager.saveFilters(filters)

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun appendCatchData(sb: StringBuilder, catches: List<FishCatch>) {
        // Suodata kalat joilla on paino tai pituus (> 0)
        val withData = catches.filter { (it.weight != null && it.weight!! > 0) || (it.length != null && it.length!! > 0) }
        
        if (withData.isNotEmpty()) {
            // Järjestä: paino desc, sitten pituus desc
            val sortedCatches = withData.sortedWith(compareByDescending<FishCatch> { it.weight ?: 0L }.thenByDescending { it.length ?: 0L })
            
            sb.append(" (")
            sb.append(sortedCatches.joinToString(", ") { c ->
                val weightStr = if (c.weight != null && c.weight!! > 0) "${c.weight}g" else null
                val lengthStr = if (c.length != null && c.length!! > 0) "${c.length}cm" else null
                if (weightStr != null && lengthStr != null) "$weightStr/$lengthStr"
                else weightStr ?: lengthStr ?: ""
            })
            sb.append(")")
        }
    }
}
