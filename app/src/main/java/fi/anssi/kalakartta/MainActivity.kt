package fi.anssi.kalakartta

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.osmdroid.config.Configuration
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.FolderOverlay
import android.graphics.Color
import android.widget.LinearLayout
import org.osmdroid.views.MapView
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import androidx.core.content.ContextCompat
import fi.anssi.kalakartta.io.ImportExportManager
import fi.anssi.kalakartta.data.*
import fi.anssi.kalakartta.ui.SettingsManager
import fi.anssi.kalakartta.ui.CatchManager
import fi.anssi.kalakartta.ui.MarkerManager
import fi.anssi.kalakartta.ui.FilterManager
import fi.anssi.kalakartta.ui.SettingsDefaults
import fi.anssi.kalakartta.ui.SettingsStore
import fi.anssi.kalakartta.ui.WindDirectionView
import fi.anssi.kalakartta.utils.SessionStatsFormatter
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import fi.anssi.kalakartta.service.FishingSessionService
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import fi.anssi.kalakartta.ui.SessionReplayResult
import fi.anssi.kalakartta.ui.MapDisplayController
import fi.anssi.kalakartta.ui.MeasurementController
import fi.anssi.kalakartta.ui.FishingSessionController
import fi.anssi.kalakartta.ui.WeatherController
import fi.anssi.kalakartta.ui.LocationController
import fi.anssi.kalakartta.ui.ReplayMapController
import fi.anssi.kalakartta.ui.MapNavigationController

class MainActivity : AppCompatActivity() {

