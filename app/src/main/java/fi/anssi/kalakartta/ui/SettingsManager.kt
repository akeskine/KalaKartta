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
import fi.anssi.kalakartta.MainActivity
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
    private val heatmapSettingsDialog: HeatmapSettingsDialog by lazy {
        HeatmapSettingsDialog(
            activity = activity,
            settingsStore = settingsStore,
            checkLimitsCallback = { checkHeatmap, checkRoutes, newGridSize, providedFilters, providedRemoveTransitions, providedMaxSpeed, onResult ->
                checkLimits(
                    checkHeatmap = checkHeatmap,
                    checkRoutes = checkRoutes,
                    newGridSize = newGridSize,
                    providedFilters = providedFilters,
                    providedRemoveTransitions = providedRemoveTransitions,
                    providedMaxSpeed = providedMaxSpeed,
                    onResult = onResult
                )
            },
            onMapSettingsChanged = onMapSettingsChanged,
            onOpenAdvancedSettings = { heatmapSettingsDialog.showAdvancedSettings() },
            onOpenRouteAdvancedSettings = { heatmapSettingsDialog.showRouteAdvancedSettings() },
            onOpenSettings = { openSettings() },
            onShowDialog = ::showDialog
        )
    }
    private val mapSettingsDialog by lazy {
        MapSettingsDialog(
            activity = activity,
            settingsStore = settingsStore,
            onMapSettingsChanged = onMapSettingsChanged,
            onOpenSettings = { openSettings() },
            onShowDialog = ::showDialog
        )
    }
    private val mapDisplaySettingsDialog: MapDisplaySettingsDialog by lazy {
        MapDisplaySettingsDialog(
            activity = activity,
            settingsStore = settingsStore,
            onMapSettingsChanged = onMapSettingsChanged,
            onOpenSettings = { generalSettingsDialog.show() },
            onShowDialog = ::showDialog
        )
    }
    private val weatherSettingsDialog: WeatherSettingsDialog by lazy {
        WeatherSettingsDialog(
            activity = activity,
            settingsStore = settingsStore,
            onWeatherSettingsChanged = onWeatherSettingsChanged,
            onOpenSettings = { generalSettingsDialog.show() },
            onShowDialog = ::showDialog
        )
    }
    private val talkingClockSettingsDialog: TalkingClockSettingsDialog by lazy {
        TalkingClockSettingsDialog(
            activity = activity,
            settingsStore = settingsStore,
            onOpenSettings = { generalSettingsDialog.show() },
            onShowDialog = ::showDialog
        )
    }
    private val generalSettingsDialog: GeneralSettingsDialog by lazy {
        GeneralSettingsDialog(
            activity = activity,
            onOpenDefaultFisherman = ::openDefaultFishermanSettings,
            onOpenWeather = { weatherSettingsDialog.show() },
            onOpenScale = { mapDisplaySettingsDialog.showScaleSettings() },
            onOpenAutoCenter = { mapDisplaySettingsDialog.showAutoCenterSettings() },
            onOpenTalkingClock = ::openTalkingClockSettingsIfPermissionsOk,
            onOpenIconSizes = { mapDisplaySettingsDialog.showIconSizeSettings() },
            onOpenDeveloperTools = ::openDeveloperTools,
            onOpenSettings = { openSettings() },
            onShowDialog = ::showDialog
        )
    }

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
                            0 -> mapSettingsDialog.show()
                            1 -> openFishingSessionSettings()
                            2 -> heatmapSettingsDialog.show()
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
                            8 -> generalSettingsDialog.show()
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
                generalSettingsDialog.show()
            }
            .setNegativeButton("Takaisin") { _, _ -> generalSettingsDialog.show() }
            .create()
        showDialog(dialog)
    }

    fun openTalkingClockSettingsIfPermissionsOk() {
        if (hasTalkingClockPermissions()) {
            talkingClockSettingsDialog.show()
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
        talkingClockSettingsDialog.show()
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
            .setPositiveButton("Takaisin") { _, _ -> generalSettingsDialog.show() }
            .create()
        showDialog(dialog)
    }
}
