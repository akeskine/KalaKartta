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

/** Heatmapin ja reittien pääasetusten dialogi. */
class HeatmapSettingsDialog(
    private val activity: AppCompatActivity,
    private val settingsStore: SettingsStore,
    private val checkLimits: (
        checkHeatmap: Boolean,
        checkRoutes: Boolean,
        providedFilters: FilterManager.Filters?,
        onResult: (Boolean) -> Unit
    ) -> Unit,
    private val onMapSettingsChanged: () -> Unit,
    private val onOpenAdvancedSettings: () -> Unit,
    private val onOpenRouteAdvancedSettings: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {

    private fun createBackLink(onClick: () -> Unit): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val backLink = TextView(activity).apply {
            text = "Takaisin"
            textSize = 18f
            setTextColor(activity.resources.getColor(android.R.color.holo_blue_dark))
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        container.addView(backLink)
        return container
    }

    fun show() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val heatmapEnabledCb = CheckBox(activity).apply {
            text = activity.getString(R.string.show_fishing_heatmap)
            isChecked = settingsStore.heatmapEnabled
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkLimits(true, false, null) { success ->
                        if (success) {
                            settingsStore.heatmapEnabled = true
                            onMapSettingsChanged()
                        } else {
                            this.isChecked = false
                        }
                    }
                } else {
                    settingsStore.heatmapEnabled = false
                    onMapSettingsChanged()
                }
            }
        }
        layout.addView(heatmapEnabledCb)

        val routesEnabledCb = CheckBox(activity).apply {
            text = activity.getString(R.string.show_fishing_routes)
            isChecked = settingsStore.fishingRoutesEnabled
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkLimits(false, true, null) { success ->
                        if (success) {
                            settingsStore.fishingRoutesEnabled = true
                            onMapSettingsChanged()
                        } else {
                            this.isChecked = false
                        }
                    }
                } else {
                    settingsStore.fishingRoutesEnabled = false
                    onMapSettingsChanged()
                }
            }
        }
        layout.addView(routesEnabledCb)

        val heatmapFilterEnabledCb = CheckBox(activity).apply {
            text = activity.getString(R.string.heatmap_filter_enabled)
            isChecked = settingsStore.heatmapFilterEnabled
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                val heatmapEnabled = settingsStore.heatmapEnabled
                if (!isChecked && heatmapEnabled) {
                    checkLimits(true, false, FilterManager.Filters()) { success ->
                        if (success) {
                            settingsStore.heatmapFilterEnabled = false
                            onMapSettingsChanged()
                        } else {
                            this.isChecked = true
                        }
                    }
                } else {
                    settingsStore.heatmapFilterEnabled = isChecked
                    onMapSettingsChanged()
                }
            }
        }
        layout.addView(heatmapFilterEnabledCb)

        val routesFilterEnabledCb = CheckBox(activity).apply {
            text = activity.getString(R.string.routes_filter_enabled)
            isChecked = settingsStore.routesFilterEnabled
            textSize = 18f
            setOnCheckedChangeListener { _, isChecked ->
                val routesEnabled = settingsStore.fishingRoutesEnabled
                if (!isChecked && routesEnabled) {
                    checkLimits(false, true, FilterManager.Filters()) { success ->
                        if (success) {
                            settingsStore.routesFilterEnabled = false
                            onMapSettingsChanged()
                        } else {
                            this.isChecked = true
                        }
                    }
                } else {
                    settingsStore.routesFilterEnabled = isChecked
                    onMapSettingsChanged()
                }
            }
        }
        layout.addView(routesFilterEnabledCb)

        val shortcutModeLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        shortcutModeLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.show_heatmap_shortcut)
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        val shortcutSpinner = Spinner(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val shortcutOptions = activity.resources.getStringArray(R.array.heatmap_shortcut_options)
        val shortcutAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, shortcutOptions)
        shortcutAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        shortcutSpinner.adapter = shortcutAdapter

        if (!settingsStore.hasHeatmapShortcutModeSetting()) {
            val oldVal = settingsStore.showHeatmapShortcut
            settingsStore.heatmapShortcutMode = if (oldVal) 3 else 0
        }

        shortcutSpinner.setSelection(settingsStore.heatmapShortcutMode)
        shortcutSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isInitialSelection = true

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInitialSelection) {
                    isInitialSelection = false
                    return
                }
                settingsStore.heatmapShortcutMode = position
                onMapSettingsChanged()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        shortcutModeLayout.addView(shortcutSpinner)
        layout.addView(shortcutModeLayout)

        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.heatmap_advanced_settings)
            textSize = 18f
            setTextColor(android.graphics.Color.BLUE)
            setPadding(0, 20, 0, 20)
            isClickable = true
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            foreground = activity.getDrawable(outValue.resourceId)
            setOnClickListener { onOpenAdvancedSettings() }
        })

        layout.addView(TextView(activity).apply {
            text = activity.getString(R.string.route_advanced_settings)
            textSize = 18f
            setTextColor(android.graphics.Color.BLUE)
            setPadding(0, 20, 0, 20)
            isClickable = true
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            foreground = activity.getDrawable(outValue.resourceId)
            setOnClickListener { onOpenRouteAdvancedSettings() }
        })

        layout.addView(createBackLink(onOpenSettings))

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.action_fishing_heatmap))
            .setView(ScrollView(activity).apply { addView(layout) })
            .create()
        onShowDialog(dialog)
    }
}
