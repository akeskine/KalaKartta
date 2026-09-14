package fi.anssi.kalakartta.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.WeatherError
import fi.anssi.kalakartta.data.WeatherUpdateAttempt
import fi.anssi.kalakartta.data.WeatherUpdateAttemptSelector
import fi.anssi.kalakartta.utils.WeatherService
import kotlinx.coroutines.*

private const val SIX_HOURS_MILLIS = 6 * 60 * 60 * 1000L

internal fun needsPressureHistoryUpdate(fishCatch: FishCatch, now: Long): Boolean {
    val caughtAt = fishCatch.caughtAt ?: return false
    if (now - caughtAt <= SIX_HOURS_MILLIS) return false

    val completionWindowStart = caughtAt + 5 * 60 * 60 * 1000L
    val completionWindowEnd = caughtAt + SIX_HOURS_MILLIS
    return fishCatch.pressureSamples.none { sample ->
        sample.time in completionWindowStart..completionWindowEnd && sample.pressure.isFinite()
    }
}

internal fun shouldShowWeatherUpdateStartButton(targetCount: Int): Boolean = targetCount > 0

class WeatherUpdateActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var weatherService: WeatherService
    private var updateJob: Job? = null

    private lateinit var pointsToUpdateText: TextView
    private lateinit var maxCountEditText: EditText
    private lateinit var startButton: TextView
    private lateinit var progressLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var cancelButton: TextView
    private lateinit var resultLayout: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var successCountText: TextView
    private lateinit var failureCountText: TextView
    private lateinit var errorLogButton: TextView
    private lateinit var okButton: TextView
    private lateinit var backButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_update)

        db = AppDatabase.getInstance(this)
        weatherService = WeatherService(this)

        initViews()
        loadStats()
    }

    private fun initViews() {
        pointsToUpdateText = findViewById(R.id.pointsToUpdateText)
        maxCountEditText = findViewById(R.id.maxCountEditText)
        startButton = findViewById(R.id.startButton)
        progressLayout = findViewById(R.id.progressLayout)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        statsText = findViewById(R.id.statsText)
        cancelButton = findViewById(R.id.cancelButton)
        resultLayout = findViewById(R.id.resultLayout)
        summaryText = findViewById(R.id.summaryText)
        successCountText = findViewById(R.id.successCountText)
        failureCountText = findViewById(R.id.failureCountText)
        errorLogButton = findViewById(R.id.errorLogButton)
        okButton = findViewById(R.id.okButton)
        backButton = findViewById(R.id.backButton)

        startButton.setOnClickListener { startUpdate() }
        cancelButton.setOnClickListener { stopUpdate() }
        errorLogButton.setOnClickListener {
            startActivity(Intent(this, WeatherErrorLogActivity::class.java))
        }
        okButton.setOnClickListener { finish() }
        backButton.setOnClickListener { finish() }
    }

    private fun hasMissingWeatherData(fishCatch: FishCatch): Boolean {
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

    private fun isUpdateTarget(fishCatch: FishCatch): Boolean {
        return (fishCatch.caughtAt ?: 0L) > 0L &&
                (hasMissingWeatherData(fishCatch) ||
                        fishCatch.pressureTrend == null ||
                        needsPressureHistoryUpdate(fishCatch, System.currentTimeMillis()))
    }

    private fun prioritizeTargets(
        allCatches: List<FishCatch>,
        attemptsByCatchId: Map<Long, WeatherUpdateAttempt>
    ): List<FishCatch> {
        val targets = allCatches.filter { isUpdateTarget(it) }
        val catchesById = targets.associateBy { it.id }
        return WeatherUpdateAttemptSelector
            .prioritizeCatchIds(targets.map { it.id }, attemptsByCatchId)
            .mapNotNull { catchesById[it] }
    }

    private fun formatUpdateStats(
        totalTargets: Int,
        totalCatches: Int,
        failedTargets: Int
    ): String {
        return "Päivitettäviä pisteitä: $totalTargets / $totalCatches\n" +
                "Viime yrityksellä epäonnistuneita $failedTargets kpl"
    }

    private fun recordAttempt(catchId: Long, succeeded: Boolean, errorMessage: String? = null) {
        db.weatherUpdateAttemptDao().upsert(
            WeatherUpdateAttempt(
                catchId = catchId,
                attemptedAt = System.currentTimeMillis(),
                succeeded = succeeded,
                errorMessage = errorMessage
            )
        )
    }

    private fun loadStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allCatches = db.fishCatchDao().getAll()
            val attemptsByCatchId = db.weatherUpdateAttemptDao().getAll().associateBy { it.catchId }
            val targets = prioritizeTargets(allCatches, attemptsByCatchId)
            val failedTargets = WeatherUpdateAttemptSelector.countFailedCatchIds(
                targets.map { it.id },
                attemptsByCatchId
            )
            
            withContext(Dispatchers.Main) {
                pointsToUpdateText.text = formatUpdateStats(targets.size, allCatches.size, failedTargets)
                startButton.visibility = if (shouldShowWeatherUpdateStartButton(targets.size)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
    }

    private fun startUpdate() {
        val maxCount = maxCountEditText.text.toString().toIntOrNull() ?: 100
        
        startButton.isEnabled = false
        maxCountEditText.isEnabled = false
        progressLayout.visibility = View.VISIBLE
        resultLayout.visibility = View.GONE
        errorLogButton.visibility = View.GONE
        okButton.visibility = View.GONE
        
        updateJob = lifecycleScope.launch(Dispatchers.IO) {
            var successful = 0
            var failed = 0
            var noChanges = 0
            try {
                val allCatches = db.fishCatchDao().getAll()
                val attemptsByCatchId = db.weatherUpdateAttemptDao().getAll().associateBy { it.catchId }
                val targetsAll = prioritizeTargets(allCatches, attemptsByCatchId)
                
                val targets = if (maxCount > 0) targetsAll.take(maxCount) else targetsAll
                
                if (targets.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Ei päivitettäviä pisteitä."
                        finishUpdate(0, 0, false)
                    }
                    return@launch
                }

                var attempted = 0
                val total = targets.size

                for (fishCatch in targets) {
                    if (!isActive) break
                    
                    attempted++
                    android.util.Log.d("KalaKartta", "Tutkitaan pistettä ID: ${fishCatch.id}, originalRef: ${fishCatch.originalRef}")
                    
                    try {
                        val needsWeatherData = hasMissingWeatherData(fishCatch)
                        val needsPressureData = fishCatch.pressureTrend == null ||
                                needsPressureHistoryUpdate(fishCatch, System.currentTimeMillis())
                        val caughtAt = fishCatch.caughtAt ?: 0L

                        val pressureResult = if (needsPressureData) {
                            weatherService.fetchPressureFromMultipleStationsSuspend(
                                fishCatch.latitude,
                                fishCatch.longitude,
                                caughtAt
                            )
                        } else {
                            null
                        }

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
                        } else {
                            null
                        }

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

                        val updatedCatch = if (hasWeatherData) {
                            val airTemp = data?.get("t2m") ?: fishCatch.airTemp
                            val cloudiness = data?.get("nn_ll01")?.toLong()
                                    ?: data?.get("n_man")?.toLong()
                                    ?: fishCatch.cloudiness
                            val rainHour = fetchedRainHour ?: fishCatch.rainHourMm
                            val windSpeed = data?.get("ws_10min") ?: fishCatch.windSpeed
                            val windDirection = data?.get("wd_10min")?.toLong() ?: fishCatch.windDirection
                            val isNowComplete = airTemp != null &&
                                    cloudiness != null &&
                                    rainHour != null &&
                                    windSpeed != null &&
                                    windDirection != null &&
                                    pressure != null
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
                        } else {
                            fishCatch
                        }

                        val pressureHistoryComplete = !needsPressureHistoryUpdate(
                            updatedCatch,
                            System.currentTimeMillis()
                        )
                        val updateCompleted = (!needsWeatherData || hasWeatherData) &&
                                (!needsPressureData || fetchedPressureTrend != null) &&
                                pressureHistoryComplete
                        if (hasWeatherData || pressureResult != null) {
                            if (updatedCatch != fishCatch) {
                                db.fishCatchDao().update(updatedCatch)
                            }

                            if (updateCompleted) {
                                recordAttempt(fishCatch.id, succeeded = true)
                                if (updatedCatch != fishCatch) {
                                    successful++
                                } else {
                                    noChanges++
                                }
                            } else {
                                failed++
                                val message = if (needsPressureData && fetchedPressureTrend == null) {
                                    "Painehistoriasta ei saatu laskettavaa trendiä."
                                } else if (!pressureHistoryComplete) {
                                    "Painehistoria ei ulotu saantihetken jälkeiseen ikkunaan."
                                } else {
                                    "Ei säädataa saatavilla."
                                }
                                recordAttempt(fishCatch.id, succeeded = false, errorMessage = message)
                                db.weatherErrorDao().insert(WeatherError(timestamp = System.currentTimeMillis(), message = message, catchId = fishCatch.id))
                            }
                        } else {
                            failed++
                            val message = "Ei säädataa saatavilla."
                            recordAttempt(fishCatch.id, succeeded = false, errorMessage = message)
                            db.weatherErrorDao().insert(WeatherError(timestamp = System.currentTimeMillis(), message = message, catchId = fishCatch.id))
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        failed++
                        val message = e.message ?: "Tuntematon virhe"
                        recordAttempt(fishCatch.id, succeeded = false, errorMessage = message)
                        db.weatherErrorDao().insert(WeatherError(timestamp = System.currentTimeMillis(), message = message, catchId = fishCatch.id))
                    }

                    withContext(Dispatchers.Main) {
                        val progressPercent = (attempted * 100) / total
                        progressBar.progress = progressPercent
                        statusText.text = "Päivitetään... $progressPercent %"
                        statsText.text = "Yritetty: $attempted / $total\nOnnistuneet: $successful\nEi muutoksia: $noChanges\nEpäonnistuneet: $failed"
                    }
                    
                }

                withContext(NonCancellable + Dispatchers.Main) {
                    finishUpdate(successful, failed, updateJob?.isCancelled == true, noChanges)
                }
            } catch (e: Exception) {
                val isCancelled = e is CancellationException || updateJob?.isCancelled == true
                withContext(NonCancellable + Dispatchers.Main) {
                    if (isCancelled) {
                        finishUpdate(successful, failed, true, noChanges)
                    } else {
                        statusText.text = "Virhe: ${e.message}"
                        startButton.isEnabled = true
                        maxCountEditText.isEnabled = true
                        okButton.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun stopUpdate() {
        statusText.text = "Keskeytetään..."
        cancelButton.isEnabled = false
        updateJob?.cancel()
        // lifecycleScope huolehtii että coroutine loppuu, mutta jos se on jo withContext(Main) niin se saattaa kestää
        // Siksi meidän pitää varmistaa että finishUpdate kutsutaan vaikka se peruuntuisi.
        // Itse asiassa meidän silmukassa on !isActive check, joten se menee finishUpdateen.
    }

    private fun finishUpdate(successful: Int, failed: Int, cancelled: Boolean, noChanges: Int = 0) {
        progressLayout.visibility = View.GONE
        resultLayout.visibility = View.VISIBLE
        okButton.visibility = View.VISIBLE
        
        summaryText.text = if (cancelled) "Päivitys keskeytetty." else "Päivitys valmis."
        successCountText.text = "Onnistuneesti päivitetty: $successful\nEi muutoksia: $noChanges"
        failureCountText.text = "Virheellisiä: $failed"
        
        if (failed > 0) {
            errorLogButton.visibility = View.VISIBLE
        }
        
        startButton.isEnabled = true
        maxCountEditText.isEnabled = true
        cancelButton.isEnabled = true
        
        loadStats()
        setResult(RESULT_OK)
    }
}
