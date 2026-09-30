package fi.anssi.kalakartta.ui

import android.app.DatePickerDialog
import android.text.InputFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.utils.CopernicusCloudCoverage

class CopernicusImageSettingsDialog(
    private val activity: AppCompatActivity,
    private val settingsStore: SettingsStore,
    private val onMapSettingsChanged: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {
    fun show(onReturnToMapSettings: (() -> Unit)? = null) {
        val density = activity.resources.displayMetrics.density
        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(),
                (8 * density).toInt(),
                (24 * density).toInt(),
                (8 * density).toInt()
            )
        }

        val maxCloudCoverageLabel = TextView(activity).apply {
            text = activity.getString(R.string.copernicus_max_cloud_coverage)
            textSize = 16f
        }
        contentLayout.addView(maxCloudCoverageLabel)

        val maxCloudCoverageInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            keyListener = DigitsKeyListener.getInstance("0123456789")
            filters = arrayOf(InputFilter.LengthFilter(3))
            setText(settingsStore.copernicusMaxCloudCoveragePercent.toString())
            hint = "${CopernicusCloudCoverage.MIN_PERCENT}–${CopernicusCloudCoverage.MAX_PERCENT}"
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s?.toString()?.toIntOrNull()
                    if (value == null || value !in CopernicusCloudCoverage.MIN_PERCENT..CopernicusCloudCoverage.MAX_PERCENT) {
                        error = if (s.isNullOrEmpty()) {
                            null
                        } else {
                            activity.getString(
                                R.string.copernicus_cloud_coverage_range_error,
                                CopernicusCloudCoverage.MIN_PERCENT,
                                CopernicusCloudCoverage.MAX_PERCENT
                            )
                        }
                        return
                    }
                    error = null
                    if (settingsStore.copernicusMaxCloudCoveragePercent != value) {
                        settingsStore.copernicusMaxCloudCoveragePercent = value
                        onMapSettingsChanged()
                    }
                }
            })
        }
        contentLayout.addView(maxCloudCoverageInput)

        val dateButton = Button(activity).apply {
            text = CopernicusDateSettings.display(settingsStore.copernicusTargetDate)
            visibility = if (settingsStore.copernicusCustomDateEnabled) android.view.View.VISIBLE else android.view.View.GONE
            setOnClickListener {
                val calendar = CopernicusDateSettings.calendarFor(settingsStore.copernicusTargetDate)
                DatePickerDialog(
                    activity,
                    { _, year, month, day ->
                        val selectedDate = CopernicusDateSettings.format(year, month, day)
                        settingsStore.copernicusTargetDate = selectedDate
                        text = CopernicusDateSettings.display(selectedDate)
                        onMapSettingsChanged()
                    },
                    calendar.get(java.util.Calendar.YEAR),
                    calendar.get(java.util.Calendar.MONTH),
                    calendar.get(java.util.Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        val customDateCheckBox = CheckBox(activity).apply {
            text = activity.getString(R.string.copernicus_custom_date)
            isChecked = settingsStore.copernicusCustomDateEnabled
            setOnCheckedChangeListener { _, isChecked ->
                settingsStore.copernicusCustomDateEnabled = isChecked
                dateButton.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
                onMapSettingsChanged()
            }
        }
        contentLayout.addView(customDateCheckBox)
        contentLayout.addView(dateButton)
        contentLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.copernicus_latest_image_note)
            textSize = 14f
            setPadding(0, (8 * density).toInt(), 0, 0)
        })

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.copernicus_image_settings_title)
            .setView(contentLayout)
            .setPositiveButton(
                if (onReturnToMapSettings != null) R.string.back else R.string.copernicus_settings_done
            ) { _, _ -> onReturnToMapSettings?.invoke() }
            .create()
        onShowDialog(dialog)
    }
}