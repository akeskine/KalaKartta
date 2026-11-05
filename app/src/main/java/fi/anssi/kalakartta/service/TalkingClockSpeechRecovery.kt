package fi.anssi.kalakartta.service

internal data class TalkingClockSpeechRequest(
    val text: String, val flush: Boolean, val utteranceId: String, val retries: Int = 0,
)

/** Retains one failed request while TTS reconnects; the retry cannot reconnect recursively. */
internal class TalkingClockSpeechRecovery {
    private var pending: TalkingClockSpeechRequest? = null

    fun offer(request: TalkingClockSpeechRequest): Boolean {
        pending = if (request.retries == 0) request.copy(retries = 1) else null
        return pending != null
    }

    fun take(): TalkingClockSpeechRequest? = pending.also { pending = null }
    fun clear() { pending = null }
}
