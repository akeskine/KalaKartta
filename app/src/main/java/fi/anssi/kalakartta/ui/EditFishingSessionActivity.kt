package fi.anssi.kalakartta.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishingSession
import fi.anssi.kalakartta.utils.enlargeButtons
import fi.anssi.kalakartta.utils.formatFishermanName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.SeekBar
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class EditFishingSessionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var sessionId: Long = -1L
    private var session: FishingSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
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
        } catch (e: Exception) {
            // Ignored
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_fishing_session)

        db = AppDatabase.getInstance(this)
        sessionId = intent.getLongExtra("SESSION_ID", -1L)

        if (sessionId == -1L) {
            finish()
            return
        }

        findViewById<TextView>(R.id.saveLink).setOnClickListener { saveAndFinish() }
        findViewById<TextView>(R.id.trimLink).setOnClickListener { showTrimDialog() }
        findViewById<TextView>(R.id.backLink).setOnClickListener {
            if (hasUnsavedChanges()) {
                showUnsavedChangesDialog()
            } else {
                finish()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    showUnsavedChangesDialog()
                } else {
                    finish()
                }
            }
        })

        loadSessionData()
    }

    private fun hasUnsavedChanges(): Boolean {
        val s = session ?: return false
        val currentFisherman = findViewById<EditText>(R.id.fishermanInput).text.toString().trim()
        val currentNotes = findViewById<EditText>(R.id.notesInput).text.toString()

        // Huom: session.fisherman on tallennettu UPPERCASE, mutta näytetään formatFishermanName-muodossa.
        // Verrataan kuitenkin trimmatusti ja case-insensitive, kuten EditCatchActivityssa.
        if (!currentFisherman.equals(s.fisherman.trim(), ignoreCase = true)) return true
        if (currentNotes != s.notes) return true

        return false
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this).setMessage(R.string.unsaved_changes_warning)
            .setPositiveButton(R.string.discard) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null).show().enlargeButtons()
    }

    private fun loadSessionData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val s = db.fishingSessionDao().getById(sessionId)
            val points = db.trackPointDao().getPointsForSession(sessionId)
            
            withContext(Dispatchers.Main) {
                if (s == null) {
                    finish()
                    return@withContext
                }
                session = s
                
                // Otsikko
                val dateFmt = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                
                val startCal = Calendar.getInstance().apply { timeInMillis = s.startedAt }
                val endCal = s.endedAt?.let { Calendar.getInstance().apply { timeInMillis = it } }
                
                val title = if (endCal == null || isSameDay(startCal, endCal)) {
                    val dateStr = dateFmt.format(Date(s.startedAt))
                    val startTimeStr = timeFmt.format(Date(s.startedAt))
                    val endTimeStr = s.endedAt?.let { timeFmt.format(Date(it)) } ?: "?"
                    "Kalastussessio $dateStr: $startTimeStr - $endTimeStr"
                } else {
                    val startDateStr = dateFmt.format(Date(s.startedAt))
                    val startTimeStr = timeFmt.format(Date(s.startedAt))
                    val endDateStr = dateFmt.format(Date(s.endedAt!!))
                    val endTimeStr = timeFmt.format(Date(s.endedAt))
                    "Kalastussessio: $startDateStr $startTimeStr - $endDateStr $endTimeStr"
                }
                findViewById<TextView>(R.id.sessionTitleText).text = title
                
                // Statsit
                val duration = (s.endedAt ?: s.startedAt) - s.startedAt
                val hours = TimeUnit.MILLISECONDS.toHours(duration)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
                val durationStr = if (hours > 0) "${hours} h ${minutes} min ${seconds} s" else "${minutes} min ${seconds} s"
                findViewById<TextView>(R.id.durationText).text = "Kesto: $durationStr"
                
                var totalDistance = 0.0
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
                    totalDistance += results[0]
                }
                val distanceStr = String.format("%.3f km", totalDistance / 1000.0).replace(".", ",")
                findViewById<TextView>(R.id.distanceText).text = "Matka: $distanceStr"
                findViewById<TextView>(R.id.trackPointsText).text = "Reittipisteitä: ${points.size} kpl"
                
                // Edit kentät
                findViewById<EditText>(R.id.fishermanInput).setText(formatFishermanName(s.fisherman))
                findViewById<EditText>(R.id.notesInput).setText(s.notes)
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun saveAndFinish() {
        val newFisherman = findViewById<EditText>(R.id.fishermanInput).text.toString().trim().uppercase()
        val newNotes = findViewById<EditText>(R.id.notesInput).text.toString()
        
        val s = session ?: return
        
        lifecycleScope.launch(Dispatchers.IO) {
            db.fishingSessionDao().update(s.copy(fisherman = newFisherman, notes = newNotes))
            withContext(Dispatchers.Main) {
                Toast.makeText(this@EditFishingSessionActivity, R.string.save_success, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun showTrimDialog() {
        val s = session ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val points = db.trackPointDao().getPointsForSession(s.id)
            withContext(Dispatchers.Main) {
                if (points.isEmpty()) {
                    Toast.makeText(this@EditFishingSessionActivity, "Istunnolla ei ole reittipisteitä", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val minAllowed = points.first().timestamp
                val maxAllowed = points.last().timestamp

                val dialogView = layoutInflater.inflate(R.layout.dialog_trim_session, null)
                val startSeekBar = dialogView.findViewById<SeekBar>(R.id.startSeekBar)
                val endSeekBar = dialogView.findViewById<SeekBar>(R.id.endSeekBar)
                val newStartInput = dialogView.findViewById<EditText>(R.id.newStartInput)
                val newEndInput = dialogView.findViewById<EditText>(R.id.newEndInput)
                val trimSessionLink = dialogView.findViewById<TextView>(R.id.trimSessionLink)
                val dialogBackLink = dialogView.findViewById<TextView>(R.id.dialogBackLink)

                val fullDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

                startSeekBar.max = points.size - 1
                startSeekBar.progress = 0
                endSeekBar.max = points.size - 1
                endSeekBar.progress = points.size - 1

                val initialStart = points.first().timestamp
                val initialEnd = points.last().timestamp
                newStartInput.setText(fullDateFormat.format(Date(initialStart)))
                newEndInput.setText(fullDateFormat.format(Date(initialEnd)))

                val dialog = AlertDialog.Builder(this@EditFishingSessionActivity)
                    .setView(dialogView)
                    .create()

                var isUpdatingFromSeekBar = false

                startSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            isUpdatingFromSeekBar = true
                            val point = points[progress.coerceIn(0, points.size - 1)]
                            newStartInput.setText(fullDateFormat.format(Date(point.timestamp)))
                            isUpdatingFromSeekBar = false
                            updateTrimLinkVisibility(
                                newStartInput, newEndInput, trimSessionLink,
                                minAllowed, maxAllowed, initialStart, initialEnd, fullDateFormat
                            )
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                endSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            isUpdatingFromSeekBar = true
                            val point = points[progress.coerceIn(0, points.size - 1)]
                            newEndInput.setText(fullDateFormat.format(Date(point.timestamp)))
                            isUpdatingFromSeekBar = false
                            updateTrimLinkVisibility(
                                newStartInput, newEndInput, trimSessionLink,
                                minAllowed, maxAllowed, initialStart, initialEnd, fullDateFormat
                            )
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                newStartInput.addTextChangedListener {
                    if (!isUpdatingFromSeekBar) {
                        try {
                            val date = fullDateFormat.parse(it.toString())
                            if (date != null) {
                                // Etsitään lähin piste
                                val index = points.indexOfFirst { p -> p.timestamp >= date.time }
                                val finalIndex = if (index == -1) points.size - 1 else index
                                startSeekBar.progress = finalIndex
                            }
                        } catch (e: ParseException) {
                        }
                    }
                    updateTrimLinkVisibility(
                        newStartInput, newEndInput, trimSessionLink,
                        minAllowed, maxAllowed, initialStart, initialEnd, fullDateFormat
                    )
                }

                newEndInput.addTextChangedListener {
                    if (!isUpdatingFromSeekBar) {
                        try {
                            val date = fullDateFormat.parse(it.toString())
                            if (date != null) {
                                // Etsitään lähin piste
                                val index = points.indexOfLast { p -> p.timestamp <= date.time }
                                val finalIndex = if (index == -1) 0 else index
                                endSeekBar.progress = finalIndex
                            }
                        } catch (e: ParseException) {
                        }
                    }
                    updateTrimLinkVisibility(
                        newStartInput, newEndInput, trimSessionLink,
                        minAllowed, maxAllowed, initialStart, initialEnd, fullDateFormat
                    )
                }

                updateTrimLinkVisibility(
                    newStartInput, newEndInput, trimSessionLink,
                    minAllowed, maxAllowed, initialStart, initialEnd, fullDateFormat
                )

                trimSessionLink.setOnClickListener {
                    val newStartStr = newStartInput.text.toString()
                    val newEndStr = newEndInput.text.toString()

                    val targetStart: Date
                    val targetEnd: Date
                    try {
                        targetStart = fullDateFormat.parse(newStartStr)!!
                        targetEnd = fullDateFormat.parse(newEndStr)!!
                    } catch (e: ParseException) {
                        Toast.makeText(this@EditFishingSessionActivity, R.string.invalid_format, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // Etsitään toteutuvat pisteet
                    // Alkuajaksi ensimmäinen ennen asetettua uutta alkuaikaa olevan pisteen tallennusaika
                    val startPoint = points.findLast { it.timestamp <= targetStart.time } ?: points.first()
                    // Session loppupäiväksi seuraava uuden loppupäivän jälkeen olevan pisteen tallennusaika
                    val endPoint = points.find { it.timestamp >= targetEnd.time } ?: points.last()

                    val newStartActual = startPoint.timestamp
                    val newEndActual = endPoint.timestamp

                    if (newStartActual >= newEndActual) {
                        Toast.makeText(this@EditFishingSessionActivity, R.string.invalid_times, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val startDiff = newStartActual - minAllowed
                    val endDiff = maxAllowed - newEndActual

                    val startDiffStr = formatDuration(startDiff)
                    val endDiffStr = formatDuration(endDiff)

                    AlertDialog.Builder(this@EditFishingSessionActivity)
                        .setMessage(getString(R.string.trim_confirm, startDiffStr, endDiffStr))
                        .setPositiveButton(R.string.yes) { _, _ ->
                            if (newStartActual != targetStart.time || newEndActual != targetEnd.time) {
                                val msg = getString(
                                    R.string.session_trimmed_info,
                                    fullDateFormat.format(Date(newStartActual)),
                                    fullDateFormat.format(Date(newEndActual))
                                )
                                Toast.makeText(this@EditFishingSessionActivity, msg, Toast.LENGTH_LONG).show()
                            }
                            performTrim(newStartActual, newEndActual)
                            dialog.dismiss()
                        }
                        .setNegativeButton(R.string.no, null)
                        .show()
                        .enlargeButtons()
                }

                dialogBackLink.setOnClickListener {
                    dialog.dismiss()
                }

                dialog.show()
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(durationMs)
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

        val sb = StringBuilder()
        if (days > 0) sb.append("${days} vrk ")
        if (hours > 0 || days > 0) sb.append("${hours} h ")
        sb.append("${minutes} min ${seconds} s")
        return sb.toString().trim()
    }

    private fun performTrim(newStartTime: Long, newEndTime: Long) {
        val s = session ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            db.trackPointDao().deletePointsOutsideRange(s.id, newStartTime, newEndTime)
            db.fishingSessionDao().update(s.copy(startedAt = newStartTime, endedAt = newEndTime))
            loadSessionData()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@EditFishingSessionActivity, R.string.trim_success, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
            }
        }
    }

    private fun updateTrimLinkVisibility(
        startInput: EditText,
        endInput: EditText,
        trimLink: TextView,
        minAllowed: Long,
        maxAllowed: Long,
        initialStart: Long,
        initialEnd: Long,
        df: SimpleDateFormat
    ) {
        try {
            val newStart = df.parse(startInput.text.toString())?.time ?: return
            val newEnd = df.parse(endInput.text.toString())?.time ?: return

            // Nollataan millisekunnit vertailua varten, koska df (dd.MM.yyyy HH:mm:ss) ei sisällä niitä
            val initialStartTrunc = initialStart / 1000 * 1000
            val initialEndTrunc = initialEnd / 1000 * 1000
            val minAllowedTrunc = minAllowed / 1000 * 1000
            val maxAllowedTrunc = maxAllowed / 1000 * 1000

            val isValid = newStart < newEnd &&
                    newStart >= minAllowedTrunc &&
                    newEnd <= maxAllowedTrunc

            val hasChanges = newStart != initialStartTrunc || newEnd != initialEndTrunc

            trimLink.visibility = if (isValid && hasChanges) android.view.View.VISIBLE else android.view.View.GONE
        } catch (e: ParseException) {
            trimLink.visibility = android.view.View.GONE
        }
    }
}
