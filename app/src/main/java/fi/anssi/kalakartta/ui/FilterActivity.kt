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
import android.graphics.BitmapFactory
import java.io.File
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
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var clearStartDateButton: ImageButton
    private lateinit var clearEndDateButton: ImageButton
    private lateinit var clearStartTimeButton: ImageButton
    private lateinit var clearEndTimeButton: ImageButton
    private lateinit var annualStartButton: Button
    private lateinit var annualEndButton: Button
    private lateinit var annualStartTimeButton: Button
    private lateinit var annualEndTimeButton: Button
    private lateinit var clearAnnualStartDateButton: ImageButton
    private lateinit var clearAnnualEndDateButton: ImageButton
    private lateinit var clearAnnualStartTimeButton: ImageButton
    private lateinit var clearAnnualEndTimeButton: ImageButton
    private lateinit var speciesSpinner: Spinner
    private lateinit var placeTypeSpinner: Spinner
    private lateinit var fishermanSpinner: Spinner
    private lateinit var freeTextEdit: EditText
    private lateinit var otherSpeciesSpinner: Spinner
    private var otherSpeciesList: List<String> = emptyList()
    private lateinit var otherSpeciesContainer: View
    private lateinit var windMinEdit: EditText
    private lateinit var windMaxEdit: EditText
    private lateinit var windDirectionPreview: WindDirectionView
    private lateinit var pressureMinEdit: EditText
    private lateinit var pressureMaxEdit: EditText
    private lateinit var waterTempMinEdit: EditText
    private lateinit var waterTempMaxEdit: EditText
    private lateinit var onlyCaughtFishCheckBox: CheckBox
    private lateinit var onlyFishPointsCheckBox: CheckBox
    private lateinit var onlyNonFishPointsCheckBox: CheckBox
    private lateinit var clearFiltersButton: Button

    private val dateOnlyFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val timeOnlyFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
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
        startTimeButton = findViewById(R.id.startTimeButton)
        endTimeButton = findViewById(R.id.endTimeButton)
        clearStartDateButton = findViewById(R.id.clearStartDateButton)
        clearEndDateButton = findViewById(R.id.clearEndDateButton)
        clearStartTimeButton = findViewById(R.id.clearStartTimeButton)
        clearEndTimeButton = findViewById(R.id.clearEndTimeButton)
        annualStartButton = findViewById(R.id.annualStartButton)
        annualEndButton = findViewById(R.id.annualEndButton)
        annualStartTimeButton = findViewById(R.id.annualStartTimeButton)
        annualEndTimeButton = findViewById(R.id.annualEndTimeButton)
        clearAnnualStartDateButton = findViewById(R.id.clearAnnualStartDateButton)
        clearAnnualEndDateButton = findViewById(R.id.clearAnnualEndDateButton)
        clearAnnualStartTimeButton = findViewById(R.id.clearAnnualStartTimeButton)
        clearAnnualEndTimeButton = findViewById(R.id.clearAnnualEndTimeButton)
        speciesSpinner = findViewById(R.id.speciesSpinner)
        placeTypeSpinner = findViewById(R.id.placeTypeSpinner)
        fishermanSpinner = findViewById(R.id.fishermanSpinner)
        freeTextEdit = findViewById(R.id.freeTextEdit)
        otherSpeciesSpinner = findViewById(R.id.otherSpeciesSpinner)

        val uniqueOther = db.fishCatchDao().getUniqueOtherSpecies()
        otherSpeciesList = listOf(getString(R.string.empty_selection)) + uniqueOther.map { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
        val otherAdapter = ArrayAdapter(this, R.layout.spinner_item, otherSpeciesList)
        otherAdapter.setDropDownViewResource(R.layout.spinner_item)
        otherSpeciesSpinner.adapter = otherAdapter
        otherSpeciesContainer = findViewById(R.id.otherSpeciesContainer)

        val allSpecies = db.fishSpeciesDao().getAll()
        val emptySpecies = FishSpecies("", getString(R.string.empty_selection), icon_default = "")
        speciesList = listOf(emptySpecies) + allSpecies
        
        val adapter = object : ArrayAdapter<FishSpecies>(this, R.layout.item_species_dialog, speciesList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_species_dialog, parent, false)
                val iconView = view.findViewById<ImageView>(R.id.speciesIcon)
                val nameView = view.findViewById<TextView>(R.id.speciesName)
                val item = getItem(position)
                nameView.text = if (item?.id?.isNotEmpty() == true) {
                    item.name.lowercase().replaceFirstChar { it.uppercase() }
                } else {
                    item?.name
                }
                val iconId = getDrawableId(item?.icon_default ?: "")
                if (iconId != 0) {
                    iconView.setImageResource(iconId)
                    iconView.visibility = View.VISIBLE
                } else if (item?.icon_default != null && item.icon_default.isNotEmpty()) {
                    val file = if (item.icon_default.startsWith("/")) File(item.icon_default) else File(filesDir, item.icon_default)
                    if (file.exists()) {
                        iconView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                        iconView.visibility = View.VISIBLE
                    } else {
                        iconView.visibility = View.GONE
                    }
                } else {
                    iconView.visibility = View.GONE
                }
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
        fishermanList = listOf(getString(R.string.empty_selection)) + allFishermen.map { 
            it.lowercase().replaceFirstChar { char -> char.uppercase() } 
        }
        val fishermanAdapter = ArrayAdapter(this, R.layout.spinner_item, fishermanList)
        fishermanAdapter.setDropDownViewResource(R.layout.spinner_item)
        fishermanSpinner.adapter = fishermanAdapter

        windMinEdit = findViewById(R.id.windMinEdit)
        windMaxEdit = findViewById(R.id.windMaxEdit)
        windDirectionPreview = findViewById(R.id.windDirectionPreview)
        pressureMinEdit = findViewById(R.id.pressureMinEdit)
        pressureMaxEdit = findViewById(R.id.pressureMaxEdit)
        waterTempMinEdit = findViewById(R.id.waterTempMinEdit)
        waterTempMaxEdit = findViewById(R.id.waterTempMaxEdit)
        onlyCaughtFishCheckBox = findViewById(R.id.onlyCaughtFishCheckBox)
        onlyFishPointsCheckBox = findViewById(R.id.onlyFishPointsCheckBox)
        onlyNonFishPointsCheckBox = findViewById(R.id.onlyNonFishPointsCheckBox)
        clearFiltersButton = findViewById(R.id.clearFiltersButton)
    }

    private fun loadFilters() {
        currentFilters = filterManager.getFilters()
        
        // Erotetaan kellonajat pvm:stä jos ne on asetettu
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))
        val startTs = currentFilters.startDate
        val startTime = if (startTs != null) {
            cal.timeInMillis = startTs
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val m = cal.get(Calendar.MINUTE)
            if (h == 0 && m == 0) null else h * 60 + m
        } else null

        val endTs = currentFilters.endDate
        val endTime = if (endTs != null) {
            cal.timeInMillis = endTs
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val m = cal.get(Calendar.MINUTE)
            if (h == 23 && m == 59) null else h * 60 + m
        } else null

        // Jos filters-oliossa ei ollut erillisiä minuutteja, mutta pvm:ssä oli, käytetään niitä
        currentFilters = currentFilters.copy(
            startTimeMinutes = currentFilters.startTimeMinutes ?: startTime,
            endTimeMinutes = currentFilters.endTimeMinutes ?: endTime,
            annualStartTimeMinutes = currentFilters.annualStartTimeMinutes,
            annualEndTimeMinutes = currentFilters.annualEndTimeMinutes
        )

        updateButtons()
        
        val selectedIndex = speciesList.indexOfFirst { it.id == currentFilters.speciesId }
        if (selectedIndex >= 0) {
            speciesSpinner.setSelection(selectedIndex)
        }
        
        val otherSpeciesDisplay = currentFilters.otherSpecies?.lowercase()?.replaceFirstChar { it.uppercase() } ?: getString(R.string.empty_selection)
        val otherIndex = otherSpeciesList.indexOf(otherSpeciesDisplay)
        if (otherIndex >= 0) {
            otherSpeciesSpinner.setSelection(otherIndex)
        }
        otherSpeciesContainer.visibility = if (currentFilters.speciesId == "OTHER") View.VISIBLE else View.GONE

        val placeIndex = placeTypeList.indexOfFirst { it.id == currentFilters.placeTypeId }
        if (placeIndex >= 0) {
            placeTypeSpinner.setSelection(placeIndex)
        }

        freeTextEdit.setText(currentFilters.freeText ?: "")
        
        val fishermanDisplay = currentFilters.fisherman?.lowercase()?.replaceFirstChar { it.uppercase() } ?: getString(R.string.empty_selection)
        val fishermanIndex = fishermanList.indexOf(fishermanDisplay)
        if (fishermanIndex >= 0) {
            fishermanSpinner.setSelection(fishermanIndex)
        }

        windMinEdit.setText(currentFilters.windMin?.toString() ?: "")
        windMaxEdit.setText(currentFilters.windMax?.toString() ?: "")
        updateWindPreview()

        pressureMinEdit.setText(currentFilters.pressureMin?.toString() ?: "")
        pressureMaxEdit.setText(currentFilters.pressureMax?.toString() ?: "")
        waterTempMinEdit.setText(currentFilters.waterTempMin?.toString() ?: "")
        waterTempMaxEdit.setText(currentFilters.waterTempMax?.toString() ?: "")
        onlyCaughtFishCheckBox.isChecked = currentFilters.onlyCaughtFish
        onlyFishPointsCheckBox.isChecked = currentFilters.onlyFishPoints
        onlyNonFishPointsCheckBox.isChecked = currentFilters.onlyNonFishPoints
    }

    private fun updateWindPreview() {
        val min = windMinEdit.text.toString().toFloatOrNull()
        val max = windMaxEdit.text.toString().toFloatOrNull()
        windDirectionPreview.setRange(min, max)
    }

    private fun hasAnyFilters(): Boolean {
        val f = currentFilters
        val windMin = windMinEdit.text.toString().toFloatOrNull()
        val windMax = windMaxEdit.text.toString().toFloatOrNull()
        val pressureMin = pressureMinEdit.text.toString().toFloatOrNull()
        val pressureMax = pressureMaxEdit.text.toString().toFloatOrNull()
        val waterTempMin = waterTempMinEdit.text.toString().toFloatOrNull()
        val waterTempMax = waterTempMaxEdit.text.toString().toFloatOrNull()
        val freeText = freeTextEdit.text.toString().let { if (it.isEmpty()) null else it }
        
        val speciesSelected = speciesSpinner.selectedItemPosition > 0
        val placeTypeSelected = placeTypeSpinner.selectedItemPosition > 0
        val fishermanSelected = fishermanSpinner.selectedItemPosition > 0
        
        // Varmistetaan, että speciesId on synkassa spinnerin kanssa, koska loadFilters asettaa sen,
        // mutta hasAnyFilters saattaa tulla kutsutuksi spinnerin listenerissä.
        val speciesId = if (speciesSelected) speciesList[speciesSpinner.selectedItemPosition].id else null
        val otherSpeciesSelected = if (speciesId == "OTHER") otherSpeciesSpinner.selectedItemPosition > 0 else false

        return f.startDate != null || f.endDate != null ||
                f.annualStartDay != null || f.annualStartMonth != null ||
                f.annualEndDay != null || f.annualEndMonth != null ||
                f.startTimeMinutes != null || f.endTimeMinutes != null ||
                f.annualStartTimeMinutes != null || f.annualEndTimeMinutes != null ||
                windMin != null || windMax != null ||
                pressureMin != null || pressureMax != null ||
                waterTempMin != null || waterTempMax != null ||
                speciesSelected || otherSpeciesSelected || placeTypeSelected || 
                freeText != null || fishermanSelected || 
                onlyCaughtFishCheckBox.isChecked || onlyFishPointsCheckBox.isChecked || 
                onlyNonFishPointsCheckBox.isChecked
    }

    private fun updateButtons() {
        clearFiltersButton.visibility = if (hasAnyFilters()) View.VISIBLE else View.GONE
        startDateButton.text = currentFilters.startDate?.let { dateOnlyFormat.format(Date(it)) } ?: getString(R.string.start_date)
        clearStartDateButton.visibility = if (currentFilters.startDate != null) View.VISIBLE else View.GONE
        
        endDateButton.text = currentFilters.endDate?.let { dateOnlyFormat.format(Date(it)) } ?: getString(R.string.end_date)
        clearEndDateButton.visibility = if (currentFilters.endDate != null) View.VISIBLE else View.GONE
        
        // Vuosittaiset alku- ja loppupäivät
        annualStartButton.text = if (currentFilters.annualStartDay != null && currentFilters.annualStartMonth != null) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.MONTH, currentFilters.annualStartMonth!!)
            cal.set(Calendar.DAY_OF_MONTH, currentFilters.annualStartDay!!)
            annualFormat.format(cal.time)
        } else getString(R.string.start_date)
        clearAnnualStartDateButton.visibility = if (currentFilters.annualStartDay != null) View.VISIBLE else View.GONE

        annualEndButton.text = if (currentFilters.annualEndDay != null && currentFilters.annualEndMonth != null) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.MONTH, currentFilters.annualEndMonth!!)
            cal.set(Calendar.DAY_OF_MONTH, currentFilters.annualEndDay!!)
            annualFormat.format(cal.time)
        } else getString(R.string.end_date)
        clearAnnualEndDateButton.visibility = if (currentFilters.annualEndDay != null) View.VISIBLE else View.GONE

        // Päivämäärävälin kellonajat
        startTimeButton.text = currentFilters.startTimeMinutes?.let { 
            val h = it / 60
            val m = it % 60
            String.format(Locale.getDefault(), "%02d:%02d", h, m)
        } ?: getString(R.string.start_time)
        clearStartTimeButton.visibility = if (currentFilters.startTimeMinutes != null) View.VISIBLE else View.GONE

        endTimeButton.text = currentFilters.endTimeMinutes?.let { 
            val h = it / 60
            val m = it % 60
            String.format(Locale.getDefault(), "%02d:%02d", h, m)
        } ?: getString(R.string.end_time)
        clearEndTimeButton.visibility = if (currentFilters.endTimeMinutes != null) View.VISIBLE else View.GONE

        // Vuosittaisen aikavälin kellonajat
        annualStartTimeButton.text = currentFilters.annualStartTimeMinutes?.let {
            val h = it / 60
            val m = it % 60
            String.format(Locale.getDefault(), "%02d:%02d", h, m)
        } ?: getString(R.string.start_time)
        clearAnnualStartTimeButton.visibility = if (currentFilters.annualStartTimeMinutes != null) View.VISIBLE else View.GONE

        annualEndTimeButton.text = currentFilters.annualEndTimeMinutes?.let {
            val h = it / 60
            val m = it % 60
            String.format(Locale.getDefault(), "%02d:%02d", h, m)
        } ?: getString(R.string.end_time)
        clearAnnualEndTimeButton.visibility = if (currentFilters.annualEndTimeMinutes != null) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        val updateButtonsWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateButtons()
            }
        }

        speciesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSpecies = speciesList[position]
                otherSpeciesContainer.visibility = if (selectedSpecies.id == "OTHER") View.VISIBLE else View.GONE
                updateButtons()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        placeTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateButtons()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        fishermanSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateButtons()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        otherSpeciesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateButtons()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        onlyCaughtFishCheckBox.setOnCheckedChangeListener { _, _ -> updateButtons() }
        onlyFishPointsCheckBox.setOnCheckedChangeListener { _, _ -> updateButtons() }
        onlyNonFishPointsCheckBox.setOnCheckedChangeListener { _, _ -> updateButtons() }

        freeTextEdit.addTextChangedListener(updateButtonsWatcher)
        pressureMinEdit.addTextChangedListener(updateButtonsWatcher)
        pressureMaxEdit.addTextChangedListener(updateButtonsWatcher)
        waterTempMinEdit.addTextChangedListener(updateButtonsWatcher)
        waterTempMaxEdit.addTextChangedListener(updateButtonsWatcher)

        startDateButton.setOnClickListener { showDatePicker(true) }
        endDateButton.setOnClickListener { showDatePicker(false) }
        
        clearStartDateButton.setOnClickListener {
            currentFilters = currentFilters.copy(startDate = null)
            updateButtons()
        }
        clearEndDateButton.setOnClickListener {
            currentFilters = currentFilters.copy(endDate = null)
            updateButtons()
        }
        clearStartTimeButton.setOnClickListener {
            currentFilters = currentFilters.copy(startTimeMinutes = null)
            updateButtons()
        }
        clearEndTimeButton.setOnClickListener {
            currentFilters = currentFilters.copy(endTimeMinutes = null)
            updateButtons()
        }
        
        clearAnnualStartDateButton.setOnClickListener {
            currentFilters = currentFilters.copy(annualStartDay = null, annualStartMonth = null)
            updateButtons()
        }
        clearAnnualEndDateButton.setOnClickListener {
            currentFilters = currentFilters.copy(annualEndDay = null, annualEndMonth = null)
            updateButtons()
        }
        clearAnnualStartTimeButton.setOnClickListener {
            currentFilters = currentFilters.copy(annualStartTimeMinutes = null)
            updateButtons()
        }
        clearAnnualEndTimeButton.setOnClickListener {
            currentFilters = currentFilters.copy(annualEndTimeMinutes = null)
            updateButtons()
        }
        
        annualStartButton.setOnClickListener { showAnnualDatePicker(true) }
        annualEndButton.setOnClickListener { showAnnualDatePicker(false) }

        startTimeButton.setOnClickListener { showTimePicker(true, isAnnual = false) }
        endTimeButton.setOnClickListener { showTimePicker(false, isAnnual = false) }
        annualStartTimeButton.setOnClickListener { showTimePicker(true, isAnnual = true) }
        annualEndTimeButton.setOnClickListener { showTimePicker(false, isAnnual = true) }

        findViewById<Button>(R.id.clearFiltersButton).setOnClickListener {
            currentFilters = FilterManager.Filters()
            updateButtons()
            speciesSpinner.setSelection(0)
            placeTypeSpinner.setSelection(0)
            fishermanSpinner.setSelection(0)
            otherSpeciesSpinner.setSelection(0)
            otherSpeciesContainer.visibility = View.GONE
            freeTextEdit.setText("")
            windMinEdit.setText("")
            windMaxEdit.setText("")
            updateWindPreview()
            pressureMinEdit.setText("")
            pressureMaxEdit.setText("")
            waterTempMinEdit.setText("")
            waterTempMaxEdit.setText("")
            onlyCaughtFishCheckBox.isChecked = false
            onlyFishPointsCheckBox.isChecked = false
            onlyNonFishPointsCheckBox.isChecked = false
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
                updateButtons()
            }
        }
        windMinEdit.addTextChangedListener(windWatcher)
        windMaxEdit.addTextChangedListener(windWatcher)
    }

    private fun showDatePicker(isStart: Boolean) {
        val current = if (isStart) currentFilters.startDate else currentFilters.endDate
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))
        if (current != null) cal.timeInMillis = current

        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            // Jos kellonaikaa ei ole asetettu, asetetaan oletus
            if (isStart && currentFilters.startTimeMinutes == null) {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
            } else if (!isStart && currentFilters.endTimeMinutes == null) {
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
            }
            
            currentFilters = if (isStart) currentFilters.copy(startDate = cal.timeInMillis)
            else currentFilters.copy(endDate = cal.timeInMillis)
            updateButtons()
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

    private fun showTimePicker(isStart: Boolean, isAnnual: Boolean) {
        val current = if (isAnnual) {
            if (isStart) currentFilters.annualStartTimeMinutes else currentFilters.annualEndTimeMinutes
        } else {
            if (isStart) currentFilters.startTimeMinutes else currentFilters.endTimeMinutes
        }
        val h = if (current != null) current / 60 else 12
        val m = if (current != null) current % 60 else 0

        TimePickerDialog(this, { _, hour, minute ->
            val totalMinutes = hour * 60 + minute
            currentFilters = if (isAnnual) {
                if (isStart) currentFilters.copy(annualStartTimeMinutes = totalMinutes)
                else currentFilters.copy(annualEndTimeMinutes = totalMinutes)
            } else {
                if (isStart) currentFilters.copy(startTimeMinutes = totalMinutes)
                else currentFilters.copy(endTimeMinutes = totalMinutes)
            }
            updateButtons()
        }, h, m, true).show()
    }

    private fun saveAndFinish() {
        val selectedSpecies = speciesList[speciesSpinner.selectedItemPosition]
        val selectedPlaceType = placeTypeList[placeTypeSpinner.selectedItemPosition]
        val selectedFisherman = fishermanList[fishermanSpinner.selectedItemPosition]
        val freeText = freeTextEdit.text.toString().trim().let { if (it.isEmpty()) null else it }
        val fisherman = if (selectedFisherman == getString(R.string.empty_selection)) null else selectedFisherman.uppercase()
        val selectedOther = otherSpeciesList[otherSpeciesSpinner.selectedItemPosition]
        val otherSpecies = if (selectedSpecies.id == "OTHER" && selectedOther != getString(R.string.empty_selection)) selectedOther.uppercase() else null
        val windMin = windMinEdit.text.toString().toFloatOrNull()
        val windMax = windMaxEdit.text.toString().toFloatOrNull()
        val pressureMin = pressureMinEdit.text.toString().toFloatOrNull()
        val pressureMax = pressureMaxEdit.text.toString().toFloatOrNull()
        val waterTempMin = waterTempMinEdit.text.toString().toFloatOrNull()
        val waterTempMax = waterTempMaxEdit.text.toString().toFloatOrNull()
        
        // Päivitetään startDate ja endDate kellonaikojen perusteella ennen tallennusta
        var startTs = currentFilters.startDate
        if (startTs != null) {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))
            cal.timeInMillis = startTs
            if (currentFilters.startTimeMinutes != null) {
                cal.set(Calendar.HOUR_OF_DAY, currentFilters.startTimeMinutes!! / 60)
                cal.set(Calendar.MINUTE, currentFilters.startTimeMinutes!! % 60)
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
            }
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            startTs = cal.timeInMillis
        }

        var endTs = currentFilters.endDate
        if (endTs != null) {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))
            cal.timeInMillis = endTs
            if (currentFilters.endTimeMinutes != null) {
                cal.set(Calendar.HOUR_OF_DAY, currentFilters.endTimeMinutes!! / 60)
                cal.set(Calendar.MINUTE, currentFilters.endTimeMinutes!! % 60)
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
            }
            cal.set(Calendar.MILLISECOND, 999)
            endTs = cal.timeInMillis
        }

        currentFilters = currentFilters.copy(
            startDate = startTs,
            endDate = endTs,
            speciesId = if (selectedSpecies.id.isEmpty()) null else selectedSpecies.id,
            otherSpecies = otherSpecies,
            placeTypeId = if (selectedPlaceType.id.isEmpty()) null else selectedPlaceType.id,
            freeText = freeText,
            fisherman = fisherman,
            annualStartTimeMinutes = currentFilters.annualStartTimeMinutes,
            annualEndTimeMinutes = currentFilters.annualEndTimeMinutes,
            windMin = windMin,
            windMax = windMax,
            pressureMin = pressureMin,
            pressureMax = pressureMax,
            waterTempMin = waterTempMin,
            waterTempMax = waterTempMax,
            onlyCaughtFish = onlyCaughtFishCheckBox.isChecked,
            onlyFishPoints = onlyFishPointsCheckBox.isChecked,
            onlyNonFishPoints = onlyNonFishPointsCheckBox.isChecked
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
