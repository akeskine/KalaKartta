package fi.anssi.kalakartta.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PlaceOfInterest
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
        val waterTempMin: Float? = null,
        val waterTempMax: Float? = null,
        val speciesId: String? = null,
        val otherSpecies: String? = null,
        val placeTypeId: String? = null,
        val freeText: String? = null,
        val fisherman: String? = null,
        val onlyCaughtFish: Boolean = false,
        val onlyFishPoints: Boolean = false
    )

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
        val waterTempMin = if (prefs.contains("waterTempMin")) prefs.getFloat("waterTempMin", 0f) else null
        val waterTempMax = if (prefs.contains("waterTempMax")) prefs.getFloat("waterTempMax", 0f) else null
        val speciesId = prefs.getString("speciesId", null)
        val otherSpecies = prefs.getString("otherSpecies", null)
        val placeTypeId = prefs.getString("placeTypeId", null)
        val freeText = prefs.getString("freeText", null)
        val fisherman = prefs.getString("fisherman", null)
        val onlyCaughtFish = prefs.getBoolean("onlyCaughtFish", false)
        val onlyFishPoints = prefs.getBoolean("onlyFishPoints", false)

        return Filters(
            startDate, endDate,
            annualStartDay, annualStartMonth,
            annualEndDay, annualEndMonth,
            startTimeMinutes, endTimeMinutes,
            annualStartTimeMinutes, annualEndTimeMinutes,
            windMin, windMax,
            pressureMin, pressureMax,
            waterTempMin, waterTempMax,
            speciesId, otherSpecies, placeTypeId, freeText, fisherman, onlyCaughtFish, onlyFishPoints
        )
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
            if (filters.waterTempMin != null) putFloat("waterTempMin", filters.waterTempMin) else remove("waterTempMin")
            if (filters.waterTempMax != null) putFloat("waterTempMax", filters.waterTempMax) else remove("waterTempMax")
            if (filters.speciesId != null) putString("speciesId", filters.speciesId) else remove("speciesId")
            if (filters.otherSpecies != null) putString("otherSpecies", filters.otherSpecies) else remove("otherSpecies")
            if (filters.placeTypeId != null) putString("placeTypeId", filters.placeTypeId) else remove("placeTypeId")
            if (filters.freeText != null) putString("freeText", filters.freeText) else remove("freeText")
            if (filters.fisherman != null) putString("fisherman", filters.fisherman) else remove("fisherman")
            putBoolean("onlyCaughtFish", filters.onlyCaughtFish)
            putBoolean("onlyFishPoints", filters.onlyFishPoints)
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
                f.waterTempMin != null || f.waterTempMax != null ||
                f.speciesId != null || f.placeTypeId != null || f.freeText != null || f.fisherman != null || f.onlyCaughtFish || f.onlyFishPoints
    }

    fun applyFilter(catches: List<FishCatch>): List<FishCatch> {
        if (!hasActiveFilters()) return catches
        val f = getFilters()
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

            // Water Temp Range
            if (f.waterTempMin != null && fish.waterTemp != null && fish.waterTemp < f.waterTempMin) return@filter false
            if (f.waterTempMax != null && fish.waterTemp != null && fish.waterTemp > f.waterTempMax) return@filter false
            if ((f.waterTempMin != null || f.waterTempMax != null) && fish.waterTemp == null) return@filter false

            // Species
            if (f.speciesId != null && fish.species != f.speciesId) return@filter false
            if (f.speciesId == "OTHER" && f.otherSpecies != null) {
                if (fish.otherSpecies == null || fish.otherSpecies.uppercase() != f.otherSpecies.uppercase()) return@filter false
            }

            // Kalastaja
            if (f.fisherman != null) {
                if (fish.fisherman.uppercase() != f.fisherman.uppercase()) return@filter false
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
            val fishermanDisplay = f.fisherman.lowercase().replaceFirstChar { it.uppercase() }
            parts.add("kalastaja: $fishermanDisplay")
        }

        if (f.startDate != null || f.endDate != null) {
            val df = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
            val start = f.startDate?.let { df.format(Date(it)) } ?: "..."
            val end = f.endDate?.let { df.format(Date(it)) } ?: "..."
            parts.add("$start-$end")
        }

        if (f.annualStartDay != null && f.annualStartMonth != null && 
            f.annualEndDay != null && f.annualEndMonth != null) {
            val start = "${f.annualStartDay}.${f.annualStartMonth + 1}."
            val end = "${f.annualEndDay}.${f.annualEndMonth + 1}."
            parts.add("$start-$end")
        }

        if (f.startTimeMinutes != null && f.endTimeMinutes != null) {
            val startH = f.startTimeMinutes / 60
            val startM = f.startTimeMinutes % 60
            val endH = f.endTimeMinutes / 60
            val endM = f.endTimeMinutes % 60
            val start = String.format(Locale.getDefault(), "%d:%02d", startH, startM)
            val end = String.format(Locale.getDefault(), "%d:%02d", endH, endM)
            parts.add("klo $start-$end")
        }

        if (f.windMin != null && f.windMax != null) {
            parts.add("tuuli ${f.windMin.toInt()}°-${f.windMax.toInt()}°")
        }

        if (f.pressureMin != null || f.pressureMax != null) {
            val min = f.pressureMin?.toInt()?.toString() ?: "..."
            val max = f.pressureMax?.toInt()?.toString() ?: "..."
            parts.add("paine $min-$max hPa")
        }

        if (f.waterTempMin != null || f.waterTempMax != null) {
            val min = f.waterTempMin?.toInt()?.toString() ?: "..."
            val max = f.waterTempMax?.toInt()?.toString() ?: "..."
            parts.add("vesi $min-$max C")
        }

        if (f.onlyCaughtFish) {
            parts.add("vain saadut")
        }

        if (f.onlyFishPoints) {
            parts.add("vain kalapisteet")
        }

        if (f.annualStartTimeMinutes != null && f.annualEndTimeMinutes != null) {
            val startH = f.annualStartTimeMinutes / 60
            val startM = f.annualStartTimeMinutes % 60
            val endH = f.annualEndTimeMinutes / 60
            val endM = f.annualEndTimeMinutes % 60
            val start = String.format(Locale.getDefault(), "%d:%02d", startH, startM)
            val end = String.format(Locale.getDefault(), "%d:%02d", endH, endM)
            parts.add("vuosittainen klo $start-$end")
        }

        return parts.joinToString(" ")
    }
}
