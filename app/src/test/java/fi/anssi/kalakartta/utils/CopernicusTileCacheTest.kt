package fi.anssi.kalakartta.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CopernicusTileCacheTest {
    @Test
    fun cachedJpegSkipsLoaderAndDateIsPartOfCachePath() {
        val root = Files.createTempDirectory("copernicus-cache-test").toFile()
        val cache = CopernicusTileCache(root)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        var loads = 0

        val first = cache.getOrLoad("2026-09-27", 13, 2345, 1148) {
            loads++
            jpeg
        }
        val second = cache.getOrLoad("2026-09-27", 13, 2345, 1148) {
            loads++
            byteArrayOf()
        }
        val differentDate = cache.cacheFile("2026-09-28", 13, 2345, 1148)

        assertArrayEquals(jpeg, first)
        assertArrayEquals(jpeg, second)
        assertEquals(1, loads)
        assertTrue(cache.cacheFile("2026-09-27", 13, 2345, 1148).isFile)
        assertTrue(!differentDate.exists())
        assertEquals("satellite/2026-09-27/13/2345/1148.jpg", cache.cacheFile("2026-09-27", 13, 2345, 1148)
            .relativeTo(root).path.replace('\\', '/'))
    }

    @Test
    fun concurrentRequestsForSameTileShareOneLoad() {
        val root = Files.createTempDirectory("copernicus-cache-concurrent-test").toFile()
        val cache = CopernicusTileCache(root)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val loads = AtomicInteger()
        val loadStarted = CountDownLatch(1)
        val allowLoadToFinish = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<ByteArray> {
                cache.getOrLoad("2026-09-27", 13, 2345, 1148) {
                    loads.incrementAndGet()
                    loadStarted.countDown()
                    allowLoadToFinish.await(3, TimeUnit.SECONDS)
                    jpeg
                }
            }
            assertTrue(loadStarted.await(3, TimeUnit.SECONDS))
            val second = executor.submit<ByteArray> {
                cache.getOrLoad("2026-09-27", 13, 2345, 1148) {
                    loads.incrementAndGet()
                    jpeg
                }
            }
            allowLoadToFinish.countDown()

            assertArrayEquals(jpeg, first.get(3, TimeUnit.SECONDS))
            assertArrayEquals(jpeg, second.get(3, TimeUnit.SECONDS))
            assertEquals(1, loads.get())
        } finally {
            allowLoadToFinish.countDown()
            executor.shutdownNow()
        }
    }
}