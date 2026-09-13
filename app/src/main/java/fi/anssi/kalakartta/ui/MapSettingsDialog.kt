package fi.anssi.kalakartta.ui

import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Taustakartan ja karttalähteiden pikavalintojen dialogi. */
class MapSettingsDialog(
    private val activity: AppCompatActivity,
    private val settingsStore: SettingsStore,
    private val onMapSettingsChanged: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {

    fun show() {
        val currentSource = settingsStore.mapSource
        val currentApiKey = settingsStore.mmlApiKey
        var showQuickMapCurrent = settingsStore.showQuickMapSource

        val sources = arrayOf(
            "OpenStreetMap",
            "MML Maastokartta",
            "MML Ilmakuva",
            activity.getString(R.string.map_source_traficom),
            activity.getString(R.string.map_source_traficom_boating)
        )
        val internalIds = arrayOf("OSM", "MML_MAASTO", "MML_ILMA", "TRAFICOM_SEA", "TRAFICOM_BOATING")
        val quickSelectEnabled = internalIds.associateWith { id ->
            val default = if (id.startsWith("MML_")) currentApiKey.isNotEmpty() else true
            settingsStore.isQuickMapSourceEnabled(id, default)
        }.toMutableMap()

        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }
        val scrollView = ScrollView(activity).apply { addView(contentLayout) }

        val headerLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 20)
            weightSum = 1f
        }
        headerLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.map_background)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
        })
        headerLayout.addView(TextView(activity).apply {
            text = activity.getString(R.string.quick_select)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f)
        })
        contentLayout.addView(headerLayout)

        val radioButtons = mutableListOf<RadioButton>()
        val checkBoxes = mutableMapOf<String, CheckBox>()

        for (i in sources.indices) {
            val id = internalIds[i]
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                weightSum = 1f
                setPadding(0, 10, 0, 10)
            }
            val rb = RadioButton(activity).apply {
                text = sources[i]
                textSize = 18f
                isChecked = currentSource == id
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
                setOnClickListener {
                    radioButtons.forEach { it.isChecked = false }
                    isChecked = true
                    settingsStore.mapSource = id
                    onMapSettingsChanged()
                }
            }
            radioButtons.add(rb)
            row.addView(rb)

            val cb = CheckBox(activity).apply {
                isChecked = quickSelectEnabled[id] ?: true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f)
                gravity = android.view.Gravity.CENTER
                setOnCheckedChangeListener { _, isChecked ->
                    quickSelectEnabled[id] = isChecked
                    settingsStore.setQuickMapSourceEnabled(id, isChecked)
                }
            }
            checkBoxes[id] = cb
            row.addView(cb)
            contentLayout.addView(row)
        }

        val quickMapCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.show_quick_map_source)
            isChecked = showQuickMapCurrent
            textSize = 18f
            setPadding(0, 20, 0, 40)
            setOnCheckedChangeListener { _, isChecked ->
                showQuickMapCurrent = isChecked
                settingsStore.showQuickMapSource = isChecked
                onMapSettingsChanged()
            }
        }

        val apiKeyLabel = TextView(activity).apply {
            text = "MML API-avain:"
            textSize = 16f
            setPadding(0, 30, 0, 0)
            visibility = if (internalIds.indexOf(currentSource) in 1..2) View.VISIBLE else View.GONE
        }
        contentLayout.addView(apiKeyLabel)

        val apiKeyInput = EditText(activity).apply {
            setText(currentApiKey)
            hint = "Syötä API-avain"
            visibility = if (internalIds.indexOf(currentSource) in 1..2) View.VISIBLE else View.GONE
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    settingsStore.mmlApiKey = s.toString()
                }
            })
        }
        contentLayout.addView(apiKeyInput)

        val setApiKeyButton = Button(activity).apply {
            text = activity.getString(R.string.set_api_key)
            visibility = if (internalIds.indexOf(currentSource) in 1..2) View.VISIBLE else View.GONE
        }
        contentLayout.addView(setApiKeyButton)

        fun selectedId(): Int = radioButtons.indexOfFirst { it.isChecked }

        fun validateApiKey(apiKey: String) {
            if (apiKey.isEmpty()) return
            val selectedLayer = if (selectedId() == 2) "ortokuva" else "maastokartta"
            activity.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val urlString = "https://avoin-karttakuva.maanmittauslaitos.fi/avoin/wmts/1.0.0/$selectedLayer/default/WGS84_Pseudo-Mercator/0/0/0.png?api-key=$apiKey"
                    val connection = java.net.URL(urlString).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    val responseCode = connection.responseCode
                    withContext(Dispatchers.Main) {
                        if (responseCode == 200) {
                            Toast.makeText(activity, "API-avain OK", Toast.LENGTH_SHORT).show()
                            if (!settingsStore.hasQuickMapSourceSetting("MML_MAASTO") && checkBoxes["MML_MAASTO"]?.isChecked == false) {
                                checkBoxes["MML_MAASTO"]?.isChecked = true
                            }
                            if (!settingsStore.hasQuickMapSourceSetting("MML_ILMA") && checkBoxes["MML_ILMA"]?.isChecked == false) {
                                checkBoxes["MML_ILMA"]?.isChecked = true
                            }
                        } else {
                            Toast.makeText(activity, "API-avain ei kelpaa (HTTP $responseCode).", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Virhe testatessa: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        setApiKeyButton.setOnClickListener { validateApiKey(apiKeyInput.text.toString()) }

        if (currentApiKey.isNotEmpty()) validateApiKey(currentApiKey)

        val attributionText = TextView(activity).apply {
            text = attribution(currentSource)
            textSize = 12f
            setPadding(0, 40, 0, 0)
            alpha = 0.7f
        }
        contentLayout.addView(attributionText)
        contentLayout.addView(quickMapCheckbox)

        radioButtons.forEachIndexed { index, radioButton ->
            radioButton.setOnCheckedChangeListener { _, isChecked ->
                if (!isChecked) return@setOnCheckedChangeListener
                val id = internalIds[index]
                val mmlVisible = if (index in 1..2) View.VISIBLE else View.GONE
                apiKeyLabel.visibility = mmlVisible
                apiKeyInput.visibility = mmlVisible
                setApiKeyButton.visibility = mmlVisible
                attributionText.text = attribution(id)
                if (index in 1..2 && apiKeyInput.text.isNotEmpty()) {
                    validateApiKey(apiKeyInput.text.toString())
                }
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Taustakartta")
            .setView(scrollView)
            .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }
            .create()
        onShowDialog(dialog)
    }

    private fun attribution(source: String): String = when (source) {
        "TRAFICOM_SEA", "TRAFICOM_BOATING" -> activity.getString(R.string.traficom_attribution)
        "OSM" -> "Lähde: OpenStreetMap-yhteisö. Lisenssi: ODbL."
        else -> "Lähde: Maanmittauslaitos / avoin aineisto. Lisenssi: CC BY 4.0."
    }
}
