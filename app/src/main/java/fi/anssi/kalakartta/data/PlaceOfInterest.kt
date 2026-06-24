package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaceOfInterest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeId: String, // viittaus PlaceOfInterestType.id
    val latitude: Double,
    val longitude: Double,
    val name: String = "",
    val additionalInfo: String = "",
    val originalRef: String = ""
)
