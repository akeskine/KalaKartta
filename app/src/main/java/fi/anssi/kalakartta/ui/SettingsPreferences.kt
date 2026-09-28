package fi.anssi.kalakartta.ui

/**
 * SharedPreferences-avaimet ja niiden nykyiset oletusarvot.
 *
 * Avainten arvot ovat osa tallennetun sovellusdatan yhteensopivuussopimusta.
 * Niitä ei pidä nimetä uudelleen ilman erillistä migraatiota.
 */
object SettingsKeys {
    const val SHOW_SCALE_BAR = "show_scale_bar"
    const val SHOW_MEASUREMENT_TOOL = "show_measurement_tool"
    const val AUTO_CENTER_ON_START = "auto_center_on_start"
    const val MAP_SOURCE = "map_source"
    const val MML_API_KEY = "mml_api_key"
    const val MML_API_KEY_INVALID = "mml_api_key_invalid"
    const val COPERNICUS_CLIENT_ID = "copernicus_client_id"
    const val COPERNICUS_CUSTOM_DATE_ENABLED = "copernicus_custom_date_enabled"
    const val COPERNICUS_TARGET_DATE = "copernicus_target_date"
    const val COPERNICUS_TILE_CACHE_LIMIT_MB = "copernicus_tile_cache_limit_mb"
    const val SHOW_QUICK_MAP_SOURCE = "show_quick_map_source"
    const val SHOW_LIVE_SESSION_ROUTE = "show_live_session_route"
    const val LOCATION_CHECK_INTERVAL = "location_check_interval"
    const val MIN_TRACK_POINT_INTERVAL = "min_track_point_interval"
    const val MAX_TRACK_POINT_INTERVAL = "max_track_point_interval"
    const val MIN_TRACK_POINT_DISTANCE = "min_track_point_distance"
    const val DEFAULT_FISHERMAN = "default_fisherman"
    const val SHOW_FISHERMAN_ON_MAP = "show_fisherman_on_map"
    const val FISH_ICON_SCALE = "fish_icon_scale"
    const val OTHER_ICON_SCALE = "other_icon_scale"
    const val WEATHER_ENABLED = "weather_enabled"
    const val TALKING_CLOCK_ENABLED = "talking_clock_enabled"
    const val TALKING_CLOCK_ONLY_FISHING = "talking_clock_only_fishing"
    const val TALKING_CLOCK_INTERVAL = "talking_clock_interval"
    const val TALKING_CLOCK_SALUTATION = "talking_clock_salutation"
    const val TALKING_CLOCK_BATTERY = "talking_clock_battery"
    const val TALKING_CLOCK_WEATHER = "talking_clock_weather"
    const val TALKING_CLOCK_SUNSET = "talking_clock_sunset"
    const val TALKING_CLOCK_SUNRISE = "talking_clock_sunrise"
    const val TALKING_CLOCK_SUNSET_LIMIT = "talking_clock_sunset_limit"
    const val TALKING_CLOCK_SUNRISE_LIMIT = "talking_clock_sunrise_limit"
    const val HEATMAP_ENABLED = "heatmap_enabled"
    const val FISHING_ROUTES_ENABLED = "fishing_routes_enabled"
    const val HEATMAP_FILTER_ENABLED = "heatmap_filter_enabled"
    const val ROUTES_FILTER_ENABLED = "routes_filter_enabled"
    const val SHOW_HEATMAP_SHORTCUT = "show_heatmap_shortcut"
    const val HEATMAP_SHORTCUT_MODE = "heatmap_shortcut_mode"
    const val HEATMAP_COLOR = "heatmap_color"
    const val HEATMAP_CALCULATION_METHOD = "heatmap_calculation_method"
    const val HEATMAP_MIN_POINTS = "heatmap_min_points"
    const val HEATMAP_MAX_POINTS = "heatmap_max_points"
    const val HEATMAP_MIN_POINTS_BY_POINTS = "heatmap_min_points_by_points"
    const val HEATMAP_MAX_POINTS_BY_POINTS = "heatmap_max_points_by_points"
    const val HEATMAP_MIN_POINTS_BY_SESSIONS = "heatmap_min_points_by_sessions"
    const val HEATMAP_MAX_POINTS_BY_SESSIONS = "heatmap_max_points_by_sessions"
    const val HEATMAP_MIN_POINTS_BY_POINTS_AND_SESSIONS = "heatmap_min_points_by_points_and_sessions"
    const val HEATMAP_MAX_POINTS_BY_POINTS_AND_SESSIONS = "heatmap_max_points_by_points_and_sessions"
    const val HEATMAP_AUTO_CONFIGURE = "heatmap_auto_configure"
    const val HEATMAP_REMOVE_TRANSITIONS = "heatmap_remove_transitions"
    const val HEATMAP_REMOVE_TRANSITIONS_MODE = "heatmap_remove_transitions_mode"
    const val HEATMAP_GRID_SIZE = "heatmap_grid_size"
    const val HEATMAP_MAX_SPEED = "heatmap_max_speed"
    const val MAX_TRACK_POINTS = "max_track_points"
    const val MAX_HEATMAP_CELLS = "max_heatmap_cells"
    const val HEATMAP_MIN_ZOOM = "heatmap_min_zoom"
    const val HEATMAP_REFERENCE_LATITUDE = "heatmap_reference_latitude"
    const val HEATMAP_FADE_CELL_EDGES = "heatmap_fade_cell_edges"
    const val ROUTES_FADE_ENABLED = "routes_fade_enabled"
    const val ROUTES_FADE_START_DAYS = "routes_fade_start_days"
    const val ROUTES_FADE_FULL_DAYS = "routes_fade_full_days"
    const val PRESSURE_TREND_THRESHOLD = "pressure_trend_threshold"
    const val PRESSURE_TURNING_TREND_THRESHOLD = "pressure_turning_trend_threshold"
    const val SEA_LEVEL_TREND_THRESHOLD = "sea_level_trend_threshold"
    const val SEA_LEVEL_TURNING_TREND_THRESHOLD = "sea_level_turning_trend_threshold"
    const val AUTOMATIC_MISSING_WEATHER_UPDATE = "automatic_missing_weather_update"
    const val LAST_MISSING_WEATHER_UPDATE_AT = "last_missing_weather_update_at"
    const val AUTOMATIC_WEATHER_UPDATE_INTERVAL_HOURS = "automatic_weather_update_interval_hours"
    const val LAST_VERSION_NAME = "last_version_name"
    const val LAST_VERSION_CODE = "last_version_code"

