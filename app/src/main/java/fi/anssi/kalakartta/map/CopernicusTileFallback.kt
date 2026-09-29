package fi.anssi.kalakartta.map

internal object CopernicusTileFallback {
    private const val BLACK_CHANNEL_THRESHOLD = 2
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
}