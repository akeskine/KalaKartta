package fi.anssi.kalakartta.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CopernicusSceneDateResolverTest {
    @Test
    fun selectedSceneDateIsCachedPerTileAndTargetForTwentyFourHours() {
        var now = 1_000L
        var catalogRequests = 0
        val catalog = CopernicusCatalogClient(CopernicusHttpConnectionFactory { url ->
            catalogRequests++
            FakeCopernicusHttpURLConnection(
                url,
                200,
                """{"value":[{"ContentDate":{"Start":"2026-09-27T09:41:21.024Z"}}]}"""
                    .toByteArray(StandardCharsets.UTF_8)
            )
        })
        val resolver = CopernicusSceneDateResolver(catalog, InMemorySceneDateCacheStore()) { now }

        assertEquals("2026-09-27", resolver.resolveSceneDate("35VLG", "2026-09-28"))
        assertEquals("2026-09-27", resolver.resolveSceneDate("35VLG", "2026-09-28"))
        assertEquals(1, catalogRequests)

        assertEquals("2026-09-27", resolver.resolveSceneDate("35VLG", "2026-09-29"))
        assertEquals(2, catalogRequests)

        now += CopernicusSceneDateResolver.CACHE_TTL_MILLIS + 1
        assertEquals("2026-09-27", resolver.resolveSceneDate("35VLG", "2026-09-28"))
        assertEquals(3, catalogRequests)
    }

    @Test
    fun changingCloudCoverageLimitUsesASeparateCatalogCacheEntry() {
        var catalogRequests = 0
        val catalog = CopernicusCatalogClient(CopernicusHttpConnectionFactory { url ->
            catalogRequests++
            FakeCopernicusHttpURLConnection(
                url,
                200,
                """{"value":[{"ContentDate":{"Start":"2026-09-27T09:41:21.024Z"}}]}"""
                    .toByteArray(StandardCharsets.UTF_8)
            )
        })
        val resolver = CopernicusSceneDateResolver(catalog, InMemorySceneDateCacheStore())

        assertEquals("2026-09-27", resolver.resolveSceneDate("35VLG", "2026-09-28", 20))
        assertEquals("2026-09-27", resolver.resolveSceneDate("35VLG", "2026-09-28", 40))
        assertEquals(2, catalogRequests)
    }

    @Test
    fun temporaryCatalogFailureIsRetriedInsteadOfCachedAsNoScene() {
        var catalogRequests = 0
        val catalog = CopernicusCatalogClient(CopernicusHttpConnectionFactory { url ->
            catalogRequests++
            if (catalogRequests == 1) {
                FakeCopernicusHttpURLConnection(url, 503, ByteArray(0))
            } else {
                FakeCopernicusHttpURLConnection(
                    url,
                    200,
                    """{"value":[{"ContentDate":{"Start":"2026-02-23T10:00:31.025Z"}}]}"""
                        .toByteArray(StandardCharsets.UTF_8)
                )
            }
        })
        val resolver = CopernicusSceneDateResolver(catalog, InMemorySceneDateCacheStore())

        assertTrue(runCatching { resolver.resolveSceneDate("35VMH", "2026-02-23") }.isFailure)
        assertEquals("2026-02-23", resolver.resolveSceneDate("35VMH", "2026-02-23"))
        assertEquals(2, catalogRequests)
    }

    @Test
    fun emptyCatalogResultIsNotPersistedAsAFullDayNegativeCacheEntry() {
        var catalogRequests = 0
        val catalog = CopernicusCatalogClient(CopernicusHttpConnectionFactory { url ->
            catalogRequests++
            val response = if (catalogRequests == 1) {
                """{"value":[]}"""
            } else {
                """{"value":[{"ContentDate":{"Start":"2026-02-23T10:00:31.025Z"}}]}"""
            }
            FakeCopernicusHttpURLConnection(url, 200, response.toByteArray(StandardCharsets.UTF_8))
        })
        val resolver = CopernicusSceneDateResolver(catalog, InMemorySceneDateCacheStore())

        assertNull(resolver.resolveSceneDate("35VMH", "2026-02-23"))
        assertEquals("2026-02-23", resolver.resolveSceneDate("35VMH", "2026-02-23"))
        assertEquals(2, catalogRequests)
    }

    @Test
    fun previousSceneLookupSearchesStrictlyBeforeTheBlackSceneDate() {
        val requestedUrls = mutableListOf<String>()
        val catalog = CopernicusCatalogClient(CopernicusHttpConnectionFactory { url ->
            requestedUrls += url.toString()
            FakeCopernicusHttpURLConnection(
                url,
                200,
                """{"value":[{"ContentDate":{"Start":"2026-02-22T10:00:31.025Z"}}]}"""
                    .toByteArray(StandardCharsets.UTF_8)
            )
        })
        val resolver = CopernicusSceneDateResolver(catalog, InMemorySceneDateCacheStore())

        assertEquals("2026-02-22", resolver.resolvePreviousSceneDate("35VMH", "2026-02-23"))
        val query = java.net.URLDecoder.decode(
            java.net.URL(requestedUrls.single()).query,
            StandardCharsets.UTF_8.name()
        )
        assertTrue(query.contains("ContentDate/Start lt 2026-02-23T00:00:00.000Z"))
    }

    @Test
    fun concurrentRequestsForSameTileShareOneCatalogLookup() {
        val catalogRequests = AtomicInteger()
        val catalogRequestStarted = CountDownLatch(1)
        val allowCatalogRequestToFinish = CountDownLatch(1)
        val catalog = CopernicusCatalogClient(CopernicusHttpConnectionFactory { url ->
            catalogRequests.incrementAndGet()
            catalogRequestStarted.countDown()
            allowCatalogRequestToFinish.await(3, TimeUnit.SECONDS)
            FakeCopernicusHttpURLConnection(
                url,
                200,
                """{"value":[{"ContentDate":{"Start":"2026-09-27T09:41:21.024Z"}}]}"""
                    .toByteArray(StandardCharsets.UTF_8)
            )
        })
        val resolver = CopernicusSceneDateResolver(catalog, InMemorySceneDateCacheStore())
        val executor = Executors.newFixedThreadPool(4)
        val startTogether = CountDownLatch(1)
        try {
            val lookups = (1..4).map {
                executor.submit<String?> {
                    startTogether.await()
                    resolver.resolveSceneDate("35VLG", "2026-09-28")
                }
            }
            startTogether.countDown()
            assertEquals(true, catalogRequestStarted.await(3, TimeUnit.SECONDS))
            allowCatalogRequestToFinish.countDown()

            assertEquals(List(4) { "2026-09-27" }, lookups.map { it.get(3, TimeUnit.SECONDS) })
            assertEquals(1, catalogRequests.get())
        } finally {
            allowCatalogRequestToFinish.countDown()
            executor.shutdownNow()
        }
    }

    private class InMemorySceneDateCacheStore : CopernicusSceneDateCacheStore {
        private val entries = mutableMapOf<String, CopernicusSceneDateCacheEntry>()

        override fun get(key: String): CopernicusSceneDateCacheEntry? = entries[key]

        override fun put(key: String, entry: CopernicusSceneDateCacheEntry) {
            entries[key] = entry
        }
    }
}