package fi.anssi.kalakartta.io

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportConflictResolverTest {

    private data class Item(val id: Long, val name: String)

    private val first = Item(1, "ensimmainen")
    private val second = Item(2, "toinen")
    private val existing = Item(10, "nykyinen")
    private val duplicates = listOf(first to existing)

    @Test
    fun skipRemovesOnlyDuplicateImportedItems() {
        val result = ImportConflictResolver.resolve(
            imported = listOf(first, second),
            duplicates = duplicates,
            mode = 0,
            idOf = Item::id
        )

        assertEquals(listOf(second), result.itemsToInsert)
        assertEquals(emptyList<Long>(), result.existingIdsToDelete)
    }

    @Test
    fun replaceKeepsImportedItemsAndDeletesMatchingExistingIds() {
        val result = ImportConflictResolver.resolve(
            imported = listOf(first, second),
            duplicates = duplicates,
            mode = 1,
            idOf = Item::id
        )

        assertEquals(listOf(first, second), result.itemsToInsert)
        assertEquals(listOf(10L), result.existingIdsToDelete)
    }

    @Test
    fun allKeepsBothSidesWithoutDeletes() {
        val result = ImportConflictResolver.resolve(
            imported = listOf(first, second),
            duplicates = duplicates,
            mode = 2,
            idOf = Item::id
        )

        assertEquals(listOf(first, second), result.itemsToInsert)
        assertEquals(emptyList<Long>(), result.existingIdsToDelete)
    }
}
