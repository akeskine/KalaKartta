package fi.anssi.kalakartta.ui

import android.content.Intent
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.MainActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.utils.WeatherService

/** Sääasetusten ja puuttuvien säätietojen päivityksen dialogi. */
class WeatherSettingsDialog(
    private val activity: AppCompatActivity,
    private val settingsStore: SettingsStore,
    private val onWeatherSettingsChanged: (Boolean) -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {

    fun show() {
        var isEnabledCurrent = settingsStore.weatherEnabled

        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }

        val scrollView = ScrollView(activity).apply {
            addView(contentLayout)
        }

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

        val updateLink = actionLinkTextView(activity).apply {
            text = "Päivitä puuttuvat säätiedot"
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
            .setTitle("Sääasetukset")
            .setView(scrollView)
            .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }
            .create()
        onShowDialog(dialog)
    }
}
