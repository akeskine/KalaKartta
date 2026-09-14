package fi.anssi.kalakartta.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import org.osmdroid.views.MapView as OsmMapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import fi.anssi.kalakartta.R

/** Owns the map location overlay, permissions and location-related UI lifecycle. */
class LocationController(
    private val activity: AppCompatActivity,
    private val map: OsmMapView,
    private val settingsStore: SettingsStore,
    private val isSelectionMode: () -> Boolean
) {
    private lateinit var locationOverlay: MyLocationNewOverlay
    private var screenReceiver: BroadcastReceiver? = null
    private var userScrolling = false
    private val locationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableMyLocationIfPermitted()
        }
        updateMyLocationButtonVisibility()
    }

    val currentLocation: org.osmdroid.util.GeoPoint?
        get() = if (::locationOverlay.isInitialized) locationOverlay.myLocation else null

    val isUserScrolling: Boolean
        get() = userScrolling

    fun initialize() {
        locationOverlay = object : MyLocationNewOverlay(GpsMyLocationProvider(activity), map) {
            override fun draw(canvas: android.graphics.Canvas, map: OsmMapView, shadow: Boolean) {
                try {
                    super.draw(canvas, map, shadow)
                } catch (e: Exception) {
                    android.util.Log.e("LocationController", "Error drawing location overlay: ${e.message}")
                }
            }
        }
        map.overlays.add(locationOverlay)
        requestLocationPermission()
        enableMyLocationIfPermitted()
    }

    fun enableMyLocationIfPermitted() {
        if (::locationOverlay.isInitialized && hasLocationPermission()) {
            locationOverlay.enableMyLocation()
        }
    }

    fun onMapTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> userScrolling = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                map.postDelayed({ userScrolling = false }, 500)
            }
        }
        return false
    }

    fun disableFollowLocation() {
        if (::locationOverlay.isInitialized) {
            locationOverlay.disableFollowLocation()
        }
    }

    fun centerOnCurrentLocation() {
        if (!::locationOverlay.isInitialized) return
        locationOverlay.enableFollowLocation()
        val myLocation = locationOverlay.myLocation
        if (myLocation != null) {
            map.controller.animateTo(myLocation, map.zoomLevelDouble, 250L)
            return
        }

        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastKnown = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }

        if (lastKnown != null) {
            val point = org.osmdroid.util.GeoPoint(lastKnown.latitude, lastKnown.longitude)
            map.controller.animateTo(point, map.zoomLevelDouble, 250L)
        } else {
            android.widget.Toast.makeText(
                activity,
                "Sijaintia ei ole vielä saatavilla",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun setupMyLocationButton() {
        activity.findViewById<MaterialButton>(R.id.myLocationButton).setOnClickListener {
            centerOnCurrentLocation()
        }
    }

    fun centerOnFirstFixIfNeeded() {
        if (!settingsStore.autoCenterOnStart || isSelectionMode() || !::locationOverlay.isInitialized) return
        locationOverlay.runOnFirstFix {
            map.post {
                locationOverlay.myLocation?.let {
                    map.controller.animateTo(it, map.zoomLevelDouble, 500L)
                }
            }
        }
    }

    fun updateMyLocationButtonVisibility() {
        if (activity.findViewById<android.view.View>(R.id.replayPlayerLayout).visibility == android.view.View.VISIBLE) {
            activity.findViewById<MaterialButton>(R.id.myLocationButton).visibility = android.view.View.GONE
            return
        }

        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            false
        }
        val networkEnabled = try {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }

        activity.findViewById<MaterialButton>(R.id.myLocationButton).visibility =
            if (hasLocationPermission() && (gpsEnabled || networkEnabled)) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
    }

    fun onResume() {
        enableMyLocationIfPermitted()
        updateMyLocationButtonVisibility()
        registerLocationProviderReceiver()
        registerScreenReceiver()
    }

    fun onPause() {
        try {
            activity.unregisterReceiver(locationProviderReceiver)
        } catch (_: IllegalArgumentException) {
        }
        try {
            screenReceiver?.let { activity.unregisterReceiver(it) }
        } catch (_: IllegalArgumentException) {
        }
        screenReceiver = null
        if (::locationOverlay.isInitialized) {
            locationOverlay.disableMyLocation()
        }
    }

    private fun requestLocationPermission() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun registerLocationProviderReceiver() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(locationProviderReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(locationProviderReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON &&
                    settingsStore.autoCenterOnStart &&
                    !isSelectionMode()
                ) {
                    currentLocation?.let { map.controller.animateTo(it, map.zoomLevelDouble, 500L) }
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(screenReceiver, filter)
        }
    }

    private val locationProviderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                updateMyLocationButtonVisibility()
            }
        }
    }

}
