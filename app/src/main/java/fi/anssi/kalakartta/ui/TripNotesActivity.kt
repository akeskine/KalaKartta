package fi.anssi.kalakartta.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import fi.anssi.kalakartta.R

class TripNotesActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_trip_notes)

        val notes = intent.getStringExtra("EXTRA_NOTES") ?: ""
        findViewById<TextView>(R.id.tripNotesTextView).text = notes

        findViewById<TextView>(R.id.okButton).setOnClickListener {
            finish()
        }
    }
}
