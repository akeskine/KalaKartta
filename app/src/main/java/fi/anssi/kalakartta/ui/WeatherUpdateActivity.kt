package fi.anssi.kalakartta.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.WeatherError
import fi.anssi.kalakartta.utils.WeatherService
import kotlinx.coroutines.*

class WeatherUpdateActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var weatherService: WeatherService
    private var updateJob: Job? = null

    private lateinit var pointsToUpdateText: TextView
    private lateinit var maxCountEditText: EditText
    private lateinit var startButton: Button
    private lateinit var progressLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var cancelButton: Button
    private lateinit var resultLayout: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var successCountText: TextView
    private lateinit var failureCountText: TextView
    private lateinit var errorLogButton: Button
    private lateinit var okButton: Button
    private lateinit var backButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
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

    private fun loadStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allCatches = db.fishCatchDao().getAll()
            val targets = allCatches.filter {
                it.caughtAt > 0L && (
                    it.airTemp == null || 
                    it.cloudiness == null || 
                    it.rainHourMm == null || 
                    it.windSpeed == null || 
                    it.windDirection == null || 
                    it.pressure == null ||
                    it.weatherSource == "" || 
                    it.weatherStation == "" || 
                    it.weatherTime == null || 
                    it.weatherTime == 0L
                )
            }
            
            withContext(Dispatchers.Main) {
                pointsToUpdateText.text = "Päivitettäviä pisteitä: ${targets.size} / ${allCatches.size}"
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
                val targetsAll = allCatches.filter {
                    it.caughtAt > 0L && (
                        it.airTemp == null || 
                        it.cloudiness == null || 
                        it.rainHourMm == null || 
                        it.windSpeed == null || 
                        it.windDirection == null || 
                        it.pressure == null ||
                        it.weatherSource == "" || 
                        it.weatherStation == "" || 
                        it.weatherTime == null || 
                        it.weatherTime == 0L
                    )
                }
                
                val targets = if (maxCount > 0) targetsAll.take(maxCount) else targetsAll
                
                if (targets.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Ei päivitettäviä pisteitä."
                        finishUpdate(0, 0, false)
                    }
                    return@launch
                }

                val stations = weatherService.fetchAllStationsSuspend()
                if (stations == null) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Sääasemia ei voitu ladata."
                        finishUpdate(0, 0, false)
                    }
                    return@launch
                }

                var attempted = 0
                val total = targets.size

                for (fishCatch in targets) {
                    if (!isActive) break
                    
                    attempted++
                    
                    try {
                        val existingData = mutableMapOf<String, Double>()
                        fishCatch.airTemp?.let { existingData["t2m"] = it }
                        fishCatch.cloudiness?.let { existingData["nn_ll01"] = it.toDouble() }
                        fishCatch.rainHourMm?.let { existingData["r_1h"] = it }
                        fishCatch.windSpeed?.let { existingData["ws_10min"] = it }
                        fishCatch.windDirection?.let { existingData["wd_10min"] = it.toDouble() }
                        fishCatch.pressure?.let { existingData["p_sea"] = it }

                        val result = weatherService.fetchWeatherFromMultipleStationsSuspend(
                            fishCatch.latitude, 
                            fishCatch.longitude, 
                            fishCatch.caughtAt,
                            existingData.ifEmpty { null }
                        )
                        
                        // Diagnostiikka: Logitetaan jos ei muutoksia
                        if (result.third.contains("Ei uutta dataa", ignoreCase = true)) {
                            android.util.Log.d("WeatherUpdate", "Catch ID ${fishCatch.id}: Ei muutoksia 300km säteellä.")
                        }

                        if (result.first != null && result.first!!.isNotEmpty()) {
                            val data = result.first!!
                            val rainHour = data["r_1h"] ?: data["ri_10min"]
                            
                            // Tarkistetaan onko tullut oikeasti jotain uutta
                            val isActuallyChanged = fishCatch.airTemp != data["t2m"] ||
                                    fishCatch.cloudiness != (data["nn_ll01"]?.toLong() ?: data["n_man"]?.toLong()) ||
                                    fishCatch.rainHourMm != rainHour ||
                                    fishCatch.windSpeed != data["ws_10min"] ||
                                    fishCatch.windDirection != data["wd_10min"]?.toLong() ||
                                    fishCatch.pressure != (data["p_sea"] ?: data["p_msl"]) ||
                                    fishCatch.weatherStation != result.third
                            
                            if (isActuallyChanged) {
                                val updatedCatch = fishCatch.copy(
                                    airTemp = data["t2m"],
                                    cloudiness = data["nn_ll01"]?.toLong() ?: data["n_man"]?.toLong(),
                                    rainHourMm = rainHour,
                                    windSpeed = data["ws_10min"],
                                    windDirection = data["wd_10min"]?.toLong(),
                                    pressure = data["p_sea"] ?: data["p_msl"],
                                    weatherSource = "FMI",
                                    weatherTime = result.second ?: fishCatch.weatherTime,
                                    weatherStation = result.third
                                )
                                db.fishCatchDao().update(updatedCatch)
                                successful++
                            } else {
                                noChanges++
                            }
                        } else {
                            failed++
                            db.weatherErrorDao().insert(WeatherError(timestamp = System.currentTimeMillis(), message = "Ei säädataa saatavilla.", catchId = fishCatch.id))
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        failed++
                        db.weatherErrorDao().insert(WeatherError(timestamp = System.currentTimeMillis(), message = e.message ?: "Tuntematon virhe", catchId = fishCatch.id))
                    }

                    withContext(Dispatchers.Main) {
                        val progressPercent = (attempted * 100) / total
                        progressBar.progress = progressPercent
                        statusText.text = "Päivitetään... $progressPercent %"
                        statsText.text = "Yritetty: $attempted / $total\nOnnistuneet: $successful\nEi muutoksia: $noChanges\nEpäonnistuneet: $failed"
                    }
                    
                    delay(500)
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
