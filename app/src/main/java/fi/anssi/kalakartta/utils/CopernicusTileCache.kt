package fi.anssi.kalakartta.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CopernicusTileCache(
    private val rootDirectory: File,
    private val maximumSizeBytes: () -> Long = { DEFAULT_MAXIMUM_SIZE_BYTES }
) {
    private val cleanupScheduled = AtomicBoolean(false)
    private val fileLock = lockFor(rootDirectory.absolutePath)

    fun getOrLoadGroup(
        imageDate: String,
        zoom: Int,
        x: Int,
        y: Int,
        imageSizePixels: Int = CopernicusTileImageSize.DEFAULT_IMAGE_SIZE_PIXELS,
        maxCloudCoveragePercent: Int = CopernicusCloudCoverage.DEFAULT_PERCENT,
        loader: (CopernicusTileGroup) -> ByteArray,
        splitter: (ByteArray, CopernicusTileGroup) -> Map<CopernicusTileCoordinate, ByteArray>
    ): ByteArray {
        getCached(imageDate, zoom, x, y, maxCloudCoveragePercent)?.let { return it }
        val group = CopernicusTileGroup.fromTile(zoom, x, y, imageSizePixels)
        val coordinate = CopernicusTileCoordinate(x, y)
        val groupKey = "${cacheFile(imageDate, zoom, group.firstX, group.firstY, maxCloudCoveragePercent).absolutePath}/" +
                group.imageSizePixels
        val newLoad = CompletableFuture<Map<CopernicusTileCoordinate, ByteArray>>()
        val activeLoad = IN_FLIGHT_GROUP_LOADS.putIfAbsent(groupKey, newLoad)
        if (activeLoad != null) {
            return await(activeLoad)[coordinate]
                ?: getCached(imageDate, zoom, x, y, maxCloudCoveragePercent)
                ?: throw IOException("Copernicus image group did not contain the requested tile")
        }

        try {
            val expectedCoordinates = group.coordinates().toSet()
            val cachedGroup = expectedCoordinates.mapNotNull { cachedCoordinate ->
                getCached(imageDate, zoom, cachedCoordinate.x, cachedCoordinate.y, maxCloudCoveragePercent)
                    ?.let { cachedCoordinate to it }
            }.toMap()
            if (cachedGroup.keys == expectedCoordinates) {
                newLoad.complete(cachedGroup)
                return cachedGroup.getValue(coordinate)
            }
            val groupImage = loader(group)
            val tiles = splitter(groupImage, group)
            if (tiles.keys != expectedCoordinates || tiles.values.any { !isJpeg(it) }) {
                throw IOException("Copernicus image could not be split into valid JPEG tiles")
            }
            tiles.forEach { (tileCoordinate, jpeg) ->
                val tileFile = cacheFile(
                    imageDate,
                    zoom,
                    tileCoordinate.x,
                    tileCoordinate.y,
                    maxCloudCoveragePercent
                )
                if (readCache(tileFile) == null) writeCache(tileFile, jpeg)
            }
            enforceSizeLimitAsync()
            newLoad.complete(tiles)
            return tiles[coordinate] ?: throw IOException("Copernicus image group did not contain the requested tile")
        } catch (failure: Throwable) {
            newLoad.completeExceptionally(failure)
            throw failure
        } finally {
            IN_FLIGHT_GROUP_LOADS.remove(groupKey, newLoad)
        }
    }

    fun getCached(
        imageDate: String,
        zoom: Int,
        x: Int,
        y: Int,
        maxCloudCoveragePercent: Int = CopernicusCloudCoverage.DEFAULT_PERCENT
    ): ByteArray? = readCache(cacheFile(imageDate, zoom, x, y, maxCloudCoveragePercent))

    fun putCached(
        imageDate: String,
        zoom: Int,
        x: Int,
        y: Int,
        jpeg: ByteArray,
        maxCloudCoveragePercent: Int = CopernicusCloudCoverage.DEFAULT_PERCENT
    ) {
        if (!isJpeg(jpeg)) throw IOException("Copernicus tile cache accepts JPEG images only")
        writeCache(cacheFile(imageDate, zoom, x, y, maxCloudCoveragePercent), jpeg, replaceExisting = true)
        enforceSizeLimitAsync()
    }

    fun sizeBytes(): Long = synchronized(fileLock) {
        val cacheRoot = File(rootDirectory, "satellite")
        if (!cacheRoot.isDirectory) return@synchronized 0L
        cacheRoot.walkTopDown()
            .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
            .sumOf(File::length)
    }

    fun cacheFile(
        imageDate: String,
        zoom: Int,
        x: Int,
        y: Int,
        maxCloudCoveragePercent: Int = CopernicusCloudCoverage.DEFAULT_PERCENT
    ): File {
        require(imageDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Invalid Copernicus image date" }
        CopernicusCloudCoverage.requireValid(maxCloudCoveragePercent)
        require(zoom in 0..30) { "Zoom level is outside the supported range" }
        val tileCount = 1L shl zoom
        require(x >= 0 && x.toLong() < tileCount && y >= 0 && y.toLong() < tileCount) {
            "Tile coordinates are outside the zoom level"
        }
        return File(rootDirectory, "satellite/$imageDate/$maxCloudCoveragePercent/$zoom/$x/$y.jpg")
    }

    fun remove(
        imageDate: String,
        zoom: Int,
        x: Int,
        y: Int,
        maxCloudCoveragePercent: Int = CopernicusCloudCoverage.DEFAULT_PERCENT
    ) {
        synchronized(fileLock) {
            cacheFile(imageDate, zoom, x, y, maxCloudCoveragePercent).delete()
        }
    }

    fun enforceSizeLimitAsync(maxSizeBytes: Long = maximumSizeBytes()) {
        if (!cleanupScheduled.compareAndSet(false, true)) return
        CACHE_CLEANUP_EXECUTOR.execute {
            try {
                trimToSize(maxSizeBytes)
            } finally {
                cleanupScheduled.set(false)
            }
        }
    }

    fun trimToSize(maxSizeBytes: Long): Long = synchronized(fileLock) {
        val files = cachedJpegFiles().sortedBy(File::lastModified)
        var totalBytes = files.sumOf(File::length)
        for (file in files) {
            if (totalBytes <= maxSizeBytes.coerceAtLeast(0)) break
            val fileSize = file.length()
            if (file.delete()) totalBytes -= fileSize
        }
        totalBytes
    }

    private fun cachedJpegFiles(): List<File> {
        val cacheRoot = File(rootDirectory, "satellite")
        if (!cacheRoot.isDirectory) return emptyList()
        return cacheRoot.walkTopDown().filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }.toList()
    }

    private fun readCache(file: File): ByteArray? {
        if (!file.isFile) return null
        return try {
            file.readBytes().takeIf(::isJpeg) ?: run {
                synchronized(fileLock) { file.delete() }
                null
            }
        } catch (_: IOException) {
            synchronized(fileLock) { file.delete() }
            null
        }
    }

    private fun writeCache(file: File, bytes: ByteArray, replaceExisting: Boolean = false) {
        synchronized(fileLock) {
            if (!replaceExisting && file.isFile && runCatching { isJpeg(file.readBytes()) }.getOrDefault(false)) return
            val parent = file.parentFile ?: throw IOException("Invalid Copernicus cache path")
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to create Copernicus tile cache")
            val temporaryFile = File(parent, "${file.name}.${Thread.currentThread().id}.${System.nanoTime()}.tmp")
            try {
                FileOutputStream(temporaryFile).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                if (file.exists() && !file.delete()) throw IOException("Unable to replace Copernicus tile cache")
                if (!temporaryFile.renameTo(file)) throw IOException("Unable to persist Copernicus tile cache")
            } finally {
                temporaryFile.delete()
            }
        }
    }

    private fun <T> await(future: CompletableFuture<T>): T {
        return try {
            future.get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                bytes[bytes.lastIndex - 1] == 0xFF.toByte() && bytes.last() == 0xD9.toByte()

    companion object {
        const val BYTES_PER_MEGABYTE = 1024L * 1024
        const val DEFAULT_MAXIMUM_SIZE_BYTES = 512L * 1024 * 1024

        private val CACHE_CLEANUP_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "CopernicusCacheCleanup").apply { isDaemon = true }
        }
        private val FILE_LOCKS = ConcurrentHashMap<String, Any>()
        private val IN_FLIGHT_GROUP_LOADS =
            ConcurrentHashMap<String, CompletableFuture<Map<CopernicusTileCoordinate, ByteArray>>>()

        private fun lockFor(path: String): Any = FILE_LOCKS.computeIfAbsent(path) { Any() }
    }
}