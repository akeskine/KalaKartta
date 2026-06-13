package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class WindDirectionView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var startAngle: Float? = null
    private var endAngle: Float? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#80FF0000") // Puoliläpinäkyvä punainen
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#40FFFFFF") // Hyvin vaalea valkoinen tausta
    }

    private val rect = RectF()

    fun setRange(min: Float?, max: Float?) {
        startAngle = min
        endAngle = max
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val size = Math.min(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = size / 2f - 4f
        
        rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        // Piirrä taustaympyrä
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
        canvas.drawCircle(centerX, centerY, radius, strokePaint)

        // Piirrä suuntaviivat (P, I, E, L)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY - radius + 10, strokePaint) // N
        canvas.drawLine(centerX + radius, centerY, centerX + radius - 10, centerY, strokePaint) // E
        canvas.drawLine(centerX, centerY + radius, centerX, centerY + radius - 10, strokePaint) // S
        canvas.drawLine(centerX - radius, centerY, centerX - radius + 10, centerY, strokePaint) // W

        val start = startAngle
        val end = endAngle

        if (start != null && end != null) {
            // Androidissa 0 on oikealla (itä), mutta meillä 0 on ylhäällä (pohjoinen).
            // Korjataan vähentämällä 90 astetta.
            val sweep: Float
            val adjustedStart = start - 90f
            
            if (start <= end) {
                sweep = end - start
            } else {
                // Sektori ylittää 360/0 rajan
                sweep = (360f - start) + end
            }
            
            canvas.drawArc(rect, adjustedStart, sweep, true, paint)
        }
    }
}
