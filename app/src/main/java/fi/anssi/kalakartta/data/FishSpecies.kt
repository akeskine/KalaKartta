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
    val icon_giant: String = "",
    val favourite_fish: Boolean = true,
    val sortOrder: Int = 0
) {
    companion object {
        fun getDefaultList(): List<FishSpecies> {
            return listOf(
                FishSpecies(
                    "PERCH", "Ahven", icon_default = "ahven",
                    small_weight = 200, small_length = 25,
                    large_weight = 500, large_length = 35,
                    giant_weight = 800, giant_length = 40,
                    sortOrder = 1
                ),
                FishSpecies(
                    "PIKE", "Hauki", icon_default = "hauki",
                    small_weight = 1000, small_length = 55,
                    large_weight = 3000, large_length = 80,
                    giant_weight = 8000, giant_length = 100,
                    sortOrder = 2
                ),
                FishSpecies(
                    "ZANDER", "Kuha", icon_default = "kuha",
                    small_weight = 800, small_length = 42,
                    large_weight = 2000, large_length = 60,
                    giant_weight = 5000, giant_length = 80,
                    sortOrder = 3
                ),
                FishSpecies(
                    "TROUT", "Taimen", icon_default = "taimen",
                    small_weight = 1000, small_length = 45,
                    large_weight = 2000, large_length = 55,
                    giant_weight = 4000, giant_length = 75,
                    sortOrder = 4
                ),
                FishSpecies(
                    "SALMON", "Lohi", icon_default = "lohi",
                    small_weight = 2000, small_length = 60,
                    large_weight = 5000, large_length = 80,
                    giant_weight = 10000, giant_length = 90,
                    sortOrder = 5
                ),
                FishSpecies(
                    "GRAYLING", "Harjus", icon_default = "harjus",
                    small_weight = 500, small_length = 40,
                    large_weight = 1000, large_length = 50,
                    giant_weight = 1500, giant_length = 55,
                    sortOrder = 6
                ),
                FishSpecies(
                    "WHITEFISH", "Siika", icon_default = "siika",
                    small_weight = 300, small_length = 35,
                    large_weight = 800, large_length = 45,
                    giant_weight = 2000, giant_length = 55,
                    sortOrder = 7
                ),
                FishSpecies(
                    "RAINBOW", "Kirjolohi", icon_default = "kirjolohi",
                    small_weight = 1000, small_length = 40,
                    large_weight = 2000, large_length = 50,
                    giant_weight = 3000, giant_length = 60,
                    sortOrder = 8
                ),
                FishSpecies(
                    "BREAM", "Lahna", icon_default = "lahna",
                    small_weight = 500, small_length = 35,
                    large_weight = 1000, large_length = 45,
                    giant_weight = 2000, giant_length = 55,
                    sortOrder = 9
                ),
                FishSpecies(
                    "IDE", "Säyne", icon_default = "sayne",
                    small_weight = 500, small_length = 35,
                    large_weight = 1000, large_length = 45,
                    giant_weight = 2000, giant_length = 55,
                    sortOrder = 10
                ),
                FishSpecies(
                    "CHAR", "Rautu", icon_default = "rautu",
                    small_weight = 500, small_length = 35,
                    large_weight = 1000, large_length = 45,
                    giant_weight = 2000, giant_length = 55,
                    sortOrder = 11
                ),
                FishSpecies(
                    "BURBOT", "Made", icon_default = "made",
                    small_weight = 1000, small_length = 50,
                    large_weight = 2000, large_length = 60,
                    giant_weight = 3000, giant_length = 70,
                    sortOrder = 12
                ),
                FishSpecies("OTHER", "Muu kalalaji", icon_default = "muukala",
                    small_weight = 500, small_length = 30,
                    large_weight = 2000, large_length = 60,
                    giant_weight = 8000, giant_length = 90,
                    sortOrder = 13
                )
            )
        }
    }
}
