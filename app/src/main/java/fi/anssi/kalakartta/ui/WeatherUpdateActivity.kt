package fi.anssi.kalakartta.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.WeatherUpdateAttemptSelector
import fi.anssi.kalakartta.utils.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun shouldShowWeatherUpdateStartButton(targetCount: Int): Boolean = targetCount > 0

class WeatherUpdateActivity : AppCompatActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private lateinit var db: AppDatabase
    private lateinit var weatherService: WeatherService
    private lateinit var settingsStore: SettingsStore
    private lateinit var updater: MissingWeatherDataUpdater
    private var updateJob: Job? = null

    private lateinit var pointsToUpdateText: TextView
    private lateinit var automaticUpdateCheckBox: CheckBox
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
        settingsStore = SettingsStore(getSharedPreferences("settings", MODE_PRIVATE))
        updater = MissingWeatherDataUpdater(db, weatherService)

        initViews()
        loadStats()
    }

    private fun initViews() {
        pointsToUpdateText = findViewById(R.id.pointsToUpdateText)
        automaticUpdateCheckBox = findViewById(R.id.automaticUpdateCheckBox)
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

        automaticUpdateCheckBox.isChecked = settingsStore.automaticMissingWeatherUpdate
        automaticUpdateCheckBox.setOnCheckedChangeListener { _, checked ->
            settingsStore.automaticMissingWeatherUpdate = checked
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        startButton.setOnClickListener { startUpdate() }
        cancelButton.setOnClickListener { stopUpdate() }
        errorLogButton.setOnClickListener {
            startActivity(Intent(this, WeatherErrorLogActivity::class.java))
        }
        okButton.setOnClickListener { finish() }
        backButton.setOnClickListener { finish() }
    }

    private fun formatUpdateStats(totalTargets: Int, totalCatches: Int, failedTargets: Int): String {
        return "Päivitettäviä pisteitä: $totalTargets / $totalCatches\n" +
                "Viime yrityksellä epäonnistuneita $failedTargets kpl"
    }

    private fun loadStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allCatches = db.fishCatchDao().getAll()
            val attempts = db.weatherUpdateAttemptDao().getAll().associateBy { it.catchId }
            val targets = prioritizeMissingWeatherTargets(allCatches, attempts)
            val failedTargets = WeatherUpdateAttemptSelector.countFailedCatchIds(
                targets.map { it.id }, attempts
            )

            withContext(Dispatchers.Main) {
                pointsToUpdateText.text = formatUpdateStats(targets.size, allCatches.size, failedTargets)
                startButton.visibility = if (shouldShowWeatherUpdateStartButton(targets.size)) {
                    View.VISIBLE
                } else View.GONE
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
            try {
                val result = updater.update(maxCount) { progress ->
                    withContext(Dispatchers.Main) {
                        val percent = if (progress.total == 0) 100 else {
                            progress.attempted * 100 / progress.total
                        }
                        progressBar.progress = percent
                        statusText.text = "Päivitetään... $percent %"
                        statsText.text = "Yritetty: ${progress.attempted} / ${progress.total}\n" +
                                "Onnistuneet: ${progress.successful}\n" +
                                "Ei muutoksia: ${progress.noChanges}\n" +
                                "Epäonnistuneet: ${progress.failed}"
                    }
                }
                withContext(NonCancellable + Dispatchers.Main) { finishUpdate(result) }
            } catch (e: Exception) {
                withContext(NonCancellable + Dispatchers.Main) {
                    statusText.text = "Virhe: ${e.message}"
                    startButton.isEnabled = true
                    maxCountEditText.isEnabled = true
                    okButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun stopUpdate() {
        statusText.text = "Keskeytetään..."
        cancelButton.isEnabled = false
        updateJob?.cancel()
    }

    private fun finishUpdate(result: MissingWeatherUpdateResult) {
        if (!result.cancelled) settingsStore.lastMissingWeatherUpdateAt = System.currentTimeMillis()
        progressLayout.visibility = View.GONE
        resultLayout.visibility = View.VISIBLE
        okButton.visibility = View.VISIBLE
        summaryText.text = if (result.cancelled) "Päivitys keskeytetty." else "Päivitys valmis."
        successCountText.text = "Onnistuneesti päivitetty: ${result.successful}\nEi muutoksia: ${result.noChanges}"
        failureCountText.text = "Virheellisiä: ${result.failed}"
        errorLogButton.visibility = if (result.failed > 0) View.VISIBLE else View.GONE
        startButton.isEnabled = true
        maxCountEditText.isEnabled = true
        cancelButton.isEnabled = true
        loadStats()
        setResult(RESULT_OK)
    }
}
