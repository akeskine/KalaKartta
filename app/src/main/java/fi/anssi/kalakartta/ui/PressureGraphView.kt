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
    private var isHistoryOnly = false

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.resolveThemeColor(android.R.attr.textColorPrimary, Color.BLACK)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.resolveThemeColor(android.R.attr.textColorPrimary, Color.BLACK)
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

    private var timeRangeHours = 6f // -6 to +6

    fun setData(samples: List<PressureSample>, caughtAt: Long) {
        android.util.Log.d("KalaKartta", "PressureGraphView.setData: samples=${samples.size}, caughtAt=$caughtAt")
        this.samples = samples.sortedBy { it.time }
        this.caughtAt = caughtAt
        isHistoryOnly = false
        timeRangeHours = 6f
        invalidate()
    }

    fun setHistoryData(samples: List<PressureSample>, endTime: Long) {
        this.samples = samples.sortedBy { it.time }
        caughtAt = endTime
        isHistoryOnly = true
        timeRangeHours = 12f
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
        val millisInRange = timeRangeHours * 60 * 60 * 1000
        val visibleSamples = samples.filter { sample ->
            val relativeTime = sample.time - caughtAt
            val inRange = if (isHistoryOnly) {
                sample.time in (caughtAt - millisInRange.toLong())..caughtAt
            } else {
                Math.abs(relativeTime) <= millisInRange
            }
            inRange && sample.pressure.isFinite()
        }
        if (visibleSamples.isEmpty()) return

        val pressureRange = PressureGraphScale.rangeFor(visibleSamples.map { it.pressure })

        // Draw grid and Y-axis labels (seaLevel every 10 hPa).
        var pressure = pressureRange.min
        while (pressure <= pressureRange.max) {
            val y = height.toFloat() - paddingBottom -
                    ((pressure - pressureRange.min) / (pressureRange.max - pressureRange.min)) * graphHeight
            
            // Grid line (horizontal)
            if (pressure != pressureRange.min && pressure != pressureRange.max) {
                canvas.drawLine(paddingLeft, y, width.toFloat() - paddingRight, y, gridPaint)
            }
            
            // Label
            canvas.drawText(pressure.toInt().toString(), 5f, y + 10f, textPaint)
            pressure += PressureGraphScale.TICK_STEP_HPA
        }

        // Draw grid and X-axis labels.
        if (isHistoryOnly) {
            for (hoursAgo in 12 downTo 0 step 3) {
                val x = paddingLeft + graphWidth * (12 - hoursAgo) / 12
                if (hoursAgo != 0 && hoursAgo != 12) {
                    canvas.drawLine(x, paddingTop, x, height.toFloat() - paddingBottom, gridPaint)
                }
                val label = if (hoursAgo == 0) "Nyt" else "${hoursAgo}h"
                val textWidth = textPaint.measureText(label)
                canvas.drawText(label, x - textWidth / 2, height.toFloat() - 10f, textPaint)
            }
        } else {
            for (h in -6..6 step 2) {
                val x = paddingLeft + graphWidth / 2 + (h.toFloat() / timeRangeHours) * (graphWidth / 2)

                if (h != 0 && Math.abs(h.toFloat()) != timeRangeHours) {
                    canvas.drawLine(x, paddingTop, x, height.toFloat() - paddingBottom, gridPaint)
                }

                val label = if (h == 0) "0" else "${h}h"
                val textWidth = textPaint.measureText(label)
                canvas.drawText(label, x - textWidth / 2, height.toFloat() - 10f, textPaint)
            }
        }

        // Draw axes
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height.toFloat() - paddingBottom, axisPaint) // Y-axis
        canvas.drawLine(paddingLeft, height.toFloat() - paddingBottom, width.toFloat() - paddingRight, height.toFloat() - paddingBottom, axisPaint) // X-axis

        if (!isHistoryOnly) {
            // Vertical line for caughtAt (0 point)
            val centerX = paddingLeft + graphWidth / 2
            canvas.drawLine(centerX, paddingTop, centerX, height.toFloat() - paddingBottom, caughtAtPaint)
        }

        // Plot samples
        val path = Path()
        var first = true

        for (sample in samples) {
            val relativeTime = sample.time - caughtAt
            if (isHistoryOnly) {
                if (sample.time !in (caughtAt - millisInRange.toLong())..caughtAt) continue
            } else if (Math.abs(relativeTime) > millisInRange) {
                continue
            }
            if (!sample.pressure.isFinite()) continue

            val x = if (isHistoryOnly) {
                paddingLeft + ((sample.time - (caughtAt - millisInRange.toLong())).toFloat() / millisInRange) * graphWidth
            } else {
                paddingLeft + graphWidth / 2 + (relativeTime.toFloat() / millisInRange) * (graphWidth / 2)
            }
            val y = height.toFloat() - paddingBottom -
                    ((sample.pressure.toFloat() - pressureRange.min) /
                            (pressureRange.max - pressureRange.min)) * graphHeight

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
