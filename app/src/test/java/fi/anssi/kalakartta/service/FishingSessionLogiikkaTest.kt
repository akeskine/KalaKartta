package fi.anssi.kalakartta.service

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Test

class FishingSessionLogiikkaTest {

    // Simuloitu Location, koska emme voi helposti mockata sitä ilman Mockito-riippuvuutta tässä ympäristössä
    class MockLocation(
        private val lat: Double,
        private val lon: Double
    ) {
        fun getLat() = lat
        fun getLon() = lon

        fun distanceTo(other: MockLocation): Float {
            val dLat = (lat - other.lat) * 111320
            val dLon = (lon - other.lon) * 111320 * Math.cos(Math.toRadians(lat))
            return Math.sqrt(dLat * dLat + dLon * dLon).toFloat()
        }
    }

    class RecordingLogic(
        val minInt: Int,
        val maxInt: Int,
        val minDist: Int
    ) {
        var lastSavedTimestamp: Long = 0
        var lastSavedLocation: MockLocation? = null
        var currentStationaryIntervalSeconds: Int = minInt
        var saveCount = 0

        fun onLocationChanged(now: Long, location: MockLocation): Boolean {
            val timeSinceLastSave = now - lastSavedTimestamp
            val distanceSinceLastSave = lastSavedLocation?.distanceTo(location) ?: Float.MAX_VALUE

            val shouldSave: Boolean
            val isStationary = distanceSinceLastSave < minDist

            if (!isStationary) {
                // Liikkeellä
                shouldSave = timeSinceLastSave >= minInt * 1000L
                if (shouldSave) {
                    saveCount++
                    lastSavedTimestamp = now
                    lastSavedLocation = location
                    currentStationaryIntervalSeconds = minInt // Nollaus
                    return true
                }
            } else {
                // Paikallaan
                shouldSave = timeSinceLastSave >= currentStationaryIntervalSeconds * 1000L
                if (shouldSave) {
                    saveCount++
                    lastSavedTimestamp = now
                    lastSavedLocation = location
                    // Kasvatus
                    currentStationaryIntervalSeconds = Math.min(currentStationaryIntervalSeconds * 2, maxInt)
                    return true
                }
            }
            return false
        }
    }

    @Test
    fun testStationaryIntervalDoubling() {
        val logic = RecordingLogic(minInt = 30, maxInt = 300, minDist = 20)
        var now = 1000000L
        val loc = MockLocation(60.0, 25.0)

        // Ensimmäinen tallennus (alku)
        logic.onLocationChanged(now, loc)
        assertEquals(1, logic.saveCount)
        assertEquals(30, logic.currentStationaryIntervalSeconds)

        // Paikallaan, 30s kulunut
        now += 30 * 1000L
        assertTrue("Pitäisi tallentaa 30s jälkeen", logic.onLocationChanged(now, loc))
        assertEquals(2, logic.saveCount)
        assertEquals(60, logic.currentStationaryIntervalSeconds)

        // Paikallaan, 30s kulunut (ei vielä pitäisi tallentaa, koska väli on 60s)
        now += 30 * 1000L
        assertFalse("Ei pitäisi tallentaa vielä 30s jälkeen", logic.onLocationChanged(now, loc))
        
        // Paikallaan, yhteensä 60s kulunut edellisestä
        now += 30 * 1000L
        assertTrue("Pitäisi tallentaa 60s jälkeen", logic.onLocationChanged(now, loc))
        assertEquals(3, logic.saveCount)
        assertEquals(120, logic.currentStationaryIntervalSeconds)
    }

