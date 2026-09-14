package fi.anssi.kalakartta.io

/** Tuonnin duplikaattivalinnan puhdas päätöslogiikka. */
object ImportConflictResolver {

    data class Resolution<T>(
        val itemsToInsert: List<T>,
        val existingIdsToDelete: List<Long>
    )

    fun <T> resolve(
        imported: List<T>,
        duplicates: List<Pair<T, T?>>,
        mode: Int,
        idOf: (T) -> Long
    ): Resolution<T> {
        val duplicateImported = duplicates.map { it.first }.toSet()

        return when (mode) {
            0 -> Resolution(
                itemsToInsert = imported.filter { it !in duplicateImported },
                existingIdsToDelete = emptyList()
            )
            1 -> Resolution(
                itemsToInsert = imported,
                existingIdsToDelete = duplicates.mapNotNull { it.second?.let(idOf) }
            )
            2 -> Resolution(
                itemsToInsert = imported,
                existingIdsToDelete = emptyList()
            )
            else -> Resolution(emptyList(), emptyList())
        }
    }
}
