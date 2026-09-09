package fi.anssi.kalakartta.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

data class PressureGraphRange(
    val min: Float,
    val max: Float
)

object PressureGraphScale {
    const val TICK_STEP_HPA = 10f
    const val MIN_RANGE_HPA = 20f

    fun rangeFor(values: Collection<Double>): PressureGraphRange {
        val finiteValues = values.filter { it.isFinite() }
        if (finiteValues.isEmpty()) {
            return PressureGraphRange(0f, MIN_RANGE_HPA)
        }

        val dataMin = finiteValues.minOrNull()!!
        val dataMax = finiteValues.maxOrNull()!!
        var min: Float
        var max: Float

        if (dataMax - dataMin <= MIN_RANGE_HPA) {
            val center = (dataMin + dataMax) / 2.0
            val centerTick = round(center / TICK_STEP_HPA) * TICK_STEP_HPA
            min = centerTick.toFloat() - MIN_RANGE_HPA / 2f
            max = min + MIN_RANGE_HPA
        } else {
            min = floor(dataMin / TICK_STEP_HPA).toFloat() * TICK_STEP_HPA
            max = ceil(dataMax / TICK_STEP_HPA).toFloat() * TICK_STEP_HPA
        }

        while (min > dataMin) min -= TICK_STEP_HPA
        while (max < dataMax) max += TICK_STEP_HPA

        return PressureGraphRange(min, max)
    }
}
