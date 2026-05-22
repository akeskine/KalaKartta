package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import android.widget.PopupMenu
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.createBitmap
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.utils.enlargeButtons
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

import java.text.SimpleDateFormat
import java.util.*

class MarkerManager(
    private val context: Context,
    private val map: MapView,
    private val db: AppDatabase,
    private val onDeleteConfirmed: (Marker) -> Unit
) {

    fun addMarker(fish: FishCatch) {
        val point = GeoPoint(fish.latitude, fish.longitude)
        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        
        // Hae lajin nimi ja kuvake tietokannasta
        val species = db.fishSpeciesDao().getById(fish.species)
        marker.title = species?.name ?: if (fish.species == "UNKNOWN") "Tuntematon laji" else fish.species
        
        val iconName = species?.icon_default ?: ""
        val drawableId = getDrawableId(iconName)
        
        val iconSize = if (drawableId == R.drawable.default_point) 24 else 40
        marker.icon = if (drawableId == R.drawable.default_point) {
            getSmallIconWithLargeTouchArea(drawableId, 16, 48)
        } else {
            getScaledMarkerIcon(drawableId, iconSize)
        }
        marker.relatedObject = fish

        marker.setOnMarkerClickListener { clickedMarker, _ ->
            showCatchDetailsDialog(clickedMarker)
            true
        }

        map.overlays.add(marker)
    }

    private fun showCatchDetailsDialog(marker: Marker) {
        val fish = marker.relatedObject as? FishCatch
        val details = StringBuilder()
        var hasSpecies = false
        
        fish?.let {
            val species = db.fishSpeciesDao().getById(it.species)
            if (species != null) {
                details.append("Laji: ${species.name}\n")
                hasSpecies = true
            }

            if (it.caughtAt > 0) {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy 'klo' HH:mm", Locale.getDefault())
                val dateStr = dateFormat.format(Date(it.caughtAt))
                details.append("Saantiaika: $dateStr\n")
            }

            if (it.weight > 0) details.append("Paino: ${it.weight} g\n")
            if (it.length > 0) details.append("Pituus: ${it.length} cm\n")
            if (it.additionalInfo.isNotEmpty()) details.append("Lisätieto: ${it.additionalInfo}\n")
            if (it.originalRef.isNotEmpty()) details.append("Alkuperäinen viite: ${it.originalRef}\n")
        }

        val messageText = details.toString().trim()
        val finalMessage: CharSequence = if (fish != null && fish.tripNotes.isNotEmpty()) {
            val linkText = "Kalapäiväkirjan merkinnät"
            val spannable = SpannableString("$messageText\n\n$linkText")
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val intent = Intent(context, TripNotesActivity::class.java)
                    intent.putExtra("EXTRA_NOTES", fish.tripNotes)
                    if (context !is android.app.Activity) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            val start = spannable.length - linkText.length
            val end = spannable.length
            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable
        } else {
            messageText
        }

        val titleView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_custom_title, null)
        titleView.findViewById<android.widget.TextView>(R.id.dialogTitle).text = if (hasSpecies) "Saaliin tiedot" else "Pisteen tiedot"

        val dialog = AlertDialog.Builder(context)
            .setCustomTitle(titleView)
            .setMessage(finalMessage)
            .setPositiveButton("OK", null)
            .create()

        val editMenuButton = titleView.findViewById<android.view.View>(R.id.editMenuButton)
        editMenuButton.setOnClickListener {
            val popup = PopupMenu(context, editMenuButton)
            popup.menu.add(0, 0, 0, context.getString(R.string.edit))
            popup.menu.add(0, 1, 1, context.getString(R.string.delete))

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> {
                        val intent = Intent(context, EditCatchActivity::class.java)
                        intent.putExtra("EXTRA_CATCH_ID", fish?.id)
                        if (context is android.app.Activity) {
                            context.startActivityForResult(intent, 1001)
                        } else {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                        dialog.dismiss()
                        true
                    }
                    1 -> {
                        dialog.dismiss()
                        confirmDeleteMarker(marker)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        if (fish != null && fish.tripNotes.isNotEmpty()) {
            dialog.setOnShowListener {
                dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
            }
        }
        
        dialog.show()
        dialog.enlargeButtons()
    }

    private fun confirmDeleteMarker(marker: Marker) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Poista merkki?")
            .setMessage("Haluatko varmasti poistaa tämän kalamerkin?")
            .setPositiveButton("Poista") { _, _ ->
                onDeleteConfirmed(marker)
            }
            .setNegativeButton("Peruuta", null)
            .show()

        dialog.enlargeButtons()
    }

    @Suppress("DiscouragedApi")
    private fun getDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return R.drawable.default_point
        
        val id = context.resources.getIdentifier(iconName, "drawable", context.packageName)
        return if (id != 0) id else R.drawable.default_point
    }

    private fun getScaledMarkerIcon(drawableId: Int, sizeDp: Int): BitmapDrawable {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: ContextCompat.getDrawable(context, R.drawable.default_point)!!
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
        val bitmap = drawable.toBitmap(sizePx, sizePx)
        return bitmap.toDrawable(context.resources)
    }

    private fun getSmallIconWithLargeTouchArea(drawableId: Int, visibleSizeDp: Int, touchSizeDp: Int): BitmapDrawable {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: ContextCompat.getDrawable(context, R.drawable.default_point)!!
        
        val density = context.resources.displayMetrics.density
        val visibleSizePx = (visibleSizeDp * density).toInt()
        val touchSizePx = (touchSizeDp * density).toInt()
        
        val bitmap = createBitmap(touchSizePx, touchSizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val left = (touchSizePx - visibleSizePx) / 2
        val top = (touchSizePx - visibleSizePx) / 2
        
        drawable.setBounds(left, top, left + visibleSizePx, top + visibleSizePx)
        drawable.draw(canvas)
        
        return bitmap.toDrawable(context.resources)
    }
}
