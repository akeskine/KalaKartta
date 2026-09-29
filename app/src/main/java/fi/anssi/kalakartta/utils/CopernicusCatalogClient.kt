package fi.anssi.kalakartta.utils

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class CopernicusCatalogClient(
    private val connectionFactory: CopernicusHttpConnectionFactory = CopernicusHttpConnectionFactory {
        it.openConnection() as HttpURLConnection
    }
) {
    fun findLatestSceneDate(tileId: String, targetDate: String): String? {
        val connection = connectionFactory.open(buildUrl(tileId, targetDate))
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECTION_TIMEOUT_MILLIS
            connection.readTimeout = CONNECTION_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) throw IOException("Copernicus catalog returned HTTP $responseCode")
            val response = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val products = JSONObject(response).optJSONArray("value") ?: return null
            if (products.length() == 0) return null

            val contentDate = products.getJSONObject(0).optJSONObject("ContentDate") ?: return null
            val start = contentDate.optString("Start").takeIf { it.isNotBlank() } ?: return null
            val sceneDate = start.substringBefore('T')
            validateDate(sceneDate)
            return sceneDate.takeIf { it <= targetDate }
        } finally {
            connection.disconnect()
        }
    }

    fun buildUrl(tileId: String, targetDate: String): URL {
        require(tileId.matches(MGRS_TILE_ID_PATTERN)) { "Invalid Sentinel MGRS tileId" }
        val exclusiveEnd = followingDate(targetDate)
        val filter = "Collection/Name eq 'SENTINEL-2'" +
                " and Attributes/OData.CSC.StringAttribute/any(att:att/Name eq 'productType'" +
                " and att/OData.CSC.StringAttribute/Value eq 'S2MSI2A')" +
                " and Attributes/OData.CSC.StringAttribute/any(att:att/Name eq 'tileId'" +
                " and att/OData.CSC.StringAttribute/Value eq '$tileId')" +
                " and Attributes/OData.CSC.DoubleAttribute/any(att:att/Name eq 'cloudCover'" +
                " and att/OData.CSC.DoubleAttribute/Value lt 20)" +
                " and ContentDate/Start lt $exclusiveEnd"
        val query = listOf(
            "\$filter=${encode(filter)}",
            "\$orderby=${encode("ContentDate/Start desc")}",
            "\$top=1",
            "\$select=${encode("Name,ContentDate")}"
        ).joinToString("&")
        return URL("$CATALOG_ENDPOINT?$query")
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun followingDate(value: String): String {
        val formatter = SimpleDateFormat(DATE_FORMAT, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsed = formatter.parse(value) ?: throw IllegalArgumentException("Invalid target date")
        require(formatter.format(parsed) == value) { "Invalid target date" }
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            time = parsed
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return "${formatter.format(calendar.time)}T00:00:00.000Z"
    }

    private fun validateDate(value: String) {
        val formatter = SimpleDateFormat(DATE_FORMAT, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsed = formatter.parse(value) ?: throw IOException("Catalog returned an invalid scene date")
        if (formatter.format(parsed) != value) throw IOException("Catalog returned an invalid scene date")
    }

    companion object {
        const val CATALOG_ENDPOINT = "https://catalogue.dataspace.copernicus.eu/odata/v1/Products"
        const val FILTER_VERSION = "sentinel2-l2a-cloud20-v2"
        private const val CONNECTION_TIMEOUT_MILLIS = 20_000
        private const val DATE_FORMAT = "yyyy-MM-dd"
        private val MGRS_TILE_ID_PATTERN = Regex("(?:[1-9]|[1-5][0-9]|60)[C-HJ-NP-X][A-HJ-NP-Z]{2}")
    }
}