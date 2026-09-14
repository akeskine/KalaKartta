package fi.anssi.kalakartta.ui

import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.utils.WeatherService
import fi.anssi.kalakartta.utils.WeatherStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/** Coordinates weather checks with the map location and weather-related UI. */
class WeatherController(
    private val activity: AppCompatActivity,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val locationProvider: () -> GeoPoint?
) {
    val weatherService = WeatherService(activity)

    private var weatherCheckDone = false
    private var lastFoundStation: WeatherStation? = null

    fun checkWeather(force: Boolean = false) {
        if (force) {
            weatherCheckDone = false
        }

        if (!settingsStore.weatherEnabled) return

        weatherService.fetchAllStations()
        if (weatherCheckDone) return

        val myLocation = locationProvider() ?: return
        weatherCheckDone = true

        weatherService.fetchNearestStation(
            myLocation.latitude,
            myLocation.longitude,
            System.currentTimeMillis()
        ) { station, error ->
            scope.launch(Dispatchers.Main) {
                if (error == null && station != null) {
                    lastFoundStation = station
                    updateWeatherUi()
                }
            }
        }
    }

    fun updateWeatherUi() {
        activity.findViewById<android.widget.TextView>(R.id.weatherStationText).visibility =
            android.view.View.GONE
    }
}
