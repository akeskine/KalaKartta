package fi.anssi.kalakartta.utils

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CopernicusTileBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
)

data class CopernicusTileCoordinate(val x: Int, val y: Int)

data class CopernicusTileCropRect(val left: Int, val top: Int, val width: Int, val height: Int)

object CopernicusTileImageSize {
    const val TILE_SIZE_PIXELS = 256
    const val DEFAULT_IMAGE_SIZE_PIXELS = 1024
    val OPTIONS_PIXELS = listOf(256, 512, 1024, 2048)

    fun normalize(imageSizePixels: Int): Int =
        imageSizePixels.takeIf { it in OPTIONS_PIXELS } ?: DEFAULT_IMAGE_SIZE_PIXELS
}

data class CopernicusTileGroup(
    val zoom: Int,
    val firstX: Int,
    val firstY: Int,
    val columns: Int,
    val rows: Int,
    val imageSizePixels: Int
) {
    val pixelWidth: Int get() = columns * CopernicusTileImageSize.TILE_SIZE_PIXELS
    val pixelHeight: Int get() = rows * CopernicusTileImageSize.TILE_SIZE_PIXELS

    fun coordinates(): List<CopernicusTileCoordinate> = buildList {
        for (y in firstY until firstY + rows) {
            for (x in firstX until firstX + columns) add(CopernicusTileCoordinate(x, y))
        }
    }

    fun cropRectFor(x: Int, y: Int): CopernicusTileCropRect {
        require(x in firstX until firstX + columns && y in firstY until firstY + rows) {
            "Tile is outside its Copernicus image group"
        }
        return CopernicusTileCropRect(
            left = (x - firstX) * CopernicusTileImageSize.TILE_SIZE_PIXELS,
            top = (y - firstY) * CopernicusTileImageSize.TILE_SIZE_PIXELS,
            width = CopernicusTileImageSize.TILE_SIZE_PIXELS,
            height = CopernicusTileImageSize.TILE_SIZE_PIXELS
        )
    }

    companion object {
        fun fromTile(
            zoom: Int,
            x: Int,
            y: Int,
            imageSizePixels: Int = CopernicusTileImageSize.DEFAULT_IMAGE_SIZE_PIXELS
        ): CopernicusTileGroup {
            require(zoom in 0..30) { "Zoom level is outside the supported range" }
            val tileCount = 1L shl zoom
            require(x >= 0 && x.toLong() < tileCount) { "Tile x is outside the zoom level" }
            require(y >= 0 && y.toLong() < tileCount) { "Tile y is outside the zoom level" }
            require(imageSizePixels in CopernicusTileImageSize.OPTIONS_PIXELS) {
                "Unsupported Copernicus image size"
            }
            val tilesPerGroup = imageSizePixels / CopernicusTileImageSize.TILE_SIZE_PIXELS
            val firstX = x / tilesPerGroup * tilesPerGroup
            val firstY = y / tilesPerGroup * tilesPerGroup
            return CopernicusTileGroup(
                zoom = zoom,
                firstX = firstX,
                firstY = firstY,
                columns = minOf(tilesPerGroup, (tileCount - firstX).toInt()),
                rows = minOf(tilesPerGroup, (tileCount - firstY).toInt()),
                imageSizePixels = imageSizePixels
            )
        }
    }
}

object CopernicusTileBoundsCalculator {
    private const val EARTH_RADIUS_METERS = 6_378_137.0
    private val halfWorldMeters = Math.PI * EARTH_RADIUS_METERS

    fun fromTile(zoom: Int, x: Int, y: Int): CopernicusTileBounds {
        require(zoom in 0..30) { "Zoom level is outside the supported range" }
        val tileCount = 1L shl zoom
        require(x >= 0 && x.toLong() < tileCount) { "Tile x is outside the zoom level" }
        require(y >= 0 && y.toLong() < tileCount) { "Tile y is outside the zoom level" }

        val tileSpan = 2.0 * halfWorldMeters / tileCount
        val minX = -halfWorldMeters + x * tileSpan
        val maxY = halfWorldMeters - y * tileSpan
        return CopernicusTileBounds(minX, maxY - tileSpan, minX + tileSpan, maxY)
    }

