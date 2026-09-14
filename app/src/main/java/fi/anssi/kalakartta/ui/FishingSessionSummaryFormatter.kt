package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishingSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FishingSessionSummaryFormatter {
    fun format(sessions: List<FishingSession>): String {
        if (sessions.isEmpty()) return "Ei kalastussessioita tältä ajalta."

        val timeFormat = SimpleDateFormat("H:mm", Locale.getDefault())
        return sessions
            .sortedBy { it.startedAt }
            .joinToString("\n\n") { session ->
                val startTime = timeFormat.format(Date(session.startedAt))
                val endTime = session.endedAt?.let { timeFormat.format(Date(it)) } ?: "?"
                val notes = session.notes.trim()
                "Sessio $startTime-$endTime" + if (notes.isEmpty()) "" else ", $notes"
            }
    }
}