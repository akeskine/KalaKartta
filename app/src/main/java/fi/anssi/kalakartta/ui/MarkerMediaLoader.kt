package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.Media
import fi.anssi.kalakartta.data.MediaService
import java.io.File

/** Keeps marker media lookup and local-file access out of dialog rendering code. */
class MarkerMediaLoader(context: Context) {
    private val mediaService = MediaService(context)
    private val mediaDirectory = File(context.filesDir, "media")

    fun getForCatch(fish: FishCatch): List<Media> {
        return mediaService.getMediaForPoint(fish.latitude, fish.longitude, fish.caughtAt)
    }

    fun getForPlace(latitude: Double, longitude: Double): List<Media> {
        return mediaService.getMediaForPoint(latitude, longitude, null)
    }

    fun fileFor(media: Media): File = File(mediaDirectory, media.fileName)

    fun decodeBitmap(media: Media): Bitmap? {
        val file = fileFor(media)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }
}
