package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterCalculatorTest {
    private val calculator = ClusterCalculator()

    @Test
    fun `same species catches close together form one cluster`() {
        val first = catch(id = 1, species = "PIKE", latitude = 60.0, longitude = 24.0)
        val second = catch(id = 2, species = "PIKE", latitude = 60.001, longitude = 24.001)

        val clusters = calculator.calculate(listOf(first, second), zoom = 12.0)

        assertEquals(1, clusters["PIKE"]?.size)
        assertEquals(listOf(first, second), clusters["PIKE"]?.single())
    }

    @Test
    fun `different species and non-catch events are kept separate`() {
        val pike = catch(id = 1, species = "PIKE")
        val perch = catch(id = 2, species = "PERCH")
        val lostPike = catch(id = 3, species = "PIKE", eventType = FishCatch.LOST_FISH)

        val clusters = calculator.calculate(listOf(pike, perch, lostPike), zoom = 12.0)

        assertTrue(clusters.containsKey("PIKE"))
        assertTrue(clusters.containsKey("PERCH"))
        assertTrue(clusters.containsKey("PIKE" to FishCatch.LOST_FISH))
    }

    @Test
    fun `unknown catches remain individual markers`() {
        val first = catch(id = 11, species = "UNKNOWN")
        val second = catch(id = 12, species = "UNKNOWN")

        val clusters = calculator.calculate(listOf(first, second), zoom = 12.0)

        assertEquals(listOf(first), clusters["UNKNOWN_INDIVIDUAL_11"]?.single())
        assertEquals(listOf(second), clusters["UNKNOWN_INDIVIDUAL_12"]?.single())
    }

    private fun catch(
        id: Long,
        species: String,
        latitude: Double = 60.0,
        longitude: Double = 24.0,
        eventType: String? = null
    ) = FishCatch(
        id = id,
        species = species,
        eventType = eventType,
        latitude = latitude,
        longitude = longitude,
        caughtAt = 1_000L
    )
}
