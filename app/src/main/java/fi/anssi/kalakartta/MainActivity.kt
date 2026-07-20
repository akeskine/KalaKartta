package fi.anssi.kalakartta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import androidx.core.content.ContextCompat
import fi.anssi.kalakartta.io.ImportExportManager
import fi.anssi.kalakartta.data.*
import androidx.room.Room
import fi.anssi.kalakartta.ui.SettingsManager
import fi.anssi.kalakartta.ui.CatchManager
import fi.anssi.kalakartta.ui.MarkerManager
import fi.anssi.kalakartta.ui.FilterManager
import fi.anssi.kalakartta.ui.WindDirectionView
import fi.anssi.kalakartta.utils.WeatherService
import fi.anssi.kalakartta.utils.MMLTileSource
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.utils.enlargeButtons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var importExportManager: ImportExportManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var catchManager: CatchManager
    private lateinit var markerManager: MarkerManager
    private lateinit var filterManager: FilterManager
    private lateinit var weatherService: WeatherService
    private var weatherCheckDone = false
    private var lastFoundStation: fi.anssi.kalakartta.utils.WeatherStation? = null
    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private var scaleBarOverlay: ScaleBarOverlay? = null

    private var isFirstResume = true
    private var screenReceiver: BroadcastReceiver? = null

    private val locationProviderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                updateMyLocationButtonVisibility()
            }
        }
    }

    private var isUserScrolling = false

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
            updateMapTileSource()
            map.setMultiTouchControls(true)
            map.setBuiltInZoomControls(false)
            map.controller.setZoom(15.0)

            // Asetetaan alkusijainti Helsingin keskustaan, jos omaa sijaintia ei vielä ole
            val helsinkiCenter = org.osmdroid.util.GeoPoint(60.1695, 24.9354)
            map.controller.setCenter(helsinkiCenter)

            locationOverlay = object : MyLocationNewOverlay(GpsMyLocationProvider(this), map) {
                override fun draw(canvas: android.graphics.Canvas, map: MapView, shadow: Boolean) {
                    try {
                        super.draw(canvas, map, shadow)
                    } catch (e: Exception) {
                        // Hiljennetään mahdolliset piirto-virheet (esim. Bitmap NPE)
                        android.util.Log.e("MainActivity", "Error drawing locationOverlay: ${e.message}")
                    }
                }
            }
            // Poistettu: locationOverlay.enableMyLocation() - siirretty lupien tarkistuksen jälkeen
            map.overlays.add(locationOverlay)

            requestLocationPermission()

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationOverlay.enableMyLocation()
            }

            map.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    isUserScrolling = true
                } else if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                    // Pieni viive, jotta scroll-tapahtuma ehtii tulla ennen kuin nollataan
                    map.postDelayed({ isUserScrolling = false }, 500)
                }
                false
            }

            findViewById<MaterialButton>(R.id.addCatchButton).setOnClickListener {
                catchManager.showSpeciesDialog()
            }

            findViewById<MaterialButton>(R.id.myLocationButton).setOnClickListener {
                // Aktivoi seuranta (keskittää sijaintiin)
                locationOverlay.enableFollowLocation()

                val myLocation = locationOverlay.myLocation
                if (myLocation != null) {
                    map.controller.animateTo(myLocation, map.zoomLevelDouble, 250L)
                } else {
                    // Jos overlaylla ei ole vielä sijaintia, kokeillaan järjestelmän LocationManageria
                    val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                    val lastKnown = try {
                        locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                            ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    } catch (e: SecurityException) {
                        null
                    }

                    if (lastKnown != null) {
                        val geoPoint = org.osmdroid.util.GeoPoint(lastKnown.latitude, lastKnown.longitude)
                        map.controller.animateTo(geoPoint, map.zoomLevelDouble, 250L)
                    } else {
                        android.widget.Toast.makeText(this, "Sijaintia ei ole vielä saatavilla", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            findViewById<MaterialButton?>(R.id.settingsButton)?.setOnClickListener {
                settingsManager.openSettings()
            }

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
                ).allowMainThreadQueries().build()
            }
            android.util.Log.d("KalaKartta", "after db init")

            // Esitäyttö taustasäikeessä
            Thread {
                val speciesDao = db.fishSpeciesDao()
                val defaults = listOf(
                    FishSpecies(
                        "PERCH", "Ahven", icon_default = "ahven",
                        small_weight = 200, small_length = 25,
                        large_weight = 500, large_length = 35,
                        giant_weight = 800, giant_length = 40
                    ),
                    FishSpecies(
                        "PIKE", "Hauki", icon_default = "hauki",
                        small_weight = 1000, small_length = 55,
                        large_weight = 3000, large_length = 80,
                        giant_weight = 8000, giant_length = 100
                    ),
                    FishSpecies(
                        "ZANDER", "Kuha", icon_default = "kuha",
                        small_weight = 800, small_length = 42,
                        large_weight = 2000, large_length = 60,
                        giant_weight = 5000, giant_length = 80
                    ),
                    FishSpecies("TROUT", "Taimen", icon_default = "taimen"),
                    FishSpecies("SALMON", "Lohi", icon_default = "lohi"),
                    FishSpecies("GRAYLING", "Harjus", icon_default = "harjus"),
                    FishSpecies("WHITEFISH", "Siika", icon_default = "siika"),
                    FishSpecies("RAINBOW", "Kirjolohi", icon_default = "kirjolohi"),
                    FishSpecies("BREAM", "Lahna", icon_default = "lahna"),
                    FishSpecies("IDE", "Säyne", icon_default = "sayne")
                )

                defaults.forEach { species ->
                    val existing = speciesDao.getById(species.id)
                    if (existing == null) {
                        speciesDao.insert(species)
                    } else {
                        var updated = false
                        var toUpdate = existing

                        // Päivitetään oletusikonit jos ne puuttuvat
                        if (existing.icon_default.isEmpty() && species.icon_default.isNotEmpty()) {
                            toUpdate = toUpdate.copy(icon_default = species.icon_default)
                            updated = true
                        }

                        // Päivitetään paino- ja pituusrajat jos ne ovat 0 (eli ei vielä asetettu)
                        if (existing.small_weight == 0L && species.small_weight != 0L) {
                            toUpdate = toUpdate.copy(
                                small_weight = species.small_weight,
                                small_length = species.small_length,
                                large_weight = species.large_weight,
                                large_length = species.large_length,
                                giant_weight = species.giant_weight,
                                giant_length = species.giant_length
                            )
                            updated = true
                        }

                        if (updated) {
                            speciesDao.insert(toUpdate)
                        }
                    }
                }

                // Muut paikat (PlaceOfInterestType) esitäyttö
                val placeTypeDao = db.placeOfInterestTypeDao()
                val placeDefaults = listOf(
                    PlaceOfInterestType("ROCK", "Kivi", icon = "kivi", sortOrder = 1),
                    PlaceOfInterestType("VEGETATION", "Kasvusto", icon = "vesikasvi", sortOrder = 2),
                    PlaceOfInterestType("SHALLOW", "Matalikko", icon = "matalikko", sortOrder = 3),
                    PlaceOfInterestType("DEEP", "Syvänne", icon = "syvanne", sortOrder = 4),
                    PlaceOfInterestType("PARKING", "Pysäköinti", icon = "pysakointi", sortOrder = 5),
                    PlaceOfInterestType("ACCESS", "Pääsy rantaan", icon = "access", sortOrder = 6),
                    PlaceOfInterestType("LANDINGSPOT", "Rantautumispaikka", icon = "rantautumispaikka", sortOrder = 7),
                    PlaceOfInterestType("RAMP", "Veneramppi", icon = "ramppi", sortOrder = 8),
                    PlaceOfInterestType("HARBOUR", "Satama", icon = "satama", sortOrder = 9),
                    PlaceOfInterestType("ACCOMMODATION", "Majoitus", icon = "majoitus", sortOrder = 10),
                    PlaceOfInterestType("CAMP", "Leiripaikka", icon = "leiripaikka", sortOrder = 11),
                    PlaceOfInterestType("CAMPFIRE", "Tulipaikka", icon = "tulipaikka", sortOrder = 12),
                    PlaceOfInterestType("SHELTER", "Laavu", icon = "laavu", sortOrder = 13),
                    PlaceOfInterestType("OTHER", "Muu kiinnostava paikka", icon = "tahti", sortOrder = 14)
                )

                placeDefaults.forEach { type ->
                    val existing = placeTypeDao.getById(type.id)
                    if (existing == null) {
                        placeTypeDao.insert(type)
                    } else {
                        var updated = false
                        var toUpdate = existing
                        if (existing.icon.isEmpty() && type.icon.isNotEmpty()) {
                            toUpdate = toUpdate.copy(icon = type.icon)
                            updated = true
                        }
                        if (existing.sortOrder != type.sortOrder) {
                            toUpdate = toUpdate.copy(sortOrder = type.sortOrder)
                            updated = true
                        }
                        if (updated) {
                            placeTypeDao.insert(toUpdate)
                        }
                    }
                }
            }.start()

            importExportManager = ImportExportManager(this, db) {
                reloadMarkersFromDb()
            }

            settingsManager = SettingsManager(this, db, importExportManager, onWeatherSettingsChanged = { isEnabled ->
                if (isEnabled) {
                    checkWeather(force = true)
                } else {
                    updateWeatherUI()
                }
            }, onMapSettingsChanged = {
                updateMapTileSource()
            }) {
                reloadMarkersFromDb()
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

            map.addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    // Jos käyttäjä skrollaa itse, poistetaan automaattinen seuranta
                    if (isUserScrolling) {
                        locationOverlay.disableFollowLocation()
                    }

                    // Kun ollaan zoomed in, päivitetään näkyvät markerit (clipping)
                    if (map.zoomLevelDouble >= 13.0) {
                        markerManager.setMarkersVisible(true, map.zoomLevelDouble, forceRebuild = true)
                    }
                    return false
                }
                override fun onZoom(event: ZoomEvent?): Boolean {
                    updateMarkersVisibility()
                    return true
                }
            })

            filterManager = FilterManager(this)

            weatherService = WeatherService(this)

            catchManager = CatchManager(this, map, db, weatherService, 
                onCatchAdded = { fish ->
                    markerManager.addOrUpdateMarkerIncremental(fish, map.zoomLevelDouble, filterManager)
                },
                onPlaceAdded = { place ->
                    markerManager.addOrUpdatePlaceIncremental(place, map.zoomLevelDouble)
                }
            )

            android.util.Log.d("KalaKartta", "before loadCatches")
            loadCatches()
            android.util.Log.d("KalaKartta", "after loadCatches")

            updateMarkersVisibility()
            updateFilterStatusUI()

            // Automaattinen kohdistus sovelluksen avauksessa
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val autoCenter = prefs.getBoolean("auto_center_on_start", true)
            if (autoCenter) {
                locationOverlay.runOnFirstFix {
                    runOnUiThread {
                        val myLocation = locationOverlay.myLocation
                        if (myLocation != null) {
                            map.controller.animateTo(myLocation, map.zoomLevelDouble, 500L)
                        }
                    }
                }
            }

            if (crashFile.exists()) {
                crashFile.delete()
            }
        } catch (t: Throwable) {
            crashFile.writeText(t.stackTraceToString())
            throw t
        }
    }

    private fun updateMapTileSource() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val mapSource = prefs.getString("map_source", "OSM")
        val apiKey = prefs.getString("mml_api_key", "") ?: ""

        when (mapSource) {
            "MML_MAASTO" -> {
                map.setTileSource(MMLTileSource("MML Maastokartta", "maastokartta", apiKey))
                updateUIColors(true)
            }
            "MML_ILMA" -> {
                map.setTileSource(MMLTileSource("MML Ilmakuva", "ortokuva", apiKey))
                updateUIColors(true)
            }
            else -> {
                map.setTileSource(TileSourceFactory.MAPNIK)
                updateUIColors(false)
            }
        }
        updateScaleBar()
    }

    private fun updateScaleBar() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val showScale = prefs.getBoolean("show_scale_bar", false)
        val mapSource = prefs.getString("map_source", "OSM")
        val useBlack = mapSource == "MML_MAASTO" || mapSource == "MML_ILMA"
        
        // Poistetaan vanha jos on
        scaleBarOverlay?.let { map.overlays.remove(it) }
        
        if (showScale) {
            val density = resources.displayMetrics.density
            val buttonMargin = resources.getDimensionPixelSize(R.dimen.button_margin_bottom)
            val targetWidth = (48 * density).toInt()

            val color = if (useBlack) {
                ContextCompat.getColor(this, android.R.color.black)
            } else {
                ContextCompat.getColor(this, android.R.color.white)
            }

            // Käytetään omaa ScaleBarOverlay-aliluokkaa, jolla pakotetaan pituus ja väri
            val scaleBar = object : ScaleBarOverlay(map) {
                override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
                    if (shadow) return

                    // Pakotetaan pituus heijastuksella juuri ennen piirtoa, 
                    // jos osmdroid yrittää laskea sen uudelleen
                    try {
                        val fields = listOf("mLineWidth", "lineWidth", "mMinWidth", "minWidth", "mMaxWidth", "maxWidth")
                        for (name in fields) {
                            try {
                                val field = ScaleBarOverlay::class.java.getDeclaredField(name)
                                field.isAccessible = true
                                field.set(this, targetWidth)
                            } catch (e: NoSuchFieldException) {}
                        }
                    } catch (e: Exception) {}

                    super.draw(canvas, mapView, shadow)
                }
            }.apply {
                setAlignBottom(true)
                
                // yOffset mitataan pohjasta ylöspäin (koska setAlignBottom(true)).
                val yOffset = buttonMargin - (22 * density).toInt() 
                val xOffset = 60
                setScaleBarOffset(xOffset, yOffset)
                
                setTextSize(density * 12)
                
                // Asetetaan värit
                barPaint.color = color
                textPaint.color = color
            }
            map.overlays.add(scaleBar)
            scaleBarOverlay = scaleBar
        } else {
            scaleBarOverlay = null
        }
        
        // Nappien paikka ei enää muutu mittakaavan mukaan
        val myLocationButton = findViewById<MaterialButton>(R.id.myLocationButton)
        val addCatchButton = findViewById<MaterialButton>(R.id.addCatchButton)
        
        val baseMargin = resources.getDimensionPixelSize(R.dimen.button_margin_bottom)
        
        val myLocParams = myLocationButton.layoutParams as FrameLayout.LayoutParams
        myLocParams.bottomMargin = baseMargin
        myLocationButton.layoutParams = myLocParams

        val addCatchParams = addCatchButton.layoutParams as FrameLayout.LayoutParams
        addCatchParams.bottomMargin = baseMargin
        addCatchButton.layoutParams = addCatchParams
        
        myLocationButton.requestLayout()
        addCatchButton.requestLayout()
        map.invalidate()
    }

    private fun updateUIColors(useBlack: Boolean) {
        val color = if (useBlack) {
            ContextCompat.getColor(this, android.R.color.black)
        } else {
            ContextCompat.getColor(this, android.R.color.white)
        }

        findViewById<TextView>(R.id.mapCrosshair).setTextColor(color)

        val buttons = listOf(
            findViewById<MaterialButton>(R.id.myLocationButton),
            findViewById<MaterialButton>(R.id.addCatchButton),
            findViewById<MaterialButton>(R.id.settingsButton)
        )

        buttons.forEach { button ->
            button.iconTint = android.content.res.ColorStateList.valueOf(color)
            button.strokeColor = android.content.res.ColorStateList.valueOf(color)
        }
    }

    private fun loadCatches() {
        val catches = db.fishCatchDao().getAll()
        val filteredCatches = filterManager.applyFilter(catches)
        markerManager.setAllCatches(filteredCatches)
        
        val places = db.placeOfInterestDao().getAll()
        val filteredPlaces = filterManager.applyPlaceFilter(places)
        markerManager.setAllPlaces(filteredPlaces)
    }

    private fun updateFilterStatusUI() {
        val layout = findViewById<android.view.View>(R.id.filterStatusLayout)
        val text = findViewById<android.widget.TextView>(R.id.filterStatusText)
        val windView = findViewById<WindDirectionView>(R.id.filterWindView)
        
        val filters = filterManager.getFilters()
        if (filterManager.hasActiveFilters()) {
            layout.visibility = android.view.View.VISIBLE
            text.text = filterManager.getFilterDescription()
            
            if (filters.windMin != null && filters.windMax != null) {
                windView.visibility = android.view.View.VISIBLE
                windView.setRange(filters.windMin, filters.windMax)
            } else {
                windView.visibility = android.view.View.GONE
            }
        } else {
            layout.visibility = android.view.View.GONE
        }
    }

    private fun reloadMarkersFromDb() {
        loadCatches()
    }

    private fun checkWeather(force: Boolean = false) {
        if (force) {
            weatherCheckDone = false
        }
        
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("weather_enabled", true)
        if (!isEnabled) return

        // Haetaan kaikki sääasemat muistiin taustalla, jos niitä ei vielä ole
        weatherService.fetchAllStations()

        if (weatherCheckDone) return

        val myLocation = if (::locationOverlay.isInitialized) locationOverlay.myLocation else null
        if (myLocation == null) {
            // Poistettu automaattinen virheilmoitus puuttuvasta sijainnista
            return
        }

        weatherCheckDone = true

        weatherService.fetchNearestStation(myLocation.latitude, myLocation.longitude, System.currentTimeMillis()) { station, error ->
            runOnUiThread {
                if (error != null) {
                    // Epäonnistumisesta ei välttämättä tarvitse ilmoittaa käyttäjälle automaattisessa haussa
                } else if (station != null) {
                    lastFoundStation = station
                    updateWeatherUI()
                }
            }
        }
    }

    private fun updateWeatherUI() {
        val weatherStationText = findViewById<TextView>(R.id.weatherStationText)
        weatherStationText.visibility = android.view.View.GONE
    }

    private fun updateMarkersVisibility() {
        // Näytetään pisteet laajemmalla zoom-alueella (alk. tasolta 1.0)
        // Optimointi on tehty MarkerManagerin kuvakevälimuistilla ja klusteroinnilla
        markerManager.setMarkersVisible(map.zoomLevelDouble >= 1.0, map.zoomLevelDouble)
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationOverlay.enableMyLocation()
        }

        updateMyLocationButtonVisibility()

        // Rekisteröidään sijaintipalveluiden seuranta
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationProviderReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(locationProviderReceiver, filter)
        }

        // Rekisteröidään näytön avauksen seuranta
        val screenFilter = IntentFilter(Intent.ACTION_SCREEN_ON)
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    if (prefs.getBoolean("auto_center_on_start", true)) {
                        val myLocation = locationOverlay.myLocation
                        if (myLocation != null) {
                            map.controller.animateTo(myLocation, map.zoomLevelDouble, 500L)
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screenFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, screenFilter)
        }

        // Yritetään näyttää sääasema jos se on vielä näyttämättä
        if (!weatherCheckDone) {
            checkWeather()
        }

        isFirstResume = false
    }

    override fun onPause() {
        try {
            unregisterReceiver(locationProviderReceiver)
        } catch (_: IllegalArgumentException) {
        }
        try {
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (_: IllegalArgumentException) {
        }
        locationOverlay.disableMyLocation()
        map.onPause()
        super.onPause()
    }

    private fun updateMyLocationButtonVisibility() {
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
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
        
        findViewById<MaterialButton>(R.id.myLocationButton).visibility = 
            if (hasPermission && (isGpsEnabled || isNetworkEnabled)) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationOverlay.enableMyLocation()
            }
            updateMyLocationButtonVisibility()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val catchId = data?.getLongExtra("EXTRA_CATCH_ID", -1L) ?: -1L
            
            if (requestCode == 1001 && catchId != -1L) {
                // Muokattu kala: päivitetään vain se (inkrementaalinen päivitys)
                val fish = db.fishCatchDao().getById(catchId)
                if (fish != null) {
                    markerManager.addOrUpdateMarkerIncremental(fish, map.zoomLevelDouble)
                } else {
                    reloadMarkersFromDb()
                }
            } else if (requestCode == 2001) {
                // Suodattimet päivitetty
                reloadMarkersFromDb()
            } else {
                // Muut tapaukset (import, asetukset tms.): täysi reload
                reloadMarkersFromDb()
            }
            updateFilterStatusUI()
        }
    }
}