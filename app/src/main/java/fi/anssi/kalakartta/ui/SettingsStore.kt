package fi.anssi.kalakartta.ui

import android.content.SharedPreferences

/**
 * Tyypitetty rajapinta nykyiseen settings-SharedPreferencesiin.
 *
 * Tallennusavaimet ja oletusarvot ovat SettingsPreferences-objekteissa,
 * jotta niiden yhteensopivuus säilyy yhdessä paikassa.
 */
class SettingsStore(private val preferences: SharedPreferences) {

    var showScaleBar: Boolean
        get() = preferences.getBoolean(SettingsKeys.SHOW_SCALE_BAR, SettingsDefaults.SHOW_SCALE_BAR)
        set(value) { preferences.edit().putBoolean(SettingsKeys.SHOW_SCALE_BAR, value).apply() }

    var showMeasurementTool: Boolean
        get() = preferences.getBoolean(SettingsKeys.SHOW_MEASUREMENT_TOOL, SettingsDefaults.SHOW_MEASUREMENT_TOOL)
        set(value) { preferences.edit().putBoolean(SettingsKeys.SHOW_MEASUREMENT_TOOL, value).apply() }

    var autoCenterOnStart: Boolean
        get() = preferences.getBoolean(SettingsKeys.AUTO_CENTER_ON_START, SettingsDefaults.AUTO_CENTER_ON_START)
        set(value) { preferences.edit().putBoolean(SettingsKeys.AUTO_CENTER_ON_START, value).apply() }

    var mapSource: String
        get() = preferences.getString(SettingsKeys.MAP_SOURCE, SettingsDefaults.MAP_SOURCE) ?: SettingsDefaults.MAP_SOURCE
        set(value) { preferences.edit().putString(SettingsKeys.MAP_SOURCE, value).apply() }

    var mmlApiKey: String
        get() = preferences.getString(SettingsKeys.MML_API_KEY, SettingsDefaults.MML_API_KEY) ?: SettingsDefaults.MML_API_KEY
        set(value) { preferences.edit().putString(SettingsKeys.MML_API_KEY, value).apply() }

    var showQuickMapSource: Boolean
        get() = preferences.getBoolean(SettingsKeys.SHOW_QUICK_MAP_SOURCE, SettingsDefaults.SHOW_QUICK_MAP_SOURCE)
        set(value) { preferences.edit().putBoolean(SettingsKeys.SHOW_QUICK_MAP_SOURCE, value).apply() }

    var fishIconScale: Float
        get() = preferences.getFloat(SettingsKeys.FISH_ICON_SCALE, SettingsDefaults.FISH_ICON_SCALE)
        set(value) { preferences.edit().putFloat(SettingsKeys.FISH_ICON_SCALE, value).apply() }

    var otherIconScale: Float
        get() = preferences.getFloat(SettingsKeys.OTHER_ICON_SCALE, SettingsDefaults.OTHER_ICON_SCALE)
        set(value) { preferences.edit().putFloat(SettingsKeys.OTHER_ICON_SCALE, value).apply() }

    var showLiveSessionRoute: Boolean
        get() = preferences.getBoolean(SettingsKeys.SHOW_LIVE_SESSION_ROUTE, SettingsDefaults.SHOW_LIVE_SESSION_ROUTE)
        set(value) { preferences.edit().putBoolean(SettingsKeys.SHOW_LIVE_SESSION_ROUTE, value).apply() }

    var locationCheckInterval: Int
        get() = preferences.getInt(SettingsKeys.LOCATION_CHECK_INTERVAL, SettingsDefaults.LOCATION_CHECK_INTERVAL)
        set(value) { preferences.edit().putInt(SettingsKeys.LOCATION_CHECK_INTERVAL, value).apply() }

    var minTrackPointInterval: Int
        get() = preferences.getInt(SettingsKeys.MIN_TRACK_POINT_INTERVAL, SettingsDefaults.MIN_TRACK_POINT_INTERVAL)
        set(value) { preferences.edit().putInt(SettingsKeys.MIN_TRACK_POINT_INTERVAL, value).apply() }