    fun fromGroup(group: CopernicusTileGroup): CopernicusTileBounds {
        val northWestTile = fromTile(group.zoom, group.firstX, group.firstY)
        val tileSpan = northWestTile.maxX - northWestTile.minX
        return CopernicusTileBounds(
            minX = northWestTile.minX,
            minY = northWestTile.maxY - tileSpan * group.rows,
            maxX = northWestTile.minX + tileSpan * group.columns,
            maxY = northWestTile.maxY
        )
    }
}

object CopernicusProcessRequest {
    const val CRS_EPSG_3857 = "http://www.opengis.net/def/crs/EPSG/0/3857"

    private const val EVALSCRIPT = """//VERSION=3
function setup() {
    return {
        input: ["B02", "B03", "B04"],
        output: {
            bands: 3,
            sampleType: "AUTO"
        }
    };
}

function evaluatePixel(s) {
    return [
        2.5 * s.B04,
        2.5 * s.B03,
        2.5 * s.B02
    ];
}"""

    fun body(
        zoom: Int,
        x: Int,
        y: Int,
        imageDate: String,
        imageSizePixels: Int = CopernicusTileImageSize.DEFAULT_IMAGE_SIZE_PIXELS
    ): String {
        val group = CopernicusTileGroup.fromTile(zoom, x, y, imageSizePixels)
        val bbox = CopernicusTileBoundsCalculator.fromGroup(group)
        val dateRange = dateRange(imageDate)
        val bounds = JSONObject()
            .put("bbox", JSONArray().put(bbox.minX).put(bbox.minY).put(bbox.maxX).put(bbox.maxY))
            .put("properties", JSONObject().put("crs", CRS_EPSG_3857))
        val dataFilter = JSONObject().put(
            "timeRange",
            JSONObject().put("from", dateRange.first).put("to", dateRange.second)
        )
        val input = JSONObject()
            .put("bounds", bounds)
            .put("data", JSONArray().put(JSONObject().put("type", "sentinel-2-l2a").put("dataFilter", dataFilter)))
        val output = JSONObject()
            .put("width", group.pixelWidth)
            .put("height", group.pixelHeight)
            .put(
                "responses",
                JSONArray().put(
                    JSONObject()
                        .put("identifier", "default")
                        .put("format", JSONObject().put("type", "image/jpeg"))
                )
            )
        return JSONObject()
            .put("input", input)
            .put("output", output)
            .put("evalscript", EVALSCRIPT)
            .toString()
    }

    private fun dateRange(imageDate: String): Pair<String, String> {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedDate = formatter.parse(imageDate)
            ?: throw IllegalArgumentException("Invalid Copernicus image date")
        require(formatter.format(parsedDate) == imageDate) { "Invalid Copernicus image date" }
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            time = parsedDate
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return "${imageDate}T00:00:00Z" to "${formatter.format(calendar.time)}T00:00:00Z"
    }
}

class CopernicusProcessClient(
    private val tokenManager: CopernicusTokenManager,
    private val connectionFactory: CopernicusHttpConnectionFactory = CopernicusHttpConnectionFactory {
        it.openConnection() as HttpURLConnection
    }
) {
    fun getTile(
        zoom: Int,
        x: Int,
        y: Int,
        imageDate: String,
        imageSizePixels: Int = CopernicusTileImageSize.DEFAULT_IMAGE_SIZE_PIXELS
    ): ByteArray {
        val requestBody = CopernicusProcessRequest.body(zoom, x, y, imageDate, imageSizePixels)
            .toByteArray(StandardCharsets.UTF_8)
        var token = tokenManager.getAccessToken()

        for (attempt in 0..1) {
            val connection = connectionFactory.open(URL(CopernicusTileSource.PROCESS_API_URL))
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECTION_TIMEOUT_MILLIS
                connection.readTimeout = CONNECTION_TIMEOUT_MILLIS
                connection.useCaches = false
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { output -> output.write(requestBody) }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && attempt == 0) {
                    token = tokenManager.refreshAfterUnauthorized(token)
                    continue
                }
                if (responseCode !in 200..299) {
                    throw IOException("Copernicus Process API returned HTTP $responseCode")
                }

                val image = connection.inputStream.use { it.readBytes() }
                if (!isJpeg(image)) throw IOException("Copernicus Process API returned an invalid JPEG")
                return image
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Copernicus Process API authorization failed")
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                bytes[bytes.lastIndex - 1] == 0xFF.toByte() && bytes.last() == 0xD9.toByte()

    companion object {
        private const val CONNECTION_TIMEOUT_MILLIS = 30_000
    }
}