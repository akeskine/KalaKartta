package fi.anssi.kalakartta.data

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class JsonService {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun exportRoutes(contentResolver: ContentResolver, uri: Uri, sessions: List<FishingSession>, pointsMap: Map<Long, List<TrackPoint>>) {
        try {
            val root = JSONObject()
            root.put("sessions", sessionsToJson(sessions, pointsMap))
            
            contentResolver.openOutputStream(uri)?.use { 
                it.write(root.toString(4).toByteArray())
            }
        } catch (e: Exception) {
            android.util.Log.e("JsonService", "Error exporting routes", e)
        }
    }

    private fun sessionsToJson(sessions: List<FishingSession>, pointsMap: Map<Long, List<TrackPoint>>): JSONArray {
        val array = JSONArray()
        sessions.forEach { session ->
            val obj = JSONObject()
            obj.put("startedAt", isoFormat.format(Date(session.startedAt)))
            if (session.endedAt != null) {
                obj.put("endedAt", isoFormat.format(Date(session.endedAt)))
            }
            obj.put("notes", session.notes)
            
            val pointsArray = JSONArray()
            pointsMap[session.id]?.forEach { pt ->
                val pObj = JSONObject()
                pObj.put("timestamp", isoFormat.format(Date(pt.timestamp)))
                pObj.put("latitude", String.format(Locale.US, "%.6f", pt.latitude).toDouble())
                pObj.put("longitude", String.format(Locale.US, "%.6f", pt.longitude).toDouble())
                pObj.put("speed", pt.speed)
                pObj.put("accuracy", pt.accuracy)
                pointsArray.put(pObj)
            }
            obj.put("points", pointsArray)
            array.put(obj)
        }
        return array
    }

    fun importRoutes(contentResolver: ContentResolver, uri: Uri): List<Pair<FishingSession, List<TrackPoint>>> {
        val text = contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return emptyList()

        val results = mutableListOf<Pair<FishingSession, List<TrackPoint>>>()

        try {
            val root = JSONObject(text)
            val sessionsArray = root.optJSONArray("sessions") ?: return emptyList()

            for (i in 0 until sessionsArray.length()) {
                val sObj = sessionsArray.getJSONObject(i)
                
                val startedAt = sObj.optString("startedAt", "")
                val startedAtMs = try { isoFormat.parse(startedAt)?.time ?: 0L } catch(e: Exception) { 0L }
                
                val endedAt = sObj.optString("endedAt", "")
                val endedAtMs = if (endedAt.isNotEmpty()) {
                    try { isoFormat.parse(endedAt)?.time } catch(e: Exception) { null }
                } else null
                
                val notes = sObj.optString("notes", "")
                
                val session = FishingSession(startedAt = startedAtMs, endedAt = endedAtMs, notes = notes)
                
                val points = mutableListOf<TrackPoint>()
                val pointsArray = sObj.optJSONArray("points")
                if (pointsArray != null) {
                    for (j in 0 until pointsArray.length()) {
                        val pObj = pointsArray.getJSONObject(j)
                        val ts = pObj.optString("timestamp", "")
                        val tsMs = try { isoFormat.parse(ts)?.time ?: 0L } catch(e: Exception) { 0L }
                        
                        points.add(TrackPoint(
                            fishingSessionId = 0, // Id assigned later when inserting session
                            timestamp = tsMs,
                            latitude = pObj.optDouble("latitude", 0.0),
                            longitude = pObj.optDouble("longitude", 0.0),
                            speed = pObj.optDouble("speed", 0.0).toFloat(),
                            accuracy = pObj.optDouble("accuracy", 0.0).toFloat()
                        ))
                    }
                }
                results.add(Pair(session, points))
            }
        } catch (e: Exception) {
            android.util.Log.e("JsonService", "Error parsing routes JSON", e)
        }

        return results
    }

    fun export(contentResolver: ContentResolver, uri: Uri, catches: List<FishCatch>, places: List<PlaceOfInterest>) {
        val root = JSONObject()
        root.put("catches", catchesToJson(catches))
        root.put("places", placesToJson(places))

        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(4).toByteArray())
        }
    }

    fun exportSpecies(contentResolver: ContentResolver, uri: Uri, speciesList: List<FishSpecies>, filesDir: File) {
        val root = JSONObject()
        val speciesArray = JSONArray()

        speciesList.forEach { species ->
            val obj = JSONObject()
            obj.put("id", species.id)
            obj.put("name", species.name)
            obj.put("small_weight", species.small_weight)
            obj.put("small_length", species.small_length)
            obj.put("large_weight", species.large_weight)
            obj.put("large_length", species.large_length)
            obj.put("giant_weight", species.giant_weight)
            obj.put("giant_length", species.giant_length)
            obj.put("favourite_fish", species.favourite_fish)
            obj.put("sortOrder", species.sortOrder)

            // Encode icons to Base64 if they are custom files
            // We store only the filename in the JSON to keep it portable
            val defaultIconName = getIconFileName(species.icon_default)
            obj.put("icon_default", defaultIconName)
            if (isCustomIcon(species.icon_default)) {
                obj.put("icon_default_data", encodeFileToBase64(getIconFile(filesDir, species.icon_default)))
            }

            val smallIconName = getIconFileName(species.icon_small)
            obj.put("icon_small", smallIconName)
            if (isCustomIcon(species.icon_small)) {
                obj.put("icon_small_data", encodeFileToBase64(getIconFile(filesDir, species.icon_small)))
            }

            val largeIconName = getIconFileName(species.icon_large)
            obj.put("icon_large", largeIconName)
            if (isCustomIcon(species.icon_large)) {
                obj.put("icon_large_data", encodeFileToBase64(getIconFile(filesDir, species.icon_large)))
            }

            val giantIconName = getIconFileName(species.icon_giant)
            obj.put("icon_giant", giantIconName)
            if (isCustomIcon(species.icon_giant)) {
                obj.put("icon_giant_data", encodeFileToBase64(getIconFile(filesDir, species.icon_giant)))
            }

            speciesArray.put(obj)
        }
        root.put("species", speciesArray)

        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(4).toByteArray())
        }
    }

    private fun getIconFileName(path: String): String {
        if (!isCustomIcon(path)) return path
        return try {
            File(path).name
        } catch (e: Exception) {
            path
        }
    }

    private fun getIconFile(filesDir: File, path: String): File {
        return if (path.startsWith("/")) {
            File(path)
        } else {
            File(filesDir, path)
        }
    }

    private fun isCustomIcon(iconName: String): Boolean {
        return iconName.isNotEmpty() && (iconName.contains("/") || iconName.startsWith("custom_icon_"))
    }

    private fun encodeFileToBase64(file: File): String? {
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBase64ToFile(base64Data: String, file: File) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            FileOutputStream(file).use { it.write(bytes) }
        } catch (e: Exception) {
            android.util.Log.e("JsonService", "Error decoding Base64 to file", e)
        }
    }

    private fun catchesToJson(catches: List<FishCatch>): JSONArray {
        val array = JSONArray()
        catches.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("species", it.species)
            val eventTypeToExport = it.eventType ?: if (it.species != "UNKNOWN" && it.species.isNotEmpty()) FishCatch.CAUGHT_FISH else null
            if (eventTypeToExport != null) obj.put("eventType", eventTypeToExport)
            obj.put("latitude", String.format(Locale.US, "%.5f", it.latitude).toDouble())
            obj.put("longitude", String.format(Locale.US, "%.5f", it.longitude).toDouble())
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
            if (it.fisherman.isNotBlank()) obj.put("fisherman", it.fisherman.uppercase())
            if (it.otherSpecies != null) obj.put("otherSpecies", it.otherSpecies.uppercase())
            if (it.weatherDataCompleteTime != null) obj.put("weatherDataCompleteTime", it.weatherDataCompleteTime)
            array.put(obj)
        }
        return array
    }

    private fun placesToJson(places: List<PlaceOfInterest>): JSONArray {
        val array = JSONArray()
        places.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("typeId", it.typeId)
            obj.put("latitude", String.format(Locale.US, "%.5f", it.latitude).toDouble())
            obj.put("longitude", String.format(Locale.US, "%.5f", it.longitude).toDouble())
            obj.put("name", it.name)
            obj.put("additionalInfo", it.additionalInfo)
            obj.put("originalRef", it.originalRef)
            array.put(obj)
        }
        return array
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

    fun importSpecies(contentResolver: ContentResolver, uri: Uri, filesDir: File): List<FishSpecies> {
        val text = contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return emptyList()

        val speciesList = mutableListOf<FishSpecies>()

        try {
            val root = JSONObject(text)
            val speciesArray = root.optJSONArray("species") ?: return emptyList()

            for (i in 0 until speciesArray.length()) {
                val obj = speciesArray.getJSONObject(i)
                val id = obj.getString("id")
                
                // Handle icons and Base64 data
                val iconDefault = obj.optString("icon_default", "")
                val iconDefaultData = obj.optString("icon_default_data", "")
                var finalIconDefault = iconDefault
                if (iconDefaultData.isNotEmpty() && isCustomIcon(iconDefault)) {
                    val file = File(filesDir, getIconFileName(iconDefault))
                    decodeBase64ToFile(iconDefaultData, file)
                    finalIconDefault = file.absolutePath
                }

                val iconSmall = obj.optString("icon_small", "")
                val iconSmallData = obj.optString("icon_small_data", "")
                var finalIconSmall = iconSmall
                if (iconSmallData.isNotEmpty() && isCustomIcon(iconSmall)) {
                    val file = File(filesDir, getIconFileName(iconSmall))
                    decodeBase64ToFile(iconSmallData, file)
                    finalIconSmall = file.absolutePath
                }

                val iconLarge = obj.optString("icon_large", "")
                val iconLargeData = obj.optString("icon_large_data", "")
                var finalIconLarge = iconLarge
                if (iconLargeData.isNotEmpty() && isCustomIcon(iconLarge)) {
                    val file = File(filesDir, getIconFileName(iconLarge))
                    decodeBase64ToFile(iconLargeData, file)
                    finalIconLarge = file.absolutePath
                }

                val iconGiant = obj.optString("icon_giant", "")
                val iconGiantData = obj.optString("icon_giant_data", "")
                var finalIconGiant = iconGiant
                if (iconGiantData.isNotEmpty() && isCustomIcon(iconGiant)) {
                    val file = File(filesDir, getIconFileName(iconGiant))
                    decodeBase64ToFile(iconGiantData, file)
                    finalIconGiant = file.absolutePath
                }

                val species = FishSpecies(
                    id = id,
                    name = obj.optString("name", ""),
                    small_weight = obj.optLong("small_weight", 0),
                    small_length = obj.optLong("small_length", 0),
                    large_weight = obj.optLong("large_weight", 0),
                    large_length = obj.optLong("large_length", 0),
                    giant_weight = obj.optLong("giant_weight", 0),
                    giant_length = obj.optLong("giant_length", 0),
                    icon_small = finalIconSmall,
                    icon_default = finalIconDefault,
                    icon_large = finalIconLarge,
                    icon_giant = finalIconGiant,
                    favourite_fish = obj.optBoolean("favourite_fish", true),
                    sortOrder = obj.optInt("sortOrder", 0)
                )
                speciesList.add(species)
            }
        } catch (e: Exception) {
            android.util.Log.e("JsonService", "Error parsing species JSON", e)
        }
        return speciesList
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
                    otherSpecies = if (obj.isNull("otherSpecies")) null else obj.optString("otherSpecies", ""),
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