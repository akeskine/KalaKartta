package fi.anssi.kalakartta.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishDiaryPage
import fi.anssi.kalakartta.data.FishDiarySearchDictionary
import fi.anssi.kalakartta.data.Media
import fi.anssi.kalakartta.utils.FishDiaryPageSearchQuery
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
    private lateinit var calendarNavigation: View
    private lateinit var diaryPagesLabel: TextView
    private lateinit var diaryScrollView: ScrollView
    private lateinit var searchEdit: EditText
    private lateinit var searchButton: Button
    private lateinit var clearSearchButton: Button
    private lateinit var searchResultCountText: TextView
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var loadMoreSearchButton: Button
    private lateinit var searchAdapter: SearchResultAdapter
    private var allDiaryPages: List<FishDiaryPage> = emptyList()
    private var currentCalendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))
    private var selectedCalendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"))
    private val timeZone = TimeZone.getTimeZone("Europe/Helsinki")
    private var searchQuery = ""
    private var searchOffset = 0
    private var searchTotalCount = 0
    private var searchRequestId = 0L
    private var isSearchMode = false

    companion object {
        private const val EDIT_PAGE_REQUEST = 2001
        private const val SEARCH_QUERY_STATE = "diary_search_query"
        private const val SEARCH_PAGE_SIZE = 50
    }

    private val editPageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) loadDiaryPages()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        lockToCurrentOrientation()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") { window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON) }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        diaryScrollView = findViewById(R.id.diaryScrollView)
        val baseBottomPadding = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(diaryScrollView) { view, insets ->
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, baseBottomPadding + navigationBottom)
            insets
        }
        ViewCompat.requestApplyInsets(diaryScrollView)
        db = AppDatabase.getInstance(this)
        calendarGrid = findViewById(R.id.calendarGrid)
        monthYearText = findViewById(R.id.monthYearText)
        diaryPagesContainer = findViewById(R.id.diaryPagesContainer)
        noPagesText = findViewById(R.id.noPagesText)
        calendarNavigation = findViewById(R.id.calendarNavigation)
        diaryPagesLabel = findViewById(R.id.diaryPagesLabel)
        searchEdit = findViewById(R.id.diarySearchEdit)
        searchButton = findViewById(R.id.diarySearchButton)
        clearSearchButton = findViewById(R.id.clearDiarySearchButton)
        searchResultCountText = findViewById(R.id.searchResultCountText)
        searchProgress = findViewById(R.id.searchProgress)
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
        loadMoreSearchButton = findViewById(R.id.loadMoreSearchButton)
        searchAdapter = SearchResultAdapter()
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        searchResultsRecyclerView.adapter = searchAdapter
        findViewById<View>(R.id.backButton).setOnClickListener { finishToSettings() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishToMap()
            }
        })
        findViewById<Button>(R.id.prevMonthButton).setOnClickListener { changeMonth(-1) }
        findViewById<Button>(R.id.nextMonthButton).setOnClickListener { changeMonth(1) }
        monthYearText.setOnClickListener { MonthYearPickerDialog.show(this, currentCalendar) { y, m -> navigateToMonth(y, m) } }
        findViewById<View>(R.id.addDiaryPageButton).setOnClickListener { openNewPage() }
        searchButton.setOnClickListener { performSearch(searchEdit.text.toString()) }
        clearSearchButton.setOnClickListener { searchEdit.text.clear() }
        searchEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchEdit.text.toString())
                true
            } else {
                false
            }
        }
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                clearSearchButton.visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE
                if (isSearchMode && s.isNullOrBlank()) clearSearchMode()
            }
        })
        loadMoreSearchButton.setOnClickListener { loadMoreSearchResults() }

        val restoredSearchQuery = savedInstanceState?.getString(SEARCH_QUERY_STATE).orEmpty()
        if (restoredSearchQuery.isBlank()) {
            loadDiaryPages()
        } else {
            searchEdit.setText(restoredSearchQuery)
            performSearch(restoredSearchQuery)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SEARCH_QUERY_STATE, if (isSearchMode) searchQuery else "")
        super.onSaveInstanceState(outState)
    }

    private fun loadDiaryPages() {
        if (isSearchMode && searchQuery.isNotBlank()) {
            performSearch(searchQuery)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            allDiaryPages = db.fishDiaryPageDao().getAll()
            withContext(Dispatchers.Main) { updateCalendar(); selectDate(selectedCalendar) }
        }
    }

    private fun performSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            clearSearchMode()
            return
        }

        searchQuery = query
        searchOffset = 0
        searchTotalCount = 0
        searchRequestId++
        isSearchMode = true
        searchAdapter.replace(emptyList())
        setSearchModeViews(true)
        searchProgress.visibility = View.VISIBLE
        searchResultCountText.text = "Haetaan..."
        loadSearchPage(append = false, requestId = searchRequestId)
    }

    private fun clearSearchMode() {
        searchRequestId++
        searchQuery = ""
        searchOffset = 0
        searchTotalCount = 0
        isSearchMode = false
        searchAdapter.replace(emptyList())
        setSearchModeViews(false)
        updateCalendar()
        updateDiaryPageList()
    }

    private fun setSearchModeViews(searchMode: Boolean) {
        calendarNavigation.visibility = if (searchMode) View.GONE else View.VISIBLE
        calendarGrid.visibility = if (searchMode) View.GONE else View.VISIBLE
        diaryPagesLabel.text = if (searchMode) "Hakutulokset:" else "Päiväkirjasivut:"
        diaryScrollView.visibility = if (searchMode) View.GONE else View.VISIBLE
        searchResultsRecyclerView.visibility = if (searchMode) View.VISIBLE else View.GONE
        loadMoreSearchButton.visibility = View.GONE
        searchResultCountText.visibility = if (searchMode) View.VISIBLE else View.GONE
        searchProgress.visibility = View.GONE
        noPagesText.visibility = View.GONE
        if (!searchMode) noPagesText.text = "Ei päiväkirjasivuja tälle päivälle."
    }

    private fun loadMoreSearchResults() {
        if (!isSearchMode || searchProgress.visibility == View.VISIBLE || searchOffset >= searchTotalCount) return
        searchRequestId++
        loadSearchPage(append = true, requestId = searchRequestId)
    }

    private fun loadSearchPage(append: Boolean, requestId: Long) {
        val querySnapshot = searchQuery
        val offset = if (append) searchOffset else 0
        searchProgress.visibility = View.VISIBLE
        loadMoreSearchButton.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val dictionary = FishDiarySearchDictionary.load(this@DiaryActivity)
                    val criteria = FishDiaryPageSearchQuery.parse(querySnapshot, dictionary)
                    val dao = db.fishDiaryPageDao()
                    val totalCount = if (append) null else dao.count(FishDiaryPageSearchQuery.countQuery(criteria))
                    val pages = dao.search(FishDiaryPageSearchQuery.searchQuery(criteria, SEARCH_PAGE_SIZE, offset))
                    SearchPageResult(totalCount, pages)
                }

                if (requestId != searchRequestId || !isSearchMode || querySnapshot != searchQuery) return@launch
                if (append) searchAdapter.append(result.pages) else searchAdapter.replace(result.pages)
                result.totalCount?.let { searchTotalCount = it }
                searchOffset += result.pages.size
                searchProgress.visibility = View.GONE
                searchResultCountText.text = "$searchTotalCount hakutulosta"
                noPagesText.text = "Hakusanalla ei löytynyt päiväkirjasivuja."
                noPagesText.visibility = if (searchOffset == 0) View.VISIBLE else View.GONE
                loadMoreSearchButton.visibility = if (searchOffset < searchTotalCount) View.VISIBLE else View.GONE
            } catch (_: Exception) {
                if (requestId != searchRequestId || !isSearchMode || querySnapshot != searchQuery) return@launch
                searchProgress.visibility = View.GONE
                searchResultCountText.text = "Haun suorittaminen epäonnistui."
                noPagesText.text = "Hakua ei voitu suorittaa."
                noPagesText.visibility = View.VISIBLE
            }
        }
    }

    private data class SearchPageResult(
        val totalCount: Int?,
        val pages: List<FishDiaryPage>
    )

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
        val selectedDate = selectedCalendar.clone() as Calendar
        val pages = allDiaryPages.filter { pageCoversDate(it, selectedDate) }.sortedBy { it.startDate }
        lifecycleScope.launch(Dispatchers.IO) {
            val media = mediaForDate(selectedDate)
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                diaryPagesContainer.removeAllViews()
                noPagesText.visibility = if (pages.isEmpty()) View.VISIBLE else View.GONE
                pages.forEachIndexed { index, page -> addDiaryPageItem(page, index + 1, media) }
            }
        }
    }

    private fun addDiaryPageItem(page: FishDiaryPage, pageNumber: Int, media: List<Media>) {
        val item = LayoutInflater.from(this).inflate(R.layout.item_diary_page, diaryPagesContainer, false)
        val details = item.findViewById<TextView>(R.id.diaryPageDetails)
        val expandIcon = item.findViewById<ImageView>(R.id.expandDiaryPageIcon)
        val mediaLayout = item.findViewById<LinearLayout>(R.id.mediaListLayout)
        val location = page.location.trim().ifBlank { "Ei paikkaa" }
        item.findViewById<TextView>(R.id.diaryPageTitle).text = "Päiväkirjasivu $pageNumber: ${formatPageDates(page)} – $location"
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
        val dates = formatPageDates(page)
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
                db.fishDiaryPageDao().delete(page)
                withContext(Dispatchers.Main) {
                    if (isSearchMode) {
                        performSearch(searchQuery)
                    } else {
                        allDiaryPages = db.fishDiaryPageDao().getAll()
                        updateCalendar()
                        updateDiaryPageList()
                    }
                }
            } }.setNegativeButton("Peruuta", null).show()
    }

    private fun openNewPage() {
        editPageLauncher.launch(Intent(this, EditDiaryPageActivity::class.java).putExtra(EditDiaryPageActivity.EXTRA_START_DATE, selectedCalendar.timeInMillis))
    }

    private fun openEditPage(id: Long) {
        editPageLauncher.launch(Intent(this, EditDiaryPageActivity::class.java).putExtra(EditDiaryPageActivity.EXTRA_PAGE_ID, id))
    }

    private fun finishToSettings() {
        setResult(RESULT_OK, Intent().putExtra("BACK_TO_SETTINGS", true))
        finish()
    }

    private fun finishToMap() {
        setResult(RESULT_OK)
        finish()
    }

    private fun updateCalendar() {
        calendarGrid.removeAllViews()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("fi", "FI")).apply { timeZone = this@DiaryActivity.timeZone }
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
        val start = normalizeCalendar(Calendar.getInstance(timeZone).apply { timeInMillis = page.startDate }); val selected = normalizeCalendar(date.clone() as Calendar)
        if (page.endDate == null) return isSameDay(start, selected)
        val end = normalizeCalendar(Calendar.getInstance(timeZone).apply { timeInMillis = page.endDate })
        return !selected.before(start) && !selected.after(end)
    }

    private fun isSameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun formatPageDates(page: FishDiaryPage): String {
        fun format(time: Long): String = SimpleDateFormat("d.M.yyyy", Locale("fi", "FI")).apply {
            timeZone = this@DiaryActivity.timeZone
        }.format(Date(time))
        return page.endDate?.let { "${format(page.startDate)}–${format(it)}" } ?: format(page.startDate)
    }

    private fun normalizeCalendar(cal: Calendar): Calendar {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private inner class SearchResultAdapter : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {
        private val pages = mutableListOf<FishDiaryPage>()

        fun replace(newPages: List<FishDiaryPage>) {
            pages.clear()
            pages.addAll(newPages)
            notifyDataSetChanged()
        }

        fun append(newPages: List<FishDiaryPage>) {
            if (newPages.isEmpty()) return
            val start = pages.size
            pages.addAll(newPages)
            notifyItemRangeInserted(start, newPages.size)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_diary_page, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val page = pages[position]
            val location = page.location.trim().ifBlank { "Ei paikkaa" }
            holder.title.text = "Päiväkirjasivu ${position + 1}: ${formatPageDates(page)} – $location"
            holder.details.text = buildPageDetails(page)
            holder.details.visibility = View.GONE
            holder.expandIcon.rotation = 0f
            holder.mediaLayout.removeAllViews()
            holder.mediaLayout.visibility = View.GONE
            holder.itemView.setOnClickListener {
                holder.details.visibility = if (holder.details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                holder.expandIcon.rotation = if (holder.details.visibility == View.VISIBLE) 180f else 0f
            }
            holder.editButton.setOnClickListener { showPageMenu(it, page) }
        }

        override fun getItemCount(): Int = pages.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.diaryPageTitle)
            val details: TextView = view.findViewById(R.id.diaryPageDetails)
            val expandIcon: ImageView = view.findViewById(R.id.expandDiaryPageIcon)
            val mediaLayout: LinearLayout = view.findViewById(R.id.mediaListLayout)
            val editButton: ImageView = view.findViewById(R.id.editDiaryPageButton)
        }
    }
}
