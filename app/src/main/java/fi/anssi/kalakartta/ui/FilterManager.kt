package fi.anssi.kalakartta.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
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
        val windMin: Float? = null,
        val windMax: Float? = null,
        val pressureMin: Float? = null,
        val pressureMax: Float? = null,
        val speciesId: String? = null,
        val onlyCaughtFish: Boolean = false
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
        val windMin = if (prefs.contains("windMin")) prefs.getFloat("windMin", 0f) else null
        val windMax = if (prefs.contains("windMax")) prefs.getFloat("windMax", 0f) else null
        val pressureMin = if (prefs.contains("pressureMin")) prefs.getFloat("pressureMin", 0f) else null
        val pressureMax = if (prefs.contains("pressureMax")) prefs.getFloat("pressureMax", 0f) else null
        val speciesId = prefs.getString("speciesId", null)
        val onlyCaughtFish = prefs.getBoolean("onlyCaughtFish", false)

        return Filters(
            startDate, endDate,
            annualStartDay, annualStartMonth,
            annualEndDay, annualEndMonth,
            startTimeMinutes, endTimeMinutes,
            windMin, windMax,
            pressureMin, pressureMax,
            speciesId, onlyCaughtFish
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
            if (filters.windMin != null) putFloat("windMin", filters.windMin) else remove("windMin")
            if (filters.windMax != null) putFloat("windMax", filters.windMax) else remove("windMax")
            if (filters.pressureMin != null) putFloat("pressureMin", filters.pressureMin) else remove("pressureMin")
            if (filters.pressureMax != null) putFloat("pressureMax", filters.pressureMax) else remove("pressureMax")
            if (filters.speciesId != null) putString("speciesId", filters.speciesId) else remove("speciesId")
            putBoolean("onlyCaughtFish", filters.onlyCaughtFish)
            apply()
        }
    }

    fun hasActiveFilters(): Boolean {
        val f = getFilters()
        return f.startDate != null || f.endDate != null ||
                f.annualStartDay != null || f.annualStartMonth != null ||
                f.annualEndDay != null || f.annualEndMonth != null ||
                f.startTimeMinutes != null || f.endTimeMinutes != null ||
                f.windMin != null || f.windMax != null ||
                f.pressureMin != null || f.pressureMax != null ||
                f.speciesId != null || f.onlyCaughtFish
    }

    fun applyFilter(catches: List<FishCatch>): List<FishCatch> {
        if (!hasActiveFilters()) return catches
        val f = getFilters()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))

        return catches.filter { fish ->
            calendar.timeInMillis = fish.caughtAt
            
            // Date Range
            if (f.startDate != null || f.endDate != null) {
                if (fish.caughtAt == 0L) return@filter false
                if (f.startDate != null && fish.caughtAt < f.startDate) return@filter false
                if (f.endDate != null && fish.caughtAt > f.endDate) return@filter false
            }

            // Annual Date Range
            if (f.annualStartDay != null && f.annualStartMonth != null && 
                f.annualEndDay != null && f.annualEndMonth != null) {
                if (fish.caughtAt == 0L) return@filter false
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

            // Time Range
            if (f.startTimeMinutes != null && f.endTimeMinutes != null) {
                if (fish.caughtAt == 0L) return@filter false
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

            // Species
            if (f.speciesId != null && fish.species != f.speciesId) return@filter false

            // Only Caught Fish
            if (f.onlyCaughtFish && fish.eventType != FishCatch.CAUGHT_FISH) return@filter false

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

        if (f.onlyCaughtFish) {
            parts.add("vain saadut")
        }

        return parts.joinToString(" ")
    }
}
