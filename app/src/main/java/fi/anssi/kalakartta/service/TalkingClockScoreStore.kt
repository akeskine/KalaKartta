package fi.anssi.kalakartta.service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Process-local text only. No files, preferences or database entries are created. */
internal object TalkingClockScoreStore {
    const val ENGINE = "fi.anssi.puhe64.tts"
    const val URI_PARAMETER = "fi.anssi.puhe64.tts.scoreUri"
    const val PLACEHOLDER = "<LAULU>"
    const val MAX_LENGTH = 1_048_576
    private val scores = ConcurrentHashMap<String, String>()
    private val song = Regex("<LAULU\\b", RegexOption.IGNORE_CASE)

    fun needsTransport(text: String, limit: Int, selectedEngine: String?, voice: String?): Boolean =
        text.length > limit && selectedEngine == ENGINE && voice == "fi-FI" && song.containsMatchIn(text)

    fun put(text: String): String {
        require(text.length <= MAX_LENGTH) { "Lauluteksti on liian pitkä" }
        return UUID.randomUUID().toString().also { scores[it] = text }
    }

    fun get(token: String): String? = scores[token]
    fun remove(token: String) { scores.remove(token) }
}
