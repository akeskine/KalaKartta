package fi.anssi.kalakartta.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class JsonCompatibilityTest {

    @Test
    fun testExportWithTwoCatchesHavingPressureSamples() {
        val service = JsonService()
        val samples1 = listOf(PressureSample(1000L, 1010.0))
        val samples2 = listOf(PressureSample(3000L, 1020.0))
        
        val catch1 = FishCatch(species = "AHVEN", latitude = 60.0, longitude = 24.0, caughtAt = 1000L, pressureSamples = samples1)
        val catch2 = FishCatch(species = "HAUKI", latitude = 61.0, longitude = 25.0, caughtAt = 2000L, pressureSamples = samples2)
        
        val catches = listOf(catch1, catch2)
        val root = service.exportCatchesAndPlaces(catches, emptyList())
        val catchesArray = root.getJSONArray("catches")
        
        assertEquals(2, catchesArray.length())
        
        val obj1 = catchesArray.getJSONObject(0)
        assertTrue(obj1.has("pressureSamples"))
        assertEquals(1, obj1.getJSONArray("pressureSamples").length())
        assertEquals(1010.0, obj1.getJSONArray("pressureSamples").getJSONObject(0).getDouble("pressure"), 0.001)
        
        val obj2 = catchesArray.getJSONObject(1)
        assertTrue(obj2.has("pressureSamples"))
        assertEquals(1, obj2.getJSONArray("pressureSamples").length())
        assertEquals(1020.0, obj2.getJSONArray("pressureSamples").getJSONObject(0).getDouble("pressure"), 0.001)
    }

    @Test
    fun testExportWithNewFields() {
        val service = JsonService()
        val samples = listOf(
            PressureSample(1000L, 1013.25123456),
            PressureSample(2000L, 1012.0)
        )
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.12345,
            longitude = 24.54321,
            caughtAt = 1672531200000L, // 2023-01-01 00:00:00 UTC
            pressureTrend = 1.500006,
            pressureSamples = samples,
            moonPhase = 0.5,
            moonAltitude = 45.0
        )
        
        val catches = listOf(fishCatch)
        val root = service.exportCatchesAndPlaces(catches, emptyList())
        val catchesArray = root.getJSONArray("catches")
        val obj = catchesArray.getJSONObject(0)
        
        assertEquals(1.50001, obj.getDouble("pressureTrend"), 0.000001)
        assertEquals(0.5, obj.getDouble("moonPhase"), 0.001)
        assertEquals(45.0, obj.getDouble("moonAltitude"), 0.001)
        
        val samplesArray = obj.getJSONArray("pressureSamples")
        assertEquals(2, samplesArray.length())
        assertEquals(1013.25123, samplesArray.getJSONObject(0).getDouble("pressure"), 0.000001)
        assertEquals(1012.0, samplesArray.getJSONObject(1).getDouble("pressure"), 0.001)
    }

    @Test
    fun testPressurePrecisionRoundTrip() {
        val service = JsonService()
        val fishCatch = FishCatch(
            species = "AHVEN",
            latitude = 60.0,
            longitude = 24.0,
            caughtAt = 1000L,
            pressureTrend = -0.1234567,
            pressureSamples = listOf(
                PressureSample(1000L, 1000.1234567),
                PressureSample(2000L, 1001.9876543)
            )
        )

        val json = service.exportCatchesAndPlaces(listOf(fishCatch), emptyList()).toString()
        val imported = service.parseImportData(json).catches.single()

        assertEquals(-0.12346, imported.pressureTrend!!, 0.000001)
        assertEquals(1000.12346, imported.pressureSamples[0].pressure, 0.000001)
        assertEquals(1001.98765, imported.pressureSamples[1].pressure, 0.000001)
    }

    @Test
    fun testImportOldJsonMissingNewFields() {
        val service = JsonService()
        val oldJson = """
            {
                "catches": [
                    {
                        "species": "HAUKI",
                        "latitude": 61.0,
                        "longitude": 25.0,
                        "method": "Heitto",
                        "weight": 2500
                    }
                ],
                "places": []
            }
        """.trimIndent()
        
        val importData = service.parseImportData(oldJson)
        assertEquals(1, importData.catches.size)
        val fishCatch = importData.catches[0]
        
        assertEquals("HAUKI", fishCatch.species)
        assertNull(fishCatch.pressureTrend)
        assertTrue(fishCatch.pressureSamples.isEmpty())
        assertNull(fishCatch.moonPhase)
        assertNull(fishCatch.moonAltitude)
    }

    @Test
    fun testImportNewJson() {
        val service = JsonService()
        val newJson = """
            {
                "catches": [
                    {
                        "species": "KUHA",
                        "latitude": 62.0,
                        "longitude": 26.0,
                        "pressureTrend": -0.5,
                        "pressureSamples": [
                            {"time": "2023-01-01T00:00:00Z", "pressure": 1010.0},
                            {"time": "2023-01-01T01:00:00Z", "pressure": 1009.5}
                        ],
                        "moonPhase": 0.25,
                        "moonAltitude": 30.0
                    }
                ]
            }
        """.trimIndent()
        
        val importData = service.parseImportData(newJson)
        assertEquals(1, importData.catches.size)
        val fishCatch = importData.catches[0]
        
        assertEquals("KUHA", fishCatch.species)
        assertEquals(-0.5, fishCatch.pressureTrend!!, 0.001)
        assertEquals(2, fishCatch.pressureSamples.size)
        assertEquals(1010.0, fishCatch.pressureSamples[0].pressure, 0.001)
        assertEquals(0.25, fishCatch.moonPhase!!, 0.001)
        assertEquals(30.0, fishCatch.moonAltitude!!, 0.001)
    }

    @Test
    fun testImportNormalizesPressurePrecision() {
        val service = JsonService()
        val json = """
            {
                "catches": [{
                    "species": "KUHA",
                    "pressureTrend": 0.9876543,
                    "pressureSamples": [
                        {"time": "2023-01-01T00:00:00Z", "pressure": 1012.3456789}
                    ]
                }]
            }
        """.trimIndent()

        val fishCatch = service.parseImportData(json).catches.single()

        assertEquals(0.98765, fishCatch.pressureTrend!!, 0.000001)
        assertEquals(1012.34568, fishCatch.pressureSamples.single().pressure, 0.000001)
    }
}
