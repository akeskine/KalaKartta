package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class MoonPhaseView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var phase: Double = 0.0 // 0.0 ... 1.0
    
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.FILL
    }
    
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5DC") // Luunvärinen
        style = Paint.Style.FILL
    }

    fun setPhase(newPhase: Double) {
        phase = newPhase % 1.0
        if (phase < 0) phase += 1.0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        shadowPaint.color = if (isEnabled) Color.parseColor("#444444") else Color.parseColor("#BDBDBD")
        lightPaint.color = if (isEnabled) Color.parseColor("#F5F5DC") else Color.parseColor("#E0E0E0")
        
        val width = width.toFloat()
        val height = height.toFloat()
        val radius = min(width, height) / 2f * 0.9f
        val cx = width / 2f
        val cy = height / 2f
        
        // Piirretään ensin koko kuu varjona
        canvas.drawCircle(cx, cy, radius, shadowPaint)
        
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        
        // Lasketaan visualisointi vaiheen mukaan
        // 0.0 = Uusikuu (kokonaan tumma)
        // 0.25 = Ensimmäinen neljännes (oikea puoli valaistu)
        // 0.5 = Täysikuu (kokonaan valaistu)
        // 0.75 = Viimeinen neljännes (vasen puoli valaistu)
        
        if (phase == 0.0) return // Uusikuu, ei piirretä valoa
        
        if (phase <= 0.5) {
            // Kasvava kuu (oikea puoli alkaa valaistua)
            // Piirretään oikea puoliympyrä
            canvas.drawArc(rect, -90f, 180f, true, lightPaint)
            
            val illumination = (phase / 0.25) - 1.0 // -1.0 (uusi) -> 0.0 (puoli) -> 1.0 (täysi)
            if (illumination < 0) {
                // Crescent (0.0 - 0.25): Oikea puoliympyrä piirretty, 
                // nyt piirretään varjo-ellipsi sen päälle peittämään osa siitä
                val innerWidth = radius * (-illumination).toFloat()
                val innerRect = RectF(cx - innerWidth, cy - radius, cx + innerWidth, cy + radius)
                canvas.drawOval(innerRect, shadowPaint)
            } else if (illumination > 0) {
                // Gibbous (0.25 - 0.5): Oikea puoliympyrä piirretty,
                // nyt piirretään valo-ellipsi vasemmalle puolelle
                val innerWidth = radius * illumination.toFloat()
                val innerRect = RectF(cx - innerWidth, cy - radius, cx + innerWidth, cy + radius)
                canvas.drawOval(innerRect, lightPaint)
            }
        } else {
            // Vähenevä kuu (vasen puoli valaistu tai oikea puoli alkaa varjostua)
            // Piirretään vasen puoliympyrä
            canvas.drawArc(rect, 90f, 180f, true, lightPaint)
            
            val illumination = ((phase - 0.5) / 0.25) - 1.0 // -1.0 (täysi) -> 0.0 (puoli) -> 1.0 (uusi)
            if (illumination < 0) {
                // Gibbous (0.5 - 0.75): Vasen puoliympyrä piirretty,
                // nyt piirretään valo-ellipsi oikealle puolelle
                val innerWidth = radius * (-illumination).toFloat()
                val innerRect = RectF(cx - innerWidth, cy - radius, cx + innerWidth, cy + radius)
                canvas.drawOval(innerRect, lightPaint)
            } else if (illumination > 0) {
                // Crescent (0.75 - 1.0): Vasen puoliympyrä piirretty,
                // nyt piirretään varjo-ellipsi sen päälle
                val innerWidth = radius * illumination.toFloat()
                val innerRect = RectF(cx - innerWidth, cy - radius, cx + innerWidth, cy + radius)
                canvas.drawOval(innerRect, shadowPaint)
            }
        }
    }
}
