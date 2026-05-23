package fi.anssi.kalakartta.ui

import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.io.ImportExportManager
import fi.anssi.kalakartta.utils.enlargeButtons
import fi.anssi.kalakartta.utils.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

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
        
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }

        val checkBox = CheckBox(activity).apply {
            text = "Säädatan automaattinen haku"
            isChecked = isEnabledInitial
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                isEnabledCurrent = isChecked
            }
        }
        layout.addView(checkBox)

        val maxLabel = TextView(activity).apply {
            text = "Max. päivitettävien pisteiden lkm"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 30
            }
        }
        layout.addView(maxLabel)

        val maxEditText = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("100")
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        layout.addView(maxEditText)

        val updateButton = Button(activity).apply {
            text = "Päivitä puuttuvat säätiedot"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 20
            }
            setOnClickListener {
                val maxStr = maxEditText.text.toString()
                val maxCount = maxStr.toIntOrNull() ?: 0
                updateMissingWeatherData(maxCount)
            }
        }
        layout.addView(updateButton)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Sääasetukset")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                if (isEnabledCurrent != isEnabledInitial) {
                    prefs.edit().putBoolean("weather_enabled", isEnabledCurrent).apply()
                    if (isEnabledCurrent) {
                        WeatherService(activity).fetchAllStations()
                    }
                    onWeatherSettingsChanged(isEnabledCurrent)
                }
            }
            .show()
        dialog.enlargeButtons()
    }

    private fun updateMissingWeatherData(maxCount: Int) {
        val weatherService = WeatherService(activity)
        
        val progressDialogView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }
        
        val statusText = TextView(activity).apply {
            text = "Haetaan päivitettäviä pisteitä..."
            textSize = 16f
        }
        progressDialogView.addView(statusText)
        
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 20
            }
            max = 100
            progress = 0
        }
        progressDialogView.addView(progressBar)
        
        val statsText = TextView(activity).apply {
            text = ""
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10
            }
        }
        progressDialogView.addView(statsText)

        val errorText = TextView(activity).apply {
            text = ""
            textSize = 12f
            setTextColor(android.graphics.Color.RED)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10
            }
        }
        progressDialogView.addView(errorText)

        var job: kotlinx.coroutines.Job? = null

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Säätietojen päivitys")
            .setView(progressDialogView)
            .setCancelable(false)
            .setNegativeButton("Peruuta", null)
            .setPositiveButton("OK", null)
            .create()
            
        dialog.show()
        dialog.enlargeButtons()
        
        // Piilotetaan OK-nappi aluksi ja asetetaan peruutustoiminto
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        positiveButton.visibility = android.view.View.GONE
        
        negativeButton.setOnClickListener {
            job?.cancel()
            statusText.text = "Keskeytetään..."
            negativeButton.isEnabled = false
        }

        job = activity.lifecycleScope.launch(Dispatchers.IO) {
            val allCatches = db.fishCatchDao().getAll()
            // Suodatetaan ne, joita ei ole muokattu käsin ja joilta puuttuu jotain oleellista
            val allTargets = allCatches.filter { 
                it.weatherSource != "MANUAL" && (it.weatherSource == "" || it.weatherStation == "" || it.weatherTime == 0L || it.pressure == 0.0)
            }
            
            val targets = if (maxCount > 0) allTargets.take(maxCount) else allTargets
            
            if (targets.isEmpty()) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Ei päivitettäviä pisteitä (kaikilla on jo tiedot tai ne on syötetty käsin)."
                    dialog.setCancelable(true)
                    negativeButton.visibility = android.view.View.GONE
                    positiveButton.visibility = android.view.View.VISIBLE
                    positiveButton.setOnClickListener { dialog.dismiss() }
                }
                return@launch
            }

            val stations = weatherService.fetchAllStationsSuspend()
            if (stations == null) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Sääasemia ei voitu ladata."
                    dialog.setCancelable(true)
                    negativeButton.visibility = android.view.View.GONE
                    positiveButton.visibility = android.view.View.VISIBLE
                    positiveButton.setOnClickListener { dialog.dismiss() }
                }
                return@launch
            }

            var attempted = 0
            var successful = 0
            var failed = 0
            var lastError = ""
            val total = targets.size

            for (fishCatch in targets) {
                if (!isActive) break
                
                attempted++
                
                try {
                    // Etsi lähin sääasema
                    var nearest: fi.anssi.kalakartta.utils.WeatherStation? = null
                    var minDistance = Double.MAX_VALUE
                    for (station in stations) {
                        // Tarkistetaan onko asema ollut toiminnassa kyseisellä hetkellä
                        if (station.startTime != null && fishCatch.caughtAt < station.startTime) continue
                        if (station.endTime != null && fishCatch.caughtAt > station.endTime) continue

                        val distance = weatherService.calculateDistance(fishCatch.latitude, fishCatch.longitude, station.latitude, station.longitude)
                        
                        // Ei huomioida sääasemia, jotka ovat yli 300 km päässä
                        if (distance > 300.0) continue

                        if (distance < minDistance) {
                            minDistance = distance
                            nearest = station
                        }
                    }

                    if (nearest != null) {
                        val result = weatherService.fetchWeatherDataSync(nearest.fmisid, fishCatch.caughtAt)
                        if (result.first != null && result.first!!.isNotEmpty()) {
                            val data = result.first!!
                            val updatedCatch = fishCatch.copy(
                                airTemp = data["t2m"] ?: fishCatch.airTemp,
                                cloudiness = data["n_man"]?.toLong() ?: data["nn_4h"]?.toLong() ?: fishCatch.cloudiness,
                                rain = data["r_1h"]?.toLong() ?: fishCatch.rain,
                                windSpeed = data["ws_10min"] ?: fishCatch.windSpeed,
                                windDirection = data["wd_10min"]?.toLong() ?: fishCatch.windDirection,
                                pressure = data["p_sea"] ?: data["p_msl"] ?: fishCatch.pressure,
                                weatherSource = "FMI",
                                weatherTime = result.second ?: fishCatch.weatherTime,
                                weatherStation = "${nearest.fmisid}:${nearest.name}"
                            )
                            db.fishCatchDao().update(updatedCatch)
                            successful++
                        } else {
                            failed++
                            lastError = result.third ?: "Ei säädataa saatavilla tälle ajankohdalle."
                        }
                    } else {
                        failed++
                        lastError = "Lähintä sääasemaa ei löytynyt."
                    }
                } catch (e: Exception) {
                    failed++
                    lastError = e.message ?: "Tuntematon virhe."
                }

                withContext(Dispatchers.Main) {
                    val progressPercent = (attempted * 100) / total
                    progressBar.progress = progressPercent
                    statusText.text = "Päivitetään... $progressPercent %"
                    statsText.text = "Yritetty: $attempted / $total\nOnnistuneet: $successful\nEpäonnistuneet: $failed"
                    if (lastError.isNotEmpty()) {
                        errorText.text = "Viimeisin virhe: $lastError"
                    }
                }
                
                // Pieni viive palvelimen kuormituksen tasaamiseksi
                kotlinx.coroutines.delay(500)
            }

            withContext(Dispatchers.Main) {
                statusText.text = if (isActive) "Päivitys valmis." else "Päivitys keskeytetty."
                dialog.setCancelable(true)
                negativeButton.visibility = android.view.View.GONE
                positiveButton.visibility = android.view.View.VISIBLE
                positiveButton.setOnClickListener { dialog.dismiss() }
                onDataChanged() 
            }
        }
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
