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
        val speciesId: String? = null
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
        val speciesId = prefs.getString("speciesId", null)

        return Filters(
            startDate, endDate,
            annualStartDay, annualStartMonth,
            annualEndDay, annualEndMonth,
            startTimeMinutes, endTimeMinutes,
            speciesId
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
            if (filters.speciesId != null) putString("speciesId", filters.speciesId) else remove("speciesId")
            apply()
        }
    }

    fun hasActiveFilters(): Boolean {
        val f = getFilters()
        return f.startDate != null || f.endDate != null ||
                f.annualStartDay != null || f.annualStartMonth != null ||
                f.annualEndDay != null || f.annualEndMonth != null ||
                f.startTimeMinutes != null || f.endTimeMinutes != null ||
                f.speciesId != null
    }

    fun applyFilter(catches: List<FishCatch>): List<FishCatch> {
        if (!hasActiveFilters()) return catches
        val f = getFilters()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))

        return catches.filter { fish ->
            calendar.timeInMillis = fish.caughtAt
            
            // Date Range
            if (f.startDate != null && fish.caughtAt < f.startDate) return@filter false
            if (f.endDate != null && fish.caughtAt > f.endDate) return@filter false

            // Annual Date Range
            if (f.annualStartDay != null && f.annualStartMonth != null && 
                f.annualEndDay != null && f.annualEndMonth != null) {
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

            // Species
            if (f.speciesId != null && fish.species != f.speciesId) return@filter false

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

        return parts.joinToString(" ")
    }
}