    @Test
    fun testMaxIntervalLimit() {
        val logic = RecordingLogic(minInt = 30, maxInt = 300, minDist = 20)
        var now = 1000000L
        val loc = MockLocation(60.0, 25.0)

        logic.onLocationChanged(now, loc) // 1. tallennus (t=0, interval=30)
        now += 30 * 1000L
        logic.onLocationChanged(now, loc) // 2. tallennus (t=30, interval=60)
        now += 60 * 1000L
        logic.onLocationChanged(now, loc) // 3. tallennus (t=90, interval=120)
        now += 120 * 1000L
        logic.onLocationChanged(now, loc) // 4. tallennus (t=210, interval=240)
        now += 240 * 1000L
        logic.onLocationChanged(now, loc) // 5. tallennus (t=450, interval=300)
        
        assertEquals(300, logic.currentStationaryIntervalSeconds)
        
        now += 300 * 1000L
        logic.onLocationChanged(now, loc) // 6. tallennus (t=750, interval=300)
        assertEquals(300, logic.currentStationaryIntervalSeconds)
    }

    @Test
    fun testResetWhenMoving() {
        val logic = RecordingLogic(minInt = 30, maxInt = 300, minDist = 20)
        var now = 1000000L
        val loc1 = MockLocation(60.0, 25.0)

        logic.onLocationChanged(now, loc1) // 1. tallennus
        now += 30 * 1000L
        logic.onLocationChanged(now, loc1) // 2. tallennus, väli nyt 60
        assertEquals(60, logic.currentStationaryIntervalSeconds)

        // Liikutaan yli 20m
        val loc2 = MockLocation(60.001, 25.0) // n. 111m päässä
        now += 30 * 1000L
        assertTrue("Pitäisi tallentaa kun liikuttu yli minimietäisyyden", logic.onLocationChanged(now, loc2))
        assertEquals(3, logic.saveCount)
        assertEquals(30, logic.currentStationaryIntervalSeconds) // Nollaantunut
    }

    @Test
    fun testSmallMovementDoesNotReset() {
        val logic = RecordingLogic(minInt = 30, maxInt = 300, minDist = 20)
        var now = 1000000L
        val loc1 = MockLocation(60.0, 25.0)

        logic.onLocationChanged(now, loc1)
        now += 30 * 1000L
        logic.onLocationChanged(now, loc1) // väli 60
        
        // Liikutaan vähän (alle 20m)
        val loc2 = MockLocation(60.0001, 25.0) // n. 11m päässä
        now += 30 * 1000L
        assertFalse("Ei pitäisi tallentaa, koska matka < 20m ja aika < 60s", logic.onLocationChanged(now, loc2))
        assertEquals(60, logic.currentStationaryIntervalSeconds) // Ei nollausta
    }
    
    @Test
    fun testCustomSettings() {
        // minimi 20 s, maksimi 90 s ja minimietäisyys 35 m. Tällöin paikallaanolovälien pitää olla 20 → 40 → 80 → 90 → 90.
        val logic = RecordingLogic(minInt = 20, maxInt = 90, minDist = 35)
        var now = 1000000L
        val loc = MockLocation(60.0, 25.0)
        
        logic.onLocationChanged(now, loc) // 1. (t=0), next=20
        assertEquals(20, logic.currentStationaryIntervalSeconds)
        
        now += 20 * 1000L
        logic.onLocationChanged(now, loc) // 2. (t=20), next=40
        assertEquals(40, logic.currentStationaryIntervalSeconds)
        
        now += 40 * 1000L
        logic.onLocationChanged(now, loc) // 3. (t=60), next=80
        assertEquals(80, logic.currentStationaryIntervalSeconds)
        
        now += 80 * 1000L
        logic.onLocationChanged(now, loc) // 4. (t=140), next=90 (min(160, 90))
        assertEquals(90, logic.currentStationaryIntervalSeconds)
        
        now += 90 * 1000L
        logic.onLocationChanged(now, loc) // 5. (t=230), next=90
        assertEquals(90, logic.currentStationaryIntervalSeconds)
    }

    private fun assertTrue(msg: String, condition: Boolean) {
        if (!condition) throw AssertionError(msg)
    }
    
    private fun assertFalse(msg: String, condition: Boolean) {
        if (condition) throw AssertionError(msg)
    }
}
