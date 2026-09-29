package fi.anssi.kalakartta.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import fi.anssi.kalakartta.utils.CopernicusTileImageSize

internal data class CopernicusAncestorTile(
    val zoom: Int,
    val x: Int,
    val y: Int,
    val offsetX: Int,
    val offsetY: Int,
    val scale: Int
)

internal object CopernicusTileFallback {
    private const val BLACK_CHANNEL_THRESHOLD = 0
    private const val MOSTLY_BLACK_RATIO = 1.0

    fun isMostlyBlack(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return false
        val blackPixels = pixels.count { pixel ->
            (pixel shr 16 and 0xFF) <= BLACK_CHANNEL_THRESHOLD &&
                    (pixel shr 8 and 0xFF) <= BLACK_CHANNEL_THRESHOLD &&
                    (pixel and 0xFF) <= BLACK_CHANNEL_THRESHOLD
        }
        return blackPixels.toDouble() / pixels.size >= MOSTLY_BLACK_RATIO
    }

    fun ancestors(zoom: Int, x: Int, y: Int, minimumZoom: Int): List<CopernicusAncestorTile> {
        require(zoom in 0..30 && minimumZoom in 0..zoom) { "Invalid Copernicus zoom range" }
        val tileCount = 1L shl zoom
        require(x >= 0 && x.toLong() < tileCount && y >= 0 && y.toLong() < tileCount) {
            "Tile coordinates are outside the zoom level"
        }
        return (zoom - 1 downTo minimumZoom).map { ancestorZoom ->
            val scale = 1 shl (zoom - ancestorZoom)
            CopernicusAncestorTile(
                zoom = ancestorZoom,
                x = x / scale,
                y = y / scale,
                offsetX = x % scale,
                offsetY = y % scale,
                scale = scale
            )
        }
    }

    fun cropDescendantTile(ancestorBitmap: Bitmap, ancestor: CopernicusAncestorTile): Bitmap {
        val tileSize = CopernicusTileImageSize.TILE_SIZE_PIXELS
        require(ancestorBitmap.width == tileSize && ancestorBitmap.height == tileSize) {
            "Copernicus ancestor tile has an unexpected size"
        }
        val output = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val scale = ancestor.scale.toFloat()
        val matrix = Matrix().apply {
            setValues(
                floatArrayOf(
                    scale, 0f, -ancestor.offsetX * tileSize.toFloat(),
                    0f, scale, -ancestor.offsetY * tileSize.toFloat(),
                    0f, 0f, 1f
                )
            )
        }
        return try {
            Canvas(output).drawBitmap(ancestorBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            output
        } catch (failure: Throwable) {
            output.recycle()
            throw failure
        }
    }
}