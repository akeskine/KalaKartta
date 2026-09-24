package fi.anssi.kalakartta.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.content.Context
import fi.anssi.kalakartta.MainActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.data.PlaceOfInterestType
import fi.anssi.kalakartta.utils.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File

class CatchManager(
    private val activity: AppCompatActivity,
    private val map: MapView,
    private val db: AppDatabase,
    private val weatherService: WeatherService,
    private val dialogOrientationLock: DialogOrientationLock,
    private val onCatchAdded: (FishCatch) -> Unit,
    private val onPlaceAdded: (PlaceOfInterest) -> Unit
) {
    private fun launchActivityForResult(intent: Intent, requestCode: Int) {
        if (activity is MainActivity) {
            activity.launchActivityForResult(intent, requestCode)
        } else {
            activity.startActivity(intent)
        }
    }

    fun showSpeciesDialog() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val speciesList = db.fishSpeciesDao().getAll()
            val favourites = if (speciesList.isEmpty()) {
                FishSpecies.getDefaultList().filter { it.favourite_fish }
            } else {
                speciesList.filter { it.favourite_fish }
            }
            withContext(Dispatchers.Main) {
                showSpeciesDialogWithData(favourites)
            }
        }
    }

    private fun showSpeciesDialogWithData(speciesList: List<FishSpecies>) {
        val fullSpeciesList = mutableListOf<FishSpecies>()
        fullSpeciesList.add(FishSpecies("UNKNOWN_STRIKE", "Tuntematon tärppi", icon_default = "tarppi_varma"))
        fullSpeciesList.addAll(speciesList)

        val adapter = object : ArrayAdapter<FishSpecies>(activity, R.layout.item_species_dialog, fullSpeciesList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_species_dialog, parent, false)
                val iconView = view.findViewById<ImageView>(R.id.speciesIcon)
                val nameView = view.findViewById<TextView>(R.id.speciesName)

                val species = fullSpeciesList[position]
                nameView.text = if (species.id == "UNKNOWN_STRIKE" || species.id == "UNKNOWN") species.name else species.name.lowercase().replaceFirstChar { it.uppercase() }
                val iconId = getDrawableId(species.icon_default)
                if (iconId != 0 && (iconId != R.drawable.default_point || species.id != "UNKNOWN")) {
                    iconView.setImageResource(iconId)
                } else if (species.icon_default.isNotEmpty()) {
                    val file = if (species.icon_default.startsWith("/")) File(species.icon_default) else File(activity.filesDir, species.icon_default)
                    if (file.exists()) {
                        iconView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                    } else {
                        iconView.setImageResource(R.drawable.muukala)
                    }
                } else if (species.id == "UNKNOWN") {
                    iconView.setImageResource(R.drawable.default_point)
                } else {
                    iconView.setImageResource(R.drawable.muukala)
                }
                iconView.visibility = View.VISIBLE
                
                if (species.icon_default == "seurio") {
                    iconView.scaleX = 1.3f
                    iconView.scaleY = 1.3f
                } else {
                    iconView.scaleX = 1.0f
                    iconView.scaleY = 1.0f
                }

                return view
            }
        }

        val builder = AlertDialog.Builder(activity)
        
        val inflater = LayoutInflater.from(activity)
        val contentView = inflater.inflate(R.layout.dialog_species_title, null)
        
        val titleView = contentView.findViewById<TextView>(R.id.dialogTitle)
        titleView.text = activity.getString(R.string.add_catch)

        val stationInfo = contentView.findViewById<TextView>(R.id.weatherStationInfo)
        stationInfo.visibility = View.GONE

        val fishInputsLayout = contentView.findViewById<View>(R.id.fishInputsLayout)
        fishInputsLayout.visibility = View.VISIBLE
        
        val placeNameInputLayout = contentView.findViewById<View>(R.id.placeNameInputLayout)
        placeNameInputLayout.visibility = View.GONE

        val eventTypeContainer = contentView.findViewById<View>(R.id.eventTypeContainer)
        eventTypeContainer.visibility = View.VISIBLE

        val weightInput = contentView.findViewById<EditText>(R.id.weightInput)
        val lengthInput = contentView.findViewById<EditText>(R.id.lengthInput)

        val eventTypeSpinner = contentView.findViewById<Spinner>(R.id.eventTypeSpinner)
        val eventTypes = listOf(
            FishCatch.CAUGHT_FISH,
            FishCatch.LOST_FISH,
            FishCatch.STRIKE_CERTAIN,
            FishCatch.STRIKE_UNCERTAIN,
            FishCatch.FISH_FOLLOW
        )
        val eventTypeAdapter = object : ArrayAdapter<String>(activity, R.layout.item_species_dialog, eventTypes) {
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
                nameView.text = FishCatch.getEventTypeName(type)
                
                val iconName = when (type) {
                    FishCatch.LOST_FISH -> "karkuutus"
                    FishCatch.STRIKE_CERTAIN -> "tarppi_varma"
                    FishCatch.STRIKE_UNCERTAIN -> "tarppi_epavarma"
                    FishCatch.FISH_FOLLOW -> "seurio"
                    else -> "" // "Saatu kala" ei tarvitse erillistä ikonia tässä listassa, tai se voi olla tyhjä
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
                
                return view
            }
        }
        eventTypeSpinner.adapter = eventTypeAdapter

        eventTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = eventTypes[position]
                val unknownItem = fullSpeciesList[0]
                
                when (selectedType) {
                    FishCatch.LOST_FISH -> {
                        fullSpeciesList[0] = fullSpeciesList[0].copy(name = "Tuntematon karkuutus", icon_default = "karkuutus")
                    }
                    FishCatch.STRIKE_UNCERTAIN -> {
                        fullSpeciesList[0] = fullSpeciesList[0].copy(name = "Tuntematon epävarma tärppi", icon_default = "tarppi_epavarma")
                    }
                    FishCatch.FISH_FOLLOW -> {
                        fullSpeciesList[0] = fullSpeciesList[0].copy(name = "Tuntematon seurio", icon_default = "seurio")
                    }
                    else -> {
                        fullSpeciesList[0] = fullSpeciesList[0].copy(name = "Tuntematon tärppi", icon_default = "tarppi_varma")
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val listView = contentView.findViewById<android.widget.ListView>(R.id.speciesListView)
        listView.adapter = adapter

        builder.setView(contentView)
        val dialog = builder.create()

        contentView.findViewById<View>(R.id.addDetailedButton).setOnClickListener {
            dialog.dismiss()
            openEditCatchForNewEntry()
        }

        contentView.findViewById<View>(R.id.addOtherButton).setOnClickListener {
            dialog.dismiss()
            showOtherTypesDialog()
        }
        
        listView.setOnItemClickListener { _, _, which, _ ->
            val weight = weightInput.text.toString().toLongOrNull()
            val length = lengthInput.text.toString().toLongOrNull()
            
            val species = fullSpeciesList[which]
            val selectedEventType = eventTypes[eventTypeSpinner.selectedItemPosition]
            
            if (species.id == "UNKNOWN_STRIKE") {
                val finalEventType = when (selectedEventType) {
                    FishCatch.CAUGHT_FISH, FishCatch.STRIKE_CERTAIN -> FishCatch.STRIKE_CERTAIN
                    else -> selectedEventType
                }
                addCatchAtSelectedLocation("UNKNOWN", weight, length, finalEventType)
            } else if (species.id == "OTHER") {
                val otherSpeciesInput = EditText(activity).apply {
                    hint = "Syötä kalalaji"
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
                val layout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 20, 60, 0)
                    addView(otherSpeciesInput)
                }
                val otherSpeciesDialog = AlertDialog.Builder(activity)
                    .setTitle("Syötä kalalaji")
                    .setView(layout)
                    .setPositiveButton("Tallenna") { _, _ ->
                        val otherSpeciesValue = otherSpeciesInput.text.toString().trim().uppercase()
                        if (otherSpeciesValue.isEmpty()) {
                            Toast.makeText(activity, "Kalalaji on annettava.", Toast.LENGTH_SHORT).show()
                        } else {
                            addCatchAtSelectedLocation(species.id, weight, length, selectedEventType, otherSpeciesValue)
                        }
                    }
                    .setNegativeButton("Peruuta", null)
                    .create()
                dialogOrientationLock.show(otherSpeciesDialog)
            } else {
                addCatchAtSelectedLocation(species.id, weight, length, selectedEventType)
            }
            dialog.dismiss()
        }

        dialogOrientationLock.show(dialog)
    }

    private fun openEditCatchForNewEntry() {
        val point = map.mapCenter as GeoPoint
        val intent = Intent(activity, EditCatchActivity::class.java)
        intent.putExtra("EXTRA_LATITUDE", point.latitude)
        intent.putExtra("EXTRA_LONGITUDE", point.longitude)
        launchActivityForResult(intent, 1001)
    }

    private fun showOtherTypesDialog() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            var typeList = db.placeOfInterestTypeDao().getAll().sortedBy { it.sortOrder }
            if (typeList.isEmpty()) {
                val fallbacks = listOf(
                PlaceOfInterestType("ROCK", "Kivi", icon = "kivi", sortOrder = 1),
                PlaceOfInterestType("VEGETATION", "Kasvusto", icon = "vesikasvi", sortOrder = 2),
                PlaceOfInterestType("SHALLOW", "Matalikko", icon = "matalikko", sortOrder = 3),
                PlaceOfInterestType("DEEP", "Syvänne", icon = "syvanne", sortOrder = 4),
                PlaceOfInterestType("PARKING", "Pysäköinti", icon = "pysakointi", sortOrder = 5),
                PlaceOfInterestType("ACCESS", "Pääsy rantaan", icon = "access", sortOrder = 6),
                PlaceOfInterestType("LANDINGSPOT", "Rantautumispaikka", icon = "rantautumispaikka", sortOrder = 7),
                PlaceOfInterestType("RAMP", "Veneramppi", icon = "ramppi", sortOrder = 8),
                PlaceOfInterestType("HARBOUR", "Satama", icon = "satama", sortOrder = 9),
                PlaceOfInterestType("ACCOMMODATION", "Majoitus", icon = "majoitus", sortOrder = 10),
                PlaceOfInterestType("CAMP", "Leiripaikka", icon = "leiripaikka", sortOrder = 11),
                PlaceOfInterestType("CAMPFIRE", "Tulipaikka", icon = "tulipaikka", sortOrder = 12),
                PlaceOfInterestType("SHELTER", "Laavu", icon = "laavu", sortOrder = 13),
                PlaceOfInterestType("OTHER", "Muu kiinnostava paikka", icon = "tahti", sortOrder = 14),
                PlaceOfInterestType("PROSPECT", "Mahdollinen kalapaikka", icon = "ehka", sortOrder = 15)
            )
                fallbacks.forEach { db.placeOfInterestTypeDao().insert(it) }
                typeList = fallbacks
            }
            withContext(Dispatchers.Main) {
                showOtherTypesDialogWithData(typeList)
            }
        }
    }

    private fun showOtherTypesDialogWithData(typeList: List<PlaceOfInterestType>) {
        val adapter = object : ArrayAdapter<PlaceOfInterestType>(activity, R.layout.item_species_dialog, typeList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_species_dialog, parent, false)
                val iconView = view.findViewById<ImageView>(R.id.speciesIcon)
                val nameView = view.findViewById<TextView>(R.id.speciesName)

                val type = typeList[position]
                nameView.text = type.name
                val iconId = getPlaceTypeDrawableId(type.icon)
                iconView.setImageResource(iconId)
                iconView.visibility = if (type.icon.isNotEmpty()) View.VISIBLE else View.GONE
                
                return view
            }
        }

        val builder = AlertDialog.Builder(activity)
        val inflater = LayoutInflater.from(activity)
        val contentView = inflater.inflate(R.layout.dialog_species_title, null)
        
        val titleView = contentView.findViewById<TextView>(R.id.dialogTitle)
        titleView.text = activity.getString(R.string.add_other)

        val stationInfo = contentView.findViewById<TextView>(R.id.weatherStationInfo)
        stationInfo.visibility = View.GONE

        val fishInputsLayout = contentView.findViewById<View>(R.id.fishInputsLayout)
        fishInputsLayout.visibility = View.GONE

        val eventTypeContainer = contentView.findViewById<View>(R.id.eventTypeContainer)
        eventTypeContainer.visibility = View.GONE
        
        val placeNameInputLayout = contentView.findViewById<View>(R.id.placeNameInputLayout)
        placeNameInputLayout.visibility = View.VISIBLE

        val nameInput = contentView.findViewById<EditText>(R.id.placeNameInput)

        val listView = contentView.findViewById<android.widget.ListView>(R.id.speciesListView)
        listView.adapter = adapter

        builder.setView(contentView)
        val dialog = builder.create()

        contentView.findViewById<View>(R.id.addDetailedButton).setOnClickListener {
            dialog.dismiss()
            val point = map.mapCenter as GeoPoint
            val intent = Intent(activity, EditCatchActivity::class.java)
            intent.putExtra("EXTRA_IS_PLACE", true)
            intent.putExtra("EXTRA_LATITUDE", point.latitude)
            intent.putExtra("EXTRA_LONGITUDE", point.longitude)
            intent.putExtra("EXTRA_PLACE_NAME", nameInput.text.toString())
            launchActivityForResult(intent, 1001)
        }

        contentView.findViewById<View>(R.id.addOtherButton).visibility = View.GONE
        
        listView.setOnItemClickListener { _, _, which, _ ->
            val name = nameInput.text.toString()
            addPlaceAtSelectedLocation(typeList[which].id, name)
            dialog.dismiss()
        }

        dialogOrientationLock.show(dialog)
    }


    private fun addPlaceAtSelectedLocation(typeId: String, name: String) {
        val point = map.mapCenter as GeoPoint
        val place = PlaceOfInterest(
            typeId = typeId,
            latitude = String.format(java.util.Locale.US, "%.5f", point.latitude).toDouble(),
            longitude = String.format(java.util.Locale.US, "%.5f", point.longitude).toDouble(),
            name = name
        )

        activity.lifecycleScope.launch {
            val placeWithId = withContext(Dispatchers.IO) {
                val id = db.placeOfInterestDao().insert(place)
                place.copy(id = id)
            }

            if (!activity.isFinishing && !activity.isDestroyed) {
                android.util.Log.d("CatchManager", "Place added: ID=${placeWithId.id}")
                onPlaceAdded(placeWithId)
            }
        }
    }

    private fun getPlaceTypeDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return 0
        val id = activity.resources.getIdentifier(iconName, "drawable", activity.packageName)
        return id
    }

    @Suppress("DiscouragedApi")
    private fun getDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return 0
        val id = activity.resources.getIdentifier(iconName, "drawable", activity.packageName)
        return id
    }

    private fun addCatchAtSelectedLocation(speciesId: String, weight: Long? = null, length: Long? = null, eventType: String? = FishCatch.CAUGHT_FISH, otherSpecies: String? = null) {
        val point = map.mapCenter as GeoPoint
        val caughtAt = System.currentTimeMillis()
        
        val settingsStore = SettingsStore(activity.getSharedPreferences("settings", Context.MODE_PRIVATE))
        val defaultFisherman = settingsStore.defaultFisherman

        val fish = FishCatch(
            species = speciesId,
            otherSpecies = otherSpecies,
            eventType = eventType,
            latitude = String.format(java.util.Locale.US, "%.5f", point.latitude).toDouble(),
            longitude = String.format(java.util.Locale.US, "%.5f", point.longitude).toDouble(),
            caughtAt = caughtAt,
            weight = weight,
            length = length,
            fisherman = defaultFisherman.uppercase()
        )

        // Tallennetaan taustalla ja päivitetään UI
        activity.lifecycleScope.launch {
            val fishWithId = withContext(Dispatchers.IO) {
                val id = db.fishCatchDao().insert(fish)
                fish.copy(id = id)
            }

            if (!activity.isFinishing && !activity.isDestroyed) {
                android.util.Log.d("CatchManager", "Initial catch added: ID=${fishWithId.id}")
                onCatchAdded(fishWithId)
            }

            // Haetaan säätiedot automaattisesti jos asetus on päällä.
            if (!settingsStore.weatherEnabled) return@launch

            val catchInfo = "ID: ${fishWithId.id} (uusi)"
            weatherService.fetchWeatherFromMultipleStations(
                point.latitude,
                point.longitude,
                caughtAt,
                null,
                catchInfo
            ) { data, obsTime, _, stations ->
                // WeatherServicein callback tulee omasta taustatyöstään. Siirretään
                // jatkokäsittely Activityn lifecycleen ennen tietokanta-/UI-päivityksiä.
                activity.lifecycleScope.launch {
                    var updatedFish = if (data != null) {
                        fishWithId.copy(
                            airTemp = data["t2m"],
                            cloudiness = data["nn_ll01"]?.toLong() ?: data["n_man"]?.toLong(),
                            rainHourMm = data["r_1h"] ?: data["ri_10min"],
                            windSpeed = data["ws_10min"],
                            windDirection = data["wd_10min"]?.toLong(),
                            pressure = data["p_sea"] ?: data["p_msl"],
                            weatherSource = "FMI",
                            weatherTime = obsTime ?: caughtAt,
                            weatherStation = stations
                        )
                    } else fishWithId

                    if (data != null) {
                        // Haetaan FMISID historiatiedon hakua varten.
                        val fmisid = stations.substringBefore(":").trim().ifEmpty { null }
                        if (fmisid != null) {
                            try {
                                val startTime = caughtAt - 6 * 60 * 60 * 1000L
                                val endTime = minOf(
                                    caughtAt + 6 * 60 * 60 * 1000L,
                                    System.currentTimeMillis()
                                )
                                val samples = weatherService.fetchPressureSamplesSuspend(fmisid, startTime, endTime)

                                if (samples.isNotEmpty()) {
                                    val withSamples = updatedFish.copy(pressureSamples = samples)
                                    updatedFish = withSamples.copy(
                                        pressureTrend = withSamples.calculatePressureTrend(),
                                        pressureTurningTrend = withSamples.calculatePressureTurningTrend()
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CatchManager", "Error fetching seaLevel history", e)
                            }
                        }
                    }

                    try {
                        val seaLevelResult = weatherService.fetchSeaLevelFromMultipleStationsSuspend(
                            point.latitude,
                            point.longitude,
                            caughtAt
                        )
                        if (seaLevelResult != null) {
                            updatedFish = updatedFish.withSeaLevelResult(seaLevelResult)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CatchManager", "Error fetching sea level history", e)
                    }

                    // Varmistetaan ennen päivitystä, ettei kohdetta ole juuri poistettu.
                    val current = withContext(Dispatchers.IO) {
                        db.fishCatchDao().getById(updatedFish.id)
                    }
                    if (current != null && updatedFish != fishWithId) {
                        withContext(Dispatchers.IO) {
                            db.fishCatchDao().update(updatedFish)
                        }
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            val pressureSuffix = if (updatedFish.pressureSamples.isNotEmpty()) {
                                " and ${updatedFish.pressureSamples.size} seaLevel samples"
                            } else {
                                ""
                            }
                            val seaLevelSuffix = if (updatedFish.seaLevelSamples.isNotEmpty()) {
                                " and ${updatedFish.seaLevelSamples.size} sea-level samples"
                            } else {
                                ""
                            }
                            android.util.Log.d(
                                "CatchManager",
                                "Updating catch with weather$pressureSuffix$seaLevelSuffix: ID=${updatedFish.id}"
                            )
                            onCatchAdded(updatedFish)
                        }
                    }
                }
            }
        }
    }
}
