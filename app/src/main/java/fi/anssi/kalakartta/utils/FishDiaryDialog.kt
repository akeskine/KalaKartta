package fi.anssi.kalakartta.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.FishDiaryPage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object FishDiaryDialog {
    fun show(context: Context, page: FishDiaryPage) {
        val content = LayoutInflater.from(context).inflate(R.layout.dialog_fish_diary_page, null)
        val dateFormat = SimpleDateFormat("d.M.yyyy", Locale("fi", "FI")).apply {
            timeZone = TimeZone.getTimeZone("Europe/Helsinki")
        }

        content.findViewById<TextView>(R.id.diaryDateValue).text = page.endDate?.let {
            context.getString(
                R.string.fish_diary_date_range,
                dateFormat.format(Date(page.startDate)),
                dateFormat.format(Date(it))
            )
        } ?: dateFormat.format(Date(page.startDate))
        content.findViewById<TextView>(R.id.diaryLocationValue).text = page.location
        content.findViewById<TextView>(R.id.diaryFishingMethodValue).text = page.fishingMethod
        content.findViewById<TextView>(R.id.diaryCatchValue).text = page.catch
        content.findViewById<TextView>(R.id.diaryStoryValue).text = page.story

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.fish_diary_entry_title)
            .setView(content)
            .setPositiveButton(R.string.back, null)
            .create()

        dialog.setOnShowListener {
            val metrics = context.resources.displayMetrics
            val maxWidth = (560 * metrics.density).toInt()
            val width = minOf((metrics.widthPixels * 0.92f).toInt(), maxWidth)
            dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).textSize = 18f
            content.post {
                val maxHeight = (metrics.heightPixels * 0.78f).toInt()
                if (content.height > maxHeight) {
                    content.layoutParams = content.layoutParams.apply { height = maxHeight }
                }
            }
        }
        dialog.show()
    }
}
