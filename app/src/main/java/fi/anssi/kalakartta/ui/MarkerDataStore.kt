package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PlaceOfInterest

data class MarkerDataSnapshot(
    val catches: List<FishCatch>,
    val places: List<PlaceOfInterest>
)

/** Owns the in-memory marker data and deletion tombstones between rebuilds. */
class MarkerDataStore {
    private val lock = Any()
    private val catches = mutableListOf<FishCatch>()
    private val places = mutableListOf<PlaceOfInterest>()
    private val deletedCatchIds = mutableSetOf<Long>()
    private val deletedPlaceIds = mutableSetOf<Long>()

    fun upsertCatch(fish: FishCatch): Boolean = synchronized(lock) {
        if (deletedCatchIds.contains(fish.id)) return@synchronized false
        val index = catches.indexOfFirst { it.id == fish.id }
        if (index >= 0) catches[index] = fish else catches.add(fish)
        true
    }

    fun upsertPlace(place: PlaceOfInterest): Boolean = synchronized(lock) {
        if (deletedPlaceIds.contains(place.id)) return@synchronized false
        val index = places.indexOfFirst { it.id == place.id }
        if (index >= 0) places[index] = place else places.add(place)
        true
    }

    fun setCatches(newCatches: List<FishCatch>) = synchronized(lock) {
        catches.clear()
        catches.addAll(newCatches)
    }

    fun setPlaces(newPlaces: List<PlaceOfInterest>) = synchronized(lock) {
        places.clear()
        places.addAll(newPlaces)
    }

    fun isCatchDeleted(id: Long): Boolean = synchronized(lock) { deletedCatchIds.contains(id) }

    fun isPlaceDeleted(id: Long): Boolean = synchronized(lock) { deletedPlaceIds.contains(id) }

    fun removeCatch(id: Long) = synchronized(lock) {
        catches.removeAll { it.id == id }
        deletedCatchIds.add(id)
    }

    fun removePlace(id: Long) = synchronized(lock) {
        places.removeAll { it.id == id }
        deletedPlaceIds.add(id)
    }

    fun snapshot(minTimestamp: Long, maxTimestamp: Long, hidePlaces: Boolean): MarkerDataSnapshot {
        return synchronized(lock) {
            val visibleCatches = catches.filter {
                (it.caughtAt ?: 0L) >= minTimestamp && (it.caughtAt ?: 0L) <= maxTimestamp
            }.toList()
            val visiblePlaces = if (hidePlaces && (minTimestamp > 0 || maxTimestamp < Long.MAX_VALUE)) {
                emptyList()
            } else {
                places.toList()
            }
            deletedCatchIds.clear()
            deletedPlaceIds.clear()
            MarkerDataSnapshot(visibleCatches, visiblePlaces)
        }
    }

    fun clear() = synchronized(lock) {
        catches.clear()
        places.clear()
        deletedCatchIds.clear()
        deletedPlaceIds.clear()
    }

    fun catchCount(): Int = synchronized(lock) { catches.size }

    fun placeCount(): Int = synchronized(lock) { places.size }
}
