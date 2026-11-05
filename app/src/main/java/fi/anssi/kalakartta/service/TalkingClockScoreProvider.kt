package fi.anssi.kalakartta.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException
import java.io.IOException

/** A granted URI exposes a single score through a read-only pipe, without a disk file. */
class TalkingClockScoreProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "text/plain"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Lauluteksti on vain luettavissa")
        val token = uri.pathSegments.singleOrNull() ?: throw FileNotFoundException("Virheellinen laulutunniste")
        val text = TalkingClockScoreStore.get(token) ?: throw FileNotFoundException("Laulupyyntö on päättynyt")
        return openPipeHelper(uri, "text/plain", null, text) { output, _, _, _, score ->
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(output).use {
                    it.write(requireNotNull(score).toByteArray(Charsets.UTF_8))
                }
            } catch (_: IOException) {
                // The engine may close its read end when playback is stopped.
            }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
