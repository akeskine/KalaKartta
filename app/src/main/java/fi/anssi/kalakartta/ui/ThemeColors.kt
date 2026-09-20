package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/** Resolves a color from the currently active light/dark app theme. */
@ColorInt
fun Context.resolveThemeColor(
    @AttrRes attribute: Int,
    @ColorInt fallback: Int = Color.TRANSPARENT
): Int {
    val typedValue = TypedValue()
    if (!theme.resolveAttribute(attribute, typedValue, true)) return fallback
    return if (typedValue.resourceId != 0) {
        obtainStyledAttributes(intArrayOf(attribute)).use { attributes ->
            attributes.getColor(0, fallback)
        }
    } else {
        typedValue.data
    }
}
