package fi.anssi.kalakartta.data

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class JsonService {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun export(contentResolver: ContentResolver, uri: Uri, catches: List<FishCatch>, places: List<PlaceOfInterest>) {
        val root = JSONObject()
        val catchesArray = JSONArray()

        catches.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("species", it.species)
            val eventTypeToExport = it.eventType ?: if (it.species != "UNKNOWN" && it.species.isNotEmpty()) FishCatch.CAUGHT_FISH else null
            if (eventTypeToExport != null) obj.put("eventType", eventTypeToExport)
            obj.put("latitude", String.format(Locale.US, "%.5f", it.latitude).toDouble())
            obj.put("longitude", String.format(Locale.US, "%.5f", it.longitude).toDouble())
            android.util.Log.d("JsonService", "Exporting catch: species=${it.species}, lat=${it.latitude}, lon=${it.longitude}")
            if (it.caughtAt != null && it.caughtAt!! > 0) {
                obj.put("caughtAt", isoFormat.format(Date(it.caughtAt!!)))
            }
            if (it.weight != null) obj.put("weight", it.weight)
            if (it.length != null) obj.put("length", it.length)
            obj.put("method", it.method)
            if (it.strikeDepth != null) obj.put("strikeDepth", it.strikeDepth)
            if (it.waterDepth != null) obj.put("waterDepth", it.waterDepth)
            if (it.waterTemp != null) obj.put("waterTemp", it.waterTemp)
            if (it.airTemp != null) obj.put("airTemp", it.airTemp)
            if (it.cloudiness != null) obj.put("cloudiness", it.cloudiness)
            if (it.rain != null) obj.put("rain", it.rain)
            if (it.rainHourMm != null) obj.put("rainHourMm", it.rainHourMm)
            if (it.windSpeed != null) obj.put("windSpeed", it.windSpeed)
            if (it.windDirection != null) obj.put("windDirection", it.windDirection)
            if (it.pressure != null) obj.put("pressure", it.pressure)
            obj.put("weatherSource", it.weatherSource)
            if (it.weatherTime != null && it.weatherTime!! > 0) {
                obj.put("weatherTime", isoFormat.format(Date(it.weatherTime!!)))
            }
            obj.put("weatherStation", it.weatherStation)
            obj.put("additionalInfo", it.additionalInfo)
            obj.put("originalRef", it.originalRef)
            obj.put("tripNotes", it.tripNotes)
            if (it.fisherman.isNotBlank()) obj.put("fisherman", it.fisherman)
            if (it.weatherDataCompleteTime != null) obj.put("weatherDataCompleteTime", it.weatherDataCompleteTime)
            catchesArray.put(obj)
        }

        val placesArray = JSONArray()
        places.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("typeId", it.typeId)
            obj.put("latitude", String.format(Locale.US, "%.5f", it.latitude).toDouble())
            obj.put("longitude", String.format(Locale.US, "%.5f", it.longitude).toDouble())
            obj.put("name", it.name)
            obj.put("additionalInfo", it.additionalInfo)
            obj.put("originalRef", it.originalRef)
            placesArray.put(obj)
        }

        root.put("catches", catchesArray)
        root.put("places", placesArray)

        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(4).toByteArray()) // 4 indentations for human readability
        }
    }

    fun import(contentResolver: ContentResolver, uri: Uri): Pair<List<FishCatch>, List<PlaceOfInterest>> {
        val text = contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return Pair(emptyList(), emptyList())

        val catches = mutableListOf<FishCatch>()
        val places = mutableListOf<PlaceOfInterest>()

        try {
            if (text.trim().startsWith("{")) {
                val root = JSONObject(text)
                val catchesArray = root.optJSONArray("catches")
                if (catchesArray != null) {
                    catches.addAll(parseCatches(catchesArray))
                }
                val placesArray = root.optJSONArray("places")
                if (placesArray != null) {
                    places.addAll(parsePlaces(placesArray))
                }
            } else if (text.trim().startsWith("[")) {
                val jsonArray = JSONArray(text)
                catches.addAll(parseCatches(jsonArray))
            }
        } catch (e: Exception) {
            android.util.Log.e("JsonService", "Error parsing JSON", e)
        }

        return Pair(catches, places)
    }

    private fun parseCatches(jsonArray: JSONArray): List<FishCatch> {
        val result = mutableListOf<FishCatch>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                
                val caughtAtStr = obj.optString("caughtAt", "")
                val caughtAtLong = if (caughtAtStr.isNotEmpty()) {
                    try {
                        isoFormat.parse(caughtAtStr)?.time
                    } catch (_: Exception) {
                        if (obj.has("caughtAt") && !obj.isNull("caughtAt")) {
                            val ca = obj.optLong("caughtAt")
                            if (ca <= 0) null else ca
                        } else null
                    }
                } else {
                    if (obj.has("caughtAt") && !obj.isNull("caughtAt")) {
                        val ca = obj.optLong("caughtAt")
                        if (ca <= 0) null else ca
                    } else null
                }

                val weatherTimeStr = obj.optString("weatherTime", "")
                val weatherTimeLong = if (weatherTimeStr.isNotEmpty()) {
                    try {
                        isoFormat.parse(weatherTimeStr)?.time
                    } catch (_: Exception) {
                        if (obj.has("weatherTime") && !obj.isNull("weatherTime")) {
                            val wt = obj.optLong("weatherTime")
                            if (wt <= 0) null else wt
                        } else null
                    }
                } else {
                    if (obj.has("weatherTime") && !obj.isNull("weatherTime")) {
                        val wt = obj.optLong("weatherTime")
                        if (wt <= 0) null else wt
                    } else null
                }

                val catch = FishCatch(
                    id = 0,
                    species = obj.optString("species", "UNKNOWN"),
                    eventType = if (obj.isNull("eventType")) {
                        val species = obj.optString("species", "UNKNOWN")
                        if (species != "UNKNOWN" && species != "") {
                            FishCatch.CAUGHT_FISH
                        } else {
                            null
                        }
                    } else {
                        obj.optString("eventType")
                    },
                    latitude = String.format(Locale.US, "%.5f", if (obj.isNull("latitude") || !obj.has("latitude")) 60.0 else obj.optDouble("latitude", 60.0)).toDouble(),
                    longitude = String.format(Locale.US, "%.5f", if (obj.isNull("longitude") || !obj.has("longitude")) 24.0 else obj.optDouble("longitude", 24.0)).toDouble(),
                    caughtAt = caughtAtLong,
                    weight = if (obj.isNull("weight")) null else obj.optLong("weight"),
                    length = if (obj.isNull("length")) null else obj.optLong("length"),
                    method = obj.optString("method", ""),
                    strikeDepth = if (obj.isNull("strikeDepth")) null else obj.optDouble("strikeDepth"),
                    waterDepth = if (obj.isNull("waterDepth")) null else obj.optDouble("waterDepth"),
                    waterTemp = if (obj.isNull("waterTemp")) null else obj.optDouble("waterTemp"),
                    airTemp = if (obj.isNull("airTemp")) null else obj.optDouble("airTemp"),
                    cloudiness = if (obj.isNull("cloudiness")) null else obj.optLong("cloudiness"),
                    rain = if (obj.isNull("rain")) null else obj.optLong("rain"),
                    rainHourMm = if (obj.isNull("rainHourMm")) null else obj.optDouble("rainHourMm"),
                    windSpeed = if (obj.isNull("windSpeed")) null else obj.optDouble("windSpeed"),
                    windDirection = if (obj.isNull("windDirection")) null else obj.optLong("windDirection"),
                    pressure = if (obj.isNull("pressure")) null else obj.optDouble("pressure"),
                    weatherSource = obj.optString("weatherSource", ""),
                    weatherTime = weatherTimeLong,
                    weatherStation = obj.optString("weatherStation", ""),
                    additionalInfo = obj.optString("additionalInfo", ""),
                    originalRef = obj.optString("originalRef", ""),
                    tripNotes = obj.optString("tripNotes", ""),
                    fisherman = obj.optString("fisherman", ""),
                    weatherDataCompleteTime = if (obj.isNull("weatherDataCompleteTime")) null else obj.optLong("weatherDataCompleteTime")
                )
                result.add(catch)
            } catch (e: Exception) {
                android.util.Log.e("JsonService", "Error parsing FishCatch object at index $i", e)
            }
        }
        return result
    }

    private fun parsePlaces(jsonArray: JSONArray): List<PlaceOfInterest> {
        val result = mutableListOf<PlaceOfInterest>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val place = PlaceOfInterest(
                    id = 0,
                    typeId = obj.optString("typeId", "UNKNOWN"),
                    latitude = String.format(Locale.US, "%.5f", if (obj.isNull("latitude") || !obj.has("latitude")) 60.0 else obj.optDouble("latitude", 60.0)).toDouble(),
                    longitude = String.format(Locale.US, "%.5f", if (obj.isNull("longitude") || !obj.has("longitude")) 24.0 else obj.optDouble("longitude", 24.0)).toDouble(),
                    name = obj.optString("name", ""),
                    additionalInfo = obj.optString("additionalInfo", ""),
                    originalRef = obj.optString("originalRef", "")
                )
                result.add(place)
            } catch (e: Exception) {
                android.util.Log.e("JsonService", "Error parsing PlaceOfInterest object at index $i", e)
            }
        }
        return result
    }

    private fun JSONObject.optDoubleSafe(key: String, defaultValue: Double): Double {
        val value = optDouble(key, defaultValue)
        return if (value.isNaN()) defaultValue else value
    }

    private fun JSONObject.putSafe(key: String, value: Double) {
        if (value.isNaN() || value.isInfinite()) {
            put(key, 0.0)
        } else {
            put(key, value)
        }
    }
}