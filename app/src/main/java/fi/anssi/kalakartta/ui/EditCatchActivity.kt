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
    private lateinit var rainEditText: EditText
    private lateinit var windSpeedEditText: EditText
    private lateinit var windDirectionEditText: EditText
    private lateinit var additionalInfoEditText: EditText
    private lateinit var tripNotesEditText: EditText
    private lateinit var latEditText: EditText
    private lateinit var lonEditText: EditText
    
    private var selectedCalendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))
    private var isChanged = false

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
        rainEditText = findViewById(R.id.rainEditText)
        windSpeedEditText = findViewById(R.id.windSpeedEditText)
        windDirectionEditText = findViewById(R.id.windDirectionEditText)
        additionalInfoEditText = findViewById(R.id.additionalInfoEditText)
        tripNotesEditText = findViewById(R.id.tripNotesEditText)
        latEditText = findViewById(R.id.latEditText)
        lonEditText = findViewById(R.id.lonEditText)
    }

    private fun setupDatabase() {
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "kalakartta-db"
        ).allowMainThreadQueries().build()
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
                latitude = lat,
                longitude = lon,
                caughtAt = System.currentTimeMillis()
            )
            setTitle(R.string.add_detailed)
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

        fishCatch?.let { fc ->
            val speciesIndex = speciesList.indexOfFirst { it.id == fc.species }
            speciesSpinner.setSelection(if (speciesIndex != -1) speciesIndex else 0)

            selectedCalendar.timeInMillis = fc.caughtAt
            updateDateTimeButtonText()

            weightEditText.setText(if (fc.weight > 0) fc.weight.toString() else "")
            lengthEditText.setText(if (fc.length > 0) fc.length.toString() else "")
            methodEditText.setText(fc.method)
            strikeDepthEditText.setText(if (fc.strikeDepth != 0.0) fc.strikeDepth.toString() else "")
            waterDepthEditText.setText(if (fc.waterDepth != 0.0) fc.waterDepth.toString() else "")
            waterTempEditText.setText(if (fc.waterTemp != 0.0) fc.waterTemp.toString() else "")
            airTempEditText.setText(if (fc.airTemp != 0.0) fc.airTemp.toString() else "")
            cloudinessEditText.setText(fc.cloudiness.toString())
            rainEditText.setText(fc.rain.toString())
            windSpeedEditText.setText(if (fc.windSpeed != 0.0) fc.windSpeed.toString() else "")
            windDirectionEditText.setText(fc.windDirection.toString())
            additionalInfoEditText.setText(fc.additionalInfo)
            tripNotesEditText.setText(fc.tripNotes)
            latEditText.setText(fc.latitude.toString())
            lonEditText.setText(fc.longitude.toString())
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
        try {
            val fc = fishCatch ?: return
            
            val selectedSpecies = speciesList[speciesSpinner.selectedItemPosition]
            
            val updatedCatch = fc.copy(
                species = selectedSpecies.id,
                caughtAt = selectedCalendar.timeInMillis,
                weight = weightEditText.text.toString().toLongOrNull() ?: 0L,
                length = lengthEditText.text.toString().toLongOrNull() ?: 0L,
                method = methodEditText.text.toString(),
                strikeDepth = strikeDepthEditText.text.toString().toDoubleOrNull() ?: 0.0,
                waterDepth = waterDepthEditText.text.toString().toDoubleOrNull() ?: 0.0,
                waterTemp = waterTempEditText.text.toString().toDoubleOrNull() ?: 0.0,
                airTemp = airTempEditText.text.toString().toDoubleOrNull() ?: 0.0,
                cloudiness = cloudinessEditText.text.toString().toLongOrNull() ?: 0L,
                rain = rainEditText.text.toString().toLongOrNull() ?: 0L,
                windSpeed = windSpeedEditText.text.toString().toDoubleOrNull() ?: 0.0,
                windDirection = windDirectionEditText.text.toString().toLongOrNull() ?: 0L,
                additionalInfo = additionalInfoEditText.text.toString(),
                tripNotes = tripNotesEditText.text.toString(),
                latitude = latEditText.text.toString().toDoubleOrNull() ?: fc.latitude,
                longitude = lonEditText.text.toString().toDoubleOrNull() ?: fc.longitude
            )

            if (updatedCatch.id == 0L) {
                db.fishCatchDao().insert(updatedCatch)
            } else {
                db.fishCatchDao().update(updatedCatch)
            }
            
            val message = if (updatedCatch.id == 0L) getString(R.string.save_success) else getString(R.string.edit_success)
            
            AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    setResult(RESULT_OK)
                    finish()
                }
                .show()
                .enlargeButtons()

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
        if (weightEditText.text.toString() != (if (fc.weight > 0) fc.weight.toString() else "")) return true
        if (lengthEditText.text.toString() != (if (fc.length > 0) fc.length.toString() else "")) return true
        if (methodEditText.text.toString() != fc.method) return true
        if (strikeDepthEditText.text.toString() != (if (fc.strikeDepth != 0.0) fc.strikeDepth.toString() else "")) return true
        if (waterDepthEditText.text.toString() != (if (fc.waterDepth != 0.0) fc.waterDepth.toString() else "")) return true
        if (waterTempEditText.text.toString() != (if (fc.waterTemp != 0.0) fc.waterTemp.toString() else "")) return true
        if (airTempEditText.text.toString() != (if (fc.airTemp != 0.0) fc.airTemp.toString() else "")) return true
        if (cloudinessEditText.text.toString() != fc.cloudiness.toString()) return true
        if (rainEditText.text.toString() != fc.rain.toString()) return true
        if (windSpeedEditText.text.toString() != (if (fc.windSpeed != 0.0) fc.windSpeed.toString() else "")) return true
        if (windDirectionEditText.text.toString() != fc.windDirection.toString()) return true
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
}
