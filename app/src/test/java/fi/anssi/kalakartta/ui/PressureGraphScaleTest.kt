package fi.anssi.kalakartta.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PressureGraphScaleTest {

    @Test
    fun `small pressure variation gets minimum range`() {
        val range = PressureGraphScale.rangeFor(listOf(1001.0, 1003.0))

        assertEquals(20f, range.max - range.min, 0.001f)
        assertEquals(990f, range.min, 0.001f)
        assertEquals(1010f, range.max, 0.001f)
    }

    @Test
    fun `small variation is centered instead of ending at the highest value`() {
        val range = PressureGraphScale.rangeFor(listOf(980.0, 1000.0))

        assertEquals(980f, range.min, 0.001f)
        assertEquals(1000f, range.max, 0.001f)
    }

    @Test
    fun `large pressure variation gets larger range`() {
        val range = PressureGraphScale.rangeFor(listOf(980.0, 1041.0))

        assertEquals(70f, range.max - range.min, 0.001f)
        assertEquals(980f, range.min, 0.001f)
        assertEquals(1050f, range.max, 0.001f)
    }

    @Test
    fun `non-finite values are ignored`() {
        val range = PressureGraphScale.rangeFor(listOf(Double.NaN, 1000.0, Double.POSITIVE_INFINITY))

        assertEquals(20f, range.max - range.min, 0.001f)
        assertEquals(990f, range.min, 0.001f)
        assertEquals(1010f, range.max, 0.001f)
    }
}
