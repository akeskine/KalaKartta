package fi.anssi.kalakartta.ui

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.service.TalkingClockService

/** Puhuvan kellon asetusten dialogi. */
class TalkingClockSettingsDialog(
    private val activity: AppCompatActivity,
    private val settingsStore: SettingsStore,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {

    fun show() {
        var dialog: AlertDialog? = null
        var statusTextView: TextView? = null

        fun getTitle(): String {
            return activity.getString(R.string.talking_clock)
        }

        fun getStatusText(): String {
            val isEnabled = settingsStore.talkingClockEnabled
            if (!isEnabled) return ""

            val interval = settingsStore.talkingClockInterval
            val status = activity.getString(R.string.talking_clock_running)
            return "$status, ${activity.getString(R.string.talking_clock_interval_info)} $interval ${activity.getString(R.string.unit_min)}"
        }

        fun updateTitle() {
            dialog?.setTitle(getTitle())
            statusTextView?.apply {
                text = getStatusText()
                visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        statusTextView = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 20)
            text = getStatusText()
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        layout.addView(statusTextView)

        val clockControlLink = TextView(activity).apply {
            val isEnabled = settingsStore.talkingClockEnabled
            text = activity.getString(if (isEnabled) R.string.talking_clock_stop else R.string.talking_clock_start)
            textSize = 18f
            setTextColor(androidx.core.content.ContextCompat.getColor(activity, android.R.color.holo_blue_dark))
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            
            setOnClickListener {
                val newState = !settingsStore.talkingClockEnabled
                settingsStore.talkingClockEnabled = newState
                text = activity.getString(if (newState) R.string.talking_clock_stop else R.string.talking_clock_start)
                updateTitle()
                
                if (newState) {
                    val interval = settingsStore.talkingClockInterval
                    val intent = Intent(activity, TalkingClockService::class.java).apply {
                        putExtra("interval", interval)
                        action = "START_IMMEDIATELY"
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity.startForegroundService(intent)
                    } else {
                        activity.startService(intent)
                    }
                } else {
                    activity.stopService(Intent(activity, TalkingClockService::class.java))
                }
            }
        }
        layout.addView(clockControlLink)

        val onlyFishingCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.talking_clock_only_fishing)
            isChecked = settingsStore.talkingClockOnlyFishing
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.talkingClockOnlyFishing = isChecked
            }
        }
        layout.addView(onlyFishingCheckbox)

        // Kerro kellonaika X minuutin välein
        val intervalLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 20, 0, 20)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        intervalLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.talking_clock_interval_prefix)
            textSize = 18f
        })

        val intervals = arrayOf("1", "2", "5", "10", "15", "20", "30", "60")
        val currentInterval = settingsStore.talkingClockInterval.toString()
        val intervalSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, intervals)
            setSelection(intervals.indexOf(currentInterval).let { if (it == -1) 4 else it }) // Oletus 30 min
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val interval = intervals[position].toInt()
                    settingsStore.talkingClockInterval = interval
                    updateTitle()
                    
                    if (settingsStore.talkingClockEnabled) {
                        val intent = Intent(activity, TalkingClockService::class.java).apply {
                            putExtra("interval", interval)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity.startForegroundService(intent)
                        } else {
                            activity.startService(intent)
                        }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        intervalLayout.addView(intervalSpinner)
        intervalLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.talking_clock_interval_suffix)
            textSize = 18f
        })
        layout.addView(intervalLayout)

        // Puhuttelu
        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.talking_clock_salutation)
            textSize = 18f
            setPadding(0, 10, 0, 0)
        })
        val salutationEdit = EditText(activity).apply {
            setText(settingsStore.talkingClockSalutation)
            textSize = 18f
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    settingsStore.talkingClockSalutation = s?.toString() ?: SettingsDefaults.TALKING_CLOCK_SALUTATION
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        layout.addView(salutationEdit)

        // Akun varaus
        val batteryCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.talking_clock_battery_status)
            isChecked = settingsStore.talkingClockBattery
            textSize = 18f
            setPadding(paddingLeft, 10, paddingRight, 0)
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.talkingClockBattery = isChecked
            }
        }
        layout.addView(batteryCheckbox)

        val weatherHoursLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 0, 0, 10)
            visibility = if (settingsStore.talkingClockWeather) View.VISIBLE else View.GONE
        }
        val weatherHourOptions = listOf(1, 3, 6, 12)
        weatherHourOptions.forEach { hours ->
            val defaultChecked = hours == SettingsDefaults.TALKING_CLOCK_WEATHER_DEFAULT_HOURS
            if (!settingsStore.hasTalkingClockWeatherSetting(hours)) {
                settingsStore.setTalkingClockWeatherEnabled(hours, defaultChecked)
            }
            weatherHoursLayout.addView(CheckBox(activity).apply {
                text = "$hours h päähän"
                textSize = 18f
                isChecked = settingsStore.isTalkingClockWeatherEnabled(hours, defaultChecked)
                setOnCheckedChangeListener { _, isChecked ->
                    settingsStore.setTalkingClockWeatherEnabled(hours, isChecked)
                }
            })
        }

        val weatherCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.talking_clock_weather)
            isChecked = settingsStore.talkingClockWeather
            textSize = 18f
            setPadding(paddingLeft, 10, paddingRight, 0)
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.talkingClockWeather = isChecked
                weatherHoursLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }
        layout.addView(weatherCheckbox)
        layout.addView(weatherHoursLayout)

        // Auringonlasku
        val sunsetLimitLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(80, 0, 0, 10)
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = if (settingsStore.talkingClockSunset) View.VISIBLE else View.GONE
        }
        sunsetLimitLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.talking_clock_sunset_limit)
            textSize = 18f
        })
        val sunsetHours = arrayOf("1", "2", "3", "4", "5", "6", "12", "24")
        val currentSunsetLimit = settingsStore.talkingClockSunsetLimit.toString()
        val sunsetSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, sunsetHours)
            setSelection(sunsetHours.indexOf(currentSunsetLimit).let { if (it == -1) 1 else it })
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    settingsStore.talkingClockSunsetLimit = sunsetHours[position].toInt()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        sunsetLimitLayout.addView(sunsetSpinner)
        sunsetLimitLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.hours_suffix)
            textSize = 18f
        })

        val sunsetCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.talking_clock_sunset)
            isChecked = settingsStore.talkingClockSunset
            textSize = 18f
            setPadding(paddingLeft, 10, paddingRight, 0)
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.talkingClockSunset = isChecked
                sunsetLimitLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }
        layout.addView(sunsetCheckbox)
        layout.addView(sunsetLimitLayout)

        // Auringonnousu
        val sunriseLimitLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(80, 0, 0, 10)
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = if (settingsStore.talkingClockSunrise) View.VISIBLE else View.GONE
        }
        sunriseLimitLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.talking_clock_sunrise_limit)
            textSize = 18f
        })
        val sunriseHours = arrayOf("1", "2", "3", "4", "6", "12", "24")
        val currentSunriseLimit = settingsStore.talkingClockSunriseLimit.toString()
        val sunriseSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, sunriseHours)
            setSelection(sunriseHours.indexOf(currentSunriseLimit).let { if (it == -1) 1 else it })
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    settingsStore.talkingClockSunriseLimit = sunriseHours[position].toInt()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        sunriseLimitLayout.addView(sunriseSpinner)
        sunriseLimitLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.hours_suffix)
            textSize = 18f
        })

        val sunriseCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.talking_clock_sunrise)
            isChecked = settingsStore.talkingClockSunrise
            textSize = 18f
            setPadding(paddingLeft, 10, paddingRight, 0)
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.talkingClockSunrise = isChecked
                sunriseLimitLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }
        layout.addView(sunriseCheckbox)
        layout.addView(sunriseLimitLayout)

        dialog = AlertDialog.Builder(activity)
            .setTitle(getTitle())
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }
            .create()
        onShowDialog(dialog!!)
    }
}
