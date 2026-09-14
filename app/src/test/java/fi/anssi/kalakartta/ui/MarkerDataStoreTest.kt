package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PlaceOfInterest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerDataStoreTest {
    @Test
    fun `snapshot applies time range and hides places when requested`() {
        val store = MarkerDataStore()
        val visibleCatch = catch(id = 1, caughtAt = 1_000L)
        val hiddenCatch = catch(id = 2, caughtAt = 10_000L)
        val place = PlaceOfInterest(id = 3, typeId = "ROCK", latitude = 60.0, longitude = 24.0)
        store.setCatches(listOf(visibleCatch, hiddenCatch))
        store.setPlaces(listOf(place))

        val snapshot = store.snapshot(0L, 2_000L, hidePlaces = true)

        assertEquals(listOf(visibleCatch), snapshot.catches)
        assertTrue(snapshot.places.isEmpty())
    }

    @Test
    fun `deleted ids block incremental updates until snapshot completes`() {
        val store = MarkerDataStore()
        val fish = catch(id = 7, caughtAt = 1_000L)
        store.upsertCatch(fish)

        store.removeCatch(fish.id)

        assertTrue(store.isCatchDeleted(fish.id))
        assertFalse(store.upsertCatch(fish))

        store.snapshot(0L, Long.MAX_VALUE, hidePlaces = false)

        assertTrue(store.upsertCatch(fish))
    }

    private fun catch(id: Long, caughtAt: Long) = FishCatch(
        id = id,
        species = "PIKE",
        latitude = 60.0,
        longitude = 24.0,
        caughtAt = caughtAt
    )
}
