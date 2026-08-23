package fi.anssi.kalakartta.ui

import android.content.Context
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
import com.google.android.material.button.MaterialButton
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

import android.text.format.DateFormat
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

class SettingsManager(
    private val activity: AppCompatActivity,
    private val db: AppDatabase,
    private val importExportManager: ImportExportManager,
    private val onWeatherSettingsChanged: (Boolean) -> Unit = {},
    private val onMapSettingsChanged: () -> Unit = {},
    private val onDataChanged: (forceRefreshSpecies: Boolean) -> Unit
) {

    private var currentDialog: AlertDialog? = null

    fun closeSettings() {
        currentDialog?.dismiss()
        currentDialog = null
    }

    fun openSettings(
        isFiltered: Boolean = false,
        filteredCatches: List<fi.anssi.kalakartta.data.FishCatch>? = null,
        filteredPlaces: List<fi.anssi.kalakartta.data.PlaceOfInterest>? = null
    ) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val count = db.fishCatchDao().getCount()
            val placeCount = db.placeOfInterestDao().getCount()
            val sessionCount = db.fishingSessionDao().getCount()
            val routePointCount = db.trackPointDao().getCount()
            val mediaCount = db.mediaDao().getCount()
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
                    .setItems(arrayOf("Taustakartta", "Kalastussessiot", "Kalastetut alueet", "Tiedon suodatus", "Yhteenveto", "Tiedonsiirto", "Sää", activity.getString(R.string.fish_species_settings), "Yleiset")) { _, which ->
                        when (which) {
                            0 -> openMapSettings()
                            1 -> openFishingSessionSettings()
                            2 -> openFishingHeatmapSettings()
                            3 -> openFilterSettings()
                            4 -> openSummary()
                            5 -> openDataTransferSettings(
                                count,
                                placeCount,
                                sessionCount,
                                routePointCount,
                                mediaCount,
                                isFiltered,
                                filteredCatches?.size ?: 0,
                                filteredPlaces?.size ?: 0,
                                filteredCatches,
                                filteredPlaces
                            )
                            6 -> openWeatherSettings()
                            7 -> openSpeciesSettings()
                            8 -> openGeneralSettings()
                        }
                    }
                    .setPositiveButton("Takaisin", null)
                    .show()
                currentDialog = dialog
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

    private fun openFishingHeatmapSettings() {
        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val heatmapEnabledCb = CheckBox(activity).apply {
            text = activity.getString(R.string.show_fishing_heatmap)
            isChecked = prefs.getBoolean("heatmap_enabled", false)
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("heatmap_enabled", isChecked).apply()
                onMapSettingsChanged()
            }
        }
        layout.addView(heatmapEnabledCb)

        val showShortcutCb = CheckBox(activity).apply {
            text = activity.getString(R.string.show_heatmap_shortcut)
            isChecked = prefs.getBoolean("show_heatmap_shortcut", false)
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("show_heatmap_shortcut", isChecked).apply()
                onMapSettingsChanged()
            }
        }
        layout.addView(showShortcutCb)

        val heatmapFilterEnabledCb = CheckBox(activity).apply {
            text = activity.getString(R.string.heatmap_filter_enabled)
            isChecked = prefs.getBoolean("heatmap_filter_enabled", false)
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("heatmap_filter_enabled", isChecked).apply()
                onMapSettingsChanged()
            }
        }
        layout.addView(heatmapFilterEnabledCb)

        val colors = arrayOf(
            activity.getString(R.string.color_red),
            activity.getString(R.string.color_purple),
            activity.getString(R.string.color_green)
        )
        
        val currentColor = prefs.getString("heatmap_color", activity.getString(R.string.color_red))
        
        val gradientView = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 100).apply {
                topMargin = 20
            }
            isClickable = true
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            foreground = activity.getDrawable(outValue.resourceId)
        }
        
        fun updateGradient(colorName: String?) {
            val baseColor = when (colorName) {
                activity.getString(R.string.color_purple) -> Color.rgb(128, 0, 128)
                activity.getString(R.string.color_green) -> Color.GREEN
                else -> Color.RED
            }
            val startColor = Color.argb(40, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            val endColor = Color.argb(240, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            val gradient = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(startColor, endColor)
            )
            gradientView.background = gradient
        }
        
        updateGradient(currentColor)
        
        gradientView.setOnClickListener {
            val current = prefs.getString("heatmap_color", activity.getString(R.string.color_red))
            val currentIndex = colors.indexOf(current).coerceAtLeast(0)
            val nextIndex = (currentIndex + 1) % colors.size
            val nextColor = colors[nextIndex]
            
            prefs.edit().putString("heatmap_color", nextColor).apply()
            updateGradient(nextColor)
            onMapSettingsChanged()
        }
        
        layout.addView(gradientView)

        val minMaxLayout = RelativeLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val midText = TextView(activity).apply {
            id = View.generateViewId()
            text = "Käyntikerrat ruudussa"
            textSize = 14f
        }

        val minLabel = TextView(activity).apply {
            id = View.generateViewId()
            text = "Min"
            textSize = 14f
        }
        val minLabelParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        }
        minMaxLayout.addView(minLabel, minLabelParams)

        val minEdit = EditText(activity).apply {
            id = View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("heatmap_min_points", 1).toString())
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = RelativeLayout.LayoutParams(120, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.BELOW, minLabel.id)
                addRule(RelativeLayout.ALIGN_LEFT, minLabel.id)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s.toString().toIntOrNull() ?: 1
                    prefs.edit().putInt("heatmap_min_points", value).apply()
                    onMapSettingsChanged()
                }
            })
        }
        minMaxLayout.addView(minEdit)

        val maxLabel = TextView(activity).apply {
            id = View.generateViewId()
            text = "Max"
            textSize = 14f
        }
        val maxLabelParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        }
        minMaxLayout.addView(maxLabel, maxLabelParams)

        val maxEdit = EditText(activity).apply {
            id = View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("heatmap_max_points", 5).toString())
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = RelativeLayout.LayoutParams(120, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.BELOW, maxLabel.id)
                addRule(RelativeLayout.ALIGN_RIGHT, maxLabel.id)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s.toString().toIntOrNull() ?: 5
                    prefs.edit().putInt("heatmap_max_points", value).apply()
                    onMapSettingsChanged()
                }
            })
        }
        minMaxLayout.addView(maxEdit)

        // Sijoitetaan midText keskelle minEditin ja maxEditin tasolle tai väliin
        val midTextParamsReal = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.CENTER_HORIZONTAL)
            addRule(RelativeLayout.ALIGN_BASELINE, minEdit.id)
        }
        minMaxLayout.addView(midText, midTextParamsReal)

        layout.addView(minMaxLayout)


        // Ruudun koko
        val gridSizeLabel = TextView(activity).apply {
            text = activity.getString(R.string.heatmap_grid_size)
            textSize = 16f
            setPadding(0, 20, 0, 10)
        }
        layout.addView(gridSizeLabel)

        val gridSizes = arrayOf("100", "300", "1000")
        val gridSizeSpinner = Spinner(activity)
        val gridAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, gridSizes)
        gridAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gridSizeSpinner.adapter = gridAdapter

        val currentGridSize = prefs.getFloat("heatmap_grid_size", 300.0f).toInt().toString()
        val gridIndex = gridSizes.indexOf(currentGridSize).coerceAtLeast(0)
        gridSizeSpinner.setSelection(gridIndex)

        gridSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val value = gridSizes[position].toFloatOrNull() ?: 300.0f
                prefs.edit().putFloat("heatmap_grid_size", value).apply()
                onMapSettingsChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        layout.addView(gridSizeSpinner)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.action_fishing_heatmap))
            .setView(ScrollView(activity).apply { addView(layout) })
            .setNegativeButton("Tallenna", null)
            .setPositiveButton("Takaisin") { _, _ -> openSettings() }
            .show()
        currentDialog = dialog
        dialog.enlargeButtons()
    }

    private fun openGeneralSettings() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        // Oletuskalastaja -linkki
        val fishermanLink = TextView(activity).apply {
            text = "Oletuskalastaja"
            textSize = 18f
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
            textSize = 18f
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
            textSize = 18f
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

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.general_settings))
            .setView(layout)
            .setPositiveButton("Takaisin") { _, _ -> openSettings() }
            .show()
        currentDialog = dialog
        dialog.enlargeButtons()
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

        val showMeasurementToolCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.show_measurement_tool)
            isChecked = prefs.getBoolean("show_measurement_tool", false)
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("show_measurement_tool", isChecked).apply()
                onMapSettingsChanged()
            }
        }
        layout.addView(showMeasurementToolCheckbox)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.scale_bar))
            .setView(layout)
            .setPositiveButton("Takaisin") { _, _ -> openGeneralSettings() }
            .show()
        currentDialog = dialog
        dialog.enlargeButtons()
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

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.auto_center))
            .setView(layout)
            .setPositiveButton("Takaisin") { _, _ -> openGeneralSettings() }
            .show()
        currentDialog = dialog
        dialog.enlargeButtons()
    }


    private fun openMapSettings() {
        val prefs = activity.getSharedPreferences("settings", AppCompatActivity.MODE_PRIVATE)
        val currentSource = prefs.getString("map_source", "OSM") ?: "OSM"
        val currentApiKey = prefs.getString("mml_api_key", "") ?: ""
        var showQuickMapCurrent = prefs.getBoolean("show_quick_map_source", false)

        val sources = arrayOf("OpenStreetMap", "MML Maastokartta", "MML Ilmakuva", activity.getString(R.string.map_source_traficom), activity.getString(R.string.map_source_traficom_boating))
        val internalIds = arrayOf("OSM", "MML_MAASTO", "MML_ILMA", "TRAFICOM_SEA", "TRAFICOM_BOATING")
        
        // Luetaan yksittäisten karttapohjien pikavalinta-asetukset
        val quickSelectEnabled = internalIds.associateWith { id ->
            // MML-kartat vaativat validin API-avaimen oletuksena
            val default = if (id.startsWith("MML_")) currentApiKey.isNotEmpty() else true
            prefs.getBoolean("quick_select_$id", default)
        }.toMutableMap()

        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }

        // Otsikkorivi
        val headerLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 20)
            weightSum = 1f
        }
        
        headerLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.map_background)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
        })
        
        headerLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.quick_select)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f)
        })
        
        contentLayout.addView(headerLayout)

        val radioButtons = mutableListOf<RadioButton>()
        val checkBoxes = mutableMapOf<String, CheckBox>()

        for (i in sources.indices) {
            val id = internalIds[i]
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                weightSum = 1f
                setPadding(0, 10, 0, 10)
            }

            val rb = RadioButton(activity).apply {
                text = sources[i]
                textSize = 18f
                isChecked = currentSource == id
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
                setOnClickListener {
                    radioButtons.forEach { it.isChecked = false }
                    isChecked = true
                }
            }
            radioButtons.add(rb)
            row.addView(rb)

            val cb = CheckBox(activity).apply {
                isChecked = quickSelectEnabled[id] ?: true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f)
                gravity = android.view.Gravity.CENTER
                setOnCheckedChangeListener { _, isChecked ->
                    quickSelectEnabled[id] = isChecked
                }
            }
            checkBoxes[id] = cb
            row.addView(cb)

            contentLayout.addView(row)
        }

        val quickMapCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.show_quick_map_source)
            isChecked = showQuickMapCurrent
            textSize = 18f
            setPadding(0, 20, 0, 40)
            visibility = android.view.View.VISIBLE
            setOnCheckedChangeListener { _, isChecked ->
                showQuickMapCurrent = isChecked
            }
        }

        val apiKeyLabel = TextView(activity).apply {
            text = "MML API-avain:"
            textSize = 16f
            setPadding(0, 30, 0, 0)
            val initialSelectedId = internalIds.indexOf(currentSource)
            visibility = if (initialSelectedId in 1..2) android.view.View.VISIBLE else android.view.View.GONE
        }
        contentLayout.addView(apiKeyLabel)

        val apiKeyInput = EditText(activity).apply {
            setText(currentApiKey)
            hint = "Syötä API-avain"
            val initialSelectedId = internalIds.indexOf(currentSource)
            visibility = if (initialSelectedId in 1..2) android.view.View.VISIBLE else android.view.View.GONE
        }
        contentLayout.addView(apiKeyInput)

        val setApiKeyButton = Button(activity).apply {
            text = activity.getString(R.string.set_api_key)
            val initialSelectedId = internalIds.indexOf(currentSource)
            visibility = if (initialSelectedId in 1..2) android.view.View.VISIBLE else android.view.View.GONE
        }
        contentLayout.addView(setApiKeyButton)

        fun getSelectedId(): Int {
            return radioButtons.indexOfFirst { it.isChecked }
        }

        fun validateApiKey(apiKey: String, updateCheckbox: Boolean = true) {
            if (apiKey.isEmpty()) {
                return
            }

            val selectedId = getSelectedId()
            val layer = if (selectedId == 2) "ortokuva" else "maastokartta"

            activity.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val urlString = "https://avoin-karttakuva.maanmittauslaitos.fi/avoin/wmts/1.0.0/$layer/default/WGS84_Pseudo-Mercator/0/0/0.png?api-key=$apiKey"
                    val url = java.net.URL(urlString)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val responseCode = connection.responseCode
                    withContext(Dispatchers.Main) {
                        if (responseCode == 200) {
                            Toast.makeText(activity, "API-avain OK", Toast.LENGTH_SHORT).show()
                            // Päivitetään MML-pikavalinnat jos avain tuli validiksi
                            if (apiKey.isNotEmpty()) {
                                if (checkBoxes["MML_MAASTO"]?.isChecked == false && !prefs.contains("quick_select_MML_MAASTO")) checkBoxes["MML_MAASTO"]?.isChecked = true
                                if (checkBoxes["MML_ILMA"]?.isChecked == false && !prefs.contains("quick_select_MML_ILMA")) checkBoxes["MML_ILMA"]?.isChecked = true
                            }
                        } else {
                            Toast.makeText(activity, "API-avain ei kelpaa (HTTP $responseCode).", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Virhe testatessa: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        setApiKeyButton.setOnClickListener {
            validateApiKey(apiKeyInput.text.toString())
        }

        apiKeyInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Automaattinen validointi poistettu
            }
        })

        if (currentApiKey.isNotEmpty()) {
            validateApiKey(currentApiKey)
        }

        val attributionText = TextView(activity).apply {
            text = when (currentSource) {
                "TRAFICOM_SEA", "TRAFICOM_BOATING" -> activity.getString(R.string.traficom_attribution)
                "OSM" -> "Lähde: OpenStreetMap-yhteisö. Lisenssi: ODbL."
                else -> "Lähde: Maanmittauslaitos / avoin aineisto. Lisenssi: CC BY 4.0."
            }
            textSize = 12f
            setPadding(0, 40, 0, 0)
            alpha = 0.7f
        }
        contentLayout.addView(attributionText)
        contentLayout.addView(quickMapCheckbox)

        radioButtons.forEachIndexed { index, radioButton ->
            radioButton.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    val id = internalIds[index]
                    val mmlVisible = if (index in 1..2) android.view.View.VISIBLE else android.view.View.GONE
                    apiKeyLabel.visibility = mmlVisible
                    apiKeyInput.visibility = mmlVisible
                    setApiKeyButton.visibility = mmlVisible
                    
                    attributionText.text = when (id) {
                        "TRAFICOM_SEA", "TRAFICOM_BOATING" -> activity.getString(R.string.traficom_attribution)
                        "OSM" -> "Lähde: OpenStreetMap-yhteisö. Lisenssi: ODbL."
                        else -> "Lähde: Maanmittauslaitos / avoin aineisto. Lisenssi: CC BY 4.0."
                    }

                    if (index in 1..2 && apiKeyInput.text.isNotEmpty()) {
                        validateApiKey(apiKeyInput.text.toString())
                    }
                }
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Taustakartta")
            .setView(scrollView)
            .setPositiveButton("Takaisin") { _, _ -> openSettings() }
            .setNegativeButton("Tallenna") { _, _ ->
                val selectedId = getSelectedId()
                val newSource = internalIds[selectedId]
                val newApiKey = apiKeyInput.text.toString()
                val finalShowQuickMap = showQuickMapCurrent

                prefs.edit().apply {
                    putString("map_source", newSource)
                    putString("mml_api_key", newApiKey)
                    putBoolean("show_quick_map_source", finalShowQuickMap)
                    
                    // Tallennetaan jokaisen karttapohjan pikavalinta-asetus
                    internalIds.forEach { id ->
                        putBoolean("quick_select_$id", quickSelectEnabled[id] ?: true)
                    }
                    
                    apply()
                }
                onMapSettingsChanged()
            }
            .show()
        currentDialog = dialog
        dialog.enlargeButtons()
    }

    private fun openWeatherSettings() {
        val prefs = activity.getSharedPreferences("settings", AppCompatActivity.MODE_PRIVATE)
        val isEnabledInitial = prefs.getBoolean("weather_enabled", true)
        var isEnabledCurrent = isEnabledInitial
        
        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }

        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }

        val checkBox = CheckBox(activity).apply {
            text = "Säädatan automaattinen haku"
            isChecked = isEnabledInitial
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                isEnabledCurrent = isChecked
            }
        }
        contentLayout.addView(checkBox)

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
        contentLayout.addView(textView)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Sääasetukset")
            .setView(scrollView)
            .setPositiveButton("Takaisin") { _, _ ->
                openSettings()
            }
            .setNegativeButton("Tallenna") { _, _ ->
                if (isEnabledCurrent != isEnabledInitial) {
                    prefs.edit().putBoolean("weather_enabled", isEnabledCurrent).apply()
                    if (isEnabledCurrent) {
                        WeatherService(activity).fetchAllStations()
                    }
                    onWeatherSettingsChanged(isEnabledCurrent)
                }
            }
            .show()
        currentDialog = dialog
        dialog.enlargeButtons()
    }

    // Poistettu updateMissingWeatherData metodit ja siirretty WeatherUpdateActivityyn

    private fun openSummary() {
        val intent = android.content.Intent(activity, SummaryActivity::class.java)
        activity.startActivityForResult(intent, 2002)
    }

    private fun openFilterSettings() {
        val intent = android.content.Intent(activity, FilterActivity::class.java)
        activity.startActivityForResult(intent, 2001)
    }

    private fun openDataTransferSettings(
        count: Int, 
        placeCount: Int, 
        sessionCount: Int,
        routePointCount: Int,
        mediaCount: Int,
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
                    "Mediatiedostoja: $mediaCount\n\n" +
                    "Mediatiedostot: ${String.format(Locale.US, "%.2f", mediaSizeMb)} Mt\n" +
                    "Tietokanta: ${String.format(Locale.US, "%.2f", databaseSizeMb)} Mt\n" +
                    "Yhteensä: ${String.format(Locale.US, "%.2f", totalSizeMb)} Mt"
            
            AlertDialog.Builder(activity)
                .setTitle("Tiedot")
                .setMessage(infoMessage)
                .setPositiveButton("OK", null)
                .show()
        }

        val dialog = AlertDialog.Builder(activity)
            .setCustomTitle(titleView)
            .setPositiveButton("Takaisin") { _, _ -> openSettings() }
            .create()

        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * activity.resources.displayMetrics.density).toInt()
            setPadding(p, 0, p, p)
        }

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
                setTextColor(Color.BLACK)
                isClickable = true
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }
            textView.setOnClickListener {
                dialog.dismiss()
                when (option) {
                    "Vie kaikki tiedot" -> importExportManager.launchExportAll()
                    "Tuo kaikki tiedot" -> importExportManager.launchImportAll()
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

        val oldOptions = arrayOf("Vie pisteet", "Tuo pisteet", "Vie reitit", "Tuo reitit", "Vie media", "Tuo media", "Poista kaikki pisteet", "Poista kaikki reitit", "Poista kaikki media")
        oldOptions.forEachIndexed { index, option ->
            val textView = TextView(activity).apply {
                text = option
                textSize = 18f
                setPadding(0, 32, 0, 32)
                setTextColor(Color.BLACK)
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
                                .setTitle("Vie pisteet")
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
                    1 -> importExportManager.launchImport()
                    2 -> importExportManager.launchExportRoutes()
                    3 -> importExportManager.launchImportRoutes()
                    4 -> importExportManager.launchExportMedia()
                    5 -> importExportManager.launchImportMedia()
                    6 -> confirmDeleteAllCatches()
                    7 -> confirmDeleteAllRoutes()
                    8 -> importExportManager.launchDeleteAllData()
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

        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }

        dialog.setView(scrollView)
        dialog.show()
        dialog.enlargeButtons()
    }

    private fun confirmDeleteAllCatches() {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Poista kaikki pisteet?")
            .setMessage("Haluatko varmasti poistaa kaikki tallennetut kalamerkit ja paikkamerkit? Tätä toimintoa ei voi kumota.")
            .setPositiveButton("Takaisin", null)
            .setNegativeButton("Poista") { _, _ ->
                deleteAllCatches()
            }
            .show()

        dialog.enlargeButtons()
    }

    private fun deleteAllCatches() {
        db.fishCatchDao().deleteAll()
        db.placeOfInterestDao().deleteAll()
        onDataChanged(false)

        val dialog = AlertDialog.Builder(activity)
            .setMessage("Kaikki pisteet poistettu.")
            .setPositiveButton("OK", null)
            .show()
        dialog.enlargeButtons()
    }

    private fun confirmDeleteAllRoutes() {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Poista kaikki reitit?")
            .setMessage("Haluatko varmasti poistaa kaikki tallennetut kalastussessiot ja reittipisteet? Tätä toimintoa ei voi kumota.")
            .setPositiveButton("Takaisin", null)
            .setNegativeButton("Poista") { _, _ ->
                deleteAllRoutes()
            }
            .show()

        dialog.enlargeButtons()
    }

    private fun deleteAllRoutes() {
        db.fishingSessionDao().deleteAll()
        onDataChanged(false)

        val dialog = AlertDialog.Builder(activity)
            .setMessage("Kaikki reitit poistettu.")
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

        val dialog = AlertDialog.Builder(activity)
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
                        val d = AlertDialog.Builder(activity)
                            .setMessage(R.string.import_species_confirm)
                            .setPositiveButton("Takaisin", null)
                            .setNegativeButton("Tuo") { _, _ ->
                                importExportManager.launchImportSpecies()
                            }
                            .show()
                        currentDialog = d
                        d.enlargeButtons()
                    }
                    activity.getString(R.string.reset_default_species) -> {
                        val d = AlertDialog.Builder(activity)
                            .setMessage(R.string.reset_species_confirm)
                            .setPositiveButton("Takaisin", null)
                            .setNegativeButton("Palauta") { _, _ ->
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
                            .show()
                        currentDialog = d
                        d.enlargeButtons()
                    }
                }
            }
            .setPositiveButton("Takaisin") { _, _ -> openSettings() }
            .show()
        currentDialog = dialog
        dialog.enlargeButtons()
            }
        }
    }

    private fun openFishingSessionSettings() {
        val mainActivity = activity as? fi.anssi.kalakartta.MainActivity
        val fishingService = mainActivity?.getFishingService()
        val isRecording = fishingService?.isRecording() ?: false

        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }

        if (!isRecording) {
            var dialog: AlertDialog? = null

            val visibleSessionId = mainActivity?.getVisibleArchivedSessionId() ?: -1L
            if (visibleSessionId != -1L) {
                val visibleInfoLabel = TextView(activity).apply {
                    textSize = 14f
                    setPadding(0, 0, 0, 10)
                }
                contentLayout.addView(visibleInfoLabel)

                val hideSessionButton = TextView(activity).apply {
                    text = "Piilota näkyvä sessio"
                    textSize = 18f
                    setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                    val outValue = android.util.TypedValue()
                    activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                    isClickable = true
                    isFocusable = true
                    setPadding(0, 20, 0, 20)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(0, 0, 0, 30)
                    layoutParams = params
                    setOnClickListener {
                        mainActivity?.hideArchivedSession()
                        dialog?.dismiss()
                        openFishingSessionSettings()
                    }
                }
                contentLayout.addView(hideSessionButton)

                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val session = db.fishingSessionDao().getById(visibleSessionId)
                    if (session != null) {
                        val sdfDate = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
                        val sdfTime = SimpleDateFormat("H:mm", Locale.getDefault())
                        val startStr = sdfTime.format(Date(session.startedAt))
                        val endStr = session.endedAt?.let { sdfTime.format(Date(it)) } ?: "?"
                        
                        val startDay = sdfDate.format(Date(session.startedAt))
                        val endDay = session.endedAt?.let { sdfDate.format(Date(it)) } ?: startDay

                        val infoText = if (startDay == endDay) {
                            "Näkyvä kalastussessio $startDay $startStr - $endStr"
                        } else {
                            "Näkyvä kalastussessio $startDay $startStr - $endDay $endStr"
                        }

                        withContext(Dispatchers.Main) {
                            visibleInfoLabel.text = infoText
                        }
                    }
                }
            }

            val fetchButton = TextView(activity).apply {
                text = "Hae kalastussessiot"
                textSize = 18f
                setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(0, 20, 0, 20)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 20)
                layoutParams = params
                setOnClickListener {
                    val intent = Intent(activity, FishingSessionActivity::class.java)
                    activity.startActivityForResult(intent, 3001)
                }
            }
            contentLayout.addView(fetchButton)

            val trackingSettingsContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            contentLayout.addView(trackingSettingsContainer)

            val sectionTitle = TextView(activity).apply {
                text = "Reittipisteiden tallennusvälit"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 30, 0, 20)
            }
            trackingSettingsContainer.addView(sectionTitle)

            val prefs = activity.getSharedPreferences("settings", AppCompatActivity.MODE_PRIVATE)
            
            val locationIntervals = arrayOf("10", "30", "60", "120")
            val minIntervals = arrayOf("10", "30", "60", "120")
            val maxIntervals = arrayOf("60", "120", "300", "600")
            val minDistances = arrayOf("10", "20", "50", "100", "200")

            var currentLocationInterval = prefs.getInt("location_check_interval", 10).toString()
            var currentMinInterval = prefs.getInt("min_track_point_interval", 30).toString()
            var currentMaxInterval = prefs.getInt("max_track_point_interval", 300).toString()
            var currentMinDistance = prefs.getInt("min_track_point_distance", 20).toString()

            // Varmistetaan että asetukset ovat valittavissa olevia arvoja
            if (currentLocationInterval !in locationIntervals) currentLocationInterval = "10"
            if (currentMinInterval !in minIntervals) currentMinInterval = "30"
            if (currentMaxInterval !in maxIntervals) currentMaxInterval = "300"
            if (currentMinDistance !in minDistances) currentMinDistance = "20"

            val locationSpinner = Spinner(activity)
            val minIntervalSpinner = Spinner(activity)
            val maxIntervalSpinner = Spinner(activity)
            val minDistanceSpinner = Spinner(activity)

            fun updateSpinners() {
                val locVal = currentLocationInterval.toInt()
                val minVal = currentMinInterval.toInt()
                val maxVal = currentMaxInterval.toInt()

                var changed = false
                if (maxVal < minVal) {
                    currentMinInterval = currentMaxInterval
                    minIntervalSpinner.setSelection(minIntervals.indexOf(currentMinInterval))
                    prefs.edit().putInt("min_track_point_interval", currentMinInterval.toInt()).apply()
                    changed = true
                }
                
                val newMinVal = currentMinInterval.toInt()
                if (newMinVal < locVal) {
                    currentLocationInterval = currentMinInterval
                    locationSpinner.setSelection(locationIntervals.indexOf(currentLocationInterval))
                    prefs.edit().putInt("location_check_interval", currentLocationInterval.toInt()).apply()
                    changed = true
                }
            }

            fun createRow(labelText: String, spinner: Spinner): LinearLayout {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 10, 0, 10)
                }
                
                val label = TextView(activity).apply {
                    text = labelText
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                
                spinner.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                
                row.addView(label)
                row.addView(spinner)
                return row
            }

            trackingSettingsContainer.addView(createRow("Sijainnin tarkastuksen aikaväli (s)", locationSpinner.apply {
                val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, locationIntervals)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                this.adapter = adapter
                setSelection(locationIntervals.indexOf(currentLocationInterval))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                        currentLocationInterval = locationIntervals[pos]
                        prefs.edit().putInt("location_check_interval", currentLocationInterval.toInt()).apply()
                        updateSpinners()
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }))

            trackingSettingsContainer.addView(createRow("Tallennusaikaväli min (s)", minIntervalSpinner.apply {
                val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, minIntervals)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                this.adapter = adapter
                setSelection(minIntervals.indexOf(currentMinInterval))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                        currentMinInterval = minIntervals[pos]
                        prefs.edit().putInt("min_track_point_interval", currentMinInterval.toInt()).apply()
                        updateSpinners()
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }))

            trackingSettingsContainer.addView(createRow("Tallennusaikaväli max (s)", maxIntervalSpinner.apply {
                val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, maxIntervals)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                this.adapter = adapter
                setSelection(maxIntervals.indexOf(currentMaxInterval))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                        currentMaxInterval = maxIntervals[pos]
                        prefs.edit().putInt("max_track_point_interval", currentMaxInterval.toInt()).apply()
                        updateSpinners()
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }))

            trackingSettingsContainer.addView(createRow("Pisteiden minimietäisyys (m)", minDistanceSpinner.apply {
                val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, minDistances)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                this.adapter = adapter
                setSelection(minDistances.indexOf(currentMinDistance))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                        currentMinDistance = minDistances[pos]
                        prefs.edit().putInt("min_track_point_distance", currentMinDistance.toInt()).apply()
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }))

            val resetDefaultsLink = TextView(activity).apply {
                text = activity.getString(R.string.reset_defaults)
                textSize = 14f
                setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.link_color))
                paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(0, 10, 0, 10)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 10, 0, 10)
                layoutParams = params
                setOnClickListener {
                    currentLocationInterval = "10"
                    currentMinInterval = "30"
                    currentMaxInterval = "300"
                    currentMinDistance = "20"
                    
                    prefs.edit()
                        .putInt("location_check_interval", 10)
                        .putInt("min_track_point_interval", 30)
                        .putInt("max_track_point_interval", 300)
                        .putInt("min_track_point_distance", 20)
                        .apply()

                    locationSpinner.setSelection(locationIntervals.indexOf(currentLocationInterval))
                    minIntervalSpinner.setSelection(minIntervals.indexOf(currentMinInterval))
                    maxIntervalSpinner.setSelection(maxIntervals.indexOf(currentMaxInterval))
                    minDistanceSpinner.setSelection(minDistances.indexOf(currentMinDistance))
                }
            }
            trackingSettingsContainer.addView(resetDefaultsLink)

            val spacer = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (activity.resources.displayMetrics.density * 18).toInt() // Noin yhden rivin korkeus
                )
            }
            trackingSettingsContainer.addView(spacer)

            val statusText = TextView(activity).apply {
                textSize = 14f
                setTextColor(Color.RED)
                setPadding(0, 10, 0, 10)
                visibility = View.GONE
            }
            contentLayout.addView(statusText)

            val startButton = TextView(activity).apply {
                text = "Aloita tallennus"
                textSize = 18f
                setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(0, 20, 0, 20)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 10, 0, 10)
                layoutParams = params
                setOnClickListener {
                    val locInt = currentLocationInterval.toInt()
                    val minInt = currentMinInterval.toInt()
                    val maxInt = currentMaxInterval.toInt()
                    val minDist = currentMinDistance.toInt()
                    
                    mainActivity?.startFishingSession(locInt, minInt, maxInt, minDist)
                    dialog?.dismiss()
                    closeSettings()
                }
            }
            contentLayout.addView(startButton)

            val updateJob = activity.lifecycleScope.launch {
                val locationManager = activity.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                while (isActive) {
                    val isGpsEnabled = try {
                        locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    } catch (_: Exception) {
                        false
                    }
                    val isNetworkEnabled = try {
                        locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                    } catch (_: Exception) {
                        false
                    }
                    
                    val locationEnabled = isGpsEnabled || isNetworkEnabled
                    startButton.isEnabled = locationEnabled
                    startButton.visibility = if (locationEnabled) View.VISIBLE else View.GONE
                    trackingSettingsContainer.visibility = if (locationEnabled) View.VISIBLE else View.GONE
                    
                    if (locationEnabled) {
                        statusText.visibility = View.GONE
                    } else {
                        statusText.text = "Sijaintipalvelu ei ole päällä. Ota sijainti käyttöön aloittaaksesi tallennuksen."
                        statusText.visibility = View.VISIBLE
                    }
                    
                    kotlinx.coroutines.delay(1000)
                }
            }

            dialog = AlertDialog.Builder(activity)
                .setTitle("Kalastussessiot")
                .setView(scrollView)
                .setPositiveButton("Takaisin") { _, _ -> openSettings() }
                .setOnDismissListener {
                    updateJob.cancel()
                    if (currentDialog == dialog) currentDialog = null
                }
                .show()
            currentDialog = dialog
            dialog.enlargeButtons()
        } else {
            var dialog: AlertDialog? = null
            
            val infoText = TextView(activity).apply {
                textSize = 16f
                setPadding(0, 0, 0, 40)
            }
            contentLayout.addView(infoText)

            val updateJob = activity.lifecycleScope.launch {
                while (isActive) {
                    val currentService = (activity as? fi.anssi.kalakartta.MainActivity)?.getFishingService()
                    if (currentService != null && currentService.isRecording()) {
                        val startedAt = currentService.getStartedAt()
                        val duration = System.currentTimeMillis() - startedAt
                        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(duration)
                        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(duration) % 60
                        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(duration) % 60
                        
                        val durationStr = if (hours > 0) "${hours} h ${minutes} min ${seconds} s" else "${minutes} min ${seconds} s"
                        val distance = currentService.getTotalDistance()
                        val distanceStr = String.format("%.4f km", distance / 1000.0).replace(".", ",")
                        val minInterval = currentService.getMinIntervalSeconds()
                        val maxInterval = currentService.getMaxIntervalSeconds()
                        val locInterval = currentService.getLocationCheckIntervalSeconds()
                        val minDist = currentService.getMinDistanceMeters()
                        val sessionId = currentService.getCurrentSessionId()
                        val pointCount = if (sessionId != -1L) {
                            db.trackPointDao().getPointCountForSession(sessionId)
                        } else 0

                        var text = "Kalastussessio käynnissä: kesto $durationStr, matka $distanceStr, reittipisteitä $pointCount kpl.\n\nTallennusvälit: min ${minInterval}s, max ${maxInterval}s, etäisyys ${minDist}m, tarkastus ${locInterval}s."
                        
                        if (fi.anssi.kalakartta.service.FishingSessionService.KALASTUSSESSIOT_DEBUG) {
                            val checkCount = currentService.getLocationCheckCount()
                            val lastTimestamp = currentService.getLastSavedTimestamp()
                            val now = System.currentTimeMillis()
                            
                            val distanceToLast = currentService.getDistanceSinceLastSave()?.let { String.format("%.1f m", it) } ?: "-"
                            val lastLoc = currentService.getLastLocation()
                            val speedStr = lastLoc?.let { String.format("%.2f km/h", it.speed * 3.6).replace(".", ",") } ?: "-"
                            val accuracyStr = lastLoc?.let { String.format("%.0f m", it.accuracy) } ?: "-"
                            val timeStr = if (lastTimestamp > 0) "${(now - lastTimestamp) / 1000} s" else "-"
                            
                            text += "\n\nSijainnin tarkastus nro: $checkCount"
                            text += "\nEtäisyys edellisestä pisteestä: $distanceToLast"
                            text += "\nNopeus: $speedStr"
                            text += "\nTarkkuus: $accuracyStr"
                            text += "\nAika edellisen pisteen tallennuksesta: $timeStr"
                        }
                        
                        infoText.text = text
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
            
            val stopButton = TextView(activity).apply {
                text = "Lopeta tallennus"
                textSize = 18f
                setTextColor(activity.getColor(android.R.color.holo_red_dark))
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(0, 20, 0, 20)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 10, 0, 10)
                layoutParams = params
                setOnClickListener {
                    (activity as? fi.anssi.kalakartta.MainActivity)?.stopFishingSession()
                    dialog?.dismiss()
                }
            }
            contentLayout.addView(stopButton)

            dialog = AlertDialog.Builder(activity)
                .setTitle("Kalastussessiot")
                .setView(scrollView)
                .setPositiveButton("Takaisin") { _, _ -> openSettings() }
                .setOnDismissListener {
                    updateJob.cancel()
                    if (currentDialog == dialog) currentDialog = null
                }
                .show()
            currentDialog = dialog
            dialog.enlargeButtons()
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
            .setPositiveButton("Takaisin") { _, _ -> openGeneralSettings() }
            .setNegativeButton("Tallenna") { _, _ ->
                val newFisherman = input.text.toString().trim()
                prefs.edit().apply {
                    putString("default_fisherman", newFisherman)
                    putBoolean("show_fisherman_on_map", checkBox.isChecked)
                    apply()
                }
                onMapSettingsChanged() // Käytetään tätä päivittämään UI
            }
            .show()
        currentDialog = dialog
        dialog.enlargeButtons()
    }
}
