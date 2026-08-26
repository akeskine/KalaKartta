package fi.anssi.kalakartta.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class TalkingClockSchedulingTest {

    private fun calculateNextTime(now: Calendar, intervalMinutes: Int): Calendar {
        val nowMs = now.timeInMillis
        val minutesSinceMidnight = (now.get(Calendar.HOUR_OF_DAY) * 60) + now.get(Calendar.MINUTE)
        val minutesToNext = intervalMinutes - (minutesSinceMidnight % intervalMinutes)
        
        val nextTime = now.clone() as Calendar
        nextTime.add(Calendar.MINUTE, minutesToNext)
        nextTime.set(Calendar.SECOND, 0)
        nextTime.set(Calendar.MILLISECOND, 0)
        
        if (nextTime.timeInMillis <= nowMs + 2000) {
            nextTime.add(Calendar.MINUTE, intervalMinutes)
        }
        
        return nextTime
    }

    @Test
    fun testNewLogicAtEdgeCase() {
        val now = Calendar.getInstance()
        now.set(2026, 7, 26, 10, 14, 57) // 3 sekuntia ennen
        now.set(Calendar.MILLISECOND, 0)
        
        val next = calculateNextTime(now, 5)
        
        val expected = Calendar.getInstance()
        expected.set(2026, 7, 26, 10, 15, 0)
        expected.set(Calendar.MILLISECOND, 0)
        
        assertEquals(expected.timeInMillis, next.timeInMillis)
    }

    @Test
    fun testNewLogicJustBeforeEdge() {
        val now = Calendar.getInstance()
        now.set(2026, 7, 26, 10, 14, 59)
        now.set(Calendar.MILLISECOND, 500) // 500ms päässä
        
        val next = calculateNextTime(now, 5)
        
        // Nyt sen pitäisi hypätä seuraavaan, koska 500ms on < 1000ms
        val expected = Calendar.getInstance()
        expected.set(2026, 7, 26, 10, 20, 0)
        expected.set(Calendar.MILLISECOND, 0)
        
        assertEquals(expected.timeInMillis, next.timeInMillis)
    }
    
    @Test
    fun testUserReportedScenario() {
        // Käyttäjä käynnisti 15:58:30, väli 5 min.
        val now = Calendar.getInstance()
        now.set(2026, 7, 26, 15, 58, 30)
        now.set(Calendar.MILLISECOND, 0)
        
        val next = calculateNextTime(now, 5)
        
        val expected = Calendar.getInstance()
        expected.set(2026, 7, 26, 16, 0, 0)
        expected.set(Calendar.MILLISECOND, 0)
        
        assertEquals("Seuraavan puheajan pitäisi olla 16:00:00", expected.timeInMillis, next.timeInMillis)
    }

    @Test
    fun testStartExactlyAtInterval() {
        // Jos käynnistetään tasan 16:00:00.000
        val now = Calendar.getInstance()
        now.set(2026, 7, 26, 16, 0, 0)
        now.set(Calendar.MILLISECOND, 0)
        
        val next = calculateNextTime(now, 5)
        
        val expected = Calendar.getInstance()
        expected.set(2026, 7, 26, 16, 5, 0)
        expected.set(Calendar.MILLISECOND, 0)
        
        assertEquals("Jos käynnistetään tasan, pitäisi mennä seuraavaan väliin", expected.timeInMillis, next.timeInMillis)
    }

    @Test
    fun testStartJustAfterInterval() {
        // Jos käynnistetään 16:00:00.500
        val now = Calendar.getInstance()
        now.set(2026, 7, 26, 16, 0, 0)
        now.set(Calendar.MILLISECOND, 500)
        
        val next = calculateNextTime(now, 5)
        
        val expected = Calendar.getInstance()
        expected.set(2026, 7, 26, 16, 5, 0)
        expected.set(Calendar.MILLISECOND, 0)
        
        assertEquals("Puskurin pitäisi estää liian läheinen hälytys", expected.timeInMillis, next.timeInMillis)
    }
    @Test
    fun testUserReportedScenario2Min() {
        // Käyttäjä käynnisti 15:58:30, väli 2 min.
        val now = Calendar.getInstance()
        now.set(2026, 7, 26, 15, 58, 30)
        now.set(Calendar.MILLISECOND, 0)
        
        val next = calculateNextTime(now, 2)
        
        val expected = Calendar.getInstance()
        expected.set(2026, 7, 26, 16, 0, 0)
        expected.set(Calendar.MILLISECOND, 0)
        
        assertEquals("Seuraavan puheajan pitäisi olla 16:00:00 (2 min väli)", expected.timeInMillis, next.timeInMillis)
    }
}
