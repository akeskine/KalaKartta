package fi.anssi.kalakartta.utils

import org.junit.Assert.assertEquals
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