package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.createBitmap
import fi.anssi.kalakartta.R
import java.io.File

/** Creates and caches the bitmap variants used by map markers. */
class MarkerIconFactory(private val context: Context) {
    private val iconCache = mutableMapOf<Pair<Int, Int>, BitmapDrawable>()
    private val pathIconCache = mutableMapOf<Pair<String, Int>, BitmapDrawable>()
    private val touchIconCache = mutableMapOf<Triple<Int, Int, Int>, BitmapDrawable>()
    private val labelIconCache = mutableMapOf<Triple<Int, Int, String>, BitmapDrawable>()
    private val clusterIconCache = mutableMapOf<Any, BitmapDrawable>()

    fun clear() {
        iconCache.clear()
        pathIconCache.clear()
        touchIconCache.clear()
        labelIconCache.clear()
        clusterIconCache.clear()
    }

    fun getScaledIcon(drawableId: Int, sizeDp: Int): BitmapDrawable {
        val key = drawableId to sizeDp
        return iconCache.getOrPut(key) { createScaledIcon(drawableId, sizeDp) }
    }

    fun getScaledIcon(path: String, sizeDp: Int): BitmapDrawable {
        val key = path to sizeDp
        return pathIconCache.getOrPut(key) { createScaledIcon(path, sizeDp) }
    }

    fun getTouchIcon(drawableId: Int, visibleSizeDp: Int, touchSizeDp: Int): BitmapDrawable {
        val key = Triple(drawableId, visibleSizeDp, touchSizeDp)
        return touchIconCache.getOrPut(key) {
            createSmallIconWithLargeTouchArea(drawableId, visibleSizeDp, touchSizeDp)
        }
    }

    fun getLabelIcon(drawableId: Int, sizeDp: Int, label: String): BitmapDrawable {
        val key = Triple(drawableId, sizeDp, label)
        return labelIconCache.getOrPut(key) { createIconWithLabel(drawableId, sizeDp, label) }
    }

    fun getClusterIcon(drawableId: Int, sizeDp: Int, count: Int): BitmapDrawable {
        val key = Triple(drawableId, sizeDp, count)
        return clusterIconCache.getOrPut(key) {
            drawClusterCountOnBitmap(getScaledIcon(drawableId, sizeDp).bitmap, count)
        }
    }

    fun getClusterIcon(path: String, sizeDp: Int, count: Int): BitmapDrawable {
        val key = Triple(path, sizeDp, count)
        return clusterIconCache.getOrPut(key) {
            drawClusterCountOnBitmap(getScaledIcon(path, sizeDp).bitmap, count)
        }
    }

    private fun createIconWithLabel(drawableId: Int, sizeDp: Int, label: String): BitmapDrawable {
        val baseIcon = getScaledIcon(drawableId, sizeDp).bitmap
        val density = context.resources.displayMetrics.density

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12 * density
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(2f, 1f, 1f, Color.WHITE)
        }

        val bounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, bounds)
        val padding = (4 * density).toInt()
        val textWidth = bounds.width()
        val textHeight = bounds.height()
        val textOffset = textPaint.fontMetrics.descent
        val bitmapWidth = baseIcon.width.coerceAtLeast(textWidth + padding * 2)
        val bitmapHeight = baseIcon.height + textHeight + padding * 2

        val bitmap = createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(baseIcon, (bitmapWidth - baseIcon.width) / 2f, 0f, null)
        canvas.drawText(
            label,
            bitmapWidth / 2f,
            (baseIcon.height + textHeight + padding).toFloat() - textOffset,
            textPaint
        )
        return bitmap.toDrawable(context.resources)
    }

    private fun drawClusterCountOnBitmap(baseIcon: Bitmap, count: Int): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val bitmap = baseIcon.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 12 * density
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val text = count.toString()
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val radius = (bounds.width().coerceAtLeast(bounds.height()) / 2f) + (4 * density)
        val centerX = bitmap.width - radius
        val centerY = radius

        canvas.drawCircle(centerX, centerY, radius, paint)
        canvas.drawText(text, centerX, centerY + (bounds.height() / 2f), textPaint)
        return bitmap.toDrawable(context.resources)
    }

    private fun createScaledIcon(drawableId: Int, sizeDp: Int): BitmapDrawable {
        val drawable = ContextCompat.getDrawable(context, drawableId)
            ?: ContextCompat.getDrawable(context, R.drawable.default_point)!!
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
        return drawable.toBitmap(sizePx, sizePx).toDrawable(context.resources)
    }

    private fun createScaledIcon(path: String, sizeDp: Int): BitmapDrawable {
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
        val file = if (path.startsWith("/")) File(path) else File(context.filesDir, path)
        val bitmap = try {
            val original = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            Bitmap.createScaledBitmap(original, sizePx, sizePx, true)
        } catch (_: Exception) {
            ContextCompat.getDrawable(context, R.drawable.default_point)!!.toBitmap(sizePx, sizePx)
        }
        return bitmap.toDrawable(context.resources)
    }

    private fun createSmallIconWithLargeTouchArea(
        drawableId: Int,
        visibleSizeDp: Int,
        touchSizeDp: Int
    ): BitmapDrawable {
        val drawable = ContextCompat.getDrawable(context, drawableId)
            ?: ContextCompat.getDrawable(context, R.drawable.default_point)!!
        val density = context.resources.displayMetrics.density
        val visibleSizePx = (visibleSizeDp * density).toInt()
        val touchSizePx = (touchSizeDp * density).toInt()

        val bitmap = createBitmap(touchSizePx, touchSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val left = (touchSizePx - visibleSizePx) / 2
        val top = (touchSizePx - visibleSizePx) / 2
        drawable.setBounds(left, top, left + visibleSizePx, top + visibleSizePx)
        drawable.draw(canvas)
        return bitmap.toDrawable(context.resources)
    }
}
