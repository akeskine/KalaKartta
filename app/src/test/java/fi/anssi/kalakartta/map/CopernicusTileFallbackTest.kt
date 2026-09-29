package fi.anssi.kalakartta.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopernicusTileFallbackTest {
    @Test
    fun identifiesOnlyCompletelyBlackImages() {
        val black = 0xFF000000.toInt()
        val nearBlack = 0xFF101010.toInt()
        val jpegBlack = 0xFF010201.toInt()
        val imagery = 0xFF304050.toInt()

        assertTrue(CopernicusTileFallback.isMostlyBlack(IntArray(100) { black }))
        assertTrue(CopernicusTileFallback.isMostlyBlack(IntArray(100) { jpegBlack }))
        assertFalse(CopernicusTileFallback.isMostlyBlack(IntArray(100) { nearBlack }))
        assertFalse(CopernicusTileFallback.isMostlyBlack(IntArray(100) { if (it < 99) black else imagery }))
        assertFalse(CopernicusTileFallback.isMostlyBlack(IntArray(100) { if (it < 85) nearBlack else imagery }))
        assertFalse(CopernicusTileFallback.isMostlyBlack(IntArray(100) { imagery }))
    }

}