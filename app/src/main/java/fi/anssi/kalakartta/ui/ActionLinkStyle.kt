package fi.anssi.kalakartta.ui

import android.content.Context
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import fi.anssi.kalakartta.R

fun actionLinkTextView(context: Context): TextView = TextView(context).apply {
    TextViewCompat.setTextAppearance(this, R.style.TextAppearance_KalaKartta_ActionLink)
}

fun menuLinkTextView(context: Context): TextView = TextView(context).apply {
    TextViewCompat.setTextAppearance(this, R.style.TextAppearance_KalaKartta_MenuLink)
}