package fi.anssi.kalakartta.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.data.TrackPoint
import java.text.SimpleDateFormat
import java.util.*

class FilterManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("filters", Context.MODE_PRIVATE)

    data class Filters(
        val startDate: Long? = null,
        val endDate: Long? = null,
        val annualStartDay: Int? = null,
        val annualStartMonth: Int? = null, // 0-11
        val annualEndDay: Int? = null,
        val annualEndMonth: Int? = null,
        val startTimeMinutes: Int? = null, // Minutes from midnight
        val endTimeMinutes: Int? = null,
        val annualStartTimeMinutes: Int? = null,
        val annualEndTimeMinutes: Int? = null,
        val windMin: Float? = null,
        val windMax: Float? = null,
        val pressureMin: Float? = null,
        val pressureMax: Float? = null,
        val pressureTrendDirection: String? = null,
        val pressureTurningTrendDirection: String? = null,
        val waterTempMin: Float? = null,
        val waterTempMax: Float? = null,
        val moonPhaseMin: Float? = null,
        val moonPhaseMax: Float? = null,
        val moonAltitudeMin: Float? = null,
        val moonAltitudeMax: Float? = null,
        val speciesId: String? = null,
        val otherSpecies: String? = null,
        val placeTypeId: String? = null,
        val freeText: String? = null,
        val fisherman: String? = null,
        val onlyCaughtFish: Boolean = false,
        val onlyFishPoints: Boolean = false,
        val onlyNonFishPoints: Boolean = false,
        val latNorth: Double? = null,
        val latSouth: Double? = null,
        val lonEast: Double? = null,
        val lonWest: Double? = null,
        val weightMin: Long? = null,
        val weightMax: Long? = null,
        val lengthMin: Long? = null,
        val lengthMax: Long? = null,
        val weightLengthOperator: String = "OR" // "AND" tai "OR"
    )

    companion object {
        const val PRESSURE_TREND_FALLING = "FALLING"
        const val PRESSURE_TREND_FLAT = "FLAT"
        const val PRESSURE_TREND_RISING = "RISING"

        const val PRESSURE_TURNING_TREND_FALLING = "TURNING_FALLING"
        const val PRESSURE_TURNING_TREND_FLAT = "TURNING_FLAT"
        const val PRESSURE_TURNING_TREND_RISING = "TURNING_RISING"

        const val PRESSURE_TREND_THRESHOLD_KEY = "pressure_trend_threshold"
        const val PRESSURE_TURNING_TREND_THRESHOLD_KEY = "pressure_turning_trend_threshold"
        const val DEFAULT_PRESSURE_TREND_THRESHOLD = 0.10f
        const val DEFAULT_PRESSURE_TURNING_TREND_THRESHOLD = 0.20f

        fun isValueInRange(value: Double, min: Double?, max: Double?, wraps: Boolean): Boolean {
            if (min != null && max != null && wraps && min > max) {
                return value >= min || value <= max
            }
            if (min != null && value < min) return false
            if (max != null && value > max) return false
            return true
        }

        fun matchesPressureTrend(value: Double?, direction: String?, threshold: Double): Boolean {
            if (direction == null) return true
            if (value == null || !threshold.isFinite() || threshold <= 0.0) return false

            return when (direction) {
                PRESSURE_TREND_FALLING,
                PRESSURE_TURNING_TREND_FALLING -> value < -threshold
                PRESSURE_TREND_FLAT,
                PRESSURE_TURNING_TREND_FLAT -> value >= -threshold && value <= threshold
                PRESSURE_TREND_RISING,
                PRESSURE_TURNING_TREND_RISING -> value > threshold
                else -> true
            }
        }
    }

    fun getFilters(): Filters {
        val startDate = if (prefs.contains("startDate")) prefs.getLong("startDate", 0) else null
        val endDate = if (prefs.contains("endDate")) prefs.getLong("endDate", 0) else null
        val annualStartDay = if (prefs.contains("annualStartDay")) prefs.getInt("annualStartDay", 0) else null
        val annualStartMonth = if (prefs.contains("annualStartMonth")) prefs.getInt("annualStartMonth", 0) else null
        val annualEndDay = if (prefs.contains("annualEndDay")) prefs.getInt("annualEndDay", 0) else null
        val annualEndMonth = if (prefs.contains("annualEndMonth")) prefs.getInt("annualEndMonth", 0) else null
        val startTimeMinutes = if (prefs.contains("startTimeMinutes")) prefs.getInt("startTimeMinutes", 0) else null
        val endTimeMinutes = if (prefs.contains("endTimeMinutes")) prefs.getInt("endTimeMinutes", 0) else null
        val annualStartTimeMinutes = if (prefs.contains("annualStartTimeMinutes")) prefs.getInt("annualStartTimeMinutes", 0) else null
        val annualEndTimeMinutes = if (prefs.contains("annualEndTimeMinutes")) prefs.getInt("annualEndTimeMinutes", 0) else null
        val windMin = if (prefs.contains("windMin")) prefs.getFloat("windMin", 0f) else null
        val windMax = if (prefs.contains("windMax")) prefs.getFloat("windMax", 0f) else null
        val pressureMin = if (prefs.contains("pressureMin")) prefs.getFloat("pressureMin", 0f) else null
        val pressureMax = if (prefs.contains("pressureMax")) prefs.getFloat("pressureMax", 0f) else null
        val pressureTrendDirection = prefs.getString("pressureTrendDirection", null)
        val pressureTurningTrendDirection = prefs.getString("pressureTurningTrendDirection", null)
        val waterTempMin = if (prefs.contains("waterTempMin")) prefs.getFloat("waterTempMin", 0f) else null
        val waterTempMax = if (prefs.contains("waterTempMax")) prefs.getFloat("waterTempMax", 0f) else null
        val moonPhaseMin = if (prefs.contains("moonPhaseMin")) prefs.getFloat("moonPhaseMin", 0f) else null
        val moonPhaseMax = if (prefs.contains("moonPhaseMax")) prefs.getFloat("moonPhaseMax", 0f) else null
        val moonAltitudeMin = if (prefs.contains("moonAltitudeMin")) prefs.getFloat("moonAltitudeMin", 0f) else null
        val moonAltitudeMax = if (prefs.contains("moonAltitudeMax")) prefs.getFloat("moonAltitudeMax", 0f) else null
        val speciesId = prefs.getString("speciesId", null)
        val otherSpecies = prefs.getString("otherSpecies", null)
        val placeTypeId = prefs.getString("placeTypeId", null)
        val freeText = prefs.getString("freeText", null)
        val fisherman = prefs.getString("fisherman", null)
        val onlyCaughtFish = prefs.getBoolean("onlyCaughtFish", false)
        val onlyFishPoints = prefs.getBoolean("onlyFishPoints", false)
        val onlyNonFishPoints = prefs.getBoolean("onlyNonFishPoints", false)
        val latNorth = if (prefs.contains("latNorth")) prefs.getFloat("latNorth", 0f).toDouble() else null
        val latSouth = if (prefs.contains("latSouth")) prefs.getFloat("latSouth", 0f).toDouble() else null
        val lonEast = if (prefs.contains("lonEast")) prefs.getFloat("lonEast", 0f).toDouble() else null
        val lonWest = if (prefs.contains("lonWest")) prefs.getFloat("lonWest", 0f).toDouble() else null
        val weightMin = if (prefs.contains("weightMin")) prefs.getLong("weightMin", 0) else null
        val weightMax = if (prefs.contains("weightMax")) prefs.getLong("weightMax", 0) else null
        val lengthMin = if (prefs.contains("lengthMin")) prefs.getLong("lengthMin", 0) else null
        val lengthMax = if (prefs.contains("lengthMax")) prefs.getLong("lengthMax", 0) else null
        val weightLengthOperator = prefs.getString("weightLengthOperator", "OR") ?: "OR"

        return Filters(
            startDate, endDate,
            annualStartDay, annualStartMonth,
            annualEndDay, annualEndMonth,
            startTimeMinutes, endTimeMinutes,
            annualStartTimeMinutes, annualEndTimeMinutes,
            windMin, windMax,
            pressureMin, pressureMax,
            pressureTrendDirection, pressureTurningTrendDirection,
            waterTempMin, waterTempMax,
            moonPhaseMin, moonPhaseMax,
            moonAltitudeMin, moonAltitudeMax,
            speciesId, otherSpecies, placeTypeId, freeText, fisherman, onlyCaughtFish, onlyFishPoints, onlyNonFishPoints,
            latNorth, latSouth, lonEast, lonWest,
            weightMin, weightMax, lengthMin, lengthMax, weightLengthOperator
        )
    }

    fun clearFilters() {
        prefs.edit().clear().apply()
    }

    fun saveFilters(filters: Filters) {
        prefs.edit().apply {
            if (filters.startDate != null) putLong("startDate", filters.startDate) else remove("startDate")
            if (filters.endDate != null) putLong("endDate", filters.endDate) else remove("endDate")
            if (filters.annualStartDay != null) putInt("annualStartDay", filters.annualStartDay) else remove("annualStartDay")
            if (filters.annualStartMonth != null) putInt("annualStartMonth", filters.annualStartMonth) else remove("annualStartMonth")
            if (filters.annualEndDay != null) putInt("annualEndDay", filters.annualEndDay) else remove("annualEndDay")
            if (filters.annualEndMonth != null) putInt("annualEndMonth", filters.annualEndMonth) else remove("annualEndMonth")
            if (filters.startTimeMinutes != null) putInt("startTimeMinutes", filters.startTimeMinutes) else remove("startTimeMinutes")
            if (filters.endTimeMinutes != null) putInt("endTimeMinutes", filters.endTimeMinutes) else remove("endTimeMinutes")
            if (filters.annualStartTimeMinutes != null) putInt("annualStartTimeMinutes", filters.annualStartTimeMinutes) else remove("annualStartTimeMinutes")
            if (filters.annualEndTimeMinutes != null) putInt("annualEndTimeMinutes", filters.annualEndTimeMinutes) else remove("annualEndTimeMinutes")
            if (filters.windMin != null) putFloat("windMin", filters.windMin) else remove("windMin")
            if (filters.windMax != null) putFloat("windMax", filters.windMax) else remove("windMax")
            if (filters.pressureMin != null) putFloat("pressureMin", filters.pressureMin) else remove("pressureMin")
            if (filters.pressureMax != null) putFloat("pressureMax", filters.pressureMax) else remove("pressureMax")
            if (filters.pressureTrendDirection != null) putString("pressureTrendDirection", filters.pressureTrendDirection) else remove("pressureTrendDirection")
            if (filters.pressureTurningTrendDirection != null) putString("pressureTurningTrendDirection", filters.pressureTurningTrendDirection) else remove("pressureTurningTrendDirection")
            if (filters.waterTempMin != null) putFloat("waterTempMin", filters.waterTempMin) else remove("waterTempMin")
            if (filters.waterTempMax != null) putFloat("waterTempMax", filters.waterTempMax) else remove("waterTempMax")
            if (filters.moonPhaseMin != null) putFloat("moonPhaseMin", filters.moonPhaseMin) else remove("moonPhaseMin")
            if (filters.moonPhaseMax != null) putFloat("moonPhaseMax", filters.moonPhaseMax) else remove("moonPhaseMax")
            if (filters.moonAltitudeMin != null) putFloat("moonAltitudeMin", filters.moonAltitudeMin) else remove("moonAltitudeMin")
            if (filters.moonAltitudeMax != null) putFloat("moonAltitudeMax", filters.moonAltitudeMax) else remove("moonAltitudeMax")
            if (filters.speciesId != null) putString("speciesId", filters.speciesId) else remove("speciesId")
            if (filters.otherSpecies != null) putString("otherSpecies", filters.otherSpecies) else remove("otherSpecies")
            if (filters.placeTypeId != null) putString("placeTypeId", filters.placeTypeId) else remove("placeTypeId")
            if (filters.freeText != null) putString("freeText", filters.freeText) else remove("freeText")
            if (filters.fisherman != null) putString("fisherman", filters.fisherman) else remove("fisherman")
            putBoolean("onlyCaughtFish", filters.onlyCaughtFish)
            putBoolean("onlyFishPoints", filters.onlyFishPoints)
            putBoolean("onlyNonFishPoints", filters.onlyNonFishPoints)
            if (filters.latNorth != null) putFloat("latNorth", filters.latNorth.toFloat()) else remove("latNorth")
            if (filters.latSouth != null) putFloat("latSouth", filters.latSouth.toFloat()) else remove("latSouth")
            if (filters.lonEast != null) putFloat("lonEast", filters.lonEast.toFloat()) else remove("lonEast")
            if (filters.lonWest != null) putFloat("lonWest", filters.lonWest.toFloat()) else remove("lonWest")
            if (filters.weightMin != null) putLong("weightMin", filters.weightMin) else remove("weightMin")
            if (filters.weightMax != null) putLong("weightMax", filters.weightMax) else remove("weightMax")
            if (filters.lengthMin != null) putLong("lengthMin", filters.lengthMin) else remove("lengthMin")
            if (filters.lengthMax != null) putLong("lengthMax", filters.lengthMax) else remove("lengthMax")
            putString("weightLengthOperator", filters.weightLengthOperator)
            apply()
        }
    }

    fun hasActiveFilters(): Boolean {
        val f = getFilters()
        return f.startDate != null || f.endDate != null ||
                f.annualStartDay != null || f.annualStartMonth != null ||
                f.annualEndDay != null || f.annualEndMonth != null ||
                f.startTimeMinutes != null || f.endTimeMinutes != null ||
                f.annualStartTimeMinutes != null || f.annualEndTimeMinutes != null ||
                f.windMin != null || f.windMax != null ||
                f.pressureMin != null || f.pressureMax != null ||
                f.pressureTrendDirection != null || f.pressureTurningTrendDirection != null ||
                f.waterTempMin != null || f.waterTempMax != null ||
                f.moonPhaseMin != null || f.moonPhaseMax != null ||
                f.moonAltitudeMin != null || f.moonAltitudeMax != null ||
                f.speciesId != null || f.placeTypeId != null || f.freeText != null || f.fisherman != null || 
                f.onlyCaughtFish || f.onlyFishPoints || f.onlyNonFishPoints ||
                f.latNorth != null
    }

    fun applyFilter(catches: List<FishCatch>): List<FishCatch> {
        if (!hasActiveFilters()) return catches
        val f = getFilters()
        val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val pressureTrendThreshold = settingsPrefs.getFloat(
            PRESSURE_TREND_THRESHOLD_KEY,
            DEFAULT_PRESSURE_TREND_THRESHOLD
        ).toDouble()
        val pressureTurningTrendThreshold = settingsPrefs.getFloat(
            PRESSURE_TURNING_TREND_THRESHOLD_KEY,
            DEFAULT_PRESSURE_TURNING_TREND_THRESHOLD
        ).toDouble()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))

        return catches.filter { fish ->
            val caughtAt = fish.caughtAt ?: 0L
            calendar.timeInMillis = caughtAt
            
            // Date Range
            if (f.startDate != null || f.endDate != null) {
                if (caughtAt == 0L) return@filter false
                if (f.startDate != null && caughtAt < f.startDate) return@filter false
                if (f.endDate != null && caughtAt > f.endDate) return@filter false
            }

            // Annual Date Range
            if (f.annualStartDay != null && f.annualStartMonth != null && 
                f.annualEndDay != null && f.annualEndMonth != null) {
                if (caughtAt == 0L) return@filter false
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                
                val currentVal = month * 100 + day
                val startVal = f.annualStartMonth * 100 + f.annualStartDay
                val endVal = f.annualEndMonth * 100 + f.annualEndDay
                
                if (startVal <= endVal) {
                    if (currentVal < startVal || currentVal > endVal) return@filter false
                } else {
                    // Spans across year end
                    if (currentVal < startVal && currentVal > endVal) return@filter false
                }
            }

            // Annual Time Range
            if (f.annualStartTimeMinutes != null && f.annualEndTimeMinutes != null) {
                if (caughtAt == 0L) return@filter false
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val currentMinutes = hour * 60 + minute
                
                if (f.annualStartTimeMinutes <= f.annualEndTimeMinutes) {
                    if (currentMinutes < f.annualStartTimeMinutes || currentMinutes > f.annualEndTimeMinutes) return@filter false
                } else {
                    // Spans across midnight
                    if (currentMinutes < f.annualStartTimeMinutes && currentMinutes > f.annualEndTimeMinutes) return@filter false
                }
            }

            // Time Range
            if (f.startTimeMinutes != null && f.endTimeMinutes != null) {
                if (caughtAt == 0L) return@filter false
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val currentMinutes = hour * 60 + minute
                
                if (f.startTimeMinutes <= f.endTimeMinutes) {
                    if (currentMinutes < f.startTimeMinutes || currentMinutes > f.endTimeMinutes) return@filter false
                } else {
                    // Spans across midnight
                    if (currentMinutes < f.startTimeMinutes && currentMinutes > f.endTimeMinutes) return@filter false
                }
            }

            // Wind Direction Range
            if (f.windMin != null && f.windMax != null) {
                val windDir = fish.windDirection
                if (windDir == null) return@filter false // Ei tuulitietoa -> suodatetaan pois jos rajattu
                
                if (f.windMin <= f.windMax) {
                    if (windDir < f.windMin || windDir > f.windMax) return@filter false
                } else {
                    // Sektori ylittää 360/0 rajan
                    if (windDir < f.windMin && windDir > f.windMax) return@filter false
                }
            }

            // Pressure Range
            if (f.pressureMin != null && fish.pressure != null && fish.pressure < f.pressureMin) return@filter false
            if (f.pressureMax != null && fish.pressure != null && fish.pressure > f.pressureMax) return@filter false
            if ((f.pressureMin != null || f.pressureMax != null) && fish.pressure == null) return@filter false

            if (!matchesPressureTrend(fish.pressureTrend, f.pressureTrendDirection, pressureTrendThreshold)) return@filter false
            if (!matchesPressureTrend(fish.pressureTurningTrend, f.pressureTurningTrendDirection, pressureTurningTrendThreshold)) return@filter false

            // Water Temp Range
            if (f.waterTempMin != null && fish.waterTemp != null && fish.waterTemp < f.waterTempMin) return@filter false
            if (f.waterTempMax != null && fish.waterTemp != null && fish.waterTemp > f.waterTempMax) return@filter false
            if ((f.waterTempMin != null || f.waterTempMax != null) && fish.waterTemp == null) return@filter false

            // Moon Phase Range
            if (f.moonPhaseMin != null || f.moonPhaseMax != null) {
                val moonPhase = fish.moonPhase ?: return@filter false
                if (!isValueInRange(moonPhase, f.moonPhaseMin?.toDouble(), f.moonPhaseMax?.toDouble(), wraps = true)) {
                    return@filter false
                }
            }

            // Moon Altitude Range
            if (f.moonAltitudeMin != null || f.moonAltitudeMax != null) {
                val moonAltitude = fish.moonAltitude ?: return@filter false
                if (!isValueInRange(moonAltitude, f.moonAltitudeMin?.toDouble(), f.moonAltitudeMax?.toDouble(), wraps = false)) {
                    return@filter false
                }
            }

            // Species
            if (f.speciesId != null && fish.species != f.speciesId) return@filter false
            if (f.speciesId == "OTHER" && f.otherSpecies != null) {
                if (fish.otherSpecies == null || !fish.otherSpecies.equals(f.otherSpecies, ignoreCase = true)) return@filter false
            }

            // Kalastaja
            if (f.fisherman != null) {
                if (!fish.fisherman.equals(f.fisherman, ignoreCase = true)) return@filter false
            }

            // Free Text
            if (f.freeText != null) {
                val searchText = f.freeText.lowercase()
                val match = fish.additionalInfo.lowercase().contains(searchText) || 
                          fish.originalRef.lowercase().contains(searchText)
                if (!match) return@filter false
            }

            // Only Caught Fish
            if (f.onlyCaughtFish && fish.eventType != FishCatch.CAUGHT_FISH) return@filter false

            // Only Non-Fish Points
            if (f.onlyNonFishPoints) return@filter false

            // Area selection (Bounding Box)
            if (f.latNorth != null && f.latSouth != null && f.lonEast != null && f.lonWest != null) {
                if (fish.latitude < f.latSouth || fish.latitude > f.latNorth ||
                    fish.longitude < f.lonWest || fish.longitude > f.lonEast) return@filter false
            }

            // Weight and Length
            val weightOk = (f.weightMin == null || (fish.weight ?: 0L) >= f.weightMin) &&
                          (f.weightMax == null || (fish.weight ?: 0L) <= f.weightMax)
            val lengthOk = (f.lengthMin == null || (fish.length ?: 0L) >= f.lengthMin) &&
                          (f.lengthMax == null || (fish.length ?: 0L) <= f.lengthMax)
            
            val hasWeightFilter = f.weightMin != null || f.weightMax != null
            val hasLengthFilter = f.lengthMin != null || f.lengthMax != null

            if (hasWeightFilter || hasLengthFilter) {
                if (f.weightLengthOperator == "AND") {
                    if (!(weightOk && lengthOk)) return@filter false
                } else {
                    // OR logic: matches if it passes weight filter OR length filter
                    // Note: if only one filter is set, it behaves as single filter
                    if (hasWeightFilter && hasLengthFilter) {
                        if (!(weightOk || lengthOk)) return@filter false
                    } else if (hasWeightFilter) {
                        if (!weightOk) return@filter false
                    } else if (hasLengthFilter) {
                        if (!lengthOk) return@filter false
                    }
                }
            }

            true
        }
    }

    fun applyPlaceFilter(places: List<PlaceOfInterest>): List<PlaceOfInterest> {
        if (!hasActiveFilters()) return places
        val f = getFilters()

        return places.filter { place ->
            // Jos suodatetaan vain kalapisteet, poistetaan kaikki paikat (PlaceOfInterest)
            if (f.onlyFishPoints) return@filter false

            // Place Type
            if (f.placeTypeId != null && place.typeId != f.placeTypeId) return@filter false

            // Free Text
            if (f.freeText != null) {
                val searchText = f.freeText.lowercase()
                val match = place.additionalInfo.lowercase().contains(searchText) || 
                          place.originalRef.lowercase().contains(searchText)
                if (!match) return@filter false
            }

            // Area selection (Bounding Box)
            if (f.latNorth != null && f.latSouth != null && f.lonEast != null && f.lonWest != null) {
                if (place.latitude < f.latSouth || place.latitude > f.latNorth ||
                    place.longitude < f.lonWest || place.longitude > f.lonEast) return@filter false
            }

            true
        }
    }

    fun applyTrackPointFilter(points: List<TrackPoint>): List<TrackPoint> {
        if (!hasActiveFilters()) return points
        val f = getFilters()
        
        // Heatmap suodatetaan vain tietyillä aikasuodattimilla
        val hasDateFilter = f.startDate != null || f.endDate != null
        val hasAnnualDateFilter = f.annualStartDay != null && f.annualStartMonth != null && 
                                f.annualEndDay != null && f.annualEndMonth != null
        val hasTimeFilter = f.startTimeMinutes != null && f.endTimeMinutes != null
        val hasAnnualTimeFilter = f.annualStartTimeMinutes != null && f.annualEndTimeMinutes != null
        val hasAreaFilter = f.latNorth != null && f.latSouth != null && f.lonEast != null && f.lonWest != null
        
        if (!hasDateFilter && !hasAnnualDateFilter && !hasTimeFilter && !hasAnnualTimeFilter && !hasAreaFilter) return points
        
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))

        return points.filter { p ->
            // Area selection (Bounding Box)
            if (hasAreaFilter) {
                if (p.latitude < f.latSouth!! || p.latitude > f.latNorth!! ||
                    p.longitude < f.lonWest!! || p.longitude > f.lonEast!!) return@filter false
            }

            val timestamp = p.timestamp
            calendar.timeInMillis = timestamp
            
            // Date Range
            if (hasDateFilter) {
                if (timestamp == 0L) return@filter false
                if (f.startDate != null && timestamp < f.startDate) return@filter false
                if (f.endDate != null && timestamp > f.endDate) return@filter false
            }

            // Annual Date Range
            if (hasAnnualDateFilter) {
                if (timestamp == 0L) return@filter false
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                
                val currentVal = month * 100 + day
                val startVal = f.annualStartMonth!! * 100 + f.annualStartDay!!
                val endVal = f.annualEndMonth!! * 100 + f.annualEndDay!!
                
                if (startVal <= endVal) {
                    if (currentVal < startVal || currentVal > endVal) return@filter false
                } else {
                    // Spans across year end
                    if (currentVal < startVal && currentVal > endVal) return@filter false
                }
            }

            // Annual Time Range
            if (hasAnnualTimeFilter) {
                if (timestamp == 0L) return@filter false
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val currentMinutes = hour * 60 + minute
                
                if (f.annualStartTimeMinutes!! <= f.annualEndTimeMinutes!!) {
                    if (currentMinutes < f.annualStartTimeMinutes || currentMinutes > f.annualEndTimeMinutes) return@filter false
                } else {
                    // Spans across midnight
                    if (currentMinutes < f.annualStartTimeMinutes && currentMinutes > f.annualEndTimeMinutes) return@filter false
                }
            }

            // Time Range
            if (hasTimeFilter) {
                if (timestamp == 0L) return@filter false
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val currentMinutes = hour * 60 + minute
                
                if (f.startTimeMinutes!! <= f.endTimeMinutes!!) {
                    if (currentMinutes < f.startTimeMinutes || currentMinutes > f.endTimeMinutes) return@filter false
                } else {
                    // Spans across midnight
                    if (currentMinutes < f.startTimeMinutes && currentMinutes > f.endTimeMinutes) return@filter false
                }
            }

            true
        }
    }

    fun getFilterDescription(): String {
        val f = getFilters()
        val parts = mutableListOf<String>()

        if (f.speciesId != null) {
            val db = AppDatabase.getInstance(context)
            val species = db.fishSpeciesDao().getById(f.speciesId)
            if (species != null) {
                parts.add(species.name)
            }
        }

        if (f.placeTypeId != null) {
            val db = AppDatabase.getInstance(context)
            val placeType = db.placeOfInterestTypeDao().getById(f.placeTypeId)
            if (placeType != null) {
                parts.add(placeType.name)
            }
        }

        if (f.freeText != null) {
            parts.add("\"${f.freeText}\"")
        }

        if (f.fisherman != null) {
            fun formatName(name: String): String {
                return name.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { part ->
                    part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
            val fishermanDisplay = formatName(f.fisherman)
            parts.add("kalastaja: $fishermanDisplay")
        }

        if (f.startDate != null || f.endDate != null) {
            val df = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
            val start = f.startDate?.let { df.format(Date(it)) } ?: "..."
            val end = f.endDate?.let { df.format(Date(it)) } ?: "..."
            
            if (start == end && start != "...") {
                parts.add(start)
            } else {
                parts.add("$start-$end")
            }
        }

        if (f.annualStartDay != null && f.annualStartMonth != null && 
            f.annualEndDay != null && f.annualEndMonth != null) {
            val start = "${f.annualStartDay}.${f.annualStartMonth + 1}."
            val end = "${f.annualEndDay}.${f.annualEndMonth + 1}."
            if (start == end) {
                parts.add(start)
            } else {
                parts.add("$start-$end")
            }
        }

        if (f.startTimeMinutes != null && f.endTimeMinutes != null) {
            val startH = f.startTimeMinutes / 60
            val startM = f.startTimeMinutes % 60
            val endH = f.endTimeMinutes / 60
            val endM = f.endTimeMinutes % 60
            val start = String.format(Locale.getDefault(), "%d:%02d", startH, startM)
            val end = String.format(Locale.getDefault(), "%d:%02d", endH, endM)
            if (start == end) {
                parts.add("klo $start")
            } else {
                parts.add("klo $start-$end")
            }
        }

        if (f.windMin != null && f.windMax != null) {
            if (f.windMin == f.windMax) {
                parts.add("tuuli ${f.windMin.toInt()}°")
            } else {
                parts.add("tuuli ${f.windMin.toInt()}°-${f.windMax.toInt()}°")
            }
        }

        if (f.pressureMin != null || f.pressureMax != null) {
            val min = f.pressureMin?.toInt()?.toString() ?: "..."
            val max = f.pressureMax?.toInt()?.toString() ?: "..."
            if (min == max && min != "...") {
                parts.add("paine $min hPa")
            } else {
                parts.add("paine $min-$max hPa")
            }
        }

        when (f.pressureTrendDirection) {
            PRESSURE_TREND_FALLING -> parts.add("painekehitys: Laskeva")
            PRESSURE_TREND_FLAT -> parts.add("painekehitys: Tasainen")
            PRESSURE_TREND_RISING -> parts.add("painekehitys: Nouseva")
        }

        when (f.pressureTurningTrendDirection) {
            PRESSURE_TURNING_TREND_FALLING -> parts.add("paineen muutos: Kääntyy alaspäin")
            PRESSURE_TURNING_TREND_FLAT -> parts.add("paineen muutos: Ei selvää kääntymistä")
            PRESSURE_TURNING_TREND_RISING -> parts.add("paineen muutos: Kääntyy ylöspäin")
        }

        if (f.waterTempMin != null || f.waterTempMax != null) {
            val min = f.waterTempMin?.toInt()?.toString() ?: "..."
            val max = f.waterTempMax?.toInt()?.toString() ?: "..."
            if (min == max && min != "...") {
                parts.add("vesi $min C")
            } else {
                parts.add("vesi $min-$max C")
            }
        }

        if (f.moonPhaseMin != null || f.moonPhaseMax != null) {
            val min = f.moonPhaseMin?.toString() ?: "..."
            val max = f.moonPhaseMax?.toString() ?: "..."
            if (min == max && min != "...") {
                parts.add("kuu $min")
            } else {
                parts.add("kuu $min-$max")
            }
        }

        if (f.moonAltitudeMin != null || f.moonAltitudeMax != null) {
            val min = f.moonAltitudeMin?.toInt()?.toString() ?: "..."
            val max = f.moonAltitudeMax?.toInt()?.toString() ?: "..."
            if (min == max && min != "...") {
                parts.add("kuun korkeus $min°")
            } else {
                parts.add("kuun korkeus $min-$max°")
            }
        }

        if (f.weightMin != null || f.weightMax != null) {
            val min = f.weightMin?.toString() ?: "..."
            val max = f.weightMax?.toString() ?: "..."
            if (min == max && min != "...") {
                parts.add("paino $min g")
            } else {
                parts.add("paino $min-$max g")
            }
        }

        if (f.lengthMin != null || f.lengthMax != null) {
            val min = f.lengthMin?.toString() ?: "..."
            val max = f.lengthMax?.toString() ?: "..."
            if (min == max && min != "...") {
                parts.add("pituus $min cm")
            } else {
                parts.add("pituus $min-$max cm")
            }
        }


        if (f.annualStartTimeMinutes != null && f.annualEndTimeMinutes != null) {
            val startH = f.annualStartTimeMinutes / 60
            val startM = f.annualStartTimeMinutes % 60
            val endH = f.annualEndTimeMinutes / 60
            val endM = f.annualEndTimeMinutes % 60
            val start = String.format(Locale.getDefault(), "%d:%02d", startH, startM)
            val end = String.format(Locale.getDefault(), "%d:%02d", endH, endM)
            if (start == end) {
                parts.add("vuosittainen klo $start")
            } else {
                parts.add("vuosittainen klo $start-$end")
            }
        }

        if (f.latNorth != null) {
            parts.add("aluerajaus")
        }

        return parts.joinToString(" ")
    }
}
