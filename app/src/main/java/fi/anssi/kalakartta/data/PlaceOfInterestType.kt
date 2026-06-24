package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaceOfInterestType(
    @PrimaryKey val id: String, // 'ACCOMMODATION', 'CAMP', etc.
    val name: String,
    val icon: String = ""
)
