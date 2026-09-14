package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.FishCatch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

/** Applies the visual state and title to a marker representing a catch cluster. */
class ClusterMarkerRenderer(
    private val iconFactory: MarkerIconFactory,
    private val resolveIconParams: (FishCatch) -> Quadruple<Int, String?, Int, Int>,
    private val resolveSpeciesName: (String) -> String?
) {
    fun render(marker: Marker, groupKey: Any, catches: List<FishCatch>): Marker? {
        if (catches.isEmpty()) return null

        marker.position = GeoPoint(
            catches.map { it.latitude }.average(),
            catches.map { it.longitude }.average()
        )
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

        val speciesId = if (groupKey is String) {
            groupKey
        } else {
            (groupKey as Pair<*, *>).first as String
        }
        val eventType = (groupKey as? Pair<*, *>)?.second as? String
        val iconParams = resolveIconParams(catches.first())
        val drawableId = iconParams.first
        val iconPath = iconParams.second
        var iconSize = iconParams.third
        val count = catches.size

        if (speciesId == "UNKNOWN" && (eventType == null || eventType == FishCatch.CAUGHT_FISH)) {
            marker.icon = iconFactory.getTouchIcon(drawableId, iconSize, 48)
            marker.title = "Tuntematon laji"
        } else {
            if (drawableId != R.drawable.default_point && iconSize == 24) {
                iconSize = 40
            }
            marker.icon = if (iconPath != null) {
                iconFactory.getClusterIcon(iconPath, iconSize, count)
            } else {
                iconFactory.getClusterIcon(drawableId, iconSize, count)
            }

            val speciesName = resolveSpeciesName(speciesId) ?: speciesId
            marker.title = if (eventType != null && eventType != FishCatch.CAUGHT_FISH) {
                "${FishCatch.getEventTypeName(eventType)}: $speciesName ($count kpl)"
            } else {
                "$speciesName ($count kpl)"
            }
        }

        marker.relatedObject = catches
        return marker
    }
}