    var maxTrackPointInterval: Int
        get() = preferences.getInt(SettingsKeys.MAX_TRACK_POINT_INTERVAL, SettingsDefaults.MAX_TRACK_POINT_INTERVAL)
        set(value) { preferences.edit().putInt(SettingsKeys.MAX_TRACK_POINT_INTERVAL, value).apply() }

    var minTrackPointDistance: Int
        get() = preferences.getInt(SettingsKeys.MIN_TRACK_POINT_DISTANCE, SettingsDefaults.MIN_TRACK_POINT_DISTANCE)
        set(value) { preferences.edit().putInt(SettingsKeys.MIN_TRACK_POINT_DISTANCE, value).apply() }

    var defaultFisherman: String
        get() = preferences.getString(SettingsKeys.DEFAULT_FISHERMAN, SettingsDefaults.DEFAULT_FISHERMAN) ?: SettingsDefaults.DEFAULT_FISHERMAN
        set(value) { preferences.edit().putString(SettingsKeys.DEFAULT_FISHERMAN, value).apply() }

    var showFishermanOnMap: Boolean
        get() = preferences.getBoolean(SettingsKeys.SHOW_FISHERMAN_ON_MAP, SettingsDefaults.SHOW_FISHERMAN_ON_MAP)
        set(value) { preferences.edit().putBoolean(SettingsKeys.SHOW_FISHERMAN_ON_MAP, value).apply() }

    var weatherEnabled: Boolean
        get() = preferences.getBoolean(SettingsKeys.WEATHER_ENABLED, SettingsDefaults.WEATHER_ENABLED)
        set(value) { preferences.edit().putBoolean(SettingsKeys.WEATHER_ENABLED, value).apply() }

    var talkingClockEnabled: Boolean
        get() = preferences.getBoolean(SettingsKeys.TALKING_CLOCK_ENABLED, SettingsDefaults.TALKING_CLOCK_ENABLED)
        set(value) { preferences.edit().putBoolean(SettingsKeys.TALKING_CLOCK_ENABLED, value).apply() }

    var talkingClockOnlyFishing: Boolean
        get() = preferences.getBoolean(SettingsKeys.TALKING_CLOCK_ONLY_FISHING, SettingsDefaults.TALKING_CLOCK_ONLY_FISHING)
        set(value) { preferences.edit().putBoolean(SettingsKeys.TALKING_CLOCK_ONLY_FISHING, value).apply() }

    var talkingClockInterval: Int
        get() = preferences.getInt(SettingsKeys.TALKING_CLOCK_INTERVAL, SettingsDefaults.TALKING_CLOCK_INTERVAL)
        set(value) { preferences.edit().putInt(SettingsKeys.TALKING_CLOCK_INTERVAL, value).apply() }

    var talkingClockSalutation: String
        get() = preferences.getString(SettingsKeys.TALKING_CLOCK_SALUTATION, SettingsDefaults.TALKING_CLOCK_SALUTATION) ?: SettingsDefaults.TALKING_CLOCK_SALUTATION
        set(value) { preferences.edit().putString(SettingsKeys.TALKING_CLOCK_SALUTATION, value).apply() }

    var talkingClockBattery: Boolean
        get() = preferences.getBoolean(SettingsKeys.TALKING_CLOCK_BATTERY, SettingsDefaults.TALKING_CLOCK_BATTERY)
        set(value) { preferences.edit().putBoolean(SettingsKeys.TALKING_CLOCK_BATTERY, value).apply() }

    var talkingClockWeather: Boolean
        get() = preferences.getBoolean(SettingsKeys.TALKING_CLOCK_WEATHER, SettingsDefaults.TALKING_CLOCK_WEATHER)
        set(value) { preferences.edit().putBoolean(SettingsKeys.TALKING_CLOCK_WEATHER, value).apply() }

    var talkingClockSunset: Boolean
        get() = preferences.getBoolean(SettingsKeys.TALKING_CLOCK_SUNSET, SettingsDefaults.TALKING_CLOCK_SUNSET)
        set(value) { preferences.edit().putBoolean(SettingsKeys.TALKING_CLOCK_SUNSET, value).apply() }

    var talkingClockSunrise: Boolean
        get() = preferences.getBoolean(SettingsKeys.TALKING_CLOCK_SUNRISE, SettingsDefaults.TALKING_CLOCK_SUNRISE)
        set(value) { preferences.edit().putBoolean(SettingsKeys.TALKING_CLOCK_SUNRISE, value).apply() }

