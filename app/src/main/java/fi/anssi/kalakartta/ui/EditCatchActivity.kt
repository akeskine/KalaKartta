package fi.anssi.kalakartta.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import fi.anssi.kalakartta.data.Media
import fi.anssi.kalakartta.data.MediaService
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.data.PlaceOfInterestType
import fi.anssi.kalakartta.utils.WeatherService
import fi.anssi.kalakartta.utils.WeatherStation
import fi.anssi.kalakartta.utils.enlargeButtons
import android.graphics.BitmapFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EditCatchActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var fishCatch: FishCatch? = null
    private var placeOfInterest: PlaceOfInterest? = null
    private var isPlace = false
    private var speciesList: List<FishSpecies> = emptyList()
    private var placeTypeList: List<PlaceOfInterestType> = emptyList()
    private var eventTypes: MutableList<String> = mutableListOf()

    private fun getDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return 0
        return resources.getIdentifier(iconName, "drawable", packageName)
    }
    
    private lateinit var speciesSpinner: Spinner
    private lateinit var eventTypeSpinner: Spinner
    private lateinit var eventTypeLabel: TextView
    private lateinit var speciesLabel: TextView
    private lateinit var fishSpecificFields: View
    private lateinit var fishWeatherLayout: View
    private lateinit var dateTimeButton: Button
    private lateinit var weightEditText: EditText
    private lateinit var lengthEditText: EditText
    private lateinit var methodEditText: EditText
    private lateinit var lureEditText: EditText
    private lateinit var lureColorEditText: EditText
    private lateinit var strikeDepthEditText: EditText
    private lateinit var waterDepthEditText: EditText
    private lateinit var waterTempEditText: EditText
    private lateinit var airTempEditText: EditText
    private lateinit var cloudinessEditText: EditText
    private lateinit var rainSpinner: Spinner
    private lateinit var rainHourMmEditText: EditText
    private lateinit var windSpeedEditText: EditText
    private lateinit var windDirectionEditText: EditText
    private lateinit var windDirectionArrow: ImageView
    private lateinit var additionalInfoEditText: EditText
    private lateinit var tripNotesEditText: EditText
    private lateinit var fishermanEditText: EditText
    private lateinit var otherSpeciesEditText: EditText
    private lateinit var otherSpeciesContainer: View
    private lateinit var pressureEditText: EditText
    private lateinit var latEditText: EditText
    private lateinit var lonEditText: EditText
    private lateinit var titleSpeciesIcon: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var clearTimeButton: ImageButton
    private lateinit var timeLabel: TextView
    private lateinit var dateTimeContainer: View
    
    private lateinit var placeNameEditText: EditText
    private lateinit var placeNameContainer: View
    
    private lateinit var mediaListLayout: LinearLayout
    private lateinit var addMediaButton: Button
    private lateinit var mediaService: MediaService
    
    private lateinit var autoWeatherCheckBox: CheckBox
    private lateinit var nearestStationText: TextView
    private lateinit var weatherService: WeatherService
    private var nearestStation: WeatherStation? = null
    
    private var currentWeatherSource: String = ""
    private var currentWeatherTime: Long = 0
    private var currentWeatherStation: String = ""
    private var currentPressure: Double = 0.0
    
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
    private var isUpdatingFromCode = false
    private var isTimeSetManually = false

    private val selectMediaLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val lat = latEditText.text.toString().toDoubleSafe()
            val lon = lonEditText.text.toString().toDoubleSafe()
            val time = if (isPlace) null else {
                if (isTimeSetManually || (fishCatch?.caughtAt ?: 0L) > 0L) selectedCalendar.timeInMillis else null
            }
            
            val media = mediaService.addMedia(it, lat, lon, time)
            if (media != null) {
                refreshMediaList()
            } else {
                Toast.makeText(this, "Median lisääminen epäonnistui", Toast.LENGTH_SHORT).show()
            }
        }
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
        eventTypeSpinner = findViewById(R.id.eventTypeSpinner)
        eventTypeLabel = findViewById(R.id.eventTypeLabel)
        speciesLabel = findViewById(R.id.speciesLabel)
        fishSpecificFields = findViewById(R.id.fishSpecificFields)
        fishWeatherLayout = findViewById(R.id.fishWeatherLayout)
        dateTimeButton = findViewById(R.id.dateTimeButton)
        weightEditText = findViewById(R.id.weightEditText)
        lengthEditText = findViewById(R.id.lengthEditText)
        methodEditText = findViewById(R.id.methodEditText)
        lureEditText = findViewById(R.id.lureEditText)
        lureColorEditText = findViewById(R.id.lureColorEditText)
        strikeDepthEditText = findViewById(R.id.strikeDepthEditText)
        waterDepthEditText = findViewById(R.id.waterDepthEditText)
        waterTempEditText = findViewById(R.id.waterTempEditText)
        airTempEditText = findViewById(R.id.airTempEditText)
        cloudinessEditText = findViewById(R.id.cloudinessEditText)
        rainSpinner = findViewById(R.id.rainSpinner)
        rainHourMmEditText = findViewById(R.id.rainHourMmEditText)
        windSpeedEditText = findViewById(R.id.windSpeedEditText)
        windDirectionEditText = findViewById(R.id.windDirectionEditText)
        windDirectionArrow = findViewById(R.id.windDirectionArrow)
        additionalInfoEditText = findViewById(R.id.additionalInfoEditText)
        tripNotesEditText = findViewById(R.id.tripNotesEditText)
        fishermanEditText = findViewById(R.id.fishermanEditText)
        otherSpeciesEditText = findViewById(R.id.otherSpeciesEditText)
        otherSpeciesContainer = findViewById(R.id.otherSpeciesContainer)
        pressureEditText = findViewById(R.id.pressureEditText)
        placeNameEditText = findViewById(R.id.placeNameEditText)
        placeNameContainer = findViewById(R.id.placeNameContainer)
        latEditText = findViewById(R.id.latEditText)
        lonEditText = findViewById(R.id.lonEditText)
        titleSpeciesIcon = findViewById(R.id.titleSpeciesIcon)
        titleTextView = findViewById(R.id.editCatchTitle)
        clearTimeButton = findViewById(R.id.clearTimeButton)
        timeLabel = findViewById(R.id.timeLabel)
        dateTimeContainer = findViewById(R.id.dateTimeContainer)
        
        mediaListLayout = findViewById(R.id.mediaListLayout)
        addMediaButton = findViewById(R.id.addMediaButton)
        mediaService = MediaService(this)
        
        autoWeatherCheckBox = findViewById(R.id.autoWeatherCheckBox)
        nearestStationText = findViewById(R.id.nearestStationText)
        nearestStationText.visibility = View.GONE
        weatherService = WeatherService(this)
    }

    private fun setupDatabase() {
        db = AppDatabase.getInstance(this)
    }

    private fun loadData() {
        isPlace = intent.getBooleanExtra("EXTRA_IS_PLACE", false)
        val catchId = intent.getLongExtra("EXTRA_CATCH_ID", -1L)
        val placeId = intent.getLongExtra("EXTRA_PLACE_ID", -1L)
        
        if (isPlace) {
            placeTypeList = db.placeOfInterestTypeDao().getAll()
                
            fishSpecificFields.visibility = View.GONE
            fishWeatherLayout.visibility = View.GONE
            eventTypeSpinner.visibility = View.GONE
            eventTypeLabel.visibility = View.GONE
            placeNameContainer.visibility = View.VISIBLE
            speciesLabel.text = "Tyyppi"
            
            if (placeId == -1L) {
                val lat = intent.getDoubleExtra("EXTRA_LATITUDE", 0.0)
                val lon = intent.getDoubleExtra("EXTRA_LONGITUDE", 0.0)
                
                placeOfInterest = PlaceOfInterest(
                    typeId = intent.getStringExtra("EXTRA_PLACE_TYPE") ?: "",
                    latitude = String.format(java.util.Locale.US, "%.5f", lat).toDouble(),
                    longitude = String.format(java.util.Locale.US, "%.5f", lon).toDouble(),
                    name = intent.getStringExtra("EXTRA_PLACE_NAME") ?: ""
                )
                titleTextView.setText(R.string.add_detailed_title)
            } else {
                placeOfInterest = db.placeOfInterestDao().getById(placeId)
                titleTextView.text = "Muokkaa paikkaa"
            }
            
            if (placeOfInterest == null) {
                Toast.makeText(this, getString(R.string.edit_error), Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            // Alustetaan kalenteri nykyhetkeen, vaikka sitä ei näytettäisi painikkeessa
            selectedCalendar.timeInMillis = System.currentTimeMillis()
        } else {
            val allSpecies = db.fishSpeciesDao().getAll()
            
            val catchId = intent.getLongExtra("EXTRA_CATCH_ID", -1L)
            fishCatch = if (catchId == -1L) {
                val lat = intent.getDoubleExtra("EXTRA_LATITUDE", 0.0)
                val lon = intent.getDoubleExtra("EXTRA_LONGITUDE", 0.0)
                
                val currentCaughtAt = if (intent.hasExtra("EXTRA_CAUGHT_AT")) {
                    val ca = intent.getLongExtra("EXTRA_CAUGHT_AT", -1L)
                    if (ca <= 0L) null else ca
                } else {
                    System.currentTimeMillis()
                }

                FishCatch(
                    species = "UNKNOWN",
                    latitude = String.format(java.util.Locale.US, "%.5f", lat).toDouble(),
                    longitude = String.format(java.util.Locale.US, "%.5f", lon).toDouble(),
                    caughtAt = currentCaughtAt
                )
            } else {
                db.fishCatchDao().getById(catchId)
            }

            if (fishCatch == null) {
                Toast.makeText(this, getString(R.string.edit_error), Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            // Alustetaan kalenteri nykyhetkeen, vaikka sitä ei näytettäisi painikkeessa
            selectedCalendar.timeInMillis = System.currentTimeMillis()

            speciesList = allSpecies

            if (catchId == -1L) {
                titleTextView.setText(R.string.add_detailed_title)
                setupWeatherForNewCatch(fishCatch!!.latitude, fishCatch!!.longitude)
            } else {
                titleTextView.setText(R.string.edit_catch_title)
            }
        }

        isUpdatingFromCode = true
        if (isPlace) {
            val adapter = object : ArrayAdapter<PlaceOfInterestType>(this, R.layout.item_species_dialog, placeTypeList) {
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
            speciesSpinner.adapter = adapter
            
            placeOfInterest?.let { poi ->
                val typeIndex = placeTypeList.indexOfFirst { it.id == poi.typeId }
                speciesSpinner.setSelection(if (typeIndex != -1) typeIndex else 0)
                
                placeNameEditText.setText(poi.name)
                additionalInfoEditText.setText(poi.additionalInfo)
                latEditText.setText(String.format(java.util.Locale.US, "%.5f", poi.latitude))
                lonEditText.setText(String.format(java.util.Locale.US, "%.5f", poi.longitude))
                
                updateDateTimeButtonText()
                timeLabel.visibility = View.GONE
                dateTimeContainer.visibility = View.GONE
            }
        } else {
            val adapter = object : ArrayAdapter<FishSpecies>(this, R.layout.item_species_dialog, speciesList) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_species_dialog, parent, false)
                    val iconView = view.findViewById<ImageView>(R.id.speciesIcon)
                    val nameView = view.findViewById<TextView>(R.id.speciesName)
                    val item = getItem(position)
                    nameView.text = if (item?.id == "UNKNOWN") item.name else item?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                    val iconId = getDrawableId(item?.icon_default ?: "")
                    if (iconId != 0 && (iconId != R.drawable.default_point || item?.id != "UNKNOWN")) {
                        iconView.setImageResource(iconId)
                        iconView.visibility = View.VISIBLE
                    } else if (item?.id == "UNKNOWN") {
                        iconView.setImageResource(R.drawable.default_point)
                        iconView.visibility = View.VISIBLE
                    } else if (item?.icon_default != null && item.icon_default.isNotEmpty()) {
                        val file = if (item.icon_default.startsWith("/")) File(item.icon_default) else File(filesDir, item.icon_default)
                        if (file.exists()) {
                            iconView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                            iconView.visibility = View.VISIBLE
                        } else {
                            iconView.setImageResource(R.drawable.muukala)
                            iconView.visibility = View.VISIBLE
                        }
                    } else {
                        iconView.visibility = View.GONE
                    }
                    
                    if (item?.icon_default == "seurio") {
                        iconView.scaleX = 1.3f
                        iconView.scaleY = 1.3f
                    } else {
                        iconView.scaleX = 1.0f
                        iconView.scaleY = 1.0f
                    }
                    return view
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return getView(position, convertView, parent)
                }
            }
            speciesSpinner.adapter = adapter

            val catchId = intent.getLongExtra("EXTRA_CATCH_ID", -1L)
            eventTypes = mutableListOf(
                FishCatch.CAUGHT_FISH,
                FishCatch.LOST_FISH,
                FishCatch.STRIKE_CERTAIN,
                FishCatch.STRIKE_UNCERTAIN,
                FishCatch.FISH_FOLLOW
            )
            // Lisätään null/tyhjä vaihtoehto vain vanhoille pisteille
            if (catchId != -1L && fishCatch?.eventType == null) {
                eventTypes.add(0, "EMPTY")
            }

            val eventTypeAdapter = object : ArrayAdapter<String>(this, R.layout.item_species_dialog, eventTypes) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return createView(position, convertView, parent)
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return createView(position, convertView, parent)
                }

                private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_species_dialog, parent, false)
                    val iconView = view.findViewById<ImageView>(R.id.speciesIcon)
                    val nameView = view.findViewById<TextView>(R.id.speciesName)

                    val type = eventTypes[position]
                    if (type == "EMPTY") {
                        nameView.text = getString(R.string.empty_selection)
                        iconView.visibility = View.INVISIBLE
                    } else {
                        nameView.text = FishCatch.getEventTypeName(type)
                        
                        val iconName = when (type) {
                            FishCatch.LOST_FISH -> "karkuutus"
                            FishCatch.STRIKE_CERTAIN -> "tarppi_varma"
                            FishCatch.STRIKE_UNCERTAIN -> "tarppi_epavarma"
                            FishCatch.FISH_FOLLOW -> "seurio"
                            else -> ""
                        }
                        
                        if (iconName.isNotEmpty()) {
                            iconView.setImageResource(getDrawableId(iconName))
                            iconView.visibility = View.VISIBLE
                            if (iconName == "seurio") {
                                iconView.scaleX = 1.3f
                                iconView.scaleY = 1.3f
                            } else {
                                iconView.scaleX = 1.0f
                                iconView.scaleY = 1.0f
                            }
                        } else {
                            iconView.visibility = View.INVISIBLE
                        }
                    }
                    
                    return view
                }
            }
            eventTypeSpinner.adapter = eventTypeAdapter
            
            val rainAdapter = ArrayAdapter.createFromResource(this, R.array.rain_levels, R.layout.spinner_item)
            rainAdapter.setDropDownViewResource(R.layout.spinner_item)
            rainSpinner.adapter = rainAdapter

            fishCatch?.let { fc ->
                var speciesIndex = speciesList.indexOfFirst { it.id == fc.species }
                
                // Jos lajia ei löydy (esim. poistettu itse lisätty laji), vaihdetaan se "Muu kalalaji" -tyyppiin
                if (speciesIndex == -1 && fc.species != "UNKNOWN" && fc.species.isNotEmpty()) {
                    speciesIndex = speciesList.indexOfFirst { it.id == "OTHER" }
                    // Jos otherSpecies on tyhjä, käytetään tuntematonta lajitunnistetta
                    if (fc.otherSpecies.isNullOrBlank()) {
                        val newCatch = fc.copy(species = "OTHER", otherSpecies = fc.species)
                        fishCatch = newCatch
                    } else {
                        val newCatch = fc.copy(species = "OTHER")
                        fishCatch = newCatch
                    }
                }
                
                speciesSpinner.setSelection(if (speciesIndex != -1) speciesIndex else 0)

                val eventTypeIndex = if (fc.eventType == null && eventTypes.contains("EMPTY")) {
                    eventTypes.indexOf("EMPTY")
                } else {
                    eventTypes.indexOf(fc.eventType ?: FishCatch.CAUGHT_FISH)
                }
                eventTypeSpinner.setSelection(if (eventTypeIndex != -1) eventTypeIndex else 0)

                fc.caughtAt?.let { selectedCalendar.timeInMillis = it }
                updateDateTimeButtonText()
                
                autoWeatherCheckBox.visibility = if (fc.caughtAt == null || fc.caughtAt!! <= 0L) View.GONE else View.VISIBLE

                val airTemp = if (fc.airTemp != null && !fc.airTemp!!.isNaN()) fc.airTemp.toString() else ""
                val cloudiness = fc.cloudiness?.toString() ?: ""
                val rain = if (fc.rain != null) fc.rain!!.toInt() + 1 else 0
                val rainHourMm = if (fc.rainHourMm != null && !fc.rainHourMm!!.isNaN()) fc.rainHourMm.toString() else ""
                val windSpeed = if (fc.windSpeed != null && !fc.windSpeed!!.isNaN()) fc.windSpeed.toString() else ""
                val windDirection = fc.windDirection?.toString() ?: ""
                updateWindArrow(windDirection)
                val pressure = if (fc.pressure != null && !fc.pressure!!.isNaN()) fc.pressure.toString() else ""

                if (!fc.otherSpecies.isNullOrBlank()) {
                    otherSpeciesEditText.setText(fc.otherSpecies.lowercase().replaceFirstChar { it.uppercase() })
                }

                weightEditText.setText(if (fc.weight != null && fc.weight!! > 0) fc.weight.toString() else "")
                lengthEditText.setText(if (fc.length != null && fc.length!! > 0) fc.length.toString() else "")
                methodEditText.setText(fc.method ?: "")
                lureEditText.setText(fc.lure ?: "")
                lureColorEditText.setText(fc.lureColor ?: "")
                strikeDepthEditText.setText(if (fc.strikeDepth != null && fc.strikeDepth!! != 0.0) fc.strikeDepth.toString() else "")
                waterDepthEditText.setText(if (fc.waterDepth != null && fc.waterDepth!! != 0.0) fc.waterDepth.toString() else "")
                waterTempEditText.setText(if (fc.waterTemp != null && fc.waterTemp!! != 0.0) fc.waterTemp.toString() else "")
                airTempEditText.setText(airTemp)
                
                currentWeatherSource = fc.weatherSource ?: ""
                currentWeatherTime = fc.weatherTime ?: 0L
                currentWeatherStation = fc.weatherStation ?: ""
                currentPressure = fc.pressure ?: 0.0
                originalAirTemp = airTemp
                originalCloudiness = cloudiness
                originalRain = rain
                originalRainHourMm = rainHourMm
                originalWindSpeed = windSpeed
                originalWindDirection = windDirection
                originalPressure = pressure
                originalWeatherSource = fc.weatherSource ?: ""
                originalWeatherTime = fc.weatherTime ?: 0L
                originalWeatherStation = fc.weatherStation ?: ""
                
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                val isWeatherEnabled = prefs.getBoolean("weather_enabled", true)
                
                if (isWeatherEnabled && fc.caughtAt != null && fc.caughtAt!! > 0L) {
                    autoWeatherCheckBox.visibility = View.VISIBLE
                    autoWeatherCheckBox.isChecked = false
                    
                    if (!fc.weatherStation.isNullOrEmpty()) {
                        val parts = fc.weatherStation!!.split(":", limit = 2)
                        if (parts.size == 2) {
                            val name = parts[1]
                            nearestStation = WeatherStation(parts[0], name, 0.0, 0.0)
                            nearestStationText.text = "Sääasema: $name"
                            nearestStationText.visibility = View.VISIBLE
                        }
                    } else {
                        weatherService.fetchNearestStation(fc.latitude, fc.longitude, fc.caughtAt!!) { station, _ ->
                            runOnUiThread {
                                if (station != null) {
                                    isUpdatingFromCode = true
                                    nearestStation = station
                                    isUpdatingFromCode = false
                                }
                            }
                        }
                    }
                } else if (isWeatherEnabled && (fc.caughtAt == null || fc.caughtAt!! <= 0L)) {
                    autoWeatherCheckBox.visibility = View.GONE
                }

                cloudinessEditText.setText(cloudiness)
                rainSpinner.setSelection(rain)
                rainHourMmEditText.setText(rainHourMm)
                windSpeedEditText.setText(windSpeed)
                windDirectionEditText.setText(windDirection)
                pressureEditText.setText(pressure)
                
                if (fc.pressure == 0.0 && fc.weatherSource == "FMI" && nearestStation != null) {
                    fetchWeatherForDisplay(onlyMissing = true)
                }
                additionalInfoEditText.setText(fc.additionalInfo ?: "")
                tripNotesEditText.setText(fc.tripNotes ?: "")
                fun formatName(name: String): String {
                    return name.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { part ->
                        part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                }

                fishermanEditText.setText(if (!fc.fisherman.isNullOrEmpty()) formatName(fc.fisherman) else "")
                val updatedFc = fishCatch ?: fc
                val otherSpeciesDisplay = updatedFc.otherSpecies?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
                otherSpeciesEditText.setText(otherSpeciesDisplay)
                otherSpeciesContainer.visibility = if (updatedFc.species == "OTHER") View.VISIBLE else View.GONE
                latEditText.setText(String.format(java.util.Locale.US, "%.5f", updatedFc.latitude))
                lonEditText.setText(String.format(java.util.Locale.US, "%.5f", updatedFc.longitude))
            }
        }
        isUpdatingFromCode = false
        refreshMediaList()
    }

    private fun refreshMediaList() {
        mediaListLayout.removeAllViews()
        val lat = fishCatch?.latitude ?: placeOfInterest?.latitude ?: return
        val lon = fishCatch?.longitude ?: placeOfInterest?.longitude ?: return
        val time = if (isPlace) null else fishCatch?.caughtAt
        
        val mediaList = mediaService.getMediaForPoint(lat, lon, time)
        mediaList.forEach { media ->
            val mediaView = LayoutInflater.from(this).inflate(R.layout.item_media, mediaListLayout, false)
            val fileNameText = mediaView.findViewById<TextView>(R.id.mediaFileName)
            val removeButton = mediaView.findViewById<ImageButton>(R.id.removeMediaButton)
            val thumbnail = mediaView.findViewById<ImageView>(R.id.mediaThumbnail)
            
            fileNameText.text = media.originalFileName
            
            if (media.mimeType.startsWith("image/")) {
                val file = File(filesDir, "media/${media.fileName}")
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    thumbnail.setImageBitmap(bitmap)
                    thumbnail.visibility = View.VISIBLE
                }
            } else {
                thumbnail.visibility = View.GONE
            }
            
            fileNameText.setOnClickListener {
                openMedia(media)
            }
            thumbnail.setOnClickListener {
                openMedia(media)
            }
            
            removeButton.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Poista media")
                    .setMessage("Haluatko varmasti poistaa tämän median?")
                    .setPositiveButton("Poista") { _, _ ->
                        mediaService.deleteMedia(media)
                        refreshMediaList()
                    }
                    .setNegativeButton("Peruuta", null)
                    .show()
            }
            
            mediaListLayout.addView(mediaView)
        }
    }

    private fun openMedia(media: fi.anssi.kalakartta.data.Media) {
        val file = File(filesDir, "media/${media.fileName}")
        if (!file.exists()) return
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, media.mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun updateWindArrow(directionStr: String?) {
        val direction = directionStr?.toFloatOrNull()
        if (direction != null) {
            windDirectionArrow.rotation = (direction + 180) % 360
            windDirectionArrow.visibility = View.VISIBLE
        } else {
            windDirectionArrow.visibility = View.INVISIBLE
        }
    }

    private fun updateDateTimeButtonText() {
        val hasTime = (isTimeSetManually || (fishCatch?.caughtAt ?: 0L) > 0L)
        
        if (!hasTime) {
            dateTimeButton.text = getString(R.string.set_time)
            clearTimeButton.visibility = View.GONE
            return
        }
        
        clearTimeButton.visibility = View.VISIBLE
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Europe/Helsinki")
        dateTimeButton.text = sdf.format(selectedCalendar.time)
    }

    private fun setupListeners() {
        dateTimeButton.setOnClickListener { showDateTimePicker() }
        clearTimeButton.setOnClickListener {
            isTimeSetManually = false
            fishCatch = fishCatch?.copy(caughtAt = null)
            updateDateTimeButtonText()
            autoWeatherCheckBox.visibility = View.GONE
        }
        speciesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isPlace) {
                    val selectedSpecies = speciesList.getOrNull(position) ?: return
                    otherSpeciesContainer.visibility = if (selectedSpecies.id == "OTHER") View.VISIBLE else View.GONE
                    
                    val iconPath = selectedSpecies.icon_default
                    if (iconPath.isNotEmpty()) {
                        val resId = getDrawableId(iconPath)
                        if (resId != 0) {
                            titleSpeciesIcon.setImageResource(resId)
                        } else {
                            val file = File(iconPath)
                            if (file.exists()) {
                                titleSpeciesIcon.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                            } else {
                                titleSpeciesIcon.setImageDrawable(null)
                            }
                        }
                    }
                    titleSpeciesIcon.visibility = View.GONE
                } else {
                    val selectedType = placeTypeList.getOrNull(position) ?: return
                    val iconName = selectedType.icon
                    val resId = getDrawableId(iconName)
                    if (resId != 0) {
                        titleSpeciesIcon.setImageResource(resId)
                    }
                    titleSpeciesIcon.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveChanges() }
        findViewById<Button>(R.id.cancelButton).setOnClickListener {
            if (hasUnsavedChanges()) showUnsavedChangesDialog() else finish()
        }
        addMediaButton.setOnClickListener {
            selectMediaLauncher.launch("*/*")
        }
        windDirectionEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateWindArrow(s?.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        autoWeatherCheckBox.setOnCheckedChangeListener { _, isChecked ->
            nearestStationText.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                if (nearestStation != null) fetchWeatherForDisplay(onlyMissing = true)
                else {
                    val lat = latEditText.text.toString().toDoubleSafe()
                    val lon = lonEditText.text.toString().toDoubleSafe()
                    weatherService.fetchNearestStation(lat, lon, selectedCalendar.timeInMillis) { station, _ ->
                        runOnUiThread {
                            if (station != null) {
                                nearestStation = station
                                fetchWeatherForDisplay(onlyMissing = true)
                            }
                        }
                    }
                }
            } else {
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
                isUpdatingFromCode = false
            }
        }
    }

    private fun setupWeatherForNewCatch(lat: Double, lon: Double) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("weather_enabled", true)) {
            autoWeatherCheckBox.visibility = View.VISIBLE
            autoWeatherCheckBox.isChecked = true
            weatherService.fetchNearestStations(lat, lon, selectedCalendar.timeInMillis, 1) { stations, _ ->
                runOnUiThread {
                    stations?.firstOrNull()?.let {
                        nearestStation = it
                        nearestStationText.text = "Sääasema: ${it.name}"
                        if (autoWeatherCheckBox.isChecked) fetchWeatherForDisplay()
                    }
                }
            }
        }
    }

    private fun fetchWeatherForDisplay(onlyMissing: Boolean = false) {
        val lat = latEditText.text.toString().toDoubleSafe()
        val lon = lonEditText.text.toString().toDoubleSafe()
        nearestStationText.text = "Haetaan säätietoja..."
        val catchInfo = if (fishCatch != null) "ID: ${fishCatch!!.id}" else "Uusi saalis"
        weatherService.fetchWeatherFromMultipleStations(lat, lon, selectedCalendar.timeInMillis, null, catchInfo) { data, time, error, stations ->
            runOnUiThread {
                if (data != null) {
                    applyWeatherData(data, time, null, onlyMissing)
                    currentWeatherStation = stations
                    nearestStationText.text = "Sääasema: ${nearestStation?.name ?: ""}"
                } else {
                    nearestStationText.text = "Virhe: $error"
                }
            }
        }
    }

    private fun applyWeatherData(data: Map<String, Double>, time: Long?, station: WeatherStation?, onlyMissing: Boolean = false) {
        val wasUpdating = isUpdatingFromCode
        isUpdatingFromCode = true
        fun setText(et: EditText, v: Double?) {
            if (v != null && (!onlyMissing || et.text.isNullOrEmpty())) et.setText(v.toString())
        }
        setText(airTempEditText, data["t2m"])
        setText(windSpeedEditText, data["ws_10min"])
        data["wd_10min"]?.let { if (!onlyMissing || windDirectionEditText.text.isNullOrEmpty()) {
            windDirectionEditText.setText(it.toInt().toString())
            updateWindArrow(it.toString())
        }}
        (data["nn_ll01"] ?: data["n_man"])?.let { if (!onlyMissing || cloudinessEditText.text.isNullOrEmpty()) cloudinessEditText.setText(it.toInt().toString()) }
        (data["r_1h"] ?: data["ri_10min"])?.let { setText(rainHourMmEditText, it) }
        (data["p_msl"] ?: data["p_sea"])?.let { if (!onlyMissing || pressureEditText.text.isNullOrEmpty()) pressureEditText.setText(it.toString()) }
        
        currentWeatherSource = "FMI"
        currentWeatherTime = time ?: selectedCalendar.timeInMillis
        isUpdatingFromCode = wasUpdating
    }

    private fun showDateTimePicker() {
        DatePickerDialog(this, { _, y, m, d ->
            selectedCalendar.set(y, m, d)
            TimePickerDialog(this, { _, h, min ->
                selectedCalendar.set(Calendar.HOUR_OF_DAY, h)
                selectedCalendar.set(Calendar.MINUTE, min)
                isTimeSetManually = true
                updateDateTimeButtonText()
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                val isWeatherEnabled = prefs.getBoolean("weather_enabled", true)
                autoWeatherCheckBox.visibility = if (isWeatherEnabled) View.VISIBLE else View.GONE
                if (autoWeatherCheckBox.isChecked) fetchWeatherForDisplay()
            }, selectedCalendar.get(Calendar.HOUR_OF_DAY), selectedCalendar.get(Calendar.MINUTE), true).show()
        }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveChanges() {
        if (autoWeatherCheckBox.visibility == View.VISIBLE && autoWeatherCheckBox.isChecked) {
            val progress = AlertDialog.Builder(this).setMessage("Päivitetään säätietoja...").setCancelable(false).show()
            val lat = latEditText.text.toString().toDoubleSafe()
            val lon = lonEditText.text.toString().toDoubleSafe()
            weatherService.fetchWeatherFromMultipleStations(lat, lon, selectedCalendar.timeInMillis, null, "Save") { data, time, _, stations ->
                runOnUiThread {
                    progress.dismiss()
                    data?.let { applyWeatherData(it, time, null); currentWeatherStation = stations; currentWeatherSource = "FMI" }
                    performFinalSave()
                }
            }
        } else performFinalSave()
    }

    private fun performFinalSave() {
        if (isPlace) {
            val poi = placeOfInterest ?: return
            val updatedPoi = poi.copy(
                typeId = placeTypeList.getOrNull(speciesSpinner.selectedItemPosition)?.id ?: poi.typeId,
                name = placeNameEditText.text.toString(),
                additionalInfo = additionalInfoEditText.text.toString(),
                latitude = latEditText.text.toString().toDoubleSafe(poi.latitude),
                longitude = lonEditText.text.toString().toDoubleSafe(poi.longitude)
            )
            Thread {
                if (updatedPoi.id == 0L) db.placeOfInterestDao().insert(updatedPoi) else db.placeOfInterestDao().update(updatedPoi)
                runOnUiThread { Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show(); setResult(RESULT_OK); finish() }
            }.start()
        } else {
            val catchId = intent.getLongExtra("EXTRA_CATCH_ID", -1L)
            
            val fc = fishCatch ?: return
            val selectedSpeciesId = speciesList.getOrNull(speciesSpinner.selectedItemPosition)?.id ?: ""
            val otherSpecies = otherSpeciesEditText.text.toString().trim()
            
            if (selectedSpeciesId == "OTHER" && otherSpecies.isEmpty()) {
                Toast.makeText(this, "Kalalaji on annettava.", Toast.LENGTH_SHORT).show()
                return
            }

            val selectedType = eventTypes.getOrNull(eventTypeSpinner.selectedItemPosition)
            val updated = fc.copy(
                species = selectedSpeciesId,
                otherSpecies = if (selectedSpeciesId == "OTHER") otherSpecies else null,
                eventType = if (selectedType == "EMPTY") null else (selectedType ?: FishCatch.CAUGHT_FISH),
                caughtAt = if (isTimeSetManually || (fc.caughtAt ?: 0L) > 0L) selectedCalendar.timeInMillis else null,
                weight = weightEditText.text.toString().toLongOrNull(),
                length = lengthEditText.text.toString().toLongOrNull(),
                method = methodEditText.text.toString(),
                lure = lureEditText.text.toString().takeIf { it.isNotBlank() },
                lureColor = lureColorEditText.text.toString().takeIf { it.isNotBlank() },
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
                fisherman = fishermanEditText.text.toString().trim(),
                latitude = latEditText.text.toString().toDoubleSafe(fc.latitude),
                longitude = lonEditText.text.toString().toDoubleSafe(fc.longitude)
            )
            Thread {
                if (updated.id == 0L) db.fishCatchDao().insert(updated) else db.fishCatchDao().update(updated)
                runOnUiThread {
                    AlertDialog.Builder(this).setMessage(R.string.save_success).setPositiveButton(R.string.ok) { _, _ ->
                        setResult(RESULT_OK); finish()
                    }.show().enlargeButtons()
                }
            }.start()
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        if (isPlace) {
            val poi = placeOfInterest ?: return false
            val selectedTypeId = placeTypeList.getOrNull(speciesSpinner.selectedItemPosition)?.id ?: ""
            if (selectedTypeId != poi.typeId) {
                // Jos alkuperäinen tyyppi oli tyhjä, ja nyt on valittu listan ensimmäinen, se ei ole muutos
                val isInitialDefault = poi.typeId.isEmpty() && speciesSpinner.selectedItemPosition == 0
                if (!isInitialDefault) return true
            }
            if (placeNameEditText.text.toString() != (poi.name ?: "")) return true
            if (additionalInfoEditText.text.toString() != (poi.additionalInfo ?: "")) return true
            return Math.abs(latEditText.text.toString().toDoubleSafe() - poi.latitude) > 0.0001 ||
                   Math.abs(lonEditText.text.toString().toDoubleSafe() - poi.longitude) > 0.0001
        }
        val fc = fishCatch ?: return false
        
        val selectedSpeciesId = speciesList.getOrNull(speciesSpinner.selectedItemPosition)?.id ?: ""
        if (selectedSpeciesId != fc.species) {
            // Jos alkuperäinen laji oli UNKNOWN/tyhjä, ja nyt on valittu listan ensimmäinen, se ei ole muutos
            val isInitialDefault = (fc.species == "" || fc.species == "UNKNOWN") && 
                                 speciesSpinner.selectedItemPosition == 0
            if (!isInitialDefault) return true
        }
        
        val selectedType = eventTypes.getOrNull(eventTypeSpinner.selectedItemPosition)
        val currentType = if (selectedType == "EMPTY") null else (selectedType ?: FishCatch.CAUGHT_FISH)
        if (currentType != fc.eventType && !(currentType == FishCatch.CAUGHT_FISH && fc.eventType == null)) return true

        val currentTime = if (isTimeSetManually || (fc.caughtAt ?: 0L) > 0L) selectedCalendar.timeInMillis else null
        if (currentTime != null && fc.caughtAt != null) {
            if (currentTime / 60000 != fc.caughtAt / 60000) return true
        } else if (currentTime != fc.caughtAt) {
            return true
        }
        
        if (weightEditText.text.toString().toLongOrNull() != (if ((fc.weight ?: 0L) > 0L) fc.weight else null)) return true
        if (lengthEditText.text.toString().toLongOrNull() != (if ((fc.length ?: 0L) > 0L) fc.length else null)) return true
        
        if (strikeDepthEditText.text.toString().toDoubleOrNull() != (if ((fc.strikeDepth ?: 0.0) != 0.0) fc.strikeDepth else null)) return true
        if (waterDepthEditText.text.toString().toDoubleOrNull() != (if ((fc.waterDepth ?: 0.0) != 0.0) fc.waterDepth else null)) return true
        if (waterTempEditText.text.toString().toDoubleOrNull() != (if ((fc.waterTemp ?: 0.0) != 0.0) fc.waterTemp else null)) return true

        if (methodEditText.text.toString() != (fc.method ?: "")) return true
        if (lureEditText.text.toString() != (fc.lure ?: "")) return true
        if (lureColorEditText.text.toString() != (fc.lureColor ?: "")) return true
        if (additionalInfoEditText.text.toString() != (fc.additionalInfo ?: "")) return true
        if (tripNotesEditText.text.toString() != (fc.tripNotes ?: "")) return true
        
        if (!fishermanEditText.text.toString().trim().equals(fc.fisherman?.trim() ?: "", ignoreCase = true)) return true
        if (!otherSpeciesEditText.text.toString().trim().equals(fc.otherSpecies?.trim() ?: "", ignoreCase = true)) return true

        return Math.abs(latEditText.text.toString().toDoubleSafe() - fc.latitude) > 0.0001 ||
               Math.abs(lonEditText.text.toString().toDoubleSafe() - fc.longitude) > 0.0001
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this).setMessage(R.string.unsaved_changes_warning)
            .setPositiveButton(R.string.discard) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null).show().enlargeButtons()
    }

    private fun String.toDoubleSafe(default: Double = 0.0): Double {
        val d = this.replace(',', '.').toDoubleOrNull()
        return if (d == null || d.isNaN()) default else d
    }
}
