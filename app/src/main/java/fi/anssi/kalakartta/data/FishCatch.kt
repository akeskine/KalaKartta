package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FishCatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val species: String, // viittaus FishSpecies.id
    val latitude: Double,
    val longitude: Double,
    val caughtAt: Long = 0,
    val weight: Long = 0,
    val length: Long = 0,
    val method: String = "",
    val strikeDepth: Double = 0.0,
    val waterDepth: Double = 0.0,
    val waterTemp: Double = 0.0,
    val airTemp: Double = 0.0,
    val cloudiness: Long = 0,
    val rain: Long = 0,
    val windSpeed: Double = 0.0,
    val windDirection: Long = 0,
    val additionalInfo: String = "",
    val originalRef: String = "",
    val tripNotes: String = ""
)