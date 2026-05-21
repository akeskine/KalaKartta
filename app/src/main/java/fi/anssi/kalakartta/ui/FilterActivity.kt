package fi.anssi.kalakartta.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishSpecies
import java.text.SimpleDateFormat
import java.util.*

class FilterActivity : AppCompatActivity() {

    private lateinit var filterManager: FilterManager
    private lateinit var db: AppDatabase
    private var currentFilters: FilterManager.Filters = FilterManager.Filters()
    private var speciesList: List<FishSpecies> = emptyList()

    private lateinit var startDateButton: Button
    private lateinit var endDateButton: Button
    private lateinit var annualStartButton: Button
    private lateinit var annualEndButton: Button
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var speciesSpinner: Spinner

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val annualFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())

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
        setContentView(R.layout.activity_filter)

        filterManager = FilterManager(this)
        db = AppDatabase.getInstance(this)

        initViews()
        loadFilters()
        setupListeners()
    }

    private fun initViews() {
        startDateButton = findViewById(R.id.startDateButton)
        endDateButton = findViewById(R.id.endDateButton)
        annualStartButton = findViewById(R.id.annualStartButton)
        annualEndButton = findViewById(R.id.annualEndButton)
        startTimeButton = findViewById(R.id.startTimeButton)
        endTimeButton = findViewById(R.id.endTimeButton)
        speciesSpinner = findViewById(R.id.speciesSpinner)

        val allSpecies = db.fishSpeciesDao().getAll()
        val emptySpecies = FishSpecies("", getString(R.string.empty_selection), icon_default = "")
        speciesList = listOf(emptySpecies) + allSpecies
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, speciesList.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        speciesSpinner.adapter = adapter
    }

    private fun loadFilters() {
        currentFilters = filterManager.getFilters()
        updateButtons()
        
        val selectedIndex = speciesList.indexOfFirst { it.id == currentFilters.speciesId }
        if (selectedIndex >= 0) {
            speciesSpinner.setSelection(selectedIndex)
        }
    }

    private fun updateButtons() {
        startDateButton.text = currentFilters.startDate?.let { dateFormat.format(Date(it)) } ?: getString(R.string.start_date)
        endDateButton.text = currentFilters.endDate?.let { dateFormat.format(Date(it)) } ?: getString(R.string.end_date)
        
        annualStartButton.text = if (currentFilters.annualStartDay != null && currentFilters.annualStartMonth != null) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.MONTH, currentFilters.annualStartMonth!!)
            cal.set(Calendar.DAY_OF_MONTH, currentFilters.annualStartDay!!)
            annualFormat.format(cal.time)
        } else getString(R.string.start_date)

        annualEndButton.text = if (currentFilters.annualEndDay != null && currentFilters.annualEndMonth != null) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.MONTH, currentFilters.annualEndMonth!!)
            cal.set(Calendar.DAY_OF_MONTH, currentFilters.annualEndDay!!)
            annualFormat.format(cal.time)
        } else getString(R.string.end_date)

        startTimeButton.text = currentFilters.startTimeMinutes?.let { 
            val h = it / 60
            val m = it % 60
            String.format(Locale.getDefault(), "%02d:%02d", h, m)
        } ?: getString(R.string.start_time)

        endTimeButton.text = currentFilters.endTimeMinutes?.let { 
            val h = it / 60
            val m = it % 60
            String.format(Locale.getDefault(), "%02d:%02d", h, m)
        } ?: getString(R.string.end_time)
    }

    private fun setupListeners() {
        startDateButton.setOnClickListener { showFullDateTimePicker(true) }
        endDateButton.setOnClickListener { showFullDateTimePicker(false) }
        
        annualStartButton.setOnClickListener { showAnnualDatePicker(true) }
        annualEndButton.setOnClickListener { showAnnualDatePicker(false) }

        startTimeButton.setOnClickListener { showTimePicker(true) }
        endTimeButton.setOnClickListener { showTimePicker(false) }

        findViewById<Button>(R.id.clearFiltersButton).setOnClickListener {
            currentFilters = FilterManager.Filters()
            updateButtons()
            speciesSpinner.setSelection(0)
            Toast.makeText(this, R.string.filters_cleared, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.okButton).setOnClickListener {
            val selectedSpecies = speciesList[speciesSpinner.selectedItemPosition]
            currentFilters = currentFilters.copy(speciesId = if (selectedSpecies.id.isEmpty()) null else selectedSpecies.id)
            filterManager.saveFilters(currentFilters)
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun showFullDateTimePicker(isStart: Boolean) {
        val current = if (isStart) currentFilters.startDate else currentFilters.endDate
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))
        if (current != null) cal.timeInMillis = current

        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                
                currentFilters = if (isStart) currentFilters.copy(startDate = cal.timeInMillis)
                else currentFilters.copy(endDate = cal.timeInMillis)
                updateButtons()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showAnnualDatePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        val m = if (isStart) currentFilters.annualStartMonth ?: cal.get(Calendar.MONTH) else currentFilters.annualEndMonth ?: cal.get(Calendar.MONTH)
        val d = if (isStart) currentFilters.annualStartDay ?: cal.get(Calendar.DAY_OF_MONTH) else currentFilters.annualEndDay ?: cal.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, _, month, day ->
            currentFilters = if (isStart) currentFilters.copy(annualStartDay = day, annualStartMonth = month)
            else currentFilters.copy(annualEndDay = day, annualEndMonth = month)
            updateButtons()
        }, cal.get(Calendar.YEAR), m, d).show()
    }

    private fun showTimePicker(isStart: Boolean) {
        val current = if (isStart) currentFilters.startTimeMinutes else currentFilters.endTimeMinutes
        val h = if (current != null) current / 60 else 12
        val m = if (current != null) current % 60 else 0

        TimePickerDialog(this, { _, hour, minute ->
            val totalMinutes = hour * 60 + minute
            currentFilters = if (isStart) currentFilters.copy(startTimeMinutes = totalMinutes)
            else currentFilters.copy(endTimeMinutes = totalMinutes)
            updateButtons()
        }, h, m, true).show()
    }
}
