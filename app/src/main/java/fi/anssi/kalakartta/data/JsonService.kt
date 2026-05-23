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
            obj.putSafe("latitude", it.latitude)
            obj.putSafe("longitude", it.longitude)
            obj.put("caughtAt", isoFormat.format(Date(it.caughtAt)))
            obj.put("weight", it.weight)
            obj.put("length", it.length)
            obj.put("method", it.method)
            obj.putSafe("strikeDepth", it.strikeDepth)
            obj.putSafe("waterDepth", it.waterDepth)
            obj.putSafe("waterTemp", it.waterTemp)
            obj.putSafe("airTemp", it.airTemp)
            obj.put("cloudiness", it.cloudiness)
            obj.put("rain", it.rain)
            obj.putSafe("windSpeed", it.windSpeed)
            obj.put("windDirection", it.windDirection)
            obj.putSafe("pressure", it.pressure)
            obj.put("weatherSource", it.weatherSource)
            obj.put("weatherTime", if (it.weatherTime > 0) isoFormat.format(Date(it.weatherTime)) else "")
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
                        0L
                    }
                } else {
                    obj.optLong("caughtAt", 0L)
                }

                val weatherTimeStr = obj.optString("weatherTime", "")
                val weatherTimeLong = if (weatherTimeStr.isNotEmpty()) {
                    try {
                        isoFormat.parse(weatherTimeStr)?.time ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                } else {
                    obj.optLong("weatherTime", 0L)
                }

                result.add(
                    FishCatch(
                        id = 0,
                        species = obj.optString("species", "UNKNOWN"),
                        latitude = obj.optDoubleSafe("latitude", 0.0),
                        longitude = obj.optDoubleSafe("longitude", 0.0),
                        caughtAt = caughtAtLong,
                        weight = obj.optLong("weight", 0),
                        length = obj.optLong("length", 0),
                        method = obj.optString("method", ""),
                        strikeDepth = obj.optDoubleSafe("strikeDepth", 0.0),
                        waterDepth = obj.optDoubleSafe("waterDepth", 0.0),
                        waterTemp = obj.optDoubleSafe("waterTemp", 0.0),
                        airTemp = obj.optDoubleSafe("airTemp", 0.0),
                        cloudiness = obj.optLong("cloudiness", 0),
                        rain = obj.optLong("rain", 0),
                        windSpeed = obj.optDoubleSafe("windSpeed", 0.0),
                        windDirection = obj.optLong("windDirection", 0),
                        pressure = obj.optDoubleSafe("pressure", 0.0),
                        weatherSource = obj.optString("weatherSource", ""),
                        weatherTime = weatherTimeLong,
                        weatherStation = obj.optString("weatherStation", ""),
                        additionalInfo = obj.optString("additionalInfo", ""),
                        originalRef = obj.optString("originalRef", ""),
                        tripNotes = obj.optString("tripNotes", "")
                    )
                )
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