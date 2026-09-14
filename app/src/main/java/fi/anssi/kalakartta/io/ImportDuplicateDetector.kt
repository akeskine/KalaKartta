package fi.anssi.kalakartta.io

import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PlaceOfInterest
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Tuontiaineiston koordinaattipohjainen duplikaattitunnistus ilman Android-riippuvuuksia. */
object ImportDuplicateDetector {

    const val DUPLICATE_DISTANCE_METERS = 2.0
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun findDuplicateCatches(
        imported: List<FishCatch>,
        current: List<FishCatch>,
        onProgress: (Int) -> Unit = {}
    ): List<Pair<FishCatch, FishCatch?>> {
        val results = mutableListOf<Pair<FishCatch, FishCatch?>>()

        for ((index, importedCatch) in imported.withIndex()) {
            val match = current.firstOrNull { currentCatch ->
                distanceMeters(
                    importedCatch.latitude,
                    importedCatch.longitude,
                    currentCatch.latitude,
                    currentCatch.longitude
                ) <= DUPLICATE_DISTANCE_METERS
            }
            if (match != null) results += importedCatch to match
            onProgress(index + 1)
        }
        return results
    }

    fun findDuplicatePlaces(
        imported: List<PlaceOfInterest>,
        current: List<PlaceOfInterest>,
        progressOffset: Int = 0,
        onProgress: (Int) -> Unit = {}
    ): List<Pair<PlaceOfInterest, PlaceOfInterest?>> {
        val results = mutableListOf<Pair<PlaceOfInterest, PlaceOfInterest?>>()

        for ((index, importedPlace) in imported.withIndex()) {
            val match = current.firstOrNull { currentPlace ->
                distanceMeters(
                    importedPlace.latitude,
                    importedPlace.longitude,
                    currentPlace.latitude,
                    currentPlace.longitude
                ) <= DUPLICATE_DISTANCE_METERS
            }
            if (match != null) results += importedPlace to match
            onProgress(progressOffset + index + 1)
        }
        return results
    }

    private fun distanceMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {
        val latitudeDelta = Math.toRadians(latitude2 - latitude1)
        val longitudeDelta = Math.toRadians(longitude2 - longitude1)
        val latitude1Radians = Math.toRadians(latitude1)
        val latitude2Radians = Math.toRadians(latitude2)
        val haversine = sin(latitudeDelta / 2).pow(2) +
            cos(latitude1Radians) * cos(latitude2Radians) * sin(longitudeDelta / 2).pow(2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }
}
