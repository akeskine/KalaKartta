package fi.anssi.kalakartta.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.util.*

class JsonServiceTest {

    @Ignore("Requires android.util.Log and JsonWriter mocking")
    @Test
    fun testSessionsToJson() {
        val service = JsonService()
        
        val sessions = listOf(
            FishingSession(id = 1, startedAt = 1000000L, endedAt = 2000000L, notes = "Test notes")
        )
        val points = listOf(
            TrackPoint(id = 1, fishingSessionId = 1, timestamp = 1500000L, latitude = 60.0, longitude = 25.0, speed = 1.5f, accuracy = 5.0f)
        )
        val pointsMap = mapOf(1L to points)
        
        val method = JsonService::class.java.getDeclaredMethod("sessionsToJson", List::class.java, Map::class.java)
        method.isAccessible = true
        
        val result = method.invoke(service, sessions, pointsMap) as JSONArray
        
        assertEquals(1, result.length())
        val sessionObj = result.getJSONObject(0)
        assertEquals("Test notes", sessionObj.getString("notes"))
        
        val pointsArray = sessionObj.getJSONArray("points")
        assertEquals(1, pointsArray.length())
        val pointObj = pointsArray.getJSONObject(0)
        assertEquals(60.0, pointObj.getDouble("latitude"), 0.0001)
        assertEquals(25.0, pointObj.getDouble("longitude"), 0.0001)
    }

    @Test
    fun testIsCustomIcon() {
        val service = JsonService()
        
        val method = JsonService::class.java.getDeclaredMethod("isCustomIcon", String::class.java)
        method.isAccessible = true
        
        fun isCustomIcon(path: String): Boolean = method.invoke(service, path) as Boolean

        assertTrue(isCustomIcon("/data/user/0/fi.anssi.kalakartta/files/custom_icon_123.png"))
        assertTrue(isCustomIcon("custom_icon_123.png")) // Now should be true if it contains "/" or we update logic
        assertTrue(!isCustomIcon("ahven"))
        assertTrue(!isCustomIcon(""))
    }

    @Test
    fun testGetIconFileName() {
        val service = JsonService()
        val method = JsonService::class.java.getDeclaredMethod("getIconFileName", String::class.java)
        method.isAccessible = true
        
        fun getIconFileName(path: String): String = method.invoke(service, path) as String

        assertEquals("custom_icon_123.png", getIconFileName("/data/user/0/fi.anssi.kalakartta/files/custom_icon_123.png"))
        assertEquals("ahven", getIconFileName("ahven"))
    }
}
