package fi.anssi.kalakartta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Media(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pointTime: Long? = null,
    val mimeType: String,
    val originalFileName: String,
    val fileName: String, // sovelluksen sisäinen tiedostonimi (esim. <uuid>_<alkuperäinen>)
    val externalId: String? = null // Käytetään importissa duplikaattien tunnistamiseen (alkuperäinen ID exportista)
)
