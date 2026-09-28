package fi.anssi.kalakartta.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fi.anssi.kalakartta.utils.CopernicusTileCoordinate
import fi.anssi.kalakartta.utils.CopernicusTileGroup
import java.io.ByteArrayOutputStream
import java.io.IOException

internal object CopernicusTileImageSplitter {
    fun split(imageBytes: ByteArray, group: CopernicusTileGroup): Map<CopernicusTileCoordinate, ByteArray> {
        val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IOException("Copernicus Process API returned an unreadable image")
        try {
            if (image.width != group.pixelWidth || image.height != group.pixelHeight) {
                throw IOException("Copernicus Process API returned an unexpected image size")
            }

            return group.coordinates().associateWith { coordinate ->
                val cropRect = group.cropRectFor(coordinate.x, coordinate.y)
                val tile = Bitmap.createBitmap(
                    image,
                    cropRect.left,
                    cropRect.top,
                    cropRect.width,
                    cropRect.height
                )
                try {
                    ByteArrayOutputStream().use { output ->
                        if (!tile.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                            throw IOException("Unable to encode Copernicus child tile")
                        }
                        output.toByteArray()
                    }
                } finally {
                    if (tile !== image) tile.recycle()
                }
            }
        } finally {
            image.recycle()
        }
    }

    private const val JPEG_QUALITY = 95
}