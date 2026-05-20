package fi.anssi.kalakartta.ui

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
        val names = speciesList.map { it.name }.toTypedArray()

        if (names.isEmpty()) {
            // Varatoimenpide jos tietokanta on tyhjä
            val fallbacks = listOf(
                Pair("PERCH", "Ahven"),
                Pair("PIKE", "Hauki"),
                Pair("ZANDER", "Kuha")
            )
            val fallbackNames = fallbacks.map { it.second }.toTypedArray()
            
            AlertDialog.Builder(activity)
                .setTitle("Valitse kalalaji")
                .setItems(fallbackNames) { _, which ->
                    val selected = fallbacks[which]
                    addCatchAtSelectedLocation(selected.first, selected.second)
                }
                .show()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Valitse kalalaji")
            .setItems(names) { _, which ->
                val selected = speciesList[which]
                addCatchAtSelectedLocation(selected.id, selected.name)
            }
            .show()
    }

    private fun addCatchAtSelectedLocation(speciesId: String, speciesName: String) {
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
