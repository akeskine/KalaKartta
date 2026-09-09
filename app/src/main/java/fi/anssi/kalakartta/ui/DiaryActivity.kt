package fi.anssi.kalakartta.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishDiaryPage
import fi.anssi.kalakartta.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DiaryActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var calendarGrid: GridLayout
    private lateinit var monthYearText: TextView
    private lateinit var diaryPagesContainer: LinearLayout
    private lateinit var noPagesText: TextView
    private var allDiaryPages: List<FishDiaryPage> = emptyList()
    private var currentCalendar = Calendar.getInstance()
    private var selectedCalendar = Calendar.getInstance()

    companion object { private const val EDIT_PAGE_REQUEST = 2001 }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") { window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON) }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)
        db = AppDatabase.getInstance(this)
        calendarGrid = findViewById(R.id.calendarGrid)
        monthYearText = findViewById(R.id.monthYearText)
        diaryPagesContainer = findViewById(R.id.diaryPagesContainer)
        noPagesText = findViewById(R.id.noPagesText)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.prevMonthButton).setOnClickListener { changeMonth(-1) }
        findViewById<Button>(R.id.nextMonthButton).setOnClickListener { changeMonth(1) }
        monthYearText.setOnClickListener { MonthYearPickerDialog.show(this, currentCalendar) { y, m -> navigateToMonth(y, m) } }
        findViewById<View>(R.id.addDiaryPageButton).setOnClickListener { openNewPage() }
        loadDiaryPages()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_PAGE_REQUEST && resultCode == RESULT_OK) loadDiaryPages()
    }

    private fun loadDiaryPages() {
        lifecycleScope.launch(Dispatchers.IO) {
            allDiaryPages = db.fishDiaryPageDao().getAll()
            withContext(Dispatchers.Main) { updateCalendar(); selectDate(selectedCalendar) }
        }
    }

    private fun changeMonth(amount: Int) {
        val target = currentCalendar.clone() as Calendar
        target.set(Calendar.DAY_OF_MONTH, 1); target.add(Calendar.MONTH, amount)
        navigateToMonth(target.get(Calendar.YEAR), target.get(Calendar.MONTH))
    }

    private fun navigateToMonth(year: Int, month: Int) {
        val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)
        val target = selectedCalendar.clone() as Calendar
        target.set(Calendar.DAY_OF_MONTH, 1); target.set(Calendar.YEAR, year); target.set(Calendar.MONTH, month)
        target.set(Calendar.DAY_OF_MONTH, minOf(day, target.getActualMaximum(Calendar.DAY_OF_MONTH)))
        currentCalendar.set(Calendar.DAY_OF_MONTH, 1); currentCalendar.set(Calendar.YEAR, year); currentCalendar.set(Calendar.MONTH, month)
        selectDate(target)
    }

    private fun selectDate(cal: Calendar) {
        selectedCalendar = normalizeCalendar(cal.clone() as Calendar)
        updateCalendar(); updateDiaryPageList()
    }

    private fun updateDiaryPageList() {
        diaryPagesContainer.removeAllViews()
        val pages = allDiaryPages.filter { pageCoversDate(it, selectedCalendar) }.sortedBy { it.startDate }
        val media = mediaForDate(selectedCalendar)
        noPagesText.visibility = if (pages.isEmpty()) View.VISIBLE else View.GONE
        pages.forEachIndexed { index, page -> addDiaryPageItem(page, index + 1, media) }
    }

    private fun addDiaryPageItem(page: FishDiaryPage, pageNumber: Int, media: List<Media>) {
        val item = LayoutInflater.from(this).inflate(R.layout.item_diary_page, diaryPagesContainer, false)
        val details = item.findViewById<TextView>(R.id.diaryPageDetails)
        val expandIcon = item.findViewById<ImageView>(R.id.expandDiaryPageIcon)
        val mediaLayout = item.findViewById<LinearLayout>(R.id.mediaListLayout)
        val location = page.location.trim().ifBlank { "Ei paikkaa" }
        item.findViewById<TextView>(R.id.diaryPageTitle).text = "Päiväkirjasivu $pageNumber: $location"
        details.text = buildPageDetails(page); details.visibility = View.GONE
        MediaComponent.render(this, mediaLayout, media, { false }, showFileName = false)
        item.setOnClickListener {
            details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            expandIcon.rotation = if (details.visibility == View.VISIBLE) 180f else 0f
            mediaLayout.visibility = details.visibility
        }
        item.findViewById<ImageView>(R.id.editDiaryPageButton).setOnClickListener { showPageMenu(it, page) }
        diaryPagesContainer.addView(item)
    }

    private fun buildPageDetails(page: FishDiaryPage): CharSequence {
        val fmt = SimpleDateFormat("d.M.yyyy", Locale("fi", "FI"))
        val dates = if (page.endDate != null) "${fmt.format(Date(page.startDate))}–${fmt.format(Date(page.endDate))}" else fmt.format(Date(page.startDate))
        val result = SpannableStringBuilder()
        appendBoldLine(result, "Päivä: ", dates)
        appendBoldLine(result, "Paikka: ", page.location.ifBlank { "-" })
        appendBoldLine(result, "Kalastustapa: ", page.fishingMethod.ifBlank { "-" })
        appendBoldLine(result, "Saalis: ", page.catch.ifBlank { "-" })
        result.append("\n")
        appendBoldLine(result, "Kertomus:\n", page.story.ifBlank { "Ei kertomusta." })
        return result
    }

    private fun mediaForDate(date: Calendar): List<Media> {
        val start = normalizeCalendar(date.clone() as Calendar)
        val end = start.clone() as Calendar
        end.add(Calendar.DAY_OF_MONTH, 1)
        return db.mediaDao().getMediaForDate(start.timeInMillis, end.timeInMillis)
    }

    private fun appendBoldLine(result: SpannableStringBuilder, label: String, value: String) {
        val start = result.length
        result.append(label)
        result.setSpan(StyleSpan(Typeface.BOLD), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        result.append(value).append("\n")
    }

    private fun showPageMenu(anchor: View, page: FishDiaryPage) {
        PopupMenu(this, anchor).apply {
            menu.add("Muokkaa"); menu.add("Poista")
            setOnMenuItemClickListener { when (it.title) {
                "Muokkaa" -> { openEditPage(page.id); true }
                "Poista" -> { confirmDelete(page); true }
                else -> false
            } }
            show()
        }
    }

    private fun confirmDelete(page: FishDiaryPage) {
        AlertDialog.Builder(this).setTitle("Poistetaanko päiväkirjasivu?")
            .setMessage("Haluatko varmasti poistaa tämän päiväkirjasivun?")
            .setPositiveButton("Poista") { _, _ -> lifecycleScope.launch(Dispatchers.IO) {
                db.fishDiaryPageDao().delete(page); allDiaryPages = db.fishDiaryPageDao().getAll()
                withContext(Dispatchers.Main) { updateCalendar(); updateDiaryPageList() }
            } }.setNegativeButton("Peruuta", null).show()
    }

    private fun openNewPage() {
        startActivityForResult(Intent(this, EditDiaryPageActivity::class.java).putExtra(EditDiaryPageActivity.EXTRA_START_DATE, selectedCalendar.timeInMillis), EDIT_PAGE_REQUEST)
    }

    private fun openEditPage(id: Long) {
        startActivityForResult(Intent(this, EditDiaryPageActivity::class.java).putExtra(EditDiaryPageActivity.EXTRA_PAGE_ID, id), EDIT_PAGE_REQUEST)
    }

    private fun updateCalendar() {
        calendarGrid.removeAllViews()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("fi", "FI"))
        monthYearText.text = sdf.format(currentCalendar.time).replaceFirstChar { it.uppercase() }
        val cal = currentCalendar.clone() as Calendar; cal.set(Calendar.DAY_OF_MONTH, 1)
        var first = cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY; if (first < 0) first += 7
        listOf("ma", "ti", "ke", "to", "pe", "la", "su").forEach { name ->
            calendarGrid.addView(TextView(this).apply { text = name; gravity = android.view.Gravity.CENTER; setPadding(0, 10, 0, 10); textSize = 12f }, GridLayout.LayoutParams().apply { width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
        }
        repeat(first) { calendarGrid.addView(View(this), GridLayout.LayoutParams().apply { width = 0; height = 1; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }) }
        val inflater = LayoutInflater.from(this)
        for (day in 1..cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            val dayView = inflater.inflate(R.layout.item_calendar_day, calendarGrid, false)
            dayView.findViewById<TextView>(R.id.dayText).text = day.toString()
            val dayCal = cal.clone() as Calendar; dayCal.set(Calendar.DAY_OF_MONTH, day)
            if (allDiaryPages.any { pageCoversDate(it, dayCal) }) dayView.findViewById<View>(R.id.sessionIndicator).visibility = View.VISIBLE
            if (isSameDay(dayCal, selectedCalendar)) { val value = android.util.TypedValue(); theme.resolveAttribute(android.R.attr.colorControlHighlight, value, true); dayView.setBackgroundColor(value.data) }
            dayView.setOnClickListener { selectDate(dayCal) }
            calendarGrid.addView(dayView, GridLayout.LayoutParams().apply { width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
        }
    }

    private fun pageCoversDate(page: FishDiaryPage, date: Calendar): Boolean {
        val start = normalizeCalendar(Calendar.getInstance().apply { timeInMillis = page.startDate }); val selected = normalizeCalendar(date.clone() as Calendar)
        if (page.endDate == null) return isSameDay(start, selected)
        val end = normalizeCalendar(Calendar.getInstance().apply { timeInMillis = page.endDate })
        return !selected.before(start) && !selected.after(end)
    }

    private fun isSameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun normalizeCalendar(cal: Calendar): Calendar { cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0); return cal }
}
