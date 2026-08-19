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
    val tripNotes: String = "",
    val fisherman: String = "",
    val lure: String? = null,
    val lureColor: String? = null,
    val otherSpecies: String? = null,
    /**
     * Aika, jolloin säätiedot on todettu "valmiiksi" siinä mielessä, että kaikki mahdolliset
     * asemat (max 5 kpl 300km säteellä) on kokeiltu, vaikka joitain tietoja jäisikin puuttumaan.
     * Jos tämä on asetettu, sovellus ei yritä hakea säätietoja uudelleen automaattisesti.
     */
    val weatherDataCompleteTime: Long? = null
) {
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