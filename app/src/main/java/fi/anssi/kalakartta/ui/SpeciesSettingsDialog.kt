package fi.anssi.kalakartta.ui

import android.content.Intent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.io.ImportExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kalalajien muokkaus-, tuonti-, vienti- ja palautusdialogi. */
class SpeciesSettingsDialog(
    private val activity: AppCompatActivity,
    private val db: AppDatabase,
    private val importExportManager: ImportExportManager,
    private val onDataChanged: (Boolean) -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {

    fun show() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val species = db.fishSpeciesDao().getAll()
            val defaults = fi.anssi.kalakartta.data.FishSpecies.getDefaultList()
            
            // Tarkistetaan onko muutoksia tehty
            val isModified = species.size != defaults.size || species.any { s ->
                val d = defaults.find { it.id == s.id }
                d == null || s.name != d.name || s.small_weight != d.small_weight || 
                s.small_length != d.small_length || s.large_weight != d.large_weight || 
                s.large_length != d.large_length || s.giant_weight != d.giant_weight || 
                s.giant_length != d.giant_length || s.icon_default != d.icon_default || 
                s.favourite_fish != d.favourite_fish || s.sortOrder != d.sortOrder
            }

            withContext(Dispatchers.Main) {
                val typedValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                val primaryTextColor = if (typedValue.resourceId != 0) {
                    activity.getColor(typedValue.resourceId)
                } else {
                    typedValue.data
                }

                val layout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                }

                val editSpeciesLink = TextView(activity).apply {
                    text = activity.getString(R.string.edit_species)
                    textSize = 16f
                    setTextColor(primaryTextColor)
                    setPadding(0, 20, 0, 40)
                    val outValue = android.util.TypedValue()
                    activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                    setOnClickListener {
                        val intent = Intent(activity, EditSpeciesActivity::class.java)
                        activity.startActivityForResult(intent, 1002)
                    }
                }
                layout.addView(editSpeciesLink)

                if (isModified) {
                    val exportSpeciesLink = TextView(activity).apply {
                        text = activity.getString(R.string.export_species_settings)
                        textSize = 18f
                        setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                        setPadding(0, 20, 0, 40)
                        val outValue = android.util.TypedValue()
                        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                        setBackgroundResource(outValue.resourceId)
                        setOnClickListener {
                            importExportManager.launchExportSpecies()
                        }
                    }
                    layout.addView(exportSpeciesLink)
                }

                val importSpeciesLink = TextView(activity).apply {
                    text = activity.getString(R.string.import_species_settings)
                    textSize = 18f
                    setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                    setPadding(0, 20, 0, 40)
                    val outValue = android.util.TypedValue()
                    activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                    setOnClickListener {
                        val d = AlertDialog.Builder(activity)
                            .setMessage(R.string.import_species_confirm)
                            .setPositiveButton("Takaisin", null)
                            .setNegativeButton("Tuo") { _, _ ->
                                importExportManager.launchImportSpecies()
                            }
                            .create()
                        onShowDialog(d)
                    }
                }
                layout.addView(importSpeciesLink)

                if (isModified) {
                    val resetSpeciesLink = TextView(activity).apply {
                        text = activity.getString(R.string.reset_default_species)
                        textSize = 18f
                        setTextColor(activity.getColor(android.R.color.holo_blue_dark))
                        setPadding(0, 20, 0, 40)
                        val outValue = android.util.TypedValue()
                        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                        setBackgroundResource(outValue.resourceId)
                        setOnClickListener {
                            val d = AlertDialog.Builder(activity)
                                .setMessage(R.string.reset_species_confirm)
                                .setPositiveButton("Takaisin", null)
                                .setNegativeButton("Palauta") { _, _ ->
                                    activity.lifecycleScope.launch(Dispatchers.IO) {
                                        db.fishSpeciesDao().deleteAll()
                                        fi.anssi.kalakartta.data.FishSpecies.getDefaultList().forEach {
                                            db.fishSpeciesDao().insert(it)
                                        }
                                        withContext(Dispatchers.Main) {
                                            onDataChanged(true)
                                            Toast.makeText(activity, "Oletukset palautettu", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .create()
                            onShowDialog(d)
                        }
                    }
                    layout.addView(resetSpeciesLink)
                }

                val dialog = AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.fish_species_settings))
                    .setView(layout)
                    .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }
                    .create()
                onShowDialog(dialog)
            }
        }
    }
}
