package fi.anssi.kalakartta.utils

import fi.anssi.kalakartta.data.FishDiaryPage
import java.util.Calendar
import java.util.TimeZone

/** Finds diary pages whose date range contains the catch date. */
object FishDiaryPageMatcher {
    private val timeZone = TimeZone.getTimeZone("Europe/Helsinki")

    fun pagesForCaughtAt(caughtAt: Long?, pages: List<FishDiaryPage>): List<FishDiaryPage> {
        if (caughtAt == null || caughtAt <= 0L) return emptyList()

        val catchDay = startOfDay(caughtAt)
        return pages
            .filter { page ->
                val start = startOfDay(page.startDate)
                val end = page.endDate?.let(::startOfDay) ?: start
                catchDay >= start && catchDay <= end
            }
            .sortedWith(compareBy<FishDiaryPage> { startOfDay(it.startDate) }.thenBy { it.id })
    }

    private fun startOfDay(timestamp: Long): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
