package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.utils.CopernicusTileImageSize
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferencesTest {

    @Test
    fun heatmapAndRouteDefaultsRemainCompatible() {
        assertEquals(false, SettingsDefaults.SHOW_SCALE_BAR)
        assertEquals(false, SettingsDefaults.SHOW_MEASUREMENT_TOOL)
        assertEquals(true, SettingsDefaults.AUTO_CENTER_ON_START)
        assertEquals("OSM", SettingsDefaults.MAP_SOURCE)
        assertEquals("", SettingsDefaults.MML_API_KEY)
        assertEquals(false, SettingsDefaults.MML_API_KEY_INVALID)
        assertEquals("", SettingsDefaults.COPERNICUS_CLIENT_ID)
        assertEquals(false, SettingsDefaults.COPERNICUS_CUSTOM_DATE_ENABLED)
        assertEquals(512, SettingsDefaults.COPERNICUS_TILE_CACHE_LIMIT_MB)
        assertEquals(64, SettingsDefaults.MIN_COPERNICUS_TILE_CACHE_LIMIT_MB)
        assertEquals(4096, SettingsDefaults.MAX_COPERNICUS_TILE_CACHE_LIMIT_MB)
        assertEquals(1024, SettingsDefaults.COPERNICUS_TILE_IMAGE_SIZE_PIXELS)
        assertEquals(listOf(256, 512, 1024, 2048), CopernicusTileImageSize.OPTIONS_PIXELS)
        assertEquals(false, SettingsDefaults.SHOW_QUICK_MAP_SOURCE)
        assertEquals(true, SettingsDefaults.SHOW_LIVE_SESSION_ROUTE)
        assertEquals(10, SettingsDefaults.LOCATION_CHECK_INTERVAL)
        assertEquals(30, SettingsDefaults.MIN_TRACK_POINT_INTERVAL)
        assertEquals(300, SettingsDefaults.MAX_TRACK_POINT_INTERVAL)
        assertEquals(20, SettingsDefaults.MIN_TRACK_POINT_DISTANCE)
        assertEquals("", SettingsDefaults.DEFAULT_FISHERMAN)
        assertEquals(false, SettingsDefaults.SHOW_FISHERMAN_ON_MAP)
        assertEquals(1.0f, SettingsDefaults.FISH_ICON_SCALE)
        assertEquals(0.75f, SettingsDefaults.OTHER_ICON_SCALE)
        assertEquals(true, SettingsDefaults.WEATHER_ENABLED)
        assertEquals(false, SettingsDefaults.AUTOMATIC_MISSING_WEATHER_UPDATE)
        assertEquals(6, SettingsDefaults.AUTOMATIC_WEATHER_UPDATE_INTERVAL_HOURS)
        assertEquals(false, SettingsDefaults.TALKING_CLOCK_ENABLED)
        assertEquals(false, SettingsDefaults.TALKING_CLOCK_ONLY_FISHING)
        assertEquals(30, SettingsDefaults.TALKING_CLOCK_INTERVAL)
        assertEquals("", SettingsDefaults.TALKING_CLOCK_SALUTATION)
        assertEquals(false, SettingsDefaults.TALKING_CLOCK_BATTERY)
        assertEquals(false, SettingsDefaults.TALKING_CLOCK_WEATHER)
        assertEquals(false, SettingsDefaults.TALKING_CLOCK_SUNSET)
        assertEquals(false, SettingsDefaults.TALKING_CLOCK_SUNRISE)
        assertEquals(2, SettingsDefaults.TALKING_CLOCK_SUNSET_LIMIT)
        assertEquals(2, SettingsDefaults.TALKING_CLOCK_SUNRISE_LIMIT)
        assertEquals(false, SettingsDefaults.TALKING_CLOCK_WEATHER_OPTION_ENABLED)
        assertEquals(3, SettingsDefaults.TALKING_CLOCK_WEATHER_DEFAULT_HOURS)
        assertEquals(false, SettingsDefaults.HEATMAP_ENABLED)
        assertEquals(false, SettingsDefaults.FISHING_ROUTES_ENABLED)
        assertEquals(false, SettingsDefaults.HEATMAP_FILTER_ENABLED)
        assertEquals(false, SettingsDefaults.ROUTES_FILTER_ENABLED)
        assertEquals(false, SettingsDefaults.SHOW_HEATMAP_SHORTCUT)
        assertEquals(0, SettingsDefaults.HEATMAP_SHORTCUT_MODE)
        assertEquals("Reittipisteet + 3x sessiot", SettingsDefaults.HEATMAP_CALCULATION_METHOD)
        assertEquals(1, SettingsDefaults.HEATMAP_MIN_POINTS)
        assertEquals(50, SettingsDefaults.HEATMAP_MAX_POINTS)
        assertEquals(1, SettingsDefaults.HEATMAP_MIN_POINTS_BY_POINTS)
        assertEquals(50, SettingsDefaults.HEATMAP_MAX_POINTS_BY_POINTS)
        assertEquals(1, SettingsDefaults.HEATMAP_MIN_POINTS_BY_SESSIONS)
        assertEquals(5, SettingsDefaults.HEATMAP_MAX_POINTS_BY_SESSIONS)
        assertEquals(1, SettingsDefaults.HEATMAP_MIN_POINTS_BY_POINTS_AND_SESSIONS)
        assertEquals(50, SettingsDefaults.HEATMAP_MAX_POINTS_BY_POINTS_AND_SESSIONS)
        assertEquals(true, SettingsDefaults.HEATMAP_AUTO_CONFIGURE)
        assertEquals(true, SettingsDefaults.HEATMAP_REMOVE_TRANSITIONS)
        assertEquals(0, SettingsDefaults.HEATMAP_REMOVE_TRANSITIONS_MODE)
        assertEquals(100.0f, SettingsDefaults.HEATMAP_GRID_SIZE)
        assertEquals(10.0f, SettingsDefaults.HEATMAP_MAX_SPEED)
        assertEquals(50_000, SettingsDefaults.MAX_TRACK_POINTS)
        assertEquals(10_000, SettingsDefaults.MAX_HEATMAP_CELLS)
        assertEquals(10.0f, SettingsDefaults.HEATMAP_MIN_ZOOM)
        assertEquals(64.7f, SettingsDefaults.HEATMAP_REFERENCE_LATITUDE)
        assertEquals(true, SettingsDefaults.HEATMAP_FADE_CELL_EDGES)
        assertEquals(true, SettingsDefaults.ROUTES_FADE_ENABLED)
        assertEquals(365, SettingsDefaults.ROUTES_FADE_START_DAYS)
        assertEquals(30, SettingsDefaults.ROUTES_FADE_FULL_DAYS)
    }

    @Test
    fun preferenceKeysRemainCompatible() {
        assertEquals("show_scale_bar", SettingsKeys.SHOW_SCALE_BAR)
        assertEquals("show_measurement_tool", SettingsKeys.SHOW_MEASUREMENT_TOOL)
        assertEquals("auto_center_on_start", SettingsKeys.AUTO_CENTER_ON_START)
        assertEquals("map_source", SettingsKeys.MAP_SOURCE)
        assertEquals("mml_api_key", SettingsKeys.MML_API_KEY)
        assertEquals("mml_api_key_invalid", SettingsKeys.MML_API_KEY_INVALID)
        assertEquals("copernicus_client_id", SettingsKeys.COPERNICUS_CLIENT_ID)
        assertEquals("copernicus_custom_date_enabled", SettingsKeys.COPERNICUS_CUSTOM_DATE_ENABLED)
        assertEquals("copernicus_target_date", SettingsKeys.COPERNICUS_TARGET_DATE)
        assertEquals("copernicus_tile_cache_limit_mb", SettingsKeys.COPERNICUS_TILE_CACHE_LIMIT_MB)
        assertEquals("copernicus_tile_image_size_pixels", SettingsKeys.COPERNICUS_TILE_IMAGE_SIZE_PIXELS)
        assertEquals("show_quick_map_source", SettingsKeys.SHOW_QUICK_MAP_SOURCE)
        assertEquals("quick_select_MML_MAASTO", SettingsKeys.quickSelect("MML_MAASTO"))
        assertEquals("show_live_session_route", SettingsKeys.SHOW_LIVE_SESSION_ROUTE)
        assertEquals("location_check_interval", SettingsKeys.LOCATION_CHECK_INTERVAL)
        assertEquals("min_track_point_interval", SettingsKeys.MIN_TRACK_POINT_INTERVAL)
        assertEquals("max_track_point_interval", SettingsKeys.MAX_TRACK_POINT_INTERVAL)
        assertEquals("min_track_point_distance", SettingsKeys.MIN_TRACK_POINT_DISTANCE)
        assertEquals("default_fisherman", SettingsKeys.DEFAULT_FISHERMAN)
        assertEquals("show_fisherman_on_map", SettingsKeys.SHOW_FISHERMAN_ON_MAP)
        assertEquals("fish_icon_scale", SettingsKeys.FISH_ICON_SCALE)
        assertEquals("other_icon_scale", SettingsKeys.OTHER_ICON_SCALE)
        assertEquals("weather_enabled", SettingsKeys.WEATHER_ENABLED)
        assertEquals("automatic_missing_weather_update", SettingsKeys.AUTOMATIC_MISSING_WEATHER_UPDATE)
        assertEquals("last_missing_weather_update_at", SettingsKeys.LAST_MISSING_WEATHER_UPDATE_AT)
        assertEquals("automatic_weather_update_interval_hours", SettingsKeys.AUTOMATIC_WEATHER_UPDATE_INTERVAL_HOURS)
        assertEquals("talking_clock_enabled", SettingsKeys.TALKING_CLOCK_ENABLED)
        assertEquals("talking_clock_only_fishing", SettingsKeys.TALKING_CLOCK_ONLY_FISHING)
        assertEquals("talking_clock_interval", SettingsKeys.TALKING_CLOCK_INTERVAL)
        assertEquals("talking_clock_salutation", SettingsKeys.TALKING_CLOCK_SALUTATION)
        assertEquals("talking_clock_battery", SettingsKeys.TALKING_CLOCK_BATTERY)
        assertEquals("talking_clock_weather", SettingsKeys.TALKING_CLOCK_WEATHER)
        assertEquals("talking_clock_sunset", SettingsKeys.TALKING_CLOCK_SUNSET)
        assertEquals("talking_clock_sunrise", SettingsKeys.TALKING_CLOCK_SUNRISE)
        assertEquals("talking_clock_sunset_limit", SettingsKeys.TALKING_CLOCK_SUNSET_LIMIT)
        assertEquals("talking_clock_sunrise_limit", SettingsKeys.TALKING_CLOCK_SUNRISE_LIMIT)
        assertEquals("talking_clock_weather_3h", SettingsKeys.talkingClockWeather(3))
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
        assertEquals("heatmap_min_points_by_points_and_sessions", SettingsKeys.HEATMAP_MIN_POINTS_BY_POINTS_AND_SESSIONS)
        assertEquals("heatmap_max_points_by_points_and_sessions", SettingsKeys.HEATMAP_MAX_POINTS_BY_POINTS_AND_SESSIONS)
        assertEquals("heatmap_auto_configure", SettingsKeys.HEATMAP_AUTO_CONFIGURE)
        assertEquals("heatmap_remove_transitions", SettingsKeys.HEATMAP_REMOVE_TRANSITIONS)
        assertEquals("heatmap_remove_transitions_mode", SettingsKeys.HEATMAP_REMOVE_TRANSITIONS_MODE)
        assertEquals("heatmap_grid_size", SettingsKeys.HEATMAP_GRID_SIZE)
        assertEquals("heatmap_max_speed", SettingsKeys.HEATMAP_MAX_SPEED)
        assertEquals("max_track_points", SettingsKeys.MAX_TRACK_POINTS)
        assertEquals("max_heatmap_cells", SettingsKeys.MAX_HEATMAP_CELLS)
        assertEquals("heatmap_min_zoom", SettingsKeys.HEATMAP_MIN_ZOOM)
        assertEquals("heatmap_reference_latitude", SettingsKeys.HEATMAP_REFERENCE_LATITUDE)
        assertEquals("heatmap_fade_cell_edges", SettingsKeys.HEATMAP_FADE_CELL_EDGES)
        assertEquals("routes_fade_enabled", SettingsKeys.ROUTES_FADE_ENABLED)
        assertEquals("routes_fade_start_days", SettingsKeys.ROUTES_FADE_START_DAYS)
        assertEquals("routes_fade_full_days", SettingsKeys.ROUTES_FADE_FULL_DAYS)
    }

    @Test
    fun copernicusQuickSelectRequiresCredentials() {
        assertEquals(false, MapSourceQuickSelectPolicy.isAvailable(MapSourceIds.COPERNICUS_S2, "", false, false))
        assertEquals(true, MapSourceQuickSelectPolicy.isAvailable(MapSourceIds.COPERNICUS_S2, "", false, true))
        assertEquals(false, MapSourceQuickSelectPolicy.isAvailable("MML_MAASTO", "", false, false))
        assertEquals(false, MapSourceQuickSelectPolicy.isAvailable("MML_ILMA", "  ", false, true))
        assertEquals(false, MapSourceQuickSelectPolicy.isAvailable("MML_MAASTO", "bad-key", true, false))
        assertEquals(true, MapSourceQuickSelectPolicy.isAvailable("MML_MAASTO", "key", false, false))
        assertEquals(true, MapSourceQuickSelectPolicy.isAvailable("OSM", "", false, false))
        assertEquals(false, MapSourceQuickSelectPolicy.defaultEnabled(MapSourceIds.COPERNICUS_S2, "", false, false))
        assertEquals(true, MapSourceQuickSelectPolicy.defaultEnabled(MapSourceIds.COPERNICUS_S2, "", false, true))
        assertEquals(false, MapSourceQuickSelectPolicy.defaultEnabled("MML_MAASTO", "", false, false))
        assertEquals(false, MapSourceQuickSelectPolicy.defaultEnabled("MML_MAASTO", "  ", false, false))
        assertEquals(false, MapSourceQuickSelectPolicy.defaultEnabled("MML_MAASTO", "bad-key", true, false))
        assertEquals(true, MapSourceQuickSelectPolicy.defaultEnabled("MML_MAASTO", "key", false, false))
        assertEquals(true, MapSourceQuickSelectPolicy.defaultEnabled("OSM", "", false, false))
    }

    @Test
    fun quickMapShortcutRequiresAtLeastTwoAvailableCheckedSources() {
        val sourceIds = listOf("OSM", "MML_MAASTO", "TRAFICOM_SEA")
        val selectedSources = setOf("OSM", "TRAFICOM_SEA")

        val oneEnabledSource = MapSourceQuickSelectPolicy.enabledQuickSelectSources(
            sourceIds,
            "",
            false,
            false
        ) { sourceId, _ -> sourceId == "OSM" }
        assertEquals(listOf("OSM"), oneEnabledSource)
        assertEquals(false, MapSourceQuickSelectPolicy.shouldShowQuickSelect(oneEnabledSource))

        val twoEnabledSources = MapSourceQuickSelectPolicy.enabledQuickSelectSources(
            sourceIds,
            "valid-key",
            false,
            false
        ) { sourceId, _ -> sourceId in selectedSources }
        assertEquals(listOf("OSM", "TRAFICOM_SEA"), twoEnabledSources)
        assertEquals(true, MapSourceQuickSelectPolicy.shouldShowQuickSelect(twoEnabledSources))
    }

    @Test
    fun unavailableSelectedSourceFallsBackToFirstQuickSourceOrOsm() {
        val sources = listOf("OSM", "MML_MAASTO", "MML_ILMA", MapSourceIds.COPERNICUS_S2)
        val available = setOf("OSM", "MML_ILMA", MapSourceIds.COPERNICUS_S2)
        val quickSelected = setOf("MML_ILMA", MapSourceIds.COPERNICUS_S2)

        assertEquals(
            "MML_ILMA",
            MapSourceQuickSelectPolicy.selectedSourceAfterAvailabilityChange(
                "MML_MAASTO",
                sources,
                { it in available },
                { it in quickSelected }
            )
        )
        assertEquals(
            "OSM",
            MapSourceQuickSelectPolicy.selectedSourceAfterAvailabilityChange(
                "MML_MAASTO",
                sources,
                { it == "OSM" },
                { false }
            )
        )
        assertEquals(
            "MML_ILMA",
            MapSourceQuickSelectPolicy.selectedSourceAfterAvailabilityChange(
                "MML_ILMA",
                sources,
                { it in available },
                { false }
            )
        )
    }

    @Test
    fun selectingUnavailableSourceKeepsCurrentDisplayUntilItBecomesAvailable() {
        assertEquals(
            "OSM",
            MapSourceQuickSelectPolicy.sourceToDisplayAfterSelection("MML_MAASTO", "OSM") { false }
        )
        assertEquals(
            "OSM",
            MapSourceQuickSelectPolicy.sourceToDisplayAfterSelection(
                MapSourceIds.COPERNICUS_S2,
                "OSM"
            ) { false }
        )
        assertEquals(
            "MML_MAASTO",
            MapSourceQuickSelectPolicy.sourceToDisplayAfterSelection("MML_MAASTO", "OSM") { true }
        )
    }

    @Test
    fun copernicusDateUsesStableStorageFormatAndDefaultsToToday() {
        assertEquals(true, CopernicusDateSettings.today().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals("2026-09-27", CopernicusDateSettings.format(2026, 8, 27))
        assertEquals("2026-09-27", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(CopernicusDateSettings.calendarFor("2026-09-27").time))
        assertEquals("24.12.2025", CopernicusDateSettings.display("2025-12-24"))
    }

    @Test
    fun copernicusImageSizeNormalizesUnsupportedStoredValuesToDefault() {
        assertEquals(256, CopernicusTileImageSize.normalize(256))
        assertEquals(512, CopernicusTileImageSize.normalize(512))
        assertEquals(1024, CopernicusTileImageSize.normalize(1024))
        assertEquals(2048, CopernicusTileImageSize.normalize(2048))
        assertEquals(1024, CopernicusTileImageSize.normalize(1536))
    }
}
