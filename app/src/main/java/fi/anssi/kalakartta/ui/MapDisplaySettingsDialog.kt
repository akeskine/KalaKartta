package fi.anssi.kalakartta.ui

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
import fi.anssi.kalakartta.R

/** Kartan mittakaava-, kohdistus- ja kuvakeasetusten dialogit. */
class MapDisplaySettingsDialog(
    private val activity: AppCompatActivity,
    private val settingsStore: SettingsStore,
    private val onMapSettingsChanged: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {

    fun showIconSizeSettings() {
        val typedValue = android.util.TypedValue()
        activity.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val primaryTextColor = if (typedValue.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(activity, typedValue.resourceId)
        } else {
            typedValue.data
        }

        val fishIconScale = settingsStore.fishIconScale
        val otherIconScale = settingsStore.otherIconScale

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val options = arrayOf("0.2", "0.3", "0.4", "0.5", "0.75", "1.0", "1.25", "1.5")
        val optionValues = arrayOf(0.2f, 0.3f, 0.4f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f)

        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.fish_icon_size)
            setTextColor(primaryTextColor)
            setPadding(0, 20, 0, 10)
        })

        val fishSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, options)
            val currentPos = optionValues.indexOf(fishIconScale).let { if (it == -1) 2 else it }
            setSelection(currentPos)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    (view as? TextView)?.setTextColor(primaryTextColor)
                    val newValue = optionValues[position]
                    if (settingsStore.fishIconScale != newValue) {
                        settingsStore.fishIconScale = newValue
                        onMapSettingsChanged()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        layout.addView(fishSpinner)

        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.other_icon_size)
            setTextColor(primaryTextColor)
            setPadding(0, 40, 0, 10)
        })

        val otherSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, options)
            val currentPos = optionValues.indexOf(otherIconScale).let { if (it == -1) 2 else it }
            setSelection(currentPos)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    (view as? TextView)?.setTextColor(primaryTextColor)
                    val newValue = optionValues[position]
                    if (settingsStore.otherIconScale != newValue) {
                        settingsStore.otherIconScale = newValue
                        onMapSettingsChanged()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        layout.addView(otherSpinner)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.icon_sizes))
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton(activity.getString(R.string.back)) { _, _ -> onOpenSettings() }
            .create()
        onShowDialog(dialog)
    }

    fun showScaleSettings() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        layout.addView(CheckBox(activity).apply {
            text = activity.getString(R.string.show_scale_bar)
            isChecked = settingsStore.showScaleBar
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.showScaleBar = isChecked
                onMapSettingsChanged()
            }
        })

        layout.addView(CheckBox(activity).apply {
            text = activity.getString(R.string.show_measurement_tool)
            isChecked = settingsStore.showMeasurementTool
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.showMeasurementTool = isChecked
                onMapSettingsChanged()
            }
        })

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.scale_bar))
            .setView(layout)
            .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }
            .create()
        onShowDialog(dialog)
    }

    fun showAutoCenterSettings() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        layout.addView(CheckBox(activity).apply {
            text = activity.getString(R.string.auto_center_on_start)
            isChecked = settingsStore.autoCenterOnStart
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.autoCenterOnStart = isChecked
            }
        })

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.auto_center))
            .setView(layout)
            .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }
            .create()
        onShowDialog(dialog)
    }
}
