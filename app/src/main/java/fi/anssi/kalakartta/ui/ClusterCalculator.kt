package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.FishCatch
import kotlin.math.pow
import kotlin.math.sqrt

/** Groups catches by species/event type and proximity for map rendering. */
class ClusterCalculator {
    fun calculate(catches: List<FishCatch>, zoom: Double): Map<Any, List<List<FishCatch>>> {
        val result = mutableMapOf<Any, MutableList<MutableList<FishCatch>>>()
        val grouped = catches.groupBy { fish ->
            when {
                fish.species == "UNKNOWN" && isCaughtFish(fish) -> {
                    "UNKNOWN_INDIVIDUAL_${fish.id}"
                }
                isCaughtFish(fish) -> fish.species
                else -> fish.species to fish.eventType
            }
        }

        val threshold = when {
            zoom < 10 -> 0.5
            zoom < 12 -> 0.1
            zoom < 13 -> 0.02
            zoom < 14 -> 0.01
            zoom < 15 -> 0.005
            else -> 0.002
        }
        val useGrid = catches.size > 15000

        grouped.forEach { (groupKey, groupCatches) ->
            val clusters = mutableListOf<MutableList<FishCatch>>()
            if (useGrid) {
                val grid = mutableMapOf<Pair<Int, Int>, MutableList<FishCatch>>()
                groupCatches.forEach { fish ->
                    val gridX = (fish.longitude / threshold).toInt()
                    val gridY = (fish.latitude / threshold).toInt()
                    grid.getOrPut(gridX to gridY) { mutableListOf() }.add(fish)
                }
                clusters.addAll(grid.values)
            } else {
                groupCatches.forEach { fish ->
                    val matchingCluster = clusters.firstOrNull { cluster ->
                        val first = cluster.first()
                        val distance = sqrt(
                            (fish.latitude - first.latitude).pow(2.0) +
                                (fish.longitude - first.longitude).pow(2.0)
                        )
                        distance < threshold
                    }
                    if (matchingCluster == null) {
                        clusters.add(mutableListOf(fish))
                    } else {
                        matchingCluster.add(fish)
                    }
                }
            }
            result[groupKey] = clusters
        }

        return result
    }

    private fun isCaughtFish(fish: FishCatch): Boolean =
        fish.eventType == null || fish.eventType == FishCatch.CAUGHT_FISH
}
