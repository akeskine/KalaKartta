package fi.anssi.kalakartta.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech

internal class TalkingClockScoreTransport(private val context: Context) {
    class ConnectionUnavailable : IllegalStateException("TTS voice is unavailable")
    data class Input(val text: String, val params: Bundle? = null)
    private val pending = mutableMapOf<String, Uri>()

    fun prepare(text: String, id: String, tts: TextToSpeech): Input {
        if (text.length <= TextToSpeech.getMaxSpeechInputLength()) return Input(text)
        val voice = tts.voice ?: throw ConnectionUnavailable()
        if (!TalkingClockScoreStore.needsTransport(text, TextToSpeech.getMaxSpeechInputLength(), tts.defaultEngine, voice.name))
            return Input(text)
        val token = TalkingClockScoreStore.put(text)
        val uri = Uri.Builder().scheme("content").authority("${context.packageName}.talking-clock-scores")
            .appendPath(token).build()
        pending[id] = uri
        try {
            context.grantUriPermission(TalkingClockScoreStore.ENGINE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return Input(TalkingClockScoreStore.PLACEHOLDER, Bundle().apply {
                putString(TalkingClockScoreStore.URI_PARAMETER, uri.toString())
            })
        } catch (error: Exception) {
            release(id)
            throw error
        }
    }

    fun release(id: String) {
        val uri = pending.remove(id) ?: return
        uri.lastPathSegment?.let(TalkingClockScoreStore::remove)
        runCatching { context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun clear() { pending.keys.toList().forEach(::release) }
}