    fun quickSelect(mapSourceId: String): String = "quick_select_$mapSourceId"

    fun talkingClockWeather(hours: Int): String = "talking_clock_weather_${hours}h"
}

object SettingsDefaults {
    const val SHOW_SCALE_BAR = false
    const val SHOW_MEASUREMENT_TOOL = false
    const val AUTO_CENTER_ON_START = true
    const val MAP_SOURCE = "OSM"
    const val MML_API_KEY = ""
    const val MML_API_KEY_INVALID = false
    const val COPERNICUS_CLIENT_ID = ""
    const val COPERNICUS_CUSTOM_DATE_ENABLED = false
    const val COPERNICUS_TILE_CACHE_LIMIT_MB = 512
    const val MIN_COPERNICUS_TILE_CACHE_LIMIT_MB = 64
    const val MAX_COPERNICUS_TILE_CACHE_LIMIT_MB = 4096
    const val SHOW_QUICK_MAP_SOURCE = false
    const val SHOW_LIVE_SESSION_ROUTE = true
    const val LOCATION_CHECK_INTERVAL = 10
    const val MIN_TRACK_POINT_INTERVAL = 30
    const val MAX_TRACK_POINT_INTERVAL = 300
    const val MIN_TRACK_POINT_DISTANCE = 20
    const val DEFAULT_FISHERMAN = ""
    const val SHOW_FISHERMAN_ON_MAP = false
    const val FISH_ICON_SCALE = 1.0f
    const val OTHER_ICON_SCALE = 0.75f
    const val WEATHER_ENABLED = true
    const val TALKING_CLOCK_ENABLED = false
    const val TALKING_CLOCK_ONLY_FISHING = false
    const val TALKING_CLOCK_INTERVAL = 30
    const val TALKING_CLOCK_SALUTATION = ""
    const val TALKING_CLOCK_BATTERY = false
    const val TALKING_CLOCK_WEATHER = false
    const val TALKING_CLOCK_WEATHER_OPTION_ENABLED = false
    const val TALKING_CLOCK_SUNSET = false
    const val TALKING_CLOCK_SUNRISE = false
    const val TALKING_CLOCK_SUNSET_LIMIT = 2
    const val TALKING_CLOCK_SUNRISE_LIMIT = 2
    const val TALKING_CLOCK_WEATHER_DEFAULT_HOURS = 3
    const val HEATMAP_ENABLED = false
    const val FISHING_ROUTES_ENABLED = false
    const val HEATMAP_FILTER_ENABLED = false
    const val ROUTES_FILTER_ENABLED = false
    const val SHOW_HEATMAP_SHORTCUT = false
    const val HEATMAP_SHORTCUT_MODE = 0
    const val HEATMAP_CALCULATION_METHOD = "Reittipisteet + 3x sessiot"
    const val HEATMAP_MIN_POINTS = 1
    const val HEATMAP_MAX_POINTS = 50
    const val HEATMAP_MIN_POINTS_BY_POINTS = 1
    const val HEATMAP_MAX_POINTS_BY_POINTS = 50
    const val HEATMAP_MIN_POINTS_BY_SESSIONS = 1
    const val HEATMAP_MAX_POINTS_BY_SESSIONS = 5
    const val HEATMAP_MIN_POINTS_BY_POINTS_AND_SESSIONS = 1
    const val HEATMAP_MAX_POINTS_BY_POINTS_AND_SESSIONS = 50
    const val HEATMAP_AUTO_CONFIGURE = true
    const val HEATMAP_REMOVE_TRANSITIONS = true
    const val HEATMAP_REMOVE_TRANSITIONS_MODE = 0
    const val HEATMAP_GRID_SIZE = 100.0f
    const val HEATMAP_MAX_SPEED = 10.0f
    const val MAX_TRACK_POINTS = 50_000
    const val MAX_HEATMAP_CELLS = 10_000
    const val HEATMAP_MIN_ZOOM = 10.0f
    const val HEATMAP_REFERENCE_LATITUDE = 64.7f
    const val HEATMAP_FADE_CELL_EDGES = true
    const val ROUTES_FADE_ENABLED = true
    const val ROUTES_FADE_START_DAYS = 365
    const val ROUTES_FADE_FULL_DAYS = 30
    const val PRESSURE_TREND_THRESHOLD = 0.20f
    const val PRESSURE_TURNING_TREND_THRESHOLD = 0.20f
    const val SEA_LEVEL_TREND_THRESHOLD = 1.0f
    const val SEA_LEVEL_TURNING_TREND_THRESHOLD = 3.0f
    const val AUTOMATIC_MISSING_WEATHER_UPDATE = false
    const val LAST_MISSING_WEATHER_UPDATE_AT = 0L
    const val AUTOMATIC_WEATHER_UPDATE_INTERVAL_HOURS = 6
    const val LAST_VERSION_NAME = ""
    const val LAST_VERSION_CODE = -1
}

