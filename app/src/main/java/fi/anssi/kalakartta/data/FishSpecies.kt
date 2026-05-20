package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FishSpecies(
    @PrimaryKey val id: String, // 'PIKE', 'PERCH', 'ZANDER'
    val name: String,
    val small_weight: Long = 0,
    val small_length: Long = 0,
    val large_weight: Long = 0,
    val large_length: Long = 0,
    val giant_weight: Long = 0,
    val giant_length: Long = 0,
    val icon_small: String = "",
    val icon_default: String = "",
    val icon_large: String = "",
    val icon_giant: String = ""
)
