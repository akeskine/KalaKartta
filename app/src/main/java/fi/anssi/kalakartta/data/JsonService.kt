package fi.anssi.kalakartta.data

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import android.util.JsonReader
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

    fun writeRoutesToWriter(
        writer: android.util.JsonWriter,
        sessions: List<FishingSession>,
        onGetPoints: (Long) -> List<TrackPoint>
    ) {
        writer.beginObject()
        writer.name("sessions")
        writer.beginArray()
        
        sessions.forEach { session ->
            writer.beginObject()
            writer.name("startedAt").value(isoFormat.format(java.util.Date(session.startedAt)))
            if (session.endedAt != null) {
                writer.name("endedAt").value(isoFormat.format(java.util.Date(session.endedAt)))
            }
            writer.name("notes").value(session.notes)
            
            writer.name("points")
            writer.beginArray()
            val points = onGetPoints(session.id)
            points.forEach { pt ->
                writer.beginObject()
                writer.name("timestamp").value(isoFormat.format(java.util.Date(pt.timestamp)))
                writer.name("latitude").value(pt.latitude)
                writer.name("longitude").value(pt.longitude)
                writer.name("speed").value(pt.speed.toDouble())
                writer.name("accuracy").value(pt.accuracy.toDouble())
                writer.endObject()
            }
            writer.endArray()
            
            writer.endObject()
        }
        
        writer.endArray()
        writer.endObject()
    }

    fun exportRoutes(
        contentResolver: ContentResolver,
        uri: Uri,
        sessions: List<FishingSession>,
        onGetPoints: (Long) -> List<TrackPoint>
    ) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = android.util.JsonWriter(outputStream.bufferedWriter())
                writer.setIndent("    ")
                writeRoutesToWriter(writer, sessions, onGetPoints)
                writer.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("JsonService", "Error exporting routes", e)
        }
    }


    fun importRoutesFromStream(
        inputStream: java.io.InputStream,
        onSessionParsed: (FishingSession, List<TrackPoint>) -> Unit
    ) {
        val reader = inputStream.bufferedReader()
        val jsonReader = JsonReader(reader)
        try {
            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                val name = jsonReader.nextName()
                if (name == "sessions") {
                    jsonReader.beginArray()
                    while (jsonReader.hasNext()) {
                        parseSessionStream(jsonReader, onSessionParsed)
                    }
                    jsonReader.endArray()
                } else {
                    jsonReader.skipValue()
                }
            }
            jsonReader.endObject()
        } catch (e: Exception) {
            android.util.Log.e("JsonService", "Error parsing routes JSON stream", e)
            throw e
        }
        // Note: we don't close the reader here to avoid closing the underlying stream
        // which might be part of a larger ZipInputStream.
    }

    fun importRoutesStream(
        contentResolver: ContentResolver,
        uri: Uri,
        onSessionParsed: (FishingSession, List<TrackPoint>) -> Unit
    ) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            importRoutesFromStream(inputStream, onSessionParsed)
        }
    }

    private fun parseSessionStream(
        reader: JsonReader,
        onSessionParsed: (FishingSession, List<TrackPoint>) -> Unit
    ) {
        var startedAtMs = 0L
        var endedAtMs: Long? = null
        var notes = ""
        var pointsFound = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "startedAt" -> {
                    val startedAt = reader.nextString()
                    startedAtMs = try {
                        isoFormat.parse(startedAt)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }
                "endedAt" -> {
                    val endedAt = reader.nextString()
                    endedAtMs = if (endedAt.isNotEmpty()) {
                        try {
                            isoFormat.parse(endedAt)?.time
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
                "notes" -> notes = reader.nextString()
                "points" -> {
                    pointsFound = true
                    val session = FishingSession(startedAt = startedAtMs, endedAt = endedAtMs, notes = notes)
                    val pointsBatch = mutableListOf<TrackPoint>()
                    
                    reader.beginArray()
                    while (reader.hasNext()) {
                        val pt = parseTrackPointStream(reader)
                        pointsBatch.add(pt)
                        
                        if (pointsBatch.size >= 1000) {
                            onSessionParsed(session, pointsBatch.toList())
                            pointsBatch.clear()
                        }
                    }
                    reader.endArray()
                    
                    if (pointsBatch.isNotEmpty() || !pointsFound) {
                        onSessionParsed(session, pointsBatch)
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        
        if (!pointsFound) {
            val session = FishingSession(startedAt = startedAtMs, endedAt = endedAtMs, notes = notes)
            onSessionParsed(session, emptyList())
        }
    }

    private fun parseTrackPointStream(reader: JsonReader): TrackPoint {
        var timestampMs = 0L
        var latitude = 0.0
        var longitude = 0.0
        var speed = 0.0f
        var accuracy = 0.0f

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "timestamp" -> {
                    val ts = reader.nextString()
                    timestampMs = try {
                        isoFormat.parse(ts)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }
                "latitude" -> latitude = reader.nextDouble()
                "longitude" -> longitude = reader.nextDouble()
                "speed" -> speed = reader.nextDouble().toFloat()
                "accuracy" -> accuracy = reader.nextDouble().toFloat()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return TrackPoint(
            fishingSessionId = 0,
            timestamp = timestampMs,
            latitude = latitude,
            longitude = longitude,
            speed = speed,
            accuracy = accuracy
        )
    }

    fun importRoutes(contentResolver: ContentResolver, uri: Uri): List<Pair<FishingSession, List<TrackPoint>>> {
        val results = mutableListOf<Pair<FishingSession, List<TrackPoint>>>()
        try {
            importRoutesStream(contentResolver, uri) { session, points ->
                val existingIndex = results.indexOfFirst { it.first.startedAt == session.startedAt }
                if (existingIndex != -1) {
                    val pair = results[existingIndex]
                    val updatedPoints = pair.second + points
                    results[existingIndex] = Pair(pair.first, updatedPoints)
                } else {
                    results.add(Pair(session, points))
                }
            }
        } catch (e: Exception) {
            // Keep existing behavior of logging via importRoutesStream and returning what we have
        }
        return results
    }

    fun exportCatchesAndPlaces(catches: List<FishCatch>, places: List<PlaceOfInterest>): JSONObject {
        val root = JSONObject()
        root.put("catches", catchesToJson(catches))
        root.put("places", placesToJson(places))
        return root
    }

    fun export(contentResolver: ContentResolver, uri: Uri, catches: List<FishCatch>, places: List<PlaceOfInterest>) {
        val root = exportCatchesAndPlaces(catches, places)

        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(4).toByteArray())
        }
    }

    fun exportSpeciesToJsonObject(speciesList: List<FishSpecies>, filesDir: File): JSONObject {
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
        return root
    }

    fun exportSpecies(contentResolver: ContentResolver, uri: Uri, speciesList: List<FishSpecies>, filesDir: File) {
        val root = exportSpeciesToJsonObject(speciesList, filesDir)

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
            if (it.lure != null) obj.put("lure", it.lure)
            if (it.lureColor != null) obj.put("lureColor", it.lureColor)
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

    fun parseCatchesAndPlaces(text: String): Pair<List<FishCatch>, List<PlaceOfInterest>> {
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

    fun import(contentResolver: ContentResolver, uri: Uri): Pair<List<FishCatch>, List<PlaceOfInterest>> {
        val text = contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return Pair(emptyList(), emptyList())

        return parseCatchesAndPlaces(text)
    }

    fun parseSpecies(text: String, filesDir: File): List<FishSpecies> {
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

    fun importSpecies(contentResolver: ContentResolver, uri: Uri, filesDir: File): List<FishSpecies> {
        val text = contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return emptyList()

        return parseSpecies(text, filesDir)
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
                    lure = if (obj.isNull("lure")) null else obj.optString("lure"),
                    lureColor = if (obj.isNull("lureColor")) null else obj.optString("lureColor"),
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