    var talkingClockSunsetLimit: Int
        get() = preferences.getInt(SettingsKeys.TALKING_CLOCK_SUNSET_LIMIT, SettingsDefaults.TALKING_CLOCK_SUNSET_LIMIT)
        set(value) { preferences.edit().putInt(SettingsKeys.TALKING_CLOCK_SUNSET_LIMIT, value).apply() }

    var talkingClockSunriseLimit: Int
        get() = preferences.getInt(SettingsKeys.TALKING_CLOCK_SUNRISE_LIMIT, SettingsDefaults.TALKING_CLOCK_SUNRISE_LIMIT)
        set(value) { preferences.edit().putInt(SettingsKeys.TALKING_CLOCK_SUNRISE_LIMIT, value).apply() }

    var heatmapEnabled: Boolean
        get() = preferences.getBoolean(SettingsKeys.HEATMAP_ENABLED, SettingsDefaults.HEATMAP_ENABLED)
        set(value) { preferences.edit().putBoolean(SettingsKeys.HEATMAP_ENABLED, value).apply() }

    var fishingRoutesEnabled: Boolean
        get() = preferences.getBoolean(SettingsKeys.FISHING_ROUTES_ENABLED, SettingsDefaults.FISHING_ROUTES_ENABLED)
        set(value) { preferences.edit().putBoolean(SettingsKeys.FISHING_ROUTES_ENABLED, value).apply() }

    var heatmapFilterEnabled: Boolean
        get() = preferences.getBoolean(SettingsKeys.HEATMAP_FILTER_ENABLED, SettingsDefaults.HEATMAP_FILTER_ENABLED)
        set(value) { preferences.edit().putBoolean(SettingsKeys.HEATMAP_FILTER_ENABLED, value).apply() }

    var routesFilterEnabled: Boolean
        get() = preferences.getBoolean(SettingsKeys.ROUTES_FILTER_ENABLED, SettingsDefaults.ROUTES_FILTER_ENABLED)
        set(value) { preferences.edit().putBoolean(SettingsKeys.ROUTES_FILTER_ENABLED, value).apply() }

    var showHeatmapShortcut: Boolean
        get() = preferences.getBoolean(SettingsKeys.SHOW_HEATMAP_SHORTCUT, SettingsDefaults.SHOW_HEATMAP_SHORTCUT)
        set(value) { preferences.edit().putBoolean(SettingsKeys.SHOW_HEATMAP_SHORTCUT, value).apply() }

    var heatmapShortcutMode: Int
        get() = preferences.getInt(SettingsKeys.HEATMAP_SHORTCUT_MODE, SettingsDefaults.HEATMAP_SHORTCUT_MODE)
        set(value) { preferences.edit().putInt(SettingsKeys.HEATMAP_SHORTCUT_MODE, value).apply() }

    var heatmapGridSize: Float
        get() = preferences.getFloat(SettingsKeys.HEATMAP_GRID_SIZE, SettingsDefaults.HEATMAP_GRID_SIZE)
        set(value) { preferences.edit().putFloat(SettingsKeys.HEATMAP_GRID_SIZE, value).apply() }

    var heatmapAutoConfigure: Boolean
        get() = preferences.getBoolean(SettingsKeys.HEATMAP_AUTO_CONFIGURE, SettingsDefaults.HEATMAP_AUTO_CONFIGURE)
        set(value) { preferences.edit().putBoolean(SettingsKeys.HEATMAP_AUTO_CONFIGURE, value).apply() }

    var heatmapMinPoints: Int
        get() = preferences.getInt(SettingsKeys.HEATMAP_MIN_POINTS, SettingsDefaults.HEATMAP_MIN_POINTS)
        set(value) { preferences.edit().putInt(SettingsKeys.HEATMAP_MIN_POINTS, value).apply() }

    var heatmapMinPointsByPoints: Int
        get() = preferences.getInt(SettingsKeys.HEATMAP_MIN_POINTS_BY_POINTS, SettingsDefaults.HEATMAP_MIN_POINTS_BY_POINTS)
        set(value) { preferences.edit().putInt(SettingsKeys.HEATMAP_MIN_POINTS_BY_POINTS, value).apply() }

