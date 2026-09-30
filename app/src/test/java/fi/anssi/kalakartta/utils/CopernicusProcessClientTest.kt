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
        assertEquals(20, data.getJSONObject("dataFilter").getInt("maxCloudCoverage"))

        val cloudLimitedBody = JSONObject(
            CopernicusProcessRequest.body(1, 1, 0, "2026-09-27", maxCloudCoveragePercent = 63)
        )
        assertEquals(
            63,
            cloudLimitedBody.getJSONObject("input").getJSONArray("data")
                .getJSONObject(0).getJSONObject("dataFilter").getInt("maxCloudCoverage")
        )
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
    fun tileGroupMapsEachRequestedCoordinateToItsCorrectCell() {
        val group = CopernicusTileGroup.fromTile(4, 11, 10, imageSizePixels = 2048)

        assertEquals(8, group.firstX)
        assertEquals(8, group.firstY)
        assertEquals(8, group.columns)
        assertEquals(8, group.rows)
        assertEquals(64, group.coordinates().size)
        assertEquals(CopernicusTileCoordinate(8, 8), group.coordinates().first())
        assertEquals(CopernicusTileCoordinate(15, 15), group.coordinates().last())
        assertEquals(CopernicusTileCropRect(768, 512, 256, 256), group.cropRectFor(11, 10))
        assertEquals(CopernicusTileCropRect(1792, 1792, 256, 256), group.cropRectFor(15, 15))

        val body = JSONObject(CopernicusProcessRequest.body(4, 11, 10, "2026-09-27", 2048))
        val bounds = CopernicusTileBoundsCalculator.fromGroup(group)
        val bbox = body.getJSONObject("input").getJSONObject("bounds").getJSONArray("bbox")
        assertEquals(bounds.minX, bbox.getDouble(0), 0.0001)
        assertEquals(bounds.minY, bbox.getDouble(1), 0.0001)
        assertEquals(bounds.maxX, bbox.getDouble(2), 0.0001)
        assertEquals(bounds.maxY, bbox.getDouble(3), 0.0001)
        val output = body.getJSONObject("output")
        assertEquals(2048, output.getInt("width"))
        assertEquals(2048, output.getInt("height"))
    }

    @Test
    fun configuredImageSizesSetGridDimensionsAndProcessOutput() {
        for (imageSizePixels in CopernicusTileImageSize.OPTIONS_PIXELS) {
            val group = CopernicusTileGroup.fromTile(4, 11, 10, imageSizePixels)
            val tilesPerSide = imageSizePixels / CopernicusTileImageSize.TILE_SIZE_PIXELS
            val body = JSONObject(CopernicusProcessRequest.body(4, 11, 10, "2026-09-27", imageSizePixels))
            val output = body.getJSONObject("output")

            assertEquals(tilesPerSide, group.columns)
            assertEquals(tilesPerSide, group.rows)
            assertEquals(imageSizePixels, group.pixelWidth)
            assertEquals(imageSizePixels, group.pixelHeight)
            assertEquals(imageSizePixels, output.getInt("width"))
            assertEquals(imageSizePixels, output.getInt("height"))
            assertEquals(
                CopernicusTileCropRect(
                    (11 - group.firstX) * CopernicusTileImageSize.TILE_SIZE_PIXELS,
                    (10 - group.firstY) * CopernicusTileImageSize.TILE_SIZE_PIXELS,
                    CopernicusTileImageSize.TILE_SIZE_PIXELS,
                    CopernicusTileImageSize.TILE_SIZE_PIXELS
                ),
                group.cropRectFor(11, 10)
            )
        }
    }

    @Test
    fun lowZoomGroupsAreClampedToWorldDimensions() {
        val configuredTilesPerSide = CopernicusTileImageSize.DEFAULT_IMAGE_SIZE_PIXELS /
                CopernicusTileImageSize.TILE_SIZE_PIXELS
        for (zoom in 0..2) {
            val group = CopernicusTileGroup.fromTile(zoom, 0, 0)
            val body = JSONObject(CopernicusProcessRequest.body(zoom, 0, 0, "2026-09-27"))
            val expectedTilesPerSide = minOf(1 shl zoom, configuredTilesPerSide)
            val expectedSizePixels = expectedTilesPerSide * CopernicusTileImageSize.TILE_SIZE_PIXELS

            assertEquals(expectedTilesPerSide, group.columns)
            assertEquals(expectedTilesPerSide, group.rows)
            assertEquals(expectedSizePixels, body.getJSONObject("output").getInt("width"))
            assertEquals(expectedSizePixels, body.getJSONObject("output").getInt("height"))
        }
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

        assertArrayEquals(jpeg, client.getTile(1, 1, 0, "2026-09-27", maxCloudCoveragePercent = 67))
        assertEquals(2, tokenRequestCount)
        assertEquals(2, processRequestCount)
        assertEquals("Bearer access-1", processConnections[0].getRequestProperty("Authorization"))
        assertEquals("Bearer access-2", processConnections[1].getRequestProperty("Authorization"))
        assertEquals("POST", processConnections[0].requestMethod)
        assertEquals("application/json", processConnections[0].getRequestProperty("Content-Type"))
        for (connection in processConnections) {
            val requestBody = JSONObject(String(connection.requestBody, StandardCharsets.UTF_8))
            assertEquals(
                67,
                requestBody.getJSONObject("input").getJSONArray("data")
                    .getJSONObject(0).getJSONObject("dataFilter").getInt("maxCloudCoverage")
            )
        }
    }
}