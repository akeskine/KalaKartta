package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaceOfInterestType(
    @PrimaryKey val id: String, // 'ACCOMMODATION', 'CAMP', etc.
    val name: String,
    val icon: String = "",
    val sortOrder: Int = 0
) {
    companion object {
        fun getDefaultList(): List<PlaceOfInterestType> {
            return listOf(
                PlaceOfInterestType("ROCK", "Kivi", icon = "kivi", sortOrder = 1),
                PlaceOfInterestType("VEGETATION", "Kasvusto", icon = "vesikasvi", sortOrder = 2),
                PlaceOfInterestType("SHALLOW", "Matalikko", icon = "matalikko", sortOrder = 3),
                PlaceOfInterestType("DEEP", "Syvänne", icon = "syvanne", sortOrder = 4),
                PlaceOfInterestType("PARKING", "Pysäköinti", icon = "pysakointi", sortOrder = 5),
                PlaceOfInterestType("ACCESS", "Pääsy rantaan", icon = "access", sortOrder = 6),
                PlaceOfInterestType("LANDINGSPOT", "Rantautumispaikka", icon = "rantautumispaikka", sortOrder = 7),
                PlaceOfInterestType("RAMP", "Veneramppi", icon = "ramppi", sortOrder = 8),
                PlaceOfInterestType("HARBOUR", "Satama", icon = "satama", sortOrder = 9),
                PlaceOfInterestType("ACCOMMODATION", "Majoitus", icon = "majoitus", sortOrder = 10),
                PlaceOfInterestType("CAMP", "Leiripaikka", icon = "leiripaikka", sortOrder = 11),
                PlaceOfInterestType("CAMPFIRE", "Tulipaikka", icon = "tulipaikka", sortOrder = 12),
                PlaceOfInterestType("SHELTER", "Laavu", icon = "laavu", sortOrder = 13),
                PlaceOfInterestType("OTHER", "Muu kiinnostava paikka", icon = "tahti", sortOrder = 14),
                PlaceOfInterestType("PROSPECT", "Mahdollinen kalapaikka", icon = "ehka", sortOrder = 15)
            )
        }
    }
}
