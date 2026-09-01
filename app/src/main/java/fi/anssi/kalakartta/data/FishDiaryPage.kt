package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FishDiaryPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: Long,         // Alkupäivä (tallennettuna Long-muodossa, päivä ilman kellonaikaa, esim. klo 00:00:00)
    val endDate: Long? = null,   // Loppupäivä (jos useamman päivän merkintä)
    val location: String,        // Paikka
    val fishingMethod: String,   // Kalastustapa
    val catch: String,           // Saalis
    val story: String            // Kertomus
)
