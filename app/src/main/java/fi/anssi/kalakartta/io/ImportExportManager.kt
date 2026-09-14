package fi.anssi.kalakartta.io

import android.view.View
import android.net.Uri
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.MediaService
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.data.JsonService
import fi.anssi.kalakartta.utils.enlargeButtons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

    private val exportDiaryLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportDiaryToJson(it) }
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

    private val importDiaryLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importDiaryFromJson(it) }
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

    fun launchExportDiary() {
        exportDiaryLauncher.launch("paivakirja.json")
    }

    fun launchImportDiary() {
        importDiaryLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
    }

    fun launchExportMedia() {
        exportMediaLauncher.launch("kalakartta-media-${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.zip")
    }

    fun launchImportMedia() {
        importMediaLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    private val exportAllLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { exportAllToZip(it) }
    }

    private val importAllLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importAllFromZip(it) }
    }

    fun launchExportAll() {
        exportAllLauncher.launch("kalakartta-kaikki-${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.zip")
    }

    fun launchImportAll() {
        importAllLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    fun launchDeleteAllData() {
        AlertDialog.Builder(activity)
            .setTitle("Poista kaikki tiedot")
            .setMessage("Haluatko varmasti poistaa KAIKKI tiedot (pisteet, reitit, mediat, kalapäiväkirjan ja asetukset)? Tätä toimintoa ei voi peruuttaa.")
            .setPositiveButton("Poista kaikki") { _, _ ->
                activity.lifecycleScope.launch {
                    if (activity.isFinishing || activity.isDestroyed) return@launch

                    try {
                        withContext(Dispatchers.IO) {
                            // 1. Poistetaan mediatiedostot levyltä
                            mediaService.deleteAllMedia()

                            // 2. Tyhjennetään kaikki tietokantataulut
                            db.clearAllTables()
                            db.weatherErrorDao().deleteAll()

                            // 3. Palautetaan oletusasetukset
                            db.initializeDefaults()

                            // 4. Pienennetään tietokantatiedostoa (VACUUM)
                            try {
                                db.openHelper.writableDatabase.execSQL("VACUUM")
                            } catch (e: Exception) {
                                android.util.Log.e("ImportExportManager", "VACUUM failed", e)
                            }
                        }

                        if (activity.isFinishing || activity.isDestroyed) return@launch
                        onImportDone(true) // Päivittää UI:n
                        showConfirmationDialog("Kaikki tiedot poistettu.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("ImportExportManager", "Delete all failed", e)
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            showConfirmationDialog("Poisto epäonnistui: ${e.message}")
                        }
                    }
                }
            }
            .setNegativeButton("Peruuta", null)
            .show()
    }

    fun launchDeleteMediaData() {
        AlertDialog.Builder(activity)
            .setTitle("Poista media?")
            .setMessage("Haluatko varmasti poistaa kaikki mediatiedostot? Kalapisteet, reitit, päiväkirja ja muut tiedot säilytetään.")
            .setPositiveButton("Takaisin", null)
            .setNegativeButton("Poista") { _, _ ->
                activity.lifecycleScope.launch {
                    if (activity.isFinishing || activity.isDestroyed) return@launch

                    try {
                        withContext(Dispatchers.IO) {
                            mediaService.deleteAllMedia()
                        }
                        if (activity.isFinishing || activity.isDestroyed) return@launch
                        onImportDone(false)
                        showConfirmationDialog("Media poistettu.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("ImportExportManager", "Delete media failed", e)
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            showConfirmationDialog("Median poisto epäonnistui: ${e.message}")
                        }
                    }
                }
            }
            .show()
    }

    fun launchDeleteDiaryData() {
        AlertDialog.Builder(activity)
            .setTitle("Poista kalapäiväkirja?")
            .setMessage("Haluatko varmasti poistaa kaikki kalapäiväkirjan merkinnät? Tätä toimintoa ei voi kumota.")
            .setPositiveButton("Takaisin", null)
            .setNegativeButton("Poista") { _, _ ->
                activity.lifecycleScope.launch {
                    if (activity.isFinishing || activity.isDestroyed) return@launch

                    try {
                        withContext(Dispatchers.IO) {
                            db.fishDiaryPageDao().getAll().forEach { db.fishDiaryPageDao().delete(it) }
                        }
                        if (activity.isFinishing || activity.isDestroyed) return@launch
                        onImportDone(false)
                        showConfirmationDialog("Kalapäiväkirja tyhjennetty.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("ImportExportManager", "Delete diary failed", e)
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            showConfirmationDialog("Poisto epäonnistui: ${e.message}")
                        }
                    }
                }
            }
            .show()
    }

    private fun exportAllToZip(uri: Uri) {
        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed) return@launch

            try {
                withContext(Dispatchers.IO) {
                    val outputStream = activity.contentResolver.openOutputStream(uri)
                        ?: throw java.io.IOException("Vientitiedostoa ei voitu avata kirjoittamista varten")
                    outputStream.use { output ->
                        val zipOut = java.util.zip.ZipOutputStream(output)

                        // 1. pisteet.json
                        val catches = db.fishCatchDao().getAll()
                        val places = db.placeOfInterestDao().getAll()
                        val catchesAndPlacesJson = jsonService.exportCatchesAndPlaces(catches, places)
                        zipOut.putNextEntry(java.util.zip.ZipEntry("pisteet.json"))
                        zipOut.write(catchesAndPlacesJson.toString(4).toByteArray())
                        zipOut.closeEntry()

                        // 2. sessiot.json (reitit)
                        val sessions = db.fishingSessionDao().getAll()
                        zipOut.putNextEntry(java.util.zip.ZipEntry("sessiot.json"))
                        val writer = android.util.JsonWriter(zipOut.bufferedWriter())
                        jsonService.writeRoutesToWriter(writer, sessions) { sessionId ->
                            db.trackPointDao().getPointsForSession(sessionId)
                        }
                        writer.flush()
                        zipOut.closeEntry()

                        // 2.5 paivakirja.json
                        val diaryPages = db.fishDiaryPageDao().getAll()
                        if (diaryPages.isNotEmpty()) {
                            zipOut.putNextEntry(java.util.zip.ZipEntry("paivakirja.json"))
                            val diaryJson = JSONObject()
                            val diaryArray = JSONArray()
                            diaryPages.forEach { page ->
                                val obj = JSONObject()
                                obj.put("id", page.id)
                                obj.put("startDate", jsonService.isoFormat.format(java.util.Date(page.startDate)))
                                if (page.endDate != null) {
                                    obj.put("endDate", jsonService.isoFormat.format(java.util.Date(page.endDate)))
                                }
                                obj.put("location", page.location)
                                obj.put("fishingMethod", page.fishingMethod)
                                obj.put("catch", page.catch)
                                obj.put("story", page.story)
                                diaryArray.put(obj)
                            }
                            diaryJson.put("diaryPages", diaryArray)
                            zipOut.write(diaryJson.toString(4).toByteArray())
                            zipOut.closeEntry()
                        }

                        // 3. kalalajit.json
                        val species = db.fishSpeciesDao().getAll()
                        val speciesJson = jsonService.exportSpeciesToJsonObject(species, activity.filesDir)
                        zipOut.putNextEntry(java.util.zip.ZipEntry("kalalajit.json"))
                        zipOut.write(speciesJson.toString(4).toByteArray())
                        zipOut.closeEntry()

                        // 4. media.json ja media/ kansio
                        mediaService.exportMediaToZip(zipOut)

                        zipOut.close()
                    }
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                showConfirmationDialog("Kaikkien tietojen vienti valmis.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Export all failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showConfirmationDialog("Vienti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun importAllFromZip(uri: Uri) {
        if (activity.isFinishing || activity.isDestroyed) return

        val progressLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isIndeterminate = false
            max = 100
            progress = 0
        }
        
        val progressText = TextView(activity).apply {
            text = "Valmistellaan tuontia... 0 %"
            setPadding(0, 0, 0, 20)
        }
        
        progressLayout.addView(progressText)
        progressLayout.addView(progressBar)
        
        val progressDialog = AlertDialog.Builder(activity)
            .setTitle("Tuodaan kaikkia tietoja")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
            
        progressDialog.show()

        var currentProgressStage = "Valmistellaan tuontia..."
        var currentStageProgress = 0
        var lastOverallProgress = 0

        fun updateProgress(
            stage: String? = null,
            stageProgress: Int = currentStageProgress,
            overallProgress: Int = lastOverallProgress
        ) {
            stage?.let { currentProgressStage = it }
            currentStageProgress = stageProgress.coerceIn(0, 100)
            lastOverallProgress = maxOf(lastOverallProgress, overallProgress.coerceIn(0, 100))
            val displayedStageProgress = currentStageProgress
            val displayedOverallProgress = lastOverallProgress
            val displayedText = "$currentProgressStage $displayedStageProgress %"

            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        progressBar.progress = displayedOverallProgress
                        progressText.text = displayedText
                    }
                }
            }
        }

        activity.lifecycleScope.launch {
            var tempZipFile: java.io.File? = null
            var importedCatches = 0
            var importedPlaces = 0
            var importedSessions = 0
            var importedTrackPoints = 0
            var importedMedia = 0
            var importedSpecies = 0
            var importedDiaryPages = 0

            try {
                withContext(Dispatchers.IO) {
                    tempZipFile = java.io.File.createTempFile("kalakartta-import-", ".zip", activity.cacheDir)
                    val zipFile = tempZipFile ?: throw java.io.IOException("Väliaikaista zip-tiedostoa ei voitu luoda")

                val totalSize = try {
                    activity.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                } catch (e: Exception) {
                    -1L
                }

                // 1. Vaihe: "Ladataan"
                updateProgress("Ladataan zip-tiedostoa...", 0, 0)

                val input = activity.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Zip-tiedostoa ei voitu avata")
                input.use { source ->
                    java.io.FileOutputStream(zipFile).use { target ->
                        val buffer = ByteArray(8192)
                        var bytesRead = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) break
                            target.write(buffer, 0, read)
                            bytesRead += read

                            val stageProgress = if (totalSize > 0) {
                                (bytesRead * 100 / totalSize).toInt()
                            } else {
                                // Joidenkin URI-lähteiden kokoa ei voi selvittää etukäteen.
                                (bytesRead / (1024 * 1024)).toInt().coerceAtMost(99)
                            }.coerceIn(0, 100)
                            val overallProgress = stageProgress * 30 / 100
                            updateProgress(
                                stageProgress = stageProgress,
                                overallProgress = overallProgress
                            )
                        }
                    }
                }

                updateProgress(stageProgress = 100, overallProgress = 30)

                // 2. Vaihe: "Puretaan"
                updateProgress("Puretaan zip-tiedostoa...", 0, 30)

                    val tempMediaFiles = mutableMapOf<String, ByteArray>()
                    var mediaJsonStr: String? = null

                val archiveSize = zipFile.length()
                java.io.FileInputStream(zipFile).use { archiveInput ->
                    var archiveBytesRead = 0L
                    var extractionProgress = 0

                    fun updateExtractionProgress() {
                        val rawProgress = if (archiveSize > 0) {
                            (archiveBytesRead * 100 / archiveSize).toInt()
                        } else {
                            0
                        }
                        extractionProgress = rawProgress.coerceIn(0, 100)
                        updateProgress(
                            stageProgress = extractionProgress,
                            overallProgress = 30 + extractionProgress * 40 / 100
                        )
                    }

                    val wrappedInput = object : java.io.FilterInputStream(archiveInput) {
                        override fun read(): Int {
                            val b = super.read()
                            if (b != -1) {
                                archiveBytesRead++
                                updateExtractionProgress()
                            }
                            return b
                        }

                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            val n = super.read(b, off, len)
                            if (n != -1) {
                                archiveBytesRead += n
                                updateExtractionProgress()
                            }
                            return n
                        }
                    }

                    val zipIn = java.util.zip.ZipInputStream(wrappedInput)
                    var entry = zipIn.nextEntry

                    while (entry != null) {
                        val entryName = entry.name
                        updateProgress(
                            "Puretaan zip-tiedostoa...",
                            extractionProgress,
                            30 + extractionProgress * 40 / 100
                        )
                        when (entryName) {
                            "pisteet.json" -> {
                                updateProgress(
                                    "Populoidaan tietokanta (pisteet)...",
                                    extractionProgress,
                                    30 + extractionProgress * 40 / 100
                                )
                                val text = zipIn.readBytes().toString(Charsets.UTF_8)
                                val (catches, places) = jsonService.parseCatchesAndPlaces(text)
                                catches.forEach { db.fishCatchDao().insertAll(listOf(it.copy(id = 0))) }
                                places.forEach { db.placeOfInterestDao().insertAll(listOf(it.copy(id = 0))) }
                                importedCatches = catches.size
                                importedPlaces = places.size
                            }
                            "sessiot.json" -> {
                                updateProgress(
                                    "Populoidaan tietokanta (reitit)...",
                                    extractionProgress,
                                    30 + extractionProgress * 40 / 100
                                )
                                var currentSessionId = -1L
                                var currentSessionStartedAt = Long.MIN_VALUE
                                jsonService.importRoutesFromStream(zipIn) { session, points ->
                                    if (session.startedAt != currentSessionStartedAt) {
                                        currentSessionId = db.fishingSessionDao().insert(session)
                                        currentSessionStartedAt = session.startedAt
                                        importedSessions++
                                    }

                                    if (points.isNotEmpty()) {
                                        val pointsToInsert = points.map {
                                            it.copy(fishingSessionId = currentSessionId)
                                        }
                                        db.trackPointDao().insertAll(pointsToInsert)
                                        importedTrackPoints += pointsToInsert.size
                                    }
                                }
                            }
                            "kalalajit.json" -> {
                                updateProgress(
                                    "Populoidaan tietokanta (lajit)...",
                                    extractionProgress,
                                    30 + extractionProgress * 40 / 100
                                )
                                val text = zipIn.readBytes().toString(Charsets.UTF_8)
                                val species = jsonService.parseSpecies(text, activity.filesDir)
                                if (species.isNotEmpty()) {
                                    db.fishSpeciesDao().deleteAll()
                                    db.fishSpeciesDao().insertAll(species)
                                    importedSpecies = species.size
                                }
                            }
                            "paivakirja.json" -> {
                                updateProgress(
                                    "Populoidaan tietokanta (päiväkirja)...",
                                    extractionProgress,
                                    30 + extractionProgress * 40 / 100
                                )
                                val text = zipIn.readBytes().toString(Charsets.UTF_8)
                                val data = jsonService.parseImportData(text)
                                data.diaryPages.forEach {
                                    db.fishDiaryPageDao().insert(it.copy(id = 0))
                                    importedDiaryPages++
                                }
                            }
                            "media.json" -> {
                                mediaJsonStr = zipIn.readBytes().toString(Charsets.UTF_8)
                            }
                            else -> {
                                if (entryName.startsWith("media/")) {
                                    val fileName = entryName.substringAfter("media/")
                                    if (fileName.isNotEmpty()) {
                                        tempMediaFiles[fileName] = zipIn.readBytes()
                                    }
                                }
                            }
                        }

                        updateExtractionProgress()

                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                // 3. Vaihe: "Käsitellään mediatiedostot"
                if (mediaJsonStr != null) {
                    updateProgress("Käsitellään mediatiedostoja...", 0, 70)
                    mediaService.processMediaImport(mediaJsonStr!!, tempMediaFiles) { current, total ->
                        importedMedia = total
                        val stageProgress = if (total > 0) current * 100 / total else 100
                        val overallProgress = 70 + stageProgress * 30 / 100
                        updateProgress(
                            stageProgress = stageProgress,
                            overallProgress = overallProgress
                        )
                    }
                    updateProgress(stageProgress = 100, overallProgress = 100)
                } else {
                    updateProgress(stageProgress = 100, overallProgress = 100)
                }

                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                progressDialog.dismiss()
                onImportDone(importedSpecies > 0)
                showConfirmationDialog("Tuonti valmis:\n- $importedCatches kalapistettä\n- $importedPlaces muun paikan pistettä\n- $importedSessions kalastussessiota\n- $importedTrackPoints reittipistettä\n- $importedMedia mediatiedostoa\n- $importedSpecies kalalajia\n- $importedDiaryPages kalapäiväkirjan sivua")
            } catch (e: CancellationException) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog.dismiss()
                }
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Import all failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog.dismiss()
                    showConfirmationDialog("Tuonti epäonnistui: ${e.message}")
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    tempZipFile?.delete()
                }
            }
        }
    }

    private fun exportToJson(uri: Uri, manualCatches: List<FishCatch>? = null, manualPlaces: List<PlaceOfInterest>? = null) {
        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed) return@launch

            try {
                val (catches, places) = withContext(Dispatchers.IO) {
                    val loadedCatches = manualCatches ?: db.fishCatchDao().getAll()
                    val loadedPlaces = manualPlaces ?: db.placeOfInterestDao().getAll()
                    jsonService.export(activity.contentResolver, uri, loadedCatches, loadedPlaces)
                    loadedCatches to loadedPlaces
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                showConfirmationDialog("Tietojen vienti valmis (${catches.size} kalaa, ${places.size} muuta paikkaa).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Export failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showConfirmationDialog("Vienti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun exportDiaryToJson(uri: Uri) {
        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed) return@launch

            try {
                val diaryPages = withContext(Dispatchers.IO) {
                    val loadedPages = db.fishDiaryPageDao().getAll()
                    jsonService.exportDiary(activity.contentResolver, uri, loadedPages)
                    loadedPages
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                showConfirmationDialog("Kalapäiväkirjan vienti valmis (${diaryPages.size} sivua).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Diary export failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showConfirmationDialog("Kalapäiväkirjan vienti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun importDiaryFromJson(uri: Uri) {
        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed) return@launch

            try {
                val importedPages = withContext(Dispatchers.IO) {
                    jsonService.importDiary(activity.contentResolver, uri)
                }
                if (importedPages.isEmpty()) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        showConfirmationDialog("Tiedostosta ei löytynyt tuotavia kalapäiväkirjan sivuja tai se on virheellinen.")
                    }
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    importedPages.forEach { db.fishDiaryPageDao().insert(it.copy(id = 0)) }
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                onImportDone(false)
                showConfirmationDialog("Kalapäiväkirjan tuonti valmis (${importedPages.size} sivua).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Diary import failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showConfirmationDialog("Kalapäiväkirjan tuonti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun exportSpeciesToJson(uri: Uri) {
        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed) return@launch

            try {
                val species = withContext(Dispatchers.IO) {
                    val loadedSpecies = db.fishSpeciesDao().getAll()
                    jsonService.exportSpecies(activity.contentResolver, uri, loadedSpecies, activity.filesDir)
                    loadedSpecies
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                showConfirmationDialog("Kalalajien asetusten vienti valmis (${species.size} lajia).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Species export failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showConfirmationDialog("Kalalajien asetusten vienti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun exportRoutesToJson(uri: Uri) {
        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed) return@launch

            try {
                val sessions = withContext(Dispatchers.IO) {
                    val loadedSessions = db.fishingSessionDao().getAll()
                    jsonService.exportRoutes(activity.contentResolver, uri, loadedSessions) { sessionId ->
                        db.trackPointDao().getPointsForSession(sessionId)
                    }
                    loadedSessions
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                showConfirmationDialog("Reittien vienti valmis (${sessions.size} reittiä).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Routes export failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showConfirmationDialog("Reittien vienti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun importRoutesFromJson(uri: Uri) {
        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed) return@launch

            var progressDialog: AlertDialog? = null
            try {
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

                progressDialog = AlertDialog.Builder(activity)
                    .setTitle("Tuodaan reittejä")
                    .setView(progressLayout)
                    .setCancelable(false)
                    .create()
                progressDialog.show()

                var currentSessionId = -1L
                var currentSessionOriginalStart = -1L
                var importedSessionsCount = 0

                withContext(Dispatchers.IO) {
                    jsonService.importRoutesStream(activity.contentResolver, uri) { session, points ->
                        // A route is represented by one session followed by its point batches.
                        if (session.startedAt != currentSessionOriginalStart) {
                            currentSessionId = db.fishingSessionDao().insert(session)
                            currentSessionOriginalStart = session.startedAt
                            importedSessionsCount++

                            activity.runOnUiThread {
                                if (!activity.isFinishing && !activity.isDestroyed) {
                                    progressText.text = "Tuodaan reittejä: $importedSessionsCount istuntoa"
                                }
                            }
                        }

                        if (points.isNotEmpty()) {
                            val pointsToInsert = points.map { it.copy(fishingSessionId = currentSessionId) }
                            db.trackPointDao().insertAll(pointsToInsert)
                        }
                    }
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch

                progressDialog.dismiss()
                onImportDone(false)
                if (importedSessionsCount > 0) {
                    showConfirmationDialog("Reittien tuonti valmis ($importedSessionsCount kalastussessiota).")
                } else {
                    showConfirmationDialog("Tiedostosta ei löytynyt tuotavia reittejä.")
                }
            } catch (e: CancellationException) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog?.dismiss()
                }
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Routes import failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog?.dismiss()
                    showConfirmationDialog("Reittien tuonti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun importFromJson(uri: Uri) {
        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed) return@launch

            var progressDialog: AlertDialog? = null
            try {
                val (importedCatches, importedPlaces) = withContext(Dispatchers.IO) {
                    jsonService.import(activity.contentResolver, uri)
                }
                if (importedCatches.isEmpty() && importedPlaces.isEmpty()) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        showConfirmationDialog("Tiedostosta ei löytynyt tuotavia tietoja tai se on virheellinen.")
                    }
                    return@launch
                }

                val (currentCatches, currentPlaces) = withContext(Dispatchers.IO) {
                    db.fishCatchDao().getAll() to db.placeOfInterestDao().getAll()
                }

                val totalToCompare = importedCatches.size + importedPlaces.size

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

                progressDialog = AlertDialog.Builder(activity)
                    .setTitle("Tarkastetaan duplikaatteja...")
                    .setView(progressLayout)
                    .setCancelable(false)
                    .create()
                progressDialog.show()

                val duplicateCatches = withContext(Dispatchers.IO) {
                    ImportDuplicateDetector.findDuplicateCatches(importedCatches, currentCatches) { progress ->
                        val progressValue = (progress * 100) / totalToCompare
                        activity.runOnUiThread {
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                progressBar.progress = progressValue
                                progressText.text = "Tarkastetaan duplikaatteja: $progressValue%"
                            }
                        }
                    }
                }

                val duplicatePlaces = withContext(Dispatchers.IO) {
                    ImportDuplicateDetector.findDuplicatePlaces(importedPlaces, currentPlaces, importedCatches.size) { progress ->
                        val progressValue = (progress * 100) / totalToCompare
                        activity.runOnUiThread {
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                progressBar.progress = progressValue
                                progressText.text = "Tarkastetaan duplikaatteja: $progressValue%"
                            }
                        }
                    }
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch

                progressDialog.dismiss()
                val totalImported = importedCatches.size + importedPlaces.size
                val totalDuplicates = duplicateCatches.size + duplicatePlaces.size
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
            } catch (e: CancellationException) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog?.dismiss()
                }
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Import failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog?.dismiss()
                    showConfirmationDialog("Tietojen tuonti epäonnistui: ${e.message}")
                }
            }
        }
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
                processImport(importedCatches, importedPlaces, duplicateCatches, duplicatePlaces, mode)
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
        if (activity.isFinishing || activity.isDestroyed) return

        val totalToProcess = importedCatches.size + importedPlaces.size

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

        activity.lifecycleScope.launch {
            try {
                val (catchesToInsertCount, placesToInsertCount) = withContext(Dispatchers.IO) {
                    val catchResolution = ImportConflictResolver.resolve(
                        importedCatches,
                        duplicateCatches,
                        mode,
                        FishCatch::id
                    )
                    val placeResolution = ImportConflictResolver.resolve(
                        importedPlaces,
                        duplicatePlaces,
                        mode,
                        PlaceOfInterest::id
                    )

                    catchResolution.existingIdsToDelete.forEach { id ->
                        db.fishCatchDao().deleteById(id)
                    }
                    placeResolution.existingIdsToDelete.forEach { id ->
                        db.placeOfInterestDao().deleteById(id)
                    }

                    val catchesToInsert = catchResolution.itemsToInsert
                    val placesToInsert = placeResolution.itemsToInsert

                    // Varmista uudet ID:t asettamalla ne nollaksi ja tallenna yksitellen edistymisen näyttämiseksi.
                    var processedCount = 0
                    val progressDenominator = totalToProcess.coerceAtLeast(1)

                    catchesToInsert.forEach { fishCatch ->
                        db.fishCatchDao().insertAll(listOf(fishCatch.copy(id = 0)))
                        processedCount++
                        val progressValue = (processedCount * 100) / progressDenominator
                        activity.runOnUiThread {
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                progressBar.progress = progressValue
                                progressText.text = "Tuodaan pisteitä: $progressValue%"
                            }
                        }
                    }

                    placesToInsert.forEach { place ->
                        db.placeOfInterestDao().insertAll(listOf(place.copy(id = 0)))
                        processedCount++
                        val progressValue = (processedCount * 100) / progressDenominator
                        activity.runOnUiThread {
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                progressBar.progress = progressValue
                                progressText.text = "Tuodaan pisteitä: $progressValue%"
                            }
                        }
                    }

                    catchesToInsert.size to placesToInsert.size
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                progressDialog.dismiss()
                onImportDone(false)
                showConfirmationDialog("Tietojen tuonti valmis ($catchesToInsertCount kalapistettä, $placesToInsertCount muun paikan pistettä).")
            } catch (e: CancellationException) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog.dismiss()
                }
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Import processing failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog.dismiss()
                    showConfirmationDialog("Tietojen tuonti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun importSpeciesFromJson(uri: Uri) {
        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed) return@launch

            try {
                val speciesList = withContext(Dispatchers.IO) {
                    jsonService.importSpecies(activity.contentResolver, uri, activity.filesDir)
                }
                if (speciesList.isEmpty()) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        showConfirmationDialog("Tiedostosta ei löytynyt tuotavia kalalajeja tai se on virheellinen.")
                    }
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    db.fishSpeciesDao().deleteAll()
                    db.fishSpeciesDao().insertAll(speciesList)
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                onImportDone(true)
                showConfirmationDialog("Kalalajien asetusten tuonti valmis (${speciesList.size} lajia).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Species import failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showConfirmationDialog("Kalalajien asetusten tuonti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun exportMediaToZip(uri: Uri) {
        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed) return@launch

            try {
                withContext(Dispatchers.IO) {
                    val outputStream = activity.contentResolver.openOutputStream(uri)
                        ?: throw java.io.IOException("Mediatiedostoa ei voitu avata kirjoittamista varten")
                    outputStream.use { output ->
                        mediaService.exportMedia(output)
                    }
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                showConfirmationDialog("Median vienti valmis.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Media export failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showConfirmationDialog("Median vienti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun importMediaFromZip(uri: Uri) {
        if (activity.isFinishing || activity.isDestroyed) return

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

        activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val inputStream = activity.contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("Mediatiedostoa ei voitu avata lukemista varten")
                    inputStream.use { input ->
                        mediaService.importMedia(input) { current, total ->
                            activity.runOnUiThread {
                                if (!activity.isFinishing && !activity.isDestroyed) {
                                    val progressValue = if (total > 0) (current * 100) / total else 100
                                    progressBar.progress = progressValue
                                    progressText.text = "Tuodaan mediaa: $progressValue% ($current/$total)"
                                }
                            }
                        }
                    }
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                progressDialog.dismiss()
                onImportDone(false)
                showConfirmationDialog("Median tuonti valmis.")
            } catch (e: CancellationException) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog.dismiss()
                }
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ImportExportManager", "Media import failed", e)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog.dismiss()
                    showConfirmationDialog("Median tuonti epäonnistui: ${e.message}")
                }
            }
        }
    }

    private fun showConfirmationDialog(message: String) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
        dialog.enlargeButtons()
    }
}
