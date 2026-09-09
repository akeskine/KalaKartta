package fi.anssi.kalakartta.utils

import fi.anssi.kalakartta.data.FishDiaryPage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FishDiaryPageMatcherTest {
    private val timeZone = TimeZone.getTimeZone("Europe/Helsinki")

    @Test
    fun returnsAllPagesContainingCatchDateIncludingMultiDayPages() {
        val pages = listOf(
            page(1, date(2024, Calendar.JUNE, 14), date(2024, Calendar.JUNE, 16)),
            page(2, date(2024, Calendar.JUNE, 15), null),
            page(3, date(2024, Calendar.JUNE, 17), null)
        )

        val result = FishDiaryPageMatcher.pagesForCaughtAt(
            date(2024, Calendar.JUNE, 15, 18),
            pages
        )

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun returnsNoPagesWithoutCatchDate() {
        assertEquals(
            emptyList<FishDiaryPage>(),
            FishDiaryPageMatcher.pagesForCaughtAt(null, emptyList())
        )
    }

    private fun page(id: Long, startDate: Long, endDate: Long?) = FishDiaryPage(
        id = id,
        startDate = startDate,
        endDate = endDate,
        location = "Paikka",
        fishingMethod = "Vapa",
        catch = "Ahven",
        story = "Kertomus"
    )

    private fun date(year: Int, month: Int, day: Int, hour: Int = 0): Long {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis
    }
}
