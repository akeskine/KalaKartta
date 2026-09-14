package fi.anssi.kalakartta.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceJsonMapperTest {

    private val mapper = PlaceJsonMapper()

    @Test
    fun exportsAllPlaceFieldsWithFiveDecimalCoordinates() {
        val json = mapper.toJson(
            listOf(
                PlaceOfInterest(
                    id = 4,
                    typeId = "ISLAND",
                    latitude = 60.123456,
                    longitude = 24.987654,
                    name = "Saari",
                    additionalInfo = "Hyvä paikka",
                    originalRef = "place-4"
                )
            )
        ).getJSONObject(0)

        assertEquals(4, json.getLong("id"))
        assertEquals("ISLAND", json.getString("typeId"))
        assertEquals(60.12346, json.getDouble("latitude"), 0.000001)
        assertEquals(24.98765, json.getDouble("longitude"), 0.000001)
        assertEquals("Saari", json.getString("name"))
        assertEquals("Hyvä paikka", json.getString("additionalInfo"))
        assertEquals("place-4", json.getString("originalRef"))
    }

    @Test
    fun importsDefaultsAndResetsDatabaseId() {
        val json = JSONArray("[{\"id\":99,\"typeId\":\"UNKNOWN\",\"name\":\"Nimetön\"}]")

        val place = mapper.fromJson(json).single()

        assertEquals(0L, place.id)
        assertEquals(60.0, place.latitude, 0.000001)
        assertEquals(24.0, place.longitude, 0.000001)
        assertEquals("Nimetön", place.name)
    }

    @Test
    fun skipsMalformedItemsWithoutDroppingValidItems() {
        val json = JSONArray("[\"not an object\", {\"typeId\":\"ROCK\",\"latitude\":61.0,\"longitude\":25.0}]")

        val places = mapper.fromJson(json)

        assertEquals(1, places.size)
        assertTrue(places.single().typeId == "ROCK")
    }
}
