package fi.anssi.kalakartta.ui

import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.MainActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kalastussessioiden ja reittipisteiden tallennusvälien dialogit. */
class FishingSessionSettingsDialog(
    private val activity: AppCompatActivity,
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit,
    private val onCloseSettings: () -> Unit,
    private val onDialogDismissed: (AlertDialog) -> Unit
) {

    fun show() {
        val mainActivity = activity as? fi.anssi.kalakartta.MainActivity
        val fishingService = mainActivity?.getFishingService()
        val isRecording = fishingService?.isRecording() ?: false

        val typedValue = android.util.TypedValue()
        activity.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val primaryTextColor = if (typedValue.resourceId != 0) {
            activity.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }

        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }

        if (!isRecording) {
            var dialog: AlertDialog? = null

            val visibleSessionId = mainActivity?.getVisibleArchivedSessionId() ?: -1L
            if (visibleSessionId != -1L) {
                val visibleInfoLabel = TextView(activity).apply {
                    textSize = 14f
                    setPadding(0, 0, 0, 10)
                }
                contentLayout.addView(visibleInfoLabel)

                val hideSessionButton = TextView(activity).apply {
                    text = "Piilota näkyvä sessio"
                    textSize = 16f
                    setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                    val outValue = android.util.TypedValue()
                    activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                    isClickable = true
                    isFocusable = true
                    setPadding(0, 20, 0, 20)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(0, 0, 0, 30)
                    layoutParams = params
                    setOnClickListener {
                        mainActivity?.hideArchivedSession()
                        dialog?.dismiss()
                        show()
                    }
                }
                contentLayout.addView(hideSessionButton)

                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val session = db.fishingSessionDao().getById(visibleSessionId)
                    if (session != null) {
                        val sdfDate = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
                        val sdfTime = SimpleDateFormat("H:mm", Locale.getDefault())
                        val startStr = sdfTime.format(Date(session.startedAt))
                        val endStr = session.endedAt?.let { sdfTime.format(Date(it)) } ?: "?"
                        
                        val startDay = sdfDate.format(Date(session.startedAt))
                        val endDay = session.endedAt?.let { sdfDate.format(Date(it)) } ?: startDay

                        val infoText = if (startDay == endDay) {
                            "Näkyvä kalastussessio $startDay $startStr - $endStr"
                        } else {
                            "Näkyvä kalastussessio $startDay $startStr - $endDay $endStr"
                        }

                        withContext(Dispatchers.Main) {
                            visibleInfoLabel.text = infoText
                        }
                    }
                }
            }

            val fetchButton = TextView(activity).apply {
                text = "Hae kalastussessiot"
                textSize = 16f
                setTextColor(primaryTextColor)

                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(0, 20, 0, 20)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 20)
                layoutParams = params
                setOnClickListener {
                    val intent = Intent(activity, FishingSessionActivity::class.java)
                    activity.startActivityForResult(intent, 3001)
                }
            }
            contentLayout.addView(fetchButton)

            val trackingSettingsLink = TextView(activity).apply {
                text = "Reittipisteiden tallennusvälit"
                textSize = 16f
                setTextColor(primaryTextColor)
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(0, 20, 0, 20)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 20)
                layoutParams = params
                setOnClickListener {
                    showTrackingIntervalSettings()
                }
            }
            contentLayout.addView(trackingSettingsLink)

            val statusText = TextView(activity).apply {
                textSize = 14f
                setTextColor(Color.RED)
                setPadding(0, 10, 0, 10)
                visibility = View.GONE
            }
            contentLayout.addView(statusText)

            val showLiveRouteCb = CheckBox(activity).apply {
                text = "Näytä tallennettavan session reitti"
                isChecked = settingsStore.showLiveSessionRoute
                textSize = 16f
                setOnCheckedChangeListener { _, isChecked ->
                    settingsStore.showLiveSessionRoute = isChecked
                    mainActivity?.updateSessionLine()
                }
            }
            contentLayout.addView(showLiveRouteCb)

            val startButton = TextView(activity).apply {
                text = "Aloita tallennus"
                textSize = 16f
                setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(0, 20, 0, 20)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 10, 0, 10)
                layoutParams = params
                setOnClickListener {
                    val locInt = settingsStore.locationCheckInterval
                    val minInt = settingsStore.minTrackPointInterval
                    val maxInt = settingsStore.maxTrackPointInterval
                    val minDist = settingsStore.minTrackPointDistance
                    
                    mainActivity?.startFishingSession(locInt, minInt, maxInt, minDist)
                    dialog?.dismiss()
                    onCloseSettings()
                }
            }
            contentLayout.addView(startButton)

            val updateJob = activity.lifecycleScope.launch {
                val locationManager = activity.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                while (isActive) {
                    val isGpsEnabled = try {
                        locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    } catch (_: Exception) {
                        false
                    }
                    val isNetworkEnabled = try {
                        locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                    } catch (_: Exception) {
                        false
                    }
                    
                    val locationEnabled = isGpsEnabled || isNetworkEnabled
                    startButton.isEnabled = locationEnabled
                    startButton.visibility = if (locationEnabled) View.VISIBLE else View.GONE
                    showLiveRouteCb.visibility = if (locationEnabled) View.VISIBLE else View.GONE
                    trackingSettingsLink.visibility = if (locationEnabled) View.VISIBLE else View.GONE
                    
                    if (locationEnabled) {
                        statusText.visibility = View.GONE
                    } else {
                        statusText.text = "Sijaintipalvelu ei ole päällä. Ota sijainti käyttöön aloittaaksesi tallennuksen."
                        statusText.visibility = View.VISIBLE
                    }
                    
                    kotlinx.coroutines.delay(1000)
                }
            }

            dialog = AlertDialog.Builder(activity)
                .setTitle("Kalastussessiot")
                .setView(scrollView)
                .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }
                .setOnDismissListener {
                    updateJob.cancel()
                    onDialogDismissed(dialog!!)
                }
                .create()
            onShowDialog(dialog!!)
        } else {
            var dialog: AlertDialog? = null
            
            val infoText = TextView(activity).apply {
                textSize = 16f
                setPadding(0, 0, 0, 40)
            }
            contentLayout.addView(infoText)

            val updateJob = activity.lifecycleScope.launch {
                while (isActive) {
                    val currentService = (activity as? fi.anssi.kalakartta.MainActivity)?.getFishingService()
                    if (currentService != null && currentService.isRecording()) {
                        val startedAt = currentService.getStartedAt()
                        val duration = System.currentTimeMillis() - startedAt
                        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(duration)
                        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(duration) % 60
                        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(duration) % 60
                        
                        val durationStr = if (hours > 0) "${hours} h ${minutes} min ${seconds} s" else "${minutes} min ${seconds} s"
                        val distance = currentService.getTotalDistance()
                        val distanceStr = String.format("%.4f km", distance / 1000.0).replace(".", ",")
                        val minInterval = currentService.getMinIntervalSeconds()
                        val maxInterval = currentService.getMaxIntervalSeconds()
                        val locInterval = currentService.getLocationCheckIntervalSeconds()
                        val minDist = currentService.getMinDistanceMeters()
                        val sessionId = currentService.getCurrentSessionId()
                        val pointCount = if (sessionId != -1L) {
                            db.trackPointDao().getPointCountForSession(sessionId)
                        } else 0

                        var text = "Kalastussessio käynnissä: kesto $durationStr, matka $distanceStr, reittipisteitä $pointCount kpl.\n\nTallennusvälit: min ${minInterval}s, max ${maxInterval}s, etäisyys ${minDist}m, tarkastus ${locInterval}s."
                        
                        if (fi.anssi.kalakartta.service.FishingSessionService.KALASTUSSESSIOT_DEBUG) {
                            val checkCount = currentService.getLocationCheckCount()
                            val lastTimestamp = currentService.getLastSavedTimestamp()
                            val now = System.currentTimeMillis()
                            
                            val distanceToLast = currentService.getDistanceSinceLastSave()?.let { String.format("%.1f m", it) } ?: "-"
                            val lastLoc = currentService.getLastLocation()
                            val speedStr = lastLoc?.let { String.format("%.2f km/h", it.speed * 3.6).replace(".", ",") } ?: "-"
                            val accuracyStr = lastLoc?.let { String.format("%.0f m", it.accuracy) } ?: "-"
                            val timeStr = if (lastTimestamp > 0) "${(now - lastTimestamp) / 1000} s" else "-"
                            
                            text += "\n\nSijainnin tarkastus nro: $checkCount"
                            text += "\nEtäisyys edellisestä pisteestä: $distanceToLast"
                            text += "\nNopeus: $speedStr"
                            text += "\nTarkkuus: $accuracyStr"
                            text += "\nAika edellisen pisteen tallennuksesta: $timeStr"
                        }
                        
                        infoText.text = text
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }

            val showLiveRouteCb = CheckBox(activity).apply {
                text = "Näytä tallennettavan session reitti"
                isChecked = settingsStore.showLiveSessionRoute
                textSize = 16f
                setOnCheckedChangeListener { _, isChecked ->
                    settingsStore.showLiveSessionRoute = isChecked
                    mainActivity?.updateSessionLine()
                }
            }
            contentLayout.addView(showLiveRouteCb)
            
            val stopButton = TextView(activity).apply {
                text = "Lopeta tallennus"
                textSize = 16f
                setTextColor(activity.getColor(android.R.color.holo_red_dark))
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                setPadding(0, 20, 0, 20)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 10, 0, 10)
                layoutParams = params
                setOnClickListener {
                    (activity as? fi.anssi.kalakartta.MainActivity)?.stopFishingSession()
                    dialog?.dismiss()
                }
            }
            contentLayout.addView(stopButton)

            dialog = AlertDialog.Builder(activity)
                .setTitle("Kalastussessiot")
                .setView(scrollView)
                .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }
                .setOnDismissListener {
                    updateJob.cancel()
                    onDialogDismissed(dialog!!)
                }
                .create()
            onShowDialog(dialog!!)
        }
    }

    fun showTrackingIntervalSettings() {
        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }

        val sectionTitle = TextView(activity).apply {
            text = "Reittipisteiden tallennusvälit"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        contentLayout.addView(sectionTitle)

        val locationIntervals = arrayOf("10", "30", "60", "120")
        val minIntervals = arrayOf("10", "30", "60", "120")
        val maxIntervals = arrayOf("60", "120", "300", "600")
        val minDistances = arrayOf("10", "20", "50", "100", "200")

        var currentLocationInterval = settingsStore.locationCheckInterval.toString()
        var currentMinInterval = settingsStore.minTrackPointInterval.toString()
        var currentMaxInterval = settingsStore.maxTrackPointInterval.toString()
        var currentMinDistance = settingsStore.minTrackPointDistance.toString()

        if (currentLocationInterval !in locationIntervals) currentLocationInterval = "10"
        if (currentMinInterval !in minIntervals) currentMinInterval = "30"
        if (currentMaxInterval !in maxIntervals) currentMaxInterval = "300"
        if (currentMinDistance !in minDistances) currentMinDistance = "20"

        val locationSpinner = Spinner(activity)
        val minIntervalSpinner = Spinner(activity)
        val maxIntervalSpinner = Spinner(activity)
        val minDistanceSpinner = Spinner(activity)

        fun updateSpinners() {
            val locVal = currentLocationInterval.toInt()
            val minVal = currentMinInterval.toInt()
            val maxVal = currentMaxInterval.toInt()

            if (maxVal < minVal) {
                currentMinInterval = currentMaxInterval
                minIntervalSpinner.setSelection(minIntervals.indexOf(currentMinInterval))
                settingsStore.minTrackPointInterval = currentMinInterval.toInt()
            }
            
            val newMinVal = currentMinInterval.toInt()
            if (newMinVal < locVal) {
                currentLocationInterval = currentMinInterval
                locationSpinner.setSelection(locationIntervals.indexOf(currentLocationInterval))
                settingsStore.locationCheckInterval = currentLocationInterval.toInt()
            }
        }

        fun createRow(labelText: String, spinner: Spinner): LinearLayout {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 10, 0, 10)
            }
            
            val label = TextView(activity).apply {
                text = labelText
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            spinner.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            row.addView(label)
            row.addView(spinner)
            return row
        }

        contentLayout.addView(createRow("Sijainnin tarkastuksen aikaväli (s)", locationSpinner.apply {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, locationIntervals)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(locationIntervals.indexOf(currentLocationInterval))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    currentLocationInterval = locationIntervals[pos]
                settingsStore.locationCheckInterval = currentLocationInterval.toInt()
                    updateSpinners()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }))

        contentLayout.addView(createRow("Tallennusaikaväli min (s)", minIntervalSpinner.apply {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, minIntervals)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(minIntervals.indexOf(currentMinInterval))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    currentMinInterval = minIntervals[pos]
                    settingsStore.minTrackPointInterval = currentMinInterval.toInt()
                    updateSpinners()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }))

        contentLayout.addView(createRow("Tallennusaikaväli max (s)", maxIntervalSpinner.apply {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, maxIntervals)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(maxIntervals.indexOf(currentMaxInterval))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    currentMaxInterval = maxIntervals[pos]
                settingsStore.maxTrackPointInterval = currentMaxInterval.toInt()
                    updateSpinners()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }))

        contentLayout.addView(createRow("Pisteiden minimietäisyys (m)", minDistanceSpinner.apply {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, minDistances)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(minDistances.indexOf(currentMinDistance))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    currentMinDistance = minDistances[pos]
                settingsStore.minTrackPointDistance = currentMinDistance.toInt()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }))

        val resetDefaultsLink = TextView(activity).apply {
            text = activity.getString(R.string.reset_defaults)
            textSize = 14f
            setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.link_color))
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setPadding(0, 10, 0, 10)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 10, 0, 10)
            layoutParams = params
            setOnClickListener {
                currentLocationInterval = "10"
                currentMinInterval = "30"
                currentMaxInterval = "300"
                currentMinDistance = "20"
                
                settingsStore.locationCheckInterval = SettingsDefaults.LOCATION_CHECK_INTERVAL
                settingsStore.minTrackPointInterval = SettingsDefaults.MIN_TRACK_POINT_INTERVAL
                settingsStore.maxTrackPointInterval = SettingsDefaults.MAX_TRACK_POINT_INTERVAL
                settingsStore.minTrackPointDistance = SettingsDefaults.MIN_TRACK_POINT_DISTANCE

                locationSpinner.setSelection(locationIntervals.indexOf(currentLocationInterval))
                minIntervalSpinner.setSelection(minIntervals.indexOf(currentMinInterval))
                maxIntervalSpinner.setSelection(maxIntervals.indexOf(currentMaxInterval))
                minDistanceSpinner.setSelection(minDistances.indexOf(currentMinDistance))
            }
        }
        contentLayout.addView(resetDefaultsLink)

        val dialog = AlertDialog.Builder(activity)
            .setView(scrollView)
            .setPositiveButton("Takaisin") { _, _ -> show() }
            .create()
        onShowDialog(dialog)
    }
}
