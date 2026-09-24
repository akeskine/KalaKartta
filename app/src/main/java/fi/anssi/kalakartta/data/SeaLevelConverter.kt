package fi.anssi.kalakartta.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class SeaLevelConverter {
    @TypeConverter
    fun fromSeaLevelSampleList(value: List<SeaLevelSample>): String {
        val array = JSONArray()
        value.forEach { sample ->
            array.put(JSONObject().apply {
                put("time", sample.time)
                put("seaLevel", sample.seaLevel)
            })
        }
        return array.toString()
    }

    @TypeConverter
    fun toSeaLevelSampleList(value: String): List<SeaLevelSample> {
        val list = mutableListOf<SeaLevelSample>()
        try {
            val array = JSONArray(value)
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                list.add(SeaLevelSample(obj.getLong("time"), obj.getLong("seaLevel")))
            }
        } catch (_: Exception) {
            // Säilytetään vanha tai virheellinen sarake tyhjänä listana.
        }
        return list
    }
}