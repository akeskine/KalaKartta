package fi.anssi.kalakartta.ui

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import fi.anssi.kalakartta.utils.WeatherService
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class CatchManager(
    private val activity: AppCompatActivity,
    private val map: MapView,
    private val db: AppDatabase,
    private val weatherService: WeatherService,
    private val onCatchAdded: (FishCatch) -> Unit
) {
    fun showSpeciesDialog() {
        val speciesList = db.fishSpeciesDao().getAll()
        
        if (speciesList.isEmpty()) {
            // Varatoimenpide jos tietokanta on tyhjä (esim. ensikäynnistys ja Thread ei ole ehtinyt loppuun)
            val fallbacks = listOf(
                FishSpecies("PERCH", "Ahven", icon_default = "ahven"),
                FishSpecies("PIKE", "Hauki", icon_default = "hauki"),
                FishSpecies("ZANDER", "Kuha", icon_default = "kuha"),
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
            override fun getCount(): Int = speciesList.size + 1

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_species_dialog, parent, false)
                val iconView = view.findViewById<ImageView>(R.id.speciesIcon)
                val nameView = view.findViewById<TextView>(R.id.speciesName)

                if (position < speciesList.size) {
                    val species = speciesList[position]
                    nameView.text = species.name
                    val iconId = getDrawableId(species.icon_default)
                    iconView.setImageResource(iconId)
                    iconView.visibility = View.VISIBLE
                } else {
                    nameView.text = context.getString(R.string.add_detailed)
                    iconView.visibility = View.GONE
                }
                return view
            }
        }

        val builder = AlertDialog.Builder(activity)
        
        // Kustomoitu otsikko sääasemalle (ei näytetä enää sääasemaa käyttäjän toiveesta)
        val inflater = LayoutInflater.from(activity)
        val titleView = inflater.inflate(R.layout.dialog_species_title, null)
        builder.setCustomTitle(titleView)
        
        val stationInfo = titleView.findViewById<TextView>(R.id.weatherStationInfo)
        stationInfo.visibility = View.GONE

        builder.setAdapter(adapter) { _, which ->
            if (which < speciesList.size) {
                addCatchAtSelectedLocation(speciesList[which].id)
            } else {
                openEditCatchForNewEntry()
            }
        }
        
        builder.show()
    }

    private fun openEditCatchForNewEntry() {
        val point = map.mapCenter as GeoPoint
        val intent = Intent(activity, EditCatchActivity::class.java)
        intent.putExtra("EXTRA_LATITUDE", point.latitude)
        intent.putExtra("EXTRA_LONGITUDE", point.longitude)
        activity.startActivityForResult(intent, 1001)
    }

    @Suppress("DiscouragedApi")
    private fun getDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return R.drawable.default_point
        val id = activity.resources.getIdentifier(iconName, "drawable", activity.packageName)
        return if (id != 0) id else R.drawable.default_point
    }

    private fun addCatchAtSelectedLocation(speciesId: String) {
        val point = map.mapCenter as GeoPoint
        val caughtAt = System.currentTimeMillis()

        val fish = FishCatch(
            species = speciesId,
            latitude = point.latitude,
            longitude = point.longitude,
            caughtAt = caughtAt
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
                weatherService.fetchNearestStation(point.latitude, point.longitude, caughtAt) { station, _ ->
                    if (station != null) {
                        weatherService.fetchWeatherData(station.fmisid, caughtAt) { data, obsTime, _ ->
                            if (data != null) {
                                val updatedFish = fishWithId.copy(
                                    airTemp = data["t2m"] ?: 0.0,
                                    cloudiness = (data["nn_ll01"] ?: 0.0).toLong(),
                                    rain = (data["r_1h"] ?: 0.0).toLong(),
                                    windSpeed = data["ws_10min"] ?: 0.0,
                                    windDirection = (data["wd_10min"] ?: 0.0).toLong(),
                                    pressure = data["p_sea"] ?: data["p_msl"] ?: 0.0,
                                    weatherSource = "FMI",
                                    weatherTime = obsTime ?: caughtAt,
                                    weatherStation = "${station.fmisid}:${station.name}"
                                )
                                db.fishCatchDao().update(updatedFish)
                                activity.runOnUiThread {
                                    onCatchAdded(updatedFish)
                                }
                            }
                        }
                    }
                }
            }
        }.start()
    }
}