    private val settingsStore by lazy {
        SettingsStore(getSharedPreferences("settings", MODE_PRIVATE))
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Jos lupa annettiin, kokeillaan avata asetukset uudelleen
            // Mutta tarkistetaan vielä Alarm-lupa jos tarpeen
            settingsManager.openTalkingClockSettingsIfPermissionsOk()
        } else {
            Toast.makeText(this, R.string.talking_clock_permission_notifications, Toast.LENGTH_LONG).show()
        }
    }

    private val fishingSessionActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleActivityResult(3001, result.resultCode, result.data)
    }

    private var pendingActivityResultRequestCode: Int? = null
    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val requestCode = pendingActivityResultRequestCode ?: return@registerForActivityResult
        pendingActivityResultRequestCode = null
        handleActivityResult(requestCode, result.resultCode, result.data)
    }

    fun getNotificationPermissionLauncher() = requestNotificationPermissionLauncher

    fun launchFishingSessionActivity(intent: Intent) {
        fishingSessionActivityLauncher.launch(intent)
    }

    fun launchActivityForResult(intent: Intent, requestCode: Int) {
        pendingActivityResultRequestCode = requestCode
        activityResultLauncher.launch(intent)
    }

    private lateinit var db: AppDatabase
    private lateinit var importExportManager: ImportExportManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var catchManager: CatchManager
    private lateinit var markerManager: MarkerManager
    private lateinit var filterManager: FilterManager
    private lateinit var weatherController: WeatherController
    private lateinit var map: MapView
    private lateinit var mapDisplayController: MapDisplayController
    private lateinit var measurementController: MeasurementController
    private lateinit var fishingSessionController: FishingSessionController
    private lateinit var locationController: LocationController
    private lateinit var replayMapController: ReplayMapController
    private lateinit var mapNavigationController: MapNavigationController
    
    private fun addOverlayBelowMarkers(overlay: Overlay) {
        var index = -1
        for (i in 0 until map.overlays.size) {
            if (map.overlays[i] is FolderOverlay) {
                index = i
                break
            }
        }
        if (index != -1) {
            map.overlays.add(index, overlay)
        } else {
            map.overlays.add(overlay)
        }
    }

    private var isSelectionMode = false


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::replayMapController.isInitialized) {
            replayMapController.saveState(outState)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val crashFile = java.io.File(filesDir, "startup-crash.txt")

        if (crashFile.exists()) {
            val errorText = try {
                crashFile.readText()
            } catch (e: Exception) {
                "Virheen lukeminen epäonnistui"
            }
            AlertDialog.Builder(this)
                .setTitle("Edellinen käynnistys kaatui")
                .setMessage(errorText)
                .setPositiveButton("OK") { _, _ ->
                    crashFile.delete()
                }
                .setCancelable(false)
                .show()
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }

            super.onCreate(savedInstanceState)

            android.util.Log.d("KalaKartta", "before config init")
            Configuration.getInstance().userAgentValue = packageName
            setContentView(R.layout.activity_main)

            android.util.Log.d("KalaKartta", "before map init")
            map = findViewById(R.id.map)

            // Alustetaan tietokanta ja managerit ennen UI-päivityksiä (kuten updateMapTileSource)
            // jotta ne eivät kaadu lateinit-virheisiin (esim. heatmap)
            android.util.Log.d("KalaKartta", "before db init")
            try {
                db = AppDatabase.getInstance(this)
            } catch (e: Exception) {
                android.util.Log.e("KalaKartta", "Database initialization failed", e)
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)

                AlertDialog.Builder(this)
                    .setTitle("Tietokantavirhe")
                    .setMessage("Tietokannan avaaminen epäonnistui. Tämä johtuu yleensä sovelluspäivityksen yhteydessä tapahtuneesta migraatiovirheestä.\n\nVirhe: ${e.localizedMessage}\n\nJos virhe toistuu, voit yrittää poistaa sovelluksen ja asentaa sen uudelleen (huom: tiedot katoavat).")
                    .setPositiveButton("OK", null)
                    .show()

                // Luodaan tyhjä in-memory tietokanta, jotta sovellus ei kaadu heti kaikkialla
                db = androidx.room.Room.inMemoryDatabaseBuilder(
                    applicationContext,
                    AppDatabase::class.java
                ).build()
            }
            android.util.Log.d("KalaKartta", "after db init")

            filterManager = FilterManager(this)
            locationController = LocationController(
                activity = this,
                map = map,
                settingsStore = settingsStore,
                isSelectionMode = { isSelectionMode }
            )
            weatherController = WeatherController(
                activity = this,
                settingsStore = settingsStore,
                scope = lifecycleScope,
                locationProvider = { locationController.currentLocation }
            )

            measurementController = MeasurementController(
                activity = this,
                map = map,
                addOverlayBelowMarkers = { overlay -> addOverlayBelowMarkers(overlay) },
                createMeasurementPinBitmap = { color -> mapDisplayController.createMeasurementPinBitmap(color) }
            )

            mapDisplayController = MapDisplayController(
                activity = this,
                map = map,
                settingsStore = settingsStore,
                database = db,
                measurementPointCount = { measurementController.pointCount },
                clearMeasurement = { measurementController.clear() },
                onDefaultFishermanChanged = { updateDefaultFishermanUI() }
            )

            fishingSessionController = FishingSessionController(
                activity = this,
                map = map,
                database = db,
                settingsStore = settingsStore,
                scope = lifecycleScope,
                addOverlayBelowMarkers = { overlay -> addOverlayBelowMarkers(overlay) },
                hideArchivedSession = { hideArchivedSession() },
                onSessionEnded = { sessionId, durationMs, distanceM ->
                    showSessionNotesDialog(sessionId, durationMs, distanceM)
                },
                onLocationDisabled = {
                    AlertDialog.Builder(this)
                        .setTitle("Kalastussessio lopetettu")
                        .setMessage("Kalastussessio on lopetettu, koska sijaintipalvelu on pois päältä.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            )

            importExportManager = ImportExportManager(this, db) { forceRefreshSpecies ->
                reloadMarkersFromDb(forceRefreshSpecies)
            }

            settingsManager = SettingsManager(this, db, importExportManager, onWeatherSettingsChanged = { isEnabled ->
                if (isEnabled) {
                    checkWeather(force = true)
                } else {
                    updateWeatherUI()
                }
            }, onMapSettingsChanged = {
                updateMapTileSource()
            }, onSettingsActivityResult = { requestCode, resultCode, data ->
                handleActivityResult(requestCode, resultCode, data)
            }) { forceRefreshSpecies ->
                reloadMarkersFromDb(forceRefreshSpecies)
            }

            onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (findViewById<android.view.View>(R.id.replayPlayerLayout).visibility == android.view.View.VISIBLE) {
                        val sessionId = replayMapController.getVisibleArchivedSessionId()
                        hideArchivedSession()
                        
                        // Avataan asetukset ja sessioiden listaus
                        settingsManager.openSettings()
                        
                        // Avataan FishingSessionActivity suoraan oikealla ID:llä
                        val intent = Intent(this@MainActivity, fi.anssi.kalakartta.ui.FishingSessionActivity::class.java)
                        intent.putExtra("EXTRA_OPEN_SESSION_ID", sessionId)
                        fishingSessionActivityLauncher.launch(intent)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            })

            // Tehtävä 1: Sovelluksen käynnistyessä aseta aina heat map-ruutujen ja reittien näyttäminen pois päältä.
            if (savedInstanceState == null) {
                settingsStore.heatmapEnabled = SettingsDefaults.HEATMAP_ENABLED
                settingsStore.fishingRoutesEnabled = SettingsDefaults.FISHING_ROUTES_ENABLED
            }

            // Migraatio vanhasta pikanäppäin-asetuksesta
            if (!settingsStore.hasHeatmapShortcutModeSetting()) {
                val oldVal = settingsStore.showHeatmapShortcut
                val newVal = if (oldVal) 3 else 0
                settingsStore.heatmapShortcutMode = newVal
            }

            markerManager = MarkerManager(this, map, db) { marker ->
                val fish = marker.relatedObject as? FishCatch
                val place = marker.relatedObject as? PlaceOfInterest

                // Poistetaan välittömästi MarkerManagerin listoista ja kartalta,
                // jotta onScroll/rebuildMarkers ei tuo sitä takaisin tietokantapoiston aikana.
                markerManager.removeMarker(marker)

                lifecycleScope.launch {
                    val deletedId = fish?.id ?: place?.id
                    android.util.Log.d("MainActivity", "Deleting from DB: ID=$deletedId")
                    withContext(Dispatchers.IO) {
                        if (fish != null) {
                            db.fishCatchDao().deleteById(fish.id)
                        }
                        if (place != null) {
                            db.placeOfInterestDao().deleteById(place.id)
                        }
                    }
                    // Lisätään väliaikainen ilmoitus käyttäjän pyynnöstä
                    android.util.Log.d("MainActivity", "Deleted from DB")
                    android.widget.Toast.makeText(this@MainActivity, "Piste poistettu", android.widget.Toast.LENGTH_SHORT).show()

                    // Kun poisto on valmistunut tietokannassa, ladataan listat uudelleen.
                    // MarkerManager pitää huolen että poistettu ID ei näy väliaikanakaan.
                    reloadMarkersFromDb()
                }
            }

            replayMapController = ReplayMapController(
                activity = this,
                map = map,
                database = db,
                markerManager = markerManager,
                scope = lifecycleScope,
                addOverlayBelowMarkers = { overlay -> addOverlayBelowMarkers(overlay) },
                onReplayVisibilityChanged = { updateMyLocationButtonVisibility() }
            )

            catchManager = CatchManager(this, map, db, weatherController.weatherService,
                onCatchAdded = { fish ->
                    markerManager.addOrUpdateMarkerIncremental(fish, map.zoomLevelDouble, filterManager)
                },
                onPlaceAdded = { place ->
                    markerManager.addOrUpdatePlaceIncremental(place, map.zoomLevelDouble)
                }
            )

            mapNavigationController = MapNavigationController(
                map = map,
                database = db,
                filterManager = filterManager,
                scope = lifecycleScope,
                closeSettings = { settingsManager.closeSettings() },
                reloadMarkers = { reloadMarkersFromDb() },
                updateFilterStatus = { updateFilterStatusUI() }
            )

            updateMapTileSource()
            map.setMultiTouchControls(true)
            map.zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )

            isSelectionMode = intent.getBooleanExtra("EXTRA_SELECTION_MODE", false)

            if (isSelectionMode) {
                val statePrefs = getSharedPreferences("map_state", MODE_PRIVATE)
                val lat = statePrefs.getFloat("lat", 60.1695f).toDouble()
                val lon = statePrefs.getFloat("lon", 24.9354f).toDouble()
                val zoom = statePrefs.getFloat("zoom", 15.0f).toDouble()
                map.controller.setZoom(zoom)
                map.controller.setCenter(org.osmdroid.util.GeoPoint(lat, lon))
            } else {
                map.controller.setZoom(15.0)
                // Asetetaan alkusijainti Helsingin keskustaan, jos omaa sijaintia ei vielä ole
                val helsinkiCenter = org.osmdroid.util.GeoPoint(60.1695, 24.9354)
                map.controller.setCenter(helsinkiCenter)
            }

            locationController.initialize()
            map.setOnTouchListener { _, event ->
                locationController.onMapTouch(event)
            }
            findViewById<MaterialButton>(R.id.addCatchButton).setOnClickListener {
                catchManager.showSpeciesDialog()
            }

            findViewById<MaterialButton>(R.id.heatmapShortcutButton).setOnClickListener {
                val shortcutMode = settingsStore.heatmapShortcutMode
                val heatmapEnabled = settingsStore.heatmapEnabled
                val routesEnabled = settingsStore.fishingRoutesEnabled

                val (newHeatmap, newRoutes) = when (shortcutMode) {
                    1 -> Pair(!heatmapEnabled, routesEnabled) // Kalastetut alueet: kytkee heat mapin päälle/pois
                    2 -> Pair(heatmapEnabled, !routesEnabled) // Reitit: kytkee reitit päälle/pois
                    3 -> when { // Kalastetut alueet ja reitit: sykli
                        !heatmapEnabled && !routesEnabled -> Pair(true, false)
                        heatmapEnabled && !routesEnabled -> Pair(true, true)
                        heatmapEnabled && routesEnabled -> Pair(false, true)
                        else -> Pair(false, false)
                    }
                    else -> Pair(heatmapEnabled, routesEnabled)
                }

                // Tarkista rajat ennen päälle kytkemistä
                val checkingHeatmap = newHeatmap && !heatmapEnabled
                val checkingRoutes = newRoutes && !routesEnabled
                
                if (checkingHeatmap || checkingRoutes) {
                    settingsManager.checkLimits(checkingHeatmap, checkingRoutes) { success ->
                        if (success) {
                            settingsStore.heatmapEnabled = newHeatmap
                            settingsStore.fishingRoutesEnabled = newRoutes
                            updateFishingHeatmap()
                        }
                    }
                } else {
                    settingsStore.heatmapEnabled = newHeatmap
                    settingsStore.fishingRoutesEnabled = newRoutes
                    updateFishingHeatmap()
                }
            }

            locationController.setupMyLocationButton()
            // Palautetaan toisto-tila
            if (savedInstanceState != null) {
                replayMapController.restoreState(savedInstanceState)
            }

            findViewById<MaterialButton?>(R.id.settingsButton)?.setOnClickListener {
                if (filterManager.hasActiveFilters()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val catches = db.fishCatchDao().getAll()
                        val filteredCatches = filterManager.applyFilter(catches)
                        val places = db.placeOfInterestDao().getAll()
                        val filteredPlaces = filterManager.applyPlaceFilter(places)
                        withContext(Dispatchers.Main) {
                            settingsManager.openSettings(true, filteredCatches, filteredPlaces)
                        }
                    }
                } else {
                    settingsManager.openSettings()
                }
            }

            measurementController.setupControls()
            findViewById<MaterialButton>(R.id.quickMapSourceButton).setOnClickListener {
                val currentApiKey = settingsStore.mmlApiKey
                val currentSource = settingsStore.mapSource

                val internalIds = arrayOf("OSM", "MML_MAASTO", "MML_ILMA", "TRAFICOM_SEA", "TRAFICOM_BOATING")

                // Suodatetaan karttapohjat, jotka on valittu pikavalintaan
                val enabledSources = internalIds.filter { id ->
                    val default = if (id.startsWith("MML_")) currentApiKey.isNotEmpty() else true
                    settingsStore.isQuickMapSourceEnabled(id, default)
                }

                if (enabledSources.isNotEmpty()) {
                    val currentIndex = enabledSources.indexOf(currentSource)
                    val nextIndex = (currentIndex + 1) % enabledSources.size
                    val nextSource = enabledSources[nextIndex]

                    settingsStore.mapSource = nextSource
                    updateMapTileSource()
                }
            }

            android.util.Log.d("KalaKartta", "after db init")
            
            // Esitäyttö taustasäikeessä
            lifecycleScope.launch(Dispatchers.IO) {
                db.initializeDefaults()
            }

            map.addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    // Jos käyttäjä skrollaa itse, poistetaan automaattinen seuranta
                    if (locationController.isUserScrolling) {
                        updateHeatmapDelayed()
                        locationController.disableFollowLocation()
                    }

                    // Kun ollaan zoomed in, päivitetään näkyvät markerit (clipping)
                    if (map.zoomLevelDouble >= 13.0) {
                        markerManager.setMarkersVisible(true, map.zoomLevelDouble, forceRebuild = true)
                    }

                    measurementController.onMapMoved()
                    return false
                }
                override fun onZoom(event: ZoomEvent?): Boolean {
                    updateHeatmapDelayed()
                    updateMarkersVisibility()
                    return true
                }
            })

            android.util.Log.d("KalaKartta", "before loadCatches")
            loadCatches()
            android.util.Log.d("KalaKartta", "after loadCatches")

            updateMarkersVisibility()
            updateFilterStatusUI()
            updateDefaultFishermanUI()

            isSelectionMode = intent.getBooleanExtra("EXTRA_SELECTION_MODE", false)
            if (isSelectionMode) {
                findViewById<android.view.View>(R.id.measurementButton).visibility = android.view.View.GONE
                // Nollataan aluerajaus valintatilaan mentäessä, jotta nähdään kaikki pisteet
                val currentFilters = filterManager.getFilters()
                if (currentFilters.latNorth != null) {
                    filterManager.saveFilters(currentFilters.copy(
                        latNorth = null, latSouth = null, lonEast = null, lonWest = null
                    ))
                    loadCatches() // Päivitetään näkyvät pisteet
                }

                findViewById<android.view.View>(R.id.selectionModeLayout).visibility = android.view.View.VISIBLE
                findViewById<android.view.View>(R.id.addCatchButton).visibility = android.view.View.GONE
                findViewById<android.view.View>(R.id.settingsButton).visibility = android.view.View.GONE
                findViewById<android.view.View>(R.id.mapCrosshair).visibility = android.view.View.GONE

                findViewById<android.widget.Button>(R.id.cancelSelectionButton).setOnClickListener {
                    setResult(RESULT_CANCELED)
                    finish()
                }

                findViewById<android.widget.Button>(R.id.confirmSelectionButton).setOnClickListener {
                    val bounds = map.boundingBox
                    val resultIntent = Intent().apply {
                        putExtra("EXTRA_LAT_NORTH", bounds.latNorth)
                        putExtra("EXTRA_LAT_SOUTH", bounds.latSouth)
                        putExtra("EXTRA_LON_EAST", bounds.lonEast)
                        putExtra("EXTRA_LON_WEST", bounds.lonWest)
                    }

                    // Kaapataan kuvakaappaus thumbnailia varten
                    try {
                        val bitmap = android.graphics.Bitmap.createBitmap(map.width, map.height, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)

                        // Piilotetaan mittaustyökalu kuvakaappauksen ajaksi
                        val mLayout = findViewById<LinearLayout>(R.id.measurementLayout)
                        val mButton = findViewById<MaterialButton>(R.id.measurementButton)
                        val oldLayoutVis = mLayout.visibility
                        val oldButtonVis = mButton.visibility
                        mLayout.visibility = android.view.View.GONE
                        mButton.visibility = android.view.View.GONE

                        map.draw(canvas)

                        mLayout.visibility = oldLayoutVis
                        mButton.visibility = oldButtonVis

                        // Pienennetään thumbnailia
                        val thumbnail = android.graphics.Bitmap.createScaledBitmap(bitmap, 160, 120, true)
                        val thumbFile = java.io.File(cacheDir, "area_thumb.jpg")
                        val out = java.io.FileOutputStream(thumbFile)
                        thumbnail.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                        out.flush()
                        out.close()
                        resultIntent.putExtra("EXTRA_THUMB_PATH", thumbFile.absolutePath)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Thumbnail capture failed", e)
                    }

                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }

            // Automaattinen kohdistus sovelluksen avauksessa
            val autoCenter = settingsStore.autoCenterOnStart

            // Tarkistetaan oletuskalastaja vain jos sovellus on asennettu tai päivitetty
            val lastVersionName = settingsStore.lastVersionName
            val currentVersionName = try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                pInfo.versionName ?: ""
            } catch (e: Exception) {
                ""
            }

            val currentFisherman = settingsStore.defaultFisherman
            if (currentVersionName != lastVersionName) {
                if (currentFisherman.isEmpty()) {
                    checkDefaultFisherman()
                }
                settingsStore.lastVersionName = currentVersionName
            }

            locationController.centerOnFirstFixIfNeeded()
            if (crashFile.exists()) {
                crashFile.delete()
            }
            
            checkUnfinishedSessions()
            settingsManager.checkShowUserManual()
        } catch (t: Throwable) {
            crashFile.writeText(t.stackTraceToString())
            throw t
        }
    }

    private fun updateMapTileSource() {
        mapDisplayController.updateMapTileSource()
    }

    private fun updateScaleBar() {
        mapDisplayController.updateScaleBar()
    }
    private fun loadCatches() {
        lifecycleScope.launch(Dispatchers.IO) {
            val catches = db.fishCatchDao().getAll()
            val filteredCatches = filterManager.applyFilter(catches)
            val places = db.placeOfInterestDao().getAll()
            val filteredPlaces = filterManager.applyPlaceFilter(places)
            withContext(Dispatchers.Main) {
                markerManager.setAllCatches(filteredCatches)
                markerManager.setAllPlaces(filteredPlaces)
            }
        }
    }

    private fun updateFilterStatusUI() {
        val layout = findViewById<android.view.View>(R.id.filterStatusLayout)
        val text = findViewById<android.widget.TextView>(R.id.filterStatusText)
        val windView = findViewById<WindDirectionView>(R.id.filterWindView)

        val filters = filterManager.getFilters()
        if (filterManager.hasActiveFilters()) {
            layout.visibility = android.view.View.VISIBLE
            if (filters.windMin != null && filters.windMax != null) {
                windView.visibility = android.view.View.VISIBLE
                windView.setRange(filters.windMin, filters.windMax)
            } else {
                windView.visibility = android.view.View.GONE
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val description = filterManager.getFilterDescription()
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed && filterManager.hasActiveFilters()) {
                        text.text = description
                    }
                }
            }
        } else {
            layout.visibility = android.view.View.GONE
        }
    }

    private fun reloadMarkersFromDb(forceRefreshSpecies: Boolean = false) {
        if (forceRefreshSpecies) {
            markerManager.rebuildMarkers(map.zoomLevelDouble, forceRefreshSpecies = true)
        }
        loadCatches()
        updateFishingHeatmap()
    }

    private fun checkDefaultFisherman() {
        val currentFisherman = settingsStore.defaultFisherman

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val input = android.widget.EditText(this).apply {
            setText(currentFisherman)
            hint = "Esim. Matti"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            }
        }
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Oletuskalastaja")
            .setMessage("Syötä oletuskalastajan nimi (valinnainen):")
            .setView(layout)
            .setPositiveButton("Tallenna") { _, _ ->
                val newFisherman = input.text.toString().trim()
                settingsStore.defaultFisherman = newFisherman
                updateDefaultFishermanUI()
            }
            .setNegativeButton("Ohita", null)
            .show()
    }

    private fun updateDefaultFishermanUI() {
        val rawFisherman = settingsStore.defaultFisherman
        val showOnMap = settingsStore.showFishermanOnMap
        val textView = findViewById<TextView>(R.id.defaultFishermanText) ?: return

        if (showOnMap && rawFisherman.isNotEmpty()) {
            fun formatName(name: String): String {
                return name.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { part ->
                    part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
            val fisherman = formatName(rawFisherman)
            textView.visibility = android.view.View.VISIBLE
            textView.text = fisherman

            val mapSource = settingsStore.mapSource
            val useBlack = mapSource == "MML_MAASTO" || mapSource == "MML_ILMA"
            val color = if (useBlack) {
                ContextCompat.getColor(this, android.R.color.black)
            } else {
                ContextCompat.getColor(this, android.R.color.white)
            }
            textView.setTextColor(color)

            // Säädetään marginaali vastaamaan mittakaavajanaa
            val density = resources.displayMetrics.density
            val buttonMargin = resources.getDimensionPixelSize(R.dimen.button_margin_bottom)
            val yOffset = buttonMargin - (22 * density).toInt()

            val params = textView.layoutParams as FrameLayout.LayoutParams
            params.bottomMargin = yOffset
            textView.layoutParams = params
        } else {
            textView.visibility = android.view.View.GONE
        }
    }

    private fun checkWeather(force: Boolean = false) {
        weatherController.checkWeather(force)
    }

    private fun updateWeatherUI() {
        weatherController.updateWeatherUi()
    }
    private fun updateFishingHeatmap() {
        mapDisplayController.updateFishingHeatmap()
    }
    private fun updateHeatmapDelayed() {
        mapDisplayController.updateHeatmapDelayed()
    }

    private fun updateMarkersVisibility() {

        // Näytetään pisteet laajemmalla zoom-alueella (alk. tasolta 1.0)
        // Optimointi on tehty MarkerManagerin kuvakevälimuistilla ja klusteroinnilla
        markerManager.setMarkersVisible(map.zoomLevelDouble >= 1.0, map.zoomLevelDouble)
    }

    override fun onStart() {
        super.onStart()
        fishingSessionController.onStart()
    }

    override fun onStop() {
        fishingSessionController.onStop()
        super.onStop()
    }
    fun updateSessionLine() {
        fishingSessionController.updateSessionLine()
    }

    fun startFishingSession(locationCheckInterval: Int, minInterval: Int, maxInterval: Int, minDistance: Int) {
        fishingSessionController.startFishingSession(locationCheckInterval, minInterval, maxInterval, minDistance)
    }

    fun stopFishingSession() {
        fishingSessionController.stopFishingSession()
    }

    private fun checkUnfinishedSessions() {
        fishingSessionController.checkUnfinishedSessions()
    }

    fun getFishingService(): FishingSessionService? = fishingSessionController.getFishingService()

    fun hideArchivedSession() {
        replayMapController.hideArchivedSession()
    }

    fun getVisibleArchivedSessionId(): Long = replayMapController.getVisibleArchivedSessionId()

    private fun showSessionNotesDialog(sessionId: Long, durationMs: Long, distanceM: Float) {
        if (isFinishing || isDestroyed) return
        
        lifecycleScope.launch {
            val pointCount = withContext(Dispatchers.IO) {
                db.trackPointDao().getPointCountForSession(sessionId)
            }

            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Kalastussessio lopetettu")
            
            val layout = android.widget.LinearLayout(this@MainActivity)
            layout.orientation = android.widget.LinearLayout.VERTICAL
            layout.setPadding(48, 24, 48, 24)

            // Session kesto ja matka
            val statsLabel = android.widget.TextView(this@MainActivity)
            statsLabel.text = SessionStatsFormatter.formatSummary(durationMs, distanceM.toDouble())
            statsLabel.textSize = 16f
            statsLabel.setPadding(0, 0, 0, 24)
            layout.addView(statsLabel)

            if (pointCount == 0) {
                val noPointsLabel = android.widget.TextView(this@MainActivity)
                noPointsLabel.text = "Kalastussessiossa ei ole reittipisteitä. Kalastussessiota ei tallenneta."
                noPointsLabel.textSize = 16f
                noPointsLabel.setTextColor(Color.RED)
                layout.addView(noPointsLabel)
                
                builder.setView(layout)
                builder.setPositiveButton("Sulje", null)
                
                // Poistetaan sessio koska reittipisteitä ei ole
                withContext(Dispatchers.IO) {
                    db.fishingSessionDao().deleteById(sessionId)
                }
            } else {
                val label = android.widget.TextView(this@MainActivity)
                label.text = "Kalastussession huomiot:"
                label.textSize = 16f
                layout.addView(label)
                
                val input = android.widget.EditText(this@MainActivity)
                input.hint = "Lisää muistiinpanoja sessiosta..."
                input.setLines(3)
                input.gravity = android.view.Gravity.TOP
                
                layout.addView(input)
                builder.setView(layout)

                builder.setPositiveButton("Tallenna") { _, _ ->
                    val notes = input.text.toString()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val session = db.fishingSessionDao().getById(sessionId)
                        if (session != null) {
                            db.fishingSessionDao().update(session.copy(notes = notes))
                        }
                    }
                }
                builder.setNegativeButton("Sulje", null)
            }
            builder.show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mapNavigationController.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        
        intent?.let { mapNavigationController.handleIntent(it) }
        
        fishingSessionController.updateRecordingStatus()

        locationController.onResume()
        updateScaleBar()
        // Yritetään näyttää sääasema jos se on vielä näyttämättä
        checkWeather()

        // Päivitetään kartta ja suodattimet
        reloadMarkersFromDb()
        updateFilterStatusUI()

    }

    private fun saveMapState() {
        val prefs = getSharedPreferences("map_state", MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("lat", map.mapCenter.latitude.toFloat())
            putFloat("lon", map.mapCenter.longitude.toFloat())
            putFloat("zoom", map.zoomLevelDouble.toFloat())
            apply()
        }
    }

    override fun onPause() {
        saveMapState()
        if (::mapDisplayController.isInitialized) {
            mapDisplayController.clearPendingUpdates()
        }
        if (::measurementController.isInitialized) {
            measurementController.clearPendingCallbacks()
        }
        locationController.onPause()
        map.onPause()
        super.onPause()
    }
    private fun updateMyLocationButtonVisibility() {
        locationController.updateMyLocationButtonVisibility()
    }

    private fun handleActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val replayRequest = SessionReplayResult.parse(resultCode, data)
        if (resultCode == RESULT_OK) {
            val catchId = data?.getLongExtra("EXTRA_CATCH_ID", -1L) ?: -1L
            val sessionId = data?.getLongExtra("EXTRA_SESSION_ID", -1L) ?: -1L

            if (sessionId != -1L) {
                // Suljetaan mahdolliset dialogit ennen kartalle siirtymistä
                settingsManager.closeSettings()
                
                if (replayRequest != null) {
                    replayMapController.replaySessionOnMap(replayRequest.sessionId, replayRequest.onlySessionCatches)
                } else {
                    replayMapController.showArchivedSessionOnMap(sessionId)
                }
            } else if (requestCode == 3001) {
                // Sessioiden listauksesta palattiin ilman valintaa, ei tehdä mitään erikoista
            } else if (requestCode == 1001 && catchId != -1L) {
                // Muokattu kala: päivitetään vain se (inkrementaalinen päivitys)
                lifecycleScope.launch(Dispatchers.IO) {
                    val fish = db.fishCatchDao().getById(catchId)
                    withContext(Dispatchers.Main) {
                        if (fish != null) {
                            markerManager.addOrUpdateMarkerIncremental(fish, map.zoomLevelDouble)
                        } else {
                            reloadMarkersFromDb()
                        }
                    }
                }
            } else if (requestCode == 2001 || requestCode == 2002 || requestCode == 2003) {
                // Suodattimet, yhteenveto tai kalapäiväkirja päivitetty
                reloadMarkersFromDb()
                if (data?.getBooleanExtra("BACK_TO_SETTINGS", false) == true) {
                    settingsManager.openSettings()
                }
            } else if (requestCode == 1002) {
                // Kalalajit muokattu: pakotetaan MarkerManagerin päivitys
                markerManager.rebuildMarkers(map.zoomLevelDouble, forceRefreshSpecies = true)
            } else {
                // Muut tapaukset (import, asetukset tms.): täysi reload
                reloadMarkersFromDb()
            }
            updateFilterStatusUI()
        }
    }
}