    var heatmapMinPointsBySessions: Int
        get() = preferences.getInt(SettingsKeys.HEATMAP_MIN_POINTS_BY_SESSIONS, SettingsDefaults.HEATMAP_MIN_POINTS_BY_SESSIONS)
        set(value) { preferences.edit().putInt(SettingsKeys.HEATMAP_MIN_POINTS_BY_SESSIONS, value).apply() }

    var heatmapMaxPoints: Int
        get() = preferences.getInt(SettingsKeys.HEATMAP_MAX_POINTS, SettingsDefaults.HEATMAP_MAX_POINTS)
        set(value) { preferences.edit().putInt(SettingsKeys.HEATMAP_MAX_POINTS, value).apply() }

    var heatmapMaxPointsByPoints: Int
        get() = preferences.getInt(SettingsKeys.HEATMAP_MAX_POINTS_BY_POINTS, SettingsDefaults.HEATMAP_MAX_POINTS_BY_POINTS)
        set(value) { preferences.edit().putInt(SettingsKeys.HEATMAP_MAX_POINTS_BY_POINTS, value).apply() }

    var heatmapMaxPointsBySessions: Int
        get() = preferences.getInt(SettingsKeys.HEATMAP_MAX_POINTS_BY_SESSIONS, SettingsDefaults.HEATMAP_MAX_POINTS_BY_SESSIONS)
        set(value) { preferences.edit().putInt(SettingsKeys.HEATMAP_MAX_POINTS_BY_SESSIONS, value).apply() }

    var routesFadeEnabled: Boolean
        get() = preferences.getBoolean(SettingsKeys.ROUTES_FADE_ENABLED, SettingsDefaults.ROUTES_FADE_ENABLED)
        set(value) { preferences.edit().putBoolean(SettingsKeys.ROUTES_FADE_ENABLED, value).apply() }

    var routesFadeStartDays: Int
        get() = preferences.getInt(SettingsKeys.ROUTES_FADE_START_DAYS, SettingsDefaults.ROUTES_FADE_START_DAYS)
        set(value) { preferences.edit().putInt(SettingsKeys.ROUTES_FADE_START_DAYS, value).apply() }

    var routesFadeFullDays: Int
        get() = preferences.getInt(SettingsKeys.ROUTES_FADE_FULL_DAYS, SettingsDefaults.ROUTES_FADE_FULL_DAYS)
        set(value) { preferences.edit().putInt(SettingsKeys.ROUTES_FADE_FULL_DAYS, value).apply() }

    var heatmapRemoveTransitions: Boolean
        get() = preferences.getBoolean(SettingsKeys.HEATMAP_REMOVE_TRANSITIONS, SettingsDefaults.HEATMAP_REMOVE_TRANSITIONS)
        set(value) { preferences.edit().putBoolean(SettingsKeys.HEATMAP_REMOVE_TRANSITIONS, value).apply() }

    var heatmapRemoveTransitionsMode: Int
        get() = preferences.getInt(SettingsKeys.HEATMAP_REMOVE_TRANSITIONS_MODE, SettingsDefaults.HEATMAP_REMOVE_TRANSITIONS_MODE)
        set(value) { preferences.edit().putInt(SettingsKeys.HEATMAP_REMOVE_TRANSITIONS_MODE, value).apply() }

    var heatmapMaxSpeed: Float
        get() = preferences.getFloat(SettingsKeys.HEATMAP_MAX_SPEED, SettingsDefaults.HEATMAP_MAX_SPEED)
        set(value) { preferences.edit().putFloat(SettingsKeys.HEATMAP_MAX_SPEED, value).apply() }

    var heatmapMinZoom: Float
        get() = preferences.getFloat(SettingsKeys.HEATMAP_MIN_ZOOM, SettingsDefaults.HEATMAP_MIN_ZOOM)
        set(value) { preferences.edit().putFloat(SettingsKeys.HEATMAP_MIN_ZOOM, value).apply() }

