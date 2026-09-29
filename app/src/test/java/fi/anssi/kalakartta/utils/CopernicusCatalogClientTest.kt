package fi.anssi.kalakartta.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class CopernicusCatalogClientTest {
    @Test
    fun catalogQueryUsesL2aTileCloudAndTargetDateFiltersWithoutIntersects() {
        val client = CopernicusCatalogClient()
        val url = client.buildUrl("35VLG", "2026-09-28")
        val query = URLDecoder.decode(url.query, StandardCharsets.UTF_8.name())

        assertTrue(query.contains("Collection/Name eq 'SENTINEL-2'"))
        assertTrue(query.contains("productType' and att/OData.CSC.StringAttribute/Value eq 'S2MSI2A'"))
        assertTrue(query.contains("tileId' and att/OData.CSC.StringAttribute/Value eq '35VLG'"))
        assertTrue(query.contains("cloudCover' and att/OData.CSC.DoubleAttribute/Value le 20"))
        assertTrue(query.contains("ContentDate/Start lt 2026-09-29T00:00:00.000Z"))
        assertTrue(query.contains("\$top=1"))
        assertTrue(query.contains("\$orderby=ContentDate/Start desc"))
        assertFalse(query.contains("Intersects"))
    }

    @Test
    fun catalogQueryUsesConfiguredMaximumCloudCoverage() {
        val client = CopernicusCatalogClient()
        val url = client.buildUrl("35VLG", "2026-09-28", maxCloudCoveragePercent = 37)
        val query = URLDecoder.decode(url.query, StandardCharsets.UTF_8.name())

        assertTrue(query.contains("cloudCover' and att/OData.CSC.DoubleAttribute/Value le 37"))
    }

    @Test
    fun catalogQueryAcceptsBothCloudCoverageLimits() {
        val client = CopernicusCatalogClient()

        for (percent in listOf(0, 100)) {
            val query = URLDecoder.decode(
                client.buildUrl("35VLG", "2026-09-28", percent).query,
                StandardCharsets.UTF_8.name()
            )
            assertTrue(query.contains("cloudCover' and att/OData.CSC.DoubleAttribute/Value le $percent"))
        }
        assertTrue(runCatching { client.buildUrl("35VLG", "2026-09-28", -1) }.isFailure)
        assertTrue(runCatching { client.buildUrl("35VLG", "2026-09-28", 101) }.isFailure)
    }

    @Test
    fun catalogResponseReturnsTheSceneDateAndHandlesNoMatches() {
        val result = """{"value":[{"Name":"product","ContentDate":{"Start":"2026-09-27T09:41:21.024Z","End":"2026-09-27T10:39:00Z"}}]}"""
        val client = CopernicusCatalogClient(CopernicusHttpConnectionFactory { url ->
            FakeCopernicusHttpURLConnection(url, 200, result.toByteArray(StandardCharsets.UTF_8))
        })
        assertEquals("2026-09-27", client.findLatestSceneDate("35VLG", "2026-09-28"))

        val emptyClient = CopernicusCatalogClient(CopernicusHttpConnectionFactory { url ->
            FakeCopernicusHttpURLConnection(url, 200, """{"value":[]}""".toByteArray(StandardCharsets.UTF_8))
        })
        assertNull(emptyClient.findLatestSceneDate("35VLG", "2026-09-28"))

        val futureClient = CopernicusCatalogClient(CopernicusHttpConnectionFactory { url ->
            FakeCopernicusHttpURLConnection(
                url,
                200,
                """{"value":[{"ContentDate":{"Start":"2026-09-29T09:41:21.024Z"}}]}"""
                    .toByteArray(StandardCharsets.UTF_8)
            )
        })
        assertNull(futureClient.findLatestSceneDate("35VLG", "2026-09-28"))
    }

    @Test
    fun selectedDateSceneIsAcceptedForHeinolaTile() {
        val response = """{"value":[{"ContentDate":{"Start":"2026-02-23T10:00:31.025000Z"}}]}"""
        val client = CopernicusCatalogClient(CopernicusHttpConnectionFactory { url ->
            FakeCopernicusHttpURLConnection(url, 200, response.toByteArray(StandardCharsets.UTF_8))
        })

        assertEquals("2026-02-23", client.findLatestSceneDate("35VMH", "2026-02-23"))
    }
}