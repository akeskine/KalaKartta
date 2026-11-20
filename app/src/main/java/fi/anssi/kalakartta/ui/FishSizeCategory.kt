package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies

enum class FishSizeCategory(val label: Int) {
    SMALL(R.string.fish_size_small),
    NORMAL(R.string.fish_size_normal),
    LARGE(R.string.fish_size_large),
    GIANT(R.string.fish_size_giant);

    companion object {
        val all: Set<FishSizeCategory> = entries.toSet()

        fun classify(fish: FishCatch, species: FishSpecies?): FishSizeCategory {
            if (species == null) return NORMAL
            val weight = fish.weight ?: 0L
            val length = fish.length ?: 0L

            fun meetsLargeThreshold(value: Long, threshold: Long): Boolean =
                threshold > 0 && value >= threshold

            return when {
                (species.giant_weight > 0 && weight >= species.giant_weight) ||
                    (species.giant_length > 0 && length >= species.giant_length) -> GIANT
                meetsLargeThreshold(weight, species.large_weight) ||
                    meetsLargeThreshold(length, species.large_length) -> LARGE
                species.small_weight > 0 && species.small_length > 0 &&
                    ((weight > 0 && weight < species.small_weight) ||
                        (length > 0 && length < species.small_length)) -> SMALL
                else -> NORMAL
            }
        }
    }
}
