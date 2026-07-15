package fi.anssi.kalakartta.ui

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.*
import fi.anssi.kalakartta.BuildConfig
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
                val inflater = activity.layoutInflater
                val titleView = inflater.inflate(fi.anssi.kalakartta.R.layout.dialog_settings_title, null)
                val infoButton = titleView.findViewById<ImageButton>(fi.anssi.kalakartta.R.id.infoButton)
                
                infoButton.setOnClickListener {
                    AlertDialog.Builder(activity)
                        .setTitle("Versiotiedot")
                        .setMessage("Versio: ${BuildConfig.VERSION_NAME}\nKoontiaika: ${BuildConfig.BUILD_TIME}")
                        .setPositiveButton("OK", null)
                        .show()
                }

                val dialog = AlertDialog.Builder(activity)
                    .setCustomTitle(titleView)
                    .setItems(arrayOf("Tiedonsiirto", "Tiedon suodatus", "Sää", "Yhteenveto", "Taustakartta", "Kaada KalaKartta", "Takaisin")) { _, which ->
                        when (which) {
                            0 -> openDataTransferSettings(count)
                            1 -> openFilterSettings()
                            2 -> openWeatherSettings()
                            3 -> openSummary()
                            4 -> openMapSettings()
                            5 -> throw RuntimeException("Test Crash") // Crashlytics testi
                            6 -> { /* Sulje valikko */ }
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

        val testButton = Button(activity).apply {
            text = "Testaa API-avain"
            visibility = if (radioGroup.checkedRadioButtonId > 0) android.view.View.VISIBLE else android.view.View.GONE
            setOnClickListener {
                val apiKey = apiKeyInput.text.toString()
                if (apiKey.isEmpty()) {
                    Toast.makeText(activity, "Syötä API-avain ensin", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                val selectedId = radioGroup.checkedRadioButtonId
                val layer = if (selectedId == 2) "ortokuva" else "maastokartta"
                
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // Testataan hakemalla yksi tiili (zoom 0, x 0, y 0)
                        val urlString = "https://avoin-karttakuva.maanmittauslaitos.fi/avoin/wmts/1.0.0/$layer/default/WGS84_Pseudo-Mercator/0/0/0.png?api-key=$apiKey"
                        val url = java.net.URL(urlString)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        
                        val responseCode = connection.responseCode
                        withContext(Dispatchers.Main) {
                            val message = if (responseCode == 200) {
                                "API-avain OK"
                            } else {
                                "API-avain ei kelpaa (HTTP $responseCode)."
                            }
                            AlertDialog.Builder(activity)
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(activity)
                                .setMessage("Virhe testatessa: ${e.message}")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            }
        }
        layout.addView(testButton)

        val attributionText = TextView(activity).apply {
            text = "Lähde: Maanmittauslaitos / avoin aineisto. Lisenssi: CC BY 4.0."
            textSize = 12f
            setPadding(0, 40, 0, 0)
            alpha = 0.7f
            visibility = if (radioGroup.checkedRadioButtonId > 0) android.view.View.VISIBLE else android.view.View.GONE
        }
        layout.addView(attributionText)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val visible = if (checkedId > 0) android.view.View.VISIBLE else android.view.View.GONE
            apiKeyLabel.visibility = visible
            apiKeyInput.visibility = visible
            testButton.visibility = visible
            attributionText.visibility = visible
        }

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
            .setTitle("Poista kaikki tiedot?")
            .setMessage("Haluatko varmasti poistaa kaikki tallennetut kalamerkit ja paikkamerkit? Tätä toimintoa ei voi kumota.")
            .setPositiveButton("Poista kaikki") { _, _ ->
                deleteAllCatches()
            }
            .setNegativeButton("Peruuta", null)
            .show()

        dialog.enlargeButtons()
    }

    private fun deleteAllCatches() {
        db.fishCatchDao().deleteAll()
        db.placeOfInterestDao().deleteAll()
        onDataChanged()

        val dialog = AlertDialog.Builder(activity)
            .setMessage("Kaikki tiedot poistettu.")
            .setPositiveButton("OK", null)
            .show()
        dialog.enlargeButtons()
    }
}
