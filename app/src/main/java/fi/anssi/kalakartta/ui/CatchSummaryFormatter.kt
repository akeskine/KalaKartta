package fi.anssi.kalakartta.ui
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
object CatchSummaryFormatter {
    fun format(catches: List<FishCatch>, speciesMap: Map<String, FishSpecies>): String = if (catches.isEmpty()) "Ei saaliita tältä ajalta." else catches.groupBy { it.species }.entries.sortedBy { speciesMap[it.key]?.sortOrder ?: Int.MAX_VALUE }.joinToString("\n\n") { (id, group) ->
        val name = (speciesMap[id]?.name ?: id).lowercase().replaceFirstChar { it.uppercase() }
        if (id == "OTHER") group.groupBy { it.otherSpecies ?: "Tuntematon" }.entries.sortedByDescending { it.value.size }.joinToString("\n\n") { (other, sub) -> "$name (${other.lowercase().replaceFirstChar { it.uppercase() }}) ${sub.size} kpl${data(sub)}" } else "$name ${group.size} kpl${data(group)}"
    }
    private fun data(catches: List<FishCatch>): String { val c = catches.filter { (it.weight ?: 0) > 0 || (it.length ?: 0) > 0 }; if (c.isEmpty()) return ""; return " (" + c.sortedWith(compareByDescending<FishCatch> { it.weight ?: 0L }.thenByDescending { it.length ?: 0L }).joinToString(", ") { val w = if ((it.weight ?: 0) > 0) "${it.weight}g" else null; val l = if ((it.length ?: 0) > 0) "${it.length}cm" else null; w?.let { x -> l?.let { y -> "$x/$y" } ?: x } ?: l.orEmpty() } + ")" }
}
