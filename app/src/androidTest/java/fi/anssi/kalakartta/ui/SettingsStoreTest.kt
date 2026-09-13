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
        assertEquals(1.0f, store.otherIconScale)
        assertTrue(store.showLiveSessionRoute)
        assertEquals(10, store.locationCheckInterval)
        assertEquals(30, store.minTrackPointInterval)
        assertEquals(300, store.maxTrackPointInterval)
        assertEquals(20, store.minTrackPointDistance)
        assertEquals("", store.defaultFisherman)
        assertFalse(store.showFishermanOnMap)
        assertTrue(store.weatherEnabled)
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
        assertTrue(store.isQuickMapSourceEnabled("MML_MAASTO", false))
    }
}
