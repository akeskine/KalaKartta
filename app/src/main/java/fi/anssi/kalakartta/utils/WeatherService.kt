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

    private val STATIONS_URL = "https://opendata.fmi.fi/wfs?request=getFeature&storedquery_id=fmi::ef::stations"

    fun fetchNearestStation(currentLat: Double, currentLon: Double, callback: (WeatherStation?, String?) -> Unit) {
        Thread {
            try {
                val url = URL(STATIONS_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                if (connection.responseCode != 200) {
                    callback(null, "Virhe ladattaessa sääasemia: ${connection.responseCode}")
                    return@Thread
                }

                val stations = parseStations(connection.inputStream)
                if (stations.isEmpty()) {
                    callback(null, "Sääasemia ei löytynyt.")
                    return@Thread
                }

                var nearest: WeatherStation? = null
                var minDistance = Double.MAX_VALUE

                for (station in stations) {
                    val distance = calculateDistance(currentLat, currentLon, station.latitude, station.longitude)
                    if (distance < minDistance) {
                        minDistance = distance
                        nearest = station
                    }
                }

                callback(nearest, null)
            } catch (e: Exception) {
                callback(null, "Virhe haettaessa säätietoja: ${e.message}")
            }
        }.start()
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
