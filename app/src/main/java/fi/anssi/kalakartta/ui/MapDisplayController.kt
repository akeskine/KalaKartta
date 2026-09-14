package fi.anssi.kalakartta.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay
import android.os.Handler
import android.os.Looper
import android.content.res.ColorStateList
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.utils.MMLTileSource
import fi.anssi.kalakartta.utils.TraficomTileSource
import fi.anssi.kalakartta.utils.VeneilykarttaTileSource

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
    private val heatmapUpdateHandler = Handler(Looper.getMainLooper())
    private val heatmapUpdateRunnable = Runnable {
        heatmapOverlay?.refreshData(map.boundingBox)
    }

    fun updateMapTileSource() {
        when (settingsStore.mapSource) {
            "TRAFICOM_SEA" -> {
                map.setTileSource(TraficomTileSource())
                updateUIColors(useBlack = true)
            }
            "TRAFICOM_BOATING" -> {
                map.setTileSource(VeneilykarttaTileSource())
                updateUIColors(useBlack = true)
            }
            "MML_MAASTO" -> {
                map.setTileSource(MMLTileSource("MML Maastokartta", "maastokartta", settingsStore.mmlApiKey))
                updateUIColors(useBlack = true)
            }
            "MML_ILMA" -> {
                map.setTileSource(MMLTileSource("MML Ilmakuva", "ortokuva", settingsStore.mmlApiKey))
                updateUIColors(useBlack = true)
            }
            else -> {
                map.setTileSource(TileSourceFactory.MAPNIK)
                updateUIColors(useBlack = false)
            }
        }
        updateScaleBar()
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

        activity.findViewById<MaterialButton>(R.id.quickMapSourceButton).visibility =
            if (showQuickMap) View.VISIBLE else View.GONE

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
        activity.findViewById<MaterialButton>(R.id.heatmapShortcutButton).visibility =
            if (shortcutMode > 0) View.VISIBLE else View.GONE

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
}
