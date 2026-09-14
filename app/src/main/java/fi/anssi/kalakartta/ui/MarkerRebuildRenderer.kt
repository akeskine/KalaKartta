package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PlaceOfInterest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

/** Builds the marker overlay set for the current zoom level and viewport. */
class MarkerRebuildRenderer(
    private val map: MapView,
    private val overlayController: MarkerOverlayController,
    private val iconFactory: MarkerIconFactory,
    private val clusterCalculator: ClusterCalculator,
    private val visibilityCalculator: MarkerVisibilityCalculator,
    private val createPlaceMarker: (PlaceOfInterest, Double) -> Marker?,
    private val createIndividualMarker: (FishCatch) -> Marker?,
    private val createClusterMarker: (Any, List<FishCatch>) -> Marker?
) {
    suspend fun render(
        catches: List<FishCatch>,
        places: List<PlaceOfInterest>,
        zoom: Double
    ): BoundingBox? {
        val totalCount = catches.size + places.size
        val clusterLimit = if (totalCount < 15000) 13.0 else 15.0

        return if (zoom < clusterLimit) {
            renderClustered(catches, places, zoom, totalCount)
            null
        } else {
            renderIndividual(catches, places, zoom)
        }
    }

    private suspend fun renderClustered(
        catches: List<FishCatch>,
        places: List<PlaceOfInterest>,
        zoom: Double,
        totalCount: Int
    ) {
        withContext(Dispatchers.Main) {
            overlayController.recycleAndClear()
            iconFactory.clear()
        }

        val clusters = withContext(Dispatchers.Default) {
            clusterCalculator.calculate(catches, zoom)
        }
        if (!currentCoroutineContext().isActive) return

        withContext(Dispatchers.Main) {
            val newDefaultMarkers = mutableListOf<Overlay>()
            val newCatchMarkers = mutableListOf<Overlay>()
            val newPlaceMarkers = mutableListOf<Overlay>()

            places.forEach { place ->
                createPlaceMarker(place, zoom)?.let(newPlaceMarkers::add)
            }

            val boundingBox = map.boundingBox
            val useThinning = totalCount > 15000 && boundingBox != null && boundingBox.latNorth != 0.0
            val thinnedDefaultGrid = mutableSetOf<Pair<Int, Int>>()
            val gridSizeDivider = when {
                zoom < 8 -> 15.0
                zoom < 10 -> 25.0
                zoom < 12 -> 35.0
                else -> 40.0
            }

            clusters.forEach { (groupKey, groupClusters) ->
                groupClusters.forEach { cluster ->
                    if (cluster.size == 1) {
                        val fish = cluster.first()
                        if (fish.species == "UNKNOWN" && useThinning) {
                            val box = requireNotNull(boundingBox)
                            val gridSizeLat = box.latitudeSpan / gridSizeDivider
                            val gridSizeLon = (box.lonEast - box.lonWest) / gridSizeDivider
                            val position =
                                ((fish.latitude - box.latSouth) / gridSizeLat).toInt() to
                                    ((fish.longitude - box.lonWest) / gridSizeLon).toInt()
                            val hasData = fish.weight != null || fish.length != null
                            if (!thinnedDefaultGrid.contains(position) || hasData) {
                                createIndividualMarker(fish)?.let { marker ->
                                    newDefaultMarkers.add(marker)
                                    if (!hasData) thinnedDefaultGrid.add(position)
                                }
                            }
                        } else {
                            createIndividualMarker(fish)?.let { marker ->
                                if (fish.species == "UNKNOWN") {
                                    newDefaultMarkers.add(marker)
                                } else {
                                    newCatchMarkers.add(marker)
                                }
                            }
                        }
                    } else {
                        createClusterMarker(groupKey, cluster)?.let(newCatchMarkers::add)
                    }
                }
            }

            if (isActive) {
                overlayController.replace(newDefaultMarkers, newCatchMarkers, newPlaceMarkers)
            }
        }
    }

    private suspend fun renderIndividual(
        catches: List<FishCatch>,
        places: List<PlaceOfInterest>,
        zoom: Double
    ): BoundingBox? {
        withContext(Dispatchers.Main) {
            overlayController.recycleAndClear()
        }

        val boundingBox = withContext(Dispatchers.Main) { map.boundingBox }
        val visible = withContext(Dispatchers.Default) {
            visibilityCalculator.calculate(catches, places, zoom, boundingBox)
        }
        if (!currentCoroutineContext().isActive) return visible.boundingBox

        withContext(Dispatchers.Main) {
            val newDefaultMarkers = mutableListOf<Overlay>()
            val newCatchMarkers = mutableListOf<Overlay>()
            val newPlaceMarkers = mutableListOf<Overlay>()

            visible.places.forEach { place ->
                createPlaceMarker(place, zoom)?.let(newPlaceMarkers::add)
            }
            visible.catches.forEach { fish ->
                createIndividualMarker(fish)?.let { marker ->
                    if (fish.species == "UNKNOWN") {
                        newDefaultMarkers.add(marker)
                    } else {
                        newCatchMarkers.add(marker)
                    }
                }
            }

            if (isActive) {
                overlayController.replace(newDefaultMarkers, newCatchMarkers, newPlaceMarkers)
            }
        }
        return visible.boundingBox
    }
}
