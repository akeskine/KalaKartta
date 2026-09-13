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
