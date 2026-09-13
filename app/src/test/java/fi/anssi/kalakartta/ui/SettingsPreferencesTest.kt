package fi.anssi.kalakartta.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferencesTest {

    @Test
    fun heatmapAndRouteDefaultsRemainCompatible() {
        assertEquals(false, SettingsDefaults.HEATMAP_ENABLED)
        assertEquals(false, SettingsDefaults.FISHING_ROUTES_ENABLED)
        assertEquals(false, SettingsDefaults.HEATMAP_FILTER_ENABLED)
        assertEquals(false, SettingsDefaults.ROUTES_FILTER_ENABLED)
        assertEquals(false, SettingsDefaults.SHOW_HEATMAP_SHORTCUT)
        assertEquals(0, SettingsDefaults.HEATMAP_SHORTCUT_MODE)
        assertEquals(1, SettingsDefaults.HEATMAP_MIN_POINTS)
        assertEquals(50, SettingsDefaults.HEATMAP_MAX_POINTS)
        assertEquals(1, SettingsDefaults.HEATMAP_MIN_POINTS_BY_POINTS)
        assertEquals(50, SettingsDefaults.HEATMAP_MAX_POINTS_BY_POINTS)
        assertEquals(1, SettingsDefaults.HEATMAP_MIN_POINTS_BY_SESSIONS)
        assertEquals(5, SettingsDefaults.HEATMAP_MAX_POINTS_BY_SESSIONS)
        assertEquals(true, SettingsDefaults.HEATMAP_AUTO_CONFIGURE)
        assertEquals(false, SettingsDefaults.HEATMAP_REMOVE_TRANSITIONS)
        assertEquals(0, SettingsDefaults.HEATMAP_REMOVE_TRANSITIONS_MODE)
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
        assertEquals("heatmap_enabled", SettingsKeys.HEATMAP_ENABLED)
        assertEquals("fishing_routes_enabled", SettingsKeys.FISHING_ROUTES_ENABLED)
        assertEquals("heatmap_filter_enabled", SettingsKeys.HEATMAP_FILTER_ENABLED)
        assertEquals("routes_filter_enabled", SettingsKeys.ROUTES_FILTER_ENABLED)
        assertEquals("show_heatmap_shortcut", SettingsKeys.SHOW_HEATMAP_SHORTCUT)
        assertEquals("heatmap_shortcut_mode", SettingsKeys.HEATMAP_SHORTCUT_MODE)
        assertEquals("heatmap_color", SettingsKeys.HEATMAP_COLOR)
        assertEquals("heatmap_calculation_method", SettingsKeys.HEATMAP_CALCULATION_METHOD)
        assertEquals("heatmap_min_points", SettingsKeys.HEATMAP_MIN_POINTS)
        assertEquals("heatmap_max_points", SettingsKeys.HEATMAP_MAX_POINTS)
        assertEquals("heatmap_min_points_by_points", SettingsKeys.HEATMAP_MIN_POINTS_BY_POINTS)
        assertEquals("heatmap_max_points_by_points", SettingsKeys.HEATMAP_MAX_POINTS_BY_POINTS)
        assertEquals("heatmap_min_points_by_sessions", SettingsKeys.HEATMAP_MIN_POINTS_BY_SESSIONS)
        assertEquals("heatmap_max_points_by_sessions", SettingsKeys.HEATMAP_MAX_POINTS_BY_SESSIONS)
        assertEquals("heatmap_auto_configure", SettingsKeys.HEATMAP_AUTO_CONFIGURE)
        assertEquals("heatmap_remove_transitions", SettingsKeys.HEATMAP_REMOVE_TRANSITIONS)
        assertEquals("heatmap_remove_transitions_mode", SettingsKeys.HEATMAP_REMOVE_TRANSITIONS_MODE)
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
