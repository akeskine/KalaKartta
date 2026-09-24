package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.utils.SeaLevelStationResult
import fi.anssi.kalakartta.data.WeatherError
import fi.anssi.kalakartta.data.WeatherUpdateAttempt
import fi.anssi.kalakartta.data.WeatherUpdateAttemptSelector
import fi.anssi.kalakartta.utils.WeatherService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SIX_HOURS_MILLIS = 6 * 60 * 60 * 1000L
internal const val MAX_AUTOMATIC_MISSING_WEATHER_UPDATE_COUNT = 500

internal fun needsPressureHistoryUpdate(fishCatch: FishCatch, now: Long): Boolean {
    val caughtAt = fishCatch.caughtAt ?: return false
    if (now - caughtAt <= SIX_HOURS_MILLIS) return false

    val completionWindowStart = caughtAt + 5 * 60 * 60 * 1000L
    val completionWindowEnd = caughtAt + SIX_HOURS_MILLIS
    return fishCatch.pressureSamples.none { sample ->
        sample.time in completionWindowStart..completionWindowEnd && sample.pressure.isFinite()
    }
}

internal fun needsSeaLevelHistoryUpdate(fishCatch: FishCatch, now: Long): Boolean {
    val caughtAt = fishCatch.caughtAt ?: return false
    if (fishCatch.seaLevelDataCompleteTime != null || now - caughtAt <= SIX_HOURS_MILLIS) return false

    val completionWindowStart = caughtAt + 5 * 60 * 60 * 1000L
    val completionWindowEnd = caughtAt + SIX_HOURS_MILLIS
    return fishCatch.seaLevelSamples.none { sample ->
        sample.time in completionWindowStart..completionWindowEnd
    }
}

internal fun hasMissingSeaLevelData(fishCatch: FishCatch): Boolean {
    return fishCatch.seaLevelDataCompleteTime == null &&
            (fishCatch.seaLevel == null || fishCatch.seaLevelSamples.isEmpty())
}

internal fun FishCatch.withSeaLevelResult(
    result: SeaLevelStationResult,
    now: Long = System.currentTimeMillis(),
    caughtAt: Long? = this.caughtAt
): FishCatch {
    if (!result.isSea) {
        return copy(
            seaLevel = null,
            seaLevelDataCompleteTime = now,
            seaLevelTrend = null,
            seaLevelTurningTrend = null,
            seaLevelSamples = emptyList()
        )
    }

    val samples = result.seaLevelSamples.ifEmpty { seaLevelSamples }
    val updated = copy(
        seaLevel = result.seaLevel ?: seaLevel,
        seaLevelDataCompleteTime = if (caughtAt != null && now - caughtAt > SIX_HOURS_MILLIS) {
            now
        } else {
            seaLevelDataCompleteTime
        },
        seaLevelSamples = samples
    )
    return if (result.seaLevelSamples.isNotEmpty()) {
        updated.copy(
            seaLevelTrend = updated.calculateSeaLevelTrend() ?: seaLevelTrend,
            seaLevelTurningTrend = updated.calculateSeaLevelTurningTrend() ?: seaLevelTurningTrend
        )
    } else {
        updated
    }
}

internal fun shouldRunAutomaticWeatherUpdate(
    enabled: Boolean,
    now: Long,
    lastUpdateAt: Long,
    intervalHours: Int
): Boolean {
    if (!enabled) return false
    val intervalMillis = intervalHours.coerceAtLeast(1).toLong() * 60 * 60 * 1000L
    return now - lastUpdateAt > intervalMillis
}

internal fun hasMissingWeatherData(fishCatch: FishCatch): Boolean {
    return fishCatch.weatherDataCompleteTime == null && (
            fishCatch.airTemp == null ||
                    fishCatch.cloudiness == null ||
                    fishCatch.rainHourMm == null ||
                    fishCatch.windSpeed == null ||
                    fishCatch.windDirection == null ||
                    fishCatch.pressure == null ||
                    fishCatch.weatherSource == "" ||
                    fishCatch.weatherStation == "" ||
                    fishCatch.weatherTime == null ||
                    fishCatch.weatherTime == 0L
            )
}

internal fun isMissingWeatherUpdateTarget(fishCatch: FishCatch, now: Long): Boolean {
    return (fishCatch.caughtAt ?: 0L) > 0L &&
            (hasMissingWeatherData(fishCatch) ||
                    fishCatch.pressureTrend == null ||
                    needsPressureHistoryUpdate(fishCatch, now) ||
                    hasMissingSeaLevelData(fishCatch) ||
                    needsSeaLevelHistoryUpdate(fishCatch, now))
}

internal fun prioritizeMissingWeatherTargets(
    allCatches: List<FishCatch>,
    attemptsByCatchId: Map<Long, WeatherUpdateAttempt>,
    now: Long = System.currentTimeMillis()
): List<FishCatch> {
    val targets = allCatches.filter { isMissingWeatherUpdateTarget(it, now) }
    val catchesById = targets.associateBy { it.id }
    return WeatherUpdateAttemptSelector
        .prioritizeCatchIds(targets.map { it.id }, attemptsByCatchId)
        .mapNotNull { catchesById[it] }
}

