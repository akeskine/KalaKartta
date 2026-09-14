package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishDiaryPage
import fi.anssi.kalakartta.data.FishSpecies
import fi.anssi.kalakartta.data.Media
import fi.anssi.kalakartta.utils.FishDiaryPageMatcher

data class LoadedCatchDetails(
    val species: FishSpecies?,
    val diaryPages: List<FishDiaryPage>,
    val media: List<Media>
)

/** Loads the database-backed content needed by marker detail dialogs. */
class MarkerDetailsLoader(
    private val database: AppDatabase,
    private val mediaLoader: MarkerMediaLoader
) {
    fun loadCatch(fish: FishCatch?): LoadedCatchDetails {
        if (fish == null) return LoadedCatchDetails(null, emptyList(), emptyList())
        return LoadedCatchDetails(
            species = database.fishSpeciesDao().getById(fish.species),
            diaryPages = FishDiaryPageMatcher.pagesForCaughtAt(
                fish.caughtAt,
                database.fishDiaryPageDao().getAll()
            ),
            media = mediaLoader.getForCatch(fish)
        )
    }

    fun loadPlaceMedia(latitude: Double, longitude: Double): List<Media> =
        mediaLoader.getForPlace(latitude, longitude)
}
