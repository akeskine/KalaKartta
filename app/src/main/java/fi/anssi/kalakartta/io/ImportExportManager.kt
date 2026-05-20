package fi.anssi.kalakartta.io

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.JsonService

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
            val list = db.fishCatchDao().getAll()
            jsonService.export(activity.contentResolver, uri, list)
        }.start()
    }

    private fun importFromJson(uri: Uri) {
        Thread {
            val list = jsonService.import(activity.contentResolver, uri)
            val dao = db.fishCatchDao()
            list.forEach { dao.insert(it) }

            activity.runOnUiThread {
                onImportDone()
            }
        }.start()
    }
}
