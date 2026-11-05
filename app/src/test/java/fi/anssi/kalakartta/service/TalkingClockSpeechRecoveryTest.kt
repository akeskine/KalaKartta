package fi.anssi.kalakartta.service

import org.junit.Assert.*
import org.junit.Test

class TalkingClockSpeechRecoveryTest {
    @Test fun disconnectedRequestResumesOnceWithItsOriginalTextQueueAndPurpose() {
        val recovery = TalkingClockSpeechRecovery()
        val original = TalkingClockSpeechRequest("<LAULU>" + " ".repeat(12000),false,"SessionEnded")
        assertTrue(recovery.offer(original))
        val retry = recovery.take()!!
        assertEquals(original.text,retry.text)
        assertEquals(original.flush,retry.flush)
        assertEquals(original.utteranceId,retry.utteranceId)
        assertEquals(1,retry.retries)
        assertNull(recovery.take())
        assertFalse(recovery.offer(retry))
        assertNull(recovery.take())
    }

    @Test fun newerServiceActionOrShutdownCancelsStaleRetry() {
        val recovery = TalkingClockSpeechRecovery()
        assertTrue(recovery.offer(TalkingClockSpeechRequest("Kello on yhdeksäntoista.",true,"TalkingClock")))
        recovery.clear()
        assertNull(recovery.take())
        val next = TalkingClockSpeechRequest("Kalastus päättyi.",true,"SessionEnded")
        assertTrue(recovery.offer(next))
        assertEquals("SessionEnded",recovery.take()!!.utteranceId)
    }
}
