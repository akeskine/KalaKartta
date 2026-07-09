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
    private val onMapSettingsChanged: () -> Unit = {},
    private val onDataChanged: () -> Unit
) {

    fun openSettings() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val count = db.fishCatchDao().getCount()
            withContext(Dispatchers.Main) {
                val dialog = AlertDialog.Builder(activity)
                    .setTitle("Asetukset")
                    .setItems(arrayOf("Tiedonsiirto", "Tiedon suodatus", "Sää", "Yhteenveto", "Taustakartta", "Takaisin")) { _, which ->
                        when (which) {
                            0 -> openDataTransferSettings(count)
                            1 -> openFilterSettings()
                            2 -> openWeatherSettings()
                            3 -> openSummary()
                            4 -> openMapSettings()
                            5 -> { /* Sulje valikko */ }
                        }
                    }
                    .show()
                dialog.enlargeButtons()
            }
        }
    }

    private fun openMapSettings() {
        val prefs = activity.getSharedPreferences("settings", AppCompatActivity.MODE_PRIVATE)
        val currentSource = prefs.getString("map_source", "OSM") ?: "OSM"
        val currentApiKey = prefs.getString("mml_api_key", "") ?: ""

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val radioGroup = RadioGroup(activity).apply {
            val sources = arrayOf("OpenStreetMap", "MML Maastokartta", "MML Ilmakuva")
            val internalIds = arrayOf("OSM", "MML_MAASTO", "MML_ILMA")
            
            for (i in sources.indices) {
                val radioButton = RadioButton(activity).apply {
                    text = sources[i]
                    id = i
                    textSize = 18f
                }
                addView(radioButton)
                if (currentSource == internalIds[i]) {
                    check(i)
                }
            }
        }
        layout.addView(radioGroup)

        val apiKeyLabel = TextView(activity).apply {
            text = "MML API-avain:"
            textSize = 16f
            setPadding(0, 30, 0, 0)
            visibility = if (radioGroup.checkedRadioButtonId > 0) android.view.View.VISIBLE else android.view.View.GONE
        }
        layout.addView(apiKeyLabel)

        val apiKeyInput = EditText(activity).apply {
            setText(currentApiKey)
            hint = "Syötä API-avain"
            visibility = if (radioGroup.checkedRadioButtonId > 0) android.view.View.VISIBLE else android.view.View.GONE
        }
        layout.addView(apiKeyInput)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val visible = if (checkedId > 0) android.view.View.VISIBLE else android.view.View.GONE
            apiKeyLabel.visibility = visible
            apiKeyInput.visibility = visible
        }

        val attributionText = TextView(activity).apply {
            text = "Lähde: Maanmittauslaitos / avoin aineisto. Lisenssi: CC BY 4.0."
            textSize = 12f
            setPadding(0, 40, 0, 0)
            alpha = 0.7f
        }
        layout.addView(attributionText)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Taustakartta")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val selectedId = radioGroup.checkedRadioButtonId
                val internalIds = arrayOf("OSM", "MML_MAASTO", "MML_ILMA")
                val newSource = internalIds[selectedId]
                val newApiKey = apiKeyInput.text.toString()

                prefs.edit().apply {
                    putString("map_source", newSource)
                    putString("mml_api_key", newApiKey)
                    apply()
                }
                onMapSettingsChanged()
            }
            .setNegativeButton("Takaisin") { _, _ -> openSettings() }
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

        val textView = TextView(activity).apply {
            text = "Päivitä puuttuvat säätiedot"
            textSize = 18f
            setTextColor(activity.resources.getColor(android.R.color.holo_blue_dark))
            setPadding(0, 30, 0, 0)
            setOnClickListener {
                val intent = android.content.Intent(activity, WeatherUpdateActivity::class.java)
                activity.startActivityForResult(intent, 1003)
            }
        }
        layout.addView(textView)

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
            .setNegativeButton("Takaisin") { _, _ ->
                openSettings()
            }
            .show()
        dialog.enlargeButtons()
    }

    // Poistettu updateMissingWeatherData metodit ja siirretty WeatherUpdateActivityyn

    private fun openSummary() {
        val intent = android.content.Intent(activity, SummaryActivity::class.java)
        activity.startActivity(intent)
    }

    private fun openFilterSettings() {
        val intent = android.content.Intent(activity, FilterActivity::class.java)
        activity.startActivityForResult(intent, 2001)
    }

    private fun openDataTransferSettings(count: Int) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Tiedonsiirto ($count pistettä)")
            .setItems(arrayOf("Vie tiedot", "Tuo tiedot", "Poista kaikki pisteet", "Takaisin")) { _, which ->
                when (which) {
                    0 -> importExportManager.launchExport()
                    1 -> importExportManager.launchImport()
                    2 -> confirmDeleteAllCatches()
                    3 -> openSettings()
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
