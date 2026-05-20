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
    private val onDataChanged: () -> Unit
) {

    fun openSettings() {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Asetukset")
            .setItems(arrayOf("Tiedonsiirto")) { _, which ->
                when (which) {
                    0 -> openDataTransferSettings()
                }
            }
            .show()
        dialog.enlargeButtons()
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
    }
}
