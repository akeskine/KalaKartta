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
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import fi.anssi.kalakartta.utils.enlargeButtons

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
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.setMultiTouchControls(true)
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
            db = AppDatabase.getInstance(this)
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
                    FishSpecies("WHITEFISH", "Siika", icon_default = "siika")
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
            }) {
                reloadMarkersFromDb()
            }

            markerManager = MarkerManager(this, map, db) { marker ->
                val fish = marker.relatedObject as? FishCatch
                if (fish != null) {
                    db.fishCatchDao().deleteById(fish.id)
                }
                markerManager.removeMarker(marker)
                // Päivitetään klusterit jos tarpeen
                if (map.zoomLevelDouble < 13.0) {
                    markerManager.rebuildMarkers(map.zoomLevelDouble)
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

            if (crashFile.exists()) {
                crashFile.delete()
            }
        } catch (t: Throwable) {
            crashFile.writeText(t.stackTraceToString())
            throw t
        }
    }

    private fun loadCatches() {
        val catches = db.fishCatchDao().getAll()
        val filteredCatches = filterManager.applyFilter(catches)
        markerManager.setAllCatches(filteredCatches)
        
        val places = db.placeOfInterestDao().getAll()
        markerManager.setAllPlaces(places)
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
        markerManager.rebuildMarkers(map.zoomLevelDouble)
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

        // Yritetään näyttää sääasema jos se on vielä näyttämättä
        if (!weatherCheckDone) {
            checkWeather()
        }
    }

    override fun onPause() {
        try {
            unregisterReceiver(locationProviderReceiver)
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