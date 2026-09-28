package fi.anssi.kalakartta.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CopernicusTileCacheTest {
    @Test
    fun cachedGroupTilesSkipLoaderAndDateIsPartOfCachePath() {
        val root = Files.createTempDirectory("copernicus-cache-test").toFile()
        val cache = CopernicusTileCache(root)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        var loads = 0

        val first = cache.getOrLoadGroup("2026-09-27", 4, 10, 9, loader = { group ->
            loads++
            assertEquals(8, group.firstX)
            assertEquals(8, group.firstY)
            assertEquals(64, group.coordinates().size)
            jpeg
        }, splitter = ::splitIntoFakeTiles)
        val second = cache.getOrLoadGroup("2026-09-27", 4, 10, 9, loader = {
            loads++
            byteArrayOf()
        }, splitter = ::splitIntoFakeTiles)
        val differentDate = cache.cacheFile("2026-09-28", 13, 2345, 1148)

        assertArrayEquals(jpeg, first)
        assertArrayEquals(jpeg, second)
        assertEquals(1, loads)
        assertTrue(cache.cacheFile("2026-09-27", 4, 10, 9).isFile)
        assertEquals(64, cache.cacheDirectoryTileCount("2026-09-27", 4, 8, 8))
        assertTrue(!differentDate.exists())
        assertEquals("satellite/2026-09-27/13/2345/1148.jpg", cache.cacheFile("2026-09-27", 13, 2345, 1148)
            .relativeTo(root).path.replace('\\', '/'))
    }

    @Test
    fun concurrentRequestsForDifferentTilesInSameGroupShareOneLoad() {
        val root = Files.createTempDirectory("copernicus-cache-concurrent-test").toFile()
        val cache = CopernicusTileCache(root)
        val secondCache = CopernicusTileCache(root)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val loads = AtomicInteger()
        val loadStarted = CountDownLatch(1)
        val allowLoadToFinish = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<ByteArray> {
                cache.getOrLoadGroup("2026-09-27", 3, 2, 2, loader = {
                    loads.incrementAndGet()
                    loadStarted.countDown()
                    allowLoadToFinish.await(3, TimeUnit.SECONDS)
                    jpeg
                }, splitter = ::splitIntoFakeTiles)
            }
            assertTrue(loadStarted.await(3, TimeUnit.SECONDS))
            val second = executor.submit<ByteArray> {
                secondCache.getOrLoadGroup("2026-09-27", 3, 3, 2, loader = {
                    loads.incrementAndGet()
                    jpeg
                }, splitter = ::splitIntoFakeTiles)
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

    @Test
    fun cacheLimitRemovesOldestTilesWithoutExceedingConfiguredSize() {
        val root = Files.createTempDirectory("copernicus-cache-limit-test").toFile()
        val cache = CopernicusTileCache(root)
        val oldest = cache.cacheFile("2026-09-27", 2, 0, 0)
        val newest = cache.cacheFile("2026-09-28", 2, 0, 0)
        oldest.parentFile?.mkdirs()
        newest.parentFile?.mkdirs()
        oldest.writeBytes(byteArrayOf(1, 2, 3, 4))
        newest.writeBytes(byteArrayOf(5, 6, 7, 8))
        oldest.setLastModified(1_000)
        newest.setLastModified(2_000)

        val cachedBytes = cache.trimToSize(maxSizeBytes = 4)

        assertEquals(4, cachedBytes)
        assertFalse(oldest.exists())
        assertTrue(newest.exists())
    }

    private fun splitIntoFakeTiles(
        @Suppress("UNUSED_PARAMETER") image: ByteArray,
        group: CopernicusTileGroup
    ): Map<CopernicusTileCoordinate, ByteArray> = group.coordinates().associateWith {
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    }

    private fun CopernicusTileCache.cacheDirectoryTileCount(
        imageDate: String,
        zoom: Int,
        firstX: Int,
        firstY: Int
    ): Int = CopernicusTileGroup.fromTile(zoom, firstX, firstY).coordinates().count { coordinate ->
        cacheFile(imageDate, zoom, coordinate.x, coordinate.y).isFile
    }
}