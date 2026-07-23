package fi.anssi.kalakartta.ui

import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.MotionEvent
import android.text.Spannable
import android.text.Spanned
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.method.LinkMovementMethod
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
    private val onDataChanged: (forceRefreshSpecies: Boolean) -> Unit
) {

    fun openSettings() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val count = db.fishCatchDao().getCount()
            val placeCount = db.placeOfInterestDao().getCount()
            withContext(Dispatchers.Main) {
                val inflater = activity.layoutInflater
                val titleView = inflater.inflate(fi.anssi.kalakartta.R.layout.dialog_settings_title, null)
                val infoButton = titleView.findViewById<ImageButton>(fi.anssi.kalakartta.R.id.infoButton)
                
                infoButton.setOnClickListener {
                    val titleViewVersion = inflater.inflate(fi.anssi.kalakartta.R.layout.dialog_version_title, null)
                    val titleText = titleViewVersion.findViewById<TextView>(fi.anssi.kalakartta.R.id.titleText)
                    val subtitleText = titleViewVersion.findViewById<TextView>(fi.anssi.kalakartta.R.id.subtitleText)
                    
                    titleText.text = "KalaKartta ${BuildConfig.VERSION_NAME}"
                    subtitleText.visibility = View.VISIBLE
                    subtitleText.text = "(${BuildConfig.BUILD_TIME})"
                    subtitleText.textSize = 14f
                    subtitleText.setTextColor(activity.getColor(android.R.color.darker_gray))

                    val contentLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(60, 20, 60, 20)
                    }

                    val helpLink = TextView(activity).apply {
                        text = "Käyttöohje"
                        textSize = 18f
                        setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                        setPadding(0, 10, 0, 10)
                        val outValue = android.util.TypedValue()
                        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                        setBackgroundResource(outValue.resourceId)
                        setOnClickListener {
                            showUserManual()
                        }
                    }
                    contentLayout.addView(helpLink)

                    AlertDialog.Builder(activity)
                        .setCustomTitle(titleViewVersion)
                        .setView(contentLayout)
                        .setPositiveButton("OK", null)
                        .show()
                }

                val dialog = AlertDialog.Builder(activity)
                    .setCustomTitle(titleView)
                    .setItems(arrayOf("Tiedonsiirto", "Tiedon suodatus", "Sää", "Yhteenveto", "Taustakartta", activity.getString(R.string.fish_species_settings), "Yleiset", "Takaisin")) { _, which ->
                        when (which) {
                            0 -> openDataTransferSettings(count, placeCount)
                            1 -> openFilterSettings()
                            2 -> openWeatherSettings()
                            3 -> openSummary()
                            4 -> openMapSettings()
                            5 -> openSpeciesSettings()
                            6 -> openGeneralSettings()
                            7 -> { /* Sulje valikko */ }
                        }
                    }
                    .show()
                dialog.enlargeButtons()
            }
        }
    }

    private fun showUserManual() {
        try {
            val inputStream = activity.assets.open("kayttoohje.md")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val content = String(buffer, Charsets.UTF_8)
            
            val textView = TextView(activity).apply {
                text = android.text.Html.fromHtml(markdownToHtml(content), android.text.Html.FROM_HTML_MODE_LEGACY)
                setPadding(60, 40, 60, 40)
                textSize = 16f
                movementMethod = object : LinkMovementMethod() {
                    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
                        val action = event.action
                        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                            var x = event.x.toInt()
                            var y = event.y.toInt()

                            x -= widget.totalPaddingLeft
                            y -= widget.totalPaddingTop

                            x += widget.scrollX
                            y += widget.scrollY

                            val layout = widget.layout
                            val line = layout.getLineForVertical(y)
                            val off = layout.getOffsetForHorizontal(line, x.toFloat())

                            val links = buffer.getSpans(off, off, URLSpan::class.java)
                            if (links.isNotEmpty()) {
                                if (action == MotionEvent.ACTION_UP) {
                                    val url = links[0].url
                                    if (url.startsWith("#")) {
                                        val anchor = url.substring(1)
                                        scrollToAnchor(widget, buffer, anchor)
                                    } else {
                                        links[0].onClick(widget)
                                    }
                                }
                                return true
                            }
                        }
                        
                        // Jos kyseessä on ACTION_UP, eikä linkkiä klikattu, annetaan super.onTouchEvent käsitellä se
                        // Mutta emme palauta falsea heti, jotta ScrollView saa tapahtuman jos se ei ollut linkki.
                        return super.onTouchEvent(widget, buffer, event)
                    }
                }
            }

            val scrollView = ScrollView(activity).apply {
                id = View.generateViewId()
                addView(textView)
            }

            AlertDialog.Builder(activity)
                .setView(scrollView)
                .setPositiveButton("Sulje", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Käyttöohjetta ei voitu ladata", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scrollToAnchor(widget: TextView, buffer: Spannable, anchor: String) {
        val text = widget.text
        if (text is Spanned) {
            val allText = text.toString()
            val lines = allText.split("\n")
            var currentPos = 0
            
            for (line in lines) {
                val normalizedLine = line.lowercase().trim()
                    .replace("ä", "a").replace("ö", "o")
                    .replace(" ", "-")
                    .replace(Regex("[^a-z0-9-]"), "")
                
                if (normalizedLine == anchor || normalizedLine.endsWith("-$anchor") || anchor.endsWith("-$normalizedLine")) {
                    val layout = widget.layout
                    if (layout != null) {
                        val lineNum = layout.getLineForOffset(currentPos)
                        // Huomioidaan TextView:n padding
                        val y = layout.getLineTop(lineNum) + widget.totalPaddingTop
                        val scrollView = widget.parent as? ScrollView
                        scrollView?.smoothScrollTo(0, y)
                    }
                    break
                }
                currentPos += line.length + 1
            }
        }
    }

    private fun markdownToHtml(markdown: String): String {
        var html = markdown
        
        // Linkit [teksti](#ankkuri) -> <a href="#ankkuri">teksti</a>
        html = html.replace(Regex("\\[([^\\]]+)\\]\\(#([^\\)]+)\\)"), "<a href=\"#$2\">$1</a>")
        
        // Otsikot ja id-attribuutit ankkureita varten
        // Muutetaan ### Otsikko -> <h3 id="otsikko">Otsikko</h3>
        html = html.replace(Regex("(?m)^### (.*)$")) { matchResult ->
            val title = matchResult.groupValues[1]
            val id = title.lowercase().trim().replace(" ", "-")
                .replace("ä", "a").replace("ö", "o")
                .replace(Regex("[^a-z0-9-]"), "")
            "<h3 id=\"$id\">$title</h3>"
        }
        html = html.replace(Regex("(?m)^## (.*)$")) { matchResult ->
            val title = matchResult.groupValues[1]
            val id = title.lowercase().trim().replace(" ", "-")
                .replace("ä", "a").replace("ö", "o")
                .replace(Regex("[^a-z0-9-]"), "")
            "<h2 id=\"$id\">$title</h2>"
        }
        html = html.replace(Regex("(?m)^# (.*)$")) { matchResult ->
            val title = matchResult.groupValues[1]
            val id = title.lowercase().trim().replace(" ", "-")
                .replace("ä", "a").replace("ö", "o")
                .replace(Regex("[^a-z0-9-]"), "")
            "<h1 id=\"$id\">$title</h1>"
        }
        html = html.replace(Regex("(?m)^- (.*)$"), "<li>$1</li>")
        
        // Lihavointi
        html = html.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>$1</b>")
        
        // Rivinvaihdot: kaksi tai useampi rivinvaihtoa -> <br><br>
        html = html.replace("\n\n", "<br><br>")
        
        return html
    }

    private fun openGeneralSettings() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        // Oletuskalastaja -linkki
        val fishermanLink = TextView(activity).apply {
            text = "Oletuskalastaja"
            textSize = 20f
            setTextColor(activity.getColor(android.R.color.holo_blue_dark))
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                openDefaultFishermanSettings()
            }
        }
        layout.addView(fishermanLink)

        // Mittakaava -linkki
        val scaleLink = TextView(activity).apply {
            text = activity.getString(R.string.scale_bar)
            textSize = 20f
            setTextColor(activity.getColor(android.R.color.holo_blue_dark))
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                openScaleSettings()
            }
        }
        layout.addView(scaleLink)

        // Automaattinen kohdistus -linkki
        val autoCenterLink = TextView(activity).apply {
            text = activity.getString(R.string.auto_center)
            textSize = 20f
            setTextColor(activity.getColor(android.R.color.holo_blue_dark))
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                openAutoCenterSettings()
            }
        }
        layout.addView(autoCenterLink)

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.general_settings))
            .setView(layout)
            .setPositiveButton("Sulje", null)
            .show()
            .enlargeButtons()
    }

    private fun openScaleSettings() {
        val prefs = activity.getSharedPreferences("settings", AppCompatActivity.MODE_PRIVATE)
        
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val showScaleCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.show_scale_bar)
            isChecked = prefs.getBoolean("show_scale_bar", false)
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("show_scale_bar", isChecked).apply()
                onMapSettingsChanged()
            }
        }
        layout.addView(showScaleCheckbox)

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.scale_bar))
            .setView(layout)
            .setPositiveButton("Sulje", null)
            .show()
            .enlargeButtons()
    }

    private fun openAutoCenterSettings() {
        val prefs = activity.getSharedPreferences("settings", AppCompatActivity.MODE_PRIVATE)

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val autoCenterCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.auto_center_on_start)
            isChecked = prefs.getBoolean("auto_center_on_start", true)
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("auto_center_on_start", isChecked).apply()
            }
        }
        layout.addView(autoCenterCheckbox)

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.auto_center))
            .setView(layout)
            .setPositiveButton("Sulje", null)
            .show()
            .enlargeButtons()
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

    private fun openDataTransferSettings(count: Int, placeCount: Int) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }

        val titleView = TextView(activity).apply {
            text = "Tiedonsiirto"
            textSize = 22f
            setTextColor(activity.resources.getColor(android.R.color.primary_text_light))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        layout.addView(titleView)

        val subtitleView = TextView(activity).apply {
            text = "($count kalapistettä, $placeCount muuta pistettä)"
            textSize = 14f
            setTextColor(activity.resources.getColor(android.R.color.darker_gray))
            setPadding(0, 4, 0, 0)
        }
        layout.addView(subtitleView)

        val dialog = AlertDialog.Builder(activity)
            .setCustomTitle(layout)
            .setItems(arrayOf("Vie pisteet", "Tuo pisteet", "Poista kaikki pisteet", "Takaisin")) { _, which ->
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
        onDataChanged(false)

        val dialog = AlertDialog.Builder(activity)
            .setMessage("Kaikki tiedot poistettu.")
            .setPositiveButton("OK", null)
            .show()
        dialog.enlargeButtons()
    }

    private fun openSpeciesSettings() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val species = db.fishSpeciesDao().getAll()
            val defaults = fi.anssi.kalakartta.data.FishSpecies.getDefaultList()
            
            // Tarkistetaan onko muutoksia tehty
            val isModified = species.size != defaults.size || species.any { s ->
                val d = defaults.find { it.id == s.id }
                d == null || s.name != d.name || s.small_weight != d.small_weight || 
                s.small_length != d.small_length || s.large_weight != d.large_weight || 
                s.large_length != d.large_length || s.giant_weight != d.giant_weight || 
                s.giant_length != d.giant_length || s.icon_default != d.icon_default || 
                s.favourite_fish != d.favourite_fish || s.sortOrder != d.sortOrder
            }

            withContext(Dispatchers.Main) {
                val options = mutableListOf<String>()
                options.add(activity.getString(R.string.edit_species))
                if (isModified) {
                    options.add(activity.getString(R.string.export_species_settings))
                }
                options.add(activity.getString(R.string.import_species_settings))
                if (isModified) {
                    options.add(activity.getString(R.string.reset_default_species))
                }
                options.add("Takaisin")

                AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.fish_species_settings))
                    .setItems(options.toTypedArray()) { _, which ->
                        when (options[which]) {
                            activity.getString(R.string.edit_species) -> {
                                val intent = Intent(activity, EditSpeciesActivity::class.java)
                                activity.startActivityForResult(intent, 1002)
                            }
                            activity.getString(R.string.export_species_settings) -> {
                                importExportManager.launchExportSpecies()
                            }
                            activity.getString(R.string.import_species_settings) -> {
                                AlertDialog.Builder(activity)
                                    .setMessage(R.string.import_species_confirm)
                                    .setPositiveButton(R.string.ok) { _, _ ->
                                        importExportManager.launchImportSpecies()
                                    }
                                    .setNegativeButton(R.string.cancel, null)
                                    .show()
                                    .enlargeButtons()
                            }
                            activity.getString(R.string.reset_default_species) -> {
                                AlertDialog.Builder(activity)
                                    .setMessage(R.string.reset_species_confirm)
                                    .setPositiveButton(R.string.delete) { _, _ ->
                                        activity.lifecycleScope.launch(Dispatchers.IO) {
                                            db.fishSpeciesDao().deleteAll()
                                            // MainActivityn esitäyttö hoitaa loput, mutta voimme myös täyttää tässä heti
                                            fi.anssi.kalakartta.data.FishSpecies.getDefaultList().forEach {
                                                db.fishSpeciesDao().insert(it)
                                            }
                                            withContext(Dispatchers.Main) {
                                                onDataChanged(true)
                                                Toast.makeText(activity, "Oletukset palautettu", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .setNegativeButton(R.string.cancel, null)
                                    .show()
                                    .enlargeButtons()
                            }
                        }
                    }
                    .show()
                    .enlargeButtons()
            }
        }
    }

    private fun openDefaultFishermanSettings() {
        val prefs = activity.getSharedPreferences("settings", AppCompatActivity.MODE_PRIVATE)
        val currentFisherman = prefs.getString("default_fisherman", "") ?: ""
        val showOnMap = prefs.getBoolean("show_fisherman_on_map", false)

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val label = TextView(activity).apply {
            text = "Oletuskalastajan nimi:"
            textSize = 16f
        }
        layout.addView(label)

        val input = EditText(activity).apply {
            setText(currentFisherman)
            hint = "Esim. Matti"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            }
        }
        layout.addView(input)

        val checkBox = CheckBox(activity).apply {
            text = "Näytä oletuskalastajan nimi kartalla"
            isChecked = showOnMap
            setPadding(0, 20, 0, 0)
        }
        layout.addView(checkBox)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Oletuskalastaja")
            .setView(layout)
            .setPositiveButton("Tallenna") { _, _ ->
                val newFisherman = input.text.toString().trim()
                prefs.edit().apply {
                    putString("default_fisherman", newFisherman)
                    putBoolean("show_fisherman_on_map", checkBox.isChecked)
                    apply()
                }
                onMapSettingsChanged() // Käytetään tätä päivittämään UI
            }
            .setNegativeButton("Takaisin") { _, _ -> openSettings() }
            .show()
        dialog.enlargeButtons()
    }
}
