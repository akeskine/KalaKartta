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
        store.setQuickMapSourceEnabled("MML_MAASTO", true)

        assertTrue(store.showScaleBar)
        assertTrue(store.showMeasurementTool)
        assertFalse(store.autoCenterOnStart)
        assertEquals("MML_MAASTO", store.mapSource)
        assertEquals("test-key", store.mmlApiKey)
        assertTrue(store.showQuickMapSource)
        assertEquals(1.5f, store.fishIconScale)
        assertEquals(0.5f, store.otherIconScale)
        assertTrue(store.isQuickMapSourceEnabled("MML_MAASTO", false))
    }
}
