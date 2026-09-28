package fi.anssi.kalakartta.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MgrsTileIdResolverTest {
    private val resolver = MgrsTileIdResolver()

    @Test
    fun knownFinnishCoordinatesResolveToExpectedSentinelTiles() {
        assertEquals("35VMJ", resolver.resolve(61.74, 26.12))
        assertEquals("35VLG", resolver.resolve(60.1699, 24.9384))
    }

    @Test
    fun norwayAndSvalbardUseTheirSpecialUtmZones() {
        assertTrue(resolver.resolve(60.0, 4.0).startsWith("32V"))
        assertTrue(resolver.resolve(76.0, 7.0).startsWith("31X"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCoordinatesOutsideUtmLatitudeCoverage() {
        resolver.resolve(85.0, 26.0)
    }
}