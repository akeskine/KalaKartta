package fi.anssi.kalakartta.utils

import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import fi.anssi.kalakartta.R

fun AlertDialog.enlargeButtons() {
    listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
        .mapNotNull(::getButton)
        .forEach { TextViewCompat.setTextAppearance(it, R.style.TextAppearance_KalaKartta_ActionLink) }
}
