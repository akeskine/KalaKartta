package fi.anssi.kalakartta.utils

import android.content.Context
import android.location.Location
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

data class WeatherStation(
    val fmisid: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

class WeatherService(private val context: Context) {

    companion object {
        private var cachedStations: List<WeatherStation>? = null
        private var isFetchingStations = false
        private val pendingCallbacks = mutableListOf<(List<WeatherStation>?, String?) -> Unit>()
        private val fetchLock = Any()
    }

    private val STATIONS_URL = "https://opendata.fmi.fi/wfs?request=getFeature&storedquery_id=fmi::ef::stations"
    private val OBSERVATIONS_URL = "https://opendata.fmi.fi/wfs?request=getFeature&storedquery_id=fmi::observations::weather::simple&fmisid="

    fun fetchWeatherData(fmisid: String, callback: (Map<String, Double>?, String?) -> Unit) {
        Thread {
            try {
                val url = URL(OBSERVATIONS_URL + fmisid)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode != 200) {
                    callback(null, "Virhe ladattaessa säätietoja: ${connection.responseCode}")
                    return@Thread
                }

                val data = parseWeatherObservations(connection.inputStream)
                callback(data, null)
            } catch (e: Exception) {
                callback(null, "Virhe haettaessa säätietoja: ${e.message}")
            }
        }.start()
    }

    private fun parseWeatherObservations(inputStream: java.io.InputStream): Map<String, Double> {
        val data = mutableMapOf<String, Double>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentParam = ""
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "ParameterName" -> currentParam = parser.nextText()
                        "ParameterValue" -> {
                            val value = parser.nextText().toDoubleOrNull()
                            if (value != null && currentParam.isNotEmpty()) {
                                data[currentParam] = value
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return data
    }

    fun fetchAllStations(callback: ((List<WeatherStation>?, String?) -> Unit)? = null) {
        synchronized(fetchLock) {
            if (cachedStations != null) {
                callback?.invoke(cachedStations, null)
                return
            }
            if (callback != null) {
                pendingCallbacks.add(callback)
            }
            if (isFetchingStations) {
                return
            }
            isFetchingStations = true
        }

        Thread {
            try {
                val url = URL(STATIONS_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                if (connection.responseCode != 200) {
                    val error = "Virhe ladattaessa sääasemia: ${connection.responseCode}"
                    val callbacksToNotify = mutableListOf<(List<WeatherStation>?, String?) -> Unit>()
                    synchronized(fetchLock) {
                        isFetchingStations = false
                        callbacksToNotify.addAll(pendingCallbacks)
                        pendingCallbacks.clear()
                    }
                    callbacksToNotify.forEach { it(null, error) }
                    return@Thread
                }

                val stations = parseStations(connection.inputStream)
                val callbacksToNotify = mutableListOf<(List<WeatherStation>?, String?) -> Unit>()
                synchronized(fetchLock) {
                    cachedStations = stations
                    isFetchingStations = false
                    callbacksToNotify.addAll(pendingCallbacks)
                    pendingCallbacks.clear()
                }
                callbacksToNotify.forEach { it(stations, null) }
            } catch (e: Exception) {
                val error = "Virhe sääasemien haussa: ${e.message}"
                val callbacksToNotify = mutableListOf<(List<WeatherStation>?, String?) -> Unit>()
                synchronized(fetchLock) {
                    isFetchingStations = false
                    callbacksToNotify.addAll(pendingCallbacks)
                    pendingCallbacks.clear()
                }
                callbacksToNotify.forEach { it(null, error) }
            }
        }.start()
    }

    fun fetchNearestStation(currentLat: Double, currentLon: Double, callback: (WeatherStation?, String?) -> Unit) {
        val stations = synchronized(fetchLock) { cachedStations }
        if (stations != null) {
            findNearest(currentLat, currentLon, stations, callback)
            return
        }

        fetchAllStations { fetchedStations, error ->
            if (error != null) {
                callback(null, error)
            } else if (fetchedStations != null) {
                findNearest(currentLat, currentLon, fetchedStations, callback)
            } else {
                callback(null, "Sääasemia ei saatu ladattua.")
            }
        }
    }

    private fun findNearest(lat: Double, lon: Double, stations: List<WeatherStation>, callback: (WeatherStation?, String?) -> Unit) {
        if (stations.isEmpty()) {
            callback(null, "Sääasemia ei löytynyt.")
            return
        }

        var nearest: WeatherStation? = null
        var minDistance = Double.MAX_VALUE

        for (station in stations) {
            val distance = calculateDistance(lat, lon, station.latitude, station.longitude)
            if (distance < minDistance) {
                minDistance = distance
                nearest = station
            }
        }
        callback(nearest, null)
    }

    private fun parseStations(inputStream: java.io.InputStream): List<WeatherStation> {
        val stations = mutableListOf<WeatherStation>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentFmisid = ""
        var currentName = ""
        var currentLat = 0.0
        var currentLon = 0.0
        var isWeatherStation = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "identifier" -> {
                            if (parser.getAttributeValue(null, "codeSpace")?.endsWith("/fmisid") == true) {
                                currentFmisid = parser.nextText()
                            }
                        }
                        "name" -> {
                            val codeSpace = parser.getAttributeValue(null, "codeSpace")
                            if (codeSpace?.endsWith("/name") == true) {
                                currentName = parser.nextText()
                            } else if (codeSpace == null) {
                                // Esim. ef:name
                                val text = parser.nextText()
                                if (text.isNotEmpty()) {
                                    currentName = text
                                }
                            }
                        }
                        "pos" -> {
                            val pos = parser.nextText().split(" ")
                            if (pos.size >= 2) {
                                currentLat = pos[0].toDoubleOrNull() ?: 0.0
                                currentLon = pos[1].toDoubleOrNull() ?: 0.0
                            }
                        }
                        "belongsTo" -> {
                            val title = parser.getAttributeValue("http://www.w3.org/1999/xlink", "title") ?: ""
                            if (title.contains("Automaattinen sääasema", ignoreCase = true) ||
                                title.contains("lentosääasema", ignoreCase = true) ||
                                title.contains("weather", ignoreCase = true) ||
                                title.contains("AWOS", ignoreCase = true)) {
                                isWeatherStation = true
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "EnvironmentalMonitoringFacility") {
                        if (isWeatherStation && currentFmisid.isNotEmpty()) {
                            stations.add(WeatherStation(currentFmisid, currentName, currentLat, currentLon))
                        }
                        // Reset for next
                        currentFmisid = ""
                        currentName = ""
                        currentLat = 0.0
                        currentLon = 0.0
                        isWeatherStation = false
                    }
                }
            }
            eventType = parser.next()
        }
        return stations
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
