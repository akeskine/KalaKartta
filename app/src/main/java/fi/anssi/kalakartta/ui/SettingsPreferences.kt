package fi.anssi.kalakartta.ui

/**
 * SharedPreferences-avaimet ja niiden nykyiset oletusarvot.
 *
 * Avainten arvot ovat osa tallennetun sovellusdatan yhteensopivuussopimusta.
 * Niitä ei pidä nimetä uudelleen ilman erillistä migraatiota.
 */
object SettingsKeys {
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
