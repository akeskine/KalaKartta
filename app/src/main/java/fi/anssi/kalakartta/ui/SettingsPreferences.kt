package fi.anssi.kalakartta.ui

/**
 * SharedPreferences-avaimet ja niiden nykyiset oletusarvot.
 *
 * Avainten arvot ovat osa tallennetun sovellusdatan yhteensopivuussopimusta.
 * Niitä ei pidä nimetä uudelleen ilman erillistä migraatiota.
 */
object SettingsKeys {
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
}

object SettingsDefaults {
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
