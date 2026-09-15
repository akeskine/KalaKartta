package fi.anssi.kalakartta.data

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/**
 * Repossa muokattava sanasto päiväkirjahakua varten.
 *
 * Varsinainen lista on assets/fish_diary_search_dictionary.txt-tiedostossa,
 * jotta uusia hakusanoja voi lisätä ilman hakulogiikan muuttamista.
 */
object FishDiarySearchDictionary {
    const val ASSET_FILE = "fish_diary_search_dictionary.txt"

    data class Entry(val name: String, val aliases: List<String>)

    fun load(context: Context): List<Entry> {
        val entries = runCatching {
            context.assets.open(ASSET_FILE).bufferedReader().useLines { lines ->
                lines.mapNotNull(::parseLine).toList()
            }
        }.getOrDefault(emptyList())

        return entries.ifEmpty { defaultEntries() }
    }

    fun aliasesFor(term: String, entries: List<Entry>): List<String> {
        val normalizedTerm = normalize(term)
        val entry = entries.firstOrNull { candidate ->
            normalize(candidate.name) == normalizedTerm ||
                    candidate.aliases.any { normalize(it) == normalizedTerm }
        }

        return linkedSetOf<String>().apply {
            add(normalize(term))
            entry?.let {
                add(normalize(it.name))
                it.aliases.forEach { alias -> add(normalize(alias)) }
            }
        }.filter { it.isNotEmpty() }
    }

    fun normalize(value: String): String {
        return Normalizer.normalize(value.trim().lowercase(Locale("fi", "FI")), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
    }

    private fun parseLine(line: String): Entry? {
        val content = line.substringBefore('#').trim()
        if (content.isEmpty()) return null

        val parts = content.split("|", limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) return null

        val aliases = parts[1].split(',').map(String::trim).filter(String::isNotEmpty)
        return Entry(parts[0].trim(), aliases)
    }

    private fun defaultEntries(): List<Entry> = listOf(
        Entry("Ahven", listOf("ahven", "ahvenen", "ahvenkalastus", "ahvenen kalastus", "perch")),
        Entry("Hauki", listOf("hauki", "hauen", "hauenkalastus", "hauen kalastus", "haukikalastus", "pike")),
        Entry("Kuha", listOf("kuha", "kuhan", "kuhankalastus", "kuhan kalastus", "kuhakalastus", "zander")),
        Entry("Taimen", listOf("taimen", "taimenen", "taimenkalastus", "taimenen kalastus", "trout")),
        Entry("Lohi", listOf("lohi", "lohen", "lohikalastus", "lohen kalastus", "salmon")),
        Entry("Harjus", listOf("harjus", "harjuksen", "harjuksenkalastus", "harjuksen kalastus", "grayling")),
        Entry("Siika", listOf("siika", "siian", "siikakalastus", "siian kalastus", "whitefish")),
        Entry("Kirjolohi", listOf("kirjolohi", "kirjolohen", "kirjolohikalastus", "kirjolohen kalastus", "rainbow trout")),
        Entry("Lahna", listOf("lahna", "lahnan", "lahnakalastus", "lahnan kalastus", "bream")),
        Entry("Säyne", listOf("säyne", "säynen", "säynekalastus", "säynen kalastus", "ide")),
        Entry("Rautu", listOf("rautu", "raudun", "rautukalastus", "raudun kalastus", "arctic char")),
        Entry("Made", listOf("made", "mateen", "mateenkalastus", "mateen kalastus", "burbot")),
        Entry("Muu kalalaji", listOf("muu", "muu kalalaji", "muukala", "other"))
    )
}