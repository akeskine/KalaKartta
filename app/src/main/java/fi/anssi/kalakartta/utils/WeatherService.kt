package fi.anssi.kalakartta.utils

import android.content.Context
import android.location.Location
import android.util.Xml
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.data.PressureSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

data class PressureStationResult(
    val station: WeatherStation,
    val pressure: Double,
    val pressureSamples: List<PressureSample>
)

data class ForecastRow(
    val time: Long,
    val parameters: Map<String, Double>
)

internal fun buildPressureSamplesUrl(
    observationsUrl: String,
    fmisid: String,
    startTime: String,
    endTime: String,
    includeTimestep: Boolean
): String {
    val url = "$observationsUrl$fmisid&starttime=$startTime&endtime=$endTime"
    return if (includeTimestep) "$url&timestep=60" else url
}

internal fun extrapolatePressureSampleIntoCompletionWindow(
    samples: List<PressureSample>,
    startTime: Long,
    endTime: Long
): List<PressureSample> {
    val sixHoursMillis = 6 * 60 * 60 * 1000L
    val fiveHoursMillis = 5 * 60 * 60 * 1000L
    val caughtAt = startTime + sixHoursMillis
    val completionWindowStart = caughtAt + fiveHoursMillis
    val completionWindowEnd = caughtAt + sixHoursMillis
    if (endTime < completionWindowStart) return samples

    val validSamples = samples
        .filter { it.pressure.isFinite() }
        .sortedBy { it.time }
    val availableWindowEnd = minOf(endTime, completionWindowEnd)
    if (validSamples.any { it.time in completionWindowStart..availableWindowEnd }) return samples

    val lastTwo = validSamples.takeLast(2)
    if (lastTwo.size < 2) return samples

    val first = lastTwo[0]
    val last = lastTwo[1]
    val elapsedMillis = last.time - first.time
    if (elapsedMillis <= 0L) return samples

    val targetTime = availableWindowEnd
    val elapsedHours = elapsedMillis.toDouble() / (60 * 60 * 1000)
    val rate = (last.pressure - first.pressure) / elapsedHours
    val projectedPressure = last.pressure + rate * (targetTime - last.time).toDouble() / (60 * 60 * 1000)
    if (!projectedPressure.isFinite()) return samples

    return (samples + PressureSample(targetTime, projectedPressure)).sortedBy { it.time }
}

class WeatherService(private val context: Context) {

    companion object {
        private var cachedStations: List<WeatherStation>? = null
        private var isFetchingStations = false
        private val pendingCallbacks = mutableListOf<(List<WeatherStation>?, String?) -> Unit>()
        private val fetchLock = Any()
    }

