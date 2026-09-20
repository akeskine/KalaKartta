package fi.anssi.kalakartta.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {

    private lateinit var store: SettingsStore
    private val preferences by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("settings_store_test", android.content.Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        store = SettingsStore(preferences)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun missingValuesUseExistingDefaults() {
        assertFalse(store.showScaleBar)
        assertFalse(store.showMeasurementTool)
        assertTrue(store.autoCenterOnStart)
        assertEquals("OSM", store.mapSource)
        assertEquals("", store.mmlApiKey)
        assertFalse(store.showQuickMapSource)
        assertEquals(1.0f, store.fishIconScale)
        assertEquals(0.75f, store.otherIconScale)
        assertTrue(store.showLiveSessionRoute)
        assertEquals(10, store.locationCheckInterval)
        assertEquals(30, store.minTrackPointInterval)
        assertEquals(300, store.maxTrackPointInterval)
        assertEquals(20, store.minTrackPointDistance)
        assertEquals("", store.defaultFisherman)
        assertFalse(store.showFishermanOnMap)
        assertTrue(store.weatherEnabled)
        assertEquals(6, store.automaticWeatherUpdateIntervalHours)
        assertFalse(store.talkingClockEnabled)
        assertFalse(store.talkingClockOnlyFishing)
        assertEquals(30, store.talkingClockInterval)
        assertEquals("", store.talkingClockSalutation)
        assertFalse(store.talkingClockBattery)
        assertFalse(store.talkingClockWeather)
        assertFalse(store.talkingClockSunset)
        assertFalse(store.talkingClockSunrise)
        assertEquals(2, store.talkingClockSunsetLimit)
        assertEquals(2, store.talkingClockSunriseLimit)
        assertFalse(store.isTalkingClockWeatherEnabled(3))
        assertFalse(store.heatmapEnabled)
        assertFalse(store.fishingRoutesEnabled)
        assertFalse(store.heatmapFilterEnabled)
        assertFalse(store.routesFilterEnabled)
        assertFalse(store.showHeatmapShortcut)
        assertEquals(0, store.heatmapShortcutMode)
        assertEquals(
            "Reittipisteet + 3x sessiot",
            store.getHeatmapCalculationMethod("Reittipisteet + 3x sessiot")
        )
        assertEquals(100.0f, store.heatmapGridSize)
        assertTrue(store.heatmapAutoConfigure)
        assertEquals(1, store.heatmapMinPoints)
        assertEquals(1, store.heatmapMinPointsByPoints)
        assertEquals(1, store.heatmapMinPointsBySessions)
        assertEquals(1, store.heatmapMinPointsByPointsAndSessions)
        assertEquals(50, store.heatmapMaxPoints)
        assertEquals(50, store.heatmapMaxPointsByPoints)
        assertEquals(5, store.heatmapMaxPointsBySessions)
        assertEquals(50, store.heatmapMaxPointsByPointsAndSessions)
        assertEquals(10.0f, store.heatmapMaxSpeed)
        assertEquals(10.0f, store.heatmapMinZoom)
        assertEquals(50_000, store.maxTrackPoints)
        assertEquals(10_000, store.maxHeatmapCells)
        assertEquals(64.7f, store.heatmapReferenceLatitude)
        assertTrue(store.heatmapFadeCellEdges)
        assertTrue(store.heatmapRemoveTransitions)
        assertEquals(0, store.heatmapRemoveTransitionsMode)
        assertEquals(0.20f, store.pressureTrendThreshold)
        assertEquals(0.20f, store.pressureTurningTrendThreshold)
        assertEquals("", store.lastVersionName)
        assertEquals(-1, store.lastVersionCode)
        assertTrue(store.isQuickMapSourceEnabled("OSM", true))
    }

    @Test
    fun valuesRoundTripThroughLegacyPreferences() {
        store.showScaleBar = true
        store.showMeasurementTool = true
        store.autoCenterOnStart = false
        store.mapSource = "MML_MAASTO"
        store.mmlApiKey = "test-key"
        store.showQuickMapSource = true
        store.fishIconScale = 1.5f
        store.otherIconScale = 0.5f
        store.showLiveSessionRoute = false
        store.locationCheckInterval = 60
        store.minTrackPointInterval = 60
        store.maxTrackPointInterval = 600
        store.minTrackPointDistance = 100
        store.defaultFisherman = "Test Fisherman"
        store.showFishermanOnMap = true
        store.weatherEnabled = false
        store.talkingClockEnabled = true
        store.talkingClockOnlyFishing = true
        store.talkingClockInterval = 10
        store.talkingClockSalutation = "Hei"
        store.talkingClockBattery = true
        store.talkingClockWeather = true
        store.talkingClockSunset = true
        store.talkingClockSunrise = true
        store.talkingClockSunsetLimit = 4
        store.talkingClockSunriseLimit = 6
        store.setTalkingClockWeatherEnabled(3, true)
        store.heatmapEnabled = true
        store.fishingRoutesEnabled = true
        store.heatmapFilterEnabled = true
        store.routesFilterEnabled = true
        store.heatmapShortcutMode = 2
        store.heatmapGridSize = 500.0f
        store.heatmapAutoConfigure = false
        store.heatmapMinPoints = 3
        store.heatmapMinPointsByPoints = 4
        store.heatmapMinPointsBySessions = 5
        store.heatmapMinPointsByPointsAndSessions = 6
        store.heatmapMaxPoints = 100
        store.heatmapMaxPointsByPoints = 110
        store.heatmapMaxPointsBySessions = 120
        store.heatmapMaxPointsByPointsAndSessions = 130
        store.routesFadeEnabled = false
        store.routesFadeStartDays = 100
        store.routesFadeFullDays = 20
        store.heatmapRemoveTransitions = true
        store.heatmapRemoveTransitionsMode = 1
        store.heatmapMaxSpeed = 12.5f
        store.heatmapMinZoom = 8.0f
        store.maxTrackPoints = 20_000
        store.maxHeatmapCells = 5_000
        store.heatmapReferenceLatitude = 60.2f
        store.heatmapFadeCellEdges = false
        store.pressureTrendThreshold = 0.15f
        store.pressureTurningTrendThreshold = 0.25f
        store.lastVersionName = "1.2.3"
        store.lastVersionCode = 123
        store.setHeatmapColor("Violetti")
        store.setHeatmapCalculationMethod("Sessions")
        store.setQuickMapSourceEnabled("MML_MAASTO", true)

        assertTrue(store.showScaleBar)
        assertTrue(store.showMeasurementTool)
        assertFalse(store.autoCenterOnStart)
        assertEquals("MML_MAASTO", store.mapSource)
        assertEquals("test-key", store.mmlApiKey)
        assertTrue(store.showQuickMapSource)
        assertEquals(1.5f, store.fishIconScale)
        assertEquals(0.5f, store.otherIconScale)
        assertFalse(store.showLiveSessionRoute)
        assertEquals(60, store.locationCheckInterval)
        assertEquals(60, store.minTrackPointInterval)
        assertEquals(600, store.maxTrackPointInterval)
        assertEquals(100, store.minTrackPointDistance)
        assertEquals("Test Fisherman", store.defaultFisherman)
        assertTrue(store.showFishermanOnMap)
        assertFalse(store.weatherEnabled)
        assertTrue(store.talkingClockEnabled)
        assertTrue(store.talkingClockOnlyFishing)
        assertEquals(10, store.talkingClockInterval)
        assertEquals("Hei", store.talkingClockSalutation)
        assertTrue(store.talkingClockBattery)
        assertTrue(store.talkingClockWeather)
        assertTrue(store.talkingClockSunset)
        assertTrue(store.talkingClockSunrise)
        assertEquals(4, store.talkingClockSunsetLimit)
        assertEquals(6, store.talkingClockSunriseLimit)
        assertTrue(store.isTalkingClockWeatherEnabled(3))
        assertTrue(store.heatmapEnabled)
        assertTrue(store.fishingRoutesEnabled)
        assertTrue(store.heatmapFilterEnabled)
        assertTrue(store.routesFilterEnabled)
        assertEquals(2, store.heatmapShortcutMode)
        assertEquals(500.0f, store.heatmapGridSize)
        assertFalse(store.heatmapAutoConfigure)
        assertEquals(3, store.heatmapMinPoints)
        assertEquals(4, store.heatmapMinPointsByPoints)
        assertEquals(5, store.heatmapMinPointsBySessions)
        assertEquals(6, store.heatmapMinPointsByPointsAndSessions)
        assertEquals(100, store.heatmapMaxPoints)
        assertEquals(110, store.heatmapMaxPointsByPoints)
        assertEquals(120, store.heatmapMaxPointsBySessions)
        assertEquals(130, store.heatmapMaxPointsByPointsAndSessions)
        assertFalse(store.routesFadeEnabled)
        assertEquals(100, store.routesFadeStartDays)
        assertEquals(20, store.routesFadeFullDays)
        assertTrue(store.heatmapRemoveTransitions)
        assertEquals(1, store.heatmapRemoveTransitionsMode)
        assertEquals(12.5f, store.heatmapMaxSpeed)
        assertEquals(8.0f, store.heatmapMinZoom)
        assertEquals(20_000, store.maxTrackPoints)
        assertEquals(5_000, store.maxHeatmapCells)
        assertEquals(60.2f, store.heatmapReferenceLatitude)
        assertFalse(store.heatmapFadeCellEdges)
        assertEquals(0.15f, store.pressureTrendThreshold)
        assertEquals(0.25f, store.pressureTurningTrendThreshold)
        assertEquals("1.2.3", store.lastVersionName)
        assertEquals(123, store.lastVersionCode)
        assertEquals("Violetti", store.getHeatmapColor("Punainen"))
        assertEquals("Sessions", store.getHeatmapCalculationMethod("Points"))
        assertTrue(store.isQuickMapSourceEnabled("MML_MAASTO", false))
    }
}
