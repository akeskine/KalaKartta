package fi.anssi.kalakartta.ui

import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** Updates marker folders as one consistent map-overlay operation. */
class MarkerOverlayController(
    private val map: MapView,
    private val layerState: MarkerLayerState
) {
    fun clear() {
        layerState.clearFolders()
        layerState.activeIndividualMarkers.clear()
        layerState.activePlaceMarkers.clear()
        map.invalidate()
    }

    fun recycleAndClear() {
        layerState.recycleVisibleMarkers()
    }

    fun replace(
        defaultMarkers: List<Overlay>,
        catchMarkers: List<Overlay>,
        placeMarkers: List<Overlay>
    ) {
        layerState.clearFolders()
        layerState.defaultPointsFolder.items.addAll(defaultMarkers)
        layerState.catchesFolder.items.addAll(catchMarkers)
        layerState.placesFolder.items.addAll(placeMarkers)
        map.invalidate()
    }
}
