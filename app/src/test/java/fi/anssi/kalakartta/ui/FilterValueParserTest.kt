package fi.anssi.kalakartta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilterValueParserTest {

    @Test
    fun blankValuesAreNull() {
        assertNull(FilterValueParser.floatOrNull(""))
        assertNull(FilterValueParser.doubleOrNull("   "))
        assertNull(FilterValueParser.longOrNull(null))
    }

    @Test
    fun numericValuesAreTrimmedAndParsed() {
        assertEquals(1.25f, FilterValueParser.floatOrNull(" 1.25 ")!!, 0.0001f)
        assertEquals(2.5, FilterValueParser.doubleOrNull(" 2.5 ")!!, 0.0001)
        assertEquals(123L, FilterValueParser.longOrNull(" 123 ")!!.toLong())
    }

    @Test
    fun invalidValuesAreNull() {
        assertNull(FilterValueParser.floatOrNull("not-a-number"))
        assertNull(FilterValueParser.doubleOrNull("not-a-number"))
        assertNull(FilterValueParser.longOrNull("12.5"))
    }

    @Test
    fun nonFiniteFloatingPointValuesAreNull() {
        assertNull(FilterValueParser.floatOrNull("NaN"))
        assertNull(FilterValueParser.floatOrNull("Infinity"))
        assertNull(FilterValueParser.doubleOrNull("NaN"))
        assertNull(FilterValueParser.doubleOrNull("Infinity"))
    }
}
