package fi.anssi.kalakartta.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DiaryPageJsonMapperTest {

    private val mapper = DiaryPageJsonMapper {
        JsonService().isoFormat
    }

    @Test
    fun exportsDiaryFieldsAndUsesHelsinkiDayFormat() {
        val start = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki")).apply {
            set(2024, Calendar.JANUARY, 2, 12, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val json = mapper.toJson(
            listOf(
                FishDiaryPage(
                    id = 7,
                    startDate = start,
                    endDate = null,
                    location = "Saimaa",
                    fishingMethod = "Heitto",
                    catch = "Hauki",
                    story = "Hyvä päivä"
                )
            )
        ).getJSONObject(0)

        assertEquals(7, json.getLong("id"))
        assertEquals("2024-01-02T00:00:00Z", json.getString("startDate"))
        assertEquals("Saimaa", json.getString("location"))
        assertEquals("Heitto", json.getString("fishingMethod"))
        assertEquals("Hauki", json.getString("catch"))
        assertEquals("Hyvä päivä", json.getString("story"))
    }

    @Test
    fun importsIsoDatesAndResetsDatabaseId() {
        val json = JSONArray(
            "[{" +
                "\"id\":99," +
                "\"startDate\":\"2024-01-02T00:00:00Z\"," +
                "\"endDate\":\"2024-01-03T00:00:00Z\"," +
                "\"location\":\"Saimaa\"" +
                "}]"
        )

        val page = mapper.fromJson(json).single()

        assertEquals(0L, page.id)
        assertEquals(1704153600000L, page.startDate)
        assertEquals(1704240000000L, page.endDate)
        assertEquals("Saimaa", page.location)
    }

}
