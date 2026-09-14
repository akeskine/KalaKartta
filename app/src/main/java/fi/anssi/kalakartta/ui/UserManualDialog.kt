package fi.anssi.kalakartta.ui

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.BuildConfig
import fi.anssi.kalakartta.utils.enlargeButtons

/** Käyttöohjeen lataamisesta, muotoilusta ja näyttämisestä vastaava dialogi. */
class UserManualDialog(
    private val activity: AppCompatActivity,
    private val settingsStore: SettingsStore,
    private val onCloseSettings: () -> Unit,
    private val onStartupDialogShown: (AlertDialog) -> Unit
) {

    fun show(isStartup: Boolean = false) {
        try {
            val inputStream = activity.assets.open("kayttoohje.md")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val content = String(buffer, Charsets.UTF_8)

            val textView = TextView(activity).apply {
                text = android.text.Html.fromHtml(
                    MarkdownToHtmlConverter.convert(content),
                    android.text.Html.FROM_HTML_MODE_LEGACY
                )
                setPadding(60, 40, 60, 40)
                textSize = 16f
                movementMethod = object : LinkMovementMethod() {
                    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
                        val action = event.action
                        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                            var x = event.x.toInt()
                            var y = event.y.toInt()

                            x -= widget.totalPaddingLeft
                            y -= widget.totalPaddingTop

                            x += widget.scrollX
                            y += widget.scrollY

                            val layout = widget.layout
                            val line = layout.getLineForVertical(y)
                            val off = layout.getOffsetForHorizontal(line, x.toFloat())

                            val links = buffer.getSpans(off, off, URLSpan::class.java)
                            if (links.isNotEmpty()) {
                                if (action == MotionEvent.ACTION_UP) {
                                    val url = links[0].url
                                    if (url.startsWith("#")) {
                                        scrollToAnchor(widget, url.substring(1))
                                    } else {
                                        links[0].onClick(widget)
                                    }
                                }
                                return true
                            }
                        }

                        return super.onTouchEvent(widget, buffer, event)
                    }
                }
            }

            val scrollView = ScrollView(activity).apply {
                id = View.generateViewId()
                addView(textView)
            }

            val dialog = AlertDialog.Builder(activity)
                .setView(scrollView)
                .setPositiveButton("Sulje") { _, _ ->
                    if (isStartup) {
                        onCloseSettings()
                    }
                }

            val shownDialog = dialog.show()
            shownDialog.enlargeButtons()

            if (isStartup) {
                onStartupDialogShown(shownDialog)
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Käyttöohjetta ei voitu ladata", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkShow() {
        val lastVersionCode = settingsStore.lastVersionCode
        val currentVersionCode = BuildConfig.VERSION_CODE

        if (lastVersionCode < currentVersionCode) {
            show(isStartup = true)
            settingsStore.lastVersionCode = currentVersionCode
        }
    }

    private fun scrollToAnchor(widget: TextView, anchor: String) {
        val normalizedAnchor = MarkdownToHtmlConverter.anchorId(anchor)
        val lines = widget.text.toString().split("\n")
        var currentPos = 0

        for (line in lines) {
            if (MarkdownToHtmlConverter.matchesAnchor(line, normalizedAnchor)) {
                val layout = widget.layout ?: break
                val lineNum = layout.getLineForOffset(currentPos)
                val y = layout.getLineTop(lineNum) + widget.totalPaddingTop
                val scrollView = widget.parent as? ScrollView
                scrollView?.smoothScrollTo(0, y)
                break
            }
            currentPos += line.length + 1
        }
    }
}
