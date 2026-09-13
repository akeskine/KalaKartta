package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RelativeLayout
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
    private val checkLimitsCallback: (
        checkHeatmap: Boolean,
        checkRoutes: Boolean,
        newGridSize: Double?,
        providedFilters: FilterManager.Filters?,
        providedRemoveTransitions: Boolean?,
        providedMaxSpeed: Float?,
        onResult: (Boolean) -> Unit
    ) -> Unit,
    private val onMapSettingsChanged: () -> Unit,
    private val onOpenAdvancedSettings: () -> Unit,
    private val onOpenRouteAdvancedSettings: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {

    private fun checkLimits(
        checkHeatmap: Boolean,
        checkRoutes: Boolean,
        newGridSize: Double? = null,
        providedFilters: FilterManager.Filters? = null,
        providedRemoveTransitions: Boolean? = null,
        providedMaxSpeed: Float? = null,
        onResult: (Boolean) -> Unit
    ) {
        checkLimitsCallback(
            checkHeatmap,
            checkRoutes,
            newGridSize,
            providedFilters,
            providedRemoveTransitions,
            providedMaxSpeed,
            onResult
        )
    }

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
                    checkLimits(true, false, providedFilters = FilterManager.Filters()) { success ->
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
                    checkLimits(false, true, providedFilters = FilterManager.Filters()) { success ->
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
    fun showAdvancedSettings() {
        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val colors = arrayOf(
            activity.getString(R.string.color_red),
            activity.getString(R.string.color_purple),
            activity.getString(R.string.color_green)
        )
        val currentColor = settingsStore.getHeatmapColor(activity.getString(R.string.color_red))
        val gradientView = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 100).apply {
                topMargin = 20
            }
            isClickable = true
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            foreground = activity.getDrawable(outValue.resourceId)
        }
        fun updateGradient(colorName: String?) {
            val baseColor = when (colorName) {
                activity.getString(R.string.color_purple) -> Color.rgb(128, 0, 128)
                activity.getString(R.string.color_green) -> Color.GREEN
                else -> Color.RED
            }
            val startColor = Color.argb(40, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            val endColor = Color.argb(240, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            val gradient = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(startColor, endColor)
            )
            gradientView.background = gradient
        }
        updateGradient(currentColor)
        gradientView.setOnClickListener {
            val current = settingsStore.getHeatmapColor(activity.getString(R.string.color_red))
            val currentIndex = colors.indexOf(current).coerceAtLeast(0)
            val nextIndex = (currentIndex + 1) % colors.size
            val nextColor = colors[nextIndex]
            settingsStore.setHeatmapColor(nextColor)
            updateGradient(nextColor)
            onMapSettingsChanged()
        }
        layout.addView(gradientView)

        val minMaxLayout = RelativeLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 10, 0, 10)
        }
        val midText = TextView(activity).apply {
            id = View.generateViewId()
            val method = settingsStore.getHeatmapCalculationMethod(
                activity.getString(R.string.heatmap_method_points)
            )
            text = if (method == activity.getString(R.string.heatmap_method_points)) {
                activity.getString(R.string.heatmap_points_in_grid)
            } else {
                activity.getString(R.string.heatmap_sessions_in_grid)
            }
            textSize = 14f
        }
        val minEdit = EditText(activity).apply {
            id = View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(
                settingsStore.heatmapMinPoints.toString()
            )
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = RelativeLayout.LayoutParams(120, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (settingsStore.heatmapAutoConfigure) return
                    val value = s.toString().toIntOrNull() ?: SettingsDefaults.HEATMAP_MIN_POINTS
                    settingsStore.heatmapMinPoints = value
                    val method = settingsStore.getHeatmapCalculationMethod(
                        activity.getString(R.string.heatmap_method_points)
                    )
                    if (method == activity.getString(R.string.heatmap_method_points)) {
                        settingsStore.heatmapMinPointsByPoints = value
                    } else {
                        settingsStore.heatmapMinPointsBySessions = value
                    }
                    onMapSettingsChanged()
                }
            })
        }
        minMaxLayout.addView(minEdit)
        val maxEdit = EditText(activity).apply {
            id = View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(
                settingsStore.heatmapMaxPoints.toString()
            )
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = RelativeLayout.LayoutParams(120, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (settingsStore.heatmapAutoConfigure) return
                    val value = s.toString().toIntOrNull() ?: SettingsDefaults.HEATMAP_MAX_POINTS
                    settingsStore.heatmapMaxPoints = value
                    val method = settingsStore.getHeatmapCalculationMethod(
                        activity.getString(R.string.heatmap_method_points)
                    )
                    if (method == activity.getString(R.string.heatmap_method_points)) {
                        settingsStore.heatmapMaxPointsByPoints = value
                    } else {
                        settingsStore.heatmapMaxPointsBySessions = value
                    }
                    onMapSettingsChanged()
                }
            })
        }
        minMaxLayout.addView(maxEdit)
        val midTextParamsReal = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.CENTER_HORIZONTAL)
            addRule(RelativeLayout.ALIGN_BASELINE, minEdit.id)
        }
        minMaxLayout.addView(midText, midTextParamsReal)
        layout.addView(minMaxLayout)

        val autoConfigureCb = CheckBox(activity).apply {
            text = activity.getString(R.string.heatmap_auto_configure)
            isChecked = settingsStore.heatmapAutoConfigure
            textSize = 16f
            minEdit.isEnabled = !isChecked
            maxEdit.isEnabled = !isChecked
            setOnCheckedChangeListener { _, checked ->
                settingsStore.heatmapAutoConfigure = checked
                minEdit.isEnabled = !checked
                maxEdit.isEnabled = !checked
                if (checked) {
                    minEdit.setText("1")
                    onMapSettingsChanged()
                } else {
                    onMapSettingsChanged()
                }
            }
        }
        layout.addView(autoConfigureCb)

        val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == SettingsKeys.HEATMAP_MIN_POINTS || key == SettingsKeys.HEATMAP_MAX_POINTS) {
                activity.runOnUiThread {
                    if (key == SettingsKeys.HEATMAP_MIN_POINTS) {
                        val newVal = settingsStore.heatmapMinPoints.toString()
                        if (minEdit.text.toString() != newVal) {
                            minEdit.setText(newVal)
                        }
                    } else if (key == SettingsKeys.HEATMAP_MAX_POINTS) {
                        val newVal = settingsStore.heatmapMaxPoints.toString()
                        if (maxEdit.text.toString() != newVal) {
                            maxEdit.setText(newVal)
                        }
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        val gridSizeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 10)
        }
        val gridSizeLabel = TextView(activity).apply {
            text = activity.getString(R.string.heatmap_grid_size)
            textSize = 16f
        }
        gridSizeRow.addView(gridSizeLabel)
        val gridSizes = arrayOf("50", "100", "300", "1000")
        val gridSizeSpinner = Spinner(activity)
        val gridAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, gridSizes)
        gridAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gridSizeSpinner.adapter = gridAdapter
        val currentGridSize = settingsStore.heatmapGridSize.toInt().toString()
        val gridIndex = gridSizes.indexOf(currentGridSize).coerceAtLeast(0)
        gridSizeSpinner.setSelection(gridIndex)
        gridSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isInitialSelection = true
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newValue = gridSizes[position].toFloatOrNull() ?: SettingsDefaults.HEATMAP_GRID_SIZE
                val oldValue = settingsStore.heatmapGridSize
                if (isInitialSelection) {
                    isInitialSelection = false
                    return
                }
                if (newValue == oldValue) return
                val heatmapEnabled = settingsStore.heatmapEnabled
                if (heatmapEnabled) {
                    checkLimits(true, false, newValue.toDouble()) { success ->
                        if (success) {
                            settingsStore.heatmapGridSize = newValue
                            onMapSettingsChanged()
                        } else {
                            val oldGridSizeStr = oldValue.toInt().toString()
                            val oldIndex = gridSizes.indexOf(oldGridSizeStr).coerceAtLeast(0)
                            gridSizeSpinner.setSelection(oldIndex)
                        }
                    }
                } else {
                    settingsStore.heatmapGridSize = newValue
                    onMapSettingsChanged()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        gridSizeRow.addView(gridSizeSpinner)
        layout.addView(gridSizeRow)

        val methodRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        val methodLabel = TextView(activity).apply {
            text = activity.getString(R.string.heatmap_calculation_method)
            textSize = 16f
            setPadding(0, 10, 0, 5)
        }
        methodRow.addView(methodLabel)
        val methods = arrayOf(
            activity.getString(R.string.heatmap_method_sessions),
            activity.getString(R.string.heatmap_method_points)
        )
        val methodSpinner = Spinner(activity)
        val methodAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, methods)
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        methodSpinner.adapter = methodAdapter
        val currentMethod = settingsStore.getHeatmapCalculationMethod(
            activity.getString(R.string.heatmap_method_points)
        )
        val methodIndex = methods.indexOf(currentMethod).coerceAtLeast(0)
        methodSpinner.setSelection(methodIndex)
        methodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMethod = methods[position]
                val oldMethod = settingsStore.getHeatmapCalculationMethod(
                    activity.getString(R.string.heatmap_method_points)
                )
                if (selectedMethod != oldMethod) {
                    settingsStore.setHeatmapCalculationMethod(selectedMethod)
                    val newMin: Int
                    val newMax: Int
                    if (selectedMethod == activity.getString(R.string.heatmap_method_points)) {
                        newMin = settingsStore.heatmapMinPointsByPoints
                        newMax = settingsStore.heatmapMaxPointsByPoints
                    } else {
                        newMin = settingsStore.heatmapMinPointsBySessions
                        newMax = settingsStore.heatmapMaxPointsBySessions
                    }
                    minEdit.setText(newMin.toString())
                    maxEdit.setText(newMax.toString())
                    settingsStore.heatmapMinPoints = newMin
                    settingsStore.heatmapMaxPoints = newMax
                    midText.text = if (selectedMethod == activity.getString(R.string.heatmap_method_points)) {
                        activity.getString(R.string.heatmap_points_in_grid)
                    } else {
                        activity.getString(R.string.heatmap_sessions_in_grid)
                    }
                    onMapSettingsChanged()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        methodRow.addView(methodSpinner)
        layout.addView(methodRow)

        // Siirtymäpisteiden poisto siirretty tänne
        val removeTransitionsCb = CheckBox(activity).apply {
            text = activity.getString(R.string.heatmap_remove_transitions)
            isChecked = settingsStore.heatmapRemoveTransitions
            textSize = 18f
        }
        val removalModeLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 0, 40, 10)
            visibility = if (removeTransitionsCb.isChecked) View.VISIBLE else View.GONE
        }
        val removalModeLabel = TextView(activity).apply {
            text = activity.getString(R.string.heatmap_transition_removal_mode)
            textSize = 16f
            setPadding(0, 10, 0, 5)
        }
        removalModeLayout.addView(removalModeLabel)
        val removalModeSpinner = Spinner(activity).apply {
            val modes = listOf(
                activity.getString(R.string.heatmap_transition_removal_only_heatmap),
                activity.getString(R.string.heatmap_transition_removal_all)
            )
            val removalModeAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, modes)
            removalModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            adapter = removalModeAdapter
            setSelection(settingsStore.heatmapRemoveTransitionsMode)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (settingsStore.heatmapRemoveTransitionsMode == position) return
                    val routesEnabled = settingsStore.fishingRoutesEnabled
                    if (position == 0 && routesEnabled) {
                         checkLimits(false, routesEnabled, providedRemoveTransitions = false) { success ->
                            if (success) {
                                settingsStore.heatmapRemoveTransitionsMode = position
                                onMapSettingsChanged()
                            } else {
                                setSelection(1)
                            }
                        }
                    } else {
                        settingsStore.heatmapRemoveTransitionsMode = position
                        onMapSettingsChanged()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        removalModeLayout.addView(removalModeSpinner)
        val speedInputLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(40, 0, 40, 0)
            visibility = if (removeTransitionsCb.isChecked) View.VISIBLE else View.GONE
        }
        val speedLabel = TextView(activity).apply {
            text = activity.getString(R.string.heatmap_max_speed)
            textSize = 16f
        }
        speedInputLayout.addView(speedLabel)
        val speedEdit = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(settingsStore.heatmapMaxSpeed.toString())
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(150, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = 20
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s.toString().replace(",", ".").toFloatOrNull() ?: SettingsDefaults.HEATMAP_MAX_SPEED
                    val oldSpeed = settingsStore.heatmapMaxSpeed
                    if (value == oldSpeed) return
                    val heatmapEnabled = settingsStore.heatmapEnabled
                    val routesEnabled = settingsStore.fishingRoutesEnabled
                    if ((heatmapEnabled || routesEnabled) && value > oldSpeed) {
                        checkLimits(heatmapEnabled, routesEnabled, providedMaxSpeed = value) { success ->
                            if (success) {
                                settingsStore.heatmapMaxSpeed = value
                                onMapSettingsChanged()
                            } else {
                                setText(oldSpeed.toString())
                            }
                        }
                        return
                    }
                    settingsStore.heatmapMaxSpeed = value
                    onMapSettingsChanged()
                }
            })
        }
        speedInputLayout.addView(speedEdit)
        removeTransitionsCb.setOnCheckedChangeListener { _, isChecked ->
            val wasChecked = settingsStore.heatmapRemoveTransitions
            if (wasChecked && !isChecked) {
                val heatmapEnabled = settingsStore.heatmapEnabled
                val routesEnabled = settingsStore.fishingRoutesEnabled
                if (heatmapEnabled || routesEnabled) {
                    checkLimits(heatmapEnabled, routesEnabled, providedRemoveTransitions = false) { success ->
                        if (success) {
                            settingsStore.heatmapRemoveTransitions = false
                            speedInputLayout.visibility = View.GONE
                            removalModeLayout.visibility = View.GONE
                            onMapSettingsChanged()
                        } else {
                            removeTransitionsCb.isChecked = true
                        }
                    }
                    return@setOnCheckedChangeListener
                }
            }
            settingsStore.heatmapRemoveTransitions = isChecked
            speedInputLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            removalModeLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            onMapSettingsChanged()
        }
        layout.addView(removeTransitionsCb)
        layout.addView(removalModeLayout)
        layout.addView(speedInputLayout)

        layout.addView(createBackLink {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            show()
        })

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.heatmap_advanced_settings))
            .setView(ScrollView(activity).apply { addView(layout) })
            .setOnCancelListener {
                prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            }
            .create()
        onShowDialog(dialog)
    }

    fun showRouteAdvancedSettings() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val typedValue = android.util.TypedValue()
        activity.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val primaryTextColor = if (typedValue.resourceId != 0) {
            activity.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }

        // Checkbox: Häivytä vanhat reittiviitat
        val fadeEnabledCheckbox = CheckBox(activity).apply {
            text = "Häivytä vanhat reittiviitat"
            setTextColor(primaryTextColor)
            isChecked = settingsStore.routesFadeEnabled
            setPadding(20, 20, 20, 20)
        }
        layout.addView(fadeEnabledCheckbox)

        // Lisäasetusten kontti
        val fadeSettingsLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (fadeEnabledCheckbox.isChecked) View.VISIBLE else View.GONE
        }
        layout.addView(fadeSettingsLayout)

        // Visuaalinen palkki
        val previewLine = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8).apply {
                topMargin = 40
                bottomMargin = 10
            }
            val colorStr = settingsStore.getHeatmapColor(activity.getString(R.string.color_red))
            val baseColor = when (colorStr) {
                activity.getString(R.string.color_purple) -> Color.rgb(128, 0, 128)
                activity.getString(R.string.color_green) -> Color.GREEN
                else -> Color.RED
            }
            val startColor = Color.argb(20, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            val endColor = Color.argb(200, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(startColor, endColor)
            )
        }
        fadeSettingsLayout.addView(previewLine)

        // Syöttökentät
        val inputsLayout = RelativeLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val startLimitEdit = EditText(activity).apply {
            id = View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(
                settingsStore.routesFadeStartDays.toString()
            )
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = RelativeLayout.LayoutParams(150, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s.toString().toIntOrNull() ?: SettingsDefaults.ROUTES_FADE_START_DAYS
                    settingsStore.routesFadeStartDays = value
                    onMapSettingsChanged()
                }
            })
        }
        inputsLayout.addView(startLimitEdit)

        val fullLimitEdit = EditText(activity).apply {
            id = View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(
                settingsStore.routesFadeFullDays.toString()
            )
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = RelativeLayout.LayoutParams(150, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s.toString().toIntOrNull() ?: SettingsDefaults.ROUTES_FADE_FULL_DAYS
                    settingsStore.routesFadeFullDays = value
                    onMapSettingsChanged()
                }
            })
        }
        inputsLayout.addView(fullLimitEdit)

        fadeSettingsLayout.addView(inputsLayout)

        // Yksikköteksti
        fadeSettingsLayout.addView(TextView(activity).apply {
            text = "Yksikkönä päivä. Vasen: häivytys alkaa, Oikea: täysin näkyvä."
            textSize = 12f
            setTextColor(primaryTextColor)
            setPadding(0, 10, 0, 20)
        })

        fadeEnabledCheckbox.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.routesFadeEnabled = isChecked
            fadeSettingsLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            onMapSettingsChanged()
        }

        layout.addView(createBackLink {
            show()
        })

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.route_advanced_settings))
            .setView(ScrollView(activity).apply { addView(layout) })
            .create()
        onShowDialog(dialog)
    }
}
