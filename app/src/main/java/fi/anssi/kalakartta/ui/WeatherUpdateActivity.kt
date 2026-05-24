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

        startButton.setOnClickListener { startUpdate() }
        cancelButton.setOnClickListener { stopUpdate() }
        errorLogButton.setOnClickListener {
            startActivity(Intent(this, WeatherErrorLogActivity::class.java))
        }
        okButton.setOnClickListener { finish() }
    }

    private fun loadStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allCatches = db.fishCatchDao().getAll()
            val targets = allCatches.filter {
                it.weatherSource != "MANUAL" && (it.weatherSource == "" || it.weatherStation == "" || it.weatherTime == 0L || it.pressure == 0.0)
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
            try {
                val allCatches = db.fishCatchDao().getAll()
                val targetsAll = allCatches.filter {
                    it.weatherSource != "MANUAL" && (it.weatherSource == "" || it.weatherStation == "" || it.weatherTime == 0L || it.pressure == 0.0)
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
                        var nearest: fi.anssi.kalakartta.utils.WeatherStation? = null
                        var minDistance = Double.MAX_VALUE
                        for (station in stations) {
                            if (station.startTime != null && fishCatch.caughtAt < station.startTime) continue
                            if (station.endTime != null && fishCatch.caughtAt > station.endTime) continue

                            val distance = weatherService.calculateDistance(fishCatch.latitude, fishCatch.longitude, station.latitude, station.longitude)
                            if (distance > 300.0) continue

                            if (distance < minDistance) {
                                minDistance = distance
                                nearest = station
                            }
                        }

                        if (nearest != null) {
                            val result = weatherService.fetchWeatherDataSync(nearest.fmisid, fishCatch.caughtAt)
                            if (result.first != null && result.first!!.isNotEmpty()) {
                                val data = result.first!!
                                val updatedCatch = fishCatch.copy(
                                    airTemp = data["t2m"] ?: fishCatch.airTemp,
                                    cloudiness = data["n_man"]?.toLong() ?: data["nn_4h"]?.toLong() ?: fishCatch.cloudiness,
                                    rain = data["r_1h"]?.toLong() ?: fishCatch.rain,
                                    windSpeed = data["ws_10min"] ?: fishCatch.windSpeed,
                                    windDirection = data["wd_10min"]?.toLong() ?: fishCatch.windDirection,
                                    pressure = data["p_sea"] ?: data["p_msl"] ?: fishCatch.pressure,
                                    weatherSource = "FMI",
                                    weatherTime = result.second ?: fishCatch.weatherTime,
                                    weatherStation = "${nearest.fmisid}:${nearest.name}"
                                )
                                db.fishCatchDao().update(updatedCatch)
                                successful++
                            } else {
                                failed++
                                val errorMsg = result.third ?: "Ei säädataa saatavilla."
                                db.weatherErrorDao().insert(WeatherError(timestamp = System.currentTimeMillis(), message = errorMsg, catchId = fishCatch.id))
                            }
                        } else {
                            failed++
                            val errorMsg = "Lähintä sääasemaa ei löytynyt (300km säde)."
                            db.weatherErrorDao().insert(WeatherError(timestamp = System.currentTimeMillis(), message = errorMsg, catchId = fishCatch.id))
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
                        statsText.text = "Yritetty: $attempted / $total\nOnnistuneet: $successful\nEpäonnistuneet: $failed"
                    }
                    
                    delay(500)
                }

                withContext(NonCancellable + Dispatchers.Main) {
                    finishUpdate(successful, failed, updateJob?.isCancelled == true)
                }
            } catch (e: Exception) {
                val isCancelled = e is CancellationException || updateJob?.isCancelled == true
                withContext(NonCancellable + Dispatchers.Main) {
                    if (isCancelled) {
                        finishUpdate(successful, failed, true)
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

    private fun finishUpdate(successful: Int, failed: Int, cancelled: Boolean) {
        progressLayout.visibility = View.GONE
        resultLayout.visibility = View.VISIBLE
        okButton.visibility = View.VISIBLE
        
        summaryText.text = if (cancelled) "Päivitys keskeytetty." else "Päivitys valmis."
        successCountText.text = "Onnistuneesti päivitetty: $successful"
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
