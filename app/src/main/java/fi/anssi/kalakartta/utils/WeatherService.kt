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
    val longitude: Double,
    val startTime: Long? = null,
    val endTime: Long? = null
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

    fun fetchWeatherData(fmisid: String, targetTime: Long? = null, callback: (Map<String, Double>?, Long?, String?) -> Unit) {
        Thread {
            val result = fetchWeatherDataSync(fmisid, targetTime)
            callback(result.first, result.second, result.third)
        }.start()
    }

    fun fetchWeatherDataSync(fmisid: String, targetTime: Long? = null): Triple<Map<String, Double>?, Long?, String?> {
        return try {
            val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            
            var urlString = OBSERVATIONS_URL + fmisid
            if (targetTime != null) {
                // Haetaan dataa +/- 12 tuntia targetTimesta, jotta varmasti löytyy lähin havainto
                val start = isoFormat.format(java.util.Date(targetTime - 12 * 60 * 60 * 1000L))
                val end = isoFormat.format(java.util.Date(targetTime + 12 * 60 * 60 * 1000L))
                urlString += "&starttime=$start&endtime=$end"
            }
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != 200) {
                return Triple(null, null, "Virhe ladattaessa säätietoja: ${connection.responseCode}")
            }

            val (data, observationTime) = connection.inputStream.use { 
                parseWeatherObservations(it, targetTime)
            }
            if (data.isEmpty()) {
                Triple(null, null, "Ei säädataa saatavilla tälle ajankohdalle (FMI).")
            } else {
                Triple(data, observationTime, null)
            }
        } catch (e: Exception) {
            Triple(null, null, "Virhe haettaessa säätietoja: ${e.message}")
        }
    }

    suspend fun fetchWeatherDataSuspend(fmisid: String, targetTime: Long? = null): Triple<Map<String, Double>?, Long?, String?> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fetchWeatherDataSync(fmisid, targetTime)
        }
    }

    private fun parseWeatherObservations(inputStream: java.io.InputStream, targetTime: Long?): Pair<Map<String, Double>, Long?> {
        val allObservations = mutableMapOf<Long, MutableMap<String, Double>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, null)

        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        var eventType = parser.eventType
        var currentParam = ""
        var currentTime: Long? = null
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            try {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "Time" -> {
                                val timeStr = parser.nextText()
                                if (timeStr.isNotEmpty()) {
                                    try {
                                        currentTime = isoFormat.parse(timeStr)?.time
                                    } catch (_: Exception) {}
                                }
                            }
                            "ParameterName" -> currentParam = parser.nextText()
                            "ParameterValue" -> {
                                val valueStr = parser.nextText()
                                val value = valueStr.toDoubleOrNull()
                                if (value != null && !value.isNaN() && currentParam.isNotEmpty() && currentTime != null) {
                                    val observation = allObservations.getOrPut(currentTime!!) { mutableMapOf() }
                                    observation[currentParam] = value
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ohitetaan yksittäiset parsimisvirheet
            }
            eventType = parser.next()
        }

        if (allObservations.isEmpty()) {
            return Pair(emptyMap(), null)
        }

        // Valitaan lähin havainto
        val finalTargetTime = targetTime ?: System.currentTimeMillis()
        var bestTime = allObservations.keys.first()
        var minDiff = abs(bestTime - finalTargetTime)

        for (time in allObservations.keys) {
            val diff = abs(time - finalTargetTime)
            if (diff < minDiff) {
                minDiff = diff
                bestTime = time
            }
        }

        return Pair(allObservations[bestTime]!!, bestTime)
    }

    suspend fun fetchAllStationsSuspend(): List<WeatherStation>? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val result = kotlin.coroutines.suspendCoroutine<List<WeatherStation>?> { continuation ->
                fetchAllStations { stations, _ ->
                    continuation.resumeWith(Result.success(stations))
                }
            }
            result
        }
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

                val stations = connection.inputStream.use { 
                    parseStations(it)
                }
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

    fun fetchNearestStation(currentLat: Double, currentLon: Double, targetTime: Long? = null, callback: (WeatherStation?, String?) -> Unit) {
        val stations = synchronized(fetchLock) { cachedStations }
        if (stations != null) {
            findNearest(currentLat, currentLon, targetTime, stations, callback)
            return
        }

        fetchAllStations { fetchedStations, error ->
            if (error != null) {
                callback(null, error)
            } else if (fetchedStations != null) {
                findNearest(currentLat, currentLon, targetTime, fetchedStations, callback)
            } else {
                callback(null, "Sääasemia ei saatu ladattua.")
            }
        }
    }

    private fun findNearest(lat: Double, lon: Double, targetTime: Long?, stations: List<WeatherStation>, callback: (WeatherStation?, String?) -> Unit) {
        if (stations.isEmpty()) {
            callback(null, "Sääasemia ei löytynyt.")
            return
        }

        var nearest: WeatherStation? = null
        var minDistance = Double.MAX_VALUE

        for (station in stations) {
            // Tarkistetaan onko asema ollut toiminnassa kyseisellä hetkellä
            if (targetTime != null) {
                if (station.startTime != null && targetTime < station.startTime) continue
                if (station.endTime != null && targetTime > station.endTime) continue
            }

            val distance = calculateDistance(lat, lon, station.latitude, station.longitude)
            
            // Ei huomioida sääasemia, jotka ovat yli 300 km päässä
            if (distance > 300.0) continue

            if (distance < minDistance) {
                minDistance = distance
                nearest = station
            }
        }
        
        if (nearest == null) {
            callback(null, "Ei sopivaa sääasemaa 300 km säteellä kyseisenä ajankohtana.")
        } else {
            callback(nearest, null)
        }
    }

    private fun parseStations(inputStream: java.io.InputStream): List<WeatherStation> {
        val stations = mutableListOf<WeatherStation>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, null)

        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        var eventType = parser.eventType
        var currentFmisid = ""
        var currentName = ""
        var currentLat = 0.0
        var currentLon = 0.0
        var currentStartTime: Long? = null
        var currentEndTime: Long? = null
        var isWeatherStation = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            try {
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
                                    val lat = pos[0].toDoubleOrNull() ?: 0.0
                                    val lon = pos[1].toDoubleOrNull() ?: 0.0
                                    currentLat = if (lat.isNaN()) 0.0 else lat
                                    currentLon = if (lon.isNaN()) 0.0 else lon
                                }
                            }
                            "beginPosition" -> {
                                val text = parser.nextText()
                                if (text.isNotEmpty()) {
                                    try {
                                        currentStartTime = isoFormat.parse(text)?.time
                                    } catch (_: Exception) {}
                                }
                            }
                            "endPosition" -> {
                                val indeterminate = parser.getAttributeValue(null, "indeterminatePosition")
                                if (indeterminate == "now") {
                                    currentEndTime = Long.MAX_VALUE
                                } else {
                                    val text = try { parser.nextText() } catch (_: Exception) { "" }
                                    if (text.isNotEmpty()) {
                                        try {
                                            currentEndTime = isoFormat.parse(text)?.time
                                        } catch (_: Exception) {}
                                    }
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
                                stations.add(WeatherStation(currentFmisid, currentName, currentLat, currentLon, currentStartTime, currentEndTime))
                            }
                            // Reset for next
                            currentFmisid = ""
                            currentName = ""
                            currentLat = 0.0
                            currentLon = 0.0
                            currentStartTime = null
                            currentEndTime = null
                            isWeatherStation = false
                        }
                    }
                }
            } catch (e: Exception) {
                // Ohitetaan parsimisvirheet
            }
            eventType = parser.next()
        }
        return stations
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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
