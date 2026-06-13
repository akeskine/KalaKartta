package fi.anssi.kalakartta.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import fi.anssi.kalakartta.utils.WeatherService
import fi.anssi.kalakartta.utils.WeatherStation
import fi.anssi.kalakartta.utils.enlargeButtons
import java.text.SimpleDateFormat
import java.util.*

class EditCatchActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var fishCatch: FishCatch? = null
    private var speciesList: List<FishSpecies> = emptyList()
    
    private lateinit var speciesSpinner: Spinner
    private lateinit var dateTimeButton: Button
    private lateinit var weightEditText: EditText
    private lateinit var lengthEditText: EditText
    private lateinit var methodEditText: EditText
    private lateinit var strikeDepthEditText: EditText
    private lateinit var waterDepthEditText: EditText
    private lateinit var waterTempEditText: EditText
    private lateinit var airTempEditText: EditText
    private lateinit var cloudinessEditText: EditText
    private lateinit var rainSpinner: Spinner
    private lateinit var rainHourMmEditText: EditText
    private lateinit var windSpeedEditText: EditText
    private lateinit var windDirectionEditText: EditText
    private lateinit var additionalInfoEditText: EditText
    private lateinit var tripNotesEditText: EditText
    private lateinit var pressureEditText: EditText
    private lateinit var latEditText: EditText
    private lateinit var lonEditText: EditText
    
    private lateinit var autoWeatherCheckBox: CheckBox
    private lateinit var nearestStationText: TextView
    private lateinit var weatherService: WeatherService
    private var nearestStation: WeatherStation? = null
    
    private var currentWeatherSource: String = ""
    private var currentWeatherTime: Long = 0
    private var currentWeatherStation: String = ""
    private var currentPressure: Double = 0.0
    
    // Alkuperäiset säätiedot palautusta varten
    private var originalAirTemp: String = ""
    private var originalCloudiness: String = ""
    private var originalRain: Int = 0
    private var originalRainHourMm: String = ""
    private var originalWindSpeed: String = ""
    private var originalWindDirection: String = ""
    private var originalPressure: String = ""
    private var originalWeatherSource: String = ""
    private var originalWeatherTime: Long = 0
    private var originalWeatherStation: String = ""
    
    private var selectedCalendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))
    private var isChanged = false
    private var isUpdatingFromCode = false

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
        setContentView(R.layout.activity_edit_catch)

        initViews()
        setupDatabase()
        loadData()
        setupListeners()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    showUnsavedChangesDialog()
                } else {
                    finish()
                }
            }
        })
    }

    private fun initViews() {
        speciesSpinner = findViewById(R.id.speciesSpinner)
        dateTimeButton = findViewById(R.id.dateTimeButton)
        weightEditText = findViewById(R.id.weightEditText)
        lengthEditText = findViewById(R.id.lengthEditText)
        methodEditText = findViewById(R.id.methodEditText)
        strikeDepthEditText = findViewById(R.id.strikeDepthEditText)
        waterDepthEditText = findViewById(R.id.waterDepthEditText)
        waterTempEditText = findViewById(R.id.waterTempEditText)
        airTempEditText = findViewById(R.id.airTempEditText)
        cloudinessEditText = findViewById(R.id.cloudinessEditText)
        rainSpinner = findViewById(R.id.rainSpinner)
        rainHourMmEditText = findViewById(R.id.rainHourMmEditText)
        windSpeedEditText = findViewById(R.id.windSpeedEditText)
        windDirectionEditText = findViewById(R.id.windDirectionEditText)
        additionalInfoEditText = findViewById(R.id.additionalInfoEditText)
        tripNotesEditText = findViewById(R.id.tripNotesEditText)
        pressureEditText = findViewById(R.id.pressureEditText)
        latEditText = findViewById(R.id.latEditText)
        lonEditText = findViewById(R.id.lonEditText)
        
        autoWeatherCheckBox = findViewById(R.id.autoWeatherCheckBox)
        nearestStationText = findViewById(R.id.nearestStationText)
        nearestStationText.visibility = android.view.View.GONE
        weatherService = WeatherService(this)
    }

    private fun setupDatabase() {
        db = AppDatabase.getInstance(this)
    }

    private fun loadData() {
        val catchId = intent.getLongExtra("EXTRA_CATCH_ID", -1L)
        val allSpecies = db.fishSpeciesDao().getAll()
        
        // Lisätään tyhjä valinta listan alkuun
        val emptySpecies = FishSpecies(id = "", name = getString(R.string.empty_selection))
        speciesList = listOf(emptySpecies) + allSpecies

        if (catchId == -1L) {
            val lat = intent.getDoubleExtra("EXTRA_LATITUDE", 0.0)
            val lon = intent.getDoubleExtra("EXTRA_LONGITUDE", 0.0)
            
            fishCatch = FishCatch(
                species = "", // Oletusarvoksi tyhjä laji
                latitude = String.format(java.util.Locale.US, "%.5f", lat).toDouble(),
                longitude = String.format(java.util.Locale.US, "%.5f", lon).toDouble(),
                caughtAt = System.currentTimeMillis()
            )
            setTitle(R.string.add_detailed)
            
            setupWeatherForNewCatch(lat, lon)
        } else {
            fishCatch = db.fishCatchDao().getById(catchId)
            setTitle(R.string.edit_catch_title)
        }

        if (fishCatch == null) {
            Toast.makeText(this, getString(R.string.edit_error), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, speciesList.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        speciesSpinner.adapter = adapter

        val rainAdapter = ArrayAdapter.createFromResource(this, R.array.rain_levels, android.R.layout.simple_spinner_item)
        rainAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        rainSpinner.adapter = rainAdapter

        fishCatch?.let { fc ->
            isUpdatingFromCode = true
            val speciesIndex = speciesList.indexOfFirst { it.id == fc.species }
            speciesSpinner.setSelection(if (speciesIndex != -1) speciesIndex else 0)

            selectedCalendar.timeInMillis = fc.caughtAt
            updateDateTimeButtonText()

            val airTemp = if (fc.airTemp != null && !fc.airTemp!!.isNaN()) fc.airTemp.toString() else ""
            val cloudiness = fc.cloudiness?.toString() ?: ""
            val rain = if (fc.rain != null) fc.rain!!.toInt() + 1 else 0
            val rainHourMm = if (fc.rainHourMm != null && !fc.rainHourMm!!.isNaN()) fc.rainHourMm.toString() else ""
            
            val windSpeed = if (fc.windSpeed != null && !fc.windSpeed!!.isNaN()) fc.windSpeed.toString() else ""
            val windDirection = fc.windDirection?.toString() ?: ""
            val pressure = if (fc.pressure != null && !fc.pressure!!.isNaN()) fc.pressure.toString() else ""

            weightEditText.setText(if (fc.weight != null && fc.weight!! > 0) fc.weight.toString() else "")
            lengthEditText.setText(if (fc.length != null && fc.length!! > 0) fc.length.toString() else "")
            methodEditText.setText(fc.method)
            strikeDepthEditText.setText(fc.strikeDepth?.toString() ?: "")
            waterDepthEditText.setText(fc.waterDepth?.toString() ?: "")
            waterTempEditText.setText(fc.waterTemp?.toString() ?: "")
            airTempEditText.setText(airTemp)
            
            currentWeatherSource = fc.weatherSource
            currentWeatherTime = fc.weatherTime ?: 0L
            currentWeatherStation = fc.weatherStation
            currentPressure = fc.pressure ?: 0.0
            originalAirTemp = airTemp
            originalCloudiness = cloudiness
            originalRain = rain
            originalRainHourMm = rainHourMm
            originalWindSpeed = windSpeed
            originalWindDirection = windDirection
            originalPressure = pressure
            originalWeatherSource = fc.weatherSource
            originalWeatherTime = fc.weatherTime ?: 0L
            originalWeatherStation = fc.weatherStation
            
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val isWeatherEnabled = prefs.getBoolean("weather_enabled", true)
            
            if (isWeatherEnabled) {
                autoWeatherCheckBox.visibility = android.view.View.VISIBLE
                autoWeatherCheckBox.isChecked = (fc.weatherSource == "FMI")
                
                if (fc.weatherStation.isNotEmpty()) {
                    val parts = fc.weatherStation.split(":", limit = 2)
                    if (parts.size == 2) {
                        val fmisid = parts[0]
                        val name = parts[1]
                        nearestStation = WeatherStation(fmisid, name, 0.0, 0.0)
                        updateWeatherStationText(nearestStation!!, fc.weatherTime)
                        nearestStationText.visibility = android.view.View.VISIBLE
                    }
                } else {
                    // Jos sääasemaa ei ole, haetaan lähin
                    weatherService.fetchNearestStation(fc.latitude, fc.longitude, fc.caughtAt) { station, _ ->
                        runOnUiThread {
                            if (station != null) {
                                nearestStation = station
                                if (autoWeatherCheckBox.isChecked) {
                                    updateWeatherStationText(station, null)
                                    nearestStationText.visibility = android.view.View.VISIBLE
                                }
                            }
                        }
                    }
                }
            }

            cloudinessEditText.setText(cloudiness)
            rainSpinner.setSelection(rain)
            
            rainHourMmEditText.setText(rainHourMm)
            windSpeedEditText.setText(windSpeed)
            windDirectionEditText.setText(windDirection)
            pressureEditText.setText(pressure)
            
            // Jos painetta ei ole vielä asetettu (esim. vanha piste), mutta säätiedot on haettu,
            // yritetään täyttää se uudelleen FMI:ltä
            if (fc.pressure == 0.0 && fc.weatherSource == "FMI" && nearestStation != null) {
                fetchWeatherForDisplay()
            }
            additionalInfoEditText.setText(fc.additionalInfo)
            tripNotesEditText.setText(fc.tripNotes)
            latEditText.setText(fc.latitude.toString())
            lonEditText.setText(fc.longitude.toString())
            isUpdatingFromCode = false
        }
    }

    private fun updateDateTimeButtonText() {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Europe/Helsinki")
        dateTimeButton.text = sdf.format(selectedCalendar.time)
    }

    private fun setupListeners() {
        dateTimeButton.setOnClickListener {
            showDateTimePicker()
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveChanges()
        }

        findViewById<Button>(R.id.cancelButton).setOnClickListener {
            if (hasUnsavedChanges()) {
                showUnsavedChangesDialog()
            } else {
                finish()
            }
        }
        
        val weatherWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isUpdatingFromCode) {
                    currentWeatherSource = "MANUAL"
                    currentWeatherStation = ""
                    currentWeatherTime = selectedCalendar.timeInMillis
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        
        airTempEditText.addTextChangedListener(weatherWatcher)
        cloudinessEditText.addTextChangedListener(weatherWatcher)
        
        rainSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (!isUpdatingFromCode) {
                    isChanged = true
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        rainHourMmEditText.addTextChangedListener(weatherWatcher)
        windSpeedEditText.addTextChangedListener(weatherWatcher)
        windDirectionEditText.addTextChangedListener(weatherWatcher)
        pressureEditText.addTextChangedListener(weatherWatcher)
        
        autoWeatherCheckBox.setOnCheckedChangeListener { _, isChecked ->
            nearestStationText.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            if (isChecked) {
                if (nearestStation != null) {
                    fetchWeatherForDisplay()
                } else {
                    // Jos sääasemaa ei ole vielä löydetty, yritetään hakea se
                    val lat = latEditText.text.toString().toDoubleSafe(fishCatch?.latitude ?: 0.0)
                    val lon = lonEditText.text.toString().toDoubleSafe(fishCatch?.longitude ?: 0.0)
                    weatherService.fetchNearestStation(lat, lon, selectedCalendar.timeInMillis) { station, _ ->
                        runOnUiThread {
                            if (station != null) {
                                nearestStation = station
                                fetchWeatherForDisplay()
                            }
                        }
                    }
                }
            } else {
                // Palautetaan alkuperäiset arvot
                isUpdatingFromCode = true
                airTempEditText.setText(originalAirTemp)
                cloudinessEditText.setText(originalCloudiness)
                rainSpinner.setSelection(originalRain)
                rainHourMmEditText.setText(originalRainHourMm)
                windSpeedEditText.setText(originalWindSpeed)
                windDirectionEditText.setText(originalWindDirection)
                pressureEditText.setText(originalPressure)
                currentWeatherSource = originalWeatherSource
                currentWeatherTime = originalWeatherTime
                currentWeatherStation = originalWeatherStation
                
                if (nearestStation != null) {
                    if (originalWeatherStation.isNotEmpty()) {
                        updateWeatherStationText(nearestStation!!, originalWeatherTime)
                    } else {
                        nearestStationText.text = "Sääasema: ${nearestStation?.name}"
                    }
                }
                isUpdatingFromCode = false
            }
            isChanged = true
        }
    }

    private fun setupWeatherForNewCatch(lat: Double, lon: Double) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isWeatherEnabled = prefs.getBoolean("weather_enabled", true)
        
        if (isWeatherEnabled) {
            autoWeatherCheckBox.visibility = android.view.View.VISIBLE
            autoWeatherCheckBox.isChecked = true
            nearestStationText.visibility = android.view.View.VISIBLE
            
            weatherService.fetchNearestStations(lat, lon, selectedCalendar.timeInMillis, 1) { stations, error ->
                runOnUiThread {
                    val station = stations?.firstOrNull()
                    if (station != null) {
                        nearestStation = station
                        nearestStationText.text = "Sääasema: ${station.name}"
                        // Haetaan säätiedot automaattisesti jos mahdollista
                        if (autoWeatherCheckBox.isChecked) {
                            fetchWeatherForDisplay()
                        }
                    } else {
                        nearestStationText.text = "Sääasemaa ei löytynyt: $error"
                    }
                }
            }
        }
    }

    private fun fetchWeatherForDisplay() {
        val lat = latEditText.text.toString().toDoubleSafe(fishCatch?.latitude ?: 0.0)
        val lon = lonEditText.text.toString().toDoubleSafe(fishCatch?.longitude ?: 0.0)
        
        nearestStationText.text = "Haetaan säätietoja..."
        
        weatherService.fetchWeatherFromMultipleStations(lat, lon, selectedCalendar.timeInMillis) { data, time, error, stations ->
            runOnUiThread {
                if (data != null) {
                    nearestStationText.visibility = android.view.View.GONE
                    applyWeatherData(data, time, null)
                    // Päivitetään weatherStation kenttä suoraan tai pidetään muistissa
                    // applyWeatherData asettaa weatherSource = "FMI"
                    currentWeatherStation = stations
                } else {
                    nearestStationText.text = "Säätietojen haku epäonnistui: $error"
                }
            }
        }
    }

    private fun updateWeatherStationText(station: WeatherStation, time: Long?) {
        nearestStationText.visibility = android.view.View.GONE
    }

    private fun showDateTimePicker() {
        val context = this
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            selectedCalendar.set(Calendar.YEAR, year)
            selectedCalendar.set(Calendar.MONTH, month)
            selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
                selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedCalendar.set(Calendar.MINUTE, minute)
                updateDateTimeButtonText()
                isChanged = true
                
                // Päivitetään sääasema ja tiedot jos automaattinen haku on päällä
                if (autoWeatherCheckBox.isChecked) {
                    val lat = latEditText.text.toString().toDoubleSafe(fishCatch?.latitude ?: 0.0)
                    val lon = lonEditText.text.toString().toDoubleSafe(fishCatch?.longitude ?: 0.0)
                    setupWeatherForNewCatch(lat, lon)
                }
            }

            TimePickerDialog(
                context,
                timeSetListener,
                selectedCalendar.get(Calendar.HOUR_OF_DAY),
                selectedCalendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        DatePickerDialog(
            context,
            dateSetListener,
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveChanges() {
        if (autoWeatherCheckBox.visibility == android.view.View.VISIBLE && autoWeatherCheckBox.isChecked) {
            val progressDialog = AlertDialog.Builder(this)
                .setMessage("Haetaan säätietoja...")
                .setCancelable(false)
                .show()
            
            val lat = latEditText.text.toString().toDoubleSafe(fishCatch?.latitude ?: 0.0)
            val lon = lonEditText.text.toString().toDoubleSafe(fishCatch?.longitude ?: 0.0)

            weatherService.fetchWeatherFromMultipleStations(lat, lon, selectedCalendar.timeInMillis) { data, time, error, stations ->
                runOnUiThread {
                    progressDialog.dismiss()
                    if (data != null) {
                        applyWeatherData(data, time, null)
                        currentWeatherStation = stations
                        currentWeatherSource = "FMI"
                        Toast.makeText(this, "Säätiedot päivitetty", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Säätietojen haku epäonnistui: $error", Toast.LENGTH_SHORT).show()
                    }
                    performFinalSave()
                }
            }
        } else {
            performFinalSave()
        }
    }

    private fun applyWeatherData(data: Map<String, Double>, time: Long?, station: WeatherStation?) {
        isUpdatingFromCode = true
        // FMI parametrit: t2m (temp), ws_10min (wind speed), wd_10min (wind direction), 
        // n_man (cloudiness), r_1h (rain), p_msl tai p_sea (pressure)
        data["t2m"]?.let { airTempEditText.setText(it.toString()) }
        data["ws_10min"]?.let { windSpeedEditText.setText(it.toString()) }
        data["wd_10min"]?.let { windDirectionEditText.setText(it.toInt().toString()) }
        data["nn_ll01"]?.let { cloudinessEditText.setText(it.toInt().toString()) } ?: data["n_man"]?.let { cloudinessEditText.setText(it.toInt().toString()) }
        data["r_1h"]?.let { rainHourMmEditText.setText(it.toString()) }
        
        val pressureValue = data["p_msl"] ?: data["p_sea"]
        pressureValue?.let { 
            currentPressure = it
            pressureEditText.setText(it.toString())
        }
        
        currentWeatherSource = "FMI"
        currentWeatherTime = time ?: selectedCalendar.timeInMillis
        if (station != null) {
            currentWeatherStation = "${station.fmisid}:${station.name}"
        }
        isUpdatingFromCode = false
    }

    private fun performFinalSave() {
        try {
            val fc = fishCatch ?: return
            
            val selectedSpecies = speciesList[speciesSpinner.selectedItemPosition]
            
            val updatedCatch = fc.copy(
                species = selectedSpecies.id,
                caughtAt = selectedCalendar.timeInMillis,
                weight = weightEditText.text.toString().toLongOrNull(),
                length = lengthEditText.text.toString().toLongOrNull(),
                method = methodEditText.text.toString(),
                strikeDepth = strikeDepthEditText.text.toString().toDoubleOrNull(),
                waterDepth = waterDepthEditText.text.toString().toDoubleOrNull(),
                waterTemp = waterTempEditText.text.toString().toDoubleOrNull(),
                airTemp = airTempEditText.text.toString().toDoubleOrNull(),
                cloudiness = cloudinessEditText.text.toString().toLongOrNull(),
                rain = if (rainSpinner.selectedItemPosition > 0) (rainSpinner.selectedItemPosition - 1).toLong() else null,
                rainHourMm = rainHourMmEditText.text.toString().toDoubleOrNull(),
                windSpeed = windSpeedEditText.text.toString().toDoubleOrNull(),
                windDirection = windDirectionEditText.text.toString().toLongOrNull(),
                pressure = pressureEditText.text.toString().toDoubleOrNull(),
                weatherSource = if (currentWeatherSource == "FMI") "FMI" else "MANUAL",
                weatherTime = if (currentWeatherSource == "FMI") currentWeatherTime else selectedCalendar.timeInMillis,
                weatherStation = if (currentWeatherSource == "FMI") currentWeatherStation else "",
                additionalInfo = additionalInfoEditText.text.toString(),
                tripNotes = tripNotesEditText.text.toString(),
                latitude = String.format(java.util.Locale.US, "%.5f", latEditText.text.toString().toDoubleOrNull() ?: fc.latitude).toDouble(),
                longitude = String.format(java.util.Locale.US, "%.5f", lonEditText.text.toString().toDoubleOrNull() ?: fc.longitude).toDouble()
            )

            Thread {
                if (updatedCatch.id == 0L) {
                    db.fishCatchDao().insert(updatedCatch)
                } else {
                    db.fishCatchDao().update(updatedCatch)
                }
                
                runOnUiThread {
                    val message = if (updatedCatch.id == 0L) getString(R.string.save_success) else getString(R.string.edit_success)
                    
                    AlertDialog.Builder(this)
                        .setMessage(message)
                        .setPositiveButton(getString(R.string.ok)) { _, _ ->
                            val resultIntent = android.content.Intent()
                            resultIntent.putExtra("EXTRA_CATCH_ID", updatedCatch.id)
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }
                        .show()
                        .enlargeButtons()
                }
            }.start()

        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setMessage("${getString(R.string.edit_error)}: ${e.message}")
                .setPositiveButton(getString(R.string.ok), null)
                .show()
                .enlargeButtons()
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        if (isChanged) return true
        val fc = fishCatch ?: return false
        
        val selectedSpeciesId = speciesList.getOrNull(speciesSpinner.selectedItemPosition)?.id
        if (selectedSpeciesId != fc.species) return true
        if (selectedCalendar.timeInMillis != fc.caughtAt) return true
        if (weightEditText.text.toString() != (fc.weight?.toString() ?: "")) return true
        if (lengthEditText.text.toString() != (fc.length?.toString() ?: "")) return true
        if (methodEditText.text.toString() != fc.method) return true
        if (strikeDepthEditText.text.toString() != (fc.strikeDepth?.toString() ?: "")) return true
        if (waterDepthEditText.text.toString() != (fc.waterDepth?.toString() ?: "")) return true
        if (waterTempEditText.text.toString() != (fc.waterTemp?.toString() ?: "")) return true
        if (airTempEditText.text.toString() != (fc.airTemp?.toString() ?: "")) return true
        if (cloudinessEditText.text.toString() != (fc.cloudiness?.toString() ?: "")) return true
        val currentRainPos = rainSpinner.selectedItemPosition
        val fcRainPos = if (fc.rain != null) (fc.rain!!.toInt() + 1) else 0
        if (currentRainPos != fcRainPos) return true
        if (rainHourMmEditText.text.toString() != (fc.rainHourMm?.toString() ?: "")) return true
        if (windSpeedEditText.text.toString() != (fc.windSpeed?.toString() ?: "")) return true
        if (windDirectionEditText.text.toString() != (fc.windDirection?.toString() ?: "")) return true
        if (pressureEditText.text.toString() != (fc.pressure?.toString() ?: "")) return true
        if (currentWeatherSource != fc.weatherSource) return true
        if (currentWeatherTime != (fc.weatherTime ?: 0L)) return true
        if (currentWeatherStation != fc.weatherStation) return true
        if (additionalInfoEditText.text.toString() != fc.additionalInfo) return true
        if (tripNotesEditText.text.toString() != fc.tripNotes) return true
        if (latEditText.text.toString() != fc.latitude.toString()) return true
        if (lonEditText.text.toString() != fc.longitude.toString()) return true
        
        return false
    }

    private fun showUnsavedChangesDialog() {
        val dialog = AlertDialog.Builder(this)
            .setMessage(getString(R.string.unsaved_changes_warning))
            .setPositiveButton(getString(R.string.discard)) { _, _ ->
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
        dialog.enlargeButtons()
    }

    private fun String.toDoubleSafe(default: Double = 0.0): Double {
        val d = this.toDoubleOrNull()
        return if (d == null || d.isNaN()) default else d
    }
}
