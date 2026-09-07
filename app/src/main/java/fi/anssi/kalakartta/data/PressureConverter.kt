package fi.anssi.kalakartta.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class PressureConverter {
    @TypeConverter
    fun fromPressureSampleList(value: List<PressureSample>): String {
        val array = JSONArray()
        for (sample in value) {
            val obj = JSONObject()
            obj.put("time", sample.time)
            obj.put("pressure", sample.pressure)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toPressureSampleList(value: String): List<PressureSample> {
        val list = mutableListOf<PressureSample>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PressureSample(
                        obj.getLong("time"),
                        obj.getDouble("pressure")
                    )
                )
            }
        } catch (e: Exception) {
            // Log error or handle gracefully
        }
        return list
    }
}
