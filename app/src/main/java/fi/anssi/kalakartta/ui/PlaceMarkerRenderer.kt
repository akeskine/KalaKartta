package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.data.PlaceOfInterestType
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

/** Applies the visual state to a marker representing a place of interest. */
class PlaceMarkerRenderer(
    private val iconFactory: MarkerIconFactory,
    private val resolveDrawableId: (String) -> Int,
    private val getOtherIconScale: () -> Float
) {
    fun render(marker: Marker, place: PlaceOfInterest, type: PlaceOfInterestType?, zoom: Double): Marker {
        marker.position = GeoPoint(place.latitude, place.longitude)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.setInfoWindowAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_TOP)

        var drawableId = resolveDrawableId(type?.icon ?: "")
        if (drawableId == R.drawable.default_point) {
            drawableId = R.drawable.default_place_point
        }

        val visibleSize = 8
        val touchSize = 48
        marker.icon = if (drawableId == R.drawable.default_place_point) {
            val scaledVisibleSize = (visibleSize * getOtherIconScale()).toInt()
            iconFactory.getTouchIcon(drawableId, scaledVisibleSize, touchSize)
        } else {
            var iconSize = (40 * getOtherIconScale()).toInt()
            when (place.typeId) {
                "SHALLOW", "DEEP" -> iconSize = (iconSize * 0.5).toInt()
                "ROCK", "VEGETATION" -> iconSize = (iconSize * 0.7).toInt()
                "ACCESS", "SHELTER", "PARKING", "RAMP", "LANDINGSPOT", "HARBOUR", "OTHER", "CAMPFIRE", "PROSPECT" -> {
                    iconSize = (iconSize * 0.8).toInt()
                }
            }

            if (zoom >= 16.5 && place.name.isNotEmpty()) {
                iconFactory.getLabelIcon(drawableId, iconSize, place.name)
            } else {
                iconFactory.getScaledIcon(drawableId, iconSize)
            }
        }
        marker.title = place.name
        marker.relatedObject = place
        return marker
    }
}
