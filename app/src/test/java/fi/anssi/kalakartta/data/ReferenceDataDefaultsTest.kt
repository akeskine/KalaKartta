package fi.anssi.kalakartta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceDataDefaultsTest {

    @Test
    fun defaultFishSpeciesHaveStableIdentityAndOrdering() {
        val defaults = FishSpecies.getDefaultList()

        assertEquals(13, defaults.size)
        assertEquals(defaults.size, defaults.map { it.id }.toSet().size)
        assertEquals((1..13).toList(), defaults.map { it.sortOrder })
        assertTrue(defaults.all { it.icon_default.isNotBlank() })
    }

    @Test
    fun defaultPlaceOfInterestTypesHaveStableIdentityAndOrdering() {
        val defaults = PlaceOfInterestType.getDefaultList()

        assertEquals(15, defaults.size)
        assertEquals(defaults.size, defaults.map { it.id }.toSet().size)
        assertEquals((1..15).toList(), defaults.map { it.sortOrder })
        assertTrue(defaults.all { it.icon.isNotBlank() })
    }
}
