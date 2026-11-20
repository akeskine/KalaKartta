package fi.anssi.kalakartta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForegroundStateTest {
    private val state = AppForegroundState()

    @Test
    fun detectsFirstOpenAndReturnFromBackground() {
        assertTrue(state.onActivityStarted())
        state.onActivityStopped(changingConfigurations = false)
        assertTrue(state.onActivityStarted())
    }

    @Test
    fun keepsAppInForegroundWhenReturningFromAnotherScreen() {
        assertTrue(state.onActivityStarted()) // Map opens.
        assertFalse(state.onActivityStarted()) // An in-app screen opens.
        state.onActivityStopped(changingConfigurations = false) // Map becomes hidden.
        assertFalse(state.onActivityStarted()) // Map becomes visible again.
        state.onActivityStopped(changingConfigurations = false) // The other screen closes.
        state.onActivityStopped(changingConfigurations = false) // App goes to the background.
        assertTrue(state.onActivityStarted())
    }

    @Test
    fun keepsAppInForegroundWhileActivityIsRecreated() {
        assertTrue(state.onActivityStarted())
        state.onActivityStopped(changingConfigurations = true)
        assertFalse(state.onActivityStarted())
        state.onActivityStopped(changingConfigurations = false)
        assertTrue(state.onActivityStarted())
    }
}