object MapSourceIds {
    const val COPERNICUS_S2 = "COPERNICUS_S2"
}

object MapSourceQuickSelectPolicy {
    fun isAvailable(
        sourceId: String,
        mmlApiKey: String,
        mmlApiKeyInvalid: Boolean,
        copernicusCredentialsAvailable: Boolean
    ): Boolean = when {
        sourceId.startsWith("MML_") -> mmlApiKey.isNotBlank() && !mmlApiKeyInvalid
        sourceId == MapSourceIds.COPERNICUS_S2 -> copernicusCredentialsAvailable
        else -> true
    }

    fun defaultEnabled(
        sourceId: String,
        mmlApiKey: String,
        mmlApiKeyInvalid: Boolean,
        copernicusCredentialsAvailable: Boolean
    ): Boolean = when {
        sourceId.startsWith("MML_") -> mmlApiKey.isNotBlank() && !mmlApiKeyInvalid
        sourceId == MapSourceIds.COPERNICUS_S2 -> copernicusCredentialsAvailable
        else -> true
    }

    fun selectedSourceAfterAvailabilityChange(
        currentSourceId: String,
        orderedSourceIds: List<String>,
        isAvailable: (String) -> Boolean,
        isQuickSelectEnabled: (String) -> Boolean
    ): String {
        if (isAvailable(currentSourceId)) return currentSourceId
        return orderedSourceIds.firstOrNull { isAvailable(it) && isQuickSelectEnabled(it) } ?: "OSM"
    }

    fun sourceToDisplayAfterSelection(
        requestedSourceId: String,
        currentSourceId: String,
        isAvailable: (String) -> Boolean
    ): String = if (isAvailable(requestedSourceId)) requestedSourceId else currentSourceId
}

object CopernicusDateSettings {
    private const val STORAGE_FORMAT = "yyyy-MM-dd"

    fun today(): String = java.text.SimpleDateFormat(STORAGE_FORMAT, java.util.Locale.US)
        .format(java.util.Date())

    fun format(year: Int, month: Int, day: Int): String =
        String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day)

    fun calendarFor(value: String): java.util.Calendar {
        val calendar = java.util.Calendar.getInstance()
        val parts = value.split('-').mapNotNull { it.toIntOrNull() }
        if (parts.size == 3) {
            calendar.set(java.util.Calendar.YEAR, parts[0])
            calendar.set(java.util.Calendar.MONTH, parts[1] - 1)
            calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2])
        }
        return calendar
    }

    fun display(value: String): String = java.text.SimpleDateFormat("d.M.yyyy", java.util.Locale.getDefault())
        .format(calendarFor(value).time)
}
