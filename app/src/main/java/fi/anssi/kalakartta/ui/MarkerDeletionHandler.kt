package fi.anssi.kalakartta.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.utils.enlargeButtons
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Owns confirmation dialogs and stale-marker handling for marker deletion. */
class MarkerDeletionHandler(
    private val context: Context,
    private val map: MapView,
    private val onDeleteConfirmed: (Marker) -> Unit
) {
    fun confirmPlace(marker: Marker, place: PlaceOfInterest) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.delete))
            .setMessage("Haluatko varmasti poistaa paikan ${place.name}?")
            .setPositiveButton(R.string.delete) { _, _ ->
                confirmCurrentMarker(marker, place)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        dialog.enlargeButtons()
    }

    fun confirmMarker(marker: Marker, fishFromDialog: FishCatch?) {
        val related = marker.relatedObject
        val fish = fishFromDialog ?: related as? FishCatch
        val place = if (fish == null) related as? PlaceOfInterest else null

        if (fish == null && place == null) {
            android.util.Log.w(TAG, "confirmMarker: marker has no related object, nothing to delete")
            return
        }

        val target: Any = fish ?: place ?: return
        val dialog = AlertDialog.Builder(context)
            .setTitle("Poista merkki?")
            .setMessage("Haluatko varmasti poistaa tämän merkin?")
            .setPositiveButton("Poista") { _, _ ->
                confirmCurrentMarker(marker, target)
            }
            .setNegativeButton("Peruuta", null)
            .show()
        dialog.enlargeButtons()
    }

    private fun confirmCurrentMarker(marker: Marker, target: Any) {
        if (marker.relatedObject == target) {
            onDeleteConfirmed(marker)
        } else {
            val dummyMarker = Marker(map)
            dummyMarker.relatedObject = target
            onDeleteConfirmed(dummyMarker)
        }
    }

    companion object {
        private const val TAG = "MarkerDeletionHandler"
    }
}
