package fi.anssi.kalakartta.ui

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DiaryActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var calendarGrid: GridLayout
    private lateinit var monthYearText: TextView
    private lateinit var multiDayCheckBox: CheckBox
    private lateinit var singleDayContainer: View
    private lateinit var multiDayContainer: View
    private lateinit var startDateText: TextView
    private lateinit var multiStartDateText: TextView
    private lateinit var endDateText: TextView
    private lateinit var locationEdit: EditText
    private lateinit var fishingMethodEdit: EditText
    private lateinit var catchEdit: EditText
    private lateinit var storyEdit: EditText
    private lateinit var saveButton: View
    private lateinit var deleteButton: View

    private var allDiaryPages: List<FishDiaryPage> = emptyList()
    private var currentCalendar = Calendar.getInstance()
    private var selectedCalendar = Calendar.getInstance()
    private var currentDiaryPage: FishDiaryPage? = null
    
    private var hasChanges = false
    private val sdfDate = SimpleDateFormat("d.M.yyyy", Locale("fi", "FI"))

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)

        db = AppDatabase.getInstance(this)
        
        initViews()
        setupListeners()
        loadDiaryPages()
    }

    private fun initViews() {
        val grid: GridLayout = findViewById(R.id.calendarGrid)
        calendarGrid = grid
        val mYText: TextView = findViewById(R.id.monthYearText)
        monthYearText = mYText
        val multiDayCB: CheckBox = findViewById(R.id.multiDayCheckBox)
        multiDayCheckBox = multiDayCB
        val sDayCont: View = findViewById(R.id.singleDayContainer)
        singleDayContainer = sDayCont
        val mDayCont: View = findViewById(R.id.multiDayContainer)
        multiDayContainer = mDayCont
        val sDateText: TextView = findViewById(R.id.startDateText)
        startDateText = sDateText
        val mStartDateText: TextView = findViewById(R.id.multiStartDateText)
        multiStartDateText = mStartDateText
        val eDateText: TextView = findViewById(R.id.endDateText)
        endDateText = eDateText
        val locEdit: EditText = findViewById(R.id.locationEdit)
        locationEdit = locEdit
        val fMethodEdit: EditText = findViewById(R.id.fishingMethodEdit)
        fishingMethodEdit = fMethodEdit
        val cEdit: EditText = findViewById(R.id.catchEdit)
        catchEdit = cEdit
        val sEdit: EditText = findViewById(R.id.storyEdit)
        storyEdit = sEdit
        val sButton: View = findViewById(R.id.saveButton)
        saveButton = sButton
        val dButton: View = findViewById(R.id.deleteButton)
        deleteButton = dButton
        
        updateDateTexts()
    }

    private fun setupListeners() {
        findViewById<View>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        findViewById<Button>(R.id.prevMonthButton).setOnClickListener {
            changeMonth(-1)
        }

        findViewById<Button>(R.id.nextMonthButton).setOnClickListener {
            changeMonth(1)
        }

        monthYearText.setOnClickListener {
            MonthYearPickerDialog.show(this, currentCalendar) { year, month ->
                navigateToMonth(year, month)
            }
        }

        multiDayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            singleDayContainer.visibility = if (isChecked) View.GONE else View.VISIBLE
            multiDayContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                currentDiaryPage = currentDiaryPage?.copy(endDate = null)
                updateDateTexts()
            }
            markChanged()
        }

        startDateText.setOnClickListener { showDatePicker(selectedCalendar) { cal -> 
            selectedCalendar = cal
            updateDateTexts()
            markChanged()
        } }
        
        multiStartDateText.setOnClickListener { showDatePicker(selectedCalendar) { cal -> 
            selectedCalendar = cal
            updateDateTexts()
            markChanged()
        } }
        
        endDateText.setOnClickListener {
            val endCal = Calendar.getInstance()
            currentDiaryPage?.endDate?.let { endCal.timeInMillis = it }
            
            val dialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (currentDiaryPage == null) {
                    currentDiaryPage = FishDiaryPage(
                        startDate = normalizeToStartOfDay(selectedCalendar.timeInMillis),
                        endDate = cal.timeInMillis,
                        location = locationEdit.text.toString(),
                        fishingMethod = fishingMethodEdit.text.toString(),
                        catch = catchEdit.text.toString(),
                        story = storyEdit.text.toString()
                    )
                } else {
                    currentDiaryPage = currentDiaryPage?.copy(endDate = cal.timeInMillis)
                }
                updateDateTexts()
                markChanged()
            }, endCal.get(Calendar.YEAR), endCal.get(Calendar.MONTH), endCal.get(Calendar.DAY_OF_MONTH))
            
            dialog.setButton(DatePickerDialog.BUTTON_NEGATIVE, "Peruuta") { _, _ -> }
            dialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Tyhjennä") { _, _ ->
                currentDiaryPage = currentDiaryPage?.copy(endDate = null)
                updateDateTexts()
                markChanged()
            }
            dialog.show()
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { markChanged() }
            override fun afterTextChanged(s: Editable?) {}
        }
        locationEdit.addTextChangedListener(watcher)
        fishingMethodEdit.addTextChangedListener(watcher)
        catchEdit.addTextChangedListener(watcher)
        storyEdit.addTextChangedListener(watcher)

        saveButton.setOnClickListener { saveDiaryPage() }
        deleteButton.setOnClickListener { confirmDelete() }
    }

    private fun changeMonth(amount: Int) {
        val target = currentCalendar.clone() as Calendar
        target.set(Calendar.DAY_OF_MONTH, 1)
        target.add(Calendar.MONTH, amount)
        navigateToMonth(target.get(Calendar.YEAR), target.get(Calendar.MONTH))
    }

    private fun navigateToMonth(year: Int, month: Int) {
        if (hasChanges) {
            AlertDialog.Builder(this)
                .setMessage("Kalapäiväkirjan tietoja on muutettu, haluatko varmasti vaihtaa kuukautta tallentamatta?")
                .setPositiveButton("Kyllä") { _, _ -> applyMonth(year, month) }
                .setNegativeButton("Ei", null)
                .show()
        } else {
            applyMonth(year, month)
        }
    }

    private fun applyMonth(year: Int, month: Int) {
        val selectedDay = selectedCalendar.get(Calendar.DAY_OF_MONTH)
        val targetSelected = selectedCalendar.clone() as Calendar
        targetSelected.set(Calendar.DAY_OF_MONTH, 1)
        targetSelected.set(Calendar.YEAR, year)
        targetSelected.set(Calendar.MONTH, month)
        targetSelected.set(
            Calendar.DAY_OF_MONTH,
            minOf(selectedDay, targetSelected.getActualMaximum(Calendar.DAY_OF_MONTH))
        )

        currentCalendar.set(Calendar.DAY_OF_MONTH, 1)
        currentCalendar.set(Calendar.YEAR, year)
        currentCalendar.set(Calendar.MONTH, month)
        selectDate(targetSelected)
    }

    private fun markChanged() {
        hasChanges = true
    }

    private fun updateDateTexts() {
        val dateStr = sdfDate.format(selectedCalendar.time)
        startDateText.text = dateStr
        multiStartDateText.text = dateStr
        
        val endTs = currentDiaryPage?.endDate
        if (endTs != null) {
            endDateText.text = sdfDate.format(Date(endTs))
        } else {
            endDateText.text = "Valitse..."
        }
    }

    private fun showDatePicker(initialCal: Calendar = selectedCalendar, onDateSelected: (Calendar) -> Unit) {
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onDateSelected(cal)
        }, initialCal.get(Calendar.YEAR), initialCal.get(Calendar.MONTH), initialCal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadDiaryPages() {
        lifecycleScope.launch(Dispatchers.IO) {
            allDiaryPages = db.fishDiaryPageDao().getAll()
            withContext(Dispatchers.Main) {
                updateCalendar()
                selectDate(selectedCalendar)
            }
        }
    }

    private fun updateCalendar() {
        calendarGrid.removeAllViews()
        
        val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale("fi", "FI"))
        monthYearText.text = sdfMonth.format(currentCalendar.time).replaceFirstChar { it.uppercase() }

        val cal = currentCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        
        var firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
        if (firstDayOfWeek < 0) firstDayOfWeek += 7

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val daysOfWeek = listOf("ma", "ti", "ke", "to", "pe", "la", "su")
        daysOfWeek.forEach { dayName ->
            val tv = TextView(this).apply {
                text = dayName
                gravity = android.view.Gravity.CENTER
                setPadding(0, 10, 0, 10)
                textSize = 12f
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            calendarGrid.addView(tv, params)
        }

        for (i in 0 until firstDayOfWeek) {
            val emptyView = View(this)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = 1
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            calendarGrid.addView(emptyView, params)
        }

        val inflater = LayoutInflater.from(this)
        for (day in 1..daysInMonth) {
            val dayView = inflater.inflate(R.layout.item_calendar_day, calendarGrid, false)
            val dayText = dayView.findViewById<TextView>(R.id.dayText)
            val indicator = dayView.findViewById<View>(R.id.sessionIndicator)
            
            dayText.text = day.toString()

            val dayCal = cal.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, day)
            
            val hasDiary = allDiaryPages.any {
                val sCal = normalizeCalendar(Calendar.getInstance().apply { timeInMillis = it.startDate })
                val eTs = it.endDate
                
                if (eTs == null) {
                    isSameDay(sCal, dayCal)
                } else {
                    val eCal = normalizeCalendar(Calendar.getInstance().apply { timeInMillis = eTs })
                    // Tarkistetaan onko dayCal sCal:n ja eCal:n välissä (mukaan lukien ne)
                    val dCalNorm = normalizeCalendar(dayCal.clone() as Calendar)
                    !dCalNorm.before(sCal) && !dCalNorm.after(eCal)
                }
            }
            
            if (hasDiary) {
                indicator.visibility = View.VISIBLE
            }

            if (isSameDay(dayCal, selectedCalendar)) {
                val outValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.colorControlHighlight, outValue, true)
                dayView.setBackgroundColor(outValue.data)
            }

            dayView.setOnClickListener {
                if (hasChanges) {
                    AlertDialog.Builder(this)
                        .setMessage("Kalapäiväkirjan tietoja on muutettu, haluatko varmasti poistua tallentamatta?")
                        .setPositiveButton("Kyllä") { _, _ ->
                            selectDate(dayCal)
                        }
                        .setNegativeButton("Ei", null)
                        .show()
                } else {
                    selectDate(dayCal)
                }
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            calendarGrid.addView(dayView, params)
        }
    }

    private fun selectDate(cal: Calendar) {
        selectedCalendar = normalizeCalendar(cal.clone() as Calendar)
        updateCalendar()
        
        // Etsitään päiväkirjasivu
        val page = allDiaryPages.firstOrNull {
            val sCal = normalizeCalendar(Calendar.getInstance().apply { timeInMillis = it.startDate })
            val eTs = it.endDate
            val selCal = normalizeCalendar(selectedCalendar.clone() as Calendar)
            
            if (eTs == null) {
                isSameDay(sCal, selCal)
            } else {
                val eCal = normalizeCalendar(Calendar.getInstance().apply { timeInMillis = eTs })
                !selCal.before(sCal) && !selCal.after(eCal)
            }
        }
        
        currentDiaryPage = page
        fillForm(page)
        hasChanges = false
    }

    private fun fillForm(page: FishDiaryPage?) {
        if (page != null) {
            multiDayCheckBox.isChecked = page.endDate != null
            locationEdit.setText(page.location)
            fishingMethodEdit.setText(page.fishingMethod)
            catchEdit.setText(page.catch)
            storyEdit.setText(page.story)
            deleteButton.visibility = View.VISIBLE
        } else {
            multiDayCheckBox.isChecked = false
            locationEdit.setText("")
            fishingMethodEdit.setText("")
            catchEdit.setText("")
            storyEdit.setText("")
            deleteButton.visibility = View.GONE
        }
        updateDateTexts()
    }

    private fun saveDiaryPage() {
        val startDate = normalizeToStartOfDay(selectedCalendar.timeInMillis)
        val endDate = if (multiDayCheckBox.isChecked) {
            // Jos loppupäivää ei ole asetettu, käytetään alkupäivää? 
            // Tehtävänannossa sanottiin: Jos "Usean päivän merkintä" on ruksittu, näytetään kentät "Alkupäivämäärä" ja "Loppupäivämäärä"
            currentDiaryPage?.endDate ?: startDate
        } else null
        
        val newPage = FishDiaryPage(
            id = currentDiaryPage?.id ?: 0,
            startDate = startDate,
            endDate = endDate,
            location = locationEdit.text.toString(),
            fishingMethod = fishingMethodEdit.text.toString(),
            catch = catchEdit.text.toString(),
            story = storyEdit.text.toString()
        )

        lifecycleScope.launch(Dispatchers.IO) {
            if (newPage.id == 0L) {
                db.fishDiaryPageDao().insert(newPage)
            } else {
                db.fishDiaryPageDao().update(newPage)
            }
            allDiaryPages = db.fishDiaryPageDao().getAll()
            withContext(Dispatchers.Main) {
                hasChanges = false
                currentDiaryPage = allDiaryPages.find { it.startDate == newPage.startDate }
                updateCalendar()
                deleteButton.visibility = View.VISIBLE
                Toast.makeText(this@DiaryActivity, "Tallennettu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete() {
        val page = currentDiaryPage ?: return
        AlertDialog.Builder(this)
            .setTitle("Poistetaanko merkintä?")
            .setMessage("Haluatko varmasti poistaa tämän päiväkirjamerkinnän?")
            .setPositiveButton("Poista") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.fishDiaryPageDao().delete(page)
                    allDiaryPages = db.fishDiaryPageDao().getAll()
                    withContext(Dispatchers.Main) {
                        selectDate(selectedCalendar)
                        Toast.makeText(this@DiaryActivity, "Poistettu", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Peruuta", null)
            .show()
    }

    override fun onBackPressed() {
        if (hasChanges) {
            AlertDialog.Builder(this)
                .setMessage("Kalapäiväkirjan tietoja on muutettu, haluatko varmasti poistua tallentamatta?")
                .setPositiveButton("Kyllä") { _, _ ->
                    super.onBackPressed()
                }
                .setNegativeButton("Ei", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    private fun normalizeToStartOfDay(timeInMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeInMillis
        return normalizeCalendar(cal).timeInMillis
    }

    private fun normalizeCalendar(cal: Calendar): Calendar {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }
}
