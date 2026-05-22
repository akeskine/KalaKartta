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
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class CatchManager(
    private val activity: AppCompatActivity,
    private val map: MapView,
    private val db: AppDatabase,
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

        AlertDialog.Builder(activity)
            .setTitle(R.string.add_catch)
            .setAdapter(adapter) { _, which ->
                if (which < speciesList.size) {
                    addCatchAtSelectedLocation(speciesList[which].id)
                } else {
                    openEditCatchForNewEntry()
                }
            }
            .show()
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

        val fish = FishCatch(
            species = speciesId,
            latitude = point.latitude,
            longitude = point.longitude,
            caughtAt = System.currentTimeMillis()
        )

        val id = db.fishCatchDao().insert(fish)
        val fishWithId = fish.copy(id = id)

        onCatchAdded(fishWithId)
    }
}
