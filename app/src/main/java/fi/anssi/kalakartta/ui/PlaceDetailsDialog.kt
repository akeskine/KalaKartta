package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.Media
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.data.PlaceOfInterestType
import fi.anssi.kalakartta.utils.enlargeButtons
import org.osmdroid.views.overlay.Marker

/** Renders a place marker's details and delegates marker actions to the owner. */
class PlaceDetailsDialog(
    private val context: Context,
    private val mediaLoader: MarkerMediaLoader,
    private val openMedia: (Media) -> Unit,
    private val onEdit: (Marker, PlaceOfInterest) -> Unit,
    private val onDelete: (Marker, PlaceOfInterest) -> Unit
) {
    fun show(
        marker: Marker,
        place: PlaceOfInterest,
        type: PlaceOfInterestType?,
        mediaList: List<Media>
    ) {
        val titleView = LayoutInflater.from(context).inflate(R.layout.dialog_custom_title, null)
        val titleText = if (place.name.isEmpty()) type?.name ?: place.typeId else place.name
        titleView.findViewById<TextView>(R.id.dialogTitle).text = titleText

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding)
        }

        val message = StringBuilder()
        if (place.name.isEmpty()) {
            message.append(place.additionalInfo)
        } else {
            message.append("${type?.name ?: place.typeId}\n\n${place.additionalInfo}")
        }
        if (place.originalRef.isNotEmpty()) {
            if (message.isNotEmpty()) message.append("\n")
            message.append("Alkuperäinen viite: ${place.originalRef}")
        }

        if (message.trim().isNotEmpty()) {
            container.addView(TextView(context).apply {
                text = message.toString().trim()
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
                setTextColor(Color.BLACK)
            })
        }

        addMedia(container, mediaList)

        val dialog = AlertDialog.Builder(context)
            .setCustomTitle(titleView)
            .setView(ScrollView(context).apply { addView(container) })
            .setPositiveButton(R.string.ok, null)
            .create()

        titleView.findViewById<View>(R.id.editMenuButton).setOnClickListener {
            val popup = PopupMenu(context, titleView.findViewById(R.id.editMenuButton))
            popup.menu.add(0, EDIT_ACTION, 0, context.getString(R.string.edit))
            popup.menu.add(0, DELETE_ACTION, 1, context.getString(R.string.delete))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    EDIT_ACTION -> {
                        onEdit(marker, place)
                        dialog.dismiss()
                        true
                    }
                    DELETE_ACTION -> {
                        dialog.dismiss()
                        onDelete(marker, place)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        dialog.show()
        dialog.enlargeButtons()
    }

    private fun addMedia(container: LinearLayout, mediaList: List<Media>) {
        if (mediaList.isEmpty()) return

        container.addView(TextView(context).apply {
            text = "\nMedia"
            TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        })

        mediaList.filter { it.mimeType.startsWith("image/") }.forEach { media ->
            container.addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8 * context.resources.displayMetrics.density).toInt()
                }
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                mediaLoader.decodeBitmap(media)?.let { setImageBitmap(it) }
                setOnClickListener { openMedia(media) }
            })
        }

        mediaList.filter { !it.mimeType.startsWith("image/") }.forEach { media ->
            container.addView(TextView(context).apply {
                text = media.originalFileName
                paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                setTextColor(Color.BLUE)
                val padding = (8 * context.resources.displayMetrics.density).toInt()
                setPadding(0, padding, 0, padding)
                setOnClickListener { openMedia(media) }
            })
        }
    }

    companion object {
        private const val EDIT_ACTION = 0
        private const val DELETE_ACTION = 1
    }
}
