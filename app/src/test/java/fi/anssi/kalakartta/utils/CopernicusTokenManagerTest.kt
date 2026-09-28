package fi.anssi.kalakartta.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CopernicusTokenManagerTest {
    @Test
    fun tokenIsReusedUntilItsRefreshDeadline() {
        var now = 1_000L
        var requestCount = 0
        val factory = CopernicusHttpConnectionFactory { url ->
            requestCount++
            FakeCopernicusHttpURLConnection(
                url,
                200,
                """{"access_token":"access-$requestCount","expires_in":3600}"""
                    .toByteArray(StandardCharsets.UTF_8)
            )
        }
        val manager = CopernicusTokenManager(
            { CopernicusClientCredentials("client", "secret") },
            factory,
            { now }
        )

        assertEquals("access-1", manager.getAccessToken())
        now += 3_000_000L
        assertEquals("access-1", manager.getAccessToken())
        now += 600_000L
        assertEquals("access-2", manager.getAccessToken())
        assertEquals(2, requestCount)
    }

    @Test
    fun tokenFormIsUrlEncodedAndUnauthorizedTokenCanBeRefreshed() {
        var requestCount = 0
        val connections = mutableListOf<FakeCopernicusHttpURLConnection>()
        val factory = CopernicusHttpConnectionFactory { url: URL ->
            requestCount++
            FakeCopernicusHttpURLConnection(
                url,
                200,
                """{"access_token":"access-$requestCount","expires_in":3600}"""
                    .toByteArray(StandardCharsets.UTF_8)
            ).also { connections.add(it) }
        }
        val manager = CopernicusTokenManager(
            { CopernicusClientCredentials("client id", "secret+value&") },
            factory
        )

        val oldToken = manager.getAccessToken()
        assertEquals("access-1", oldToken)
        assertEquals("access-2", manager.refreshAfterUnauthorized(oldToken))
        assertEquals("access-2", manager.getAccessToken())
        assertEquals(2, requestCount)
        assertEquals("POST", connections.first().requestMethod)
        assertEquals(
            "application/x-www-form-urlencoded",
            connections.first().getRequestProperty("Content-Type")
        )
        assertEquals(
            "grant_type=client_credentials&client_id=client+id&client_secret=secret%2Bvalue%26",
            String(connections.first().requestBody, StandardCharsets.UTF_8)
        )
    }

    @Test
    fun concurrentRequestsShareOneTokenFetch() {
        val requestCount = AtomicInteger()
        val manager = CopernicusTokenManager(
            { CopernicusClientCredentials("client", "secret") },
            CopernicusHttpConnectionFactory { url ->
                val requestNumber = requestCount.incrementAndGet()
                FakeCopernicusHttpURLConnection(
                    url,
                    200,
                    """{"access_token":"access-$requestNumber","expires_in":3600}"""
                        .toByteArray(StandardCharsets.UTF_8)
                )
            }
        )
        val executor = Executors.newFixedThreadPool(8)
        val startTogether = CountDownLatch(1)
        try {
            val requests = (1..8).map {
                executor.submit<String> {
                    startTogether.await()
                    manager.getAccessToken()
                }
            }
            startTogether.countDown()
            assertEquals(List(8) { "access-1" }, requests.map { it.get(3, TimeUnit.SECONDS) })
            assertEquals(1, requestCount.get())
        } finally {
            executor.shutdownNow()
        }
    }
}