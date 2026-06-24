package fi.anssi.kalakartta.io

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.JsonService
import fi.anssi.kalakartta.utils.enlargeButtons

class ImportExportManager(
    private val activity: ComponentActivity,
    private val db: AppDatabase,
    private val onImportDone: () -> Unit
) {
    private val jsonService = JsonService()

    private val exportLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportToJson(it) }
    }

    private val importLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFromJson(it) }
    }

    fun launchExport() {
        exportLauncher.launch("kalakartta.json")
    }

    fun launchImport() {
        importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
    }

    private fun exportToJson(uri: Uri) {
        Thread {
            val catches = db.fishCatchDao().getAll()
            val places = db.placeOfInterestDao().getAll()
            jsonService.export(activity.contentResolver, uri, catches, places)
            showConfirmationDialog("Tietojen vienti valmis (${catches.size} kalaa, ${places.size} muuta paikkaa).")
        }.start()
    }

    private fun importFromJson(uri: Uri) {
        Thread {
            try {
                val (catches, places) = jsonService.import(activity.contentResolver, uri)
                if (catches.isEmpty() && places.isEmpty()) {
                    activity.runOnUiThread {
                        showConfirmationDialog("Tiedostosta ei löytynyt tuotavia tietoja tai se on virheellinen.")
                    }
                    return@Thread
                }
                
                if (catches.isNotEmpty()) {
                    db.fishCatchDao().insertAll(catches)
                }
                if (places.isNotEmpty()) {
                    db.placeOfInterestDao().insertAll(places)
                }

                activity.runOnUiThread {
                    onImportDone()
                    showConfirmationDialog("Tietojen tuonti valmis (${catches.size} kalaa, ${places.size} muuta paikkaa).")
                }
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Import failed", e)
                activity.runOnUiThread {
                    showConfirmationDialog("Tietojen tuonti epäonnistui: ${e.message}")
                }
            }
        }.start()
    }

    private fun showConfirmationDialog(message: String) {
        activity.runOnUiThread {
            val dialog = AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
            dialog.enlargeButtons()
        }
    }
}
