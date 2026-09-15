package fi.anssi.kalakartta.utils

import fi.anssi.kalakartta.data.FishDiarySearchDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishDiaryPageSearchQueryTest {
    private val dictionary = listOf(
        FishDiarySearchDictionary.Entry(
            "Hauki",
            listOf("hauki", "hauen", "hauenkalastus")
        ),
        FishDiarySearchDictionary.Entry("Säyne", listOf("säyne"))
    )

    @Test
    fun fishNameExpandsToCompoundWords() {
        val criteria = FishDiaryPageSearchQuery.parse("hauki", dictionary)

        assertTrue(criteria.terms.single().aliases.contains("hauenkalastus"))
    }

    @Test
    fun FinnishDiacriticsAreNormalized() {
        val criteria = FishDiaryPageSearchQuery.parse("SAYNE", dictionary)

        assertEquals(listOf("sayne"), criteria.terms.single().aliases)
    }

    @Test
    fun exactDateAndYearBecomeDateRanges() {
        val exact = FishDiaryPageSearchQuery.parse("15.9.2026", emptyList()).terms.single().dateRange
        val year = FishDiaryPageSearchQuery.parse("2026", emptyList()).terms.single().dateRange

        assertTrue(exact != null)
        assertTrue(year != null)
        assertEquals(24 * 60 * 60 * 1000L, exact!!.endExclusive - exact.start)
        assertEquals(365 * 24 * 60 * 60 * 1000L, year!!.endExclusive - year.start)
    }
}