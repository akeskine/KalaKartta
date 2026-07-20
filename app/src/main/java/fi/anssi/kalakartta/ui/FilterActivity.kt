package fi.anssi.kalakartta.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishSpecies
import fi.anssi.kalakartta.data.PlaceOfInterestType
import java.text.SimpleDateFormat
import java.util.*

class FilterActivity : AppCompatActivity() {

    private lateinit var filterManager: FilterManager
    private lateinit var db: AppDatabase
    private var currentFilters: FilterManager.Filters = FilterManager.Filters()
    private var speciesList: List<FishSpecies> = emptyList()
    private var placeTypeList: List<PlaceOfInterestType> = emptyList()
    private var fishermanList: List<String> = emptyList()

    private lateinit var startDateButton: Button
    private lateinit var endDateButton: Button
    private lateinit var annualStartButton: Button
    private lateinit var annualEndButton: Button
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var speciesSpinner: Spinner
    private lateinit var placeTypeSpinner: Spinner
    private lateinit var fishermanSpinner: Spinner
    private lateinit var freeTextEdit: EditText
    private lateinit var windMinEdit: EditText
    private lateinit var windMaxEdit: EditText
    private lateinit var windDirectionPreview: WindDirectionView
    private lateinit var pressureMinEdit: EditText
    private lateinit var pressureMaxEdit: EditText
    private lateinit var onlyCaughtFishCheckBox: CheckBox

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

