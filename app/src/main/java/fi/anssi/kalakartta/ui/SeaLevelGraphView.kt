package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import fi.anssi.kalakartta.data.SeaLevelSample
import kotlin.math.ceil
import kotlin.math.floor

class SeaLevelGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var samples: List<SeaLevelSample> = emptyList()
    private var caughtAt: Long = 0L

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 105, 92)
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

    private val timeRangeHours = 6f

    fun setData(samples: List<SeaLevelSample>, caughtAt: Long) {
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
        if (graphWidth <= 0f || graphHeight <= 0f) return

        val millisInRange = timeRangeHours * 60 * 60 * 1000
        val visibleSamples = samples.filter { sample ->
            kotlin.math.abs(sample.time - caughtAt) <= millisInRange
        }
        if (visibleSamples.isEmpty()) return

        val rawMin = visibleSamples.minOf { it.seaLevel }.toFloat()
        val rawMax = visibleSamples.maxOf { it.seaLevel }.toFloat()
        var minLevel = floor(rawMin / 10f) * 10f
        var maxLevel = ceil(rawMax / 10f) * 10f
        if (maxLevel - minLevel < 20f) {
            val center = (rawMin + rawMax) / 2f
            minLevel = floor((center - 10f) / 10f) * 10f
            maxLevel = minLevel + 20f
        }

        var level = minLevel
        while (level <= maxLevel) {
            val y = height.toFloat() - paddingBottom -
                    ((level - minLevel) / (maxLevel - minLevel)) * graphHeight
            if (level != minLevel && level != maxLevel) {
                canvas.drawLine(paddingLeft, y, width.toFloat() - paddingRight, y, gridPaint)
            }
            canvas.drawText(level.toInt().toString(), 5f, y + 10f, textPaint)
            level += 10f
        }

        for (hour in -6..6 step 2) {
            val x = paddingLeft + graphWidth / 2 + (hour.toFloat() / timeRangeHours) * (graphWidth / 2)
            if (hour != 0 && kotlin.math.abs(hour.toFloat()) != timeRangeHours) {
                canvas.drawLine(x, paddingTop, x, height.toFloat() - paddingBottom, gridPaint)
            }
            val label = if (hour == 0) "0" else "${hour}h"
            canvas.drawText(label, x - textPaint.measureText(label) / 2, height.toFloat() - 10f, textPaint)
        }

        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height.toFloat() - paddingBottom, axisPaint)
        canvas.drawLine(paddingLeft, height.toFloat() - paddingBottom, width.toFloat() - paddingRight, height.toFloat() - paddingBottom, axisPaint)
        val centerX = paddingLeft + graphWidth / 2
        canvas.drawLine(centerX, paddingTop, centerX, height.toFloat() - paddingBottom, caughtAtPaint)

        val path = Path()
        var first = true
        visibleSamples.forEach { sample ->
            val relativeTime = sample.time - caughtAt
            val x = paddingLeft + graphWidth / 2 + (relativeTime.toFloat() / millisInRange) * (graphWidth / 2)
            val y = height.toFloat() - paddingBottom -
                    ((sample.seaLevel.toFloat() - minLevel) / (maxLevel - minLevel)) * graphHeight
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