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
import androidx.core.content.ContextCompat
import fi.anssi.kalakartta.io.ImportExportManager
import fi.anssi.kalakartta.data.*
import androidx.room.Room
import fi.anssi.kalakartta.ui.SettingsManager
import fi.anssi.kalakartta.ui.CatchManager
import fi.anssi.kalakartta.ui.MarkerManager

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var importExportManager: ImportExportManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var catchManager: CatchManager
    private lateinit var markerManager: MarkerManager
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

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "kalakartta-db"
        )
        .addMigrations(AppDatabase.MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .allowMainThreadQueries()
        .build()

        // Esitäyttö taustasäikeessä jos tietokanta on tyhjä
        Thread {
            val speciesDao = db.fishSpeciesDao()
            if (speciesDao.getAll().isEmpty()) {
                speciesDao.insert(FishSpecies("PERCH", "Ahven", icon_default = "ahven"))
                speciesDao.insert(FishSpecies("PIKE", "Hauki", icon_default = "hauki"))
                speciesDao.insert(FishSpecies("ZANDER", "Kuha", icon_default = "kuha"))
            }
        }.start()

        importExportManager = ImportExportManager(this, db) {
            reloadMarkersFromDb()
        }

        settingsManager = SettingsManager(this, db, importExportManager) {
            reloadMarkersFromDb()
        }

        markerManager = MarkerManager(this, map, db) { marker ->
            val fish = marker.relatedObject as? FishCatch
            if (fish != null) {
                db.fishCatchDao().deleteById(fish.id)
            }
            map.overlays.remove(marker)
            map.invalidate()
        }

        catchManager = CatchManager(this, map, db) { fish ->
            markerManager.addMarker(fish)
            map.invalidate()
        }

        loadCatches()
    }

    private fun loadCatches() {
        val catches = db.fishCatchDao().getAll()
        catches.forEach { markerManager.addMarker(it) }
        map.invalidate()
    }

    private fun reloadMarkersFromDb() {
        map.overlays.removeAll { it is Marker }
        loadCatches()
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
}