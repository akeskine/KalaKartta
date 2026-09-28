package fi.anssi.kalakartta.map

import fi.anssi.kalakartta.utils.CopernicusTileSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.tileprovider.tilesource.XYTileSource

class CopernicusMapTileProviderTest {
    @Test
    fun copernicusProviderIsEligibleOnlyForCopernicusTiles() {
        val otherSource = XYTileSource("Other source", 0, 18, 256, ".png", arrayOf("https://example.com/"))

        assertFalse(usesCopernicusTileProvider(otherSource))
        assertTrue(usesCopernicusTileProvider(CopernicusTileSource()))
        assertNull(copernicusProviderZoomRange(otherSource))
        assertEquals(0..18, copernicusProviderZoomRange(CopernicusTileSource()))
    }
}