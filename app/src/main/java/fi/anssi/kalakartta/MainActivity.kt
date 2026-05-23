package fi.anssi.kalakartta

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
import fi.anssi.kalakartta.utils.WeatherService
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
    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
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

        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        locationOverlay.runOnFirstFix {
            runOnUiThread {
                checkWeather()
            }
        }
        map.overlays.add(locationOverlay)

        requestLocationPermission()

        findViewById<MaterialButton>(R.id.addCatchButton).setOnClickListener {
            catchManager.showSpeciesDialog()
        }

        findViewById<MaterialButton>(R.id.myLocationButton).setOnClickListener {
            val myLocation = locationOverlay.myLocation
            if (myLocation != null) {
                // Nelinkertaistetaan nopeus (oletus 1000ms -> 250ms)
                map.controller.animateTo(myLocation, map.zoomLevelDouble, 250L)
            }
        }

        findViewById<MaterialButton?>(R.id.settingsButton)?.setOnClickListener {
            settingsManager.openSettings()
        }

        db = AppDatabase.getInstance(this)

        // Esitäyttö taustasäikeessä
        Thread {
            val speciesDao = db.fishSpeciesDao()
            val defaults = listOf(
                FishSpecies("PERCH", "Ahven", icon_default = "ahven"),
                FishSpecies("PIKE", "Hauki", icon_default = "hauki"),
                FishSpecies("ZANDER", "Kuha", icon_default = "kuha"),
                FishSpecies("TROUT", "Taimen", icon_default = "taimen"),
                FishSpecies("SALMON", "Lohi", icon_default = "lohi"),
                FishSpecies("GRAYLING", "Harjus", icon_default = "harjus"),
                FishSpecies("WHITEFISH", "Siika", icon_default = "siika")
            )

            defaults.forEach { species ->
                val existing = speciesDao.getById(species.id)
                if (existing == null) {
                    speciesDao.insert(species)
                } else if (existing.icon_default.isEmpty() && species.icon_default.isNotEmpty()) {
                    speciesDao.insert(existing.copy(icon_default = species.icon_default))
                }
            }
        }.start()

        importExportManager = ImportExportManager(this, db) {
            reloadMarkersFromDb()
        }

        settingsManager = SettingsManager(this, db, importExportManager, onWeatherSettingsChanged = { isEnabled ->
            if (isEnabled) {
                checkWeather(force = true)
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
            if (map.zoomLevelDouble < 14.5) {
                markerManager.rebuildMarkers(map.zoomLevelDouble)
            }
        }

        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                // Kun ollaan zoomed in, päivitetään näkyvät markerit (clipping)
                if (map.zoomLevelDouble >= 14.5) {
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

        catchManager = CatchManager(this, map, db) { fish ->
            markerManager.addOrUpdateMarkerIncremental(fish, map.zoomLevelDouble)
        }

        weatherService = WeatherService(this)

        loadCatches()
        markerManager.rebuildMarkers(map.zoomLevelDouble)
        updateMarkersVisibility()
        updateFilterStatusUI()
    }

    private fun loadCatches() {
        val catches = db.fishCatchDao().getAll()
        val filteredCatches = filterManager.applyFilter(catches)
        filteredCatches.forEach { markerManager.addMarker(it) }
        // Poistettu map.invalidate() tästä, koska rebuildMarkers hoitaa sen
    }

    private fun updateFilterStatusUI() {
        val layout = findViewById<android.view.View>(R.id.filterStatusLayout)
        val text = findViewById<android.widget.TextView>(R.id.filterStatusText)
        
        if (filterManager.hasActiveFilters()) {
            layout.visibility = android.view.View.VISIBLE
            text.text = filterManager.getFilterDescription()
        } else {
            layout.visibility = android.view.View.GONE
        }
    }

    private fun reloadMarkersFromDb() {
        markerManager.clearMarkers()
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

        val myLocation = locationOverlay.myLocation
        if (myLocation == null) {
            if (force) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Säätiedot")
                        .setMessage("Lähimmän sääaseman haku epäonnistui: Sijaintia ei ole vielä saatavilla.")
                        .setPositiveButton("OK", null)
                        .show()
                        .enlargeButtons()
                }
            }
            return
        }

        weatherCheckDone = true

        weatherService.fetchNearestStation(myLocation.latitude, myLocation.longitude, System.currentTimeMillis()) { station, error ->
            runOnUiThread {
                if (error != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Säätiedot")
                        .setMessage("Säätietojen haku epäonnistui: $error")
                        .setPositiveButton("OK", null)
                        .show()
                        .enlargeButtons()
                } else if (station != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Säätiedot")
                        .setMessage("Säädatan automaattinen haku on käytössä.\n\nLähin sääasema:\n${station.name}\nfmisid: ${station.fmisid}")
                        .setPositiveButton("OK", null)
                        .show()
                        .enlargeButtons()
                }
            }
        }
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
        locationOverlay.enableMyLocation()
        updateMyLocationButtonVisibility()
        
        // Yritetään näyttää sääasema jos se on vielä näyttämättä
        if (!weatherCheckDone) {
            checkWeather()
        }
    }

    override fun onPause() {
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
        
        findViewById<MaterialButton>(R.id.myLocationButton).visibility = 
            if (hasPermission && isGpsEnabled) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
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
            } else {
                // Muut tapaukset (import, asetukset tms.): täysi reload
                reloadMarkersFromDb()
            }
            updateFilterStatusUI()
        }
    }
}