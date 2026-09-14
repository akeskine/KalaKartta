package fi.anssi.kalakartta.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** PlaceOfInterest-mallin ja JSON-vientimuodon välinen muunnos. */
class PlaceJsonMapper {

    fun toJson(places: List<PlaceOfInterest>): JSONArray {
        val array = JSONArray()
        places.forEach { place ->
            val obj = JSONObject()
            obj.put("id", place.id)
            obj.put("typeId", place.typeId)
            obj.put("latitude", roundCoordinate(place.latitude))
            obj.put("longitude", roundCoordinate(place.longitude))
            obj.put("name", place.name)
            obj.put("additionalInfo", place.additionalInfo)
            obj.put("originalRef", place.originalRef)
            array.put(obj)
        }
        return array
    }

    fun fromJson(jsonArray: JSONArray): List<PlaceOfInterest> {
        val result = mutableListOf<PlaceOfInterest>()
        for (index in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(index)
                result += PlaceOfInterest(
                    id = 0,
                    typeId = obj.optString("typeId", "UNKNOWN"),
                    latitude = roundCoordinate(coordinate(obj, "latitude", 60.0)),
                    longitude = roundCoordinate(coordinate(obj, "longitude", 24.0)),
                    name = obj.optString("name", ""),
                    additionalInfo = obj.optString("additionalInfo", ""),
                    originalRef = obj.optString("originalRef", "")
                )
            } catch (_: Exception) {
                // Säilytetään aiempi käyttäytyminen: yksittäinen viallinen piste ei estä koko tuontia.
            }
        }
        return result
    }

    private fun coordinate(obj: JSONObject, key: String, defaultValue: Double): Double =
        if (obj.isNull(key) || !obj.has(key)) defaultValue else obj.optDouble(key, defaultValue)

    private fun roundCoordinate(value: Double): Double =
        String.format(Locale.US, "%.5f", value).toDouble()
}
