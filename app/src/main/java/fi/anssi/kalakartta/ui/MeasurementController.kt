package fi.anssi.kalakartta.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.utils.SessionStatsFormatter

/** Owns the measurement tool state, overlays, gestures and measurement UI. */
class MeasurementController(
    private val activity: AppCompatActivity,
    private val map: MapView,
    private val addOverlayBelowMarkers: (Overlay) -> Unit,
    private val createMeasurementPinBitmap: (Int) -> Bitmap
) {
    private val measurementMarkers = mutableListOf<Marker>()
    private val measurementPoints = mutableListOf<GeoPoint>()
    private var measurementPolyline: Polyline? = null
    private var measurementCursorLine: Polyline? = null
    private val measurementHandler = Handler(Looper.getMainLooper())
    private var measurementLongClickRunnable: Runnable? = null

    val pointCount: Int
        get() = measurementPoints.size

    fun setupControls() {
        activity.findViewById<MaterialButton>(R.id.measurementButton).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    measurementLongClickRunnable = Runnable {
                        clear()
                        android.widget.Toast.makeText(
                            activity,
                            "Mittaustyökalu nollattu",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    measurementHandler.postDelayed(measurementLongClickRunnable!!, 1000)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    measurementLongClickRunnable?.let { measurementHandler.removeCallbacks(it) }
                    if (event.eventTime - event.downTime < 1000) {
                        addMeasurementPoint()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    measurementLongClickRunnable?.let { measurementHandler.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }

        activity.findViewById<MaterialButton>(R.id.undoMeasurementButton).setOnClickListener {
            undoLastPoint()
        }
    }

    fun clear() {
        measurementLongClickRunnable?.let { measurementHandler.removeCallbacks(it) }
        measurementLongClickRunnable = null
        measurementPoints.clear()
        measurementMarkers.forEach { map.overlays.remove(it) }
        measurementMarkers.clear()
        measurementPolyline?.let { map.overlays.remove(it) }
        measurementPolyline = null
        measurementCursorLine?.let { map.overlays.remove(it) }
        measurementCursorLine = null
        activity.findViewById<MaterialButton>(R.id.undoMeasurementButton).visibility = android.view.View.GONE
        activity.findViewById<LinearLayout>(R.id.measurementLayout).visibility = android.view.View.GONE
        map.invalidate()
    }

    fun onMapMoved() {
        if (measurementPoints.isEmpty()) return

        val center = map.mapCenter as GeoPoint
        if (measurementCursorLine == null) {
            measurementCursorLine = Polyline(map).apply {
                outlinePaint.color = Color.RED
                outlinePaint.strokeWidth = 3f
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                setOnClickListener { _, _, _ -> true }
            }
            addOverlayBelowMarkers(measurementCursorLine!!)
        }
        measurementCursorLine?.setPoints(listOf(measurementPoints.last(), center))

        val textView = activity.findViewById<TextView>(R.id.measurementText)
        val distanceToCenter = measurementPoints.last().distanceToAsDouble(center)
        val totalDistance = totalDistance() + distanceToCenter
        val distStr = SessionStatsFormatter.formatDistance(distanceToCenter)
        val totalStr = SessionStatsFormatter.formatDistance(totalDistance)

        textView.text = if (measurementPoints.size == 1) {
            "${activity.getString(R.string.distance)} $distStr."
        } else {
            "${activity.getString(R.string.distance)} $distStr, ${activity.getString(R.string.route)} $totalStr"
        }
        map.invalidate()
    }

    fun clearPendingCallbacks() {
        measurementLongClickRunnable?.let { measurementHandler.removeCallbacks(it) }
        measurementLongClickRunnable = null
    }

    private fun addMeasurementPoint() {
        val center = map.mapCenter as GeoPoint
        measurementPoints.add(center)

        val marker = Marker(map).apply {
            position = center
            icon = BitmapDrawable(activity.resources, createMeasurementPinBitmap(Color.RED))

            val size = (32 * activity.resources.displayMetrics.density).toInt()
            val startY = size / 4f + size / 8f
            val originalLength = size * 0.9f - (size / 3f + size / 4f)
            val endY = startY + originalLength * 1.3f
            setAnchor(Marker.ANCHOR_CENTER, endY / size.toFloat())
            setOnMarkerClickListener { _, _ -> true }
        }
        map.overlays.add(marker)
        measurementMarkers.add(marker)

        if (measurementPoints.size > 1) {
            if (measurementPolyline == null) {
                measurementPolyline = Polyline(map).apply {
                    outlinePaint.color = Color.RED
                    outlinePaint.strokeWidth = 5f
                    setOnClickListener { _, _, _ -> true }
                }
                addOverlayBelowMarkers(measurementPolyline!!)
            }
            measurementPolyline?.setPoints(measurementPoints)
        }

        activity.findViewById<MaterialButton>(R.id.undoMeasurementButton).visibility = android.view.View.VISIBLE
        updateMeasurementUi()
        map.invalidate()
    }

    private fun undoLastPoint() {
        if (measurementPoints.isEmpty()) return

        measurementPoints.removeAt(measurementPoints.size - 1)
        val lastMarker = measurementMarkers.removeAt(measurementMarkers.size - 1)
        map.overlays.remove(lastMarker)

        if (measurementPoints.isEmpty()) {
            measurementPolyline?.let { map.overlays.remove(it) }
            measurementPolyline = null
            measurementCursorLine?.let { map.overlays.remove(it) }
            measurementCursorLine = null
            activity.findViewById<MaterialButton>(R.id.undoMeasurementButton).visibility = android.view.View.GONE
        } else {
            measurementPolyline?.setPoints(measurementPoints)
            val center = map.mapCenter as GeoPoint
            measurementCursorLine?.setPoints(listOf(measurementPoints.last(), center))
        }
        updateMeasurementUi()
        map.invalidate()
    }

    private fun updateMeasurementUi() {
        val layout = activity.findViewById<LinearLayout>(R.id.measurementLayout)
        val textView = activity.findViewById<TextView>(R.id.measurementText)

        if (measurementPoints.isEmpty()) {
            layout.visibility = android.view.View.GONE
            return
        }

        layout.visibility = android.view.View.VISIBLE
        if (measurementPoints.size == 1) {
            textView.text = activity.getString(R.string.measurement_start_hint)
            return
        }

        val lastPoint = measurementPoints.last()
        val secondLastPoint = measurementPoints[measurementPoints.size - 2]
        val distanceToLast = secondLastPoint.distanceToAsDouble(lastPoint)
        val distStr = SessionStatsFormatter.formatDistance(distanceToLast)
        val totalStr = SessionStatsFormatter.formatDistance(totalDistance())

        textView.text = if (measurementPoints.size == 2) {
            "${activity.getString(R.string.distance)} $distStr."
        } else {
            "${activity.getString(R.string.distance)} $distStr, ${activity.getString(R.string.route)} $totalStr"
        }

        android.widget.Toast.makeText(
            activity,
            activity.getString(R.string.measurement_next_hint),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun totalDistance(): Double {
        var totalDistance = 0.0
        for (index in 0 until measurementPoints.size - 1) {
            totalDistance += measurementPoints[index].distanceToAsDouble(measurementPoints[index + 1])
        }
        return totalDistance
    }
}
