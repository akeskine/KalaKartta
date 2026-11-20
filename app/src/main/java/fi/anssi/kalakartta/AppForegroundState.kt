package fi.anssi.kalakartta

internal class AppForegroundState {
    private var startedActivities = 0
    private var inBackground = true

    fun onActivityStarted(): Boolean {
        startedActivities++
        val enteringForeground = inBackground
        inBackground = false
        return enteringForeground
    }

    fun onActivityStopped(changingConfigurations: Boolean) {
        startedActivities--
        // Android starts the incoming screen before stopping the outgoing screen.
        if (startedActivities == 0 && !changingConfigurations) {
            inBackground = true
        }
    }
}
