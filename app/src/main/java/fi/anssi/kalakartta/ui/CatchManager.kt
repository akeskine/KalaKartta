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
                FishSpecies("WHITEFISH", "Siika", icon_default = "siika")
            )
            showSpeciesDialogWithData(fallbacks)
            return
        }

        showSpeciesDialogWithData(speciesList)
    }

    private fun showSpeciesDialogWithData(speciesList: List<FishSpecies>) {
        val adapter = object : ArrayAdapter<FishSpecies>(activity, R.layout.item_species_dialog, speciesList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_species_dialog, parent, false)
                val iconView = view.findViewById<ImageView>(R.id.speciesIcon)
                val nameView = view.findViewById<TextView>(R.id.speciesName)

                val species = speciesList[position]
                nameView.text = species.name
                val iconId = getDrawableId(species.icon_default)
                iconView.setImageResource(iconId)
                iconView.visibility = View.VISIBLE
                
                return view
            }
        }

        val builder = AlertDialog.Builder(activity)
        
        val inflater = LayoutInflater.from(activity)
        val contentView = inflater.inflate(R.layout.dialog_species_title, null)
        
        val stationInfo = contentView.findViewById<TextView>(R.id.weatherStationInfo)
        stationInfo.visibility = View.GONE

        val weightInput = contentView.findViewById<EditText>(R.id.weightInput)
        val lengthInput = contentView.findViewById<EditText>(R.id.lengthInput)

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
            addCatchAtSelectedLocation(speciesList[which].id, weight, length)
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
        var typeList = db.placeOfInterestTypeDao().getAll()
        if (typeList.isEmpty()) {
            val fallbacks = listOf(
                PlaceOfInterestType("ACCOMMODATION", "Majoitus", icon = "majoitus"),
                PlaceOfInterestType("CAMP", "Leiripaikka"),
                PlaceOfInterestType("ACCESS", "Pääsy rantaan"),
                PlaceOfInterestType("PARKING", "Pysäköinti", icon = "pysakointi"),
                PlaceOfInterestType("RAMP", "Veneramppi"),
                PlaceOfInterestType("HARBOUR", "Satama"),
                PlaceOfInterestType("ROCK", "Kivi"),
                PlaceOfInterestType("VEGETATION", "Kasvusto")
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

                iconView.layoutParams.width = (40 * context.resources.displayMetrics.density).toInt()
                iconView.layoutParams.height = (40 * context.resources.displayMetrics.density).toInt()
                
                return view
            }
        }

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.add_other)
        
        val listView = android.widget.ListView(activity)
        listView.adapter = adapter
        builder.setView(listView)
        
        val dialog = builder.create()
        listView.setOnItemClickListener { _, _, which, _ ->
            dialog.dismiss()
            showPlaceNameDialog(typeList[which])
        }
        dialog.show()
    }

    private fun showPlaceNameDialog(type: PlaceOfInterestType) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(type.name)
        
        val input = EditText(activity)
        input.setHint(R.string.place_name)
        val container = LinearLayout(activity)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(48, 20, 48, 20)
        input.layoutParams = params
        container.addView(input)
        builder.setView(container)

        builder.setPositiveButton(R.string.ok) { _, _ ->
            val name = input.text.toString()
            if (name.isNotEmpty()) {
                addPlaceAtSelectedLocation(type.id, name)
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
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

    private fun addCatchAtSelectedLocation(speciesId: String, weight: Long? = null, length: Long? = null) {
        val point = map.mapCenter as GeoPoint
        val caughtAt = System.currentTimeMillis()

        val fish = FishCatch(
            species = speciesId,
            latitude = String.format(java.util.Locale.US, "%.5f", point.latitude).toDouble(),
            longitude = String.format(java.util.Locale.US, "%.5f", point.longitude).toDouble(),
            caughtAt = caughtAt,
            weight = weight,
            length = length
        )

        // Tallennetaan taustalla ja päivitetään UI
        Thread {
            val id = db.fishCatchDao().insert(fish)
            val fishWithId = fish.copy(id = id)

            activity.runOnUiThread {
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
                        db.fishCatchDao().update(updatedFish)
                        activity.runOnUiThread {
                            onCatchAdded(updatedFish)
                        }
                    }
                }
            }
        }.start()
    }
}
