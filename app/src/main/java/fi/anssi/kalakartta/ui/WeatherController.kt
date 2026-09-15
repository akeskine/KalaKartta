package fi.anssi.kalakartta.ui

import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.utils.WeatherService
import org.osmdroid.util.GeoPoint

/** Coordinates weather checks with the map location. */
class WeatherController(
    private val activity: AppCompatActivity,
    private val settingsStore: SettingsStore,
    private val locationProvider: () -> GeoPoint?
) {
    val weatherService = WeatherService(activity)

    private var weatherCheckDone = false

    fun checkWeather(force: Boolean = false) {
        if (force) {
            weatherCheckDone = false
        }

        if (!settingsStore.weatherEnabled) {
            return
        }

        weatherService.fetchAllStations()
        if (weatherCheckDone) return

        val myLocation = locationProvider() ?: return
        weatherCheckDone = true

        weatherService.fetchNearestStation(
            myLocation.latitude,
            myLocation.longitude,
            System.currentTimeMillis()
        ) { _, _ -> }
    }
}
