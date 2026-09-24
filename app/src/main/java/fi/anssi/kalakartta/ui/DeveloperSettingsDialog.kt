package fi.anssi.kalakartta.ui

import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.service.FishingSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos

/** Kehittäjäasetusten dialogi ja näkyvien piste-/ruutumäärien laskenta. */
class DeveloperSettingsDialog(
    private val activity: AppCompatActivity,
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val onOpenGeneralSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {

    fun show() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val debugCheckbox = CheckBox(activity).apply {
            text = "Kalastussessioiden debug"
            isChecked = FishingSessionService.KALASTUSSESSIOT_DEBUG
            setOnCheckedChangeListener { _, isChecked ->
                FishingSessionService.KALASTUSSESSIOT_DEBUG = isChecked
            }
        }
        layout.addView(debugCheckbox)

        // Rivi 1: Reittipisteitä max
        val row1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val maxPointsLabel = TextView(activity).apply {
            text = "Reittipisteitä max:"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }
        val maxPointsEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(settingsStore.maxTrackPoints.toString())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row1.addView(maxPointsLabel)
        row1.addView(maxPointsEdit)
        layout.addView(row1)

        // Rivi 2: Heat map ruutuja max
        val row2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val maxCellsLabel = TextView(activity).apply {
            text = "Heat map ruutuja max:"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }
        val maxCellsEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(settingsStore.maxHeatmapCells.toString())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row2.addView(maxCellsLabel)
        row2.addView(maxCellsEdit)
        layout.addView(row2)

        // Rivi 3: Heat map zoomaustaso min
        val row3 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val minZoomLabel = TextView(activity).apply {
            text = "Heat map zoomaustaso min:"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }
        val minZoomEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(settingsStore.heatmapMinZoom.toString())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row3.addView(minZoomLabel)
        row3.addView(minZoomEdit)
        layout.addView(row3)

        // Rivi 4: Heatmap referenssileveyspiiri
        val row4 = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        val refLatLabel = TextView(activity).apply {
            text = "Heatmap referenssileveyspiiri (0-180°):"
        }
        val refLatEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(
                settingsStore.heatmapReferenceLatitude.toString()
            )
        }
        val refLatHint = TextView(activity).apply {
            text = "Määrittää pituuspiirien välisen etäisyyden. Oletus: 64.7 (Suomen keskipiste). Arvoalue 0-180."
            textSize = 12f
        }
        row4.addView(refLatLabel)
        row4.addView(refLatEdit)
        row4.addView(refLatHint)
        layout.addView(row4)

        // Rivi 5: Ilmanpaineen kehityksen raja-arvo
        val pressureTrendThresholdRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        val pressureTrendThresholdLabel = TextView(activity).apply {
            text = "Ilmanpaineen kehityksen raja-arvo (hPa/h):"
        }
        val pressureTrendThresholdEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(
                settingsStore.pressureTrendThreshold.toString()
            )
        }
        val pressureTrendThresholdHint = TextView(activity).apply {
            text = "Oletus ${FilterManager.DEFAULT_PRESSURE_TREND_THRESHOLD} (sama raja-arvo laskevalle ja nousevalle)."
            textSize = 12f
        }
        pressureTrendThresholdRow.addView(pressureTrendThresholdLabel)
        pressureTrendThresholdRow.addView(pressureTrendThresholdEdit)
        pressureTrendThresholdRow.addView(pressureTrendThresholdHint)
        layout.addView(pressureTrendThresholdRow)

        // Rivi 6: Ilmanpaineen kehityksen muutoksen raja-arvo
        val pressureTurningTrendThresholdRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        val pressureTurningTrendThresholdLabel = TextView(activity).apply {
            text = "Ilmanpaineen kehityksen muutoksen raja-arvo (hPa/h):"
        }
        val pressureTurningTrendThresholdEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(
                settingsStore.pressureTurningTrendThreshold.toString()
            )
        }
        val pressureTurningTrendThresholdHint = TextView(activity).apply {
            text = "Oletus ${FilterManager.DEFAULT_PRESSURE_TURNING_TREND_THRESHOLD} (sama raja-arvo ala- ja ylöspäin kääntyvälle)."
            textSize = 12f
        }
        pressureTurningTrendThresholdRow.addView(pressureTurningTrendThresholdLabel)
        pressureTurningTrendThresholdRow.addView(pressureTurningTrendThresholdEdit)
        pressureTurningTrendThresholdRow.addView(pressureTurningTrendThresholdHint)
        layout.addView(pressureTurningTrendThresholdRow)

        val seaLevelTrendThresholdRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        val seaLevelTrendThresholdLabel = TextView(activity).apply {
            text = "Meriveden korkeuden muutoksen raja-arvo (cm/h):"
        }
        val seaLevelTrendThresholdEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(settingsStore.seaLevelTrendThreshold.toString())
        }
        val seaLevelTrendThresholdHint = TextView(activity).apply {
            text = "Oletus ${FilterManager.DEFAULT_SEA_LEVEL_TREND_THRESHOLD} (sama raja-arvo laskevalle ja nousevalle)."
            textSize = 12f
        }
        seaLevelTrendThresholdRow.addView(seaLevelTrendThresholdLabel)
        seaLevelTrendThresholdRow.addView(seaLevelTrendThresholdEdit)
        seaLevelTrendThresholdRow.addView(seaLevelTrendThresholdHint)
        layout.addView(seaLevelTrendThresholdRow)

        val seaLevelTurningTrendThresholdRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        val seaLevelTurningTrendThresholdLabel = TextView(activity).apply {
            text = "Meriveden korkeuden kehityksen muutoksen raja-arvo (cm/h):"
        }
        val seaLevelTurningTrendThresholdEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(settingsStore.seaLevelTurningTrendThreshold.toString())
        }
        val seaLevelTurningTrendThresholdHint = TextView(activity).apply {
            text = "Oletus ${FilterManager.DEFAULT_SEA_LEVEL_TURNING_TREND_THRESHOLD} (sama raja-arvo ala- ja ylöspäin kääntyvälle)."
            textSize = 12f
        }
        seaLevelTurningTrendThresholdRow.addView(seaLevelTurningTrendThresholdLabel)
        seaLevelTurningTrendThresholdRow.addView(seaLevelTurningTrendThresholdEdit)
        seaLevelTurningTrendThresholdRow.addView(seaLevelTurningTrendThresholdHint)
        layout.addView(seaLevelTurningTrendThresholdRow)

        // Automaattisen puuttuvien säätietojen päivityksen väli
        val automaticWeatherUpdateIntervalRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        val automaticWeatherUpdateIntervalInputRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val automaticWeatherUpdateIntervalLabel = TextView(activity).apply {
            text = "Automaattisen säätietohaun päivitysväli (h)"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val automaticWeatherUpdateIntervalEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(settingsStore.automaticWeatherUpdateIntervalHours.toString())
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                (80 * activity.resources.displayMetrics.density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val automaticWeatherUpdateIntervalHint = TextView(activity).apply {
            text = "Oletus ${SettingsDefaults.AUTOMATIC_WEATHER_UPDATE_INTERVAL_HOURS} h."
            textSize = 12f
        }
        automaticWeatherUpdateIntervalInputRow.addView(automaticWeatherUpdateIntervalLabel)
        automaticWeatherUpdateIntervalInputRow.addView(automaticWeatherUpdateIntervalEdit)
        automaticWeatherUpdateIntervalRow.addView(automaticWeatherUpdateIntervalInputRow)
        automaticWeatherUpdateIntervalRow.addView(automaticWeatherUpdateIntervalHint)
        layout.addView(automaticWeatherUpdateIntervalRow)

        // Tulostus: Näkyvät määrät
        val statusText = TextView(activity).apply {
            text = "Lasketaan..."
            setPadding(0, 20, 0, 0)
        }
        layout.addView(statusText)

        // Laskenta taustalla
        activity.lifecycleScope.launch(Dispatchers.IO) {
            if (!isActive) return@launch
            val filters = FilterManager(activity).getFilters()
            val heatmapFilterEnabled = settingsStore.heatmapFilterEnabled
            val routesFilterEnabled = settingsStore.routesFilterEnabled
            val removeTransitions = settingsStore.heatmapRemoveTransitions
            val maxSpeed = settingsStore.heatmapMaxSpeed
            val routesFadeEnabled = settingsStore.routesFadeEnabled
            val routesFadeStartDays = settingsStore.routesFadeStartDays.coerceAtLeast(0).toLong()
            val routeSessionStartLimit = if (routesFadeEnabled) {
                val dayMillis = 1000L * 60 * 60 * 24
                System.currentTimeMillis() - (routesFadeStartDays + 1L) * dayMillis
            } else {
                Long.MIN_VALUE
            }
            val removeTransitionsForRoutes = removeTransitions &&
                    settingsStore.heatmapRemoveTransitionsMode == 1
            
            val hasAreaFilter = filters.latNorth != null && filters.latSouth != null && filters.lonEast != null && filters.lonWest != null
            
            // Reittipisteet
            val pointCount = db.trackPointDao().getCountFilteredForRoutes(
                minSessionStart = routeSessionStartLimit,
                checkRange = routesFilterEnabled && (filters.startDate != null || filters.endDate != null),
                startDate = filters.startDate ?: 0L,
                endDate = filters.endDate ?: Long.MAX_VALUE,
                checkArea = routesFilterEnabled && hasAreaFilter,
                latSouth = filters.latSouth ?: 0.0,
                latNorth = filters.latNorth ?: 0.0,
                lonWest = filters.lonWest ?: 0.0,
                lonEast = filters.lonEast ?: 0.0,
                removeTransitions = removeTransitionsForRoutes,
                maxSpeed = maxSpeed
            )

            // Heatmap ruudut
            val gridSize = settingsStore.heatmapGridSize.toDouble().coerceAtLeast(1.0)
            val refLat = settingsStore.heatmapReferenceLatitude.toDouble()
            val latDegreeMeters = 111320.0
            val lonDegreeMeters = latDegreeMeters * cos(Math.toRadians(refLat))
            
            val cellCount = db.trackPointDao().getHeatmapCellCountFiltered(
                checkRange = heatmapFilterEnabled && (filters.startDate != null || filters.endDate != null),
                startDate = filters.startDate ?: 0L,
                endDate = filters.endDate ?: Long.MAX_VALUE,
                checkArea = heatmapFilterEnabled && hasAreaFilter,
                latSouth = filters.latSouth ?: 0.0,
                latNorth = filters.latNorth ?: 0.0,
                lonWest = filters.lonWest ?: 0.0,
                lonEast = filters.lonEast ?: 0.0,
                removeTransitions = removeTransitions,
                maxSpeed = maxSpeed,
                latDegreeMeters = latDegreeMeters,
                lonDegreeMeters = lonDegreeMeters,
                gridSizeMeters = gridSize
            )

            withContext(Dispatchers.Main) {
                statusText.text = "Näkyvät reittpisteet $pointCount, \nNäkyvät heat map-ruudut $cellCount"
            }
        }

        val scrollView = ScrollView(activity).apply {
            isFillViewport = true
            addView(layout)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Kehittäjäasetukset")
            .setView(scrollView)
            .setNegativeButton("Tallenna") { _, _ ->
                val maxPoints = SettingsValueValidator.positiveIntOrDefault(
                    maxPointsEdit.text,
                    SettingsDefaults.MAX_TRACK_POINTS
                )
                val maxCells = SettingsValueValidator.positiveIntOrDefault(
                    maxCellsEdit.text,
                    SettingsDefaults.MAX_HEATMAP_CELLS
                )
                val minZoom = SettingsValueValidator.nonNegativeFloatOrDefault(
                    minZoomEdit.text,
                    SettingsDefaults.HEATMAP_MIN_ZOOM
                )
                val refLat = SettingsValueValidator.latitudeOrDefault(
                    refLatEdit.text,
                    SettingsDefaults.HEATMAP_REFERENCE_LATITUDE
                )
                val pressureTrendThreshold = SettingsValueValidator.positiveFloatOrDefault(
                    pressureTrendThresholdEdit.text,
                    FilterManager.DEFAULT_PRESSURE_TREND_THRESHOLD
                )
                val pressureTurningTrendThreshold = SettingsValueValidator.positiveFloatOrDefault(
                    pressureTurningTrendThresholdEdit.text,
                    FilterManager.DEFAULT_PRESSURE_TURNING_TREND_THRESHOLD
                )
                val seaLevelTrendThreshold = SettingsValueValidator.positiveFloatOrDefault(
                    seaLevelTrendThresholdEdit.text,
                    FilterManager.DEFAULT_SEA_LEVEL_TREND_THRESHOLD
                )
                val seaLevelTurningTrendThreshold = SettingsValueValidator.positiveFloatOrDefault(
                    seaLevelTurningTrendThresholdEdit.text,
                    FilterManager.DEFAULT_SEA_LEVEL_TURNING_TREND_THRESHOLD
                )
                val automaticWeatherUpdateInterval = SettingsValueValidator.positiveIntOrDefault(
                    automaticWeatherUpdateIntervalEdit.text,
                    SettingsDefaults.AUTOMATIC_WEATHER_UPDATE_INTERVAL_HOURS
                )
                
                settingsStore.maxTrackPoints = maxPoints
                settingsStore.maxHeatmapCells = maxCells
                settingsStore.heatmapMinZoom = minZoom
                settingsStore.heatmapReferenceLatitude = refLat
                settingsStore.pressureTrendThreshold = pressureTrendThreshold
                settingsStore.pressureTurningTrendThreshold = pressureTurningTrendThreshold
                settingsStore.seaLevelTrendThreshold = seaLevelTrendThreshold
                settingsStore.seaLevelTurningTrendThreshold = seaLevelTurningTrendThreshold
                settingsStore.automaticWeatherUpdateIntervalHours = automaticWeatherUpdateInterval
                onOpenGeneralSettings()
            }
            .setPositiveButton("Takaisin") { _, _ -> onOpenGeneralSettings() }
            .create()
        onShowDialog(dialog)
    }
}