    private val STATIONS_URL = "https://opendata.fmi.fi/wfs?request=getFeature&storedquery_id=fmi::ef::stations"
    private val OBSERVATIONS_URL = "https://opendata.fmi.fi/wfs?request=getFeature&storedquery_id=fmi::observations::weather::simple&fmisid="
    private val FORECAST_URL = "https://opendata.fmi.fi/wfs?request=getFeature&storedquery_id=fmi::forecast::harmonie::surface::point::simple"
    private val requestScope: CoroutineScope = if (context is LifecycleOwner) {
        context.lifecycleScope
    } else {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    suspend fun fetchForecastSuspend(
        latitude: Double,
        longitude: Double,
        targetHours: List<Int>
    ): List<Pair<Int, ForecastRow>> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (targetHours.isEmpty()) return@withContext emptyList()

            try {
                val now = System.currentTimeMillis()
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val startTime = dateFormat.format(java.util.Date(now))
                val endTime = dateFormat.format(java.util.Date(now + 12 * 60 * 60 * 1000L))
                val urlString = "$FORECAST_URL&latlon=$latitude,$longitude" +
                    "&starttime=$startTime&endtime=$endTime&timestep=60" +
                    "&parameters=Temperature,WindSpeedMS,WindGust,WindDirection,Precipitation1h,TotalCloudCover"

                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext emptyList()
                }

                val rows = connection.inputStream.use { parseAllWeatherObservations(it) }
                targetHours.sorted().mapNotNull { hours ->
                    val targetTime = now + hours * 60 * 60 * 1000L
                    val closest = rows.minByOrNull { abs(it.key - targetTime) } ?: return@mapNotNull null
                    hours to ForecastRow(closest.key, closest.value.toMap())
                }
            } catch (e: Exception) {
                android.util.Log.e("KalaKartta", "Virhe sääennusteen haussa: ${e.message}", e)
                emptyList()
            }
        }
    }

    fun fetchWeatherData(fmisid: String, targetTime: Long? = null, callback: (Map<String, Double>?, Long?, String?) -> Unit) {
        requestScope.launch(Dispatchers.IO) {
            val result = fetchWeatherDataSync(fmisid, targetTime)
            callback(result.first, result.second, result.third)
        }
    }

    fun fetchWeatherFromMultipleStations(lat: Double, lon: Double, targetTime: Long? = null, existingData: Map<String, Double>? = null, catchInfo: String = "", callback: (Map<String, Double>?, Long?, String?, String) -> Unit) {
        requestScope.launch(Dispatchers.IO) {
            if (catchInfo.isNotEmpty()) {
                android.util.Log.d("KalaKartta", "Päivitetään säätietoja kohteelle: $catchInfo")
            }
            fetchNearestStations(lat, lon, targetTime, 5) { stations, error ->
                if (stations.isNullOrEmpty()) {
                    callback(null, null, error ?: "Ei sopivia sääasemia.", "")
                    return@fetchNearestStations
                }

                val finalData = mutableMapOf<String, Double>()
                if (existingData != null) {
                    finalData.putAll(existingData)
                }
                val usedStations = mutableListOf<String>()
                var pressureStationInfo: String? = null
                var bestTime: Long? = null

                val keysToFill = mutableSetOf("t2m", "nn_ll01", "n_man", "r_1h", "ws_10min", "wd_10min", "p_sea", "p_msl", "ri_10min")
            android.util.Log.d("KalaKartta", "Etsitään tietoja: $keysToFill")

                // Poistetaan jo olemassa olevat avaimet
                for (key in finalData.keys) {
                    keysToFill.remove(key)
                    if (key == "r_1h") keysToFill.remove("ri_10min")
                    if (key == "p_sea") keysToFill.remove("p_msl")
                    if (key == "p_msl") keysToFill.remove("p_sea")
                    if (key == "nn_ll01") keysToFill.remove("n_man")
                    if (key == "n_man") keysToFill.remove("nn_ll01")
                }

                if (keysToFill.isEmpty()) {
                    callback(finalData, bestTime, null, "EI_MUUTOKSIA")
                    return@fetchNearestStations
                }

                var anyNewData = false
                for ((index, station) in stations.withIndex()) {
                    val distance = calculateDistance(lat, lon, station.latitude, station.longitude)
                    android.util.Log.d("KalaKartta", "Kokeillaan asemaa #${index + 1}: ${station.fmisid}:${station.name}, etäisyys: ${String.format("%.1f", distance)} km")
                    val result = fetchWeatherDataSync(station.fmisid, targetTime)
                    val data = result.first
                    if (data != null && data.isNotEmpty()) {
                        android.util.Log.d("KalaKartta", "Asema palautti: $data")
                        
                        val providesPressure = data.containsKey("p_sea") || data.containsKey("p_msl")
                        val neededPressure = keysToFill.contains("p_sea") || keysToFill.contains("p_msl")
                        
                        var addedAnyFromThisStation = false
                        for (key in keysToFill.toList()) {
                            if (data.containsKey(key)) {
                                finalData[key] = data[key]!!
                                keysToFill.remove(key)
                                anyNewData = true
                                addedAnyFromThisStation = true
                                
                                // Jos saatiin jompikumpi pilvisyys, poistetaan molemmat listalta
                                if (key == "nn_ll01") keysToFill.remove("n_man")
                                if (key == "n_man") keysToFill.remove("nn_ll01")
                                
                                // Ensisijaisesti ri_10min (intensiteetti), varalla r_1h (tunnin kertymä)
                                if (key == "ri_10min") {
                                    finalData["r_1h"] = data["ri_10min"]!!
                                    keysToFill.remove("r_1h")
                                } else if (key == "r_1h") {
                                    finalData["r_1h"] = data["r_1h"]!!
                                    keysToFill.remove("ri_10min")
                                }
                            }
                        }
                        
                        // Erikoiskäsittely paineelle, jos p_sea puuttuu mutta p_msl löytyy (tai päinvastoin)
                        if (keysToFill.contains("p_sea") && data.containsKey("p_msl")) {
                            finalData["p_sea"] = data["p_msl"]!!
                            keysToFill.remove("p_sea")
                            keysToFill.remove("p_msl")
                            anyNewData = true
                            addedAnyFromThisStation = true
                        } else if (keysToFill.contains("p_msl") && data.containsKey("p_sea")) {
                            finalData["p_msl"] = data["p_sea"]!!
                            keysToFill.remove("p_msl")
                            keysToFill.remove("p_sea")
                            anyNewData = true
                            addedAnyFromThisStation = true
                        }

                        if (addedAnyFromThisStation) {
                            val info = "${station.fmisid}:${station.name}"
                            if (providesPressure && neededPressure && pressureStationInfo == null) {
                                pressureStationInfo = info
                            } else {
                                usedStations.add(info)
                            }
                            if (bestTime == null) bestTime = result.second
                        }
                    }
                    if (keysToFill.isEmpty()) break
                }

                if (pressureStationInfo != null) {
                    usedStations.add(0, pressureStationInfo!!)
                }

                val stationInfo = if (usedStations.isNotEmpty()) {
                    usedStations.joinToString(", ")
                } else if (bestTime != null) {
                    "FMI (asema tuntematon)"
                } else if (anyNewData) {
                    "FMI"
                } else if (stations.isNotEmpty()) {
                    "${stations[0].fmisid}:${stations[0].name}"
                } else {
                    "FMI"
                }

                if (!anyNewData) {
                    android.util.Log.d("KalaKartta", "Haku valmis. Saatiin: $finalData, Asema: $stationInfo")
                    callback(finalData, bestTime, null, stationInfo)
                } else {
                    android.util.Log.d("KalaKartta", "Haku valmis. Saatiin: $finalData, Asema: $stationInfo")
                    callback(finalData, bestTime, null, stationInfo)
                }
            }
        }
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

    suspend fun fetchWeatherFromMultipleStationsSuspend(lat: Double, lon: Double, targetTime: Long? = null, existingData: Map<String, Double>? = null, catchInfo: String = ""): Triple<Map<String, Double>?, Long?, String> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (catchInfo.isNotEmpty()) {
                android.util.Log.d("KalaKartta", "Päivitetään säätietoja kohteelle: $catchInfo")
            }
            val stations = fetchNearestStationsSuspend(lat, lon, targetTime, 5)
            if (stations.isNullOrEmpty()) {
                return@withContext Triple(null, null, "")
            }

            val finalData = mutableMapOf<String, Double>()
            if (existingData != null) {
                finalData.putAll(existingData)
            }
            val usedStations = mutableListOf<String>()
            var pressureStationInfo: String? = null
            var bestTime: Long? = null

            val keysToFill = mutableSetOf("t2m", "nn_ll01", "n_man", "r_1h", "ws_10min", "wd_10min", "p_sea", "p_msl", "ri_10min")
            android.util.Log.d("KalaKartta", "Etsitään tietoja: $keysToFill")
            
            // Poistetaan jo olemassa olevat avaimet
            for (key in finalData.keys) {
                keysToFill.remove(key)
                if (key == "r_1h") keysToFill.remove("ri_10min")
                if (key == "p_sea") keysToFill.remove("p_msl")
                if (key == "p_msl") keysToFill.remove("p_sea")
                if (key == "nn_ll01") keysToFill.remove("n_man")
                if (key == "n_man") keysToFill.remove("nn_ll01")
            }
            
            if (keysToFill.isEmpty()) {
                if (catchInfo.isNotEmpty()) {
                    android.util.Log.d("KalaKartta", "Piste ID: $catchInfo: Kaikki tiedot jo olemassa, ei haettavaa.")
                }
                return@withContext Triple(finalData, null, "EI_MUUTOKSIA")
            }

            var anyNewData = false
            for ((index, station) in stations.withIndex()) {
                val distance = calculateDistance(lat, lon, station.latitude, station.longitude)
                android.util.Log.d("KalaKartta", "Kokeillaan asemaa #${index + 1}: ${station.fmisid}:${station.name}, etäisyys: ${String.format("%.1f", distance)} km")
                val result = fetchWeatherDataSync(station.fmisid, targetTime)
                val data = result.first
                if (data != null && data.isNotEmpty()) {
                    android.util.Log.d("KalaKartta", "Asema palautti: $data")
                    
                    val providesPressure = data.containsKey("p_sea") || data.containsKey("p_msl")
                    val neededPressure = keysToFill.contains("p_sea") || keysToFill.contains("p_msl")
                    
                    var addedAnyFromThisStation = false
                    val foundThisStation = mutableListOf<String>()
                    for (key in keysToFill.toList()) {
                        if (data.containsKey(key)) {
                            foundThisStation.add(key)
                            finalData[key] = data[key]!!
                            keysToFill.remove(key)
                            anyNewData = true
                            addedAnyFromThisStation = true
                            
                            // Jos saatiin jompikumpi pilvisyys, poistetaan molemmat listalta
                            if (key == "nn_ll01") keysToFill.remove("n_man")
                            if (key == "n_man") keysToFill.remove("nn_ll01")
                            
                            // Ensisijaisesti ri_10min (intensiteetti), varalla r_1h (tunnin kertymä)
                            if (key == "ri_10min") {
                                finalData["r_1h"] = data["ri_10min"]!!
                                keysToFill.remove("r_1h")
                            } else if (key == "r_1h") {
                                finalData["r_1h"] = data["r_1h"]!!
                                keysToFill.remove("ri_10min")
                            }
                        }
                    }
                    if (foundThisStation.isNotEmpty()) {
                        android.util.Log.d("KalaKartta", "Asemalta #${index + 1} löytyi uusia tietoja: $foundThisStation")
                    } else {
                        android.util.Log.d("KalaKartta", "Asemalta #${index + 1} ei löytynyt mitään tarvittavista tiedoista ($keysToFill)")
                    }
                    
                    // Erikoiskäsittely paineelle, jos p_sea puuttuu mutta p_msl löytyy (tai päinvastoin)
                    if (keysToFill.contains("p_sea") && data.containsKey("p_msl")) {
                        finalData["p_sea"] = data["p_msl"]!!
                        keysToFill.remove("p_sea")
                        keysToFill.remove("p_msl")
                        anyNewData = true
                        addedAnyFromThisStation = true
                    } else if (keysToFill.contains("p_msl") && data.containsKey("p_sea")) {
                        finalData["p_msl"] = data["p_sea"]!!
                        keysToFill.remove("p_msl")
                        keysToFill.remove("p_sea")
                        anyNewData = true
                        addedAnyFromThisStation = true
                    }

                    if (addedAnyFromThisStation) {
                        val info = "${station.fmisid}:${station.name}"
                        if (providesPressure && neededPressure && pressureStationInfo == null) {
                            pressureStationInfo = info
                        } else {
                            usedStations.add(info)
                        }
                        if (bestTime == null) bestTime = result.second
                    }
                }
                if (keysToFill.isEmpty()) break
            }

            if (pressureStationInfo != null) {
                usedStations.add(0, pressureStationInfo!!)
            }

            val stationInfo = if (usedStations.isNotEmpty()) {
                usedStations.joinToString(", ")
            } else if (bestTime != null) {
                "FMI (asema tuntematon)"
            } else if (anyNewData) {
                "FMI"
            } else if (stations.isNotEmpty()) {
                "${stations[0].fmisid}:${stations[0].name}"
            } else {
                "FMI"
            }

            if (!anyNewData) {
                if (catchInfo.isNotEmpty()) {
                    android.util.Log.d("KalaKartta", "Haku valmis kohteelle $catchInfo. Ei uutta dataa sääasemilta 300km säteellä.")
                }
                android.util.Log.d("KalaKartta", "Haku valmis. Saatiin: $finalData, Asema: $stationInfo")
                Triple(finalData, bestTime, stationInfo)
            } else {
                android.util.Log.d("KalaKartta", "Haku valmis. Saatiin uutta dataa! Lopullinen: $finalData, Asema: $stationInfo")
                Triple(finalData, bestTime, stationInfo)
            }
        }
    }

    suspend fun fetchNearestStationsSuspend(currentLat: Double, currentLon: Double, targetTime: Long? = null, maxStations: Int = 5): List<WeatherStation>? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val stations = fetchAllStationsSuspend()
            if (stations != null) {
                findNearestList(currentLat, currentLon, targetTime, stations, maxStations)
            } else {
                null
            }
        }
    }

    suspend fun fetchWeatherDataSuspend(fmisid: String, targetTime: Long? = null): Triple<Map<String, Double>?, Long?, String?> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fetchWeatherDataSync(fmisid, targetTime)
        }
    }

    suspend fun fetchPressureFromMultipleStationsSuspend(
        latitude: Double,
        longitude: Double,
        caughtAt: Long
    ): PressureStationResult? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val stations = fetchNearestStationsSuspend(latitude, longitude, caughtAt, 5).orEmpty()
            val sixHoursMillis = 6 * 60 * 60 * 1000L
            val startTime = caughtAt - sixHoursMillis
            val endTime = minOf(caughtAt + sixHoursMillis, System.currentTimeMillis())
            val requiresCompleteHistory = System.currentTimeMillis() - caughtAt > sixHoursMillis

            for ((index, station) in stations.withIndex()) {
                android.util.Log.d(
                    "KalaKartta",
                    "Kokeillaan paineasemaa #${index + 1}: ${station.fmisid}:${station.name}"
                )

                val weatherData = fetchWeatherDataSuspend(station.fmisid, caughtAt).first
                val pressure = weatherData?.get("p_sea") ?: weatherData?.get("p_msl")
                if (pressure == null || !pressure.isFinite()) continue

                val samples = fetchPressureSamplesSuspend(station.fmisid, startTime, endTime)
                if (samples.size < 2) continue
                if (requiresCompleteHistory && samples.none { sample ->
                        sample.time in (caughtAt + 5 * 60 * 60 * 1000L)..(caughtAt + sixHoursMillis) &&
                                sample.pressure.isFinite()
                    }) {
                    continue
                }

                return@withContext PressureStationResult(station, pressure, samples)
            }

            null
        }
    }

    suspend fun fetchPressureSamplesSuspend(fmisid: String, startTime: Long, endTime: Long): List<PressureSample> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                
                val startStr = isoFormat.format(java.util.Date(startTime))
                val endStr = isoFormat.format(java.util.Date(endTime))
                
                val urlStrings = listOf(
                    buildPressureSamplesUrl(OBSERVATIONS_URL, fmisid, startStr, endStr, includeTimestep = true),
                    buildPressureSamplesUrl(OBSERVATIONS_URL, fmisid, startStr, endStr, includeTimestep = false)
                )

                for ((attempt, urlString) in urlStrings.withIndex()) {
                    try {
                        android.util.Log.i(
                            "KalaKartta",
                            "Haetaan ilmanpainehistoria FMI:ltä (yritys ${attempt + 1}/${urlStrings.size}): $urlString"
                        )

                        val url = URL(urlString)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000

                        if (connection.responseCode != 200) {
                            android.util.Log.e(
                                "KalaKartta",
                                "FMI-haku epäonnistui: ${connection.responseCode} ${connection.responseMessage}"
                            )
                            continue
                        }

                        val observations = connection.inputStream.use {
                            parseAllWeatherObservations(it)
                        }

                        val samples = observations.mapNotNull { (time, params) ->
                            val pressure = params["p_sea"] ?: params["p_msl"]
                            if (pressure != null) {
                                PressureSample(time, pressure)
                            } else {
                                null
                            }
                        }.sortedBy { it.time }

                        android.util.Log.d("KalaKartta", "Löydetty ${samples.size} ilmanpainenäytettä asemalta $fmisid")
                        samples.forEach {
                            android.util.Log.d("KalaKartta", "  Sample: time=${it.time}, pressure=${it.pressure}")
                        }

                        if (samples.isNotEmpty()) {
                            return@withContext extrapolatePressureSampleIntoCompletionWindow(
                                samples,
                                startTime,
                                endTime
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("KalaKartta", "Virhe painenäytteiden haussa: ${e.message}", e)
                    }
                }

                emptyList()
            } catch (e: Exception) {
                android.util.Log.e("KalaKartta", "Virhe painenäytteiden haussa: ${e.message}", e)
                emptyList<PressureSample>()
            }
        }
    }

    private fun parseAllWeatherObservations(inputStream: java.io.InputStream): Map<Long, Map<String, Double>> {
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
        return allObservations
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
                                    // android.util.Log.d("KalaKartta", "FMI Parsed: time=$currentTime, param=$currentParam, value=$value")
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

        requestScope.launch(Dispatchers.IO) {
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
                    return@launch
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
        }
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

    fun fetchNearestStations(currentLat: Double, currentLon: Double, targetTime: Long? = null, maxStations: Int = 5, callback: (List<WeatherStation>?, String?) -> Unit) {
        val stations = synchronized(fetchLock) { cachedStations }
        if (stations != null) {
            val nearest = findNearestList(currentLat, currentLon, targetTime, stations, maxStations)
            callback(nearest, if (nearest.isEmpty()) "Ei sopivia sääasemia 300 km säteellä." else null)
            return
        }

        fetchAllStations { fetchedStations, error ->
            if (error != null) {
                callback(null, error)
            } else if (fetchedStations != null) {
                val nearest = findNearestList(currentLat, currentLon, targetTime, fetchedStations, maxStations)
                callback(nearest, if (nearest.isEmpty()) "Ei sopivia sääasemia 300 km säteellä." else null)
            } else {
                callback(null, "Sääasemia ei saatu ladattua.")
            }
        }
    }

    private fun findNearestList(lat: Double, lon: Double, targetTime: Long?, stations: List<WeatherStation>, maxStations: Int): List<WeatherStation> {
        if (stations.isEmpty()) return emptyList()

        return stations.asSequence()
            .filter { station ->
                if (targetTime != null) {
                    if (station.startTime != null && targetTime < station.startTime) return@filter false
                    if (station.endTime != null && targetTime > station.endTime) return@filter false
                }
                true
            }
            .map { station ->
                station to calculateDistance(lat, lon, station.latitude, station.longitude)
            }
            .filter { it.second <= 300.0 }
            .sortedBy { it.second }
            .take(maxStations)
            .map { it.first }
            .toList()
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

fun formatForecastSpeech(hours: Int, row: ForecastRow): String? {
    val values = row.parameters
    val parts = mutableListOf<String>()
    values["Temperature"]?.let { temperature ->
        val sign = if (temperature >= 0) "+" else ""
        parts.add("$sign${formatForecastNumber(temperature)} astetta")
    }
    values["TotalCloudCover"]?.let { parts.add(formatCloudCover(it)) }
    values["Precipitation1h"]?.let { parts.add(formatPrecipitation(it)) }

    val windParts = mutableListOf<String>()
    values["WindDirection"]?.let { windParts.add("Tuuli ${formatWindDirection(it)}") }
    values["WindSpeedMS"]?.let { windParts.add("${formatForecastNumber(it)} metriä sekunnissa") }
    values["WindGust"]?.let { windParts.add("puuskissa ${formatForecastNumber(it)} metriä sekunnissa") }
    if (windParts.isNotEmpty()) {
        parts.add(windParts.joinToString(" "))
    }

    if (parts.isEmpty()) return null
    return "Sää ${formatHourFinnish(hours)} päästä: ${parts.joinToString(", ")}."
}

private fun formatForecastNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value).replace('.', ',')
    }
}

private fun formatHourFinnish(hours: Int): String = when (hours) {
    1 -> "yhden tunnin"
    3 -> "kolmen tunnin"
    6 -> "kuuden tunnin"
    12 -> "kahdentoista tunnin"
    else -> "$hours tunnin"
}

private fun formatCloudCover(value: Double): String = when {
    value < 20.0 -> "selkeää"
    value < 33.0 -> "melkein selkeää"
    value < 72.0 -> "puolipilvistä"
    value < 93.0 -> "pilvistä"
    else -> "täysin pilvistä"
}

private fun formatPrecipitation(value: Double): String = when {
    value < 0.025 -> "ei sadetta"
    value < 0.4 -> "heikkoa sadetta"
    value < 4.0 -> "sadetta"
    else -> "runsasta sadetta"
}

private fun formatWindDirection(value: Double): String {
    val directions = listOf(
        "Pohjoisesta", "Koillisesta", "Idästä", "Kaakosta",
        "Etelästä", "Lounaasta", "Lännestä", "Luoteesta"
    )
    val normalized = ((value % 360.0) + 360.0) % 360.0
    val index = floor((normalized + 22.5) / 45.0).toInt() % directions.size
    return directions[index]
}
