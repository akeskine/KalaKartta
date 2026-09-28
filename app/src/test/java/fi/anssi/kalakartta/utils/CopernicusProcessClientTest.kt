package fi.anssi.kalakartta.utils

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import java.nio.charset.StandardCharsets

class CopernicusProcessClientTest {
    @Test
    fun tileCoordinatesProduceExpectedEpsg3857BoundsAndProcessBody() {
        val bounds = CopernicusTileBoundsCalculator.fromTile(1, 1, 0)
        val halfWorld = Math.PI * 6_378_137.0
        assertEquals(0.0, bounds.minX, 0.0001)
        assertEquals(0.0, bounds.minY, 0.0001)
        assertEquals(halfWorld, bounds.maxX, 0.0001)
        assertEquals(halfWorld, bounds.maxY, 0.0001)

        val bodyText = CopernicusProcessRequest.body(1, 1, 0, "2026-09-27")
        val body = JSONObject(bodyText)
        val input = body.getJSONObject("input")
        val processBounds = input.getJSONObject("bounds")
        val bbox = processBounds.getJSONArray("bbox")
        assertEquals(-halfWorld, bbox.getDouble(0), 0.0001)
        assertEquals(-halfWorld, bbox.getDouble(1), 0.0001)
        assertEquals(halfWorld, bbox.getDouble(2), 0.0001)
        assertEquals(halfWorld, bbox.getDouble(3), 0.0001)
        assertEquals(CopernicusProcessRequest.CRS_EPSG_3857, processBounds.getJSONObject("properties").getString("crs"))
        assertEquals(bodyText, CopernicusProcessRequest.body(1, 0, 1, "2026-09-27"))

        val data = input.getJSONArray("data").getJSONObject(0)
        assertEquals("sentinel-2-l2a", data.getString("type"))
        val timeRange = data.getJSONObject("dataFilter").getJSONObject("timeRange")
        assertEquals("2026-09-27T00:00:00Z", timeRange.getString("from"))
        assertEquals("2026-09-28T00:00:00Z", timeRange.getString("to"))
        val output = body.getJSONObject("output")
        assertEquals(512, output.getInt("width"))
        assertEquals(512, output.getInt("height"))
        assertEquals("image/jpeg", output.getJSONArray("responses").getJSONObject(0)
            .getJSONObject("format").getString("type"))
        assertTrue(body.getString("evalscript").contains("2.5 * s.B04"))
        assertTrue(body.getString("evalscript").contains("2.5 * s.B03"))
        assertTrue(body.getString("evalscript").contains("2.5 * s.B02"))
    }

    @Test
    fun tileGroupMapsEachRequestedCoordinateToItsCorrectQuadrant() {
        val group = CopernicusTileGroup.fromTile(2, 3, 2)

        assertEquals(2, group.firstX)
        assertEquals(2, group.firstY)
        assertEquals(
            listOf(
                CopernicusTileCoordinate(2, 2),
                CopernicusTileCoordinate(3, 2),
                CopernicusTileCoordinate(2, 3),
                CopernicusTileCoordinate(3, 3)
            ),
            group.coordinates()
        )
        assertEquals(CopernicusTileCropRect(0, 0, 256, 256), group.cropRectFor(2, 2))
        assertEquals(CopernicusTileCropRect(256, 0, 256, 256), group.cropRectFor(3, 2))
        assertEquals(CopernicusTileCropRect(0, 256, 256, 256), group.cropRectFor(2, 3))
        assertEquals(CopernicusTileCropRect(256, 256, 256, 256), group.cropRectFor(3, 3))
    }

    @Test
    fun zoomZeroUsesSingleWorldTileDimensions() {
        val group = CopernicusTileGroup.fromTile(0, 0, 0)
        val body = JSONObject(CopernicusProcessRequest.body(0, 0, 0, "2026-09-27"))

        assertEquals(1, group.columns)
        assertEquals(1, group.rows)
        assertEquals(256, body.getJSONObject("output").getInt("width"))
        assertEquals(256, body.getJSONObject("output").getInt("height"))
    }

    @Test
    fun processRequestRefreshesAfterUnauthorizedAndRetriesOnce() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        var tokenRequestCount = 0
        var processRequestCount = 0
        val processConnections = mutableListOf<FakeCopernicusHttpURLConnection>()
        val factory = CopernicusHttpConnectionFactory { url: URL ->
            if (url.toString() == CopernicusTokenManager.TOKEN_ENDPOINT) {
                tokenRequestCount++
                FakeCopernicusHttpURLConnection(
                    url,
                    200,
                    """{"access_token":"access-$tokenRequestCount","expires_in":3600}"""
                        .toByteArray(StandardCharsets.UTF_8)
                )
            } else {
                processRequestCount++
                FakeCopernicusHttpURLConnection(
                    url,
                    if (processRequestCount == 1) 401 else 200,
                    if (processRequestCount == 1) byteArrayOf() else jpeg
                ).also { processConnections.add(it) }
            }
        }
        val tokenManager = CopernicusTokenManager(
            { CopernicusClientCredentials("client", "secret") },
            factory
        )
        val client = CopernicusProcessClient(tokenManager, factory)

        assertArrayEquals(jpeg, client.getTile(1, 1, 0, "2026-09-27"))
        assertEquals(2, tokenRequestCount)
        assertEquals(2, processRequestCount)
        assertEquals("Bearer access-1", processConnections[0].getRequestProperty("Authorization"))
        assertEquals("Bearer access-2", processConnections[1].getRequestProperty("Authorization"))
        assertEquals("POST", processConnections[0].requestMethod)
        assertEquals("application/json", processConnections[0].getRequestProperty("Content-Type"))
    }
}