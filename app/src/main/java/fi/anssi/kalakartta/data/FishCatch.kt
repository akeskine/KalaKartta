package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FishCatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val species: String, // viittaus FishSpecies.id
    val latitude: Double,
    val longitude: Double,
    val caughtAt: Long,
    val weight: Long? = null,
    val length: Long? = null,
    val method: String = "",
    val strikeDepth: Double? = null,
    val waterDepth: Double? = null,
    val waterTemp: Double? = null,
    val airTemp: Double? = null,
    val cloudiness: Long? = null,
    val rain: Long? = null,
    val windSpeed: Double? = null,
    val windDirection: Long? = null,
    val pressure: Double? = null,
    val weatherSource: String = "",
    val weatherTime: Long? = null,
    val weatherStation: String = "",
    val additionalInfo: String = "",
    val originalRef: String = "",
    val tripNotes: String = ""
)