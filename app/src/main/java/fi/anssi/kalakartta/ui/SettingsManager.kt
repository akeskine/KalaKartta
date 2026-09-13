package fi.anssi.kalakartta.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.MotionEvent
import android.text.Spannable
import android.text.Spanned
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.widget.*
import android.widget.RelativeLayout
import com.google.android.material.button.MaterialButton
import fi.anssi.kalakartta.BuildConfig
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.io.ImportExportManager
import fi.anssi.kalakartta.utils.enlargeButtons
import fi.anssi.kalakartta.utils.WeatherService
import fi.anssi.kalakartta.MainActivity
import fi.anssi.kalakartta.service.TalkingClockService
import fi.anssi.kalakartta.service.FishingSessionService
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
    private val settingsStore = SettingsStore(activity.getSharedPreferences("settings", AppCompatActivity.MODE_PRIVATE))

    fun closeSettings() {
        currentDialog?.dismiss()
        currentDialog = null
    }

    private fun createBackLink(onClick: () -> Unit): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val backLink = TextView(activity).apply {
            text = "Takaisin"
            textSize = 18f
            setTextColor(activity.resources.getColor(android.R.color.holo_blue_dark))
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onClick()
            }
        }
        container.addView(backLink)
        return container
    }

    private fun showDialog(dialog: AlertDialog) {
        currentDialog?.dismiss()
        currentDialog = dialog
        dialog.show()
        dialog.enlargeButtons()
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
            val diaryPageCount = db.fishDiaryPageDao().getAll().size // Luodaan myöhemmin getCount() jos tarpeen
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

                    val typedValue = android.util.TypedValue()
                    activity.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                    val primaryTextColor = if (typedValue.resourceId != 0) {
                        activity.getColor(typedValue.resourceId)
                    } else {
                        typedValue.data
                    }

                    val helpLink = TextView(activity).apply {
                        text = "Käyttöohje"
                        textSize = 16f
                        setTextColor(primaryTextColor)
                        setPadding(0, 10, 0, 10)
                        val outValue = android.util.TypedValue()
                        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                        setBackgroundResource(outValue.resourceId)
                        setOnClickListener {
                            showUserManual()
                        }
                    }
                    contentLayout.addView(helpLink)

                    val manualDialog = AlertDialog.Builder(activity)
                        .setCustomTitle(titleViewVersion)
                        .setView(contentLayout)
                        .setPositiveButton("OK", null)
                        .create()
                    showDialog(manualDialog)
                }

                val dialog = AlertDialog.Builder(activity)
                    .setCustomTitle(titleView)
                    .setItems(arrayOf("Taustakartta", "Kalastussessiot", "Kalastetut alueet", "Tiedon suodatus", "Yhteenveto", "Kalapäiväkirja", "Tiedonsiirto", activity.getString(R.string.fish_species_settings), "Yleiset")) { _, which ->
                        when (which) {
                            0 -> openMapSettings()
                            1 -> openFishingSessionSettings()
                            2 -> openFishingHeatmapSettings()
                            3 -> openFilterSettings()
                            4 -> openSummary()
                            5 -> openDiary()
                            6 -> openDataTransferSettings(
                                count,
                                placeCount,
                                sessionCount,
                                routePointCount,
                                mediaCount,
                                diaryPageCount,
                                isFiltered,
                                filteredCatches?.size ?: 0,
                                filteredPlaceCount = filteredPlaces?.size ?: 0,
                                filteredCatches = filteredCatches,
                                filteredPlaces = filteredPlaces
                            )
                            7 -> openSpeciesSettings()
                            8 -> openGeneralSettings()
                        }
                    }
                    .setPositiveButton("Takaisin", null)
                    .create()
                showDialog(dialog)
            }
        }
    }

    fun showUserManual(isStartup: Boolean = false) {
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

            val dialog = AlertDialog.Builder(activity)
                .setView(scrollView)
                .setPositiveButton("Sulje") { _, _ ->
                    if (isStartup) {
                        closeSettings()
                    }
                }
            
            val shownDialog = dialog.show()
            shownDialog.enlargeButtons()
            
            if (isStartup) {
                currentDialog = shownDialog
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Käyttöohjetta ei voitu ladata", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkShowUserManual() {
        val lastVersionCode = settingsStore.lastVersionCode
        val currentVersionCode = BuildConfig.VERSION_CODE

        if (lastVersionCode < currentVersionCode) {
            showUserManual(isStartup = true)
            settingsStore.lastVersionCode = currentVersionCode
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

    private fun disableHeatmapAndRoutes() {
        settingsStore.heatmapEnabled = SettingsDefaults.HEATMAP_ENABLED
        settingsStore.fishingRoutesEnabled = SettingsDefaults.FISHING_ROUTES_ENABLED
        onMapSettingsChanged()
    }

    private fun openFishingHeatmapSettings() {
        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val heatmapEnabledCb = CheckBox(activity).apply {
            text = activity.getString(R.string.show_fishing_heatmap)
            isChecked = settingsStore.heatmapEnabled
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkLimits(true, false) { success ->
                        if (success) {
                            settingsStore.heatmapEnabled = true
                            onMapSettingsChanged()
                        } else {
                            this.isChecked = false
                        }
                    }
                } else {
                    settingsStore.heatmapEnabled = false
                    onMapSettingsChanged()
                }
            }
        }
        layout.addView(heatmapEnabledCb)

        val routesEnabledCb = CheckBox(activity).apply {
            text = activity.getString(R.string.show_fishing_routes)
            isChecked = settingsStore.fishingRoutesEnabled
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkLimits(false, true) { success ->
                        if (success) {
                            settingsStore.fishingRoutesEnabled = true
                            onMapSettingsChanged()
                        } else {
                            this.isChecked = false
                        }
                    }
                } else {
                    settingsStore.fishingRoutesEnabled = false
                    onMapSettingsChanged()
                }
            }
        }
        layout.addView(routesEnabledCb)

        val heatmapFilterEnabledCb = CheckBox(activity).apply {
            text = activity.getString(R.string.heatmap_filter_enabled)
            isChecked = settingsStore.heatmapFilterEnabled
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                val heatmapEnabled = settingsStore.heatmapEnabled
                if (!isChecked && heatmapEnabled) {
                    // Jos kytketään suodatus pois ja heatmap on päällä, tarkistetaan rajat (kaikki pisteet)
                    checkLimits(true, false, providedFilters = FilterManager.Filters()) { success ->
                        if (success) {
                            settingsStore.heatmapFilterEnabled = false
                            onMapSettingsChanged()
                        } else {
                            this.isChecked = true
                        }
                    }
                } else {
                    settingsStore.heatmapFilterEnabled = isChecked
                    onMapSettingsChanged()
                }
            }
        }
        layout.addView(heatmapFilterEnabledCb)

        val routesFilterEnabledCb = CheckBox(activity).apply {
            text = activity.getString(R.string.routes_filter_enabled)
            isChecked = settingsStore.routesFilterEnabled
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                val routesEnabled = settingsStore.fishingRoutesEnabled
                if (!isChecked && routesEnabled) {
                    // Jos kytketään suodatus pois ja reitit on päällä, tarkistetaan rajat (kaikki pisteet)
                    checkLimits(false, true, providedFilters = FilterManager.Filters()) { success ->
                        if (success) {
                            settingsStore.routesFilterEnabled = false
                            onMapSettingsChanged()
                        } else {
                            this.isChecked = true
                        }
                    }
                } else {
                    settingsStore.routesFilterEnabled = isChecked
                    onMapSettingsChanged()
                }
            }
        }
        layout.addView(routesFilterEnabledCb)

        val shortcutModeLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        val shortcutLabel = TextView(activity).apply {
            text = activity.getString(R.string.show_heatmap_shortcut)
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        shortcutModeLayout.addView(shortcutLabel)

        val shortcutSpinner = Spinner(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val shortcutOptions = activity.resources.getStringArray(R.array.heatmap_shortcut_options)
        val shortcutAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, shortcutOptions)
        shortcutAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        shortcutSpinner.adapter = shortcutAdapter

        if (!settingsStore.hasHeatmapShortcutModeSetting()) {
            val oldVal = settingsStore.showHeatmapShortcut
            val newVal = if (oldVal) 3 else 0
            settingsStore.heatmapShortcutMode = newVal
        }
        
        shortcutSpinner.setSelection(settingsStore.heatmapShortcutMode)
        shortcutSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isInitialSelection = true
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInitialSelection) {
                    isInitialSelection = false
                    return
                }
                settingsStore.heatmapShortcutMode = position
                onMapSettingsChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        shortcutModeLayout.addView(shortcutSpinner)
        layout.addView(shortcutModeLayout)

        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.heatmap_advanced_settings)
            textSize = 18f
            setTextColor(Color.BLUE)
            setPadding(0, 20, 0, 20)
            isClickable = true
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            foreground = activity.getDrawable(outValue.resourceId)
            setOnClickListener {
                openFishingHeatmapAdvancedSettings()
            }
        })

        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.route_advanced_settings)
            textSize = 18f
            setTextColor(Color.BLUE)
            setPadding(0, 20, 0, 20)
            isClickable = true
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            foreground = activity.getDrawable(outValue.resourceId)
            setOnClickListener {
                openFishingRouteAdvancedSettings()
            }
        })

        layout.addView(createBackLink {
            openSettings()
        })

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.action_fishing_heatmap))
            .setView(ScrollView(activity).apply { addView(layout) })
            .create()
        showDialog(dialog)
    }

    private fun openFishingHeatmapAdvancedSettings() {
        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val colors = arrayOf(
            activity.getString(R.string.color_red),
            activity.getString(R.string.color_purple),
            activity.getString(R.string.color_green)
        )
        val currentColor = settingsStore.getHeatmapColor(activity.getString(R.string.color_red))
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
            val current = settingsStore.getHeatmapColor(activity.getString(R.string.color_red))
            val currentIndex = colors.indexOf(current).coerceAtLeast(0)
            val nextIndex = (currentIndex + 1) % colors.size
            val nextColor = colors[nextIndex]
            settingsStore.setHeatmapColor(nextColor)
            updateGradient(nextColor)
            onMapSettingsChanged()
        }
        layout.addView(gradientView)

        val minMaxLayout = RelativeLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 10, 0, 10)
        }
        val midText = TextView(activity).apply {
            id = View.generateViewId()
            val method = settingsStore.getHeatmapCalculationMethod(
                activity.getString(R.string.heatmap_method_points)
            )
            text = if (method == activity.getString(R.string.heatmap_method_points)) {
                activity.getString(R.string.heatmap_points_in_grid)
            } else {
                activity.getString(R.string.heatmap_sessions_in_grid)
            }
            textSize = 14f
        }
        val minEdit = EditText(activity).apply {
            id = View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(
                settingsStore.heatmapMinPoints.toString()
            )
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = RelativeLayout.LayoutParams(120, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (settingsStore.heatmapAutoConfigure) return
                    val value = s.toString().toIntOrNull() ?: SettingsDefaults.HEATMAP_MIN_POINTS
                    settingsStore.heatmapMinPoints = value
                    val method = settingsStore.getHeatmapCalculationMethod(
                        activity.getString(R.string.heatmap_method_points)
                    )
                    if (method == activity.getString(R.string.heatmap_method_points)) {
                        settingsStore.heatmapMinPointsByPoints = value
                    } else {
                        settingsStore.heatmapMinPointsBySessions = value
                    }
                    onMapSettingsChanged()
                }
            })
        }
        minMaxLayout.addView(minEdit)
        val maxEdit = EditText(activity).apply {
            id = View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(
                settingsStore.heatmapMaxPoints.toString()
            )
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = RelativeLayout.LayoutParams(120, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (settingsStore.heatmapAutoConfigure) return
                    val value = s.toString().toIntOrNull() ?: SettingsDefaults.HEATMAP_MAX_POINTS
                    settingsStore.heatmapMaxPoints = value
                    val method = settingsStore.getHeatmapCalculationMethod(
                        activity.getString(R.string.heatmap_method_points)
                    )
                    if (method == activity.getString(R.string.heatmap_method_points)) {
                        settingsStore.heatmapMaxPointsByPoints = value
                    } else {
                        settingsStore.heatmapMaxPointsBySessions = value
                    }
                    onMapSettingsChanged()
                }
            })
        }
        minMaxLayout.addView(maxEdit)
        val midTextParamsReal = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.CENTER_HORIZONTAL)
            addRule(RelativeLayout.ALIGN_BASELINE, minEdit.id)
        }
        minMaxLayout.addView(midText, midTextParamsReal)
        layout.addView(minMaxLayout)

        val autoConfigureCb = CheckBox(activity).apply {
            text = activity.getString(R.string.heatmap_auto_configure)
            isChecked = settingsStore.heatmapAutoConfigure
            textSize = 16f
            minEdit.isEnabled = !isChecked
            maxEdit.isEnabled = !isChecked
            setOnCheckedChangeListener { _, checked ->
                settingsStore.heatmapAutoConfigure = checked
                minEdit.isEnabled = !checked
                maxEdit.isEnabled = !checked
                if (checked) {
                    minEdit.setText("1")
                    onMapSettingsChanged()
                } else {
                    onMapSettingsChanged()
                }
            }
        }
        layout.addView(autoConfigureCb)

        val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == SettingsKeys.HEATMAP_MIN_POINTS || key == SettingsKeys.HEATMAP_MAX_POINTS) {
                activity.runOnUiThread {
                    if (key == SettingsKeys.HEATMAP_MIN_POINTS) {
                        val newVal = settingsStore.heatmapMinPoints.toString()
                        if (minEdit.text.toString() != newVal) {
                            minEdit.setText(newVal)
                        }
                    } else if (key == SettingsKeys.HEATMAP_MAX_POINTS) {
                        val newVal = settingsStore.heatmapMaxPoints.toString()
                        if (maxEdit.text.toString() != newVal) {
                            maxEdit.setText(newVal)
                        }
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        val gridSizeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 10)
        }
        val gridSizeLabel = TextView(activity).apply {
            text = activity.getString(R.string.heatmap_grid_size)
            textSize = 16f
        }
        gridSizeRow.addView(gridSizeLabel)
        val gridSizes = arrayOf("50", "100", "300", "1000")
        val gridSizeSpinner = Spinner(activity)
        val gridAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, gridSizes)
        gridAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gridSizeSpinner.adapter = gridAdapter
        val currentGridSize = settingsStore.heatmapGridSize.toInt().toString()
        val gridIndex = gridSizes.indexOf(currentGridSize).coerceAtLeast(0)
        gridSizeSpinner.setSelection(gridIndex)
        gridSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isInitialSelection = true
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newValue = gridSizes[position].toFloatOrNull() ?: SettingsDefaults.HEATMAP_GRID_SIZE
                val oldValue = settingsStore.heatmapGridSize
                if (isInitialSelection) {
                    isInitialSelection = false
                    return
                }
                if (newValue == oldValue) return
                val heatmapEnabled = settingsStore.heatmapEnabled
                if (heatmapEnabled) {
                    checkLimits(true, false, newValue.toDouble()) { success ->
                        if (success) {
                            settingsStore.heatmapGridSize = newValue
                            onMapSettingsChanged()
                        } else {
                            val oldGridSizeStr = oldValue.toInt().toString()
                            val oldIndex = gridSizes.indexOf(oldGridSizeStr).coerceAtLeast(0)
                            gridSizeSpinner.setSelection(oldIndex)
                        }
                    }
                } else {
                    settingsStore.heatmapGridSize = newValue
                    onMapSettingsChanged()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        gridSizeRow.addView(gridSizeSpinner)
        layout.addView(gridSizeRow)

        val methodRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        val methodLabel = TextView(activity).apply {
            text = activity.getString(R.string.heatmap_calculation_method)
            textSize = 16f
            setPadding(0, 10, 0, 5)
        }
        methodRow.addView(methodLabel)
        val methods = arrayOf(
            activity.getString(R.string.heatmap_method_sessions),
            activity.getString(R.string.heatmap_method_points)
        )
        val methodSpinner = Spinner(activity)
        val methodAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, methods)
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        methodSpinner.adapter = methodAdapter
        val currentMethod = settingsStore.getHeatmapCalculationMethod(
            activity.getString(R.string.heatmap_method_points)
        )
        val methodIndex = methods.indexOf(currentMethod).coerceAtLeast(0)
        methodSpinner.setSelection(methodIndex)
        methodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMethod = methods[position]
                val oldMethod = settingsStore.getHeatmapCalculationMethod(
                    activity.getString(R.string.heatmap_method_points)
                )
                if (selectedMethod != oldMethod) {
                    settingsStore.setHeatmapCalculationMethod(selectedMethod)
                    val newMin: Int
                    val newMax: Int
                    if (selectedMethod == activity.getString(R.string.heatmap_method_points)) {
                        newMin = settingsStore.heatmapMinPointsByPoints
                        newMax = settingsStore.heatmapMaxPointsByPoints
                    } else {
                        newMin = settingsStore.heatmapMinPointsBySessions
                        newMax = settingsStore.heatmapMaxPointsBySessions
                    }
                    minEdit.setText(newMin.toString())
                    maxEdit.setText(newMax.toString())
                    settingsStore.heatmapMinPoints = newMin
                    settingsStore.heatmapMaxPoints = newMax
                    midText.text = if (selectedMethod == activity.getString(R.string.heatmap_method_points)) {
                        activity.getString(R.string.heatmap_points_in_grid)
                    } else {
                        activity.getString(R.string.heatmap_sessions_in_grid)
                    }
                    onMapSettingsChanged()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        methodRow.addView(methodSpinner)
        layout.addView(methodRow)

        // Siirtymäpisteiden poisto siirretty tänne
        val removeTransitionsCb = CheckBox(activity).apply {
            text = activity.getString(R.string.heatmap_remove_transitions)
            isChecked = settingsStore.heatmapRemoveTransitions
            textSize = 18f
        }
        val removalModeLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 0, 40, 10)
            visibility = if (removeTransitionsCb.isChecked) View.VISIBLE else View.GONE
        }
        val removalModeLabel = TextView(activity).apply {
            text = activity.getString(R.string.heatmap_transition_removal_mode)
            textSize = 16f
            setPadding(0, 10, 0, 5)
        }
        removalModeLayout.addView(removalModeLabel)
        val removalModeSpinner = Spinner(activity).apply {
            val modes = listOf(
                activity.getString(R.string.heatmap_transition_removal_only_heatmap),
                activity.getString(R.string.heatmap_transition_removal_all)
            )
            val removalModeAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, modes)
            removalModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            adapter = removalModeAdapter
            setSelection(settingsStore.heatmapRemoveTransitionsMode)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (settingsStore.heatmapRemoveTransitionsMode == position) return
                    val routesEnabled = settingsStore.fishingRoutesEnabled
                    if (position == 0 && routesEnabled) {
                         checkLimits(false, routesEnabled, providedRemoveTransitions = false) { success ->
                            if (success) {
                                settingsStore.heatmapRemoveTransitionsMode = position
                                onMapSettingsChanged()
                            } else {
                                setSelection(1)
                            }
                        }
                    } else {
                        settingsStore.heatmapRemoveTransitionsMode = position
                        onMapSettingsChanged()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        removalModeLayout.addView(removalModeSpinner)
        val speedInputLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(40, 0, 40, 0)
            visibility = if (removeTransitionsCb.isChecked) View.VISIBLE else View.GONE
        }
        val speedLabel = TextView(activity).apply {
            text = activity.getString(R.string.heatmap_max_speed)
            textSize = 16f
        }
        speedInputLayout.addView(speedLabel)
        val speedEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(settingsStore.heatmapMaxSpeed.toString())
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(150, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = 20
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s.toString().replace(",", ".").toFloatOrNull() ?: SettingsDefaults.HEATMAP_MAX_SPEED
                    val oldSpeed = settingsStore.heatmapMaxSpeed
                    if (value == oldSpeed) return
                    val heatmapEnabled = settingsStore.heatmapEnabled
                    val routesEnabled = settingsStore.fishingRoutesEnabled
                    if ((heatmapEnabled || routesEnabled) && value > oldSpeed) {
                        checkLimits(heatmapEnabled, routesEnabled, providedMaxSpeed = value) { success ->
                            if (success) {
                                settingsStore.heatmapMaxSpeed = value
                                onMapSettingsChanged()
                            } else {
                                setText(oldSpeed.toString())
                            }
                        }
                        return
                    }
                    settingsStore.heatmapMaxSpeed = value
                    onMapSettingsChanged()
                }
            })
        }
        speedInputLayout.addView(speedEdit)
        removeTransitionsCb.setOnCheckedChangeListener { _, isChecked ->
            val wasChecked = settingsStore.heatmapRemoveTransitions
            if (wasChecked && !isChecked) {
                val heatmapEnabled = settingsStore.heatmapEnabled
                val routesEnabled = settingsStore.fishingRoutesEnabled
                if (heatmapEnabled || routesEnabled) {
                    checkLimits(heatmapEnabled, routesEnabled, providedRemoveTransitions = false) { success ->
                        if (success) {
                            settingsStore.heatmapRemoveTransitions = false
                            speedInputLayout.visibility = View.GONE
                            removalModeLayout.visibility = View.GONE
                            onMapSettingsChanged()
                        } else {
                            removeTransitionsCb.isChecked = true
                        }
                    }
                    return@setOnCheckedChangeListener
                }
            }
            settingsStore.heatmapRemoveTransitions = isChecked
            speedInputLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            removalModeLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            onMapSettingsChanged()
        }
        layout.addView(removeTransitionsCb)
        layout.addView(removalModeLayout)
        layout.addView(speedInputLayout)

        layout.addView(createBackLink {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            openFishingHeatmapSettings()
        })

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.heatmap_advanced_settings))
            .setView(ScrollView(activity).apply { addView(layout) })
            .setOnCancelListener {
                prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            }
            .create()
        showDialog(dialog)
    }

    private fun openFishingRouteAdvancedSettings() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val typedValue = android.util.TypedValue()
        activity.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val primaryTextColor = if (typedValue.resourceId != 0) {
            activity.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }

        // Checkbox: Häivytä vanhat reittiviitat
        val fadeEnabledCheckbox = CheckBox(activity).apply {
            text = "Häivytä vanhat reittiviitat"
            setTextColor(primaryTextColor)
            isChecked = settingsStore.routesFadeEnabled
            setPadding(20, 20, 20, 20)
        }
        layout.addView(fadeEnabledCheckbox)

        // Lisäasetusten kontti
        val fadeSettingsLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (fadeEnabledCheckbox.isChecked) View.VISIBLE else View.GONE
        }
        layout.addView(fadeSettingsLayout)

        // Visuaalinen palkki
        val previewLine = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8).apply {
                topMargin = 40
                bottomMargin = 10
            }
            val colorStr = settingsStore.getHeatmapColor(activity.getString(R.string.color_red))
            val baseColor = when (colorStr) {
                activity.getString(R.string.color_purple) -> Color.rgb(128, 0, 128)
                activity.getString(R.string.color_green) -> Color.GREEN
                else -> Color.RED
            }
            val startColor = Color.argb(20, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            val endColor = Color.argb(200, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(startColor, endColor)
            )
        }
        fadeSettingsLayout.addView(previewLine)

        // Syöttökentät
        val inputsLayout = RelativeLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        
        val startLimitEdit = EditText(activity).apply {
            id = View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(
                settingsStore.routesFadeStartDays.toString()
            )
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = RelativeLayout.LayoutParams(150, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s.toString().toIntOrNull() ?: SettingsDefaults.ROUTES_FADE_START_DAYS
                    settingsStore.routesFadeStartDays = value
                    onMapSettingsChanged()
                }
            })
        }
        inputsLayout.addView(startLimitEdit)

        val fullLimitEdit = EditText(activity).apply {
            id = View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(
                settingsStore.routesFadeFullDays.toString()
            )
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = RelativeLayout.LayoutParams(150, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s.toString().toIntOrNull() ?: SettingsDefaults.ROUTES_FADE_FULL_DAYS
                    settingsStore.routesFadeFullDays = value
                    onMapSettingsChanged()
                }
            })
        }
        inputsLayout.addView(fullLimitEdit)
        
        fadeSettingsLayout.addView(inputsLayout)

        // Yksikköteksti
        fadeSettingsLayout.addView(TextView(activity).apply {
            text = "Yksikkönä päivä. Vasen: häivytys alkaa, Oikea: täysin näkyvä."
            textSize = 12f
            setTextColor(primaryTextColor)
            setPadding(0, 10, 0, 20)
        })

        fadeEnabledCheckbox.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.routesFadeEnabled = isChecked
            fadeSettingsLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            onMapSettingsChanged()
        }

        layout.addView(createBackLink {
            openFishingHeatmapSettings()
        })

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.route_advanced_settings))
            .setView(ScrollView(activity).apply { addView(layout) })
            .create()
        showDialog(dialog)
    }

    private fun openIconSizeSettings() {
        val typedValue = android.util.TypedValue()
        activity.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val primaryTextColor = if (typedValue.resourceId != 0) {
            activity.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }

        val fishIconScale = settingsStore.fishIconScale
        val otherIconScale = settingsStore.otherIconScale

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val options = arrayOf("0.2", "0.3", "0.4", "0.5", "0.75", "1.0", "1.25", "1.5")
        val optionValues = arrayOf(0.2f, 0.3f, 0.4f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f)

        // Kalakuvakkeiden koko
        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.fish_icon_size)
            setTextColor(primaryTextColor)
            setPadding(0, 20, 0, 10)
        })

        val fishSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, options)
            val currentPos = optionValues.indexOf(fishIconScale).let { if (it == -1) 2 else it } // oletus 1.0 jos ei löydy
            setSelection(currentPos)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    (view as? TextView)?.setTextColor(primaryTextColor)
                    val newValue = optionValues[position]
                    if (settingsStore.fishIconScale != newValue) {
                        settingsStore.fishIconScale = newValue
                        onMapSettingsChanged()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        layout.addView(fishSpinner)

        // Muu paikka -kuvakkeiden koko
        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.other_icon_size)
            setTextColor(primaryTextColor)
            setPadding(0, 40, 0, 10)
        })

        val otherSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, options)
            val currentPos = optionValues.indexOf(otherIconScale).let { if (it == -1) 2 else it } // oletus 1.0 jos ei löydy
            setSelection(currentPos)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    (view as? TextView)?.setTextColor(primaryTextColor)
                    val newValue = optionValues[position]
                    if (settingsStore.otherIconScale != newValue) {
                        settingsStore.otherIconScale = newValue
                        onMapSettingsChanged()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        layout.addView(otherSpinner)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.icon_sizes))
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton(activity.getString(R.string.back)) { _, _ -> openGeneralSettings() }
            .create()
        showDialog(dialog)
    }

    private fun openGeneralSettings() {
        val typedValue = android.util.TypedValue()
        activity.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val primaryTextColor = if (typedValue.resourceId != 0) {
            activity.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        // Oletuskalastaja -linkki
        val fishermanLink = TextView(activity).apply {
            text = "Oletuskalastaja"
            textSize = 16f
            setTextColor(primaryTextColor)
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                openDefaultFishermanSettings()
            }
        }
        layout.addView(fishermanLink)

        // Sää -linkki
        val weatherLink = TextView(activity).apply {
            text = "Sää"
            textSize = 16f
            setTextColor(primaryTextColor)
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                openWeatherSettings()
            }
        }
        layout.addView(weatherLink)

        // Mittakaava -linkki
        val scaleLink = TextView(activity).apply {
            text = activity.getString(R.string.scale_bar)
            textSize = 16f
            setTextColor(primaryTextColor)
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
            textSize = 16f
            setTextColor(primaryTextColor)
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                openAutoCenterSettings()
            }
        }
        layout.addView(autoCenterLink)
        
        // Puhuva kello -linkki
        val talkingClockLink = TextView(activity).apply {
            text = activity.getString(R.string.talking_clock)
            textSize = 16f
            setTextColor(primaryTextColor)
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                openTalkingClockSettingsIfPermissionsOk()
            }
        }
        layout.addView(talkingClockLink)
        
        // Kuvakkeiden koot -linkki
        val iconSizesLink = TextView(activity).apply {
            text = activity.getString(R.string.icon_sizes)
            textSize = 16f
            setTextColor(primaryTextColor)
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                openIconSizeSettings()
            }
        }
        layout.addView(iconSizesLink)
        
        // Kehittäjäasetukset-linkki
        val developerToolsLink = TextView(activity).apply {
            text = "Kehittäjäasetukset"
            textSize = 16f
            setTextColor(primaryTextColor)
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                openDeveloperTools()
            }
        }
        layout.addView(developerToolsLink)

        val scrollView = ScrollView(activity).apply {
            addView(layout)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.general_settings))
            .setView(scrollView)
            .setPositiveButton("Takaisin") { _, _ -> openSettings() }
            .create()
        showDialog(dialog)
    }

    companion object {
        fun checkLimits(
            context: Context,
            db: AppDatabase,
            lifecycleScope: LifecycleCoroutineScope,
            checkHeatmap: Boolean,
            checkRoutes: Boolean,
            newGridSize: Double? = null,
            providedFilters: FilterManager.Filters? = null,
            providedRemoveTransitions: Boolean? = null,
            providedMaxSpeed: Float? = null,
            onResult: (success: Boolean) -> Unit
        ) {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val settingsStore = SettingsStore(prefs)
            val maxPoints = settingsStore.maxTrackPoints
            val maxCells = settingsStore.maxHeatmapCells
            
            val filters = providedFilters ?: FilterManager(context).getFilters()
            
            // Selvitä käytetäänkö suodatusta heatmapille/reiteille
            // Jos providedFilters on annettu, oletetaan että testataan suodatuksen vaikutusta
            val heatmapFilterEnabled = if (providedFilters != null) {
                true
            } else {
                settingsStore.heatmapFilterEnabled
            }
            val routesFilterEnabled = if (providedFilters != null) {
                true
            } else {
                settingsStore.routesFilterEnabled
            }
            
            val removeTransitionsMode = settingsStore.heatmapRemoveTransitionsMode
            val baseRemoveTransitions = providedRemoveTransitions ?: settingsStore.heatmapRemoveTransitions
            val maxSpeed = providedMaxSpeed ?: settingsStore.heatmapMaxSpeed
            val routesFadeEnabled = settingsStore.routesFadeEnabled
            val routesFadeStartDays = settingsStore.routesFadeStartDays.coerceAtLeast(0).toLong()
            val routeSessionStartLimit = if (routesFadeEnabled) {
                val dayMillis = 1000L * 60 * 60 * 24
                System.currentTimeMillis() - (routesFadeStartDays + 1L) * dayMillis
            } else {
                Long.MIN_VALUE
            }

            lifecycleScope.launch(Dispatchers.IO) {
                var error: String? = null
                
                if (checkRoutes) {
                    val hasAreaFilter = filters.latNorth != null && filters.latSouth != null && filters.lonEast != null && filters.lonWest != null
                    val hasRangeFilter = filters.startDate != null || filters.endDate != null
                    
                    val removeTransitionsRoutes = if (removeTransitionsMode == 1) baseRemoveTransitions else false
                    
                    val count = if (routesFilterEnabled) {
                        db.trackPointDao().getCountFilteredForRoutes(
                            routeSessionStartLimit,
                            hasRangeFilter, filters.startDate ?: 0L, filters.endDate ?: Long.MAX_VALUE,
                            hasAreaFilter, filters.latSouth ?: 0.0, filters.latNorth ?: 0.0, filters.lonWest ?: 0.0, filters.lonEast ?: 0.0,
                            removeTransitionsRoutes, maxSpeed
                        )
                    } else {
                        db.trackPointDao().getCountFilteredForRoutes(
                            routeSessionStartLimit,
                            false, 0L, Long.MAX_VALUE,
                            false, 0.0, 0.0, 0.0, 0.0,
                            removeTransitionsRoutes, maxSpeed
                        )
                    }
                    
                    if (count > maxPoints) {
                        error = context.getString(R.string.too_many_track_points, count, maxPoints)
                    }
                }
                
                if (error == null && checkHeatmap) {
                    val gridSize = newGridSize ?: settingsStore.heatmapGridSize.toDouble().coerceAtLeast(1.0)
                    val latDegreeMeters = 111320.0
                    val lonDegreeMeters = latDegreeMeters * cos(Math.toRadians(60.0))
                    
                    val hasAreaFilter = filters.latNorth != null && filters.latSouth != null && filters.lonEast != null && filters.lonWest != null
                    val hasRangeFilter = filters.startDate != null || filters.endDate != null

                    val count = if (heatmapFilterEnabled) {
                        db.trackPointDao().getHeatmapCellCountFiltered(
                            hasRangeFilter, filters.startDate ?: 0L, filters.endDate ?: Long.MAX_VALUE,
                            hasAreaFilter, filters.latSouth ?: 0.0, filters.latNorth ?: 0.0, filters.lonWest ?: 0.0, filters.lonEast ?: 0.0,
                            baseRemoveTransitions, maxSpeed,
                            latDegreeMeters, lonDegreeMeters, gridSize
                        )
                    } else {
                        db.trackPointDao().getHeatmapCellCountFiltered(
                            false, 0L, Long.MAX_VALUE,
                            false, 0.0, 0.0, 0.0, 0.0,
                            baseRemoveTransitions, maxSpeed,
                            latDegreeMeters, lonDegreeMeters, gridSize
                        )
                    }
                    
                    if (count > maxCells) {
                        error = context.getString(R.string.too_many_heatmap_cells, count, maxCells)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (error != null) {
                        AlertDialog.Builder(context)
                            .setTitle(context.getString(R.string.warning))
                            .setMessage(error)
                            .setPositiveButton("OK", null)
                            .show()
                        onResult(false)
                    } else {
                        onResult(true)
                    }
                }
            }
        }
    }

    fun checkLimits(
        checkHeatmap: Boolean,
        checkRoutes: Boolean,
        newGridSize: Double? = null,
        providedFilters: FilterManager.Filters? = null,
        providedRemoveTransitions: Boolean? = null,
        providedMaxSpeed: Float? = null,
        onResult: (success: Boolean) -> Unit
    ) {
        checkLimits(activity, db, activity.lifecycleScope, checkHeatmap, checkRoutes, newGridSize, providedFilters, providedRemoveTransitions, providedMaxSpeed, onResult)
    }

    private fun openDeveloperTools() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val debugCheckbox = CheckBox(activity).apply {
            text = "Kalastussessioiden debug"
            isChecked = FishingSessionService.KALASTUSSESSIOT_DEBUG
            setOnCheckedChangeListener { _, isChecked ->
                FishingSessionService.KALASTUSSESSIOT_DEBUG = isChecked
            }
        }
        layout.addView(debugCheckbox)

        // Rivi 1: Reittipisteitä max
        val row1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val maxPointsLabel = TextView(activity).apply {
            text = "Reittipisteitä max:"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }
        val maxPointsEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(settingsStore.maxTrackPoints.toString())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row1.addView(maxPointsLabel)
        row1.addView(maxPointsEdit)
        layout.addView(row1)

        // Rivi 2: Heat map ruutuja max
        val row2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val maxCellsLabel = TextView(activity).apply {
            text = "Heat map ruutuja max:"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }
        val maxCellsEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(settingsStore.maxHeatmapCells.toString())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row2.addView(maxCellsLabel)
        row2.addView(maxCellsEdit)
        layout.addView(row2)

        // Rivi 3: Heat map zoomaustaso min
        val row3 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val minZoomLabel = TextView(activity).apply {
            text = "Heat map zoomaustaso min:"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }
        val minZoomEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(settingsStore.heatmapMinZoom.toString())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row3.addView(minZoomLabel)
        row3.addView(minZoomEdit)
        layout.addView(row3)

        // Rivi 4: Heatmap referenssileveyspiiri
        val row4 = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        val refLatLabel = TextView(activity).apply {
            text = "Heatmap referenssileveyspiiri (0-180°):"
        }
        val refLatEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(
                settingsStore.heatmapReferenceLatitude.toString()
            )
        }
        val refLatHint = TextView(activity).apply {
            text = "Määrittää pituuspiirien välisen etäisyyden. Oletus: 64.7 (Suomen keskipiste). Arvoalue 0-180."
            textSize = 12f
        }
        row4.addView(refLatLabel)
        row4.addView(refLatEdit)
        row4.addView(refLatHint)
        layout.addView(row4)

        // Rivi 5: Ilmanpaineen kehityksen raja-arvo
        val pressureTrendThresholdRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        val pressureTrendThresholdLabel = TextView(activity).apply {
            text = "Ilmanpaineen kehityksen raja-arvo (hPa/h):"
        }
        val pressureTrendThresholdEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(
                settingsStore.pressureTrendThreshold.toString()
            )
        }
        val pressureTrendThresholdHint = TextView(activity).apply {
            text = "Oletus ${FilterManager.DEFAULT_PRESSURE_TREND_THRESHOLD} (sama raja-arvo laskevalle ja nousevalle)."
            textSize = 12f
        }
        pressureTrendThresholdRow.addView(pressureTrendThresholdLabel)
        pressureTrendThresholdRow.addView(pressureTrendThresholdEdit)
        pressureTrendThresholdRow.addView(pressureTrendThresholdHint)
        layout.addView(pressureTrendThresholdRow)

        // Rivi 6: Ilmanpaineen kehityksen muutoksen raja-arvo
        val pressureTurningTrendThresholdRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        val pressureTurningTrendThresholdLabel = TextView(activity).apply {
            text = "Ilmanpaineen kehityksen muutoksen raja-arvo (hPa/h):"
        }
        val pressureTurningTrendThresholdEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(
                settingsStore.pressureTurningTrendThreshold.toString()
            )
        }
        val pressureTurningTrendThresholdHint = TextView(activity).apply {
            text = "Oletus ${FilterManager.DEFAULT_PRESSURE_TURNING_TREND_THRESHOLD} (sama raja-arvo ala- ja ylöspäin kääntyvälle)."
            textSize = 12f
        }
        pressureTurningTrendThresholdRow.addView(pressureTurningTrendThresholdLabel)
        pressureTurningTrendThresholdRow.addView(pressureTurningTrendThresholdEdit)
        pressureTurningTrendThresholdRow.addView(pressureTurningTrendThresholdHint)
        layout.addView(pressureTurningTrendThresholdRow)

        // Tulostus: Näkyvät määrät
        val statusText = TextView(activity).apply {
            text = "Lasketaan..."
            setPadding(0, 20, 0, 0)
        }
        layout.addView(statusText)

        // Laskenta taustalla
        activity.lifecycleScope.launch(Dispatchers.IO) {
            if (!isActive) return@launch
            val filters = FilterManager(activity).getFilters()
            val heatmapFilterEnabled = settingsStore.heatmapFilterEnabled
            val routesFilterEnabled = settingsStore.routesFilterEnabled
            val removeTransitions = settingsStore.heatmapRemoveTransitions
            val maxSpeed = settingsStore.heatmapMaxSpeed
            val routesFadeEnabled = settingsStore.routesFadeEnabled
            val routesFadeStartDays = settingsStore.routesFadeStartDays.coerceAtLeast(0).toLong()
            val routeSessionStartLimit = if (routesFadeEnabled) {
                val dayMillis = 1000L * 60 * 60 * 24
                System.currentTimeMillis() - (routesFadeStartDays + 1L) * dayMillis
            } else {
                Long.MIN_VALUE
            }
            val removeTransitionsForRoutes = removeTransitions &&
                    settingsStore.heatmapRemoveTransitionsMode == 1
            
            val hasAreaFilter = filters.latNorth != null && filters.latSouth != null && filters.lonEast != null && filters.lonWest != null
            
            // Reittipisteet
            val pointCount = db.trackPointDao().getCountFilteredForRoutes(
                minSessionStart = routeSessionStartLimit,
                checkRange = routesFilterEnabled && (filters.startDate != null || filters.endDate != null),
                startDate = filters.startDate ?: 0L,
                endDate = filters.endDate ?: Long.MAX_VALUE,
                checkArea = routesFilterEnabled && hasAreaFilter,
                latSouth = filters.latSouth ?: 0.0,
                latNorth = filters.latNorth ?: 0.0,
                lonWest = filters.lonWest ?: 0.0,
                lonEast = filters.lonEast ?: 0.0,
                removeTransitions = removeTransitionsForRoutes,
                maxSpeed = maxSpeed
            )

            // Heatmap ruudut
            val gridSize = settingsStore.heatmapGridSize.toDouble().coerceAtLeast(1.0)
            val refLat = settingsStore.heatmapReferenceLatitude.toDouble()
            val latDegreeMeters = 111320.0
            val lonDegreeMeters = latDegreeMeters * cos(Math.toRadians(refLat))
            
            val cellCount = db.trackPointDao().getHeatmapCellCountFiltered(
                checkRange = heatmapFilterEnabled && (filters.startDate != null || filters.endDate != null),
                startDate = filters.startDate ?: 0L,
                endDate = filters.endDate ?: Long.MAX_VALUE,
                checkArea = heatmapFilterEnabled && hasAreaFilter,
                latSouth = filters.latSouth ?: 0.0,
                latNorth = filters.latNorth ?: 0.0,
                lonWest = filters.lonWest ?: 0.0,
                lonEast = filters.lonEast ?: 0.0,
                removeTransitions = removeTransitions,
                maxSpeed = maxSpeed,
                latDegreeMeters = latDegreeMeters,
                lonDegreeMeters = lonDegreeMeters,
                gridSizeMeters = gridSize
            )

            withContext(Dispatchers.Main) {
                statusText.text = "Näkyvät reittpisteet $pointCount, \nNäkyvät heat map-ruudut $cellCount"
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Kehittäjäasetukset")
            .setView(layout)
            .setPositiveButton("Tallenna") { _, _ ->
                val maxPoints = maxPointsEdit.text.toString().toIntOrNull() ?: SettingsDefaults.MAX_TRACK_POINTS
                val maxCells = maxCellsEdit.text.toString().toIntOrNull() ?: SettingsDefaults.MAX_HEATMAP_CELLS
                val minZoom = minZoomEdit.text.toString().toFloatOrNull() ?: SettingsDefaults.HEATMAP_MIN_ZOOM
                val refLatInput = refLatEdit.text.toString().toFloatOrNull()
                val refLat = if (refLatInput != null && refLatInput in 0f..180f) {
                    refLatInput
                } else {
                    SettingsDefaults.HEATMAP_REFERENCE_LATITUDE
                }
                val pressureTrendThresholdInput = pressureTrendThresholdEdit.text.toString().toFloatOrNull()
                val pressureTrendThreshold = if (pressureTrendThresholdInput != null &&
                    pressureTrendThresholdInput > 0f &&
                    !pressureTrendThresholdInput.isNaN() &&
                    !pressureTrendThresholdInput.isInfinite()
                ) {
                    pressureTrendThresholdInput
                } else {
                    FilterManager.DEFAULT_PRESSURE_TREND_THRESHOLD
                }
                val pressureTurningTrendThresholdInput = pressureTurningTrendThresholdEdit.text.toString().toFloatOrNull()
                val pressureTurningTrendThreshold = if (pressureTurningTrendThresholdInput != null &&
                    pressureTurningTrendThresholdInput > 0f &&
                    !pressureTurningTrendThresholdInput.isNaN() &&
                    !pressureTurningTrendThresholdInput.isInfinite()
                ) {
                    pressureTurningTrendThresholdInput
                } else {
                    FilterManager.DEFAULT_PRESSURE_TURNING_TREND_THRESHOLD
                }
                
                settingsStore.maxTrackPoints = maxPoints
                settingsStore.maxHeatmapCells = maxCells
                settingsStore.heatmapMinZoom = minZoom
                settingsStore.heatmapReferenceLatitude = refLat
                settingsStore.pressureTrendThreshold = pressureTrendThreshold
                settingsStore.pressureTurningTrendThreshold = pressureTurningTrendThreshold
                openGeneralSettings()
            }
            .setNegativeButton("Takaisin") { _, _ -> openGeneralSettings() }
            .create()
        showDialog(dialog)
    }

    fun openTalkingClockSettingsIfPermissionsOk() {
        if (hasTalkingClockPermissions()) {
            openTalkingClockSettings()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.talking_clock)
            .setMessage(R.string.talking_clock_permissions_needed)
            .setPositiveButton("Kyllä") { _, _ ->
                requestTalkingClockPermissions()
            }
            .setNegativeButton("Ei", null)
            .show()
    }

    private fun requestTalkingClockPermissions() {
        // 1. Ilmoituslupa Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val launcher = (activity as? MainActivity)?.getNotificationPermissionLauncher()
                if (launcher != null) {
                    launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                }
                return
            }
        }

        // 2. Tarkka hälytys Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.talking_clock)
                    .setMessage(R.string.talking_clock_permission_alarms)
                    .setPositiveButton("OK") { _, _ ->
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = android.net.Uri.fromParts("package", activity.packageName, null)
                            }
                            activity.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                            activity.startActivity(intent)
                        }
                    }
                    .setNegativeButton("Peruuta", null)
                    .show()
                return
            }
        }
        
        // Jos päästään tänne, kaikki luvat on jo annettu (esim. juuri myönnetty)
        openTalkingClockSettings()
    }

    private fun hasTalkingClockPermissions(): Boolean {
        // Ilmoituslupa Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        
        // Tarkka hälytys Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                return false
            }
        }
        
        return true
    }

    private fun openTalkingClockSettings() {
        var dialog: AlertDialog? = null
        var statusTextView: TextView? = null

        fun getTitle(): String {
            return activity.getString(R.string.talking_clock)
        }

        fun getStatusText(): String {
            val isEnabled = settingsStore.talkingClockEnabled
            if (!isEnabled) return ""

            val interval = settingsStore.talkingClockInterval
            val status = activity.getString(R.string.talking_clock_running)
            return "$status, ${activity.getString(R.string.talking_clock_interval_info)} $interval ${activity.getString(R.string.unit_min)}"
        }

        fun updateTitle() {
            dialog?.setTitle(getTitle())
            statusTextView?.apply {
                text = getStatusText()
                visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        statusTextView = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 20)
            text = getStatusText()
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        layout.addView(statusTextView)

        val clockControlLink = TextView(activity).apply {
            val isEnabled = settingsStore.talkingClockEnabled
            text = activity.getString(if (isEnabled) R.string.talking_clock_stop else R.string.talking_clock_start)
            textSize = 18f
            setTextColor(activity.getColor(android.R.color.holo_blue_dark))
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            
            setOnClickListener {
                val newState = !settingsStore.talkingClockEnabled
                settingsStore.talkingClockEnabled = newState
                text = activity.getString(if (newState) R.string.talking_clock_stop else R.string.talking_clock_start)
                updateTitle()
                
                if (newState) {
                    val interval = settingsStore.talkingClockInterval
                    val intent = Intent(activity, TalkingClockService::class.java).apply {
                        putExtra("interval", interval)
                        action = "START_IMMEDIATELY"
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity.startForegroundService(intent)
                    } else {
                        activity.startService(intent)
                    }
                } else {
                    activity.stopService(Intent(activity, TalkingClockService::class.java))
                }
            }
        }
        layout.addView(clockControlLink)

        val onlyFishingCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.talking_clock_only_fishing)
            isChecked = settingsStore.talkingClockOnlyFishing
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.talkingClockOnlyFishing = isChecked
            }
        }
        layout.addView(onlyFishingCheckbox)

        // Kerro kellonaika X minuutin välein
        val intervalLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 20, 0, 20)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        intervalLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.talking_clock_interval_prefix)
            textSize = 18f
        })

        val intervals = arrayOf("1", "2", "5", "10", "15", "20", "30", "60")
        val currentInterval = settingsStore.talkingClockInterval.toString()
        val intervalSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, intervals)
            setSelection(intervals.indexOf(currentInterval).let { if (it == -1) 4 else it }) // Oletus 30 min
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val interval = intervals[position].toInt()
                    settingsStore.talkingClockInterval = interval
                    updateTitle()
                    
                    if (settingsStore.talkingClockEnabled) {
                        val intent = Intent(activity, TalkingClockService::class.java).apply {
                            putExtra("interval", interval)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity.startForegroundService(intent)
                        } else {
                            activity.startService(intent)
                        }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        intervalLayout.addView(intervalSpinner)
        intervalLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.talking_clock_interval_suffix)
            textSize = 18f
        })
        layout.addView(intervalLayout)

        // Puhuttelu
        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.talking_clock_salutation)
            textSize = 18f
            setPadding(0, 10, 0, 0)
        })
        val salutationEdit = EditText(activity).apply {
            setText(settingsStore.talkingClockSalutation)
            textSize = 18f
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    settingsStore.talkingClockSalutation = s?.toString() ?: SettingsDefaults.TALKING_CLOCK_SALUTATION
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        layout.addView(salutationEdit)

        // Akun varaus
        val batteryCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.talking_clock_battery_status)
            isChecked = settingsStore.talkingClockBattery
            textSize = 18f
            setPadding(paddingLeft, 10, paddingRight, 0)
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.talkingClockBattery = isChecked
            }
        }
        layout.addView(batteryCheckbox)

        val weatherHoursLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 0, 0, 10)
            visibility = if (settingsStore.talkingClockWeather) View.VISIBLE else View.GONE
        }
        val weatherHourOptions = listOf(1, 3, 6, 12)
        weatherHourOptions.forEach { hours ->
            val defaultChecked = hours == SettingsDefaults.TALKING_CLOCK_WEATHER_DEFAULT_HOURS
            if (!settingsStore.hasTalkingClockWeatherSetting(hours)) {
                settingsStore.setTalkingClockWeatherEnabled(hours, defaultChecked)
            }
            weatherHoursLayout.addView(CheckBox(activity).apply {
                text = "$hours h päähän"
                textSize = 18f
                isChecked = settingsStore.isTalkingClockWeatherEnabled(hours, defaultChecked)
                setOnCheckedChangeListener { _, isChecked ->
                    settingsStore.setTalkingClockWeatherEnabled(hours, isChecked)
                }
            })
        }

        val weatherCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.talking_clock_weather)
            isChecked = settingsStore.talkingClockWeather
            textSize = 18f
            setPadding(paddingLeft, 10, paddingRight, 0)
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.talkingClockWeather = isChecked
                weatherHoursLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }
        layout.addView(weatherCheckbox)
        layout.addView(weatherHoursLayout)

        // Auringonlasku
        val sunsetLimitLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(80, 0, 0, 10)
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = if (settingsStore.talkingClockSunset) View.VISIBLE else View.GONE
        }
        sunsetLimitLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.talking_clock_sunset_limit)
            textSize = 18f
        })
        val sunsetHours = arrayOf("1", "2", "3", "4", "5", "6", "12", "24")
        val currentSunsetLimit = settingsStore.talkingClockSunsetLimit.toString()
        val sunsetSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, sunsetHours)
            setSelection(sunsetHours.indexOf(currentSunsetLimit).let { if (it == -1) 1 else it })
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    settingsStore.talkingClockSunsetLimit = sunsetHours[position].toInt()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        sunsetLimitLayout.addView(sunsetSpinner)
        sunsetLimitLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.hours_suffix)
            textSize = 18f
        })

        val sunsetCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.talking_clock_sunset)
            isChecked = settingsStore.talkingClockSunset
            textSize = 18f
            setPadding(paddingLeft, 10, paddingRight, 0)
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.talkingClockSunset = isChecked
                sunsetLimitLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }
        layout.addView(sunsetCheckbox)
        layout.addView(sunsetLimitLayout)

        // Auringonnousu
        val sunriseLimitLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(80, 0, 0, 10)
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = if (settingsStore.talkingClockSunrise) View.VISIBLE else View.GONE
        }
        sunriseLimitLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.talking_clock_sunrise_limit)
            textSize = 18f
        })
        val sunriseHours = arrayOf("1", "2", "3", "4", "6", "12", "24")
        val currentSunriseLimit = settingsStore.talkingClockSunriseLimit.toString()
        val sunriseSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, sunriseHours)
            setSelection(sunriseHours.indexOf(currentSunriseLimit).let { if (it == -1) 1 else it })
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    settingsStore.talkingClockSunriseLimit = sunriseHours[position].toInt()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        sunriseLimitLayout.addView(sunriseSpinner)
        sunriseLimitLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.hours_suffix)
            textSize = 18f
        })

        val sunriseCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.talking_clock_sunrise)
            isChecked = settingsStore.talkingClockSunrise
            textSize = 18f
            setPadding(paddingLeft, 10, paddingRight, 0)
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.talkingClockSunrise = isChecked
                sunriseLimitLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }
        layout.addView(sunriseCheckbox)
        layout.addView(sunriseLimitLayout)

        dialog = AlertDialog.Builder(activity)
            .setTitle(getTitle())
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton("Takaisin") { _, _ -> openGeneralSettings() }
            .create()
        showDialog(dialog!!)
    }

    private fun openScaleSettings() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val showScaleCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.show_scale_bar)
            isChecked = settingsStore.showScaleBar
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.showScaleBar = isChecked
                onMapSettingsChanged()
            }
        }
        layout.addView(showScaleCheckbox)

        val showMeasurementToolCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.show_measurement_tool)
            isChecked = settingsStore.showMeasurementTool
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.showMeasurementTool = isChecked
                onMapSettingsChanged()
            }
        }
        layout.addView(showMeasurementToolCheckbox)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.scale_bar))
            .setView(layout)
            .setPositiveButton("Takaisin") { _, _ -> openGeneralSettings() }
            .create()
        showDialog(dialog)
    }

    private fun openAutoCenterSettings() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val autoCenterCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.auto_center_on_start)
            isChecked = settingsStore.autoCenterOnStart
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.autoCenterOnStart = isChecked
            }
        }
        layout.addView(autoCenterCheckbox)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.auto_center))
            .setView(layout)
            .setPositiveButton("Takaisin") { _, _ -> openGeneralSettings() }
            .create()
        showDialog(dialog)
    }


    private fun openMapSettings() {
        val currentSource = settingsStore.mapSource
        val currentApiKey = settingsStore.mmlApiKey
        var showQuickMapCurrent = settingsStore.showQuickMapSource

        val sources = arrayOf("OpenStreetMap", "MML Maastokartta", "MML Ilmakuva", activity.getString(R.string.map_source_traficom), activity.getString(R.string.map_source_traficom_boating))
        val internalIds = arrayOf("OSM", "MML_MAASTO", "MML_ILMA", "TRAFICOM_SEA", "TRAFICOM_BOATING")
        
        // Luetaan yksittäisten karttapohjien pikavalinta-asetukset
        val quickSelectEnabled = internalIds.associateWith { id ->
            // MML-kartat vaativat validin API-avaimen oletuksena
            val default = if (id.startsWith("MML_")) currentApiKey.isNotEmpty() else true
            settingsStore.isQuickMapSourceEnabled(id, default)
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
                    settingsStore.mapSource = id
                    onMapSettingsChanged()
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
                    settingsStore.setQuickMapSourceEnabled(id, isChecked)
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
                settingsStore.showQuickMapSource = isChecked
                onMapSettingsChanged()
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
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    settingsStore.mmlApiKey = s.toString()
                }
            })
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
                                if (checkBoxes["MML_MAASTO"]?.isChecked == false && !settingsStore.hasQuickMapSourceSetting("MML_MAASTO")) checkBoxes["MML_MAASTO"]?.isChecked = true
                                if (checkBoxes["MML_ILMA"]?.isChecked == false && !settingsStore.hasQuickMapSourceSetting("MML_ILMA")) checkBoxes["MML_ILMA"]?.isChecked = true
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
                // Tallennus tapahtuu jo ylempänä lisätyssä listenerissä
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
            .create()
        showDialog(dialog)
    }

    private fun openWeatherSettings() {
        val isEnabledInitial = settingsStore.weatherEnabled
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
                if (isEnabledCurrent != settingsStore.weatherEnabled) {
                    settingsStore.weatherEnabled = isEnabledCurrent
                    if (isEnabledCurrent) {
                        WeatherService(activity).fetchAllStations()
                    }
                    onWeatherSettingsChanged(isEnabledCurrent)
                }
            }
        }
        contentLayout.addView(checkBox)

        val textView = TextView(activity).apply {
            text = "Päivitä puuttuvat säätiedot"
            textSize = 18f
            setTextColor(activity.getColor(android.R.color.holo_blue_dark))
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
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
                openGeneralSettings()
            }
            .create()
        showDialog(dialog)
    }

    // Poistettu updateMissingWeatherData metodit ja siirretty WeatherUpdateActivityyn

    private fun openDiary() {
        val intent = android.content.Intent(activity, DiaryActivity::class.java)
        activity.startActivityForResult(intent, 2003)
    }

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
            .setPositiveButton("Takaisin") { _, _ -> openSettings() }

        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * activity.resources.displayMetrics.density).toInt()
            setPadding(p, 0, p, p)
        }
        
        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }
        
        val dialog = dialogBuilder.setView(scrollView).create()
        showDialog(dialog)

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
                        disableHeatmapAndRoutes()
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
                        disableHeatmapAndRoutes()
                        importExportManager.launchImport()
                    }
                    2 -> importExportManager.launchExportRoutes()
                    3 -> {
                        disableHeatmapAndRoutes()
                        importExportManager.launchImportRoutes()
                    }
                    4 -> importExportManager.launchExportMedia()
                    5 -> importExportManager.launchImportMedia()
                    6 -> importExportManager.launchExportDiary()
                    7 -> {
                        disableHeatmapAndRoutes()
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

        showDialog(dialog)
    }

    private fun deleteAllCatches() {
        db.fishCatchDao().deleteAll()
        db.placeOfInterestDao().deleteAll()
        onDataChanged(false)

        val dialog = AlertDialog.Builder(activity)
            .setMessage("Kalapisteet ja muut pisteet poistettu.")
            .setPositiveButton("OK", null)
            .create()
        showDialog(dialog)
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

        showDialog(dialog)
    }

    private fun deleteAllRoutes() {
        db.fishingSessionDao().deleteAll()
        onDataChanged(false)

        val dialog = AlertDialog.Builder(activity)
            .setMessage("Kalastussessiot poistettu.")
            .setPositiveButton("OK", null)
            .create()
        showDialog(dialog)
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
                val typedValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                val primaryTextColor = if (typedValue.resourceId != 0) {
                    activity.getColor(typedValue.resourceId)
                } else {
                    typedValue.data
                }

                val layout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                }

                val editSpeciesLink = TextView(activity).apply {
                    text = activity.getString(R.string.edit_species)
                    textSize = 16f
                    setTextColor(primaryTextColor)
                    setPadding(0, 20, 0, 40)
                    val outValue = android.util.TypedValue()
                    activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                    setOnClickListener {
                        val intent = Intent(activity, EditSpeciesActivity::class.java)
                        activity.startActivityForResult(intent, 1002)
                    }
                }
                layout.addView(editSpeciesLink)

                if (isModified) {
                    val exportSpeciesLink = TextView(activity).apply {
                        text = activity.getString(R.string.export_species_settings)
                        textSize = 18f
                        setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                        setPadding(0, 20, 0, 40)
                        val outValue = android.util.TypedValue()
                        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                        setBackgroundResource(outValue.resourceId)
                        setOnClickListener {
                            importExportManager.launchExportSpecies()
                        }
                    }
                    layout.addView(exportSpeciesLink)
                }

                val importSpeciesLink = TextView(activity).apply {
                    text = activity.getString(R.string.import_species_settings)
                    textSize = 18f
                    setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                    setPadding(0, 20, 0, 40)
                    val outValue = android.util.TypedValue()
                    activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                    setOnClickListener {
                        val d = AlertDialog.Builder(activity)
                            .setMessage(R.string.import_species_confirm)
                            .setPositiveButton("Takaisin", null)
                            .setNegativeButton("Tuo") { _, _ ->
                                importExportManager.launchImportSpecies()
                            }
                            .create()
                        showDialog(d)
                    }
                }
                layout.addView(importSpeciesLink)

                if (isModified) {
                    val resetSpeciesLink = TextView(activity).apply {
                        text = activity.getString(R.string.reset_default_species)
                        textSize = 18f
                        setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                        setPadding(0, 20, 0, 40)
                        val outValue = android.util.TypedValue()
                        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                        setBackgroundResource(outValue.resourceId)
                        setOnClickListener {
                            val d = AlertDialog.Builder(activity)
                                .setMessage(R.string.reset_species_confirm)
                                .setPositiveButton("Takaisin", null)
                                .setNegativeButton("Palauta") { _, _ ->
                                    activity.lifecycleScope.launch(Dispatchers.IO) {
                                        db.fishSpeciesDao().deleteAll()
                                        fi.anssi.kalakartta.data.FishSpecies.getDefaultList().forEach {
                                            db.fishSpeciesDao().insert(it)
                                        }
                                        withContext(Dispatchers.Main) {
                                            onDataChanged(true)
                                            Toast.makeText(activity, "Oletukset palautettu", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .create()
                            showDialog(d)
                        }
                    }
                    layout.addView(resetSpeciesLink)
                }

                val dialog = AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.fish_species_settings))
                    .setView(layout)
                    .setPositiveButton("Takaisin") { _, _ -> openSettings() }
                    .create()
                showDialog(dialog)
            }
        }
    }

    fun openFishingSessionSettings() {
        val mainActivity = activity as? fi.anssi.kalakartta.MainActivity
        val fishingService = mainActivity?.getFishingService()
        val isRecording = fishingService?.isRecording() ?: false

        val typedValue = android.util.TypedValue()
        activity.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val primaryTextColor = if (typedValue.resourceId != 0) {
            activity.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }

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
                    textSize = 16f
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
                textSize = 16f
                setTextColor(primaryTextColor)

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

            val trackingSettingsLink = TextView(activity).apply {
                text = "Reittipisteiden tallennusvälit"
                textSize = 16f
                setTextColor(primaryTextColor)
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
                    openTrackingIntervalSettings()
                }
            }
            contentLayout.addView(trackingSettingsLink)

            val statusText = TextView(activity).apply {
                textSize = 14f
                setTextColor(Color.RED)
                setPadding(0, 10, 0, 10)
                visibility = View.GONE
            }
            contentLayout.addView(statusText)

            val showLiveRouteCb = CheckBox(activity).apply {
                text = "Näytä tallennettavan session reitti"
                isChecked = settingsStore.showLiveSessionRoute
                textSize = 16f
                setOnCheckedChangeListener { _, isChecked ->
                    settingsStore.showLiveSessionRoute = isChecked
                    mainActivity?.updateSessionLine()
                }
            }
            contentLayout.addView(showLiveRouteCb)

            val startButton = TextView(activity).apply {
                text = "Aloita tallennus"
                textSize = 16f
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
                    val locInt = settingsStore.locationCheckInterval
                    val minInt = settingsStore.minTrackPointInterval
                    val maxInt = settingsStore.maxTrackPointInterval
                    val minDist = settingsStore.minTrackPointDistance
                    
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
                    showLiveRouteCb.visibility = if (locationEnabled) View.VISIBLE else View.GONE
                    trackingSettingsLink.visibility = if (locationEnabled) View.VISIBLE else View.GONE
                    
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
                .create()
            showDialog(dialog!!)
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

            val showLiveRouteCb = CheckBox(activity).apply {
                text = "Näytä tallennettavan session reitti"
                isChecked = settingsStore.showLiveSessionRoute
                textSize = 16f
                setOnCheckedChangeListener { _, isChecked ->
                    settingsStore.showLiveSessionRoute = isChecked
                    mainActivity?.updateSessionLine()
                }
            }
            contentLayout.addView(showLiveRouteCb)
            
            val stopButton = TextView(activity).apply {
                text = "Lopeta tallennus"
                textSize = 16f
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
                .create()
            showDialog(dialog!!)
        }
    }

    private fun openTrackingIntervalSettings() {
        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }

        val sectionTitle = TextView(activity).apply {
            text = "Reittipisteiden tallennusvälit"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        contentLayout.addView(sectionTitle)

        val locationIntervals = arrayOf("10", "30", "60", "120")
        val minIntervals = arrayOf("10", "30", "60", "120")
        val maxIntervals = arrayOf("60", "120", "300", "600")
        val minDistances = arrayOf("10", "20", "50", "100", "200")

        var currentLocationInterval = settingsStore.locationCheckInterval.toString()
        var currentMinInterval = settingsStore.minTrackPointInterval.toString()
        var currentMaxInterval = settingsStore.maxTrackPointInterval.toString()
        var currentMinDistance = settingsStore.minTrackPointDistance.toString()

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

            if (maxVal < minVal) {
                currentMinInterval = currentMaxInterval
                minIntervalSpinner.setSelection(minIntervals.indexOf(currentMinInterval))
                settingsStore.minTrackPointInterval = currentMinInterval.toInt()
            }
            
            val newMinVal = currentMinInterval.toInt()
            if (newMinVal < locVal) {
                currentLocationInterval = currentMinInterval
                locationSpinner.setSelection(locationIntervals.indexOf(currentLocationInterval))
                settingsStore.locationCheckInterval = currentLocationInterval.toInt()
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
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            spinner.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            row.addView(label)
            row.addView(spinner)
            return row
        }

        contentLayout.addView(createRow("Sijainnin tarkastuksen aikaväli (s)", locationSpinner.apply {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, locationIntervals)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(locationIntervals.indexOf(currentLocationInterval))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    currentLocationInterval = locationIntervals[pos]
                settingsStore.locationCheckInterval = currentLocationInterval.toInt()
                    updateSpinners()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }))

        contentLayout.addView(createRow("Tallennusaikaväli min (s)", minIntervalSpinner.apply {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, minIntervals)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(minIntervals.indexOf(currentMinInterval))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    currentMinInterval = minIntervals[pos]
                    settingsStore.minTrackPointInterval = currentMinInterval.toInt()
                    updateSpinners()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }))

        contentLayout.addView(createRow("Tallennusaikaväli max (s)", maxIntervalSpinner.apply {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, maxIntervals)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(maxIntervals.indexOf(currentMaxInterval))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    currentMaxInterval = maxIntervals[pos]
                settingsStore.maxTrackPointInterval = currentMaxInterval.toInt()
                    updateSpinners()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }))

        contentLayout.addView(createRow("Pisteiden minimietäisyys (m)", minDistanceSpinner.apply {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, minDistances)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(minDistances.indexOf(currentMinDistance))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    currentMinDistance = minDistances[pos]
                settingsStore.minTrackPointDistance = currentMinDistance.toInt()
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
                
                settingsStore.locationCheckInterval = SettingsDefaults.LOCATION_CHECK_INTERVAL
                settingsStore.minTrackPointInterval = SettingsDefaults.MIN_TRACK_POINT_INTERVAL
                settingsStore.maxTrackPointInterval = SettingsDefaults.MAX_TRACK_POINT_INTERVAL
                settingsStore.minTrackPointDistance = SettingsDefaults.MIN_TRACK_POINT_DISTANCE

                locationSpinner.setSelection(locationIntervals.indexOf(currentLocationInterval))
                minIntervalSpinner.setSelection(minIntervals.indexOf(currentMinInterval))
                maxIntervalSpinner.setSelection(maxIntervals.indexOf(currentMaxInterval))
                minDistanceSpinner.setSelection(minDistances.indexOf(currentMinDistance))
            }
        }
        contentLayout.addView(resetDefaultsLink)

        val dialog = AlertDialog.Builder(activity)
            .setView(scrollView)
            .setPositiveButton("Takaisin") { _, _ -> openFishingSessionSettings() }
            .create()
        showDialog(dialog)
    }

    private fun openDefaultFishermanSettings() {
        val currentFisherman = settingsStore.defaultFisherman
        val showOnMap = settingsStore.showFishermanOnMap

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
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    settingsStore.defaultFisherman = s?.toString()?.trim() ?: SettingsDefaults.DEFAULT_FISHERMAN
                    onMapSettingsChanged()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        layout.addView(input)

        val checkBox = CheckBox(activity).apply {
            text = "Näytä oletuskalastajan nimi kartalla"
            isChecked = showOnMap
            setPadding(0, 20, 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.showFishermanOnMap = isChecked
                onMapSettingsChanged()
            }
        }
        layout.addView(checkBox)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Oletuskalastaja")
            .setView(layout)
            .setPositiveButton("Takaisin") { _, _ -> openGeneralSettings() }
            .create()
        showDialog(dialog)
    }
}
