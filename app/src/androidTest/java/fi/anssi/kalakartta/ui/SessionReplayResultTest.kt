package fi.anssi.kalakartta.ui

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionReplayResultTest {

    @Test
    fun replayResultRoundTripPreservesSessionAndFilter() {
        val intent = SessionReplayResult.createIntent(42L, onlySessionCatches = true)

        val request = SessionReplayResult.parse(Activity.RESULT_OK, intent)

        assertEquals(SessionReplayRequest(42L, true), request)
    }

    @Test
    fun nonReplayResultIsIgnored() {
        val intent = SessionReplayResult.createIntent(42L, onlySessionCatches = false)
            .apply { putExtra(SessionReplayResult.EXTRA_REPLAY_REQUEST, false) }

        assertNull(SessionReplayResult.parse(Activity.RESULT_OK, intent))
        assertNull(SessionReplayResult.parse(Activity.RESULT_CANCELED, intent))
    }

    @Test
    fun replayResultWithoutSessionIsIgnored() {
        val intent = SessionReplayResult.createIntent(-1L, onlySessionCatches = false)

        assertNull(SessionReplayResult.parse(Activity.RESULT_OK, intent))
    }
}
