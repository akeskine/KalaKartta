package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FishCatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val species: String, // viittaus FishSpecies.id
    val eventType: String? = null,
    val latitude: Double,
    val longitude: Double,
    val caughtAt: Long?,
    val weight: Long? = null,
    val length: Long? = null,
    val method: String = "",
    val strikeDepth: Double? = null,
    val waterDepth: Double? = null,
    val waterTemp: Double? = null,
    val airTemp: Double? = null,
    val cloudiness: Long? = null,
    val rain: Long? = null,
    val rainHourMm: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: Long? = null,
    val pressure: Double? = null,
    val weatherSource: String = "",
    val weatherTime: Long? = null,
    val weatherStation: String = "",
    val additionalInfo: String = "",
    val originalRef: String = "",
    val fisherman: String = "",
    val lure: String? = null,
    val lureColor: String? = null,
    val otherSpecies: String? = null,
    /**
     * Aika, jolloin säätiedot on todettu "valmiiksi" siinä mielessä, että kaikki mahdolliset
     * asemat (max 5 kpl 300km säteellä) on kokeiltu, vaikka joitain tietoja jäisikin puuttumaan.
     * Jos tämä on asetettu, sovellus ei yritä hakea säätietoja uudelleen automaattisesti.
     */
    val weatherDataCompleteTime: Long? = null,
    val pressureTrend: Double? = null, // hPa/h
    val pressureTurningTrend: Double? = null, // hPa/h
    val pressureSamples: List<PressureSample> = emptyList(),
    val seaLevel: Long? = null,
    val seaLevelDataCompleteTime: Long? = null,
    val seaLevelTrend: Double? = null, // cm/h
    val seaLevelTurningTrend: Double? = null, // cm/h
    val seaLevelSamples: List<SeaLevelSample> = emptyList(),
    val moonPhase: Double? = null,
    val moonAltitude: Double? = null
) {
    fun calculatePressureTrend(): Double? {
        return calculateAveragePressureTrend(pressureSamples)
    }

    fun calculatePressureTurningTrend(): Double? {
        caughtAt ?: return null
        val sortedSamples = pressureSamples.sortedBy { it.time }
        if (sortedSamples.size < 12) return null

        val trendBefore = calculateAveragePressureTrend(sortedSamples.take(6)) ?: return null
        val trendAfter = calculateAveragePressureTrend(sortedSamples.takeLast(6)) ?: return null

        return trendAfter - trendBefore
    }

    fun calculateSeaLevelTrend(): Double? {
        return calculateAverageSeaLevelTrend(seaLevelSamples)
    }

    fun calculateSeaLevelTurningTrend(): Double? {
        caughtAt ?: return null
        val sortedSamples = seaLevelSamples.sortedBy { it.time }
        if (sortedSamples.size < 12) return null

        val trendBefore = calculateAverageSeaLevelTrend(sortedSamples.take(6)) ?: return null
        val trendAfter = calculateAverageSeaLevelTrend(sortedSamples.takeLast(6)) ?: return null

        return trendAfter - trendBefore
    }

    private fun calculateAveragePressureTrend(samples: List<PressureSample>): Double? {
        if (samples.size < 2) return null

        val sortedSamples = samples.sortedBy { it.time }
        val first = sortedSamples.first()
        val last = sortedSamples.last()
        val elapsedHours = (last.time - first.time).toDouble() / (1000 * 60 * 60)
        if (elapsedHours == 0.0) return null

        return (last.pressure - first.pressure) / elapsedHours
    }

    private fun calculateAverageSeaLevelTrend(samples: List<SeaLevelSample>): Double? {
        if (samples.size < 2) return null

        val sortedSamples = samples.sortedBy { it.time }
        val first = sortedSamples.first()
        val last = sortedSamples.last()
        val elapsedHours = (last.time - first.time).toDouble() / (1000 * 60 * 60)
        if (elapsedHours == 0.0) return null

        return (last.seaLevel - first.seaLevel) / elapsedHours
    }

    companion object {
        const val CAUGHT_FISH = "CAUGHT_FISH"
        const val LOST_FISH = "LOST_FISH"
        const val STRIKE_CERTAIN = "STRIKE_CERTAIN"
        const val STRIKE_UNCERTAIN = "STRIKE_UNCERTAIN"
        const val FISH_FOLLOW = "FISH_FOLLOW"

        fun getEventTypeName(type: String?): String {
            return when (type) {
                LOST_FISH -> "Karkuutus"
                STRIKE_CERTAIN -> "Varma tärppi"
                STRIKE_UNCERTAIN -> "Epävarma tärppi"
                FISH_FOLLOW -> "Seurio"
                else -> "Saatu kala"
            }
        }
    }
}