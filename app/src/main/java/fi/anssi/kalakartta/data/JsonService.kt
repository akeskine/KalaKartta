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
            obj.put("latitude", it.latitude)
            obj.put("longitude", it.longitude)
            obj.put("caughtAt", isoFormat.format(Date(it.caughtAt)))
            obj.put("weight", it.weight)
            obj.put("length", it.length)
            obj.put("method", it.method)
            obj.put("strikeDepth", it.strikeDepth)
            obj.put("waterDepth", it.waterDepth)
            obj.put("waterTemp", it.waterTemp)
            obj.put("airTemp", it.airTemp)
            obj.put("cloudiness", it.cloudiness)
            obj.put("rain", it.rain)
            obj.put("windSpeed", it.windSpeed)
            obj.put("windDirection", it.windDirection)
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
            val obj = jsonArray.getJSONObject(i)

            val caughtAtStr = obj.optString("caughtAt", "")
            val caughtAtLong = if (caughtAtStr.isNotEmpty()) {
                try { isoFormat.parse(caughtAtStr)?.time ?: 0L } catch (e: Exception) { 0L }
            } else {
                obj.optLong("caughtAt", 0L)
            }

            result.add(
                FishCatch(
                    id = 0,
                    species = obj.getString("species"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    caughtAt = caughtAtLong,
                    weight = obj.optLong("weight", 0),
                    length = obj.optLong("length", 0),
                    method = obj.optString("method", ""),
                    strikeDepth = obj.optDouble("strikeDepth", 0.0),
                    waterDepth = obj.optDouble("waterDepth", 0.0),
                    waterTemp = obj.optDouble("waterTemp", 0.0),
                    airTemp = obj.optDouble("airTemp", 0.0),
                    cloudiness = obj.optLong("cloudiness", 0),
                    rain = obj.optLong("rain", 0),
                    windSpeed = obj.optDouble("windSpeed", 0.0),
                    windDirection = obj.optLong("windDirection", 0),
                    additionalInfo = obj.optString("additionalInfo", ""),
                    originalRef = obj.optString("originalRef", ""),
                    tripNotes = obj.optString("tripNotes", "")
                )
            )
        }

        return result
    }
}