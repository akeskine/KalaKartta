package fi.anssi.kalakartta.ui

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.io.ImportExportManager
import fi.anssi.kalakartta.utils.enlargeButtons

class SettingsManager(
    private val activity: AppCompatActivity,
    private val db: AppDatabase,
    private val importExportManager: ImportExportManager,
    private val onWeatherSettingsChanged: (Boolean) -> Unit = {},
    private val onDataChanged: () -> Unit
) {

    fun openSettings() {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Asetukset")
            .setItems(arrayOf("Tiedonsiirto", "Tiedon suodatus", "Sää")) { _, which ->
                when (which) {
                    0 -> openDataTransferSettings()
                    1 -> openFilterSettings()
                    2 -> openWeatherSettings()
                }
            }
            .show()
        dialog.enlargeButtons()
    }

    private fun openWeatherSettings() {
        val prefs = activity.getSharedPreferences("settings", AppCompatActivity.MODE_PRIVATE)
        val isEnabledInitial = prefs.getBoolean("weather_enabled", true)
        var isEnabledCurrent = isEnabledInitial
        
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Sääasetukset")
            .setMultiChoiceItems(arrayOf("Säädatan automaattinen haku"), booleanArrayOf(isEnabledInitial)) { _, _, isChecked ->
                isEnabledCurrent = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                if (isEnabledCurrent != isEnabledInitial) {
                    prefs.edit().putBoolean("weather_enabled", isEnabledCurrent).apply()
                    onWeatherSettingsChanged(isEnabledCurrent)
                }
            }
            .setNegativeButton("Peruuta", null)
            .show()
        dialog.enlargeButtons()
    }

    private fun openFilterSettings() {
        val intent = android.content.Intent(activity, FilterActivity::class.java)
        activity.startActivityForResult(intent, 1002)
    }

    private fun openDataTransferSettings() {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Tiedonsiirto")
            .setItems(arrayOf("Vie tiedot", "Tuo tiedot", "Poista kaikki pisteet")) { _, which ->
                when (which) {
                    0 -> importExportManager.launchExport()
                    1 -> importExportManager.launchImport()
                    2 -> confirmDeleteAllCatches()
                }
            }
            .show()
        dialog.enlargeButtons()
    }

    private fun confirmDeleteAllCatches() {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Poista kaikki pisteet?")
            .setMessage("Haluatko varmasti poistaa kaikki tallennetut kalamerkit? Tätä toimintoa ei voi kumota.")
            .setPositiveButton("Poista kaikki") { _, _ ->
                deleteAllCatches()
            }
            .setNegativeButton("Peruuta", null)
            .show()

        dialog.enlargeButtons()
    }

    private fun deleteAllCatches() {
        db.fishCatchDao().deleteAll()
        onDataChanged()

        val dialog = AlertDialog.Builder(activity)
            .setMessage("Kaikki pisteet poistettu.")
            .setPositiveButton("OK", null)
            .show()
        dialog.enlargeButtons()
    }
}
