package fi.anssi.kalakartta.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import fi.anssi.kalakartta.data.Media
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MediaService(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val mediaDao = db.mediaDao()
    private val mediaDir = File(context.filesDir, "media").apply { if (!exists()) mkdirs() }

    fun addMedia(uri: Uri, lat: Double, lon: Double, pointTime: Long?): Media? {
        val originalFileName = getFileName(uri) ?: "unknown"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        
        // Luodaan uniikki tiedostonimi: <uuid>_<alkuperäinen>
        val uuid = UUID.randomUUID().toString()
        val extension = originalFileName.substringAfterLast('.', "")
        val safeFileName = if (extension.isNotEmpty()) "$uuid.$extension" else uuid
        
        val destFile = File(mediaDir, safeFileName)
        
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e("MediaService", "Error copying file", e)
            return null
        }

        val media = Media(
            latitude = lat,
            longitude = lon,
            pointTime = pointTime,
            mimeType = mimeType,
            originalFileName = originalFileName,
            fileName = safeFileName
        )
        
        val id = mediaDao.insert(media)
        return media.copy(id = id)
    }

    fun getMediaForPoint(lat: Double, lon: Double, pointTime: Long?): List<Media> {
        return mediaDao.getMediaForPoint(lat, lon, pointTime)
    }

    fun deleteMedia(media: Media) {
        mediaDao.delete(media)
        val file = File(mediaDir, media.fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    fun deleteAllMedia() {
        val allMedia = mediaDao.getAll()
        mediaDao.deleteAll()
        allMedia.forEach {
            val file = File(mediaDir, it.fileName)
            if (file.exists()) file.delete()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    fun exportMedia(outputStream: OutputStream) {
        val allMedia = mediaDao.getAll()
        val zipOut = ZipOutputStream(outputStream)
        
        // media.json
        val root = JSONObject()
        root.put("version", 1)
        val mediaArray = JSONArray()
        allMedia.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id.toString())
            obj.put("latitude", m.latitude)
            obj.put("longitude", m.longitude)
            obj.put("pointTime", m.pointTime)
            obj.put("originalFileName", m.originalFileName)
            obj.put("fileName", m.fileName)
            obj.put("mimeType", m.mimeType)
            mediaArray.put(obj)
        }
        root.put("media", mediaArray)
        
        zipOut.putNextEntry(ZipEntry("media.json"))
        zipOut.write(root.toString(2).toByteArray())
        zipOut.closeEntry()
        
        // media/ tiedostot
        allMedia.forEach { m ->
            val file = File(mediaDir, m.fileName)
            if (file.exists()) {
                zipOut.putNextEntry(ZipEntry("media/${m.fileName}"))
                file.inputStream().use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
        
        zipOut.close()
    }

    fun importMedia(inputStream: InputStream, onProgress: (Int, Int) -> Unit) {
        val zipIn = ZipInputStream(inputStream)
        var entry = zipIn.nextEntry
        var mediaJsonStr: String? = null
        val tempFiles = mutableMapOf<String, ByteArray>()
        
        while (entry != null) {
            if (entry.name == "media.json") {
                mediaJsonStr = zipIn.readBytes().toString(Charsets.UTF_8)
            } else if (entry.name.startsWith("media/")) {
                val fileName = entry.name.substringAfter("media/")
                if (fileName.isNotEmpty()) {
                    tempFiles[fileName] = zipIn.readBytes()
                }
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }
        
        if (mediaJsonStr == null) throw Exception("Invalid ZIP: media.json missing")
        
        val root = JSONObject(mediaJsonStr)
        val mediaArray = root.getJSONArray("media")
        val total = mediaArray.length()
        
        for (i in 0 until total) {
            val obj = mediaArray.getJSONObject(i)
            val externalId = obj.getString("id")
            val fileName = obj.getString("fileName")
            
            // Tarkistetaan onko jo tuotu
            if (mediaDao.getByExternalId(externalId) != null) {
                continue
            }
            
            val content = tempFiles[fileName] ?: continue
            
            // Tallennetaan tiedosto
            val destFile = File(mediaDir, fileName)
            // Jos tiedostonimi on jo käytössä mutta eri media, pitäisi ehkä nimetä uudelleen,
            // mutta tässä oletetaan että UUID takaa uniikkiuden ZIP:in sisällä ja kohteessa.
            // Jos destFile on jo olemassa, se ylikirjoitetaan.
            FileOutputStream(destFile).use { it.write(content) }
            
            val media = Media(
                latitude = obj.getDouble("latitude"),
                longitude = obj.getDouble("longitude"),
                pointTime = if (obj.isNull("pointTime")) null else obj.getLong("pointTime"),
                mimeType = obj.getString("mimeType"),
                originalFileName = obj.getString("originalFileName"),
                fileName = fileName,
                externalId = externalId
            )
            mediaDao.insert(media)
            onProgress(i + 1, total)
        }
    }
}
