package fi.anssi.kalakartta.utils

import java.util.Locale

fun formatFishermanName(name: String): String {
    if (name.isBlank()) return ""
    return name.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { part ->
        part.lowercase().replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
        }
    }
}
