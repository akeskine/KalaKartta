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

    fun isQuickMapSourceEnabled(mapSourceId: String, default: Boolean): Boolean =
        preferences.getBoolean(SettingsKeys.quickSelect(mapSourceId), default)

    fun setQuickMapSourceEnabled(mapSourceId: String, enabled: Boolean) {
        preferences.edit().putBoolean(SettingsKeys.quickSelect(mapSourceId), enabled).apply()
    }

    fun hasQuickMapSourceSetting(mapSourceId: String): Boolean =
        preferences.contains(SettingsKeys.quickSelect(mapSourceId))
}
