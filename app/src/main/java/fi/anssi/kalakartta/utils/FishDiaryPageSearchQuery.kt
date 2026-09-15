package fi.anssi.kalakartta.utils

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import fi.anssi.kalakartta.data.FishDiarySearchDictionary
import java.util.Calendar
import java.util.TimeZone

data class FishDiarySearchDateRange(val start: Long, val endExclusive: Long)

data class FishDiarySearchTerm(
    val aliases: List<String>,
    val dateRange: FishDiarySearchDateRange?
)

data class FishDiarySearchCriteria(val terms: List<FishDiarySearchTerm>)

object FishDiaryPageSearchQuery {
    private val timeZone = TimeZone.getTimeZone("Europe/Helsinki")
    private val fullDatePattern = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})$")
    private val yearPattern = Regex("^\\d{4}$")
    private val searchableColumns = listOf("location", "fishingMethod", "catch", "story")

    fun parse(rawQuery: String, dictionary: List<FishDiarySearchDictionary.Entry>): FishDiarySearchCriteria {
        val terms = rawQuery.trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .map { token ->
                FishDiarySearchTerm(
                    aliases = FishDiarySearchDictionary.aliasesFor(token, dictionary),
                    dateRange = parseDate(token)
                )
            }
        return FishDiarySearchCriteria(terms)
    }

    fun searchQuery(criteria: FishDiarySearchCriteria, limit: Int, offset: Int): SupportSQLiteQuery {
        return buildQuery(criteria, limit, offset)
    }

    fun countQuery(criteria: FishDiarySearchCriteria): SupportSQLiteQuery {
        return buildQuery(criteria, null, 0)
    }

    private fun buildQuery(criteria: FishDiarySearchCriteria, limit: Int?, offset: Int): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val termConditions = criteria.terms.map { term ->
            term.dateRange?.let { range ->
                args += range.endExclusive
                args += range.start
                "startDate < ? AND COALESCE(endDate, startDate) >= ?"
            } ?: run {
                val alternatives = searchableColumns.flatMap { column ->
                    term.aliases.map { alias ->
                        args += "%${escapeLike(alias)}%"
                        "${normalizedColumn(column)} LIKE ? ESCAPE '\\'"
                    }
                }
                if (alternatives.isEmpty()) "0" else "(${alternatives.joinToString(" OR ")})"
            }
        }

        val where = if (termConditions.isEmpty()) "0" else termConditions.joinToString(" AND ")
        val sql = buildString {
            append("SELECT * FROM FishDiaryPage WHERE ")
            append(where)
            append(" ORDER BY startDate DESC, id DESC")
            if (limit != null) {
                append(" LIMIT ? OFFSET ?")
                args += limit.coerceAtLeast(0)
                args += offset.coerceAtLeast(0)
            }
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun normalizedColumn(column: String): String {
        return "LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(`$column`, 'ä', 'a'), 'Ä', 'a'), 'ö', 'o'), 'Ö', 'o'), 'å', 'a'), 'Å', 'a'))"
    }

    private fun escapeLike(value: String): String {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }

    private fun parseDate(token: String): FishDiarySearchDateRange? {
        fullDatePattern.matchEntire(token)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()
            return createDateRange(year, month - 1, day, exactDay = true)
        }
        if (yearPattern.matches(token)) {
            return createDateRange(token.toInt(), Calendar.JANUARY, 1, exactDay = false)
        }
        return null
    }

    private fun createDateRange(year: Int, month: Int, day: Int, exactDay: Boolean): FishDiarySearchDateRange? {
        return runCatching {
            val startCalendar = Calendar.getInstance(timeZone).apply {
                clear()
                isLenient = false
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            val start = startCalendar.timeInMillis
            val endCalendar = startCalendar.clone() as Calendar
            if (exactDay) {
                endCalendar.add(Calendar.DAY_OF_MONTH, 1)
            } else {
                endCalendar.add(Calendar.YEAR, 1)
            }
            FishDiarySearchDateRange(start, endCalendar.timeInMillis)
        }.getOrNull()
    }
}