    var maxTrackPoints: Int
        get() = preferences.getInt(SettingsKeys.MAX_TRACK_POINTS, SettingsDefaults.MAX_TRACK_POINTS)
        set(value) { preferences.edit().putInt(SettingsKeys.MAX_TRACK_POINTS, value).apply() }

    var maxHeatmapCells: Int
        get() = preferences.getInt(SettingsKeys.MAX_HEATMAP_CELLS, SettingsDefaults.MAX_HEATMAP_CELLS)
        set(value) { preferences.edit().putInt(SettingsKeys.MAX_HEATMAP_CELLS, value).apply() }

    var heatmapReferenceLatitude: Float
        get() = preferences.getFloat(SettingsKeys.HEATMAP_REFERENCE_LATITUDE, SettingsDefaults.HEATMAP_REFERENCE_LATITUDE)
        set(value) { preferences.edit().putFloat(SettingsKeys.HEATMAP_REFERENCE_LATITUDE, value).apply() }

    var pressureTrendThreshold: Float
        get() = preferences.getFloat(SettingsKeys.PRESSURE_TREND_THRESHOLD, SettingsDefaults.PRESSURE_TREND_THRESHOLD)
        set(value) { preferences.edit().putFloat(SettingsKeys.PRESSURE_TREND_THRESHOLD, value).apply() }

    var pressureTurningTrendThreshold: Float
        get() = preferences.getFloat(
            SettingsKeys.PRESSURE_TURNING_TREND_THRESHOLD,
            SettingsDefaults.PRESSURE_TURNING_TREND_THRESHOLD
        )
        set(value) { preferences.edit().putFloat(SettingsKeys.PRESSURE_TURNING_TREND_THRESHOLD, value).apply() }

    var lastVersionName: String
        get() = preferences.getString(SettingsKeys.LAST_VERSION_NAME, SettingsDefaults.LAST_VERSION_NAME)
            ?: SettingsDefaults.LAST_VERSION_NAME
        set(value) { preferences.edit().putString(SettingsKeys.LAST_VERSION_NAME, value).apply() }

    var lastVersionCode: Int
        get() = preferences.getInt(SettingsKeys.LAST_VERSION_CODE, SettingsDefaults.LAST_VERSION_CODE)
        set(value) { preferences.edit().putInt(SettingsKeys.LAST_VERSION_CODE, value).apply() }

    fun getHeatmapColor(default: String): String =
        preferences.getString(SettingsKeys.HEATMAP_COLOR, default) ?: default

    fun setHeatmapColor(value: String) {
        preferences.edit().putString(SettingsKeys.HEATMAP_COLOR, value).apply()
    }

    fun getHeatmapCalculationMethod(default: String): String =
        preferences.getString(SettingsKeys.HEATMAP_CALCULATION_METHOD, default) ?: default

    fun setHeatmapCalculationMethod(value: String) {
        preferences.edit().putString(SettingsKeys.HEATMAP_CALCULATION_METHOD, value).apply()
    }

    fun hasHeatmapShortcutModeSetting(): Boolean =
        preferences.contains(SettingsKeys.HEATMAP_SHORTCUT_MODE)

    fun isQuickMapSourceEnabled(mapSourceId: String, default: Boolean): Boolean =
        preferences.getBoolean(SettingsKeys.quickSelect(mapSourceId), default)

    fun setQuickMapSourceEnabled(mapSourceId: String, enabled: Boolean) {
        preferences.edit().putBoolean(SettingsKeys.quickSelect(mapSourceId), enabled).apply()
    }

    fun hasQuickMapSourceSetting(mapSourceId: String): Boolean =
        preferences.contains(SettingsKeys.quickSelect(mapSourceId))

    fun isTalkingClockWeatherEnabled(hours: Int, default: Boolean = SettingsDefaults.TALKING_CLOCK_WEATHER_OPTION_ENABLED): Boolean =
        preferences.getBoolean(SettingsKeys.talkingClockWeather(hours), default)

    fun setTalkingClockWeatherEnabled(hours: Int, enabled: Boolean) {
        preferences.edit().putBoolean(SettingsKeys.talkingClockWeather(hours), enabled).apply()
    }

    fun hasTalkingClockWeatherSetting(hours: Int): Boolean =
        preferences.contains(SettingsKeys.talkingClockWeather(hours))
}
