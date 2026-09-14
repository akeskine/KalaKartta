package fi.anssi.kalakartta.ui

import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R

/** Yleisasetusten navigointidialogi. */
class GeneralSettingsDialog(
    private val activity: AppCompatActivity,
    private val onOpenDefaultFisherman: () -> Unit,
    private val onOpenWeather: () -> Unit,
    private val onOpenScale: () -> Unit,
    private val onOpenAutoCenter: () -> Unit,
    private val onOpenTalkingClock: () -> Unit,
    private val onOpenIconSizes: () -> Unit,
    private val onOpenDeveloperTools: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onShowDialog: (AlertDialog) -> Unit
) {

    fun show() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        fun addLink(text: String, onClick: () -> Unit) {
            layout.addView(menuLinkTextView(activity).apply {
                this.text = text
                setPadding(0, 20, 0, 40)
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { onClick() }
            })
        }

        addLink("Oletuskalastaja", onOpenDefaultFisherman)
        addLink("Sää", onOpenWeather)
        addLink(activity.getString(R.string.scale_bar), onOpenScale)
        addLink(activity.getString(R.string.auto_center), onOpenAutoCenter)
        addLink(activity.getString(R.string.talking_clock), onOpenTalkingClock)
        addLink(activity.getString(R.string.icon_sizes), onOpenIconSizes)
        addLink("Kehittäjäasetukset", onOpenDeveloperTools)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.general_settings))
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton("Takaisin") { _, _ -> onOpenSettings() }
            .create()
        onShowDialog(dialog)
    }
}
