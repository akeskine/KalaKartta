package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import org.junit.Assert.assertEquals
import org.junit.Test

class CatchSummaryFormatterTest {
    @Test
    fun formatsEachCatchSpeciesOnce() {
        val speciesMap = mapOf(
            "PERCH" to FishSpecies("PERCH", "Ahven", sortOrder = 1),
            "PIKE" to FishSpecies("PIKE", "Hauki", sortOrder = 2)
        )
        val catches = listOf(
            FishCatch(species = "PERCH", latitude = 0.0, longitude = 0.0, caughtAt = 1L),
            FishCatch(species = "PERCH", latitude = 0.0, longitude = 0.0, caughtAt = 2L),
            FishCatch(species = "PIKE", latitude = 0.0, longitude = 0.0, caughtAt = 3L),
            FishCatch(species = "PIKE", latitude = 0.0, longitude = 0.0, caughtAt = 4L),
            FishCatch(species = "PIKE", latitude = 0.0, longitude = 0.0, caughtAt = 5L)
        )

        assertEquals("Ahven 2 kpl\n\nHauki 3 kpl", CatchSummaryFormatter.format(catches, speciesMap))
    }
}
