package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import fi.anssi.kalakartta.data.PressureSample
import java.util.Locale

class PressureGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var samples: List<PressureSample> = emptyList()
    private var caughtAt: Long = 0L

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
    }

    private val caughtAtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        style = Paint.Style.STROKE
    }

    private val minPressure = 940f
    private val maxPressure = 1060f
    private val timeRangeHours = 6f // -6 to +6

    fun setData(samples: List<PressureSample>, caughtAt: Long) {
        this.samples = samples.sortedBy { it.time }
        this.caughtAt = caughtAt
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (samples.isEmpty() || caughtAt == 0L) return

        val paddingLeft = 80f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 60f

        val graphWidth = width.toFloat() - paddingLeft - paddingRight
        val graphHeight = height.toFloat() - paddingTop - paddingBottom

        // Draw grid and Y-axis labels (Pressure every 20 hPa)
        for (p in 940..1060 step 20) {
            val y = height.toFloat() - paddingBottom - ((p.toFloat() - minPressure) / (maxPressure - minPressure)) * graphHeight
            
            // Grid line (horizontal)
            if (p.toFloat() != minPressure && p.toFloat() != maxPressure) {
                canvas.drawLine(paddingLeft, y, width.toFloat() - paddingRight, y, gridPaint)
            }
            
            // Label
            canvas.drawText(p.toString(), 5f, y + 10f, textPaint)
        }

        // Draw grid and X-axis labels (Time every 2h)
        for (h in -6..6 step 2) {
            val x = paddingLeft + graphWidth / 2 + (h.toFloat() / timeRangeHours) * (graphWidth / 2)
            
            // Grid line (vertical)
            if (h != 0 && Math.abs(h.toFloat()) != timeRangeHours) {
                canvas.drawLine(x, paddingTop, x, height.toFloat() - paddingBottom, gridPaint)
            }
            
            // Label
            val label = if (h == 0) "0" else "${h}h"
            val textWidth = textPaint.measureText(label)
            canvas.drawText(label, x - textWidth / 2, height.toFloat() - 10f, textPaint)
        }

        // Draw axes
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height.toFloat() - paddingBottom, axisPaint) // Y-axis
        canvas.drawLine(paddingLeft, height.toFloat() - paddingBottom, width.toFloat() - paddingRight, height.toFloat() - paddingBottom, axisPaint) // X-axis

        // Vertical line for caughtAt (0 point)
        val centerX = paddingLeft + graphWidth / 2
        canvas.drawLine(centerX, paddingTop, centerX, height.toFloat() - paddingBottom, caughtAtPaint)

        // Plot samples
        val path = Path()
        var first = true

        val millisInRange = timeRangeHours * 60 * 60 * 1000

        for (sample in samples) {
            val relativeTime = sample.time - caughtAt
            if (Math.abs(relativeTime) > millisInRange) continue

            val x = paddingLeft + graphWidth / 2 + (relativeTime.toFloat() / millisInRange) * (graphWidth / 2)
            val y = height.toFloat() - paddingBottom - ((sample.pressure.toFloat() - minPressure) / (maxPressure - minPressure)) * graphHeight

            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, linePaint)
    }
}
