package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishingSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class FishingSessionSummaryFormatterTest {
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    @Test
    fun formatsSessionsInStartTimeOrderWithBlankLineBetweenSessions() {
        val later = timeFormat.parse("2024-06-15 14:00")!!.time
        val earlier = timeFormat.parse("2024-06-15 08:23")!!.time
        val sessions = listOf(
            FishingSession(startedAt = later, endedAt = timeFormat.parse("2024-06-15 20:33")!!.time, notes = "ahventen perässä Hasutoselällä. Paljon tärppejä"),
            FishingSession(startedAt = earlier, endedAt = timeFormat.parse("2024-06-15 10:33")!!.time, notes = "perhon heittoa tyynissä paikoissa")
        )

        assertEquals(
            "Sessio 8:23-10:33, perhon heittoa tyynissä paikoissa\n\n" +
                    "Sessio 14:00-20:33, ahventen perässä Hasutoselällä. Paljon tärppejä",
            FishingSessionSummaryFormatter.format(sessions)
        )
    }

    @Test
    fun keepsSessionWithoutNotesAndMarksUnfinishedSession() {
        val startedAt = timeFormat.parse("2024-06-15 08:23")!!.time

        assertEquals(
            "Sessio 8:23-?",
            FishingSessionSummaryFormatter.format(
                listOf(FishingSession(startedAt = startedAt, notes = "  "))
            )
        )
    }

    @Test
    fun describesEmptySessionList() {
        assertEquals(
            "Ei kalastussessioita tältä ajalta.",
            FishingSessionSummaryFormatter.format(emptyList())
        )
    }
}