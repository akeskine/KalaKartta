package fi.anssi.kalakartta.ui

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.data.PlaceOfInterestType
import fi.anssi.kalakartta.utils.WeatherService
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class CatchManager(
    private val activity: AppCompatActivity,
    private val map: MapView,
    private val db: AppDatabase,
    private val weatherService: WeatherService,
    private val onCatchAdded: (FishCatch) -> Unit,
    private val onPlaceAdded: (PlaceOfInterest) -> Unit
) {
    fun showSpeciesDialog() {
        val speciesList = db.fishSpeciesDao().getAll()
        
        if (speciesList.isEmpty()) {
            // Varatoimenpide jos tietokanta on tyhjä (esim. ensikäynnistys ja Thread ei ole ehtinyt loppuun)
            val fallbacks = listOf(
                FishSpecies(
                    "PERCH", "Ahven", icon_default = "ahven",
                    small_weight = 200, small_length = 25,
                    large_weight = 500, large_length = 35,
                    giant_weight = 800, giant_length = 40
                ),
                FishSpecies(
                    "PIKE", "Hauki", icon_default = "hauki",
                    small_weight = 1000, small_length = 55,
                    large_weight = 3000, large_length = 80,
                    giant_weight = 8000, giant_length = 100
                ),
                FishSpecies(
                    "ZANDER", "Kuha", icon_default = "kuha",
                    small_weight = 800, small_length = 42,
                    large_weight = 2000, large_length = 60,
                    giant_weight = 5000, giant_length = 80
                ),
                FishSpecies("TROUT", "Taimen", icon_default = "taimen"),
                FishSpecies("SALMON", "Lohi", icon_default = "lohi"),
                FishSpecies("GRAYLING", "Harjus", icon_default = "harjus"),
                FishSpecies("WHITEFISH", "Siika", icon_default = "siika"),
                FishSpecies("RAINBOW", "Kirjolohi", icon_default = "kirjolohi"),
                FishSpecies("BREAM", "Lahna", icon_default = "lahna"),
                FishSpecies("IDE", "Säyne", icon_default = "sayne"),
                FishSpecies("CHAR", "Rautu", icon_default = "rautu"),
                FishSpecies("BURBOT", "Made", icon_default = "made"),
                FishSpecies("OTHER", "Muu kalalaji", icon_default = "muukala")
            )
            showSpeciesDialogWithData(fallbacks)
            return
        }

        showSpeciesDialogWithData(speciesList)
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
                nameView.text = species.name
                val iconId = getDrawableId(species.icon_default)
                iconView.setImageResource(iconId)
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
            } else {
                addCatchAtSelectedLocation(species.id, weight, length, selectedEventType)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openEditCatchForNewEntry() {
        val point = map.mapCenter as GeoPoint
        val intent = Intent(activity, EditCatchActivity::class.java)
        intent.putExtra("EXTRA_LATITUDE", point.latitude)
        intent.putExtra("EXTRA_LONGITUDE", point.longitude)
        activity.startActivityForResult(intent, 1001)
    }

    private fun showOtherTypesDialog() {
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
                PlaceOfInterestType("OTHER", "Muu kiinnostava paikka", icon = "tahti", sortOrder = 14)
            )
            // Tallennetaan fallbackit kerralla kantaan
            Thread {
                fallbacks.forEach { db.placeOfInterestTypeDao().insert(it) }
            }.start()
            typeList = fallbacks
        }

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
            activity.startActivityForResult(intent, 1001)
        }

        contentView.findViewById<View>(R.id.addOtherButton).visibility = View.GONE
        
        listView.setOnItemClickListener { _, _, which, _ ->
            val name = nameInput.text.toString()
            addPlaceAtSelectedLocation(typeList[which].id, name)
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun addPlaceAtSelectedLocation(typeId: String, name: String) {
        val point = map.mapCenter as GeoPoint
        val place = PlaceOfInterest(
            typeId = typeId,
            latitude = String.format(java.util.Locale.US, "%.5f", point.latitude).toDouble(),
            longitude = String.format(java.util.Locale.US, "%.5f", point.longitude).toDouble(),
            name = name
        )

        Thread {
            val id = db.placeOfInterestDao().insert(place)
            val placeWithId = place.copy(id = id)
            activity.runOnUiThread {
                android.util.Log.d("CatchManager", "Place added: ID=${placeWithId.id}")
                onPlaceAdded(placeWithId)
            }
        }.start()
    }

    private fun getPlaceTypeDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return 0
        val id = activity.resources.getIdentifier(iconName, "drawable", activity.packageName)
        return id
    }

    @Suppress("DiscouragedApi")
    private fun getDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return R.drawable.default_point
        val id = activity.resources.getIdentifier(iconName, "drawable", activity.packageName)
        return if (id != 0) id else R.drawable.default_point
    }

    private fun addCatchAtSelectedLocation(speciesId: String, weight: Long? = null, length: Long? = null, eventType: String? = FishCatch.CAUGHT_FISH) {
        val point = map.mapCenter as GeoPoint
        val caughtAt = System.currentTimeMillis()
        
        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val defaultFisherman = prefs.getString("default_fisherman", "") ?: ""

        val fish = FishCatch(
            species = speciesId,
            eventType = eventType,
            latitude = String.format(java.util.Locale.US, "%.5f", point.latitude).toDouble(),
            longitude = String.format(java.util.Locale.US, "%.5f", point.longitude).toDouble(),
            caughtAt = caughtAt,
            weight = weight,
            length = length,
            fisherman = defaultFisherman
        )

        // Tallennetaan taustalla ja päivitetään UI
        Thread {
            val id = db.fishCatchDao().insert(fish)
            val fishWithId = fish.copy(id = id)

            activity.runOnUiThread {
                android.util.Log.d("CatchManager", "Initial catch added: ID=${fishWithId.id}")
                onCatchAdded(fishWithId)
            }

            // Haetaan säätiedot automaattisesti jos asetus on päällä
            val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val weatherEnabled = prefs.getBoolean("weather_enabled", true)
            if (weatherEnabled) {
                val catchInfo = "ID: $id (uusi)"
                weatherService.fetchWeatherFromMultipleStations(point.latitude, point.longitude, caughtAt, null, catchInfo) { data, obsTime, _, stations ->
                    if (data != null) {
                        val rainHour = data["r_1h"] ?: data["ri_10min"]
                        val updatedFish = fishWithId.copy(
                            airTemp = data["t2m"],
                            cloudiness = data["nn_ll01"]?.toLong() ?: data["n_man"]?.toLong(),
                            rainHourMm = rainHour,
                            windSpeed = data["ws_10min"],
                            windDirection = data["wd_10min"]?.toLong(),
                            pressure = data["p_sea"] ?: data["p_msl"],
                            weatherSource = "FMI",
                            weatherTime = obsTime ?: caughtAt,
                            weatherStation = stations
                        )
                        // Varmistetaan ennen päivitystä, ettei kohdetta ole juuri poistettu
                        val isStillValid = Thread {
                            val current = db.fishCatchDao().getById(updatedFish.id)
                            if (current != null) {
                                db.fishCatchDao().update(updatedFish)
                                activity.runOnUiThread {
                                    android.util.Log.d("CatchManager", "Updating catch with weather: ID=${updatedFish.id}")
                                    onCatchAdded(updatedFish)
                                }
                            } else {
                                android.util.Log.d("CatchManager", "Catch ID=${updatedFish.id} was deleted during weather fetch, skipping update.")
                            }
                        }.start()
                    }
                }
            }
        }.start()
    }
}
