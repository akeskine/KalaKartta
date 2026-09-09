package fi.anssi.kalakartta.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.Media
import fi.anssi.kalakartta.data.MediaService
import java.io.File

object MediaComponent {
    fun render(context: Context, container: LinearLayout, media: List<Media>, allowDelete: (Media) -> Boolean, onChanged: () -> Unit = {}) {
        container.removeAllViews()
        media.forEach { item ->
            val view = LayoutInflater.from(context).inflate(R.layout.item_media, container, false)
            val thumb = view.findViewById<ImageView>(R.id.mediaThumbnail)
            view.findViewById<TextView>(R.id.mediaFileName).text = item.originalFileName
            if (item.mimeType.startsWith("image/")) {
                val file = File(context.filesDir, "media/${item.fileName}")
                if (file.exists()) { thumb.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath)); thumb.visibility = View.VISIBLE }
            }
            view.findViewById<TextView>(R.id.mediaFileName).setOnClickListener { open(context, item) }
            thumb.setOnClickListener { open(context, item) }
            val remove = view.findViewById<ImageButton>(R.id.removeMediaButton)
            remove.visibility = if (allowDelete(item)) View.VISIBLE else View.GONE
            remove.setOnClickListener {
                AlertDialog.Builder(context).setTitle("Poista media").setMessage("Haluatko varmasti poistaa tämän median?")
                    .setPositiveButton("Poista") { _, _ -> MediaService(context).deleteMedia(item); onChanged() }
                    .setNegativeButton("Peruuta", null).show()
            }
            container.addView(view)
        }
    }

    fun open(context: Context, media: Media) {
        val file = File(context.filesDir, "media/${media.fileName}")
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, media.mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
    }
}
