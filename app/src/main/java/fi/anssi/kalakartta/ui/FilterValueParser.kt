package fi.anssi.kalakartta.ui

/** FilterActivityn tekstikenttien Androidista riippumaton numerojäsennys. */
object FilterValueParser {

    fun floatOrNull(value: CharSequence?): Float? =
        value.normalizedText()?.toFloatOrNull()?.takeIf { it.isFinite() }

    fun doubleOrNull(value: CharSequence?): Double? =
        value.normalizedText()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun longOrNull(value: CharSequence?): Long? =
        value.normalizedText()?.toLongOrNull()

    private fun CharSequence?.normalizedText(): String? =
        this?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
