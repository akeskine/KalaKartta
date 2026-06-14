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

    fun export(contentResolver: ContentResolver, uri: Uri, list: List<FishCatch>) {
        val jsonArray = JSONArray()

        list.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("species", it.species)
            obj.put("latitude", String.format(Locale.US, "%.5f", it.latitude).toDouble())
            obj.put("longitude", String.format(Locale.US, "%.5f", it.longitude).toDouble())
            android.util.Log.d("JsonService", "Exporting catch: species=${it.species}, lat=${it.latitude}, lon=${it.longitude}")
            if (it.caughtAt > 0) {
                obj.put("caughtAt", isoFormat.format(Date(it.caughtAt)))
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
            jsonArray.put(obj)
        }

        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(jsonArray.toString(4).toByteArray()) // 4 indentations for human readability
        }
    }

    fun import(contentResolver: ContentResolver, uri: Uri): List<FishCatch> {
        val text = contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return emptyList()

        val jsonArray = JSONArray(text)
        val result = mutableListOf<FishCatch>()

        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)

                val caughtAtStr = obj.optString("caughtAt", "")
                val caughtAtLong = if (caughtAtStr.isNotEmpty()) {
                    try {
                        isoFormat.parse(caughtAtStr)?.time ?: 0L
                    } catch (_: Exception) {
                        // If parsing as ISO string fails, try to read as Long (legacy or missing)
                        if (obj.has("caughtAt") && !obj.isNull("caughtAt")) obj.optLong("caughtAt", 0L) else 0L
                    }
                } else {
                    if (obj.has("caughtAt") && !obj.isNull("caughtAt")) obj.optLong("caughtAt", 0L) else 0L
                }

                val weatherTimeStr = obj.optString("weatherTime", "")
                val weatherTimeLong = if (weatherTimeStr.isNotEmpty()) {
                    try {
                        isoFormat.parse(weatherTimeStr)?.time
                    } catch (_: Exception) {
                        // If parsing as ISO string fails, try to read as Long (legacy or missing)
                        if (obj.has("weatherTime") && !obj.isNull("weatherTime")) obj.optLong("weatherTime") else null
                    }
                } else {
                    if (obj.has("weatherTime") && !obj.isNull("weatherTime")) obj.optLong("weatherTime") else null
                }

                val catch = FishCatch(
                        id = 0,
                        species = obj.optString("species", "UNKNOWN"),
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
                        tripNotes = obj.optString("tripNotes", "")
                    )
                android.util.Log.d("JsonService", "Imported catch: species=${catch.species}, lat=${catch.latitude}, lon=${catch.longitude}")
                result.add(catch)
            } catch (e: Exception) {
                // Skip invalid objects
                android.util.Log.e("JsonService", "Error parsing JSON object at index $i", e)
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