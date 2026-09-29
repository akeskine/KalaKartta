package fi.anssi.kalakartta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopernicusTileFallbackTest {
    @Test
    fun identifiesOnlyCompletelyBlackImages() {
        val black = 0xFF000000.toInt()
        val nearBlack = 0xFF101010.toInt()
        val imagery = 0xFF304050.toInt()

        assertTrue(CopernicusTileFallback.isMostlyBlack(IntArray(100) { black }))
        assertFalse(CopernicusTileFallback.isMostlyBlack(IntArray(100) { nearBlack }))
        assertFalse(CopernicusTileFallback.isMostlyBlack(IntArray(100) { if (it < 99) black else imagery }))
        assertFalse(CopernicusTileFallback.isMostlyBlack(IntArray(100) { if (it < 85) nearBlack else imagery }))
        assertFalse(CopernicusTileFallback.isMostlyBlack(IntArray(100) { imagery }))
    }

    @Test
    fun ancestorsAreVisitedNearestFirstAndMapToTheCorrectParentSubtile() {
        val ancestors = CopernicusTileFallback.ancestors(zoom = 6, x = 45, y = 22, minimumZoom = 3)

        assertEquals(listOf(5, 4, 3), ancestors.map { it.zoom })
        assertEquals(CopernicusAncestorTile(5, 22, 11, 1, 0, 2), ancestors[0])
        assertEquals(CopernicusAncestorTile(4, 11, 5, 1, 2, 4), ancestors[1])
        assertEquals(CopernicusAncestorTile(3, 5, 2, 5, 6, 8), ancestors[2])
    }
}