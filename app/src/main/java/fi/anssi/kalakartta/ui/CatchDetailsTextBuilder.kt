package fi.anssi.kalakartta.ui

import android.content.Context
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CatchDetailsContent(
    val text: String,
    val hasSpecies: Boolean
)

/** Formats the textual content shown in the catch details dialog. */
class CatchDetailsTextBuilder(private val context: Context) {
    fun build(fish: FishCatch?, species: FishSpecies?): CatchDetailsContent {
        val details = StringBuilder()
        var hasSpecies = false

        fish?.let {
            val shouldShowPressureGraph = it.pressureSamples.isNotEmpty() && (it.caughtAt ?: 0L) > 0L
            var pressureGraphMarkerAdded = false
            if (species != null) {
                val speciesName = if (it.species == "OTHER" && !it.otherSpecies.isNullOrEmpty()) {
                    val otherSpeciesDisplay = it.otherSpecies.lowercase().replaceFirstChar { character -> character.uppercase() }
                    "${species.name.lowercase().replaceFirstChar { character -> character.uppercase() }} ($otherSpeciesDisplay)"
                } else if (it.species == "UNKNOWN") {
                    species.name
                } else {
                    species.name.lowercase().replaceFirstChar { character -> character.uppercase() }
                }
                details.append("Laji: $speciesName\n")
                hasSpecies = true
            } else {
                val otherSpeciesDisplay = if (!it.otherSpecies.isNullOrEmpty()) {
                    it.otherSpecies.lowercase().replaceFirstChar { character -> character.uppercase() }
                } else {
                    it.species.lowercase().replaceFirstChar { character -> character.uppercase() }
                }
                details.append("Laji: Muu kalalaji ($otherSpeciesDisplay)\n")
                hasSpecies = true
            }

            it.caughtAt?.let { caughtAt ->
                if (caughtAt > 0) {
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy 'klo' HH:mm", Locale.getDefault())
                    val dateText = dateFormat.format(Date(caughtAt))
                    val timeLabel = if (it.eventType != null && it.eventType != FishCatch.CAUGHT_FISH) {
                        "Aika"
                    } else {
                        "Saantiaika"
                    }
                    details.append("$timeLabel: $dateText\n")
                }
            }

            if (it.fisherman.isNotEmpty()) {
                val fishermanDisplay = it.fisherman.split(" ")
                    .filter(String::isNotEmpty)
                    .joinToString(" ") { part ->
                        part.lowercase().replaceFirstChar { character ->
                            if (character.isLowerCase()) character.titlecase(Locale.getDefault()) else character.toString()
                        }
                    }
                details.append("Kalastaja: $fishermanDisplay\n")
            }

            if (it.method.isNotEmpty()) details.append("Kalastustapa: ${it.method}\n")
            if (!it.lure.isNullOrEmpty() || !it.lureColor.isNullOrEmpty()) {
                val lureParts = listOfNotNull(it.lure, it.lureColor).filter(String::isNotEmpty)
                details.append("Viehe: ${lureParts.joinToString(", ")}\n")
            }

            if (it.weight != null && it.weight > 0) details.append("Paino: ${it.weight} g\n")
            if (it.length != null && it.length > 0) details.append("Pituus: ${it.length} cm\n")

            if (it.weatherSource.isNotEmpty()) {
                details.append("\nSää (${it.weatherSource}):\n")
                if (it.airTemp != null) details.append("  Ilma: ${it.airTemp} °C\n")
                if (it.waterTemp != null) details.append("  Vesi: ${it.waterTemp} °C\n")
                if (it.windSpeed != null) {
                    val direction = if (it.windDirection != null) " (${it.windDirection}°)" else ""
                    details.append("  Tuuli: ${it.windSpeed} m/s$direction")
                    if (it.windDirection != null) details.append(" ")
                    details.append("\n")
                }

                val rainLevels = context.resources.getStringArray(R.array.rain_levels)
                val rainDescription = if (it.rain != null && it.rain.toInt() + 1 < rainLevels.size) {
                    rainLevels[it.rain.toInt() + 1]
                } else {
                    ""
                }
                if (it.cloudiness != null || rainDescription.isNotEmpty() || it.rainHourMm != null) {
                    val weatherParts = mutableListOf<String>()
                    if (it.cloudiness != null) weatherParts.add("Pilvisyys: ${it.cloudiness}/8")
                    if (rainDescription.isNotEmpty()) weatherParts.add("Sade: $rainDescription")
                    if (it.rainHourMm != null) weatherParts.add("Sade: ${it.rainHourMm} mm/h")
                    details.append("  ${weatherParts.joinToString(", ")}\n")
                }

                if (it.pressure != null) {
                    details.append("  Paine: ${it.pressure} hPa\n")
                    if (shouldShowPressureGraph) {
                        details.append(PRESSURE_GRAPH_MARKER)
                        pressureGraphMarkerAdded = true
                    }
                } else if (shouldShowPressureGraph) {
                    details.append(PRESSURE_GRAPH_MARKER)
                    pressureGraphMarkerAdded = true
                }

                if (it.weatherStation.isNotEmpty()) {
                    details.append("  Asema: ${it.weatherStation.substringAfter(":")}\n")
                }
            }

            if (shouldShowPressureGraph && !pressureGraphMarkerAdded) {
                details.append(PRESSURE_GRAPH_MARKER)
            }
            if (it.seaLevel != null) {
                val signedSeaLevel = if (it.seaLevel >= 0) "+${it.seaLevel}" else it.seaLevel.toString()
                details.append("Meriveden korkeus: $signedSeaLevel cm (MW)\n")
            }
            if (it.seaLevelSamples.isNotEmpty() && (it.caughtAt ?: 0L) > 0L) {
                details.append(SEA_LEVEL_GRAPH_MARKER)
            }
            if (it.seaLevelStation.isNotEmpty()) {
                details.append("  Asema: ${it.seaLevelStation.substringAfter(":")}\n")
            }
            if (it.additionalInfo.isNotEmpty()) details.append("\nLisätieto: ${it.additionalInfo}\n")
            if (it.originalRef.isNotEmpty()) details.append("Alkuperäinen viite: ${it.originalRef}\n")
        }

        return CatchDetailsContent(details.toString().trim(), hasSpecies)
    }

    companion object {
        const val PRESSURE_GRAPH_MARKER = "\u0000PRESSURE_GRAPH\u0000"
        const val SEA_LEVEL_GRAPH_MARKER = "\u0000SEA_LEVEL_GRAPH\u0000"
    }
}
