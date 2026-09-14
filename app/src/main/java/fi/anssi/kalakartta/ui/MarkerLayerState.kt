package fi.anssi.kalakartta.ui

import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker

/** Owns the marker overlays, active indexes, and reusable marker pool. */
class MarkerLayerState {
    val defaultPointsFolder = FolderOverlay()
    val catchesFolder = FolderOverlay()
    val placesFolder = FolderOverlay()
    val markersFolder = FolderOverlay()

    val markerPool = mutableListOf<Marker>()
    val activeIndividualMarkers = mutableMapOf<Long, Marker>()
    val activePlaceMarkers = mutableMapOf<Long, Marker>()

    fun allActiveMarkers(): List<Marker> = listOf(
        defaultPointsFolder.items,
        catchesFolder.items,
        placesFolder.items,
        markersFolder.items
    ).flatten().filterIsInstance<Marker>()

    fun obtainMarker(map: MapView): Marker {
        return markerPool.removeLastOrNull() ?: Marker(map)
    }

    fun recycleVisibleMarkers() {
        if (markerPool.size < MAX_POOL_SIZE) {
            markerPool.addAll(allActiveMarkers())
        }
        activeIndividualMarkers.clear()
        activePlaceMarkers.clear()
        clearFolders()
    }

    fun clearFolders() {
        defaultPointsFolder.items.clear()
        catchesFolder.items.clear()
        placesFolder.items.clear()
        markersFolder.items.clear()
    }

    fun recycleMarker(marker: Marker) {
        if (markerPool.size < MAX_POOL_SIZE) {
            markerPool.add(marker)
        }
    }

    companion object {
        private const val MAX_POOL_SIZE = 5000
    }
}
