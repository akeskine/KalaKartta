package fi.anssi.kalakartta.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferencesTest {

    @Test
    fun heatmapAndRouteDefaultsRemainCompatible() {
        assertEquals(300.0f, SettingsDefaults.HEATMAP_GRID_SIZE)
        assertEquals(10.0f, SettingsDefaults.HEATMAP_MAX_SPEED)
        assertEquals(50_000, SettingsDefaults.MAX_TRACK_POINTS)
        assertEquals(10_000, SettingsDefaults.MAX_HEATMAP_CELLS)
        assertEquals(10.0f, SettingsDefaults.HEATMAP_MIN_ZOOM)
        assertEquals(64.7f, SettingsDefaults.HEATMAP_REFERENCE_LATITUDE)
        assertEquals(true, SettingsDefaults.ROUTES_FADE_ENABLED)
        assertEquals(365, SettingsDefaults.ROUTES_FADE_START_DAYS)
        assertEquals(30, SettingsDefaults.ROUTES_FADE_FULL_DAYS)
    }

    @Test
    fun preferenceKeysRemainCompatible() {
        assertEquals("heatmap_grid_size", SettingsKeys.HEATMAP_GRID_SIZE)
        assertEquals("heatmap_max_speed", SettingsKeys.HEATMAP_MAX_SPEED)
        assertEquals("max_track_points", SettingsKeys.MAX_TRACK_POINTS)
        assertEquals("max_heatmap_cells", SettingsKeys.MAX_HEATMAP_CELLS)
        assertEquals("heatmap_min_zoom", SettingsKeys.HEATMAP_MIN_ZOOM)
        assertEquals("heatmap_reference_latitude", SettingsKeys.HEATMAP_REFERENCE_LATITUDE)
        assertEquals("routes_fade_enabled", SettingsKeys.ROUTES_FADE_ENABLED)
        assertEquals("routes_fade_start_days", SettingsKeys.ROUTES_FADE_START_DAYS)
        assertEquals("routes_fade_full_days", SettingsKeys.ROUTES_FADE_FULL_DAYS)
    }
}