    private fun getDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return 0
        return resources.getIdentifier(iconName, "drawable", packageName)
    }

    private fun initViews() {
        startDateButton = findViewById(R.id.startDateButton)
        endDateButton = findViewById(R.id.endDateButton)
        annualStartButton = findViewById(R.id.annualStartButton)
        annualEndButton = findViewById(R.id.annualEndButton)
        startTimeButton = findViewById(R.id.startTimeButton)
        endTimeButton = findViewById(R.id.endTimeButton)
        speciesSpinner = findViewById(R.id.speciesSpinner)
        placeTypeSpinner = findViewById(R.id.placeTypeSpinner)
        fishermanSpinner = findViewById(R.id.fishermanSpinner)
        freeTextEdit = findViewById(R.id.freeTextEdit)

        val allSpecies = db.fishSpeciesDao().getAll()
        val emptySpecies = FishSpecies("", getString(R.string.empty_selection), icon_default = "")
        speciesList = listOf(emptySpecies) + allSpecies
        
        val adapter = object : ArrayAdapter<FishSpecies>(this, R.layout.item_species_dialog, speciesList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_species_dialog, parent, false)
                val iconView = view.findViewById<ImageView>(R.id.speciesIcon)
                val nameView = view.findViewById<TextView>(R.id.speciesName)
                val item = getItem(position)
                nameView.text = item?.name
                val iconId = getDrawableId(item?.icon_default ?: "")
                iconView.setImageResource(iconId)
                iconView.visibility = if (iconId != 0) View.VISIBLE else View.GONE
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return getView(position, convertView, parent)
            }
        }
        speciesSpinner.adapter = adapter

        val allPlaceTypes = db.placeOfInterestTypeDao().getAll().sortedBy { it.sortOrder }
        val emptyPlaceType = PlaceOfInterestType("", getString(R.string.empty_selection), icon = "")
        placeTypeList = listOf(emptyPlaceType) + allPlaceTypes

        val placeAdapter = object : ArrayAdapter<PlaceOfInterestType>(this, R.layout.item_species_dialog, placeTypeList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_species_dialog, parent, false)
                val iconView = view.findViewById<ImageView>(R.id.speciesIcon)
                val nameView = view.findViewById<TextView>(R.id.speciesName)
                val item = getItem(position)
                nameView.text = item?.name
                val iconId = getDrawableId(item?.icon ?: "")
                iconView.setImageResource(iconId)
                iconView.visibility = if (iconId != 0) View.VISIBLE else View.GONE
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return getView(position, convertView, parent)
            }
        }
        placeTypeSpinner.adapter = placeAdapter

        val allFishermen = db.fishCatchDao().getUniqueFishermen()
        fishermanList = listOf(getString(R.string.empty_selection)) + allFishermen
        val fishermanAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fishermanList)
        fishermanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fishermanSpinner.adapter = fishermanAdapter

        windMinEdit = findViewById(R.id.windMinEdit)
        windMaxEdit = findViewById(R.id.windMaxEdit)
        windDirectionPreview = findViewById(R.id.windDirectionPreview)
        pressureMinEdit = findViewById(R.id.pressureMinEdit)
        pressureMaxEdit = findViewById(R.id.pressureMaxEdit)
        onlyCaughtFishCheckBox = findViewById(R.id.onlyCaughtFishCheckBox)
    }

    private fun loadFilters() {
        currentFilters = filterManager.getFilters()
        updateButtons()
        
        val selectedIndex = speciesList.indexOfFirst { it.id == currentFilters.speciesId }
        if (selectedIndex >= 0) {
            speciesSpinner.setSelection(selectedIndex)
        }

        val placeIndex = placeTypeList.indexOfFirst { it.id == currentFilters.placeTypeId }
        if (placeIndex >= 0) {
            placeTypeSpinner.setSelection(placeIndex)
        }

        freeTextEdit.setText(currentFilters.freeText ?: "")
        
        val fishermanIndex = fishermanList.indexOf(currentFilters.fisherman ?: getString(R.string.empty_selection))
        if (fishermanIndex >= 0) {
            fishermanSpinner.setSelection(fishermanIndex)
        }

        windMinEdit.setText(currentFilters.windMin?.toString() ?: "")
        windMaxEdit.setText(currentFilters.windMax?.toString() ?: "")
        updateWindPreview()

        pressureMinEdit.setText(currentFilters.pressureMin?.toString() ?: "")
        pressureMaxEdit.setText(currentFilters.pressureMax?.toString() ?: "")
        onlyCaughtFishCheckBox.isChecked = currentFilters.onlyCaughtFish
    }

    private fun updateWindPreview() {
        val min = windMinEdit.text.toString().toFloatOrNull()
        val max = windMaxEdit.text.toString().toFloatOrNull()
        windDirectionPreview.setRange(min, max)
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
            placeTypeSpinner.setSelection(0)
            fishermanSpinner.setSelection(0)
            freeTextEdit.setText("")
            windMinEdit.setText("")
            windMaxEdit.setText("")
            updateWindPreview()
            pressureMinEdit.setText("")
            pressureMaxEdit.setText("")
            Toast.makeText(this, R.string.filters_cleared, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.okButton).setOnClickListener {
            saveAndFinish()
        }

        val windWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateWindPreview()
            }
        }
        windMinEdit.addTextChangedListener(windWatcher)
        windMaxEdit.addTextChangedListener(windWatcher)
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

    private fun saveAndFinish() {
        val selectedSpecies = speciesList[speciesSpinner.selectedItemPosition]
        val selectedPlaceType = placeTypeList[placeTypeSpinner.selectedItemPosition]
        val selectedFisherman = fishermanList[fishermanSpinner.selectedItemPosition]
        val freeText = freeTextEdit.text.toString().trim().let { if (it.isEmpty()) null else it }
        val fisherman = if (selectedFisherman == getString(R.string.empty_selection)) null else selectedFisherman
        val windMin = windMinEdit.text.toString().toFloatOrNull()
        val windMax = windMaxEdit.text.toString().toFloatOrNull()
        val pressureMin = pressureMinEdit.text.toString().toFloatOrNull()
        val pressureMax = pressureMaxEdit.text.toString().toFloatOrNull()
        
        currentFilters = currentFilters.copy(
            speciesId = if (selectedSpecies.id.isEmpty()) null else selectedSpecies.id,
            placeTypeId = if (selectedPlaceType.id.isEmpty()) null else selectedPlaceType.id,
            freeText = freeText,
            fisherman = fisherman,
            windMin = windMin,
            windMax = windMax,
            pressureMin = pressureMin,
            pressureMax = pressureMax,
            onlyCaughtFish = onlyCaughtFishCheckBox.isChecked
        )
        filterManager.saveFilters(currentFilters)
        setResult(RESULT_OK)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        saveAndFinish()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
