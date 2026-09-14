package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.osmdroid.util.BoundingBox

class MarkerVisibilityCalculatorTest {
    private val calculator = MarkerVisibilityCalculator()
    private val mapBounds = BoundingBox(61.0, 25.0, 59.0, 23.0)

    @Test
    fun `small data sets are returned without clipping`() {
        val fish = catch(id = 1, latitude = 50.0, longitude = 10.0)

        val result = calculator.calculate(listOf(fish), emptyList(), zoom = 10.0, mapBounds)

        assertEquals(listOf(fish), result.catches)
        assertNull(result.boundingBox)
    }

    @Test
    fun `large data sets are clipped to the map bounds with margin`() {
        val inside = catch(id = 1, latitude = 60.0, longitude = 24.0)
        val outside = (2L..15001L).map { id ->
            catch(id = id, latitude = 50.0, longitude = 10.0)
        }

        val result = calculator.calculate(listOf(inside) + outside, emptyList(), zoom = 10.0, mapBounds)

        assertEquals(listOf(inside), result.catches)
        assertEquals(mapBounds, result.boundingBox)
    }

    private fun catch(id: Long, latitude: Double, longitude: Double) = FishCatch(
        id = id,
        species = "PIKE",
        latitude = latitude,
        longitude = longitude,
        caughtAt = 1_000L
    )
}
