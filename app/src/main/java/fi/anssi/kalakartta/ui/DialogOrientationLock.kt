package fi.anssi.kalakartta.ui

import android.app.Activity
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.view.View
import java.util.Collections
import java.util.IdentityHashMap

/** Keeps the activity orientation fixed while one or more dialogs are visible. */
class DialogOrientationLock(
    private val activity: Activity
) {
    private val trackedDialogs = Collections.newSetFromMap(IdentityHashMap<Dialog, Boolean>())
    private val detachListeners = IdentityHashMap<Dialog, View.OnAttachStateChangeListener>()
    private var previousRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    fun show(dialog: Dialog): Dialog {
        track(dialog)
        dialog.show()
        attachDetachListener(dialog)
        return dialog
    }

    fun trackShown(dialog: Dialog) {
        track(dialog)
        attachDetachListener(dialog)
    }

    fun clear() {
        detachListeners.forEach { (dialog, listener) ->
            dialog.window?.decorView?.removeOnAttachStateChangeListener(listener)
        }
        detachListeners.clear()
        trackedDialogs.clear()
        restoreOrientation()
    }

    private fun track(dialog: Dialog) {
        if (!trackedDialogs.add(dialog)) return

        if (trackedDialogs.size == 1) {
            previousRequestedOrientation = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }

    private fun attachDetachListener(dialog: Dialog) {
        val decorView = dialog.window?.decorView ?: return
        if (!dialog.isShowing) {
            release(dialog)
            return
        }

        val listener = detachListeners[dialog] ?: object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                release(dialog)
            }
        }.also { detachListeners[dialog] = it }
        decorView.addOnAttachStateChangeListener(listener)
    }

    private fun release(dialog: Dialog) {
        if (!trackedDialogs.remove(dialog)) return

        val listener = detachListeners.remove(dialog)
        if (listener != null) {
            dialog.window?.decorView?.removeOnAttachStateChangeListener(listener)
        }

        if (trackedDialogs.isEmpty()) {
            restoreOrientation()
        }
    }

    private fun restoreOrientation() {
        if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
            activity.requestedOrientation = previousRequestedOrientation
        }
    }
}