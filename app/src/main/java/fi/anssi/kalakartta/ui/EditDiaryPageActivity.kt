package fi.anssi.kalakartta.ui

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishDiaryPage
import fi.anssi.kalakartta.data.MediaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class EditDiaryPageActivity : AppCompatActivity() {
    companion object { const val EXTRA_PAGE_ID = "PAGE_ID"; const val EXTRA_START_DATE = "START_DATE" }
    private lateinit var db: AppDatabase
    private lateinit var startDateText: TextView
    private lateinit var endDateText: TextView
    private lateinit var endDateContainer: View
    private lateinit var multiDayCheckBox: CheckBox
    private lateinit var locationEdit: EditText
    private lateinit var fishingMethodEdit: EditText
    private lateinit var catchEdit: EditText
    private lateinit var storyEdit: EditText
    private lateinit var mediaListLayout: LinearLayout
    private lateinit var addMediaButton: Button
    private lateinit var mediaService: MediaService
    private var page: FishDiaryPage? = null
    private var startDate = 0L
    private var endDate: Long? = null
    private var changed = false
    private val dateFormat = SimpleDateFormat("d.M.yyyy", Locale("fi", "FI"))

    private val selectMediaLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            if (mediaService.addMedia(it, null, null, startDate) != null) refreshMediaList()
            else Toast.makeText(this, "Median lisääminen epäonnistui", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_edit_diary_page)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val editorRoot = findViewById<ScrollView>(R.id.editorRoot)
        val baseBottomPadding = (140 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(editorRoot) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, baseBottomPadding + maxOf(imeBottom, navigationBottom))
            insets
        }
        ViewCompat.requestApplyInsets(editorRoot)
        db = AppDatabase.getInstance(this)
        startDateText = findViewById(R.id.startDateText); endDateText = findViewById(R.id.endDateText); endDateContainer = findViewById(R.id.endDateContainer)
        multiDayCheckBox = findViewById(R.id.multiDayCheckBox); locationEdit = findViewById(R.id.locationEdit); fishingMethodEdit = findViewById(R.id.fishingMethodEdit); catchEdit = findViewById(R.id.catchEdit); storyEdit = findViewById(R.id.storyEdit)
        mediaListLayout = findViewById(R.id.mediaListLayout); addMediaButton = findViewById(R.id.addMediaButton); mediaService = MediaService(this)
        findViewById<TextView>(R.id.saveButton).setOnClickListener { save() }
        findViewById<TextView>(R.id.backButton).setOnClickListener { goBack() }
        addMediaButton.setOnClickListener { selectMediaLauncher.launch("*/*") }
        multiDayCheckBox.setOnCheckedChangeListener { _, checked -> endDateContainer.visibility = if (checked) View.VISIBLE else View.GONE; changed = true; refreshMediaList() }
        startDateText.setOnClickListener { pickDate(startDate) { startDate = it; updateDates(); changed = true; refreshMediaList() } }
        endDateText.setOnClickListener { pickDate(endDate ?: startDate) { endDate = it; updateDates(); changed = true; refreshMediaList() } }
        listOf(locationEdit, fishingMethodEdit, catchEdit, storyEdit).forEach { edit ->
            edit.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    changed = true
                    edit.post { editorRoot.smoothScrollTo(0, edit.bottom) }
                }
            }
        }
        loadPage()
    }

    private fun loadPage() {
        val id = intent.getLongExtra(EXTRA_PAGE_ID, -1L)
        if (id == -1L) {
            startDate = normalize(intent.getLongExtra(EXTRA_START_DATE, System.currentTimeMillis()))
            updateDates()
            refreshMediaList()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) { val loaded = db.fishDiaryPageDao().getById(id); withContext(Dispatchers.Main) {
            if (loaded == null) { finish(); return@withContext }; page = loaded; startDate = loaded.startDate; endDate = loaded.endDate
            locationEdit.setText(loaded.location); fishingMethodEdit.setText(loaded.fishingMethod); catchEdit.setText(loaded.catch); storyEdit.setText(loaded.story); multiDayCheckBox.isChecked = loaded.endDate != null; updateDates(); changed = false
            refreshMediaList()
        } }
    }

    private fun refreshMediaList() {
        val dayStart = normalize(startDate)
        val selectedEnd = if (multiDayCheckBox.isChecked) endDate ?: dayStart else dayStart
        val rangeStart = minOf(dayStart, normalize(selectedEnd))
        val rangeEnd = Calendar.getInstance().apply { timeInMillis = maxOf(dayStart, normalize(selectedEnd)); add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        val media = db.mediaDao().getMediaForDate(rangeStart, rangeEnd)
        MediaComponent.render(this, mediaListLayout, media, { it.latitude == null && it.longitude == null }, onChanged = { refreshMediaList() })
    }

    private fun pickDate(initial: Long, callback: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(this, { _, y, m, d -> callback(normalize(Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }.timeInMillis)) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDates() { startDateText.text = dateFormat.format(Date(startDate)); endDateText.text = endDate?.let { dateFormat.format(Date(it)) } ?: "Valitse..." }

    private fun save() {
        val saved = FishDiaryPage(page?.id ?: 0L, startDate, if (multiDayCheckBox.isChecked) endDate ?: startDate else null, locationEdit.text.toString(), fishingMethodEdit.text.toString(), catchEdit.text.toString(), storyEdit.text.toString())
        lifecycleScope.launch(Dispatchers.IO) { if (saved.id == 0L) db.fishDiaryPageDao().insert(saved) else db.fishDiaryPageDao().update(saved); withContext(Dispatchers.Main) { setResult(RESULT_OK); finish() } }
    }

    private fun goBack() { if (!changed) finish() else AlertDialog.Builder(this).setMessage("Tietoja on muutettu, poistutaanko tallentamatta?").setPositiveButton("Kyllä") { _, _ -> finish() }.setNegativeButton("Ei", null).show() }
    override fun onBackPressed() { goBack() }
    private fun normalize(time: Long): Long { val c = Calendar.getInstance().apply { timeInMillis = time }; c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c.timeInMillis }
}