internal data class MissingWeatherUpdateProgress(
    val attempted: Int,
    val total: Int,
    val successful: Int,
    val noChanges: Int,
    val failed: Int
)

internal data class MissingWeatherUpdateResult(
    val successful: Int,
    val failed: Int,
    val noChanges: Int,
    val attempted: Int = 0,
    val cancelled: Boolean = false
)

/** The single implementation used by both the manual and automatic update flows. */
internal class MissingWeatherDataUpdater(
    private val db: AppDatabase,
    private val weatherService: WeatherService
) {
    suspend fun update(
        maxCount: Int,
        onProgress: suspend (MissingWeatherUpdateProgress) -> Unit = {}
    ): MissingWeatherUpdateResult = updateMutex.withLock {
        updateInternal(maxCount, onProgress)
    }

    private suspend fun updateInternal(
        maxCount: Int,
        onProgress: suspend (MissingWeatherUpdateProgress) -> Unit
    ): MissingWeatherUpdateResult {
        var successful = 0
        var failed = 0
        var noChanges = 0
        var attempted = 0

        try {
            val allCatches = db.fishCatchDao().getAll()
            val attemptsByCatchId = db.weatherUpdateAttemptDao().getAll().associateBy { it.catchId }
            val targetsAll = prioritizeMissingWeatherTargets(allCatches, attemptsByCatchId)
            val targets = if (maxCount > 0) targetsAll.take(maxCount) else targetsAll

            for (fishCatch in targets) {
                currentCoroutineContext().ensureActive()
                attempted++

                try {
                    val needsWeatherData = hasMissingWeatherData(fishCatch)
                    val needsPressureData = fishCatch.pressureTrend == null ||
                            needsPressureHistoryUpdate(fishCatch, System.currentTimeMillis())
                    val needsSeaLevelData = hasMissingSeaLevelData(fishCatch) ||
                            needsSeaLevelHistoryUpdate(fishCatch, System.currentTimeMillis())
                    val caughtAt = fishCatch.caughtAt ?: 0L

                    val pressureResult = if (needsPressureData) {
                        weatherService.fetchPressureFromMultipleStationsSuspend(
                            fishCatch.latitude,
                            fishCatch.longitude,
                            caughtAt
                        )
                    } else null

                    val seaLevelResult = if (needsSeaLevelData) {
                        weatherService.fetchSeaLevelFromMultipleStationsSuspend(
                            fishCatch.latitude,
                            fishCatch.longitude,
                            caughtAt
                        )
                    } else null

                    val fetchedPressureTrend = pressureResult?.let { result ->
                        fishCatch.copy(
                            pressure = result.pressure,
                            pressureSamples = result.pressureSamples
                        ).calculatePressureTrend()
                    }
                    val fetchedPressureTurningTrend = pressureResult?.let { result ->
                        fishCatch.copy(
                            pressure = result.pressure,
                            pressureSamples = result.pressureSamples
                        ).calculatePressureTurningTrend()
                    }

                    val existingData = mutableMapOf<String, Double>()
                    fishCatch.airTemp?.let { existingData["t2m"] = it }
                    fishCatch.cloudiness?.let { existingData["nn_ll01"] = it.toDouble() }
                    fishCatch.rainHourMm?.let { existingData["r_1h"] = it }
                    fishCatch.windSpeed?.let { existingData["ws_10min"] = it }
                    fishCatch.windDirection?.let { existingData["wd_10min"] = it.toDouble() }
                    fishCatch.pressure?.let { existingData["p_sea"] = it }

                    val weatherResult = if (needsWeatherData) {
                        weatherService.fetchWeatherFromMultipleStationsSuspend(
                            fishCatch.latitude,
                            fishCatch.longitude,
                            caughtAt,
                            existingData.ifEmpty { null },
                            "ID: ${fishCatch.id}, Ref: ${fishCatch.originalRef}"
                        )
                    } else null

                    val data = weatherResult?.first
                    val hasWeatherData = data?.isNotEmpty() == true
                    val fetchedRainHour = data?.get("r_1h") ?: data?.get("ri_10min")
                    val pressure = pressureResult?.pressure
                            ?: data?.get("p_sea")
                            ?: data?.get("p_msl")
                            ?: fishCatch.pressure
                    val pressureSamples = pressureResult?.pressureSamples ?: fishCatch.pressureSamples
                    val pressureTrend = fetchedPressureTrend ?: fishCatch.pressureTrend
                    val pressureTurningTrend = fetchedPressureTurningTrend ?: fishCatch.pressureTurningTrend

                    val weatherUpdatedCatch = if (hasWeatherData) {
                        val airTemp = data?.get("t2m") ?: fishCatch.airTemp
                        val cloudiness = data?.get("nn_ll01")?.toLong()
                                ?: data?.get("n_man")?.toLong()
                                ?: fishCatch.cloudiness
                        val rainHour = fetchedRainHour ?: fishCatch.rainHourMm
                        val windSpeed = data?.get("ws_10min") ?: fishCatch.windSpeed
                        val windDirection = data?.get("wd_10min")?.toLong() ?: fishCatch.windDirection
                        val isNowComplete = airTemp != null && cloudiness != null && rainHour != null &&
                                windSpeed != null && windDirection != null && pressure != null
                        val station = weatherResult?.third?.takeIf {
                            it.isNotBlank() && it != "EI_MUUTOKSIA"
                        } ?: fishCatch.weatherStation

                        fishCatch.copy(
                            airTemp = airTemp,
                            cloudiness = cloudiness,
                            rainHourMm = rainHour,
                            windSpeed = windSpeed,
                            windDirection = windDirection,
                            pressure = pressure,
                            weatherSource = "FMI",
                            weatherTime = weatherResult?.second ?: fishCatch.weatherTime,
                            weatherStation = station,
                            weatherDataCompleteTime = if (isNowComplete) null else System.currentTimeMillis(),
                            pressureTurningTrend = pressureTurningTrend,
                            pressureSamples = pressureSamples,
                            pressureTrend = pressureTrend
                        )
                    } else if (pressureResult != null) {
                        fishCatch.copy(
                            pressure = pressureResult.pressure,
                            pressureTurningTrend = pressureTurningTrend,
                            pressureSamples = pressureResult.pressureSamples,
                            pressureTrend = pressureTrend
                        )
                    } else fishCatch

                    val updatedCatch = seaLevelResult?.let { result ->
                        weatherUpdatedCatch.withSeaLevelResult(result, caughtAt = caughtAt)
                    } ?: weatherUpdatedCatch

                    val now = System.currentTimeMillis()
                    val pressureHistoryComplete = !needsPressureHistoryUpdate(
                        updatedCatch,
                        now
                    )
                    val seaLevelHistoryComplete = !needsSeaLevelHistoryUpdate(updatedCatch, now)
                    val seaLevelUpdateComplete = !needsSeaLevelData || seaLevelResult != null
                    val updateCompleted = (!needsWeatherData || hasWeatherData) &&
                            (!needsPressureData || fetchedPressureTrend != null) &&
                            pressureHistoryComplete && seaLevelUpdateComplete && seaLevelHistoryComplete

                    if (hasWeatherData || pressureResult != null || seaLevelResult != null) {
                        if (updatedCatch != fishCatch) db.fishCatchDao().update(updatedCatch)
                        if (updateCompleted) {
                            recordAttempt(fishCatch.id, true)
                            if (updatedCatch != fishCatch) successful++ else noChanges++
                        } else {
                            val message = if (needsSeaLevelData && seaLevelResult == null) {
                                "Meriveden korkeustietojen haku epäonnistui."
                            } else if (needsPressureData && fetchedPressureTrend == null) {
                                "Painehistoriasta ei saatu laskettavaa trendiä."
                            } else if (!pressureHistoryComplete) {
                                "Painehistoria ei ulotu saantihetken jälkeiseen ikkunaan."
                            } else if (!seaLevelHistoryComplete) {
                                "Meriveden korkeushistoria ei ulotu saantihetken jälkeiseen ikkunaan."
                            } else "Ei säädataa saatavilla."
                            failed++
                            recordAttempt(fishCatch.id, false, message)
                            db.weatherErrorDao().insert(
                                WeatherError(
                                    timestamp = System.currentTimeMillis(),
                                    message = message,
                                    catchId = fishCatch.id
                                )
                            )
                        }
                    } else {
                        failed++
                        val message = "Ei säädataa saatavilla."
                        recordAttempt(fishCatch.id, false, message)
                        db.weatherErrorDao().insert(
                            WeatherError(
                                timestamp = System.currentTimeMillis(),
                                message = message,
                                catchId = fishCatch.id
                            )
                        )
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    failed++
                    val message = e.message ?: "Tuntematon virhe"
                    recordAttempt(fishCatch.id, false, message)
                    db.weatherErrorDao().insert(
                        WeatherError(
                            timestamp = System.currentTimeMillis(),
                            message = message,
                            catchId = fishCatch.id
                        )
                    )
                }

                onProgress(MissingWeatherUpdateProgress(attempted, targets.size, successful, noChanges, failed))
            }
        } catch (e: CancellationException) {
            return MissingWeatherUpdateResult(successful, failed, noChanges, attempted, cancelled = true)
        }

        return MissingWeatherUpdateResult(successful, failed, noChanges, attempted)
    }

    private fun recordAttempt(catchId: Long, succeeded: Boolean, errorMessage: String? = null) {
        db.weatherUpdateAttemptDao().upsert(
            WeatherUpdateAttempt(catchId, System.currentTimeMillis(), succeeded, errorMessage)
        )
    }

    private companion object {
        val updateMutex = Mutex()
    }
}
