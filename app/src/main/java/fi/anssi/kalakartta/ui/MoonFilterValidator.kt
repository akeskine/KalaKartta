package fi.anssi.kalakartta.ui

/** Kuun vaihe- ja korkeussuodattimien Androidista riippumaton validointi. */
object MoonFilterValidator {

    enum class Error {
        RANGE,
        BOTH_REQUIRED,
        ORDER
    }

    data class PairValidation(val error: Error? = null)

    data class Result(
        val moonPhase: PairValidation,
        val moonAltitude: PairValidation
    ) {
        val isValid: Boolean
            get() = moonPhase.error == null && moonAltitude.error == null
    }

    fun validate(
        moonPhaseMin: String?,
        moonPhaseMax: String?,
        moonAltitudeMin: String?,
        moonAltitudeMax: String?
    ): Result {
        return Result(
            moonPhase = validatePair(moonPhaseMin, moonPhaseMax, 0f, 1f),
            moonAltitude = validatePair(
                moonAltitudeMin,
                moonAltitudeMax,
                -90f,
                90f,
                requireAscending = true
            )
        )
    }

    private fun validatePair(
        minText: String?,
        maxText: String?,
        rangeMin: Float,
        rangeMax: Float,
        requireAscending: Boolean = false
    ): PairValidation {
        val min = parseBound(minText, rangeMin, rangeMax)
        val max = parseBound(maxText, rangeMin, rangeMax)

        if ((min.value == null) != (max.value == null)) {
            return PairValidation(Error.BOTH_REQUIRED)
        }
        if (min.invalid || max.invalid) {
            return PairValidation(Error.RANGE)
        }
        if (requireAscending && min.value != null && max.value != null && min.value >= max.value) {
            return PairValidation(Error.ORDER)
        }
        return PairValidation()
    }

    private data class ParsedBound(val value: Float?, val invalid: Boolean)

    private fun parseBound(text: String?, rangeMin: Float, rangeMax: Float): ParsedBound {
        if (text.isNullOrBlank()) return ParsedBound(value = null, invalid = false)

        val value = text.trim()
            .toFloatOrNull()
        return ParsedBound(
            value = value,
            invalid = value == null || !value.isFinite() || value !in rangeMin..rangeMax
        )
    }
}
