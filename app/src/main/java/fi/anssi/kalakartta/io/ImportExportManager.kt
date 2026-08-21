package fi.anssi.kalakartta.io

import android.view.View
import android.net.Uri
import android.location.Location
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.MediaService
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.data.JsonService
import fi.anssi.kalakartta.utils.enlargeButtons
import java.text.SimpleDateFormat
import java.util.*

class ImportExportManager(
    private val activity: ComponentActivity,
    private val db: AppDatabase,
    private val onImportDone: (forceRefreshSpecies: Boolean) -> Unit
) {
    private val jsonService = JsonService()
    private val mediaService = MediaService(activity)

    private var pendingExportCatches: List<FishCatch>? = null
    private var pendingExportPlaces: List<PlaceOfInterest>? = null

    private val exportLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportToJson(it, pendingExportCatches, pendingExportPlaces) }
        pendingExportCatches = null
        pendingExportPlaces = null
    }

    private val exportSpeciesLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportSpeciesToJson(it) }
    }

    private val exportRoutesLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportRoutesToJson(it) }
    }

    private val importLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFromJson(it) }
    }

    private val importSpeciesLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importSpeciesFromJson(it) }
    }

    private val importRoutesLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importRoutesFromJson(it) }
    }

    private val exportMediaLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { exportMediaToZip(it) }
    }

    private val importMediaLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importMediaFromZip(it) }
    }

    fun launchExport(catches: List<FishCatch>? = null, places: List<PlaceOfInterest>? = null) {
        pendingExportCatches = catches
        pendingExportPlaces = places
        exportLauncher.launch("kalakartta.json")
    }

    fun launchExportSpecies() {
        exportSpeciesLauncher.launch("kalalajit.json")
    }

    fun launchImport() {
        importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
    }

    fun launchImportSpecies() {
        importSpeciesLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
    }

    fun launchExportRoutes() {
        exportRoutesLauncher.launch("reitit.json")
    }

    fun launchImportRoutes() {
        importRoutesLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
    }

    fun launchExportMedia() {
        exportMediaLauncher.launch("kalakartta-media-${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.zip")
    }

    fun launchImportMedia() {
        importMediaLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    fun launchDeleteAllMedia() {
        AlertDialog.Builder(activity)
            .setTitle("Poista kaikki media")
            .setMessage("Haluatko varmasti poistaa KAIKKI mediatiedostot ja niiden metatiedot? Tätä toimintoa ei voi peruuttaa.")
            .setPositiveButton("Poista kaikki") { _, _ ->
                Thread {
                    mediaService.deleteAllMedia()
                    showConfirmationDialog("Kaikki mediatiedostot poistettu.")
                }.start()
            }
            .setNegativeButton("Peruuta", null)
            .show()
    }

    private fun exportToJson(uri: Uri, manualCatches: List<FishCatch>? = null, manualPlaces: List<PlaceOfInterest>? = null) {
        Thread {
            val catches = manualCatches ?: db.fishCatchDao().getAll()
            val places = manualPlaces ?: db.placeOfInterestDao().getAll()
            jsonService.export(activity.contentResolver, uri, catches, places)
            showConfirmationDialog("Tietojen vienti valmis (${catches.size} kalaa, ${places.size} muuta paikkaa).")
        }.start()
    }

    private fun exportSpeciesToJson(uri: Uri) {
        Thread {
            try {
                val species = db.fishSpeciesDao().getAll()
                jsonService.exportSpecies(activity.contentResolver, uri, species, activity.filesDir)
                showConfirmationDialog("Kalalajien asetusten vienti valmis (${species.size} lajia).")
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Species export failed", e)
                activity.runOnUiThread {
                    showConfirmationDialog("Kalalajien asetusten vienti epäonnistui: ${e.message}")
                }
            }
        }.start()
    }

    private fun exportRoutesToJson(uri: Uri) {
        Thread {
            try {
                val sessions = db.fishingSessionDao().getAll()
                jsonService.exportRoutes(activity.contentResolver, uri, sessions) { sessionId ->
                    db.trackPointDao().getPointsForSession(sessionId)
                }
                showConfirmationDialog("Reittien vienti valmis (${sessions.size} reittiä).")
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Routes export failed", e)
                activity.runOnUiThread {
                    showConfirmationDialog("Reittien vienti epäonnistui: ${e.message}")
                }
            }
        }.start()
    }

    private fun importRoutesFromJson(uri: Uri) {
        Thread {
            try {
                activity.runOnUiThread {
                    val progressLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(50, 40, 50, 10)
                    }

                    val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = 0
                        isIndeterminate = true
                    }

                    val progressText = TextView(activity).apply {
                        text = "Tuodaan reittejä..."
                        textSize = 18f
                        setPadding(0, 0, 0, 20)
                    }

                    progressLayout.addView(progressText)
                    progressLayout.addView(progressBar)

                    val progressDialog = AlertDialog.Builder(activity)
                        .setTitle("Tuodaan reittejä")
                        .setView(progressLayout)
                        .setCancelable(false)
                        .create()

                    progressDialog.show()

                    Thread {
                        try {
                            var currentSessionId = -1L
                            var currentSessionOriginalStart = -1L
                            var importedSessionsCount = 0
                            
                            jsonService.importRoutesStream(activity.contentResolver, uri) { session, points ->
                                // If it's a new session or the first one
                                if (session.startedAt != currentSessionOriginalStart) {
                                    currentSessionId = db.fishingSessionDao().insert(session)
                                    currentSessionOriginalStart = session.startedAt
                                    importedSessionsCount++
                                    
                                    activity.runOnUiThread {
                                        progressText.text = "Tuodaan reittejä: $importedSessionsCount istuntoa"
                                    }
                                }
                                
                                if (points.isNotEmpty()) {
                                    val pointsToInsert = points.map { it.copy(fishingSessionId = currentSessionId) }
                                    db.trackPointDao().insertAll(pointsToInsert)
                                }
                            }

                            activity.runOnUiThread {
                                progressDialog.dismiss()
                                onImportDone(false)
                                if (importedSessionsCount > 0) {
                                    showConfirmationDialog("Reittien tuonti valmis ($importedSessionsCount reittiä).")
                                } else {
                                    showConfirmationDialog("Tiedostosta ei löytynyt tuotavia reittejä.")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ImportExportManager", "Routes import processing failed", e)
                            activity.runOnUiThread {
                                progressDialog.dismiss()
                                showConfirmationDialog("Reittien tuonti epäonnistui: ${e.message}")
                            }
                        }
                    }.start()
                }
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Routes import failed", e)
                activity.runOnUiThread {
                    showConfirmationDialog("Reittien tuonti epäonnistui: ${e.message}")
                }
            }
        }.start()
    }

    private fun importFromJson(uri: Uri) {
        Thread {
            try {
                val (importedCatches, importedPlaces) = jsonService.import(activity.contentResolver, uri)
                if (importedCatches.isEmpty() && importedPlaces.isEmpty()) {
                    activity.runOnUiThread {
                        showConfirmationDialog("Tiedostosta ei löytynyt tuotavia tietoja tai se on virheellinen.")
                    }
                    return@Thread
                }

                val currentCatches = db.fishCatchDao().getAll()
                val currentPlaces = db.placeOfInterestDao().getAll()

                val totalToCompare = importedCatches.size + importedPlaces.size
                
                activity.runOnUiThread {
                    val progressLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(50, 40, 50, 10)
                    }
                    
                    val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = 0
                    }
                    
                    val progressText = TextView(activity).apply {
                        text = "Tarkastetaan duplikaatteja: 0%"
                        textSize = 18f
                        setPadding(0, 0, 0, 20)
                    }
                    
                    progressLayout.addView(progressText)
                    progressLayout.addView(progressBar)
                    
                    val progressDialog = AlertDialog.Builder(activity)
                        .setTitle("Tarkastetaan duplikaatteja...")
                        .setView(progressLayout)
                        .setCancelable(false)
                        .create()
                        
                    progressDialog.show()
                    
                    Thread {
                        val duplicateCatches = findDuplicateCatches(importedCatches, currentCatches) { progress ->
                            activity.runOnUiThread {
                                val progressValue = (progress * 100) / totalToCompare
                                progressBar.progress = progressValue
                                progressText.text = "Tarkastetaan duplikaatteja: $progressValue%"
                            }
                        }
                        
                        val duplicatePlaces = findDuplicatePlaces(importedPlaces, currentPlaces, importedCatches.size) { progress ->
                            activity.runOnUiThread {
                                val progressValue = (progress * 100) / totalToCompare
                                progressBar.progress = progressValue
                                progressText.text = "Tarkastetaan duplikaatteja: $progressValue%"
                            }
                        }

                        val totalImported = importedCatches.size + importedPlaces.size
                        val totalDuplicates = duplicateCatches.size + duplicatePlaces.size

                        activity.runOnUiThread {
                            progressDialog.dismiss()
                            if (totalDuplicates > 0) {
                                showImportConflictDialog(
                                    totalImported,
                                    totalDuplicates,
                                    importedCatches,
                                    importedPlaces,
                                    duplicateCatches,
                                    duplicatePlaces
                                )
                            } else {
                                processImport(importedCatches, importedPlaces, emptyList(), emptyList(), 0)
                            }
                        }
                    }.start()
                }
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Import failed", e)
                activity.runOnUiThread {
                    showConfirmationDialog("Tietojen tuonti epäonnistui: ${e.message}")
                }
            }
        }.start()
    }

    private fun findDuplicateCatches(imported: List<FishCatch>, current: List<FishCatch>, onProgress: (Int) -> Unit): List<Pair<FishCatch, FishCatch?>> {
        val results = mutableListOf<Pair<FishCatch, FishCatch?>>()

        for ((index, imp) in imported.withIndex()) {
            // Tarkista nykyiset
            val matchInCurrent = current.find { curr ->
                calculateDistance(imp.latitude, imp.longitude, curr.latitude, curr.longitude) <= 2.0
            }

            if (matchInCurrent != null) {
                results.add(imp to matchInCurrent)
            }
            onProgress(index + 1)
        }
        return results
    }

    private fun findDuplicatePlaces(imported: List<PlaceOfInterest>, current: List<PlaceOfInterest>, offset: Int, onProgress: (Int) -> Unit): List<Pair<PlaceOfInterest, PlaceOfInterest?>> {
        val results = mutableListOf<Pair<PlaceOfInterest, PlaceOfInterest?>>()

        for ((index, imp) in imported.withIndex()) {
            val matchInCurrent = current.find { curr ->
                calculateDistance(imp.latitude, imp.longitude, curr.latitude, curr.longitude) <= 2.0
            }

            if (matchInCurrent != null) {
                results.add(imp to matchInCurrent)
            }
            onProgress(offset + index + 1)
        }
        return results
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun showImportConflictDialog(
        totalImported: Int,
        totalDuplicates: Int,
        importedCatches: List<FishCatch>,
        importedPlaces: List<PlaceOfInterest>,
        duplicateCatches: List<Pair<FishCatch, FishCatch?>>,
        duplicatePlaces: List<Pair<PlaceOfInterest, PlaceOfInterest?>>
    ) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }

        val messageView = TextView(activity).apply {
            text = "Tuodussa aineistossa on $totalImported pistettä. Näistä $totalDuplicates vastaa koordinaattiensa perusteella sovelluksessa jo olevia tai tuontitiedostossa keskenään päällekkäisiä pisteitä.\n\nValitse, miten päällekkäiset pisteet käsitellään:"
            textSize = 16f
            setTextColor(activity.resources.getColor(android.R.color.primary_text_light))
        }
        layout.addView(messageView)

        val radioGroup = RadioGroup(activity).apply {
            setPadding(0, 20, 0, 0)
        }

        val rbSkip = RadioButton(activity).apply {
            text = "Ohita päällekkäiset tuontipisteet"
            id = View.generateViewId()
        }
        val rbReplaceId = View.generateViewId()
        val rbReplace = RadioButton(activity).apply {
            text = "Korvaa nykyiset pisteet tuoduilla pisteillä"
            id = rbReplaceId
        }
        val rbAllId = View.generateViewId()
        val rbAll = RadioButton(activity).apply {
            text = "Tuo kaikki pisteet ja säilytä myös nykyiset"
            id = rbAllId
        }

        radioGroup.addView(rbSkip)
        radioGroup.addView(rbReplace)
        radioGroup.addView(rbAll)
        rbSkip.isChecked = true

        layout.addView(radioGroup)

        val dialog = AlertDialog.Builder(activity)
            .setView(layout)
            .setPositiveButton("Jatka") { _, _ ->
                val mode = when (radioGroup.checkedRadioButtonId) {
                    rbReplaceId -> 1 // Replace
                    rbAllId -> 2 // All
                    else -> 0 // Skip
                }
                Thread {
                    processImport(importedCatches, importedPlaces, duplicateCatches, duplicatePlaces, mode)
                }.start()
            }
            .setNegativeButton("Peruuta", null)
            .show()
        dialog.enlargeButtons()
    }

    private fun processImport(
        importedCatches: List<FishCatch>,
        importedPlaces: List<PlaceOfInterest>,
        duplicateCatches: List<Pair<FishCatch, FishCatch?>>,
        duplicatePlaces: List<Pair<PlaceOfInterest, PlaceOfInterest?>>,
        mode: Int // 0: Skip, 1: Replace, 2: All
    ) {
        val totalToProcess = importedCatches.size + importedPlaces.size
        
        activity.runOnUiThread {
            val progressLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
            }
            
            val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 0
            }
            
            val progressText = TextView(activity).apply {
                text = "Tuodaan pisteitä: 0%"
                textSize = 18f
                setPadding(0, 0, 0, 20)
            }
            
            progressLayout.addView(progressText)
            progressLayout.addView(progressBar)
            
            val progressDialog = AlertDialog.Builder(activity)
                .setTitle("Tuodaan pisteitä")
                .setView(progressLayout)
                .setCancelable(false)
                .create()
                
            progressDialog.show()
            
            Thread {
                try {
                    val duplicateImportedCatches = duplicateCatches.map { it.first }.toSet()
                    val duplicateImportedPlaces = duplicatePlaces.map { it.first }.toSet()

                    val catchesToInsert = mutableListOf<FishCatch>()
                    val placesToInsert = mutableListOf<PlaceOfInterest>()

                    when (mode) {
                        0 -> { // Skip
                            catchesToInsert.addAll(importedCatches.filter { it !in duplicateImportedCatches })
                            placesToInsert.addAll(importedPlaces.filter { it !in duplicateImportedPlaces })
                        }
                        1 -> { // Replace
                            // Poista olemassa olevat päällekkäiset
                            duplicateCatches.forEach { (_, existing) ->
                                existing?.let { db.fishCatchDao().deleteById(it.id) }
                            }
                            duplicatePlaces.forEach { (_, existing) ->
                                existing?.let { db.placeOfInterestDao().deleteById(it.id) }
                            }
                            catchesToInsert.addAll(importedCatches)
                            placesToInsert.addAll(importedPlaces)
                        }
                        2 -> { // All
                            catchesToInsert.addAll(importedCatches)
                            placesToInsert.addAll(importedPlaces)
                        }
                    }

                    // Varmista uudet ID:t asettamalla ne nollaksi ja tallenna yksitellen tai erissä edistymisen näyttämiseksi
                    var processedCount = 0
                    
                    catchesToInsert.forEach { fishCatch ->
                        db.fishCatchDao().insertAll(listOf(fishCatch.copy(id = 0)))
                        processedCount++
                        val progressValue = (processedCount * 100) / totalToProcess
                        activity.runOnUiThread {
                            progressBar.progress = progressValue
                            progressText.text = "Tuodaan pisteitä: $progressValue%"
                        }
                    }
                    
                    placesToInsert.forEach { place ->
                        db.placeOfInterestDao().insertAll(listOf(place.copy(id = 0)))
                        processedCount++
                        val progressValue = (processedCount * 100) / totalToProcess
                        activity.runOnUiThread {
                            progressBar.progress = progressValue
                            progressText.text = "Tuodaan pisteitä: $progressValue%"
                        }
                    }

                    activity.runOnUiThread {
                        progressDialog.dismiss()
                        onImportDone(false)
                        showConfirmationDialog("Tietojen tuonti valmis (${catchesToInsert.size} kalaa, ${placesToInsert.size} muuta paikkaa).")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ImportExportManager", "Import processing failed", e)
                    activity.runOnUiThread {
                        progressDialog.dismiss()
                        showConfirmationDialog("Tietojen tuonti epäonnistui: ${e.message}")
                    }
                }
            }.start()
        }
    }

    private fun importSpeciesFromJson(uri: Uri) {
        Thread {
            try {
                val speciesList = jsonService.importSpecies(activity.contentResolver, uri, activity.filesDir)
                if (speciesList.isEmpty()) {
                    activity.runOnUiThread {
                        showConfirmationDialog("Tiedostosta ei löytynyt tuotavia kalalajeja tai se on virheellinen.")
                    }
                    return@Thread
                }

                db.fishSpeciesDao().deleteAll()
                db.fishSpeciesDao().insertAll(speciesList)

                activity.runOnUiThread {
                    onImportDone(true)
                    showConfirmationDialog("Kalalajien asetusten tuonti valmis (${speciesList.size} lajia).")
                }
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Species import failed", e)
                activity.runOnUiThread {
                    showConfirmationDialog("Kalalajien asetusten tuonti epäonnistui: ${e.message}")
                }
            }
        }.start()
    }

    private fun exportMediaToZip(uri: Uri) {
        Thread {
            try {
                activity.contentResolver.openOutputStream(uri)?.use { output ->
                    mediaService.exportMedia(output)
                }
                showConfirmationDialog("Median vienti valmis.")
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Media export failed", e)
                activity.runOnUiThread {
                    showConfirmationDialog("Median vienti epäonnistui: ${e.message}")
                }
            }
        }.start()
    }

    private fun importMediaFromZip(uri: Uri) {
        val progressLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isIndeterminate = false
            max = 100
        }
        
        val progressText = TextView(activity).apply {
            text = "Tuodaan mediaa..."
            setPadding(0, 0, 0, 20)
        }
        
        progressLayout.addView(progressText)
        progressLayout.addView(progressBar)
        
        val progressDialog = AlertDialog.Builder(activity)
            .setTitle("Tuodaan mediaa")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
            
        progressDialog.show()

        Thread {
            try {
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    mediaService.importMedia(input) { current, total ->
                        activity.runOnUiThread {
                            val progressValue = (current * 100) / total
                            progressBar.progress = progressValue
                            progressText.text = "Tuodaan mediaa: $progressValue% ($current/$total)"
                        }
                    }
                }
                activity.runOnUiThread {
                    progressDialog.dismiss()
                    onImportDone(false)
                    showConfirmationDialog("Median tuonti valmis.")
                }
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Media import failed", e)
                activity.runOnUiThread {
                    progressDialog.dismiss()
                    showConfirmationDialog("Median tuonti epäonnistui: ${e.message}")
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
