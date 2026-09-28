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
    private var forecastSamples: List<SeaLevelSample> = emptyList()
    private var caughtAt: Long = 0L
    private var isWeatherSummary = false

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
    private val forecastLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 105, 92)
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
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
        forecastSamples = emptyList()
        this.caughtAt = caughtAt
        isWeatherSummary = false
        invalidate()
    }

    fun setWeatherSummaryData(
        historySamples: List<SeaLevelSample>,
        forecastSamples: List<SeaLevelSample>,
        now: Long
    ) {
        samples = historySamples.filter { it.time <= now }.sortedBy { it.time }
        this.forecastSamples = forecastSamples.filter { it.time > now }.sortedBy { it.time }
        caughtAt = now
        isWeatherSummary = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (caughtAt == 0L || (!isWeatherSummary && samples.isEmpty())) return

        val paddingLeft = 80f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 60f
        val graphWidth = width.toFloat() - paddingLeft - paddingRight
        val graphHeight = height.toFloat() - paddingTop - paddingBottom
        if (graphWidth <= 0f || graphHeight <= 0f) return

        val rangeHours = if (isWeatherSummary) 12f else timeRangeHours
        val millisInRange = rangeHours * 60 * 60 * 1000
        val visibleHistory = samples.filter { sample ->
            val relativeTime = sample.time - caughtAt
            if (isWeatherSummary) relativeTime in -millisInRange.toLong()..0L
            else kotlin.math.abs(relativeTime) <= millisInRange
        }
        val visibleForecast = if (isWeatherSummary) {
            forecastSamples.filter { sample -> sample.time - caughtAt in 0L..millisInRange.toLong() }
        } else {
            emptyList()
        }
        val visibleSamples = visibleHistory + visibleForecast
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

        val hoursBetweenLabels = if (isWeatherSummary) 6 else 2
        for (hour in -rangeHours.toInt()..rangeHours.toInt() step hoursBetweenLabels) {
            val x = paddingLeft + graphWidth / 2 + (hour.toFloat() / rangeHours) * (graphWidth / 2)
            if (hour != 0 && kotlin.math.abs(hour.toFloat()) != rangeHours) {
                canvas.drawLine(x, paddingTop, x, height.toFloat() - paddingBottom, gridPaint)
            }
            val label = if (hour == 0) {
                if (isWeatherSummary) "Nyt" else "0"
            } else {
                "${hour}h"
            }
            canvas.drawText(label, x - textPaint.measureText(label) / 2, height.toFloat() - 10f, textPaint)
        }

        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height.toFloat() - paddingBottom, axisPaint)
        canvas.drawLine(paddingLeft, height.toFloat() - paddingBottom, width.toFloat() - paddingRight, height.toFloat() - paddingBottom, axisPaint)
        val centerX = paddingLeft + graphWidth / 2
        canvas.drawLine(centerX, paddingTop, centerX, height.toFloat() - paddingBottom, caughtAtPaint)

        fun drawSeaLevelLine(lineSamples: List<SeaLevelSample>, paint: Paint) {
            val path = Path()
            var first = true
            lineSamples.forEach { sample ->
                val relativeTime = sample.time - caughtAt
                val x = paddingLeft + graphWidth / 2 +
                        (relativeTime.toFloat() / millisInRange) * (graphWidth / 2)
                val y = height.toFloat() - paddingBottom -
                        ((sample.seaLevel.toFloat() - minLevel) / (maxLevel - minLevel)) * graphHeight
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, paint)
        }

        drawSeaLevelLine(visibleHistory, linePaint)
        drawSeaLevelLine(visibleForecast, forecastLinePaint)
    }
}