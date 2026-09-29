package fi.anssi.kalakartta.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay
import android.os.Handler
import android.os.Looper
import android.content.res.ColorStateList
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.map.CopernicusTilesOverlay
import fi.anssi.kalakartta.map.mapBaseTileSource
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.utils.CopernicusCatalogClient
import fi.anssi.kalakartta.utils.CopernicusSceneDateResolver
import fi.anssi.kalakartta.utils.MgrsTileIdResolver
import fi.anssi.kalakartta.utils.SharedPreferencesCopernicusSceneDateCacheStore
import fi.anssi.kalakartta.utils.CopernicusTileSource
import fi.anssi.kalakartta.utils.MMLTileSource
import fi.anssi.kalakartta.utils.TraficomTileSource
import fi.anssi.kalakartta.utils.VeneilykarttaTileSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns map presentation settings that are independent of marker data. */
class MapDisplayController(
    private val activity: AppCompatActivity,
    private val map: MapView,
    private val settingsStore: SettingsStore,
    private val database: AppDatabase,
    private val measurementPointCount: () -> Int,
    private val clearMeasurement: () -> Unit,
    private val onDefaultFishermanChanged: () -> Unit
) {
    private var scaleBarOverlay: ScaleBarOverlay? = null
    private var heatmapOverlay: FishingHeatmapOverlay? = null
    private val mgrsTileIdResolver = MgrsTileIdResolver()
    private val copernicusSceneDateResolver by lazy {
        CopernicusSceneDateResolver(
            CopernicusCatalogClient(),
            SharedPreferencesCopernicusSceneDateCacheStore(activity)
        )
    }
    private var copernicusSceneDate = CopernicusTileSource.INITIAL_IMAGE_DATE
    private var copernicusSceneAvailable = true
    private var copernicusSceneKey: String? = null
    private var pendingCopernicusSceneKey: String? = null
    private var resolvedCopernicusSceneKey: String? = null
    private var copernicusSceneResolutionJob: Job? = null
    private var pendingCopernicusFallbackKey: String? = null
    private var attemptedCopernicusFallbackKey: String? = null
    private var copernicusFallbackJob: Job? = null
    private var lastCopernicusCatalogFailureKey: String? = null
    private var copernicusTilesOverlay: CopernicusTilesOverlay? = null
    private val heatmapUpdateHandler = Handler(Looper.getMainLooper())
    private val heatmapUpdateRunnable = Runnable {
        heatmapOverlay?.refreshData(map.boundingBox)
    }

    fun updateMapTileSource() {
        when (settingsStore.mapSource) {
            "TRAFICOM_SEA" -> {
                setMapTileSource(TraficomTileSource())
                updateUIColors(useBlack = true)
            }
            "TRAFICOM_BOATING" -> {
                setMapTileSource(VeneilykarttaTileSource())
                updateUIColors(useBlack = true)
            }
            "MML_MAASTO" -> {
                setMapTileSource(MMLTileSource("MML Maastokartta", "maastokartta", settingsStore.mmlApiKey))
                updateUIColors(useBlack = true)
            }
            "MML_ILMA" -> {
                setMapTileSource(MMLTileSource("MML Ilmakuva", "ortokuva", settingsStore.mmlApiKey))
                updateUIColors(useBlack = true)
            }
            MapSourceIds.COPERNICUS_S2 -> {
                setMapTileSource(createCopernicusTileSource(copernicusSceneDate, copernicusSceneAvailable))
                updateUIColors(useBlack = false)
            }
            else -> {
                setMapTileSource(TileSourceFactory.MAPNIK)
                updateUIColors(useBlack = false)
            }
        }
        updateCopernicusDateOverlay()
        updateScaleBar()
        if (settingsStore.mapSource == MapSourceIds.COPERNICUS_S2) {
            updateCopernicusSceneDateForCenter()
        } else {
            copernicusSceneResolutionJob?.cancel()
            copernicusSceneResolutionJob = null
            pendingCopernicusSceneKey = null
            resolvedCopernicusSceneKey = null
            cancelCopernicusFallback()
        }
    }

    fun onMapCenterChanged() {
        updateCopernicusSceneDateForCenter()
    }

    private fun setMapTileSource(tileSource: ITileSource) {
        val copernicusTileSource = tileSource as? CopernicusTileSource
        if (copernicusTileSource != null) {
            val overlay = copernicusTilesOverlay ?: CopernicusTilesOverlay(
                activity.applicationContext,
                settingsStore,
                map
            ).also { copernicusTilesOverlay = it }
            overlay.setCopernicusTileSource(copernicusTileSource)
            if (overlay !in map.overlays) {
                map.overlays.add(0, overlay)
            }
        } else {
            removeCopernicusTileOverlay()
        }

        val baseTileSource = mapBaseTileSource(tileSource)
        if (map.tileProvider.tileSource !== baseTileSource) {
            map.setTileSource(baseTileSource)
        }
        map.invalidate()
    }

    private fun removeCopernicusTileOverlay() {
        copernicusTilesOverlay?.let { overlay ->
            map.overlays.remove(overlay)
            overlay.onDetach(map)
            copernicusTilesOverlay = null
        }
    }

    private fun updateCopernicusSceneDateForCenter() {
        if (settingsStore.mapSource != MapSourceIds.COPERNICUS_S2) return
        val targetDate = if (settingsStore.copernicusCustomDateEnabled) {
            settingsStore.copernicusTargetDate
        } else {
            CopernicusDateSettings.today()
        }
        if (settingsStore.copernicusClientId.isBlank() || !CopernicusCredentialStore(activity).hasClientSecret()) {
            val missingCredentialsKey = "credentials|$targetDate"
            cancelCopernicusFallback()
            copernicusSceneResolutionJob?.cancel()
            copernicusSceneResolutionJob = null
            pendingCopernicusSceneKey = null
            if (resolvedCopernicusSceneKey != missingCredentialsKey) {
                resolvedCopernicusSceneKey = missingCredentialsKey
                setCopernicusScene(targetDate, sceneAvailable = false, sceneKey = missingCredentialsKey)
                Toast.makeText(activity, R.string.copernicus_credentials_required, Toast.LENGTH_LONG).show()
            }
            return
        }
        val center = map.mapCenter
        val tileId = try {
            mgrsTileIdResolver.resolve(center.latitude, center.longitude)
        } catch (_: IllegalArgumentException) {
            val unsupportedCenterKey = "unsupported|$targetDate"
            cancelCopernicusFallback()
            copernicusSceneResolutionJob?.cancel()
            copernicusSceneResolutionJob = null
            pendingCopernicusSceneKey = null
            if (resolvedCopernicusSceneKey != unsupportedCenterKey) {
                resolvedCopernicusSceneKey = unsupportedCenterKey
                setCopernicusScene(targetDate, sceneAvailable = false, sceneKey = unsupportedCenterKey)
                Toast.makeText(activity, R.string.copernicus_location_unsupported, Toast.LENGTH_LONG).show()
            }
            return
        }
        val requestKey = "$tileId|$targetDate"
        if (requestKey == pendingCopernicusSceneKey || requestKey == resolvedCopernicusSceneKey) return

        if (requestKey != copernicusSceneKey) cancelCopernicusFallback()
        pendingCopernicusSceneKey = requestKey
        copernicusSceneResolutionJob?.cancel()
        if (copernicusSceneKey != requestKey || !copernicusSceneAvailable) {
            setCopernicusScene(targetDate, sceneAvailable = false, sceneKey = requestKey)
        }

        copernicusSceneResolutionJob = activity.lifecycleScope.launch {
            delay(SCENE_DATE_LOOKUP_DEBOUNCE_MILLIS)
            try {
                val sceneDate = withContext(Dispatchers.IO) {
                    copernicusSceneDateResolver.resolveSceneDate(tileId, targetDate)
                }
                if (settingsStore.mapSource != MapSourceIds.COPERNICUS_S2 || pendingCopernicusSceneKey != requestKey) {
                    return@launch
                }

                val latestCenter = map.mapCenter
                val latestTileId = try {
                    mgrsTileIdResolver.resolve(latestCenter.latitude, latestCenter.longitude)
                } catch (_: IllegalArgumentException) {
                    return@launch
                }
                val latestTargetDate = if (settingsStore.copernicusCustomDateEnabled) {
                    settingsStore.copernicusTargetDate
                } else {
                    CopernicusDateSettings.today()
                }
                val latestKey = "$latestTileId|$latestTargetDate"
                if (latestKey != requestKey) {
                    pendingCopernicusSceneKey = null
                    updateCopernicusSceneDateForCenter()
                    return@launch
                }

                pendingCopernicusSceneKey = null
                resolvedCopernicusSceneKey = requestKey
                if (sceneDate == null) {
                    setCopernicusScene(targetDate, sceneAvailable = false, sceneKey = requestKey)
                    if (lastCopernicusCatalogFailureKey != requestKey) {
                        lastCopernicusCatalogFailureKey = requestKey
                        Toast.makeText(activity, R.string.copernicus_scene_not_found, Toast.LENGTH_LONG).show()
                    }
                    delay(SCENE_DATE_NO_SCENE_RETRY_MILLIS)
                    if (settingsStore.mapSource == MapSourceIds.COPERNICUS_S2 &&
                        pendingCopernicusSceneKey == null && resolvedCopernicusSceneKey == requestKey
                    ) {
                        resolvedCopernicusSceneKey = null
                        updateCopernicusSceneDateForCenter()
                    }
                } else {
                    lastCopernicusCatalogFailureKey = null
                    setCopernicusScene(sceneDate, sceneAvailable = true, sceneKey = requestKey)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (pendingCopernicusSceneKey == requestKey) {
                    android.util.Log.w(
                        "CopernicusCatalog",
                        "Scene lookup failed for $tileId before $targetDate: " +
                                (failure.message ?: failure.javaClass.simpleName)
                    )
                    pendingCopernicusSceneKey = null
                    setCopernicusScene(targetDate, sceneAvailable = false, sceneKey = requestKey)
                    if (lastCopernicusCatalogFailureKey != requestKey) {
                        lastCopernicusCatalogFailureKey = requestKey
                        Toast.makeText(activity, R.string.copernicus_catalog_unavailable, Toast.LENGTH_LONG).show()
                    }
                    delay(SCENE_DATE_LOOKUP_RETRY_MILLIS)
                    if (settingsStore.mapSource == MapSourceIds.COPERNICUS_S2 &&
                        pendingCopernicusSceneKey == null
                    ) {
                        updateCopernicusSceneDateForCenter()
                    }
                }
            }
        }
    }

    private fun setCopernicusScene(imageDate: String, sceneAvailable: Boolean, sceneKey: String) {
        if (copernicusSceneDate == imageDate && copernicusSceneAvailable == sceneAvailable && copernicusSceneKey == sceneKey) {
            return
        }
        copernicusSceneDate = imageDate
        copernicusSceneAvailable = sceneAvailable
        copernicusSceneKey = sceneKey
        if (settingsStore.mapSource == MapSourceIds.COPERNICUS_S2) {
            setMapTileSource(createCopernicusTileSource(imageDate, sceneAvailable))
        }
        updateCopernicusDateOverlay()
    }

    private fun createCopernicusTileSource(imageDate: String, sceneAvailable: Boolean) =
        CopernicusTileSource(imageDate, sceneAvailable) { blackSceneDate ->
            activity.runOnUiThread { requestOlderSceneForBlackTile(blackSceneDate) }
        }

    private fun requestOlderSceneForBlackTile(blackSceneDate: String) {
        if (settingsStore.mapSource != MapSourceIds.COPERNICUS_S2 ||
            !copernicusSceneAvailable || copernicusSceneDate != blackSceneDate
        ) return

        val sceneKey = copernicusSceneKey ?: return
        val center = map.mapCenter
        val tileId = try {
            mgrsTileIdResolver.resolve(center.latitude, center.longitude)
        } catch (_: IllegalArgumentException) {
            return
        }
        val targetDate = if (settingsStore.copernicusCustomDateEnabled) {
            settingsStore.copernicusTargetDate
        } else {
            CopernicusDateSettings.today()
        }
        if (sceneKey != "$tileId|$targetDate") return

        val fallbackKey = "$sceneKey|$blackSceneDate"
        if (pendingCopernicusFallbackKey == fallbackKey || attemptedCopernicusFallbackKey == fallbackKey) return

        pendingCopernicusFallbackKey = fallbackKey
        copernicusFallbackJob?.cancel()
        copernicusFallbackJob = activity.lifecycleScope.launch {
            try {
                val previousSceneDate = withContext(Dispatchers.IO) {
                    copernicusSceneDateResolver.resolvePreviousSceneDate(tileId, blackSceneDate)
                }
                if (pendingCopernicusFallbackKey != fallbackKey) return@launch
                if (!isCurrentCopernicusFallback(tileId, sceneKey, blackSceneDate)) {
                    pendingCopernicusFallbackKey = null
                    return@launch
                }

                pendingCopernicusFallbackKey = null
                attemptedCopernicusFallbackKey = fallbackKey
                if (previousSceneDate != null) {
                    setCopernicusScene(previousSceneDate, sceneAvailable = true, sceneKey = sceneKey)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (pendingCopernicusFallbackKey == fallbackKey) {
                    android.util.Log.w(
                        "CopernicusCatalog",
                        "Previous-scene lookup failed for $tileId before $blackSceneDate: " +
                                (failure.message ?: failure.javaClass.simpleName)
                    )
                    delay(SCENE_DATE_LOOKUP_RETRY_MILLIS)
                    if (pendingCopernicusFallbackKey == fallbackKey &&
                        isCurrentCopernicusFallback(tileId, sceneKey, blackSceneDate)
                    ) {
                        pendingCopernicusFallbackKey = null
                        requestOlderSceneForBlackTile(blackSceneDate)
                    } else if (pendingCopernicusFallbackKey == fallbackKey) {
                        pendingCopernicusFallbackKey = null
                    }
                }
            }
        }
    }

    private fun isCurrentCopernicusFallback(tileId: String, sceneKey: String, imageDate: String): Boolean {
        if (settingsStore.mapSource != MapSourceIds.COPERNICUS_S2 ||
            copernicusSceneKey != sceneKey || copernicusSceneDate != imageDate || !copernicusSceneAvailable
        ) return false

        val center = map.mapCenter
        val currentTileId = try {
            mgrsTileIdResolver.resolve(center.latitude, center.longitude)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val targetDate = if (settingsStore.copernicusCustomDateEnabled) {
            settingsStore.copernicusTargetDate
        } else {
            CopernicusDateSettings.today()
        }
        return currentTileId == tileId && sceneKey == "$tileId|$targetDate"
    }

    private fun cancelCopernicusFallback() {
        copernicusFallbackJob?.cancel()
        copernicusFallbackJob = null
        pendingCopernicusFallbackKey = null
        attemptedCopernicusFallbackKey = null
    }

    private fun updateCopernicusDateOverlay() {
        val dateText = activity.findViewById<TextView>(R.id.copernicusDateText) ?: return
        if (settingsStore.mapSource != MapSourceIds.COPERNICUS_S2 || !copernicusSceneAvailable || copernicusSceneKey == null) {
            dateText.visibility = View.GONE
            return
        }

        dateText.text = activity.getString(
            R.string.copernicus_image_date,
            CopernicusDateSettings.display(copernicusSceneDate)
        )
        dateText.visibility = View.VISIBLE
    }

    fun updateScaleBar() {
        val showScale = settingsStore.showScaleBar
        val showMeasurement = settingsStore.showMeasurementTool
        val showQuickMap = settingsStore.showQuickMapSource
        val useBlack = usesBlackMapControls(settingsStore.mapSource)

        val measurementButton = activity.findViewById<MaterialButton>(R.id.measurementButton)
        val undoMeasurementButton = activity.findViewById<MaterialButton>(R.id.undoMeasurementButton)
        if (showMeasurement) {
            measurementButton.visibility = View.VISIBLE
            undoMeasurementButton.visibility = if (measurementPointCount() > 0) View.VISIBLE else View.GONE
        } else {
            measurementButton.visibility = View.GONE
            undoMeasurementButton.visibility = View.GONE
            if (measurementPointCount() > 0) {
                clearMeasurement()
            }
        }

        val quickMapSourceButton = activity.findViewById<MaterialButton>(R.id.quickMapSourceButton)
        quickMapSourceButton.visibility = if (showQuickMap) View.VISIBLE else View.GONE

        scaleBarOverlay?.let { map.overlays.remove(it) }
        if (showScale) {
            val density = activity.resources.displayMetrics.density
            val buttonMargin = activity.resources.getDimensionPixelSize(R.dimen.button_margin_bottom)
            val targetWidth = (48 * density).toInt()
            val color = controlColor(useBlack)

            val scaleBar = object : ScaleBarOverlay(map) {
                override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                    if (shadow) return
                    try {
                        val fields = listOf("mLineWidth", "lineWidth", "mMinWidth", "minWidth", "mMaxWidth", "maxWidth")
                        for (name in fields) {
                            try {
                                val field = ScaleBarOverlay::class.java.getDeclaredField(name)
                                field.isAccessible = true
                                field.set(this, targetWidth)
                            } catch (_: NoSuchFieldException) {
                            }
                        }
                    } catch (_: Exception) {
                    }
                    super.draw(canvas, mapView, shadow)
                }
            }.apply {
                setAlignBottom(true)
                val yOffset = buttonMargin - (22 * density).toInt()
                setScaleBarOffset(60, yOffset)
                setTextSize(density * 12)
                barPaint.color = color
                textPaint.color = color
            }
            map.overlays.add(scaleBar)
            scaleBarOverlay = scaleBar
        } else {
            scaleBarOverlay = null
        }

        val baseMargin = activity.resources.getDimensionPixelSize(R.dimen.button_margin_bottom)
        activity.findViewById<MaterialButton>(R.id.myLocationButton).apply {
            layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { bottomMargin = baseMargin }
            requestLayout()
        }
        activity.findViewById<MaterialButton>(R.id.addCatchButton).apply {
            layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { bottomMargin = baseMargin }
            requestLayout()
        }

        onDefaultFishermanChanged()
        val shortcutMode = settingsStore.heatmapShortcutMode
        val heatmapShortcutButton = activity.findViewById<MaterialButton>(R.id.heatmapShortcutButton)
        heatmapShortcutButton.visibility = if (shortcutMode > 0) View.VISIBLE else View.GONE
        val quickMapLayoutParams = quickMapSourceButton.layoutParams as FrameLayout.LayoutParams
        val shortcutLayoutParams = heatmapShortcutButton.layoutParams as FrameLayout.LayoutParams
        val shortcutOffset = (70 * activity.resources.displayMetrics.density).toInt()
        shortcutLayoutParams.topMargin = if (showQuickMap) {
            quickMapLayoutParams.topMargin + shortcutOffset
        } else {
            quickMapLayoutParams.topMargin
        }
        heatmapShortcutButton.layoutParams = shortcutLayoutParams

        updateFishingHeatmap()
        map.invalidate()
    }

    fun updateUIColors(useBlack: Boolean) {
        val color = controlColor(useBlack)
        activity.findViewById<TextView>(R.id.mapCrosshair).setTextColor(color)

        val buttons = listOf(
            activity.findViewById<MaterialButton>(R.id.myLocationButton),
            activity.findViewById<MaterialButton>(R.id.addCatchButton),
            activity.findViewById<MaterialButton>(R.id.settingsButton),
            activity.findViewById<MaterialButton>(R.id.quickMapSourceButton),
            activity.findViewById<MaterialButton>(R.id.heatmapShortcutButton),
            activity.findViewById<MaterialButton>(R.id.measurementButton),
            activity.findViewById<MaterialButton>(R.id.undoMeasurementButton)
        )

        buttons.forEach { button ->
            if (button.id == R.id.measurementButton) {
                button.icon = BitmapDrawable(activity.resources, createMeasurementPinBitmap(color))
            }
            button.iconTint = ColorStateList.valueOf(color)
            button.strokeColor = ColorStateList.valueOf(color)
        }
    }

    fun createMeasurementPinBitmap(color: Int): Bitmap {
        val size = (32 * activity.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        canvas.drawCircle(size / 2f, size / 4f, size / 8f, paint)
        val startY = size / 4f + size / 8f
        val originalLength = size * 0.9f - (size / 3f + size / 4f)
        val endY = startY + originalLength * 1.3f
        paint.strokeWidth = size / 10f
        canvas.drawLine(size / 2f, startY, size / 2f, endY, paint)
        return bitmap
    }

    fun updateFishingHeatmap() {
        val heatmapEnabled = settingsStore.heatmapEnabled
        val routesEnabled = settingsStore.fishingRoutesEnabled

        if (heatmapEnabled || routesEnabled) {
            if (heatmapOverlay == null) {
                heatmapOverlay = FishingHeatmapOverlay(activity, database, map)
                map.overlays.add(0, heatmapOverlay)
            } else {
                heatmapOverlay?.refreshData()
            }
        } else {
            heatmapOverlay?.let {
                map.overlays.remove(it)
                heatmapOverlay = null
            }
        }

        val shortcutButton = activity.findViewById<MaterialButton>(R.id.heatmapShortcutButton)
        if (heatmapEnabled || routesEnabled) {
            val baseColor = when (settingsStore.getHeatmapColor("Punainen")) {
                "Violetti" -> Color.rgb(128, 0, 128)
                "Vihreä" -> Color.GREEN
                else -> Color.RED
            }
            val alpha = if (heatmapEnabled && routesEnabled) 160 else 80
            val shortcutColor = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            shortcutButton.backgroundTintList = ColorStateList.valueOf(shortcutColor)
            shortcutButton.strokeColor = ColorStateList.valueOf(baseColor)
        } else {
            shortcutButton.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            shortcutButton.strokeColor = ColorStateList.valueOf(controlColor(usesBlackMapControls(settingsStore.mapSource)))
        }
        map.invalidate()
    }

    fun updateHeatmapDelayed() {
        heatmapUpdateHandler.removeCallbacks(heatmapUpdateRunnable)
        heatmapUpdateHandler.postDelayed(heatmapUpdateRunnable, 500)
    }

    fun clearPendingUpdates() {
        heatmapUpdateHandler.removeCallbacks(heatmapUpdateRunnable)
        copernicusSceneResolutionJob?.cancel()
        copernicusSceneResolutionJob = null
        pendingCopernicusSceneKey = null
    }

    private fun usesBlackMapControls(mapSource: String): Boolean =
        mapSource == "MML_MAASTO" ||
                mapSource == "MML_ILMA" ||
                mapSource == "TRAFICOM_SEA" ||
                mapSource == "TRAFICOM_BOATING"

    private fun controlColor(useBlack: Boolean): Int = ContextCompat.getColor(
        activity,
        if (useBlack) android.R.color.black else android.R.color.white
    )

    private companion object {
        const val SCENE_DATE_LOOKUP_DEBOUNCE_MILLIS = 400L
        const val SCENE_DATE_LOOKUP_RETRY_MILLIS = 15_000L
        const val SCENE_DATE_NO_SCENE_RETRY_MILLIS = 5 * 60_000L
    }
}
