package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishSpecies
import java.io.File

/** Resolves the drawable, size, and optional file path for a catch marker. */
class FishIconResolver(
    private val filesDir: File,
    private val resolveSpecies: (String) -> FishSpecies?,
    private val resolveDrawableId: (String) -> Int,
    private val getIconScale: () -> Float
) {
    fun resolve(fish: FishCatch): Quadruple<Int, String?, Int, Int> {
        val species = resolveSpecies(fish.species)
        var iconName = species?.icon_default ?: ""
        var iconPath: String? = null
        var scaleFactor = 1.0

        val eventIcon = when (fish.eventType) {
            FishCatch.LOST_FISH -> "karkuutus"
            FishCatch.STRIKE_CERTAIN -> "tarppi_varma"
            FishCatch.STRIKE_UNCERTAIN -> "tarppi_epavarma"
            FishCatch.FISH_FOLLOW -> "seurio"
            else -> null
        }

        if (eventIcon != null) {
            iconName = eventIcon
            if (fish.eventType == FishCatch.FISH_FOLLOW) {
                scaleFactor *= 1.3
            }
        } else if (species != null) {
            when (FishSizeCategory.classify(fish, species)) {
                FishSizeCategory.GIANT -> {
                    if (species.icon_giant.isNotEmpty()) iconName = species.icon_giant else scaleFactor = 1.6
                }
                FishSizeCategory.LARGE -> {
                    if (species.icon_large.isNotEmpty()) iconName = species.icon_large else scaleFactor = 1.3
                }
                FishSizeCategory.SMALL -> {
                    if (species.icon_small.isNotEmpty()) iconName = species.icon_small else scaleFactor = 0.7
                }
                FishSizeCategory.NORMAL -> Unit
            }
        }

        var drawableId = resolveDrawableId(iconName)
        if (drawableId == 0 && iconName.isNotEmpty() &&
            (iconName.startsWith("/") || iconName.startsWith("custom_icon_"))) {
            iconPath = iconName
        }

        if (drawableId == 0 && iconPath == null) {
            val defaultSpecies = FishSpecies.getDefaultList().find { it.id == fish.species }
            if (defaultSpecies != null && defaultSpecies.icon_default.isNotEmpty()) {
                drawableId = resolveDrawableId(defaultSpecies.icon_default)
            }
            if (drawableId == 0) {
                drawableId = if (fish.species == "UNKNOWN") {
                    R.drawable.default_point
                } else {
                    R.drawable.muukala
                }
            }
        }

        if (iconPath != null) {
            val file = if (iconPath.startsWith("/")) File(iconPath) else File(filesDir, iconPath)
            if (!file.exists()) {
                iconPath = null
                drawableId = if (fish.species == "UNKNOWN") {
                    R.drawable.default_point
                } else {
                    R.drawable.muukala
                }
            }
        }

        val baseIconSize = if (drawableId == R.drawable.default_point) 24 else 40
        val visibleSize = if (drawableId == R.drawable.default_point) 8 else 40
        scaleFactor *= getIconScale().toDouble()

        if (fish.weight == null && fish.length == null && drawableId != R.drawable.default_point) {
            scaleFactor *= 0.85
        }
        if (drawableId == R.drawable.default_point) {
            scaleFactor = 0.8 * getIconScale().toDouble()
        }

        if (species != null && species.small_weight == 0L && species.small_length == 0L) {
            when (fish.species) {
                "SALMON", "TROUT", "RAINBOW" -> scaleFactor *= 1.3
                "PERCH", "IDE" -> scaleFactor *= 0.8
                "BURBOT" -> scaleFactor *= 1.2
            }
        }

        val finalIconSize = (baseIconSize * scaleFactor).toInt()
        val finalVisibleSize = (visibleSize * scaleFactor).toInt()
        val adjustedFinalIconSize = if (drawableId != R.drawable.default_point) {
            finalIconSize.coerceAtLeast(16)
        } else {
            finalIconSize
        }

        return Quadruple(drawableId, iconPath, adjustedFinalIconSize, finalVisibleSize)
    }
}
