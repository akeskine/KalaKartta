package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import org.junit.Assert.assertEquals
import org.junit.Test

class FishSizeCategoryTest {
    @Test
    fun eitherMeasurementAtTheLargeThresholdIsEnough() {
        val species = FishSpecies(
            id = "PERCH", name = "Perch",
            large_weight = 500, large_length = 35,
            giant_weight = 800, giant_length = 40
        )
        val fish = FishCatch(species = species.id, latitude = 0.0, longitude = 0.0, caughtAt = null)

        assertEquals(FishSizeCategory.LARGE, FishSizeCategory.classify(fish.copy(weight = 500), species))
        assertEquals(FishSizeCategory.LARGE, FishSizeCategory.classify(fish.copy(length = 35), species))
        assertEquals(FishSizeCategory.NORMAL, FishSizeCategory.classify(fish.copy(weight = 499, length = 34), species))
        assertEquals(FishSizeCategory.GIANT, FishSizeCategory.classify(fish.copy(weight = 500, length = 40), species))
    }
}
