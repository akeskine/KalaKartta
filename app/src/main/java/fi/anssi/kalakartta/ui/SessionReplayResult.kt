package fi.anssi.kalakartta.ui

import android.app.Activity
import android.content.Intent

data class SessionReplayRequest(
    val sessionId: Long,
    val onlySessionCatches: Boolean
)

object SessionReplayResult {
    const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
    const val EXTRA_REPLAY_REQUEST = "EXTRA_REPLAY_REQUEST"
    const val EXTRA_ONLY_SESSION_CATCHES = "EXTRA_ONLY_SESSION_CATCHES"

    fun createIntent(sessionId: Long, onlySessionCatches: Boolean): Intent =
        Intent().apply {
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_REPLAY_REQUEST, true)
            putExtra(EXTRA_ONLY_SESSION_CATCHES, onlySessionCatches)
        }

    fun parse(resultCode: Int, data: Intent?): SessionReplayRequest? {
        if (resultCode != Activity.RESULT_OK || data?.getBooleanExtra(EXTRA_REPLAY_REQUEST, false) != true) {
            return null
        }

        val sessionId = data.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId == -1L) return null

        return SessionReplayRequest(
            sessionId = sessionId,
            onlySessionCatches = data.getBooleanExtra(EXTRA_ONLY_SESSION_CATCHES, false)
        )
    }
}
