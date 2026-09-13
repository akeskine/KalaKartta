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
    const val SHOW_QUICK_MAP_SOURCE = "show_quick_map_source"
    const val SHOW_LIVE_SESSION_ROUTE = "show_live_session_route"
    const val LOCATION_CHECK_INTERVAL = "location_check_interval"
    const val MIN_TRACK_POINT_INTERVAL = "min_track_point_interval"
    const val MAX_TRACK_POINT_INTERVAL = "max_track_point_interval"
    const val MIN_TRACK_POINT_DISTANCE = "min_track_point_distance"
    const val DEFAULT_FISHERMAN = "default_fisherman"
    const val SHOW_FISHERMAN_ON_MAP = "show_fisherman_on_map"
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
    const val HEATMAP_AUTO_CONFIGURE = "heatmap_auto_configure"
    const val HEATMAP_REMOVE_TRANSITIONS = "heatmap_remove_transitions"
    const val HEATMAP_REMOVE_TRANSITIONS_MODE = "heatmap_remove_transitions_mode"
    const val HEATMAP_GRID_SIZE = "heatmap_grid_size"
    const val HEATMAP_MAX_SPEED = "heatmap_max_speed"
    const val MAX_TRACK_POINTS = "max_track_points"
    const val MAX_HEATMAP_CELLS = "max_heatmap_cells"
    const val HEATMAP_MIN_ZOOM = "heatmap_min_zoom"
    const val HEATMAP_REFERENCE_LATITUDE = "heatmap_reference_latitude"
    const val ROUTES_FADE_ENABLED = "routes_fade_enabled"
    const val ROUTES_FADE_START_DAYS = "routes_fade_start_days"
    const val ROUTES_FADE_FULL_DAYS = "routes_fade_full_days"

    fun quickSelect(mapSourceId: String): String = "quick_select_$mapSourceId"

    fun talkingClockWeather(hours: Int): String = "talking_clock_weather_${hours}h"
}

object SettingsDefaults {
    const val SHOW_SCALE_BAR = false
    const val SHOW_MEASUREMENT_TOOL = false
    const val AUTO_CENTER_ON_START = true
    const val MAP_SOURCE = "OSM"
    const val MML_API_KEY = ""
    const val SHOW_QUICK_MAP_SOURCE = false
    const val SHOW_LIVE_SESSION_ROUTE = true
    const val LOCATION_CHECK_INTERVAL = 10
    const val MIN_TRACK_POINT_INTERVAL = 30
    const val MAX_TRACK_POINT_INTERVAL = 300
    const val MIN_TRACK_POINT_DISTANCE = 20
    const val DEFAULT_FISHERMAN = ""
    const val SHOW_FISHERMAN_ON_MAP = false
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
    const val HEATMAP_MIN_POINTS = 1
    const val HEATMAP_MAX_POINTS = 50
    const val HEATMAP_MIN_POINTS_BY_POINTS = 1
    const val HEATMAP_MAX_POINTS_BY_POINTS = 50
    const val HEATMAP_MIN_POINTS_BY_SESSIONS = 1
    const val HEATMAP_MAX_POINTS_BY_SESSIONS = 5
    const val HEATMAP_AUTO_CONFIGURE = true
    const val HEATMAP_REMOVE_TRANSITIONS = false
    const val HEATMAP_REMOVE_TRANSITIONS_MODE = 0
    const val HEATMAP_GRID_SIZE = 300.0f
    const val HEATMAP_MAX_SPEED = 10.0f
    const val MAX_TRACK_POINTS = 50_000
    const val MAX_HEATMAP_CELLS = 10_000
    const val HEATMAP_MIN_ZOOM = 10.0f
    const val HEATMAP_REFERENCE_LATITUDE = 64.7f
    const val ROUTES_FADE_ENABLED = true
    const val ROUTES_FADE_START_DAYS = 365
    const val ROUTES_FADE_FULL_DAYS = 30
}
