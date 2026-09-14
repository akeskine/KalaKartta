package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishDiaryPage
import fi.anssi.kalakartta.data.Media
import fi.anssi.kalakartta.utils.FishDiaryDialog
import fi.anssi.kalakartta.utils.enlargeButtons
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Renders catch details and delegates editing, deletion, and media actions. */
class CatchDetailsDialog(
    private val context: Context,
    private val mediaLoader: MarkerMediaLoader,
    private val resolveIconParams: (FishCatch) -> Quadruple<Int, String?, Int, Int>,
    private val openMedia: (Media) -> Unit,
    private val onEdit: (Marker, FishCatch?) -> Unit,
    private val onDelete: (Marker, FishCatch?) -> Unit
) {
    fun show(
        marker: Marker,
        fish: FishCatch?,
        diaryPages: List<FishDiaryPage>,
        mediaList: List<Media>,
        detailsText: String,
        hasSpecies: Boolean
    ) {
        val titleView = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_custom_title, null)
        val dialogTitle = if (fish?.eventType != null && fish.eventType != FishCatch.CAUGHT_FISH) {
            FishCatch.getEventTypeName(fish.eventType)
        } else if (hasSpecies || fish?.species != "UNKNOWN") {
            "Saaliin tiedot"
        } else {
            "Pisteen tiedot"
        }
        titleView.findViewById<TextView>(R.id.dialogTitle).text = dialogTitle
        setTitleIcon(titleView, fish)

        val messageText = detailsText.trim()
        val spannableMessage = SpannableString(messageText)
        addWindDirection(spannableMessage, messageText, fish?.windDirection)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding)
        }

        val graphMarker = CatchDetailsTextBuilder.PRESSURE_GRAPH_MARKER
        val graphMarkerIndex = messageText.indexOf(graphMarker)
        if (graphMarkerIndex >= 0) {
            val beforeGraphEnd = messageText.substring(0, graphMarkerIndex).trimEnd().length
            val afterGraphStart = graphMarkerIndex + graphMarker.length
            val afterGraphText = messageText.substring(afterGraphStart)
            val firstAfterGraphCharacter = afterGraphText.indexOfFirst { !it.isWhitespace() }
            val afterGraphContentStart = if (firstAfterGraphCharacter >= 0) {
                afterGraphStart + firstAfterGraphCharacter
            } else {
                messageText.length
            }

            addDetailsText(container, spannableMessage.subSequence(0, beforeGraphEnd))
            fish?.let { addPressureGraph(container, it) }
            addDetailsText(container, spannableMessage.subSequence(afterGraphContentStart, messageText.length))
        } else {
            addDetailsText(container, spannableMessage)
        }

        addDiaryLinks(container, diaryPages)
        addMedia(container, mediaList)

        val dialog = AlertDialog.Builder(context)
            .setCustomTitle(titleView)
            .setView(ScrollView(context).apply { addView(container) })
            .setPositiveButton("OK", null)
            .create()

        val editMenuButton = titleView.findViewById<View>(R.id.editMenuButton)
        editMenuButton.setOnClickListener {
            val popup = PopupMenu(context, editMenuButton)
            popup.menu.add(0, EDIT_ACTION, 0, context.getString(R.string.edit))
            popup.menu.add(0, DELETE_ACTION, 1, context.getString(R.string.delete))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    EDIT_ACTION -> {
                        onEdit(marker, fish)
                        dialog.dismiss()
                        true
                    }
                    DELETE_ACTION -> {
                        dialog.dismiss()
                        onDelete(marker, fish)
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

    private fun setTitleIcon(titleView: View, fish: FishCatch?) {
        fish ?: return
        val params = resolveIconParams(fish)
        val drawableId = params.first
        val iconPath = params.second
        val finalIconSize = params.third
        val titleIconView = titleView.findViewById<ImageView>(R.id.titleIcon)
        titleIconView.visibility = View.VISIBLE

        val density = context.resources.displayMetrics.density
        titleIconView.layoutParams = titleIconView.layoutParams.apply {
            width = (finalIconSize * density).toInt()
            height = (finalIconSize * density).toInt()
        }

        if (drawableId != 0 && (drawableId != R.drawable.default_point || fish.species != "UNKNOWN")) {
            titleIconView.setImageResource(drawableId)
        } else if (iconPath != null) {
            val file = if (iconPath.startsWith("/")) File(iconPath) else File(context.filesDir, iconPath)
            if (file.exists()) {
                titleIconView.setImageBitmap(android.graphics.BitmapFactory.decodeFile(file.absolutePath))
            } else {
                titleIconView.setImageResource(R.drawable.muukala)
            }
        } else if (drawableId == R.drawable.default_point && fish.species == "UNKNOWN") {
            titleIconView.setImageResource(R.drawable.default_point)
        } else {
            titleIconView.setImageResource(R.drawable.muukala)
        }
    }

    private fun addWindDirection(
        spannableMessage: SpannableString,
        messageText: String,
        direction: Long?
    ) {
        direction ?: return
        val windMarker = "Tuuli: "
        val windIndex = messageText.indexOf(windMarker)
        if (windIndex == -1) return
        val endOfLine = messageText.indexOf("\n", windIndex)
        val insertPos = if (endOfLine != -1) endOfLine else messageText.length
        val arrowDrawable = ContextCompat.getDrawable(context, R.drawable.ic_wind_arrow)?.mutate() ?: return
        val size = (16 * context.resources.displayMetrics.density).toInt()
        arrowDrawable.setBounds(0, 0, size, size)

        val bitmap = Bitmap.createBitmap(
            arrowDrawable.intrinsicWidth,
            arrowDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        Canvas(bitmap).apply {
            rotate((direction.toFloat() + 180) % 360, bitmap.width / 2f, bitmap.height / 2f)
            arrowDrawable.draw(this)
        }
        val rotatedDrawable = BitmapDrawable(context.resources, bitmap).apply {
            setBounds(0, 0, size, size)
        }
        val spacePos = insertPos - 1
        if (spacePos >= 0 && messageText[spacePos] == ' ') {
            spannableMessage.setSpan(
                android.text.style.ImageSpan(rotatedDrawable, android.text.style.ImageSpan.ALIGN_BOTTOM),
                spacePos,
                insertPos,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun addDetailsText(container: LinearLayout, text: CharSequence) {
        if (text.isNotBlank()) {
            container.addView(TextView(context).apply {
                this.text = text
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
                setTextColor(Color.BLACK)
                movementMethod = LinkMovementMethod.getInstance()
            })
        }
    }

    private fun addPressureGraph(container: LinearLayout, fish: FishCatch) {
        container.addView(PressureGraphView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * context.resources.displayMetrics.density).toInt()
            ).apply {
                topMargin = (8 * context.resources.displayMetrics.density).toInt()
            }
            setData(fish.pressureSamples, fish.caughtAt!!)
        })
    }

    private fun addDiaryLinks(container: LinearLayout, pages: List<FishDiaryPage>) {
        pages.forEachIndexed { index, page ->
            val linkText = if (pages.size == 1) {
                context.getString(R.string.trip_notes)
            } else {
                context.getString(R.string.trip_notes) + " ${index + 1}"
            }
            container.addView(TextView(context).apply {
                text = linkText
                setTextColor(ContextCompat.getColor(context, R.color.link_color))
                paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                textSize = 16f
                setPadding(0, (8 * context.resources.displayMetrics.density).toInt(), 0, 0)
                setOnClickListener { FishDiaryDialog.show(context, page) }
            })
        }
    }

    private fun addMedia(container: LinearLayout, mediaList: List<Media>) {
        if (mediaList.isEmpty()) return
        container.addView(TextView(context).apply {
            text = "\nMedia"
            TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
            setTypeface(null, android.graphics.Typeface.BOLD)
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
