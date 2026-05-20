package fi.anssi.kalakartta.utils

import androidx.appcompat.app.AlertDialog

fun AlertDialog.enlargeButtons() {
    getButton(AlertDialog.BUTTON_POSITIVE)?.textSize = 20f
    getButton(AlertDialog.BUTTON_NEGATIVE)?.textSize = 20f
    getButton(AlertDialog.BUTTON_NEUTRAL)?.textSize = 20f
}
