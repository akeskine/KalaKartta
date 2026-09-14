package fi.anssi.kalakartta.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** FishDiaryPage-mallin ja päiväkirjan JSON-vientimuodon välinen muunnos. */
class DiaryPageJsonMapper(
    private val isoFormatProvider: () -> SimpleDateFormat
) {

    fun toJson(pages: List<FishDiaryPage>): JSONArray {
        val array = JSONArray()
        val dayFormat = SimpleDateFormat("yyyy-MM-dd'T'00:00:00'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Europe/Helsinki")
        }

        pages.forEach { page ->
            val obj = JSONObject()
            obj.put("id", page.id)
            obj.put("startDate", dayFormat.format(Date(page.startDate)))
            if (page.endDate != null) {
                obj.put("endDate", dayFormat.format(Date(page.endDate)))
            }
            obj.put("location", page.location)
            obj.put("fishingMethod", page.fishingMethod)
            obj.put("catch", page.catch)
            obj.put("story", page.story)
            array.put(obj)
        }
        return array
    }

    fun fromJson(jsonArray: JSONArray): List<FishDiaryPage> {
        val result = mutableListOf<FishDiaryPage>()
        for (index in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(index)
                val startDate = parseDate(obj, "startDate") ?: 0L
                val endDate = parseDate(obj, "endDate")
                result += FishDiaryPage(
                    id = 0,
                    startDate = startDate,
                    endDate = endDate,
                    location = obj.optString("location", ""),
                    fishingMethod = obj.optString("fishingMethod", ""),
                    catch = obj.optString("catch", ""),
                    story = obj.optString("story", "")
                )
            } catch (_: Exception) {
                // Yksittäinen viallinen sivu ei estä muiden sivujen tuontia.
            }
        }
        return result
    }

    private fun parseDate(obj: JSONObject, key: String): Long? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optString(key, "")
        if (value.isNotEmpty()) {
            return isoFormatProvider().parse(value)?.time
        }
        return obj.optLong(key)
    }
}
