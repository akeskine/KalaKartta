package fi.anssi.kalakartta.io

import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PlaceOfInterest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDuplicateDetectorTest {

    @Test
    fun findsCatchDuplicatesAndReportsProgress() {
        val imported = listOf(catch(60.0, 25.0), catch(60.0, 25.001))
        val current = listOf(catch(60.0, 25.00001))
        val progress = mutableListOf<Int>()

        val duplicates = ImportDuplicateDetector.findDuplicateCatches(imported, current) {
            progress += it
        }

        assertEquals(1, duplicates.size)
        assertEquals(imported.first(), duplicates.first().first)
        assertEquals(listOf(1, 2), progress)
    }

    @Test
    fun findsPlaceDuplicatesUsingProgressOffset() {
        val imported = listOf(place(60.0, 25.0))
        val current = listOf(place(60.0, 25.00001))
        val progress = mutableListOf<Int>()

        val duplicates = ImportDuplicateDetector.findDuplicatePlaces(
            imported,
            current,
            progressOffset = 3
        ) { progress += it }

        assertEquals(1, duplicates.size)
        assertEquals(listOf(4), progress)
    }

    @Test
    fun doesNotMatchPointsOverTwoMetersApart() {
        val duplicates = ImportDuplicateDetector.findDuplicatePlaces(
            imported = listOf(place(60.0, 25.0)),
            current = listOf(place(60.0, 25.0001))
        )

        assertTrue(duplicates.isEmpty())
    }

    private fun catch(latitude: Double, longitude: Double) = FishCatch(
        species = "PIKE",
        latitude = latitude,
        longitude = longitude,
        caughtAt = 0L
    )

    private fun place(latitude: Double, longitude: Double) = PlaceOfInterest(
        typeId = "ISLAND",
        latitude = latitude,
        longitude = longitude
    )
}
