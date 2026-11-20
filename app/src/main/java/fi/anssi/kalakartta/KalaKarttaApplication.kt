package fi.anssi.kalakartta

import android.app.Activity
import android.app.Application
import android.os.Bundle

class KalaKarttaApplication : Application(), Application.ActivityLifecycleCallbacks {
    private val foregroundState = AppForegroundState()
    private var foregroundEntryActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    internal fun isForegroundEntry(activity: Activity): Boolean =
        foregroundEntryActivity === activity

    override fun onActivityStarted(activity: Activity) {
        if (foregroundState.onActivityStarted()) {
            foregroundEntryActivity = activity
        }
    }

    override fun onActivityStopped(activity: Activity) {
        foregroundState.onActivityStopped(activity.isChangingConfigurations)
        if (foregroundEntryActivity === activity) {
            foregroundEntryActivity = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
