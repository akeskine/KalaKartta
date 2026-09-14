package fi.anssi.kalakartta.ui

/**
 * Asetusdialogien tekstikenttien yhteinen, Androidista riippumaton validointi.
 *
 * Virheellinen syöte palautetaan aina kutsujan antamaan oletusarvoon. Näin
 * asetusten tallentaminen ei riipu EditTextin tai inputType-määrittelyn
 * toiminnasta.
 */
object SettingsValueValidator {

    fun positiveIntOrDefault(value: CharSequence?, defaultValue: Int): Int =
        value?.toString()?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: defaultValue

    fun nonNegativeIntOrDefault(value: CharSequence?, defaultValue: Int): Int =
        value?.toString()?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: defaultValue

    fun nonNegativeFloatOrDefault(value: CharSequence?, defaultValue: Float): Float =
        finiteFloatOrNull(value)?.takeIf { it >= 0f } ?: defaultValue

    fun positiveFloatOrDefault(value: CharSequence?, defaultValue: Float): Float =
        finiteFloatOrNull(value)?.takeIf { it > 0f } ?: defaultValue

    fun latitudeOrDefault(value: CharSequence?, defaultValue: Float): Float =
        finiteFloatOrNull(value)?.takeIf { it in 0f..180f } ?: defaultValue

    private fun finiteFloatOrNull(value: CharSequence?): Float? =
        value?.toString()
            ?.trim()
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() }
}
