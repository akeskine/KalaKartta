package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.FishCatch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

/** Applies the visual state to a marker representing one catch. */
class IndividualMarkerRenderer(
    private val iconFactory: MarkerIconFactory,
    private val resolveIconParams: (FishCatch) -> Quadruple<Int, String?, Int, Int>,
    private val resolveSpeciesName: (String) -> String?
) {
    fun render(marker: Marker, fish: FishCatch): Marker {
        marker.position = GeoPoint(fish.latitude, fish.longitude)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

        val speciesName = resolveSpeciesName(fish.species)
        marker.title = if (fish.species == "OTHER" && !fish.otherSpecies.isNullOrEmpty()) {
            val otherName = fish.otherSpecies.lowercase().replaceFirstChar { it.uppercase() }
            "${speciesName ?: fish.species} ($otherName)"
        } else {
            speciesName ?: if (fish.species == "UNKNOWN") "Tuntematon laji" else fish.species
        }

        val iconParams = resolveIconParams(fish)
        marker.icon = when {
            iconParams.first == R.drawable.default_point -> {
                iconFactory.getTouchIcon(iconParams.first, iconParams.fourth, 48)
            }
            iconParams.second != null -> {
                iconFactory.getScaledIcon(iconParams.second!!, iconParams.third)
            }
            else -> {
                iconFactory.getScaledIcon(iconParams.first, iconParams.third)
            }
        }
        marker.relatedObject = fish
        return marker
    }
}
