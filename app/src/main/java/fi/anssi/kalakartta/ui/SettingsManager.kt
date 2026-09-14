package fi.anssi.kalakartta.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.*
import fi.anssi.kalakartta.BuildConfig
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.io.ImportExportManager
import fi.anssi.kalakartta.utils.enlargeButtons
import fi.anssi.kalakartta.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsManager(
    private val activity: AppCompatActivity,
    private val db: AppDatabase,
    private val importExportManager: ImportExportManager,
    private val onWeatherSettingsChanged: (Boolean) -> Unit = {},
    private val onMapSettingsChanged: () -> Unit = {},
    private val onSettingsActivityResult: (requestCode: Int, resultCode: Int, data: Intent?) -> Unit = { _, _, _ -> },
    private val onDataChanged: (forceRefreshSpecies: Boolean) -> Unit
) {

    private var currentDialog: AlertDialog? = null
    private var pendingActivityResultRequestCode: Int? = null
    private val settingsActivityLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val requestCode = pendingActivityResultRequestCode ?: return@registerForActivityResult
        pendingActivityResultRequestCode = null
        onSettingsActivityResult(requestCode, result.resultCode, result.data)
    }
    private val settingsStore = SettingsStore(activity.getSharedPreferences("settings", AppCompatActivity.MODE_PRIVATE))
    private val heatmapLimitsChecker by lazy {
        HeatmapLimitsChecker(activity, db, activity.lifecycleScope)
    }
    private val userManualDialog by lazy {
        UserManualDialog(
            activity = activity,
            settingsStore = settingsStore,
            onCloseSettings = ::closeSettings,
            onStartupDialogShown = { dialog -> currentDialog = dialog }
        )
    }
    private val heatmapSettingsDialog: HeatmapSettingsDialog by lazy {
        HeatmapSettingsDialog(
            activity = activity,
            settingsStore = settingsStore,
            checkLimitsCallback = { checkHeatmap, checkRoutes, newGridSize, providedFilters, providedRemoveTransitions, providedMaxSpeed, onResult ->
                heatmapLimitsChecker.check(
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
            onOpenDefaultFisherman = { fishermanSettingsDialog.show() },
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
    private val fishermanSettingsDialog by lazy {
        FishermanSettingsDialog(
            activity = activity,
            settingsStore = settingsStore,
            onMapSettingsChanged = onMapSettingsChanged,
            onOpenGeneralSettings = { generalSettingsDialog.show() },
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
    private val speciesSettingsDialog: SpeciesSettingsDialog by lazy {
        SpeciesSettingsDialog(
            activity = activity,
            db = db,
            importExportManager = importExportManager,
            onDataChanged = onDataChanged,
            onOpenSettings = { openSettings() },
            onShowDialog = ::showDialog
        )
    }

    fun closeSettings() {
        currentDialog?.dismiss()
        currentDialog = null
    }

    private fun showDialog(dialog: AlertDialog) {
        currentDialog?.dismiss()
        currentDialog = dialog
        dialog.show()
        dialog.enlargeButtons()
    }

    private fun launchSettingsActivity(intent: Intent, requestCode: Int) {
        pendingActivityResultRequestCode = requestCode
        settingsActivityLauncher.launch(intent)
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
                    subtitleText.setTextColor(androidx.core.content.ContextCompat.getColor(activity, android.R.color.darker_gray))

                    val contentLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(60, 20, 60, 20)
                    }

                    val helpLink = menuLinkTextView(activity).apply {
                        text = "Käyttöohje"
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
                            3 -> launchSettingsActivity(Intent(activity, FilterActivity::class.java), 2001)
                            4 -> launchSettingsActivity(Intent(activity, SummaryActivity::class.java), 2002)
                            5 -> launchSettingsActivity(Intent(activity, DiaryActivity::class.java), 2003)
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
                            7 -> speciesSettingsDialog.show()
                            8 -> generalSettingsDialog.show()
                        }
                    }
                    .setPositiveButton("Takaisin", null)
                    .create()
                showDialog(dialog)
            }
        }
    }

    fun showUserManual(isStartup: Boolean = false) = userManualDialog.show(isStartup)

    fun checkShowUserManual() = userManualDialog.checkShow()

    private fun disableHeatmapAndRoutes() {
        settingsStore.heatmapEnabled = SettingsDefaults.HEATMAP_ENABLED
        settingsStore.fishingRoutesEnabled = SettingsDefaults.FISHING_ROUTES_ENABLED
        onMapSettingsChanged()
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
        heatmapLimitsChecker.check(
            checkHeatmap,
            checkRoutes,
            newGridSize,
            providedFilters,
            providedRemoveTransitions,
            providedMaxSpeed,
            onResult
        )
    }

    companion object {
        fun checkLimits(
            context: Context,
            db: AppDatabase,
            lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope,
            checkHeatmap: Boolean,
            checkRoutes: Boolean,
            newGridSize: Double? = null,
            providedFilters: FilterManager.Filters? = null,
            providedRemoveTransitions: Boolean? = null,
            providedMaxSpeed: Float? = null,
            onResult: (success: Boolean) -> Unit
        ) {
            HeatmapLimitsChecker(context, db, lifecycleScope).check(
                checkHeatmap,
                checkRoutes,
                newGridSize,
                providedFilters,
                providedRemoveTransitions,
                providedMaxSpeed,
                onResult
            )
        }
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


}
