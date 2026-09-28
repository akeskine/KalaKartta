package fi.anssi.kalakartta.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

class CopernicusTileCache(private val rootDirectory: File) {
    private val inFlightLoads = ConcurrentHashMap<String, CompletableFuture<ByteArray>>()

    fun getOrLoad(imageDate: String, zoom: Int, x: Int, y: Int, loader: () -> ByteArray): ByteArray {
        getCached(imageDate, zoom, x, y)?.let { return it }
        val cacheFile = cacheFile(imageDate, zoom, x, y)

        val key = cacheFile.absolutePath
        val newLoad = CompletableFuture<ByteArray>()
        val activeLoad = inFlightLoads.putIfAbsent(key, newLoad)
        if (activeLoad != null) return await(activeLoad)

        try {
            readCache(cacheFile)?.let {
                newLoad.complete(it)
                return it
            }
            val loaded = loader()
            if (!isJpeg(loaded)) throw IOException("Copernicus tile response is not a JPEG")
            writeCache(cacheFile, loaded)
            newLoad.complete(loaded)
            return loaded
        } catch (failure: Throwable) {
            newLoad.completeExceptionally(failure)
            throw failure
        } finally {
            inFlightLoads.remove(key, newLoad)
        }
    }

    fun getCached(imageDate: String, zoom: Int, x: Int, y: Int): ByteArray? =
        readCache(cacheFile(imageDate, zoom, x, y))

    fun cacheFile(imageDate: String, zoom: Int, x: Int, y: Int): File {
        require(imageDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Invalid Copernicus image date" }
        require(zoom in 0..30) { "Zoom level is outside the supported range" }
        val tileCount = 1L shl zoom
        require(x >= 0 && x.toLong() < tileCount && y >= 0 && y.toLong() < tileCount) {
            "Tile coordinates are outside the zoom level"
        }
        return File(rootDirectory, "satellite/$imageDate/$zoom/$x/$y.jpg")
    }

    fun remove(imageDate: String, zoom: Int, x: Int, y: Int) {
        cacheFile(imageDate, zoom, x, y).delete()
    }

    private fun readCache(file: File): ByteArray? {
        if (!file.isFile) return null
        return try {
            file.readBytes().takeIf(::isJpeg) ?: run {
                file.delete()
                null
            }
        } catch (_: IOException) {
            file.delete()
            null
        }
    }

    private fun writeCache(file: File, bytes: ByteArray) {
        val parent = file.parentFile ?: throw IOException("Invalid Copernicus cache path")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to create Copernicus tile cache")
        val temporaryFile = File(file.parentFile, "${file.name}.${Thread.currentThread().id}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!temporaryFile.renameTo(file)) throw IOException("Unable to persist Copernicus tile cache")
        } finally {
            temporaryFile.delete()
        }
    }

    private fun await(future: CompletableFuture<ByteArray>): ByteArray {
        return try {
            future.get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                bytes[bytes.lastIndex - 1] == 0xFF.toByte() && bytes.last() == 0xD9.toByte()
}