package fi.anssi.kalakartta.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.data.PressureSample
import fi.anssi.kalakartta.MainActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.utils.ForecastSummary
import fi.anssi.kalakartta.utils.ForecastCloudCoverLevel
import fi.anssi.kalakartta.utils.MoonCalculator
import fi.anssi.kalakartta.utils.SunService
import fi.anssi.kalakartta.utils.WeatherService
import fi.anssi.kalakartta.utils.formatForecastSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Sääasetusten ja puuttuvien säätietojen päivityksen dialogi. */
class WeatherSettingsDialog(
    private val activity: AppCompatActivity,
    private val settingsStore: SettingsStore,
    private val onWeatherSettingsChanged: (Boolean) -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit,
    private val locationProvider: () -> Pair<Double, Double>? = { null }
) {

    fun show() {
        var isEnabledCurrent = settingsStore.weatherEnabled
        val coordinates = locationProvider()

        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }

        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }

        val summaryTime = System.currentTimeMillis()
        val forecastTitle = TextView(activity).apply {
            text = "Sääennustetta haetaan…"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(activity.resolveThemeColor(android.R.attr.textColorPrimary))
        }
        val forecastRows = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val currentPressureLabel = TextView(activity).apply {
            text = "Nyt: — hPa"
            textSize = 10f
            setTextColor(activity.resolveThemeColor(android.R.attr.textColorPrimary))
            setPadding(dp(4), dp(2), dp(4), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(activity.resolveThemeColor(android.R.attr.colorBackground))
            }
        }
        val pressureGraph = PressureGraphView(activity).apply {
            setHistoryData(emptyList(), summaryTime)
        }
        val moonAndPressureVisuals = createMoonAndPressureVisuals(
            pressureGraph,
            currentPressureLabel,
            summaryTime,
            coordinates
        )
        val seaLevelGraph = SeaLevelGraphView(activity)
        val seaLevelStatus = TextView(activity).apply {
            textSize = 10f
            setTextColor(activity.resolveThemeColor(android.R.attr.textColorSecondary))
        }
        val seaLevelTitle = TextView(activity).apply {
            textSize = 10f
            setTextColor(activity.resolveThemeColor(android.R.attr.textColorPrimary))
        }
        val seaLevelContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            addView(seaLevelTitle)
            addView(seaLevelStatus)
            addView(seaLevelGraph, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(112)
            ))
        }
        val sunriseSunsetSummary = createSunriseSunsetSummary(summaryTime, coordinates)

        val forecastBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(activity.resolveThemeColor(android.R.attr.colorBackground))
                setStroke(dp(1), activity.resolveThemeColor(android.R.attr.textColorSecondary))
            }
            addView(forecastTitle)
            addView(forecastRows)
            addView(sunriseSunsetSummary, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            })
            addView(moonAndPressureVisuals, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(112)
            ).apply {
                topMargin = dp(6)
            })
            addView(seaLevelContent, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            })
        }
        contentLayout.addView(forecastBox, 0, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        })

        val checkBox = CheckBox(activity).apply {
            text = "Säädatan automaattinen haku"
            isChecked = isEnabledCurrent
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                isEnabledCurrent = isChecked
                if (isEnabledCurrent != settingsStore.weatherEnabled) {
                    settingsStore.weatherEnabled = isEnabledCurrent
                    if (isEnabledCurrent) {
                        WeatherService(activity).fetchAllStations()
                    }
                    onWeatherSettingsChanged(isEnabledCurrent)
                }
            }
        }
        contentLayout.addView(checkBox)

        val updateLink = menuLinkTextView(activity).apply {
            text = "Puuttuvien säätietojen päivitys"
            setPadding(0, 20, 0, 40)
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                val intent = Intent(activity, WeatherUpdateActivity::class.java)
                if (activity is MainActivity) {
                    activity.launchActivityForResult(intent, 1003)
                } else {
                    activity.startActivity(intent)
                }
            }
        }
        contentLayout.addView(updateLink)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Sää")
            .setView(scrollView)
            .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }
            .create()
        var summaryJob: Job? = null
        dialog.setOnDismissListener { summaryJob?.cancel() }
        onShowDialog(dialog)
        if (coordinates != null) {
            summaryJob = loadForecastSummary(
                coordinates = coordinates,
                summaryTime = summaryTime,
                titleView = forecastTitle,
                rowsLayout = forecastRows,
                pressureGraph = pressureGraph,
                currentPressureLabel = currentPressureLabel,
                seaLevelContent = seaLevelContent,
                seaLevelTitle = seaLevelTitle,
                seaLevelStatus = seaLevelStatus,
                seaLevelGraph = seaLevelGraph
            )
        } else {
            forecastTitle.text = "Sääennuste"
            forecastRows.addView(createInfoText("Karttasijaintia ei ole saatavilla."))
            currentPressureLabel.text = "Painehistoria ei saatavilla"
        }
    }

    private fun createSunriseSunsetSummary(
        now: Long,
        coordinates: Pair<Double, Double>?
    ): TextView {
        val sunTimes = coordinates?.let { (latitude, longitude) ->
            SunService().getSunriseSunset(
                latitude,
                longitude,
                Calendar.getInstance().apply { timeInMillis = now }
            )
        }
        val timeFormatter = SimpleDateFormat("H:mm", Locale.getDefault())
        val sunrise = sunTimes?.first?.timeInMillis?.let { timeFormatter.format(Date(it)) } ?: "—"
        val sunset = sunTimes?.second?.timeInMillis?.let { timeFormatter.format(Date(it)) } ?: "—"

        return TextView(activity).apply {
            text = "Aurinko nousee $sunrise, laskee $sunset."
            textSize = 10f
            setTextColor(activity.resolveThemeColor(android.R.attr.textColorPrimary))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(activity.resolveThemeColor(android.R.attr.colorBackground))
                setStroke(1, Color.BLACK)
            }
        }
    }

    private fun createMoonAndPressureVisuals(
        pressureGraph: PressureGraphView,
        currentPressureLabel: TextView,
        now: Long,
        coordinates: Pair<Double, Double>?
    ): LinearLayout {
        val visualRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val moonColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        moonColumn.addView(TextView(activity).apply {
            text = "Kuun vaihe"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(activity.resolveThemeColor(android.R.attr.textColorPrimary))
        })
        val moonCalculator = MoonCalculator()
        moonColumn.addView(MoonPhaseView(activity).apply {
            setPhase(moonCalculator.getMoonPhase(now))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })
        val moonRiseSetTimes = coordinates?.let { (latitude, longitude) ->
            val startOfDay = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfNextDay = (startOfDay.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 1)
            }
            moonCalculator.getMoonRiseSetTimes(
                latitude,
                longitude,
                startOfDay.timeInMillis,
                startOfNextDay.timeInMillis
            )
        }
        val timeFormatter = SimpleDateFormat("H:mm", Locale.getDefault())
        moonColumn.addView(TextView(activity).apply {
            text = "Nousee: ${moonRiseSetTimes?.riseTimeMillis?.let { timeFormatter.format(Date(it)) } ?: "—"}\n" +
                    "Laskee: ${moonRiseSetTimes?.setTimeMillis?.let { timeFormatter.format(Date(it)) } ?: "—"}"
            textSize = 8f
            gravity = Gravity.CENTER
            setTextColor(activity.resolveThemeColor(android.R.attr.textColorPrimary))
        })
        visualRow.addView(moonColumn, LinearLayout.LayoutParams(dp(68), LinearLayout.LayoutParams.MATCH_PARENT))

        val graphFrame = FrameLayout(activity)
        graphFrame.addView(pressureGraph, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        graphFrame.addView(currentPressureLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))
        visualRow.addView(graphFrame, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        return visualRow
    }

    private fun loadForecastSummary(
        coordinates: Pair<Double, Double>,
        summaryTime: Long,
        titleView: TextView,
        rowsLayout: LinearLayout,
        pressureGraph: PressureGraphView,
        currentPressureLabel: TextView,
        seaLevelContent: LinearLayout,
        seaLevelTitle: TextView,
        seaLevelStatus: TextView,
        seaLevelGraph: SeaLevelGraphView
    ): Job {
        val weatherService = WeatherService(activity)
        return activity.lifecycleScope.launch {
            try {
                val (latitude, longitude) = coordinates
                val forecastRequest = async {
                    weatherService.fetchForecastSuspend(latitude, longitude, listOf(1, 3, 6, 12, 24))
                }
                val pressureForecastRequest = async {
                    weatherService.fetchPressureForecastSuspend(latitude, longitude, summaryTime)
                }
                val stationRequest = async {
                    weatherService.fetchNearestStationsSuspend(latitude, longitude, summaryTime, 5)
                }
                val seaLevelSummaryRequest = async {
                    weatherService.fetchSeaLevelWeatherSummarySuspend(latitude, longitude, summaryTime)
                }
                val forecasts = forecastRequest.await()
                val pressureForecast = pressureForecastRequest.await()
                val stations = stationRequest.await().orEmpty()
                val seaLevelSummary = seaLevelSummaryRequest.await()

                var pressureHistory: Pair<List<PressureSample>, Double>? = null
                for (station in stations.take(3)) {
                    val samples = weatherService.fetchPressureSamplesSuspend(
                        station.fmisid,
                        summaryTime - 12 * 60 * 60 * 1000L,
                        summaryTime
                    ).filter { it.pressure.isFinite() }
                    val currentPressure = samples.lastOrNull()?.pressure
                    if (samples.size >= 2 && currentPressure != null) {
                        pressureHistory = samples to currentPressure
                        break
                    }
                }

                if (activity.isFinishing || activity.isDestroyed) return@launch
                if (seaLevelSummary.isSea) {
                    seaLevelTitle.text = formatSeaLevelTitle(
                        seaLevelSummary.stationName,
                        seaLevelSummary.currentSeaLevel
                    )
                    seaLevelContent.visibility = android.view.View.VISIBLE
                    if (seaLevelSummary.historySamples.isEmpty() && seaLevelSummary.forecastSamples.isEmpty()) {
                        seaLevelStatus.text = "Meriveden korkeustietoja ei saatu haettua."
                        seaLevelStatus.visibility = android.view.View.VISIBLE
                        seaLevelGraph.visibility = android.view.View.GONE
                    } else {
                        seaLevelStatus.visibility = android.view.View.GONE
                        seaLevelGraph.setWeatherSummaryData(
                            seaLevelSummary.historySamples,
                            seaLevelSummary.forecastSamples,
                            summaryTime
                        )
                        seaLevelGraph.visibility = android.view.View.VISIBLE
                    }
                }
                val stationName = stations.firstOrNull()?.name?.takeIf(String::isNotBlank)
                titleView.text = stationName?.let { "Sää $it" } ?: "Sää"
                rowsLayout.removeAllViews()
                val forecastSummaries = forecasts.mapNotNull { (_, row) -> formatForecastSummary(row) }
                val timeColumnWidth = forecastSummaries.maxOfOrNull { summary ->
                    val timeText = createForecastText("${summary.time}:")
                    timeText.paint.measureText(timeText.text.toString()).toInt() + dp(4)
                } ?: 0
                val temperatureColumnWidth = maxOf(
                    dp(40),
                    forecastSummaries.maxOfOrNull { summary ->
                        val temperatureText = createForecastText(summary.temperatureText.orEmpty())
                        temperatureText.paint.measureText(temperatureText.text.toString()).toInt() + dp(4)
                    } ?: 0
                )
                forecastSummaries.forEach { summary ->
                    rowsLayout.addView(createForecastRow(summary, timeColumnWidth, temperatureColumnWidth))
                }
                if (rowsLayout.childCount == 0) {
                    rowsLayout.addView(createInfoText("Sääennustetta ei saatu haettua."))
                }

                pressureGraph.setWeatherSummaryData(
                    historySamples = pressureHistory?.first.orEmpty(),
                    forecastSamples = buildList {
                        pressureHistory?.second?.let { add(PressureSample(summaryTime, it)) }
                        addAll(pressureForecast.filter { it.time > summaryTime })
                    },
                    now = summaryTime
                )
                if (pressureHistory != null) {
                    currentPressureLabel.text = "Nyt: ${String.format(Locale.getDefault(), "%.1f", pressureHistory.second)} hPa"
                } else {
                    currentPressureLabel.text = "Painehistoria ei saatavilla"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (activity.isFinishing || activity.isDestroyed) return@launch
                titleView.text = "Sää"
                rowsLayout.removeAllViews()
                rowsLayout.addView(createInfoText("Sääennustetta ei saatu haettua."))
                currentPressureLabel.text = "Painehistoria ei saatavilla"
            }
        }
    }

    private fun createForecastRow(
        summary: ForecastSummary,
        timeColumnWidth: Int,
        temperatureColumnWidth: Int
    ): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))

            addView(createForecastText("${summary.time}:").apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(timeColumnWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(
                createForecastText(summary.temperatureText.orEmpty()),
                forecastItemLayoutParams(temperatureColumnWidth)
            )

            val cloudIcon = createWeatherIcon(
                resource = summary.cloudCoverLevel?.let(::cloudCoverIcon) ?: R.drawable.ic_weather_cloudy,
                description = summary.cloudCoverDescription ?: "Pilvisyys"
            ).apply {
                if (summary.cloudCoverLevel == null) visibility = android.view.View.INVISIBLE
            }
            addView(cloudIcon, forecastIconLayoutParams())

            val rainIconResource = summary.precipitationMmPerHour?.let { precipitation ->
                if (precipitation < 0.025) {
                    R.drawable.ic_weather_no_rain
                } else if (precipitation < 1.5) {
                    R.drawable.ic_weather_rain_light
                } else if (precipitation < 3.0) {
                    R.drawable.ic_weather_rain
                } else {
                    R.drawable.ic_weather_rain_heavy
                }
            }
            val rainIcon = createWeatherIcon(
                resource = rainIconResource ?: R.drawable.ic_weather_no_rain,
                description = summary.precipitationDescription ?: "Sade"
            ).apply {
                if (rainIconResource == null) visibility = android.view.View.INVISIBLE
            }
            addView(rainIcon, forecastIconLayoutParams())

            summary.windText?.let { wind ->
                addView(createForecastText(wind), forecastItemLayoutParams())
            }

            summary.windDirectionDegrees?.takeIf(Float::isFinite)?.let { direction ->
                addView(ImageView(activity).apply {
                    setImageResource(R.drawable.ic_wind_arrow)
                    rotation = (direction + 180f) % 360f
                    contentDescription = "Tuulen suunta"
                }, LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                    marginStart = dp(4)
                })
            }
        }
    }

    private fun createForecastText(text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 12f
        isSingleLine = true
        setTextColor(activity.resolveThemeColor(android.R.attr.textColorPrimary))
    }

    private fun createWeatherIcon(resource: Int, description: String): ImageView = ImageView(activity).apply {
        setImageResource(resource)
        contentDescription = description
    }

    private fun forecastItemLayoutParams(width: Int = LinearLayout.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(
        width,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        marginStart = dp(4)
    }

    private fun forecastIconLayoutParams() = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
        marginStart = dp(4)
    }

    private fun cloudCoverIcon(level: ForecastCloudCoverLevel?): Int = when (level) {
        ForecastCloudCoverLevel.CLEAR,
        ForecastCloudCoverLevel.MOSTLY_CLEAR -> R.drawable.ic_weather_clear
        ForecastCloudCoverLevel.PARTLY_CLOUDY -> R.drawable.ic_weather_partly_cloudy
        ForecastCloudCoverLevel.MOSTLY_CLOUDY -> R.drawable.ic_weather_cloudy
        ForecastCloudCoverLevel.OVERCAST -> R.drawable.ic_weather_overcast
        null -> R.drawable.ic_weather_cloudy
    }

    private fun createInfoText(text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 12f
        setTextColor(activity.resolveThemeColor(android.R.attr.textColorSecondary))
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}

internal fun formatSeaLevelTitle(stationName: String, seaLevel: Long?): String {
    val station = stationName.substringAfter(':').trim().takeIf(String::isNotEmpty)
    val value = seaLevel?.let { if (it >= 0) "+$it" else it.toString() } ?: "—"
    val stationLabel = station?.let { " $it" }.orEmpty()
    return "Meriveden korkeus$stationLabel: $value cm (MW)"
}
