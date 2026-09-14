package fi.anssi.kalakartta.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration

fun Activity.lockToCurrentOrientation() {
    requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}