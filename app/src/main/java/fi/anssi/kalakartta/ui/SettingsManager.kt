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
import kotlinx.coroutines.Dispatchers
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
            onOpenDeveloperTools = { developerSettingsDialog.show() },
            onOpenSettings = { openSettings() },
            onShowDialog = ::showDialog
        )
    }
    private val developerSettingsDialog: DeveloperSettingsDialog by lazy {
        DeveloperSettingsDialog(
            activity = activity,
            db = db,
            settingsStore = settingsStore,
            onOpenGeneralSettings = { generalSettingsDialog.show() },
            onShowDialog = ::showDialog
        )
    }
    private val dataTransferSettingsDialog: DataTransferSettingsDialog by lazy {
        DataTransferSettingsDialog(
            activity = activity,
            db = db,
            importExportManager = importExportManager,
            onDisableHeatmapAndRoutes = ::disableHeatmapAndRoutes,
            onDataChanged = onDataChanged,
            onOpenSettings = { openSettings() },
            onShowDialog = ::showDialog
        )
    }
    private val fishingSessionSettingsDialog: FishingSessionSettingsDialog by lazy {
        FishingSessionSettingsDialog(
            activity = activity,
            db = db,
            settingsStore = settingsStore,
            onOpenSettings = { openSettings() },
            onShowDialog = ::showDialog,
            onCloseSettings = ::closeSettings,
            onDialogDismissed = { dismissed ->
                if (currentDialog === dismissed) currentDialog = null
            }
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
                            1 -> fishingSessionSettingsDialog.show()
                            2 -> heatmapSettingsDialog.show()
                            3 -> openFilterSettings()
                            4 -> openSummary()
                            5 -> openDiary()
                            6 -> dataTransferSettingsDialog.show(
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
