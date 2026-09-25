package fi.anssi.kalakartta.data

import fi.anssi.kalakartta.utils.MoonCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** FishCatch-mallin ja JSON-vientimuodon välinen muunnos. */
class FishCatchJsonMapper(
    private val isoFormatProvider: () -> SimpleDateFormat,
    private val moonCalculator: MoonCalculator = MoonCalculator()
) {

    fun toJson(catches: List<FishCatch>): JSONArray {
        val array = JSONArray()
        catches.forEach { fishCatch ->
            val obj = JSONObject()
            obj.put("id", fishCatch.id)
            obj.put("species", fishCatch.species)
            val eventTypeToExport = fishCatch.eventType
                ?: if (fishCatch.species != "UNKNOWN" && fishCatch.species.isNotEmpty()) {
                    FishCatch.CAUGHT_FISH
                } else {
                    null
                }
            if (eventTypeToExport != null) obj.put("eventType", eventTypeToExport)
            obj.put("latitude", roundCoordinate(fishCatch.latitude))
            obj.put("longitude", roundCoordinate(fishCatch.longitude))
            if (fishCatch.caughtAt != null && fishCatch.caughtAt > 0) {
                obj.put("caughtAt", isoFormatProvider().format(Date(fishCatch.caughtAt)))
            }
            if (fishCatch.weight != null) obj.put("weight", fishCatch.weight)
            if (fishCatch.length != null) obj.put("length", fishCatch.length)
            obj.put("method", fishCatch.method)
            if (fishCatch.lure != null) obj.put("lure", fishCatch.lure)
            if (fishCatch.lureColor != null) obj.put("lureColor", fishCatch.lureColor)
            if (fishCatch.strikeDepth != null) obj.put("strikeDepth", fishCatch.strikeDepth)
            if (fishCatch.waterDepth != null) obj.put("waterDepth", fishCatch.waterDepth)
            if (fishCatch.waterTemp != null) obj.put("waterTemp", fishCatch.waterTemp)
            if (fishCatch.airTemp != null) obj.put("airTemp", fishCatch.airTemp)
            if (fishCatch.cloudiness != null) obj.put("cloudiness", fishCatch.cloudiness)
            if (fishCatch.rain != null) obj.put("rain", fishCatch.rain)
            if (fishCatch.rainHourMm != null) obj.put("rainHourMm", fishCatch.rainHourMm)
            if (fishCatch.windSpeed != null) obj.put("windSpeed", fishCatch.windSpeed)
            if (fishCatch.windDirection != null) obj.put("windDirection", fishCatch.windDirection)
            if (fishCatch.pressure != null) obj.put("pressure", fishCatch.pressure)
            if (fishCatch.seaLevel != null) obj.put("seaLevel", fishCatch.seaLevel)
            obj.put("seaLevelSource", fishCatch.seaLevelSource)
            if (fishCatch.seaLevelTime != null && fishCatch.seaLevelTime > 0) {
                obj.put("seaLevelTime", isoFormatProvider().format(Date(fishCatch.seaLevelTime)))
            }
            obj.put("seaLevelStation", fishCatch.seaLevelStation)
            obj.put("weatherSource", fishCatch.weatherSource)
            if (fishCatch.weatherTime != null && fishCatch.weatherTime > 0) {
                obj.put("weatherTime", isoFormatProvider().format(Date(fishCatch.weatherTime)))
            }
            obj.put("weatherStation", fishCatch.weatherStation)
            obj.put("additionalInfo", fishCatch.additionalInfo)
            obj.put("originalRef", fishCatch.originalRef)
            if (fishCatch.fisherman.isNotBlank()) obj.put("fisherman", fishCatch.fisherman.uppercase())
            if (fishCatch.otherSpecies != null) obj.put("otherSpecies", fishCatch.otherSpecies.uppercase())
            if (fishCatch.weatherDataCompleteTime != null) {
                obj.put("weatherDataCompleteTime", fishCatch.weatherDataCompleteTime)
            }
            if (fishCatch.pressureTrend != null) {
                obj.put("pressureTrend", normalizePressure(fishCatch.pressureTrend))
            }
            if (fishCatch.pressureTurningTrend != null) {
                obj.put("pressureTurningTrend", normalizePressure(fishCatch.pressureTurningTrend))
            }
            if (fishCatch.pressureSamples.isNotEmpty()) {
                val samplesArray = JSONArray()
                fishCatch.pressureSamples.forEach { sample ->
                    val sampleObj = JSONObject()
                    sampleObj.put("time", isoFormatProvider().format(Date(sample.time)))
                    sampleObj.put("pressure", normalizePressure(sample.pressure))
                    samplesArray.put(sampleObj)
                }
                obj.put("pressureSamples", samplesArray)
            }
            if (fishCatch.seaLevelDataCompleteTime != null) {
                obj.put("seaLevelDataCompleteTime", fishCatch.seaLevelDataCompleteTime)
            }
            if (fishCatch.seaLevelTrend != null) {
                obj.put("seaLevelTrend", normalizeSeaLevel(fishCatch.seaLevelTrend))
            }
            if (fishCatch.seaLevelTurningTrend != null) {
                obj.put("seaLevelTurningTrend", normalizeSeaLevel(fishCatch.seaLevelTurningTrend))
            }
            if (fishCatch.seaLevelSamples.isNotEmpty()) {
                val samplesArray = JSONArray()
                fishCatch.seaLevelSamples.forEach { sample ->
                    val sampleObj = JSONObject()
                    sampleObj.put("time", isoFormatProvider().format(Date(sample.time)))
                    sampleObj.put("seaLevel", sample.seaLevel)
                    samplesArray.put(sampleObj)
                }
                obj.put("seaLevelSamples", samplesArray)
            }

            var moonPhase = fishCatch.moonPhase
            var moonAltitude = fishCatch.moonAltitude
            if (fishCatch.caughtAt != null && fishCatch.caughtAt > 0) {
                if (moonPhase == null) moonPhase = moonCalculator.getMoonPhase(fishCatch.caughtAt)
                if (moonAltitude == null) {
                    moonAltitude = moonCalculator.getMoonAltitude(
                        fishCatch.latitude,
                        fishCatch.longitude,
                        fishCatch.caughtAt
                    )
                }
            }
            if (moonPhase != null) {
                obj.put("moonPhase", String.format(Locale.US, "%.2f", moonPhase).toDouble())
            }
            if (moonAltitude != null) obj.put("moonAltitude", Math.round(moonAltitude).toInt())
            array.put(obj)
        }
        return array
    }

    fun fromJson(jsonArray: JSONArray): List<FishCatch> {
        val result = mutableListOf<FishCatch>()
        for (index in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(index)
                val caughtAt = parseOptionalDate(obj, "caughtAt")
                val weatherTime = parseOptionalDate(obj, "weatherTime")
                val seaLevelTime = parseOptionalDate(obj, "seaLevelTime")

                var moonPhase = if (obj.isNull("moonPhase")) null else obj.optDouble("moonPhase")
                var moonAltitude = if (obj.isNull("moonAltitude")) null else obj.optDouble("moonAltitude")
                if (caughtAt != null && caughtAt > 0) {
                    if (moonPhase == null) moonPhase = moonCalculator.getMoonPhase(caughtAt)
                    if (moonAltitude == null) {
                        moonAltitude = moonCalculator.getMoonAltitude(
                            coordinate(obj, "latitude", 60.0),
                            coordinate(obj, "longitude", 24.0),
                            caughtAt
                        )
                    }
                }
                if (moonPhase != null) {
                    moonPhase = String.format(Locale.US, "%.2f", moonPhase).toDouble()
                }
                if (moonAltitude != null) moonAltitude = Math.round(moonAltitude).toDouble()

                val species = obj.optString("species", "UNKNOWN")
                result += FishCatch(
                    id = 0,
                    species = species,
                    eventType = if (obj.isNull("eventType")) {
                        if (species != "UNKNOWN" && species != "") FishCatch.CAUGHT_FISH else null
                    } else {
                        obj.optString("eventType")
                    },
                    latitude = roundCoordinate(coordinate(obj, "latitude", 60.0)),
                    longitude = roundCoordinate(coordinate(obj, "longitude", 24.0)),
                    caughtAt = caughtAt,
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
                    pressure = if (obj.has("pressure") && !obj.isNull("pressure")) {
                        obj.optDouble("pressure")
                    } else if (obj.isNull("seaLevel")) {
                        null
                    } else {
                        obj.optDouble("seaLevel")
                    },
                    seaLevel = if (obj.isNull("seaLevel")) null else obj.optLong("seaLevel"),
                    seaLevelSource = obj.optString("seaLevelSource", ""),
                    seaLevelTime = seaLevelTime,
                    seaLevelStation = obj.optString("seaLevelStation", ""),
                    weatherSource = obj.optString("weatherSource", ""),
                    weatherTime = weatherTime,
                    weatherStation = obj.optString("weatherStation", ""),
                    additionalInfo = obj.optString("additionalInfo", ""),
                    originalRef = obj.optString("originalRef", ""),
                    fisherman = obj.optString("fisherman", ""),
                    otherSpecies = if (obj.isNull("otherSpecies")) null else obj.optString("otherSpecies", ""),
                    weatherDataCompleteTime = if (obj.isNull("weatherDataCompleteTime")) null else obj.optLong("weatherDataCompleteTime"),
                    pressureTrend = if (obj.isNull("pressureTrend")) null else normalizePressure(obj.optDouble("pressureTrend")),
                    pressureTurningTrend = if (obj.isNull("pressureTurningTrend")) null else normalizePressure(obj.optDouble("pressureTurningTrend")),
                    pressureSamples = parsePressureSamples(obj.optJSONArray("pressureSamples")),
                    seaLevelDataCompleteTime = if (obj.isNull("seaLevelDataCompleteTime")) null else obj.optLong("seaLevelDataCompleteTime"),
                    seaLevelTrend = if (obj.isNull("seaLevelTrend")) null else normalizeSeaLevel(obj.optDouble("seaLevelTrend")),
                    seaLevelTurningTrend = if (obj.isNull("seaLevelTurningTrend")) null else normalizeSeaLevel(obj.optDouble("seaLevelTurningTrend")),
                    seaLevelSamples = parseSeaLevelSamples(obj.optJSONArray("seaLevelSamples")),
                    moonPhase = moonPhase,
                    moonAltitude = moonAltitude
                )
            } catch (_: Exception) {
                // Yksittäinen viallinen saalis ei estä muun tiedoston tuontia.
            }
        }
        return result
    }

    private fun parsePressureSamples(jsonArray: JSONArray?): List<PressureSample> {
        if (jsonArray == null) return emptyList()
        val result = mutableListOf<PressureSample>()
        for (index in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(index)
            if (!obj.has("pressure") || obj.isNull("pressure")) continue
            val time = parseOptionalDate(obj, "time") ?: obj.optLong("time", 0L)
            val pressure = normalizePressure(obj.optDouble("pressure", 0.0))
            result += PressureSample(time, pressure)
        }
        return result
    }

    private fun parseSeaLevelSamples(jsonArray: JSONArray?): List<SeaLevelSample> {
        if (jsonArray == null) return emptyList()
        val result = mutableListOf<SeaLevelSample>()
        for (index in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(index)
            val time = parseOptionalDate(obj, "time") ?: obj.optLong("time", 0L)
            val pressure = if (obj.has("seaLevel")) obj.optLong("seaLevel") else obj.optLong("seaLevel", 0L)
            result += SeaLevelSample(time, pressure)
        }
        return result
    }

    private fun parseOptionalDate(obj: JSONObject, key: String): Long? {
        val value = obj.optString(key, "")
        if (value.isNotEmpty()) {
            return try {
                isoFormatProvider().parse(value)?.time
            } catch (_: Exception) {
                val legacyValue = if (obj.has(key) && !obj.isNull(key)) obj.optLong(key) else 0L
                if (legacyValue <= 0) null else legacyValue
            }
        }
        val legacyValue = if (obj.has(key) && !obj.isNull(key)) obj.optLong(key) else 0L
        return if (legacyValue <= 0) null else legacyValue
    }

    private fun coordinate(obj: JSONObject, key: String, defaultValue: Double): Double =
        if (obj.isNull(key) || !obj.has(key)) defaultValue else obj.optDouble(key, defaultValue)

    private fun roundCoordinate(value: Double): Double =
        String.format(Locale.US, "%.5f", value).toDouble()

    private fun normalizePressure(value: Double): Double =
        if (value.isFinite()) String.format(Locale.US, "%.5f", value).toDouble() else value

    private fun normalizeSeaLevel(value: Double): Double =
        if (value.isFinite()) String.format(Locale.US, "%.5f", value).toDouble() else value
}
