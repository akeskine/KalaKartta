package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PlaceOfInterest
import org.osmdroid.util.BoundingBox

data class VisibleMarkerData(
    val catches: List<FishCatch>,
    val places: List<PlaceOfInterest>,
    val boundingBox: BoundingBox?
)

/** Applies viewport clipping and high-volume thinning before marker creation. */
class MarkerVisibilityCalculator {
    fun calculate(
        catches: List<FishCatch>,
        places: List<PlaceOfInterest>,
        zoom: Double,
        boundingBox: BoundingBox?
    ): VisibleMarkerData {
        val totalCount = catches.size + places.size
        if (totalCount < 15000) {
            return VisibleMarkerData(catches, places, null)
        }

        if (!isUsable(boundingBox)) {
            return VisibleMarkerData(catches, places, null)
        }

        val box = requireNotNull(boundingBox)
        val marginMultiplier = if (totalCount < 15000) 2.0 else 0.2
        val latMargin = box.latitudeSpan * marginMultiplier
        val lonMargin = (box.lonEast - box.lonWest) * marginMultiplier

        val filteredCatches = catches.filter { fish ->
            fish.latitude >= box.latSouth - latMargin &&
                fish.latitude <= box.latNorth + latMargin &&
                fish.longitude >= box.lonWest - lonMargin &&
                fish.longitude <= box.lonEast + lonMargin
        }
        val filteredPlaces = places.filter { place ->
            place.latitude >= box.latSouth - latMargin &&
                place.latitude <= box.latNorth + latMargin &&
                place.longitude >= box.lonWest - lonMargin &&
                place.longitude <= box.lonEast + lonMargin
        }

        val maxVisible = 2000
        val finalCatches = if (filteredCatches.size > maxVisible && totalCount > 15000) {
            val (unknowns, knowns) = filteredCatches.partition { it.species == "UNKNOWN" }
            if (unknowns.size > 200) {
                thinUnknowns(unknowns, knowns, box, zoom, maxVisible)
            } else {
                filteredCatches.take(maxVisible)
            }
        } else {
            filteredCatches
        }

        return VisibleMarkerData(finalCatches, filteredPlaces, box)
    }

    private fun thinUnknowns(
        unknowns: List<FishCatch>,
        knowns: List<FishCatch>,
        boundingBox: BoundingBox,
        zoom: Double,
        maxVisible: Int
    ): List<FishCatch> {
        val gridSizeDivider = when {
            zoom < 14 -> 20.0
            zoom < 16 -> 30.0
            else -> 40.0
        }
        val gridSizeLat = boundingBox.latitudeSpan / gridSizeDivider
        val gridSizeLon = (boundingBox.lonEast - boundingBox.lonWest) / gridSizeDivider
        val grid = mutableSetOf<Pair<Int, Int>>()
        val thinnedUnknowns = mutableListOf<FishCatch>()

        for (fish in unknowns) {
            val gridX = ((fish.latitude - boundingBox.latSouth) / gridSizeLat).toInt()
            val gridY = ((fish.longitude - boundingBox.lonWest) / gridSizeLon).toInt()
            val position = gridX to gridY
            val hasData = fish.weight != null || fish.length != null
            if (!grid.contains(position) || hasData) {
                thinnedUnknowns.add(fish)
                if (!hasData) grid.add(position)
            }
            if (thinnedUnknowns.size + knowns.size >= maxVisible) break
        }
        return thinnedUnknowns + knowns
    }

    private fun isUsable(box: BoundingBox?): Boolean =
        box != null &&
            box.latNorth != 0.0 &&
            box.latSouth != 0.0 &&
            (box.latitudeSpan > 0.0 || box.lonEast - box.lonWest > 0.0)
}
