package fi.anssi.kalakartta.service

import org.junit.Assert.*
import org.junit.Test

class TalkingClockScoreStoreTest {
    @Test fun ordinaryClockSpeechAndShortSongsKeepTheDirectPath() {
        for (text in listOf("Kello on yhdeksäntoista.", "<LAULU>[C4:500] laa</LAULU>", "<LAULU>" + " ".repeat(3993))) {
            assertFalse(TalkingClockScoreStore.needsTransport(text,4000,TalkingClockScoreStore.ENGINE,"fi-FI"))
        }
        assertFalse(TalkingClockScoreStore.needsTransport("a".repeat(12000),4000,TalkingClockScoreStore.ENGINE,"fi-FI"))
    }

    @Test fun longSongTransportRequiresPuhe64AndItsFinnishVoice() {
        val text = "<laulu>[C4:500] laa</laulu>" + " ".repeat(12000)
        assertTrue(TalkingClockScoreStore.needsTransport(text,4000,TalkingClockScoreStore.ENGINE,"fi-FI"))
        assertFalse(TalkingClockScoreStore.needsTransport(text,4000,"another.engine","fi-FI"))
        assertFalse(TalkingClockScoreStore.needsTransport(text,4000,TalkingClockScoreStore.ENGINE,"fallback-voice"))
        assertFalse(TalkingClockScoreStore.needsTransport(text,4000,null,null))
    }

    @Test fun independentMemoryRequestsRetainUnicodeUntilExplicitRelease() {
        val text = "<LAULU>[A2:173:58:231] käänt</LAULU> Kello on yhdeksäntoista. " + " ".repeat(12386)
        val first = TalkingClockScoreStore.put(text)
        val second = TalkingClockScoreStore.put(text)
        try {
            assertNotEquals(first,second)
            assertEquals(text,TalkingClockScoreStore.get(first))
            TalkingClockScoreStore.remove(first)
            assertNull(TalkingClockScoreStore.get(first))
            assertEquals(text,TalkingClockScoreStore.get(second))
            TalkingClockScoreStore.remove(first)
            assertNull(TalkingClockScoreStore.get("unknown-token"))
        } finally {
            TalkingClockScoreStore.remove(first); TalkingClockScoreStore.remove(second)
        }
    }

    @Test fun memoryRequestsEnforceTheExistingPuhe64ScoreLimit() {
        assertThrows(IllegalArgumentException::class.java) { TalkingClockScoreStore.put(" ".repeat(TalkingClockScoreStore.MAX_LENGTH+1)) }
    }
}
