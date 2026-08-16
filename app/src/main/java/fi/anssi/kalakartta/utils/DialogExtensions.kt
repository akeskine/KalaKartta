package fi.anssi.kalakartta.utils

import androidx.appcompat.app.AlertDialog

fun AlertDialog.enlargeButtons() {
    getButton(AlertDialog.BUTTON_POSITIVE)?.textSize = 18f
    getButton(AlertDialog.BUTTON_NEGATIVE)?.textSize = 18f
    getButton(AlertDialog.BUTTON_NEUTRAL)?.textSize = 18f
}
