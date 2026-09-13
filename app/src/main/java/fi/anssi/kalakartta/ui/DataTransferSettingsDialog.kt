package fi.anssi.kalakartta.ui

import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.io.ImportExportManager
import fi.anssi.kalakartta.utils.enlargeButtons
import java.util.Locale

/** Tiedonsiirron ja tiedonpoiston asetusten dialogi. */
class DataTransferSettingsDialog(
    private val activity: AppCompatActivity,
    private val db: AppDatabase,
    private val importExportManager: ImportExportManager,
    private val onDisableHeatmapAndRoutes: () -> Unit,
    private val onDataChanged: (Boolean) -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {

    fun show(
        count: Int, 
        placeCount: Int, 
        sessionCount: Int,
        routePointCount: Int,
        mediaCount: Int,
        diaryPageCount: Int,
        isFiltered: Boolean = false, 
        filteredCount: Int = 0, 
        filteredPlaceCount: Int = 0,
        filteredCatches: List<fi.anssi.kalakartta.data.FishCatch>? = null,
        filteredPlaces: List<fi.anssi.kalakartta.data.PlaceOfInterest>? = null
    ) {
        val inflater = activity.layoutInflater
        val titleView = inflater.inflate(R.layout.dialog_settings_title, null)
        titleView.findViewById<TextView>(R.id.dialogTitle).text = "Tiedonsiirto"
        
        val infoButton = titleView.findViewById<ImageButton>(R.id.infoButton)
        infoButton.setOnClickListener {
            val mediaService = fi.anssi.kalakartta.data.MediaService(activity)
            val mediaSizeBytes = mediaService.getTotalSize()
            val dbFile = activity.getDatabasePath("kalakartta-db")
            val dbSizeBytes = if (dbFile.exists()) dbFile.length() else 0L
            
            // Lasketaan mukaan myös WAL ja SHM tiedostot jos ne ovat olemassa
            val walFile = activity.getDatabasePath("kalakartta-db-wal")
            val walSizeBytes = if (walFile.exists()) walFile.length() else 0L
            val shmFile = activity.getDatabasePath("kalakartta-db-shm")
            val shmSizeBytes = if (shmFile.exists()) shmFile.length() else 0L

            val databaseSizeBytes = dbSizeBytes + walSizeBytes + shmSizeBytes
            val mediaSizeMb = mediaSizeBytes.toDouble() / (1024 * 1024)
            val databaseSizeMb = databaseSizeBytes.toDouble() / (1024 * 1024)
            val totalSizeMb = (mediaSizeBytes + databaseSizeBytes).toDouble() / (1024 * 1024)
            
            val infoMessage = "Kalapisteitä: $count\n" +
                    "Muita pisteitä: $placeCount\n" +
                    "Kalastussessioita: $sessionCount\n" +
                    "Reittipisteitä: $routePointCount\n" +
                    "Mediatiedostoja: $mediaCount\n" +
                    "Kalapäiväkirjan sivuja: $diaryPageCount\n\n" +
                    "Mediatiedostot: ${String.format(Locale.US, "%.2f", mediaSizeMb)} Mt\n" +
                    "Tietokanta: ${String.format(Locale.US, "%.2f", databaseSizeMb)} Mt\n" +
                    "Yhteensä: ${String.format(Locale.US, "%.2f", totalSizeMb)} Mt"
            
            AlertDialog.Builder(activity)
                .setTitle("Tiedot")
                .setMessage(infoMessage)
                .setPositiveButton("OK", null)
                .show()
        }

        val dialogBuilder = AlertDialog.Builder(activity)
            .setCustomTitle(titleView)
            .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }

        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * activity.resources.displayMetrics.density).toInt()
            setPadding(p, 0, p, p)
        }
        
        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }
        
        val dialog = dialogBuilder.setView(scrollView).create()
        onShowDialog(dialog)

        val options = listOf(
            "Vie kaikki tiedot",
            "Tuo kaikki tiedot",
            "Poista kaikki tiedot"
        )
        
        options.forEach { option ->
            val textView = TextView(activity).apply {
                text = option
                textSize = 18f
                setPadding(0, 32, 0, 32)
                setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                isClickable = true
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }
            textView.setOnClickListener {
                dialog.dismiss()
                when (option) {
                    "Vie kaikki tiedot" -> importExportManager.launchExportAll()
                    "Tuo kaikki tiedot" -> {
                        onDisableHeatmapAndRoutes()
                        importExportManager.launchImportAll()
                    }
                    "Poista kaikki tiedot" -> importExportManager.launchDeleteAllData()
                }
            }
            contentLayout.addView(textView)
        }

        val moreLink = TextView(activity).apply {
            text = "Lisää..."
            textSize = 18f
            setPadding(0, 32, 0, 32)
            setTextColor(activity.resources.getColor(android.R.color.holo_blue_dark))
            isClickable = true
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }
        contentLayout.addView(moreLink)

        val extraOptionsLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val oldOptions = arrayOf("Vie kalapisteet ja muut pisteet", "Tuo kalapisteet ja muut pisteet", "Vie kalastussessiot", "Tuo kalastussessiot", "Vie media", "Tuo media", "Vie kalapäiväkirja", "Tuo kalapäiväkirja", "Poista kalapisteet ja muut pisteet", "Poista kalastussessiot", "Poista media", "Poista kalapäiväkirja")
        oldOptions.forEachIndexed { index, option ->
            val textView = TextView(activity).apply {
                text = option
                textSize = 18f
                setPadding(0, 32, 0, 32)
                setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                isClickable = true
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }
            textView.setOnClickListener {
                dialog.dismiss()
                when (index) {
                    0 -> {
                        if (isFiltered) {
                            val exportDialog = AlertDialog.Builder(activity)
                                .setTitle("Vie kalapisteet ja muut pisteet")
                                .setMessage("Viedäänkö kaikki pisteet vai nykyisen suodatuksen rajaamat pisteet?\n\n" +
                                        "Kaikki: $count kalapistettä, $placeCount muuta pistettä\n\n" +
                                        "Suodatetut: $filteredCount kalapistettä, $filteredPlaceCount muuta pistettä")
                                .setPositiveButton("Takaisin", null)
                                .setNegativeButton("Vie suodatetut") { _, _ ->
                                    importExportManager.launchExport(filteredCatches, filteredPlaces)
                                }
                                .setNeutralButton("Vie kaikki") { _, _ ->
                                    importExportManager.launchExport()
                                }
                                .show()
                            exportDialog.enlargeButtons()
                        } else {
                            importExportManager.launchExport()
                        }
                    }
                    1 -> {
                        onDisableHeatmapAndRoutes()
                        importExportManager.launchImport()
                    }
                    2 -> importExportManager.launchExportRoutes()
                    3 -> {
                        onDisableHeatmapAndRoutes()
                        importExportManager.launchImportRoutes()
                    }
                    4 -> importExportManager.launchExportMedia()
                    5 -> importExportManager.launchImportMedia()
                    6 -> importExportManager.launchExportDiary()
                    7 -> {
                        onDisableHeatmapAndRoutes()
                        importExportManager.launchImportDiary()
                    }
                    8 -> confirmDeleteAllCatches()
                    9 -> confirmDeleteAllRoutes()
                    10 -> importExportManager.launchDeleteAllData()
                    11 -> importExportManager.launchDeleteDiaryData()
                }
            }
            extraOptionsLayout.addView(textView)
        }

        val lessLink = TextView(activity).apply {
            text = "Vähemmän..."
            textSize = 18f
            setPadding(0, 32, 0, 32)
            setTextColor(activity.resources.getColor(android.R.color.holo_blue_dark))
            isClickable = true
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }
        extraOptionsLayout.addView(lessLink)

        contentLayout.addView(extraOptionsLayout)

        moreLink.setOnClickListener {
            moreLink.visibility = View.GONE
            extraOptionsLayout.visibility = View.VISIBLE
        }

        lessLink.setOnClickListener {
            extraOptionsLayout.visibility = View.GONE
            moreLink.visibility = View.VISIBLE
        }
    }

    private fun confirmDeleteAllCatches() {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Poista kalapisteet ja muut pisteet?")
            .setMessage("Haluatko varmasti poistaa kaikki tallennetut kalamerkit ja paikkamerkit? Tätä toimintoa ei voi kumota.")
            .setPositiveButton("Takaisin", null)
            .setNegativeButton("Poista") { _, _ ->
                deleteAllCatches()
            }
            .create()

        onShowDialog(dialog)
    }

    private fun deleteAllCatches() {
        db.fishCatchDao().deleteAll()
        db.placeOfInterestDao().deleteAll()
        onDataChanged(false)

        val dialog = AlertDialog.Builder(activity)
            .setMessage("Kalapisteet ja muut pisteet poistettu.")
            .setPositiveButton("OK", null)
            .create()
        onShowDialog(dialog)
    }

    private fun confirmDeleteAllRoutes() {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Poista kalastussessiot?")
            .setMessage("Haluatko varmasti poistaa kaikki tallennetut kalastussessiot ja reittipisteet? Tätä toimintoa ei voi kumota.")
            .setPositiveButton("Takaisin", null)
            .setNegativeButton("Poista") { _, _ ->
                deleteAllRoutes()
            }
            .create()

        onShowDialog(dialog)
    }

    private fun deleteAllRoutes() {
        db.fishingSessionDao().deleteAll()
        onDataChanged(false)

        val dialog = AlertDialog.Builder(activity)
            .setMessage("Kalastussessiot poistettu.")
            .setPositiveButton("OK", null)
            .create()
        onShowDialog(dialog)
    }
}
