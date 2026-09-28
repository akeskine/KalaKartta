package fi.anssi.kalakartta.utils

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class CopernicusClientCredentials(val clientId: String, val clientSecret: String)

fun interface CopernicusHttpConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

class CopernicusTokenManager(
    private val credentialsProvider: () -> CopernicusClientCredentials,
    private val connectionFactory: CopernicusHttpConnectionFactory = CopernicusHttpConnectionFactory {
        it.openConnection() as HttpURLConnection
    },
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) {
    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var refreshAtMillis: Long = 0

    @Synchronized
    fun getAccessToken(): String {
        val cached = accessToken
        if (cached != null && currentTimeMillis() < refreshAtMillis) return cached
        return requestToken()
    }

    @Synchronized
    fun refreshAfterUnauthorized(rejectedToken: String): String {
        if (accessToken == rejectedToken) {
            accessToken = null
            refreshAtMillis = 0
        }
        return getAccessToken()
    }

    private fun requestToken(): String {
        val credentials = credentialsProvider()
        if (credentials.clientId.isBlank() || credentials.clientSecret.isBlank()) {
            throw IOException("Copernicus credentials are not configured")
        }

        val formBody = "grant_type=client_credentials" +
                "&client_id=${encode(credentials.clientId)}" +
                "&client_secret=${encode(credentials.clientSecret)}"
        val connection = connectionFactory.open(URL(TOKEN_ENDPOINT))
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECTION_TIMEOUT_MILLIS
            connection.readTimeout = CONNECTION_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { output -> output.write(formBody.toByteArray(StandardCharsets.UTF_8)) }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) throw IOException("Copernicus token endpoint returned HTTP $responseCode")

            val response = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val json = JSONObject(response)
            val token = json.getString("access_token")
            val lifetimeSeconds = json.getLong("expires_in")
            if (token.isBlank() || lifetimeSeconds <= 0) throw IOException("Invalid Copernicus token response")

            val refreshMarginSeconds = minOf(60L, maxOf(1L, lifetimeSeconds / 10))
            accessToken = token
            refreshAtMillis = currentTimeMillis() + maxOf(0L, lifetimeSeconds - refreshMarginSeconds) * 1000L
            return token
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        const val TOKEN_ENDPOINT =
            "https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token"
        private const val CONNECTION_TIMEOUT_MILLIS = 20_000
    }
}